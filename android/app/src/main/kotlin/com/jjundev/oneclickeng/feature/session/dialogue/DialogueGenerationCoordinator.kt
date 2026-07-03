package com.jjundev.oneclickeng.feature.session.dialogue

import com.jjundev.oneclickeng.core.network.DialogueEvent
import com.jjundev.oneclickeng.core.network.DialogueMeta
import com.jjundev.oneclickeng.core.network.DialoguePayload
import com.jjundev.oneclickeng.core.network.DialogueRequest
import com.jjundev.oneclickeng.core.network.DialogueStream
import com.jjundev.oneclickeng.core.network.DialogueTurn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates dialogue-script generation (M1-01): consumes the typed SSE stream ([DialogueStream])
 * and surfaces completed objects as [DialogueGenState] — a coroutine state machine mirroring
 * [com.jjundev.oneclickeng.feature.session.tts.TtsPlaybackCoordinator] (injected scope + monotonic
 * token stale-guard). The client renders completed turns and never parses raw JSON (FR-6).
 *
 * Stale-guard (FR-14): each [start]/[retry] bumps [sessionToken] and cancels the prior collect Job;
 * a late event whose token != current is dropped, so an earlier generation's slow response can never
 * pollute the current UI. Cancelling the collect Job runs the stream's `awaitClose`, closing the SSE
 * socket.
 *
 * Idempotency (backend-functions.md §7): one [idempotencyKey] is minted per user-initiated
 * generation and reused verbatim on [retry] — the server transparently handles transient (same
 * session) vs terminal-refund (fresh start) cases, so the client needs no refund signal.
 *
 * Watchdog: an inter-event idle timeout ([IDLE_WATCHDOG_MS]) bounds only the pre-Ready phase — once a
 * turn arrives ([DialogueGenState.Ready]) the state is sticky and a stalled/late stream no longer
 * fails it.
 */
@Singleton
class DialogueGenerationCoordinator
    @Inject
    constructor(
        private val stream: DialogueStream,
        private val scope: CoroutineScope,
    ) {
        private val _state = MutableStateFlow<DialogueGenState>(DialogueGenState.Idle)
        val state: StateFlow<DialogueGenState> = _state.asStateFlow()

        @Volatile
        private var sessionToken = 0L
        private var currentJob: Job? = null
        private var watchdogJob: Job? = null

        // The request of the current attempt, retained so retry() reuses the SAME idempotencyKey.
        private var lastRequest: DialogueRequest? = null

        // Accumulators for the current attempt, snapshotted into Ready on each turn.
        private var sessionId: String? = null
        private var remaining: Int? = null
        private var meta: DialogueMeta? = null
        private var streamStatus = DialogueStreamStatus.Streaming
        private val turns = mutableListOf<DialogueTurn>()

        /** The server-minted sessionId of the current dialogue (or null), for the turn loop (M1-06). */
        fun sessionId(): String? = sessionId

        /** Begin a fresh generation. Mints a new idempotencyKey; supersedes any in-flight attempt. */
        fun start(
            level: String,
            topic: String,
            length: Int,
            firstSession: Boolean,
        ) {
            launchAttempt(
                DialogueRequest(
                    idempotencyKey = UUID.randomUUID().toString(),
                    payload = DialoguePayload(level, topic, length, firstSession),
                ),
            )
        }

        /** Retry the current attempt, REUSING its idempotencyKey (backend-functions.md §7). No-op if
         *  nothing has been started yet, or if the daily limit blocked the start — retrying a quota
         *  rejection would just be re-rejected(FR-27), so [DialogueGenState.QuotaBlocked] is terminal
         *  here (UI also omits the retry affordance; this guard is defense-in-depth). [lastRequest] is
         *  left untouched so a later [start] still behaves normally. */
        fun retry() {
            if (_state.value is DialogueGenState.QuotaBlocked) return
            val request = lastRequest ?: return
            launchAttempt(request)
        }

        private fun launchAttempt(request: DialogueRequest) {
            val token = ++sessionToken
            currentJob?.cancel()
            watchdogJob?.cancel()
            lastRequest = request
            sessionId = null
            remaining = null
            meta = null
            streamStatus = DialogueStreamStatus.Streaming
            turns.clear()
            _state.value = DialogueGenState.Generating
            armWatchdog(token)
            currentJob =
                scope.launch {
                    stream.events(request).collect { event ->
                        if (token != sessionToken) return@collect
                        onEvent(token, event)
                    }
                    // Stream closed. If we never reached Ready, it ended prematurely → Failed.
                    if (token == sessionToken && _state.value is DialogueGenState.Generating) {
                        fail(token)
                    }
                }
        }

        private fun onEvent(
            token: Long,
            event: DialogueEvent,
        ) {
            when (event) {
                is DialogueEvent.Start -> {
                    sessionId = event.sessionId
                    remaining = event.remaining
                    refreshIfReady()
                    rearmIfGenerating(token)
                }
                is DialogueEvent.Meta -> {
                    meta = event.meta
                    refreshIfReady()
                    rearmIfGenerating(token)
                }
                is DialogueEvent.Turn -> {
                    turns.add(event.turn)
                    // First completed turn flips Ready and retires the idle watchdog.
                    watchdogJob?.cancel()
                    streamStatus = DialogueStreamStatus.Streaming
                    _state.value = readySnapshot()
                }
                is DialogueEvent.QuotaExceeded ->
                    // 일일 한도 거부: 아직 대본이 없을 때만 QuotaBlocked 로 분기(Failed 와 달리 재시도 아님).
                    // Ready 이후엔 이미 렌더된 대본이 우선이므로 무시(sticky). 워치독은 중단한다.
                    if (_state.value is DialogueGenState.Generating) {
                        watchdogJob?.cancel()
                        _state.value = DialogueGenState.QuotaBlocked(event.remaining)
                    }
                is DialogueEvent.Done ->
                    // Done before any turn = no usable content → Failed. After Ready = no-op.
                    if (_state.value is DialogueGenState.Generating) {
                        fail(token)
                    } else if (_state.value is DialogueGenState.Ready) {
                        streamStatus = DialogueStreamStatus.Done
                        _state.value = readySnapshot()
                    }
                is DialogueEvent.Error ->
                    // Failure before Ready → Failed; after Ready the content stands (sticky), log-only.
                    if (_state.value is DialogueGenState.Generating) {
                        fail(token)
                    } else if (_state.value is DialogueGenState.Ready) {
                        streamStatus = DialogueStreamStatus.FailedAfterReady
                        _state.value = readySnapshot()
                    }
            }
        }

        /** Re-snapshot Start/Meta into an already-Ready state so late metadata isn't lost. */
        private fun refreshIfReady() {
            if (_state.value is DialogueGenState.Ready) {
                _state.value = readySnapshot()
            }
        }

        private fun readySnapshot(): DialogueGenState.Ready =
            DialogueGenState.Ready(sessionId, remaining, meta, turns.toList(), streamStatus)

        /** Reset the idle watchdog on any pre-Ready progress (meta/start drip without a turn yet). */
        private fun rearmIfGenerating(token: Long) {
            if (_state.value is DialogueGenState.Generating) armWatchdog(token)
        }

        private fun armWatchdog(token: Long) {
            watchdogJob?.cancel()
            watchdogJob =
                scope.launch {
                    delay(IDLE_WATCHDOG_MS)
                    if (token == sessionToken && _state.value is DialogueGenState.Generating) {
                        currentJob?.cancel() // closes the SSE socket via awaitClose
                        fail(token)
                    }
                }
        }

        private fun fail(token: Long) {
            if (token != sessionToken) return
            watchdogJob?.cancel()
            _state.value = DialogueGenState.Failed
        }

        companion object {
            // Inter-event idle bound for the pre-Ready phase. Constant for now; a config override is a
            // follow-up seam (mirrors ANALYZE_WATCHDOG_MS). Authoritative over the SSE socket, whose
            // read timeout is disabled (DialogueSseStream).
            const val IDLE_WATCHDOG_MS = 30_000L
        }
    }

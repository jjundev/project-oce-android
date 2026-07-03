package com.jjundev.oneclickeng.feature.session.feedback

import com.jjundev.oneclickeng.core.network.FeedbackEvent
import com.jjundev.oneclickeng.core.network.FeedbackPayload
import com.jjundev.oneclickeng.core.network.FeedbackRequest
import com.jjundev.oneclickeng.core.network.FeedbackStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** 재시도/스킵 API가 지정하는 슬림 섹션. 세 섹션은 각자 독립 카운터로 재시도된다(FB8). */
enum class SlimSection { WritingScore, Grammar, NaturalExpression }

/**
 * 턴 슬림 피드백(M1-07)을 오케스트레이션한다: `feedback` SSE([FeedbackStream])를 소비해 완성 섹션을
 * [SlimFeedbackState] 로 점진 노출하는 코루틴 상태 머신 — [com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenerationCoordinator]
 * (주입 scope + 단조 stale 토큰 + idle 워치독)와 [com.jjundev.oneclickeng.feature.session.speaking.SpeakingAnalysisCoordinator]
 * 를 계승한다. 클라는 완성 섹션만 렌더하고 원시 JSON 을 파싱하지 않는다(FR-6).
 *
 * Stale-guard: 각 [start]/[retry] 는 [sessionToken] 을 bump 하고 이전 collect Job 을 취소한다 — 늦은 이벤트는
 * 토큰 불일치로 드롭되어 이전 요청의 느린 응답이 현재 UI 를 오염시키지 못한다(collect 취소는 스트림
 * `awaitClose` 로 소켓을 닫는다).
 *
 * 워치독(§4): 어떤 섹션이든 아직 Loading 인 동안 인터-이벤트 idle 타임아웃을 건다. 발화 시 currentJob 을
 * 취소하고(소켓 닫음) 아직 도착 못 한 섹션을 전부 Failed 로 만든다(도착 섹션은 sticky). 캡 거부(§2b)는 이와
 * 별개 경로로 요청-레벨 [SlimFeedbackState.QuotaBlocked] 로 분기한다(섹션 개별 Failed 아님).
 *
 * 트리거(start) 호출은 마이크 4상태 배선(M1-08) 소관이다. 이 코디네이터는 진입점(seam)만 노출한다.
 */
// 상태 머신 코디네이터라 작은 전이 헬퍼가 많다(ReminderOrchestrator 선례와 동일 판단).
@Suppress("TooManyFunctions")
@Singleton
class SlimFeedbackCoordinator
    @Inject
    constructor(
        private val stream: FeedbackStream,
        private val scope: CoroutineScope,
    ) {
        private val _state = MutableStateFlow<SlimFeedbackState>(SlimFeedbackState.Idle)
        val state: StateFlow<SlimFeedbackState> = _state.asStateFlow()

        @Volatile
        private var sessionToken = 0L
        private var currentJob: Job? = null
        private var watchdogJob: Job? = null

        // The request of the current turn, retained so retry() re-runs the same feedback call.
        private var lastRequest: FeedbackRequest? = null
        private var header: RecapHeader? = null

        // Per-section accumulators, snapshotted into Active on every change.
        private var writingScore: SectionState<WritingScore> = SectionState.Loading
        private var grammar: SectionState<Grammar> = SectionState.Loading
        private var natural: SectionState<NaturalExpression> = SectionState.Loading

        // Cumulative failure counts per section (persist across retries — FB8 누적 2회).
        private var attemptsWs = 0
        private var attemptsGr = 0
        private var attemptsNat = 0

        /**
         * Begin slim feedback for a completed turn. Cancels any in-flight request first, resets all
         * three sections to Loading, and opens the stream. `userEnglish` (voice transcript or typed
         * input) doubles as the header echo (§2.1).
         */
        fun start(
            sessionId: String,
            koreanPrompt: String,
            userEnglish: String,
            referenceEnglish: String,
            level: String,
        ) {
            header = RecapHeader(koreanPrompt = koreanPrompt, userText = userEnglish)
            writingScore = SectionState.Loading
            grammar = SectionState.Loading
            natural = SectionState.Loading
            attemptsWs = 0
            attemptsGr = 0
            attemptsNat = 0
            lastRequest =
                FeedbackRequest(
                    sessionId = sessionId,
                    payload =
                        FeedbackPayload(
                            koreanPrompt = koreanPrompt,
                            userEnglish = userEnglish,
                            referenceEnglish = referenceEnglish,
                            level = level,
                        ),
                )
            emit()
            launchAttempt()
        }

        /**
         * Retry one failed section (FB8: manual, user-tap). Re-runs the whole feedback call (the
         * backend streams all three together) but only the retried section is set back to Loading;
         * already-Ready sections stay put and a re-delivered section simply refreshes. No-op unless
         * that section is [SectionState.Failed] with retries remaining.
         */
        fun retry(section: SlimSection) {
            val failed = sectionState(section) as? SectionState.Failed
            if (lastRequest == null || failed == null || !failed.canRetry) return
            setSection(section, SectionState.Loading)
            emit()
            launchAttempt()
        }

        /**
         * Skip a repeatedly-failed section (FB8: after 누적 2회). Marks it [SectionState.Skipped] so it
         * counts as settled for "다음" gating and its buffer key becomes null (§9.1). No-op unless the
         * section is currently Failed.
         */
        fun skip(section: SlimSection) {
            if (sectionState(section) !is SectionState.Failed) return
            setSection(section, SectionState.Skipped)
            emit()
        }

        /** Cancel any in-flight request and reset to idle (e.g. sheet dismissed). */
        fun reset() {
            sessionToken++
            currentJob?.cancel()
            watchdogJob?.cancel()
            currentJob = null
            header = null
            lastRequest = null
            _state.value = SlimFeedbackState.Idle
        }

        /**
         * Turn buffer snapshot for the summary pipeline (dialogue-learning-flow.md §8). Ready sections
         * contribute their value; failed/skipped sections leave their key null (§9.1). `correctedText`
         * is the §17 flattening of the grammar segments.
         */
        fun bufferSnapshot(): TurnFeedbackBuffer =
            TurnFeedbackBuffer(
                slimScore = writingScore.readyValueOrNull()?.score,
                correctedText = grammar.readyValueOrNull()?.segments?.flattenCorrectedText(),
                naturalExpression =
                    natural.readyValueOrNull()?.segments?.joinToString(separator = "") { it.text },
            )

        private fun launchAttempt() {
            val request = lastRequest ?: return
            val token = ++sessionToken
            currentJob?.cancel()
            armWatchdog(token)
            currentJob =
                scope.launch {
                    stream.events(request).collect { event ->
                        if (token != sessionToken) return@collect
                        onEvent(token, event)
                    }
                    // Stream closed. Any section that never arrived failed this attempt.
                    if (token == sessionToken) {
                        failLoadingSections(token)
                    }
                }
        }

        private fun onEvent(
            token: Long,
            event: FeedbackEvent,
        ) {
            when (event) {
                is FeedbackEvent.Section.WritingScore -> {
                    // Never overwrite a user Skip; else fill the section (fresh arrival or retry refresh).
                    if (writingScore !is SectionState.Skipped) {
                        writingScore = SectionState.Ready(event.value.toDomain())
                    }
                    afterSection(token)
                }
                is FeedbackEvent.Section.Grammar -> {
                    if (grammar !is SectionState.Skipped) {
                        grammar = SectionState.Ready(event.value.toDomain())
                    }
                    afterSection(token)
                }
                is FeedbackEvent.Section.NaturalExpression -> {
                    if (natural !is SectionState.Skipped) {
                        natural = SectionState.Ready(event.value.toDomain())
                    }
                    afterSection(token)
                }
                is FeedbackEvent.Done -> failLoadingSections(token)
                is FeedbackEvent.Error -> failLoadingSections(token)
                is FeedbackEvent.QuotaExceeded -> {
                    // 캡 거부: 요청-레벨 중립 상태로 분기(섹션 개별 Failed 아님, §2b). 워치독 중단.
                    watchdogJob?.cancel()
                    val h = header
                    if (token == sessionToken && h != null) {
                        _state.value = SlimFeedbackState.QuotaBlocked(h, event.remaining)
                    }
                }
            }
        }

        /** Snapshot after a section arrival and re-arm the watchdog while any section is still Loading. */
        private fun afterSection(token: Long) {
            emit()
            if (anyLoading) {
                armWatchdog(token)
            } else {
                watchdogJob?.cancel()
            }
        }

        /** Convert every still-Loading section into Failed (attempts++), leaving arrived sections sticky. */
        private fun failLoadingSections(token: Long) {
            if (token != sessionToken) return
            watchdogJob?.cancel()
            if (writingScore is SectionState.Loading) {
                attemptsWs++
                writingScore = SectionState.Failed(attemptsWs)
            }
            if (grammar is SectionState.Loading) {
                attemptsGr++
                grammar = SectionState.Failed(attemptsGr)
            }
            if (natural is SectionState.Loading) {
                attemptsNat++
                natural = SectionState.Failed(attemptsNat)
            }
            emit()
        }

        private fun armWatchdog(token: Long) {
            watchdogJob?.cancel()
            watchdogJob =
                scope.launch {
                    delay(IDLE_WATCHDOG_MS)
                    if (token == sessionToken && anyLoading) {
                        currentJob?.cancel() // closes the SSE socket via awaitClose
                        failLoadingSections(token)
                    }
                }
        }

        private val anyLoading: Boolean
            get() =
                writingScore is SectionState.Loading ||
                    grammar is SectionState.Loading ||
                    natural is SectionState.Loading

        /** Re-snapshot the three sections into [SlimFeedbackState.Active] (no-op before a header exists). */
        private fun emit() {
            val h = header ?: return
            // Do not clobber a terminal QuotaBlocked with a section snapshot.
            if (_state.value is SlimFeedbackState.QuotaBlocked) return
            _state.value = SlimFeedbackState.Active(h, writingScore, grammar, natural)
        }

        // --- per-section field accessors (the three sections have distinct value types) ---

        private fun sectionState(section: SlimSection): SectionState<*> =
            when (section) {
                SlimSection.WritingScore -> writingScore
                SlimSection.Grammar -> grammar
                SlimSection.NaturalExpression -> natural
            }

        /** Set a section to a value-less terminal/loading state (Loading or Skipped). */
        private fun setSection(
            section: SlimSection,
            value: SectionState<Nothing>,
        ) {
            when (section) {
                SlimSection.WritingScore -> writingScore = value
                SlimSection.Grammar -> grammar = value
                SlimSection.NaturalExpression -> natural = value
            }
        }

        companion object {
            // Inter-event idle bound while any section is still Loading. Constant for now; a config
            // override is a follow-up seam (mirrors DialogueGenerationCoordinator.IDLE_WATCHDOG_MS and
            // SpeakingAnalysisCoordinator.ANALYZE_WATCHDOG_MS). Authoritative over the SSE socket, whose
            // read timeout is disabled (FeedbackSseStream).
            const val IDLE_WATCHDOG_MS = 20_000L
        }
    }

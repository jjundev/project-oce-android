package com.jjundev.oneclickeng.feature.session.tts

import com.jjundev.oneclickeng.core.audio.PcmPlayer
import com.jjundev.oneclickeng.core.network.LlmApi
import com.jjundev.oneclickeng.core.network.TtsPayload
import com.jjundev.oneclickeng.core.network.TtsRequest
import com.jjundev.oneclickeng.core.settings.TtsQuality
import com.jjundev.oneclickeng.core.settings.TtsSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** Cache key for a synthesized opponent line — server output depends only on
 *  (text, gender, rate). Rate is part of the key so a mid-session speed change
 *  re-synthesizes instead of playing stale-speed audio. */
internal data class TtsCacheKey(
    val text: String,
    val gender: String?,
    val speechRate: Float,
)

/**
 * Orchestrates opponent-turn TTS: server (Gemini) synthesis with an 8s watchdog, device
 * fallback with a 7s watchdog, in-memory replay, mute, and stale-session guarding
 * (Kotlin/coroutine port of the archived `DialoguePlaybackCoordinator`).
 *
 * The watchdog bounds *synthesis* (PCM reception), not playback duration — once audio
 * arrives, it plays to completion (tts.md §4). Base64→PCM decoding happens here (plan
 * #11) via [Base64] (available at minSdk 26), keeping the player free of encoding.
 * Framework audio/engine work is behind [PcmPlayer]/[DeviceTts] so this class is a pure,
 * unit-testable coroutine state machine.
 */
// 상태 머신 코디네이터라 작은 전이 헬퍼가 많다(DeepFeedbackCoordinator 선례와 동일 판단).
@Suppress("TooManyFunctions")
@Singleton
class TtsPlaybackCoordinator
    @Inject
    constructor(
        private val api: LlmApi,
        private val player: PcmPlayer,
        private val deviceTts: DeviceTts,
        private val settingsRepo: TtsSettingsRepository,
        private val scope: CoroutineScope,
    ) {
        private val _state = MutableStateFlow(PlaybackState.IDLE)
        val state: StateFlow<PlaybackState> = _state.asStateFlow()

        /** emits once whenever the turn is done and the session may auto-advance (FR-7). */
        private val _completions = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val completions: SharedFlow<Unit> = _completions.asSharedFlow()

        /** emits once when the current turn's audio actually begins — the server PCM playback
         *  start, or the device engine's onStart after init. The opponent bubble reveal is gated
         *  on this so the "typing" skeleton stays up through synthesis / engine-init LOADING and
         *  the bubble appears in sync with the first audible sound (not before). Consumers must
         *  phase-guard — replay and self-clip playback also reach PLAYING. */
        private val _audioReady = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val audioReady: SharedFlow<Unit> = _audioReady.asSharedFlow()

        // Monotonic guard: a late callback whose token != current is a stale session, ignored.
        @Volatile
        private var sessionToken = 0L
        private var currentJob: Job? = null

        // Current turn's decoded audio, retained for instant, re-synthesis-free replay
        // (plan #14/#18). Cleared at the start of each new turn.
        @Volatile
        private var lastPcm: ByteArray? = null

        @Volatile
        private var lastSampleRate = 0

        private class CachedAudio(val pcm: ByteArray, val sampleRate: Int)

        // Session-scoped synthesis cache: opponent-line PCM keyed by (text, gender, rate).
        // accessOrder=true + removeEldestEntry makes it LRU-bounded. All access is on the
        // coordinator's single-threaded Main scope, so no locking is needed (see Global
        // Constraints). Populated by obtainAudio; cleared by clearCache (Task 2) on screen exit.
        private val cache =
            object : LinkedHashMap<TtsCacheKey, CachedAudio>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<TtsCacheKey, CachedAudio>): Boolean = size > CACHE_CAP
            }

        // Shared in-flight synthesis: one Deferred per key so a live playFromServer and a
        // concurrent prefetch for the same line join a single /llm call (exactly-once).
        private val inFlight = mutableMapOf<TtsCacheKey, Deferred<CachedAudio?>>()

        // Outstanding prefetch launches, cancelled by clearCache on screen exit.
        private val prefetchJobs = mutableListOf<Job>()

        // replay 등 "발화는 하되 턴을 전진시키지 않는" 재생을 위한 플래그. startNewSession 이 true 로 리셋하고
        // playTurn 이 인자로 덮어쓴다. finish 가 completions emit 여부를 이 값으로 게이트한다.
        @Volatile
        private var advanceOnDone = true

        /** Synthesize + play the opponent line. Cancels any in-flight playback first.
         *  [deviceOnly] 면 서버 경로를 건너뛴다(디바이스 전용). [advanceOnDone]=false 면 정상 종료에서도
         *  completions 를 emit 하지 않아 재생이 턴 전진을 구동하지 않는다(replay 용). */
        fun playTurn(
            text: String,
            gender: String?,
            deviceOnly: Boolean = false,
            advanceOnDone: Boolean = true,
        ) {
            val token = startNewSession()
            this.advanceOnDone = advanceOnDone
            currentJob =
                scope.launch {
                    val settings = settingsRepo.current()
                    if (settings.muted) {
                        finish(token, PlaybackState.IDLE, advance = true)
                        return@launch
                    }
                    lastPcm = null
                    _state.value = PlaybackState.LOADING
                    if (!deviceOnly && settings.quality == TtsQuality.SERVER) {
                        if (playFromServer(token, text, gender, settings.speechRate)) return@launch
                        // server timed out / failed → fall through to device TTS
                    }
                    playFromDevice(token, text, gender, settings.speechRate)
                }
        }

        /** Replay the current turn's audio from memory — no re-synthesis (plan #18). */
        fun replay() {
            val token = startNewSession()
            currentJob =
                scope.launch {
                    // Re-read mute: if muted mid-turn, replay is a no-op that still advances (#14).
                    if (settingsRepo.current().muted) {
                        finish(token, PlaybackState.IDLE, advance = true)
                        return@launch
                    }
                    val pcm = lastPcm
                    val rate = lastSampleRate
                    if (pcm == null || rate <= 0) {
                        finish(token, PlaybackState.IDLE, advance = true)
                        return@launch
                    }
                    playPcm(token, pcm, rate)
                }
        }

        /**
         * 학습자 자기 녹음(캡처된 raw PCM16)을 그대로 재생한다. 진행 중 재생을 먼저 취소하고
         * ([startNewSession]) 이 클립을 재생한다. 상대 발화·`다시 듣기`와 **동일한 단일 재생 권위**
         * (같은 [player])를 공유해 두 오디오가 겹치지 않는다. 자기 녹음 재생은 대화 턴을 전진시키지
         * 않으므로 완료 신호를 내지 않는다([advanceOnDone]=false). muted 면 무음 no-op 으로 즉시 IDLE 로
         * 정착한다(상대 [replay] 정합). 상대 턴 재합성용 [lastPcm] 은 건드리지 않는다.
         */
        fun playClip(
            pcm: ByteArray,
            sampleRate: Int,
        ) {
            val token = startNewSession()
            this.advanceOnDone = false
            currentJob =
                scope.launch {
                    if (settingsRepo.current().muted) {
                        finish(token, PlaybackState.IDLE, advance = false)
                        return@launch
                    }
                    playPcm(token, pcm, sampleRate)
                }
        }

        /** Warm the cache for an upcoming opponent line so its later [playTurn] is a cache
         *  hit (instant, no watchdog). SERVER + unmuted only. Delegates to [obtainAudio], so
         *  a live [playTurn] for the same line that arrives mid-synthesis joins this one call
         *  (exactly-once). Never touches player/state/token, so it cannot disturb playback. */
        fun prefetch(
            text: String,
            gender: String?,
        ) {
            val job =
                scope.launch {
                    val settings = settingsRepo.current()
                    if (settings.muted || settings.quality != TtsQuality.SERVER) return@launch
                    obtainAudio(text, gender, settings.speechRate) // result cached as a side effect
                }
            prefetchJobs.add(job)
            job.invokeOnCompletion { prefetchJobs.remove(job) }
        }

        /** Drop all cached and in-flight synthesis (call on leaving the dialogue screen).
         *  Cancels outstanding prefetch launches and their in-flight synthesis jobs. Does not
         *  affect live playback — [stop] handles that separately. */
        fun clearCache() {
            prefetchJobs.toList().forEach { it.cancel() }
            prefetchJobs.clear()
            inFlight.values.toList().forEach { it.cancel() }
            inFlight.clear()
            cache.clear()
        }

        /** Stop all playback and reset to idle (e.g. speaker re-tap = stop, plan #14). */
        fun stop() {
            sessionToken++
            currentJob?.cancel()
            currentJob = null
            player.stop()
            deviceTts.stop()
            _state.value = PlaybackState.IDLE
        }

        private fun startNewSession(): Long {
            val token = ++sessionToken
            currentJob?.cancel()
            currentJob = null
            player.stop()
            deviceTts.stop()
            advanceOnDone = true
            return token
        }

        /** Server synthesis + base64 decode under the watchdog. Returns null on
         *  timeout / network / HTTP / malformed / undecodable payload (caller falls back
         *  to device, or — for prefetch — simply skips). Never touches player/state/token,
         *  so it is safe to call from a background prefetch. */
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private suspend fun synthesize(
            text: String,
            gender: String?,
            rate: Float,
        ): CachedAudio? {
            val response =
                withTimeoutOrNull(SERVER_WATCHDOG_MS) {
                    try {
                        api.tts(TtsRequest(payload = TtsPayload(text = text, gender = gender, speechRate = rate)))
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                } ?: return null
            return try {
                CachedAudio(Base64.getDecoder().decode(response.pcmBase64), response.sampleRate)
            } catch (e: IllegalArgumentException) {
                null // undecodable payload
            }
        }

        /** The single synthesis authority: cache hit → return it; else join an in-flight
         *  synthesis for the same key (so a live play and a prefetch never double-call);
         *  else start one. The synthesis job caches its own result and evicts its own
         *  in-flight entry via [invokeOnCompletion] — tied to the SYNTHESIS finishing, not to
         *  any awaiter. This matters: `scope.async` is a sibling of the awaiters (not their
         *  child), so a `startNewSession()` that cancels a live awaiter mid-await must NOT
         *  evict the still-running entry — otherwise a same-key caller would start a second
         *  synthesis. A cancelled awaiter therefore still leaves the job running and cached.
         *  Main-thread confined, so the map ops need no lock. */
        private suspend fun obtainAudio(
            text: String,
            gender: String?,
            rate: Float,
        ): CachedAudio? {
            val key = TtsCacheKey(text, gender, rate)
            cache[key]?.let { return it }
            val deferred =
                inFlight[key] ?: scope.async {
                    synthesize(text, gender, rate)?.also { cache[key] = it }
                }.also { d ->
                    inFlight[key] = d
                    d.invokeOnCompletion { inFlight.remove(key, d) } // identity remove; tied to the job, not awaiters
                }
            return deferred.await()
        }

        /** @return true if the server path terminally handled the turn (played or swallowed
         *  as stale); false if synthesis failed and the caller should try device TTS.
         *  A cache hit / in-flight join plays without a fresh network call. */
        @Suppress("ReturnCount")
        private suspend fun playFromServer(
            token: Long,
            text: String,
            gender: String?,
            rate: Float,
        ): Boolean {
            val audio = obtainAudio(text, gender, rate) ?: return false
            if (token != sessionToken) return true // stale: swallow, don't advance
            lastPcm = audio.pcm
            lastSampleRate = audio.sampleRate
            playPcm(token, audio.pcm, audio.sampleRate)
            return true
        }

        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private suspend fun playPcm(
            token: Long,
            pcm: ByteArray,
            sampleRate: Int,
        ) {
            if (token != sessionToken) return
            _state.value = PlaybackState.PLAYING
            _audioReady.tryEmit(Unit)
            try {
                player.play(pcm, sampleRate)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                finish(token, PlaybackState.FAILED, advance = true)
                return
            }
            finish(token, PlaybackState.IDLE, advance = true)
        }

        private suspend fun playFromDevice(
            token: Long,
            text: String,
            gender: String?,
            rate: Float,
        ) {
            if (token != sessionToken) return
            // No premature PLAYING here: the device engine may still be initializing on the first
            // utterance (the 1–2s first-audio load). PLAYING + audioReady fire from onStart, when
            // audio actually begins, so the opponent skeleton stays up until then.
            val result =
                withTimeoutOrNull(DEVICE_WATCHDOG_MS) {
                    deviceTts.speak(text, gender, rate) { onPlaybackStarted(token) }
                }
            if (token != sessionToken) return
            when (result) {
                null -> finish(token, PlaybackState.FAILED, advance = true) // 7s watchdog fired
                DeviceTtsResult.COMPLETED -> finish(token, PlaybackState.IDLE, advance = true)
                DeviceTtsResult.LANGUAGE_MISSING ->
                    finish(token, PlaybackState.ERROR_TEXT_ONLY, advance = false) // retry, no advance
                DeviceTtsResult.ERROR -> finish(token, PlaybackState.FAILED, advance = true)
            }
        }

        /** Device engine reported audio actually started — promote to PLAYING and signal reveal.
         *  A stale token (a newer turn started) is ignored. */
        private fun onPlaybackStarted(token: Long) {
            if (token != sessionToken) return
            _state.value = PlaybackState.PLAYING
            _audioReady.tryEmit(Unit)
        }

        /** Set the terminal state and, if [advance], emit a completion so the turn progresses. */
        private fun finish(
            token: Long,
            state: PlaybackState,
            advance: Boolean,
        ) {
            if (token != sessionToken) return
            _state.value = state
            if (advance && advanceOnDone) _completions.tryEmit(Unit)
        }

        companion object {
            // Client watchdogs (tts.md §4). Constants for now; a config override is a follow-up seam.
            const val SERVER_WATCHDOG_MS = 8_000L
            const val DEVICE_WATCHDOG_MS = 7_000L

            // A dialogue has ~2–3 opponent lines; 4 covers current + next with margin, and
            // LRU eviction bounds memory if a longer session accumulates more.
            const val CACHE_CAP = 4
        }
    }

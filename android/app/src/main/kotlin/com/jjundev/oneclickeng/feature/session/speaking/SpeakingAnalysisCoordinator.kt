package com.jjundev.oneclickeng.feature.session.speaking

import com.jjundev.oneclickeng.core.audio.RecordingResult
import com.jjundev.oneclickeng.core.audio.WavEncoder
import com.jjundev.oneclickeng.core.network.LlmApi
import com.jjundev.oneclickeng.core.network.SpeakingPayload
import com.jjundev.oneclickeng.core.network.SpeakingRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates speaking analysis (M1-06): wrap the captured PCM as WAV, base64-encode it,
 * POST `/llm task=speaking`, and surface `{transcript, feedbackMessage}` as typed state — a
 * Kotlin/coroutine state machine mirroring [com.jjundev.oneclickeng.feature.session.tts.TtsPlaybackCoordinator]
 * (watchdog + monotonic stale-token guard). Framework work (WAV/base64) is off the main
 * thread; the state is UI-facing.
 *
 * `transcript` is retained in [transcript] so the slim feedback stage (M1-07) reuses it with
 * NO re-transcription (issue acceptance criterion). No numeric score exists anywhere here —
 * the response type has none by design (speaking-analyze.md, PRD A8/R3).
 *
 * `sessionId` is injected per call: it originates from the dialogue start `meta` event
 * (M1-01/M1-02) held by the session state; this coordinator does not own dialogue start. The
 * mic-loop wiring that calls [analyze] and renders the result into the chat lands in M1-08.
 */
@Singleton
class SpeakingAnalysisCoordinator
    @Inject
    constructor(
        private val api: LlmApi,
        private val scope: CoroutineScope,
    ) {
        private val _state = MutableStateFlow<SpeakingAnalysisState>(SpeakingAnalysisState.Idle)
        val state: StateFlow<SpeakingAnalysisState> = _state.asStateFlow()

        // Last successful transcript, retained for re-synthesis-free reuse by M1-07 feedback.
        // Null unless the current result is a non-empty transcript.
        @Volatile
        private var lastTranscript: String? = null

        /** The transcript of the current turn (or null), for M1-07 feedback reuse. */
        fun transcript(): String? = lastTranscript

        // Monotonic guard: a late response whose token != current is a stale turn, ignored.
        @Volatile
        private var sessionToken = 0L
        private var currentJob: Job? = null

        /**
         * Analyze a captured utterance for [sessionId]. Cancels any in-flight analysis first.
         * An empty/unintelligible transcript resolves to [SpeakingAnalysisState.Empty]; any
         * network/server/watchdog failure resolves to [SpeakingAnalysisState.Failed].
         */
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        fun analyze(
            captured: RecordingResult.Captured,
            sessionId: String,
        ) {
            val token = ++sessionToken
            currentJob?.cancel()
            lastTranscript = null
            _state.value = SpeakingAnalysisState.Analyzing
            currentJob =
                scope.launch {
                    // WAV-wrap + base64 inline on the coordinator scope (mirrors
                    // TtsPlaybackCoordinator's inline base64 work) — a single utterance is
                    // small, and this keeps the state machine deterministic under virtual time.
                    val audioBase64 =
                        Base64.getEncoder()
                            .encodeToString(WavEncoder.wrap(captured.pcm, captured.sampleRate))
                    if (token != sessionToken) return@launch

                    val response =
                        withTimeoutOrNull(ANALYZE_WATCHDOG_MS) {
                            // Any failure (network, HTTP error, malformed) → Failed.
                            try {
                                api.speaking(
                                    SpeakingRequest(
                                        sessionId = sessionId,
                                        payload = SpeakingPayload(audioBase64 = audioBase64),
                                    ),
                                )
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                null
                            }
                        }
                    if (token != sessionToken) return@launch

                    _state.value =
                        when {
                            response == null -> SpeakingAnalysisState.Failed
                            response.transcript.isBlank() -> SpeakingAnalysisState.Empty
                            else -> {
                                lastTranscript = response.transcript
                                SpeakingAnalysisState.Result(
                                    transcript = response.transcript,
                                    encouragement = response.feedbackMessage,
                                )
                            }
                        }
                }
        }

        /** Cancel any in-flight analysis and reset to idle (e.g. re-record). */
        fun reset() {
            sessionToken++
            currentJob?.cancel()
            currentJob = null
            lastTranscript = null
            _state.value = SpeakingAnalysisState.Idle
        }

        companion object {
            // Client watchdog bounding the analysis round-trip. Constant for now; a config
            // override is a follow-up seam (mirrors TtsPlaybackCoordinator). Kept below the
            // OkHttp read timeout (20s, NetworkModule) so the watchdog is the harder bound.
            const val ANALYZE_WATCHDOG_MS = 15_000L
        }
    }

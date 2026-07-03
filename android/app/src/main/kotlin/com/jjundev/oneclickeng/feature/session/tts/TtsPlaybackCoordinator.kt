package com.jjundev.oneclickeng.feature.session.tts

import com.jjundev.oneclickeng.core.audio.PcmPlayer
import com.jjundev.oneclickeng.core.network.LlmApi
import com.jjundev.oneclickeng.core.network.TtsPayload
import com.jjundev.oneclickeng.core.network.TtsRequest
import com.jjundev.oneclickeng.core.settings.TtsQuality
import com.jjundev.oneclickeng.core.settings.TtsSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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

        /** Synthesize + play the opponent line. Cancels any in-flight playback first. */
        fun playTurn(
            text: String,
            gender: String?,
        ) {
            val token = startNewSession()
            currentJob =
                scope.launch {
                    val settings = settingsRepo.current()
                    if (settings.muted) {
                        finish(token, PlaybackState.IDLE, advance = true)
                        return@launch
                    }
                    lastPcm = null
                    _state.value = PlaybackState.LOADING
                    if (settings.quality == TtsQuality.SERVER) {
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
            return token
        }

        /** @return true if the server path terminally handled the turn (played or swallowed
         *  as stale); false if it timed out/failed and the caller should try device TTS. */
        @Suppress("ReturnCount", "TooGenericExceptionCaught", "SwallowedException")
        private suspend fun playFromServer(
            token: Long,
            text: String,
            gender: String?,
            rate: Float,
        ): Boolean {
            val response =
                withTimeoutOrNull(SERVER_WATCHDOG_MS) {
                    // Any synthesis failure (network, HTTP error, malformed) → device fallback.
                    try {
                        api.tts(TtsRequest(payload = TtsPayload(text = text, gender = gender, speechRate = rate)))
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                } ?: return false

            if (token != sessionToken) return true // stale: swallow, don't advance
            val pcm =
                try {
                    Base64.getDecoder().decode(response.pcmBase64)
                } catch (e: IllegalArgumentException) {
                    return false // undecodable payload → device fallback
                }
            lastPcm = pcm
            lastSampleRate = response.sampleRate
            playPcm(token, pcm, response.sampleRate)
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
            _state.value = PlaybackState.PLAYING
            val result =
                withTimeoutOrNull(DEVICE_WATCHDOG_MS) {
                    deviceTts.speak(text, gender, rate)
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

        /** Set the terminal state and, if [advance], emit a completion so the turn progresses. */
        private fun finish(
            token: Long,
            state: PlaybackState,
            advance: Boolean,
        ) {
            if (token != sessionToken) return
            _state.value = state
            if (advance) _completions.tryEmit(Unit)
        }

        companion object {
            // Client watchdogs (tts.md §4). Constants for now; a config override is a follow-up seam.
            const val SERVER_WATCHDOG_MS = 8_000L
            const val DEVICE_WATCHDOG_MS = 7_000L
        }
    }

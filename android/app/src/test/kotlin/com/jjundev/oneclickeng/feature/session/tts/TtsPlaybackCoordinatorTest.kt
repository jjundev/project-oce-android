package com.jjundev.oneclickeng.feature.session.tts

import com.jjundev.oneclickeng.core.audio.PcmPlayer
import com.jjundev.oneclickeng.core.audio.TtsSpeedCalibration
import com.jjundev.oneclickeng.core.network.LlmApi
import com.jjundev.oneclickeng.core.network.SpeakingRequest
import com.jjundev.oneclickeng.core.network.SpeakingResponse
import com.jjundev.oneclickeng.core.network.TtsRequest
import com.jjundev.oneclickeng.core.network.TtsResponse
import com.jjundev.oneclickeng.core.settings.TtsQuality
import com.jjundev.oneclickeng.core.settings.TtsSettings
import com.jjundev.oneclickeng.core.settings.TtsSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

private val PCM_BYTES = byteArrayOf(1, 2, 3, 4, 5, 6)

private fun okResponse(rate: Int = 24000) =
    TtsResponse(
        pcmBase64 = Base64.getEncoder().encodeToString(PCM_BYTES),
        sampleRate = rate,
        mimeType = "audio/L16;rate=$rate",
    )

private class FakeLlmApi(
    var response: TtsResponse = okResponse(),
    var error: Throwable? = null,
    var delayMs: Long = 0,
    // first N calls throw — models a cold call the server aborts (it still preheats)
    var failFirst: Int = 0,
) : LlmApi {
    var callCount = 0

    /** 마지막으로 나간 tts 요청 — 와이어에 실린 speechRate 단언용. */
    var lastRequest: TtsRequest? = null

    @Suppress("TooGenericExceptionThrown") // models an opaque server abort — real callers see no typed cause either.
    override suspend fun tts(body: TtsRequest): TtsResponse {
        callCount++
        lastRequest = body
        if (delayMs > 0) delay(delayMs)
        if (callCount <= failFirst) throw RuntimeException("cold synth aborted by server")
        error?.let { throw it }
        return response
    }

    override suspend fun speaking(body: SpeakingRequest): SpeakingResponse = error("unused")
}

private class FakePcmPlayer(var throwOnPlay: Boolean = false) : PcmPlayer {
    val played = mutableListOf<Pair<ByteArray, Int>>()

    /** 재생 호출별 배속(= [played] 와 같은 인덱스). Task 3 의 보정 단언이 쓴다. */
    val speeds = mutableListOf<Float>()

    override suspend fun play(
        pcm: ByteArray,
        sampleRateHz: Int,
        speed: Float,
    ) {
        if (throwOnPlay) error("playback boom")
        played += pcm to sampleRateHz
        speeds += speed
    }

    override fun stop() = Unit
}

private class FakeDeviceTts(
    var result: DeviceTtsResult = DeviceTtsResult.COMPLETED,
    var delayMs: Long = 0,
) : DeviceTts {
    var callCount = 0

    /** 엔진에 실제로 넘어간 배속 — 보정 단언용. */
    var lastRate: Float? = null

    override suspend fun speak(
        text: String,
        gender: String?,
        speechRate: Float,
        onStart: () -> Unit,
    ): DeviceTtsResult {
        callCount++
        lastRate = speechRate
        if (delayMs > 0) delay(delayMs)
        // Faithful to AndroidDeviceTts: onStart fires only when audio actually begins — i.e. the
        // engine initialized and the utterance started. LANGUAGE_MISSING / ERROR return before that.
        if (result == DeviceTtsResult.COMPLETED) onStart()
        return result
    }

    override fun stop() = Unit
}

// SERVER by default: most of this suite exercises the server synthesis/cache/prefetch/warm-up
// path, independent of production's TtsSettings() default (DEVICE — a product choice unrelated
// to what these tests need to exercise; tests that want DEVICE pass it explicitly).
private class FakeSettings(var value: TtsSettings = TtsSettings(quality = TtsQuality.SERVER)) : TtsSettingsRepository {
    override val settings: Flow<TtsSettings> = flowOf(value)

    override suspend fun current(): TtsSettings = value

    override suspend fun setQuality(quality: com.jjundev.oneclickeng.core.settings.TtsQuality) {
        value = value.copy(quality = quality)
    }

    override suspend fun setSpeechRate(rate: Float) {
        value = value.copy(speechRate = rate)
    }

    override suspend fun setMuted(muted: Boolean) {
        value = value.copy(muted = muted)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass") // one coordinator, one exhaustive scenario-per-test suite (repo convention for this class).
class TtsPlaybackCoordinatorTest {
    @Test
    fun `server path synthesizes decodes and plays at server sample rate`() =
        runTest {
            val api = FakeLlmApi(response = okResponse(rate = 16000))
            val player = FakePcmPlayer()
            val device = FakeDeviceTts()
            val coordinator = TtsPlaybackCoordinator(api, player, device, FakeSettings(), coordScope())

            val completions = collectCompletions(coordinator)
            coordinator.playTurn("Hello", "female")
            advanceUntilIdle()

            assertEquals(1, api.callCount)
            assertEquals(0, device.callCount)
            assertEquals(1, player.played.size)
            assertTrue(PCM_BYTES.contentEquals(player.played[0].first))
            assertEquals(16000, player.played[0].second) // honors server rate, not a 24k default
            assertEquals(PlaybackState.IDLE, coordinator.state.value)
            assertEquals(1, completions.size)
        }

    @Test
    fun `mute skips synthesis and playback but still advances`() =
        runTest {
            val api = FakeLlmApi()
            val player = FakePcmPlayer()
            val coordinator =
                TtsPlaybackCoordinator(
                    api,
                    player,
                    FakeDeviceTts(),
                    FakeSettings(TtsSettings(muted = true)),
                    coordScope(),
                )

            val completions = collectCompletions(coordinator)
            coordinator.playTurn("Hello", "male")
            advanceUntilIdle()

            assertEquals(0, api.callCount)
            assertTrue(player.played.isEmpty())
            assertEquals(1, completions.size)
            assertEquals(PlaybackState.IDLE, coordinator.state.value)
        }

    @Test
    fun `server watchdog timeout falls back to device tts`() =
        runTest {
            val api = FakeLlmApi(delayMs = 1_000_000) // never returns before the 8s watchdog
            val device = FakeDeviceTts(result = DeviceTtsResult.COMPLETED)
            val coordinator =
                TtsPlaybackCoordinator(api, FakePcmPlayer(), device, FakeSettings(), coordScope())

            val completions = collectCompletions(coordinator)
            coordinator.playTurn("Hello", "female")
            advanceUntilIdle()

            assertEquals(1, device.callCount) // fell back after watchdog
            assertEquals(1, completions.size)
            assertEquals(PlaybackState.IDLE, coordinator.state.value)
        }

    @Test
    fun `device language-missing degrades to text-only without advancing`() =
        runTest {
            val api = FakeLlmApi(error = RuntimeException("server down"))
            val device = FakeDeviceTts(result = DeviceTtsResult.LANGUAGE_MISSING)
            val coordinator =
                TtsPlaybackCoordinator(api, FakePcmPlayer(), device, FakeSettings(), coordScope())

            val completions = collectCompletions(coordinator)
            coordinator.playTurn("Hello", "female")
            advanceUntilIdle()

            assertEquals(1, device.callCount)
            assertEquals(PlaybackState.ERROR_TEXT_ONLY, coordinator.state.value)
            assertTrue(completions.isEmpty()) // no auto-advance — awaits retry
        }

    @Test
    fun `generic device error fails and advances`() =
        runTest {
            val device = FakeDeviceTts(result = DeviceTtsResult.ERROR)
            val coordinator =
                TtsPlaybackCoordinator(
                    FakeLlmApi(error = RuntimeException("x")),
                    FakePcmPlayer(),
                    device,
                    FakeSettings(TtsSettings(quality = TtsQuality.DEVICE)),
                    coordScope(),
                )

            val completions = collectCompletions(coordinator)
            coordinator.playTurn("Hello", null)
            advanceUntilIdle()

            assertEquals(PlaybackState.FAILED, coordinator.state.value)
            assertEquals(1, completions.size)
        }

    @Test
    fun `replay reuses retained pcm without re-synthesizing`() =
        runTest {
            val api = FakeLlmApi()
            val player = FakePcmPlayer()
            val coordinator =
                TtsPlaybackCoordinator(api, player, FakeDeviceTts(), FakeSettings(), coordScope())

            coordinator.playTurn("Hello", "female")
            advanceUntilIdle()
            coordinator.replay()
            advanceUntilIdle()

            assertEquals(1, api.callCount) // no second synthesis
            assertEquals(2, player.played.size) // played twice
            assertTrue(PCM_BYTES.contentEquals(player.played[1].first))
        }

    @Test
    fun `player failure maps to FAILED and advances`() =
        runTest {
            val coordinator =
                TtsPlaybackCoordinator(
                    FakeLlmApi(),
                    FakePcmPlayer(throwOnPlay = true),
                    FakeDeviceTts(),
                    FakeSettings(),
                    coordScope(),
                )

            val completions = collectCompletions(coordinator)
            coordinator.playTurn("Hello", "female")
            advanceUntilIdle()

            assertEquals(PlaybackState.FAILED, coordinator.state.value)
            assertEquals(1, completions.size)
        }

    @Test
    fun `stop resets to idle`() =
        runTest {
            val coordinator =
                TtsPlaybackCoordinator(
                    FakeLlmApi(delayMs = 1_000_000),
                    FakePcmPlayer(),
                    FakeDeviceTts(),
                    FakeSettings(),
                    coordScope(),
                )
            coordinator.playTurn("Hello", "female")
            runCurrent()
            coordinator.stop()
            advanceUntilIdle()
            assertEquals(PlaybackState.IDLE, coordinator.state.value)
        }

    @Test
    fun `deviceOnly skips server synthesis even when quality is SERVER`() =
        runTest {
            val api = FakeLlmApi() // FakeSettings() 기본 quality = SERVER
            val device = FakeDeviceTts(result = DeviceTtsResult.COMPLETED)
            val coordinator =
                TtsPlaybackCoordinator(api, FakePcmPlayer(), device, FakeSettings(), coordScope())

            val completions = collectCompletions(coordinator)
            coordinator.playTurn("Hello", null, deviceOnly = true)
            advanceUntilIdle()

            assertEquals(0, api.callCount) // 서버 합성 미호출
            assertEquals(1, device.callCount) // 곧장 디바이스 TTS
            assertEquals(1, completions.size) // 정상 종료 → 자동진행
            assertEquals(PlaybackState.IDLE, coordinator.state.value)
        }

    @Test
    fun `advanceOnDone false suppresses the completion so replay never advances`() =
        runTest {
            val device = FakeDeviceTts(result = DeviceTtsResult.COMPLETED)
            val coordinator =
                TtsPlaybackCoordinator(
                    FakeLlmApi(),
                    FakePcmPlayer(),
                    device,
                    FakeSettings(),
                    coordScope(),
                )

            val completions = collectCompletions(coordinator)
            coordinator.playTurn("Hello", null, deviceOnly = true, advanceOnDone = false)
            advanceUntilIdle()

            assertEquals(1, device.callCount) // 발화는 정상 재생
            assertTrue(completions.isEmpty()) // 그러나 자동진행 신호는 없음
            assertEquals(PlaybackState.IDLE, coordinator.state.value)
        }

    @Test
    fun `playClip plays given pcm at its rate without advancing`() =
        runTest {
            val player = FakePcmPlayer()
            val coordinator =
                TtsPlaybackCoordinator(FakeLlmApi(), player, FakeDeviceTts(), FakeSettings(), coordScope())

            val completions = collectCompletions(coordinator)
            val clip = byteArrayOf(9, 8, 7, 6)
            coordinator.playClip(clip, 16000)
            advanceUntilIdle()

            assertEquals(1, player.played.size)
            assertTrue(clip.contentEquals(player.played[0].first))
            assertEquals(16000, player.played[0].second) // honors the recording's own rate
            assertTrue(completions.isEmpty()) // 자기 녹음 재생은 턴을 전진시키지 않는다
            assertEquals(PlaybackState.IDLE, coordinator.state.value)
        }

    @Test
    fun `playClip is a silent no-op when muted`() =
        runTest {
            val player = FakePcmPlayer()
            val coordinator =
                TtsPlaybackCoordinator(
                    FakeLlmApi(),
                    player,
                    FakeDeviceTts(),
                    FakeSettings(TtsSettings(muted = true)),
                    coordScope(),
                )

            coordinator.playClip(byteArrayOf(1, 2, 3), 16000)
            advanceUntilIdle()

            assertTrue(player.played.isEmpty())
            assertEquals(PlaybackState.IDLE, coordinator.state.value)
        }

    @Test
    fun `second playTurn of same line reuses cache without a second synthesis`() =
        runTest {
            val api = FakeLlmApi(response = okResponse(rate = 24000))
            val player = FakePcmPlayer()
            val coordinator = TtsPlaybackCoordinator(api, player, FakeDeviceTts(), FakeSettings(), coordScope())

            coordinator.playTurn("Hello", "female")
            advanceUntilIdle()
            coordinator.playTurn("Hello", "female")
            advanceUntilIdle()

            assertEquals(1, api.callCount) // synthesized once, second play served from cache
            assertEquals(2, player.played.size) // but played both times
            assertTrue(PCM_BYTES.contentEquals(player.played[1].first))
        }

    @Test
    fun `second playTurn of the same line joins an in-flight synthesis`() =
        runTest {
            val api = FakeLlmApi(delayMs = 1_000_000) // keep the first synthesis in flight
            val player = FakePcmPlayer()
            val coordinator = TtsPlaybackCoordinator(api, player, FakeDeviceTts(), FakeSettings(), coordScope())

            coordinator.playTurn("Hello", "female")
            runCurrent() // first synthesis registers inFlight and suspends inside api.tts
            coordinator.playTurn("Hello", "female") // same line arrives before the first resolves;
            // its startNewSession() cancels the first awaiter, but the sibling synthesis survives.
            advanceUntilIdle()

            assertEquals(1, api.callCount) // joined the in-flight synthesis; the cancelled awaiter did NOT evict it
        }

    @Test
    fun `replay path advanceOnDone false of a cached line makes no server call`() =
        runTest {
            val api = FakeLlmApi()
            val player = FakePcmPlayer()
            val coordinator = TtsPlaybackCoordinator(api, player, FakeDeviceTts(), FakeSettings(), coordScope())
            val completions = collectCompletions(coordinator)

            coordinator.playTurn("Hi", "male") // auto-speak: synthesizes + caches
            advanceUntilIdle()
            coordinator.playTurn("Hi", "male", advanceOnDone = false) // "다시 듣기"
            advanceUntilIdle()

            assertEquals(1, api.callCount) // replay served from cache, no re-synthesis
            assertEquals(2, player.played.size)
            assertEquals(1, completions.size) // only the auto-speak advanced; replay did not
        }

    @Test
    fun `a rate change reuses the cached synthesis instead of re-synthesizing`() =
        runTest {
            val api = FakeLlmApi()
            val settings = FakeSettings(TtsSettings(quality = TtsQuality.SERVER, speechRate = 1.0f))
            val player = FakePcmPlayer()
            val coordinator = TtsPlaybackCoordinator(api, player, FakeDeviceTts(), settings, coordScope())

            coordinator.playTurn("Hello", null)
            advanceUntilIdle()
            settings.setSpeechRate(1.5f)
            coordinator.playTurn("Hello", null)
            advanceUntilIdle()

            // 서버 오디오는 이제 중립으로 합성되고 배속은 재생 시점에 걸린다 → 키에 rate 가 없다.
            assertEquals(1, api.callCount)
            // 그래도 두 번째 재생은 새 속도로 나가야 한다.
            assertEquals(
                TtsSpeedCalibration.serverPlaybackSpeed(1.0f, null),
                player.speeds[0],
                SPEED_TOLERANCE,
            )
            assertEquals(
                TtsSpeedCalibration.serverPlaybackSpeed(1.5f, null),
                player.speeds[1],
                SPEED_TOLERANCE,
            )
        }

    @Test
    fun `server synthesis always requests the neutral rate whatever the slider says`() =
        runTest {
            val api = FakeLlmApi()
            val settings = FakeSettings(TtsSettings(quality = TtsQuality.SERVER, speechRate = 1.4f))
            val coordinator = TtsPlaybackCoordinator(api, FakePcmPlayer(), FakeDeviceTts(), settings, coordScope())

            coordinator.playTurn("Hello", "male")
            advanceUntilIdle()

            // Gemini 에 속도를 부탁하지 않는다 — 힌트는 늘 중립이고 배속은 클라가 건다.
            assertEquals(
                TtsPlaybackCoordinator.NEUTRAL_SYNTHESIS_RATE,
                api.lastRequest!!.payload.speechRate,
                SPEED_TOLERANCE,
            )
        }

    @Test
    fun `server playback applies the voice-specific calibration weight`() =
        runTest {
            val player = FakePcmPlayer()
            val settings = FakeSettings(TtsSettings(quality = TtsQuality.SERVER, speechRate = 1.2f))
            val coordinator = TtsPlaybackCoordinator(FakeLlmApi(), player, FakeDeviceTts(), settings, coordScope())

            coordinator.playTurn("Hello", "male")
            advanceUntilIdle()

            assertEquals(
                TtsSpeedCalibration.serverPlaybackSpeed(1.2f, "male"),
                player.speeds.single(),
                SPEED_TOLERANCE,
            )
        }

    @Test
    fun `device path applies the device calibration weight`() =
        runTest {
            val device = FakeDeviceTts()
            val settings = FakeSettings(TtsSettings(quality = TtsQuality.DEVICE, speechRate = 1.2f))
            val coordinator = TtsPlaybackCoordinator(FakeLlmApi(), FakePcmPlayer(), device, settings, coordScope())

            coordinator.playTurn("Hello", "male")
            advanceUntilIdle()

            assertEquals(TtsSpeedCalibration.deviceSpeechRate(1.2f, "male"), device.lastRate!!, SPEED_TOLERANCE)
        }

    @Test
    fun `replay honors a speed changed after the turn played`() =
        runTest {
            val player = FakePcmPlayer()
            val settings = FakeSettings(TtsSettings(quality = TtsQuality.SERVER, speechRate = 1.0f))
            val coordinator = TtsPlaybackCoordinator(FakeLlmApi(), player, FakeDeviceTts(), settings, coordScope())

            coordinator.playTurn("Hello", "male")
            advanceUntilIdle()
            settings.setSpeechRate(1.5f)
            coordinator.replay()
            advanceUntilIdle()

            // replay 는 캐시된 중립 PCM 을 *현재* 설정으로 다시 배속한다(마지막 턴의 성별 계수로).
            assertEquals(
                TtsSpeedCalibration.serverPlaybackSpeed(1.5f, "male"),
                player.speeds.last(),
                SPEED_TOLERANCE,
            )
        }

    @Test
    fun `self clip plays at unmodified speed`() =
        runTest {
            val player = FakePcmPlayer()
            val settings = FakeSettings(TtsSettings(quality = TtsQuality.SERVER, speechRate = 1.5f))
            val coordinator = TtsPlaybackCoordinator(FakeLlmApi(), player, FakeDeviceTts(), settings, coordScope())

            coordinator.playClip(byteArrayOf(9, 9), 16000)
            advanceUntilIdle()

            // 학습자 자기 녹음은 보정 대상이 아니다 — 자기 목소리를 왜곡하지 않는다.
            assertEquals(1.0f, player.speeds.single(), SPEED_TOLERANCE)
        }

    @Test
    fun `prefetch warms cache so the later playTurn makes no server call`() =
        runTest {
            val api = FakeLlmApi()
            val player = FakePcmPlayer()
            val coordinator =
                TtsPlaybackCoordinator(api, player, FakeDeviceTts(), FakeSettings(), coordScope())

            coordinator.prefetch("Ahead", "female")
            advanceUntilIdle()
            assertEquals(1, api.callCount) // synthesized in the background
            assertEquals(0, player.played.size) // but not played

            coordinator.playTurn("Ahead", "female")
            advanceUntilIdle()
            assertEquals(1, api.callCount) // served from the warmed cache — no second call
            assertEquals(1, player.played.size)
        }

    @Test
    fun `prefetch is a no-op in DEVICE quality and when muted`() =
        runTest {
            val deviceApi = FakeLlmApi()
            val deviceCoord =
                TtsPlaybackCoordinator(
                    deviceApi,
                    FakePcmPlayer(),
                    FakeDeviceTts(),
                    FakeSettings(TtsSettings(quality = TtsQuality.DEVICE)),
                    coordScope(),
                )
            deviceCoord.prefetch("x", null)
            advanceUntilIdle()
            assertEquals(0, deviceApi.callCount)

            val mutedApi = FakeLlmApi()
            val mutedCoord =
                TtsPlaybackCoordinator(
                    mutedApi,
                    FakePcmPlayer(),
                    FakeDeviceTts(),
                    FakeSettings(TtsSettings(muted = true)),
                    coordScope(),
                )
            mutedCoord.prefetch("x", null)
            advanceUntilIdle()
            assertEquals(0, mutedApi.callCount)
        }

    @Test
    fun `duplicate prefetch of the same line dedups to one synthesis`() =
        runTest {
            val api = FakeLlmApi(delayMs = 50) // keep the first in flight while the second arrives
            val coordinator =
                TtsPlaybackCoordinator(api, FakePcmPlayer(), FakeDeviceTts(), FakeSettings(), coordScope())

            coordinator.prefetch("Dup", null)
            coordinator.prefetch("Dup", null)
            advanceUntilIdle()

            assertEquals(1, api.callCount)
        }

    @Test
    fun `prefetch does not disturb playback state`() =
        runTest {
            val coordinator =
                TtsPlaybackCoordinator(FakeLlmApi(), FakePcmPlayer(), FakeDeviceTts(), FakeSettings(), coordScope())
            val completions = collectCompletions(coordinator)
            val audioReady = collectAudioReady(coordinator)

            coordinator.prefetch("Bg", null)
            advanceUntilIdle()

            assertEquals(PlaybackState.IDLE, coordinator.state.value) // never left IDLE
            assertEquals(0, completions.size)
            assertEquals(0, audioReady.size)
        }

    @Test
    fun `clearCache forces the next playTurn to re-synthesize`() =
        runTest {
            val api = FakeLlmApi()
            val coordinator =
                TtsPlaybackCoordinator(api, FakePcmPlayer(), FakeDeviceTts(), FakeSettings(), coordScope())

            coordinator.prefetch("Gone", null)
            advanceUntilIdle()
            coordinator.clearCache()
            coordinator.playTurn("Gone", null)
            advanceUntilIdle()

            assertEquals(2, api.callCount) // cache cleared → fresh synthesis
        }

    @Test
    fun `playTurn joins an in-flight prefetch instead of re-synthesizing`() =
        runTest {
            val api = FakeLlmApi(delayMs = 100) // keep the synthesis in flight
            val player = FakePcmPlayer()
            val coordinator =
                TtsPlaybackCoordinator(api, player, FakeDeviceTts(), FakeSettings(), coordScope())

            coordinator.prefetch("Join", null) // starts synthesis (Unconfined runs it to the delay)
            coordinator.playTurn("Join", null) // same line arrives before synthesis resolves → joins it
            advanceUntilIdle()

            assertEquals(1, api.callCount) // live play joined the in-flight synthesis — no 2nd call
            assertEquals(1, player.played.size)
        }

    @Test
    fun `awaitWarm synthesizes and caches so a later playTurn makes no call`() =
        runTest {
            val api = FakeLlmApi()
            val player = FakePcmPlayer()
            val coordinator =
                TtsPlaybackCoordinator(api, player, FakeDeviceTts(), FakeSettings(), coordScope())

            assertTrue(coordinator.awaitWarm("Warm", "male"))
            advanceUntilIdle()
            assertEquals(1, api.callCount)

            coordinator.playTurn("Warm", "male")
            advanceUntilIdle()
            assertEquals(1, api.callCount) // played from the warmed cache — no 2nd synthesis
            assertEquals(1, player.played.size)
        }

    @Test
    fun `awaitWarm returns true immediately without synthesis in DEVICE quality`() =
        runTest {
            val api = FakeLlmApi()
            val coordinator =
                TtsPlaybackCoordinator(
                    api,
                    FakePcmPlayer(),
                    FakeDeviceTts(),
                    FakeSettings(TtsSettings(quality = TtsQuality.DEVICE)),
                    coordScope(),
                )

            assertTrue(coordinator.awaitWarm("x", null)) // nothing to warm on the device path
            advanceUntilIdle()
            assertEquals(0, api.callCount)
        }

    @Test
    fun `awaitWarm returns true immediately when muted`() =
        runTest {
            val api = FakeLlmApi()
            val coordinator =
                TtsPlaybackCoordinator(
                    api,
                    FakePcmPlayer(),
                    FakeDeviceTts(),
                    FakeSettings(TtsSettings(muted = true)),
                    coordScope(),
                )

            assertTrue(coordinator.awaitWarm("x", null))
            advanceUntilIdle()
            assertEquals(0, api.callCount)
        }

    @Test
    fun `warmUpModel preheats with a throwaway synthesis that is never played`() =
        runTest {
            val api = FakeLlmApi()
            val player = FakePcmPlayer()
            val coordinator =
                TtsPlaybackCoordinator(api, player, FakeDeviceTts(), FakeSettings(), coordScope())
            val completions = collectCompletions(coordinator)

            coordinator.warmUpModel()
            advanceUntilIdle()

            assertEquals(1, api.callCount) // the preheat call fired
            assertTrue(player.played.isEmpty()) // but nothing was played
            assertEquals(PlaybackState.IDLE, coordinator.state.value) // and playback state is untouched
            assertTrue(completions.isEmpty())
        }

    @Test
    fun `warmUpModel is a no-op in DEVICE quality and when muted`() =
        runTest {
            val deviceApi = FakeLlmApi()
            TtsPlaybackCoordinator(
                deviceApi,
                FakePcmPlayer(),
                FakeDeviceTts(),
                FakeSettings(TtsSettings(quality = TtsQuality.DEVICE)),
                coordScope(),
            ).warmUpModel()
            advanceUntilIdle()
            assertEquals(0, deviceApi.callCount)

            val mutedApi = FakeLlmApi()
            TtsPlaybackCoordinator(
                mutedApi,
                FakePcmPlayer(),
                FakeDeviceTts(),
                FakeSettings(TtsSettings(muted = true)),
                coordScope(),
            ).warmUpModel()
            advanceUntilIdle()
            assertEquals(0, mutedApi.callCount)
        }

    @Test
    fun `warmUpModel does not stack while one is already in flight`() =
        runTest {
            val api = FakeLlmApi(delayMs = 1_000) // keep the first preheat in flight
            val coordinator =
                TtsPlaybackCoordinator(api, FakePcmPlayer(), FakeDeviceTts(), FakeSettings(), coordScope())

            coordinator.warmUpModel()
            coordinator.warmUpModel() // a second foreground before the first finished
            advanceUntilIdle()

            assertEquals(1, api.callCount)
        }

    @Test
    fun `warmUpModel does not occupy the cache`() =
        runTest {
            // The throwaway line must not take a slot in the CACHE_CAP-bounded cache, so a later
            // real play of the same text still synthesizes.
            val api = FakeLlmApi()
            val coordinator =
                TtsPlaybackCoordinator(api, FakePcmPlayer(), FakeDeviceTts(), FakeSettings(), coordScope())

            coordinator.warmUpModel()
            advanceUntilIdle()
            assertEquals(1, api.callCount)

            coordinator.playTurn(TtsPlaybackCoordinator.WARM_UP_TEXT, null)
            advanceUntilIdle()
            assertEquals(2, api.callCount) // not served from cache — the preheat was discarded
        }

    @Test
    fun `awaitWarm retries once after a cold failure and caches the retry`() =
        runTest {
            val api = FakeLlmApi(failFirst = 1) // cold attempt aborts; the retry lands
            val player = FakePcmPlayer()
            val coordinator =
                TtsPlaybackCoordinator(api, player, FakeDeviceTts(), FakeSettings(), coordScope())

            assertTrue(coordinator.awaitWarm("Line one", "female"))
            advanceUntilIdle()
            assertEquals(2, api.callCount) // cold attempt + successful retry

            coordinator.playTurn("Line one", "female")
            advanceUntilIdle()
            assertEquals(2, api.callCount) // the retry's audio was cached → live play is a hit
            assertEquals(1, player.played.size)
        }

    @Test
    fun `awaitWarm gives up after exactly one retry`() =
        runTest {
            val api = FakeLlmApi(failFirst = 2) // both attempts fail
            val coordinator =
                TtsPlaybackCoordinator(api, FakePcmPlayer(), FakeDeviceTts(), FakeSettings(), coordScope())

            assertFalse(coordinator.awaitWarm("Line one", null))
            advanceUntilIdle()
            assertEquals(2, api.callCount) // one retry only — not a loop
        }

    /** Coordinator scope on an unconfined dispatcher tied to the test scheduler, so
     *  launches run eagerly while virtual time still drives the watchdogs. */
    private fun TestScope.coordScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    /** Subscribe to completions before the action so the SharedFlow (replay=0) delivers.
     *  An unconfined collector subscribes eagerly and receives emissions synchronously. */
    private fun TestScope.collectCompletions(coordinator: TtsPlaybackCoordinator): List<Unit> {
        val received = mutableListOf<Unit>()
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)).launch {
            coordinator.completions.collect { received += it }
        }
        return received
    }

    /** Subscribe to audioReady before the action so the SharedFlow (replay=0) delivers. */
    private fun TestScope.collectAudioReady(coordinator: TtsPlaybackCoordinator): List<Unit> {
        val received = mutableListOf<Unit>()
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)).launch {
            coordinator.audioReady.collect { received += it }
        }
        return received
    }

    @Test
    fun `audioReady emits when the server path starts playing`() =
        runTest {
            val coordinator =
                TtsPlaybackCoordinator(
                    FakeLlmApi(),
                    FakePcmPlayer(),
                    FakeDeviceTts(),
                    FakeSettings(),
                    coordScope(),
                )

            val ready = collectAudioReady(coordinator)
            coordinator.playTurn("Hello", "female")
            advanceUntilIdle()

            assertEquals(1, ready.size)
        }

    @Test
    fun `audioReady emits when the device path starts playing`() =
        runTest {
            val coordinator =
                TtsPlaybackCoordinator(
                    FakeLlmApi(),
                    FakePcmPlayer(),
                    FakeDeviceTts(result = DeviceTtsResult.COMPLETED),
                    FakeSettings(),
                    coordScope(),
                )

            val ready = collectAudioReady(coordinator)
            coordinator.playTurn("Hello", null, deviceOnly = true)
            advanceUntilIdle()

            assertEquals(1, ready.size)
        }

    @Test
    fun `audioReady does not emit when muted`() =
        runTest {
            val coordinator =
                TtsPlaybackCoordinator(
                    FakeLlmApi(),
                    FakePcmPlayer(),
                    FakeDeviceTts(),
                    FakeSettings(TtsSettings(muted = true)),
                    coordScope(),
                )

            val ready = collectAudioReady(coordinator)
            coordinator.playTurn("Hello", "female", deviceOnly = true)
            advanceUntilIdle()

            assertTrue(ready.isEmpty()) // muted → no audio; reveal comes from the completions path
        }

    @Test
    fun `audioReady does not emit when the device has no english voice`() =
        runTest {
            val coordinator =
                TtsPlaybackCoordinator(
                    FakeLlmApi(error = RuntimeException("server down")),
                    FakePcmPlayer(),
                    FakeDeviceTts(result = DeviceTtsResult.LANGUAGE_MISSING),
                    FakeSettings(),
                    coordScope(),
                )

            val ready = collectAudioReady(coordinator)
            coordinator.playTurn("Hello", "female", deviceOnly = true)
            advanceUntilIdle()

            assertTrue(ready.isEmpty()) // reveal comes from the ERROR_TEXT_ONLY safety net, not audioReady
        }

    @Test
    fun `prefetch tolerates a cold synthesis longer than the live watchdog and caches it`() =
        runTest {
            // 12s synth: exceeds the 8s live-playback bound but is within the 16s synthesis budget.
            val api = FakeLlmApi(delayMs = 12_000)
            val player = FakePcmPlayer()
            val coordinator =
                TtsPlaybackCoordinator(api, player, FakeDeviceTts(), FakeSettings(), coordScope())

            coordinator.prefetch("Cold", "female")
            advanceUntilIdle()
            assertEquals(1, api.callCount) // the cold synthesis completed (not killed at 8s)

            coordinator.playTurn("Cold", "female")
            advanceUntilIdle()
            assertEquals(1, api.callCount) // served from the warmed cache — no re-synthesis
            assertEquals(1, player.played.size)
        }

    @Test
    fun `live playTurn falls back to device at 8s while the cold synthesis keeps caching`() =
        runTest {
            val api = FakeLlmApi(delayMs = 12_000) // > 8s live bound, < 16s synth budget
            val device = FakeDeviceTts(result = DeviceTtsResult.COMPLETED)
            val player = FakePcmPlayer()
            val coordinator =
                TtsPlaybackCoordinator(api, player, device, FakeSettings(), coordScope())

            coordinator.playTurn("Cold", "female") // no prefetch: live path starts the synthesis
            advanceUntilIdle()
            assertEquals(1, device.callCount) // fell back to device at the 8s bound
            assertEquals(1, api.callCount) // one synthesis was started

            // The synthesis kept running past the 8s live bound and cached; a later play is a hit.
            coordinator.playTurn("Cold", "female")
            advanceUntilIdle()
            assertEquals(1, api.callCount) // no second synthesis — cache hit
            assertEquals(1, player.played.size) // and it played from the server cache this time
        }

    @Test
    fun `device path stays LOADING with no audioReady until the engine reports onStart`() =
        runTest {
            val device = FakeDeviceTts(result = DeviceTtsResult.COMPLETED, delayMs = 5_000)
            val coordinator =
                TtsPlaybackCoordinator(FakeLlmApi(), FakePcmPlayer(), device, FakeSettings(), coordScope())

            val ready = collectAudioReady(coordinator)
            coordinator.playTurn("Hello", null, deviceOnly = true)
            runCurrent() // playTurn sets LOADING and suspends inside deviceTts.speak's delay (pre-onStart)

            assertEquals(PlaybackState.LOADING, coordinator.state.value) // not prematurely PLAYING
            assertTrue(ready.isEmpty()) // audioReady must wait for onStart, not fire before speak

            advanceUntilIdle() // delay elapses → onStart → PLAYING + audioReady → finish(IDLE)

            assertEquals(1, ready.size)
            assertEquals(PlaybackState.IDLE, coordinator.state.value)
        }

    private companion object {
        const val SPEED_TOLERANCE = 1e-4f
    }
}

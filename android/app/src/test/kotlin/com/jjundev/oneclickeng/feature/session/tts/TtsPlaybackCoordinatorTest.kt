package com.jjundev.oneclickeng.feature.session.tts

import com.jjundev.oneclickeng.core.audio.PcmPlayer
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
) : LlmApi {
    var callCount = 0

    override suspend fun tts(body: TtsRequest): TtsResponse {
        callCount++
        if (delayMs > 0) delay(delayMs)
        error?.let { throw it }
        return response
    }

    override suspend fun speaking(body: SpeakingRequest): SpeakingResponse = error("unused")
}

private class FakePcmPlayer(var throwOnPlay: Boolean = false) : PcmPlayer {
    val played = mutableListOf<Pair<ByteArray, Int>>()

    override suspend fun play(
        pcm: ByteArray,
        sampleRateHz: Int,
    ) {
        if (throwOnPlay) error("playback boom")
        played += pcm to sampleRateHz
    }

    override fun stop() = Unit
}

private class FakeDeviceTts(
    var result: DeviceTtsResult = DeviceTtsResult.COMPLETED,
    var delayMs: Long = 0,
) : DeviceTts {
    var callCount = 0

    override suspend fun speak(
        text: String,
        gender: String?,
        speechRate: Float,
    ): DeviceTtsResult {
        callCount++
        if (delayMs > 0) delay(delayMs)
        return result
    }

    override fun stop() = Unit
}

private class FakeSettings(var value: TtsSettings = TtsSettings()) : TtsSettingsRepository {
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
}

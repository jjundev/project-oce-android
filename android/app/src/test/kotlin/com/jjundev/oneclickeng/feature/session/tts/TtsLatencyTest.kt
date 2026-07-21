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
import com.jjundev.oneclickeng.core.time.FakeElapsedClock
import com.jjundev.oneclickeng.feature.session.analytics.LatencyAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.RecordingLatencyAnalytics
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
import org.junit.Test
import java.io.IOException
import java.util.Base64

private val PCM_BYTES = byteArrayOf(1, 2, 3, 4)

private fun okResponse() =
    TtsResponse(
        pcmBase64 = Base64.getEncoder().encodeToString(PCM_BYTES),
        sampleRate = 24000,
        mimeType = "audio/L16;rate=24000",
    )

private class LatencyFakeLlmApi(
    var response: TtsResponse? = okResponse(),
    var delayMs: Long = 0,
) : LlmApi {
    var callCount = 0

    override suspend fun tts(body: TtsRequest): TtsResponse {
        callCount++
        if (delayMs > 0) delay(delayMs)
        return response ?: throw IOException("boom")
    }

    override suspend fun speaking(body: SpeakingRequest): SpeakingResponse = error("unused")
}

private class LatencyFakePcmPlayer : PcmPlayer {
    override suspend fun play(pcm: ByteArray, sampleRateHz: Int, speed: Float) = Unit
    override fun stop() = Unit
}

private class LatencyFakeDeviceTts : DeviceTts {
    override suspend fun speak(
        text: String,
        gender: String?,
        speechRate: Float,
        onStart: () -> Unit,
    ): DeviceTtsResult = DeviceTtsResult.COMPLETED

    override fun stop() = Unit
}

private class LatencyFakeTtsSettings(
    private val value: TtsSettings = TtsSettings(quality = TtsQuality.SERVER),
) : TtsSettingsRepository {
    override val settings: Flow<TtsSettings> = flowOf(value)
    override suspend fun current(): TtsSettings = value
    override suspend fun setQuality(quality: TtsQuality) = Unit
    override suspend fun setSpeechRate(rate: Float) = Unit
    override suspend fun setMuted(muted: Boolean) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class TtsLatencyTest {
    private fun TestScope.coordScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `prefetch success logs tts_latency_ms successful with exact elapsed`() =
        runTest {
            val api = LatencyFakeLlmApi(delayMs = 500)
            val clock = FakeElapsedClock(now = 10L)
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                TtsPlaybackCoordinator(
                    api,
                    LatencyFakePcmPlayer(),
                    LatencyFakeDeviceTts(),
                    LatencyFakeTtsSettings(),
                    coordScope(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            // Real in-flight gap (mirrors the M4-01f Task 3/4 pattern): the trigger runs first,
            // then the clock advances, then advanceUntilIdle() lets the delayed fake resolve.
            coordinator.prefetch("Hello", "male")
            runCurrent()
            clock.advance(500L)
            advanceUntilIdle()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("tts", LatencyAnalytics.OUTCOME_SUCCESSFUL, 500L)),
                latency.calls,
            )
        }

    @Test
    fun `network failure logs tts_latency_ms failed`() =
        runTest {
            val api = LatencyFakeLlmApi(response = null, delayMs = 500)
            val clock = FakeElapsedClock(now = 20L)
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                TtsPlaybackCoordinator(
                    api,
                    LatencyFakePcmPlayer(),
                    LatencyFakeDeviceTts(),
                    LatencyFakeTtsSettings(),
                    coordScope(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            coordinator.prefetch("Hello", "male")
            runCurrent()
            clock.advance(300L)
            advanceUntilIdle()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("tts", LatencyAnalytics.OUTCOME_FAILED, 300L)),
                latency.calls,
            )
        }

    @Test
    fun `second prefetch for an already-cached line does not log again`() =
        runTest {
            val api = LatencyFakeLlmApi()
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                TtsPlaybackCoordinator(
                    api,
                    LatencyFakePcmPlayer(),
                    LatencyFakeDeviceTts(),
                    LatencyFakeTtsSettings(),
                    coordScope(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            coordinator.prefetch("Hello", "male")
            advanceUntilIdle()
            assertEquals(1, latency.calls.size)

            coordinator.prefetch("Hello", "male") // now a cache hit — no network call, no new log
            advanceUntilIdle()

            assertEquals(1, latency.calls.size)
            assertEquals(1, api.callCount)
        }

    @Test
    fun `concurrent callers for the same line join a single synthesize call and a single log`() =
        runTest {
            val api = LatencyFakeLlmApi(delayMs = 1_000)
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                TtsPlaybackCoordinator(
                    api,
                    LatencyFakePcmPlayer(),
                    LatencyFakeDeviceTts(),
                    LatencyFakeTtsSettings(),
                    coordScope(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            coordinator.prefetch("Hello", "male")
            runCurrent() // the in-flight Deferred is created and suspended inside the 1s delay

            var warmResult = false
            launch { warmResult = coordinator.awaitWarm("Hello", "male") }
            runCurrent()

            advanceUntilIdle()

            assertEquals(1, api.callCount)
            assertEquals(1, latency.calls.size)
            assertEquals(true, warmResult)
        }

    @Test
    fun `warmUpModel does not log tts_latency_ms`() =
        runTest {
            val api = LatencyFakeLlmApi()
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                TtsPlaybackCoordinator(
                    api,
                    LatencyFakePcmPlayer(),
                    LatencyFakeDeviceTts(),
                    LatencyFakeTtsSettings(),
                    coordScope(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            coordinator.warmUpModel()
            advanceUntilIdle()

            assertEquals(1, api.callCount) // the preheat call did happen
            assertEquals(emptyList<RecordingLatencyAnalytics.Call>(), latency.calls) // but nothing logged
        }
}

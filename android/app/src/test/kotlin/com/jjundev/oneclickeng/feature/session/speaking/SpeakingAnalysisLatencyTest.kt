package com.jjundev.oneclickeng.feature.session.speaking

import com.jjundev.oneclickeng.core.audio.RecordingResult
import com.jjundev.oneclickeng.core.network.LlmApi
import com.jjundev.oneclickeng.core.network.SpeakingRequest
import com.jjundev.oneclickeng.core.network.SpeakingResponse
import com.jjundev.oneclickeng.core.network.TtsRequest
import com.jjundev.oneclickeng.core.network.TtsResponse
import com.jjundev.oneclickeng.core.time.FakeElapsedClock
import com.jjundev.oneclickeng.feature.session.analytics.LatencyAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.RecordingLatencyAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private val PCM = byteArrayOf(0, 1, 2, 3)

private fun captured() = RecordingResult.Captured(pcm = PCM, sampleRate = 16000, durationMs = 1000)

private class LatencyFakeLlmApi(
    var response: SpeakingResponse? = SpeakingResponse(transcript = "hello", feedbackMessage = "좋아요"),
    var delayMs: Long = 0,
) : LlmApi {
    override suspend fun tts(body: TtsRequest): TtsResponse = error("unused")

    override suspend fun speaking(body: SpeakingRequest): SpeakingResponse {
        if (delayMs > 0) delay(delayMs)
        return response ?: throw java.io.IOException("boom")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SpeakingAnalysisLatencyTest {
    private fun TestScope.coordScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `successful analysis logs speaking_analyze_latency_ms successful`() =
        runTest {
            val clock = FakeElapsedClock(now = 200L)
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                SpeakingAnalysisCoordinator(
                    LatencyFakeLlmApi(delayMs = 10_000),
                    coordScope(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            // Real in-flight gap (mirrors DialogueGenerationScriptGenLatencyTest): analyze() must be
            // called BEFORE the clock advances, so the elapsed reading spans the actual round trip
            // rather than being pre-baked before analyzeStartMs is even captured.
            coordinator.analyze(captured(), "s1")
            clock.advance(900L)
            advanceUntilIdle()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("speaking_analyze", LatencyAnalytics.OUTCOME_SUCCESSFUL, 900L)),
                latency.calls,
            )
        }

    @Test
    fun `network failure logs speaking_analyze_latency_ms failed`() =
        runTest {
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                SpeakingAnalysisCoordinator(
                    LatencyFakeLlmApi(response = null, delayMs = 10_000),
                    coordScope(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            coordinator.analyze(captured(), "s1")
            clock.advance(300L)
            advanceUntilIdle()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("speaking_analyze", LatencyAnalytics.OUTCOME_FAILED, 300L)),
                latency.calls,
            )
        }

    @Test
    fun `reset while analyzing logs speaking_analyze_latency_ms canceled`() =
        runTest {
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                SpeakingAnalysisCoordinator(
                    LatencyFakeLlmApi(delayMs = 10_000),
                    coordScope(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            coordinator.analyze(captured(), "s1")
            clock.advance(150L)
            coordinator.reset()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("speaking_analyze", LatencyAnalytics.OUTCOME_CANCELED, 150L)),
                latency.calls,
            )
        }
}

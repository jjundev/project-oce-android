package com.jjundev.oneclickeng.feature.session.speaking

import com.jjundev.oneclickeng.core.audio.RecordingResult
import com.jjundev.oneclickeng.core.audio.WavEncoder
import com.jjundev.oneclickeng.core.network.LlmApi
import com.jjundev.oneclickeng.core.network.SpeakingRequest
import com.jjundev.oneclickeng.core.network.SpeakingResponse
import com.jjundev.oneclickeng.core.network.TtsRequest
import com.jjundev.oneclickeng.core.network.TtsResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

private val PCM = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)

private fun captured() = RecordingResult.Captured(pcm = PCM, sampleRate = 16000, durationMs = 1200)

private fun result(
    transcript: String = "hello",
    feedback: String = "좋아요",
) = SpeakingResponse(transcript = transcript, feedbackMessage = feedback)

private class FakeLlmApi(
    var response: SpeakingResponse = result(),
    var error: Throwable? = null,
    var delayMs: Long = 0,
) : LlmApi {
    var callCount = 0
    val requests = mutableListOf<SpeakingRequest>()

    override suspend fun tts(body: TtsRequest): TtsResponse = error("unused")

    override suspend fun speaking(body: SpeakingRequest): SpeakingResponse {
        callCount++
        requests += body
        if (delayMs > 0) delay(delayMs)
        error?.let { throw it }
        return response
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SpeakingAnalysisCoordinatorTest {
    @Test
    fun `success surfaces Result, retains transcript, sends top-level sessionId`() =
        runTest {
            val api = FakeLlmApi(response = result("I went to school", "또렷했어요"))
            val coordinator = SpeakingAnalysisCoordinator(api, coordScope())

            coordinator.analyze(captured(), sessionId = "s1")
            advanceUntilIdle()

            val state = coordinator.state.value
            assertTrue(state is SpeakingAnalysisState.Result)
            state as SpeakingAnalysisState.Result
            assertEquals("I went to school", state.transcript)
            assertEquals("또렷했어요", state.encouragement)
            // transcript retained for M1-07 reuse
            assertEquals("I went to school", coordinator.transcript())
            // sessionId travels as a top-level envelope field, not inside payload
            assertEquals("s1", api.requests.single().sessionId)
            assertTrue(api.requests.single().payload.audioBase64.isNotEmpty())
        }

    @Test
    fun `sent audioBase64 is the base64 of the WAV-wrapped PCM (round-trip)`() =
        runTest {
            val api = FakeLlmApi()
            val coordinator = SpeakingAnalysisCoordinator(api, coordScope())

            coordinator.analyze(captured(), "s1")
            advanceUntilIdle()

            // Decode what the coordinator actually sent and confirm it reconstructs the
            // WAV-wrapped original PCM exactly (audio-pipeline correctness, not just non-empty).
            val decoded = Base64.getDecoder().decode(api.requests.single().payload.audioBase64)
            assertArrayEquals(WavEncoder.wrap(PCM, 16000), decoded)
            assertEquals(44 + PCM.size, decoded.size) // 44-byte RIFF/WAVE header + PCM payload
        }

    @Test
    fun `blank transcript maps to Empty and retains no transcript`() =
        runTest {
            val api = FakeLlmApi(response = result(transcript = "", feedback = "다시 해볼까요?"))
            val coordinator = SpeakingAnalysisCoordinator(api, coordScope())

            coordinator.analyze(captured(), "s1")
            advanceUntilIdle()

            assertEquals(SpeakingAnalysisState.Empty, coordinator.state.value)
            assertNull(coordinator.transcript())
        }

    @Test
    fun `network failure maps to Failed`() =
        runTest {
            val api = FakeLlmApi(error = RuntimeException("server down"))
            val coordinator = SpeakingAnalysisCoordinator(api, coordScope())

            coordinator.analyze(captured(), "s1")
            advanceUntilIdle()

            assertEquals(SpeakingAnalysisState.Failed, coordinator.state.value)
            assertNull(coordinator.transcript())
        }

    @Test
    fun `watchdog timeout maps to Failed`() =
        runTest {
            val api = FakeLlmApi(delayMs = 1_000_000) // never returns before the 15s watchdog
            val coordinator = SpeakingAnalysisCoordinator(api, coordScope())

            coordinator.analyze(captured(), "s1")
            advanceUntilIdle()

            assertEquals(SpeakingAnalysisState.Failed, coordinator.state.value)
        }

    @Test
    fun `stale response from a superseded turn is ignored`() =
        runTest {
            // First call is slow; a second analyze supersedes it. The first's late result
            // must not overwrite the second's state (monotonic token guard).
            val api = FakeLlmApi(response = result("second"), delayMs = 5_000)
            val coordinator = SpeakingAnalysisCoordinator(api, coordScope())

            coordinator.analyze(captured(), "s1")
            runCurrent() // first request in flight
            api.delayMs = 0
            api.response = result("second")
            coordinator.analyze(captured(), "s2") // supersede
            advanceUntilIdle()

            val state = coordinator.state.value
            assertTrue(state is SpeakingAnalysisState.Result)
            assertEquals("second", (state as SpeakingAnalysisState.Result).transcript)
            assertEquals("s2", api.requests.last().sessionId)
        }

    @Test
    fun `reset returns to Idle and clears transcript`() =
        runTest {
            val api = FakeLlmApi(response = result("hi"))
            val coordinator = SpeakingAnalysisCoordinator(api, coordScope())

            coordinator.analyze(captured(), "s1")
            advanceUntilIdle()
            coordinator.reset()
            advanceUntilIdle()

            assertEquals(SpeakingAnalysisState.Idle, coordinator.state.value)
            assertNull(coordinator.transcript())
        }

    /** Coordinator scope on an unconfined dispatcher tied to the test scheduler, so launches
     *  run eagerly while virtual time still drives the watchdog. */
    private fun TestScope.coordScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
}

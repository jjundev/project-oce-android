package com.jjundev.oneclickeng.feature.session.feedback

import com.jjundev.oneclickeng.core.network.CorrectedSentenceDto
import com.jjundev.oneclickeng.core.network.FeedbackEvent
import com.jjundev.oneclickeng.core.network.FeedbackRequest
import com.jjundev.oneclickeng.core.network.FeedbackStream
import com.jjundev.oneclickeng.core.network.GrammarDto
import com.jjundev.oneclickeng.core.network.NaturalExpressionDto
import com.jjundev.oneclickeng.core.network.ReasonDto
import com.jjundev.oneclickeng.core.network.WritingScoreDto
import com.jjundev.oneclickeng.core.time.FakeElapsedClock
import com.jjundev.oneclickeng.feature.session.analytics.LatencyAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.RecordingLatencyAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class LatencyFakeFeedbackStream : FeedbackStream {
    private val channels = mutableListOf<Channel<FeedbackEvent>>()

    override fun events(request: FeedbackRequest): Flow<FeedbackEvent> {
        val channel = Channel<FeedbackEvent>(Channel.UNLIMITED)
        channels += channel
        return channel.consumeAsFlow()
    }

    fun push(event: FeedbackEvent) = channels.last().trySend(event)

    fun end() = channels.last().close()
}

private fun writingScore() = FeedbackEvent.Section.WritingScore(WritingScoreDto(85, "잘했어요!"))

private fun grammar() = FeedbackEvent.Section.Grammar(GrammarDto(CorrectedSentenceDto(emptyList()), "좋아요."))

private fun natural() = FeedbackEvent.Section.NaturalExpression(NaturalExpressionDto(emptyList(), ReasonDto("k", "d")))

@OptIn(ExperimentalCoroutinesApi::class)
class SlimFeedbackLatencyTest {
    private fun TestScope.coordScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private fun SlimFeedbackCoordinator.begin() =
        start(
            sessionId = "s1",
            koreanPrompt = "커피 주세요",
            userEnglish = "One coffee",
            referenceEnglish = "ref",
            level = "normal",
        )

    @Test
    fun `all 3 sections arriving logs slim_latency_ms successful`() =
        runTest {
            val stream = LatencyFakeFeedbackStream()
            val clock = FakeElapsedClock(now = 10L)
            val latency = RecordingLatencyAnalytics()
            val coordinator = SlimFeedbackCoordinator(stream, coordScope(), clock = clock, latencyAnalytics = latency)

            coordinator.begin()
            runCurrent()
            clock.advance(120L)
            stream.push(writingScore())
            stream.push(grammar())
            stream.push(natural())
            runCurrent()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("slim", LatencyAnalytics.OUTCOME_SUCCESSFUL, 120L)),
                latency.calls,
            )
        }

    @Test
    fun `stream closing before all sections arrive logs slim_latency_ms failed`() =
        runTest {
            val stream = LatencyFakeFeedbackStream()
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator = SlimFeedbackCoordinator(stream, coordScope(), clock = clock, latencyAnalytics = latency)

            coordinator.begin()
            runCurrent()
            clock.advance(200L)
            stream.push(writingScore())
            stream.end()
            advanceUntilIdle()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("slim", LatencyAnalytics.OUTCOME_FAILED, 200L)),
                latency.calls,
            )
        }
}

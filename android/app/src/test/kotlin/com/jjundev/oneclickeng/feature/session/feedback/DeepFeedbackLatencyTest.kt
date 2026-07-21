package com.jjundev.oneclickeng.feature.session.feedback

import com.jjundev.oneclickeng.core.network.ConceptualBridgeDto
import com.jjundev.oneclickeng.core.network.DeepFeedbackStream
import com.jjundev.oneclickeng.core.network.FeedbackDeepEvent
import com.jjundev.oneclickeng.core.network.FeedbackDeepRequest
import com.jjundev.oneclickeng.core.network.ParaphraseItemDto
import com.jjundev.oneclickeng.core.network.ParaphrasingDto
import com.jjundev.oneclickeng.core.network.ToneLevelDto
import com.jjundev.oneclickeng.core.network.ToneStyleDto
import com.jjundev.oneclickeng.core.network.VennCircleDto
import com.jjundev.oneclickeng.core.network.VennDto
import com.jjundev.oneclickeng.core.network.VennIntersectionDto
import com.jjundev.oneclickeng.core.time.FakeElapsedClock
import com.jjundev.oneclickeng.feature.session.analytics.LatencyAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.NoOpSavedCardAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.RecordingLatencyAnalytics
import com.jjundev.oneclickeng.feature.session.saved.FakeSavedCardRepository
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

private class LatencyFakeDeepStream : DeepFeedbackStream {
    private val channels = mutableListOf<Channel<FeedbackDeepEvent>>()

    override fun events(request: FeedbackDeepRequest): Flow<FeedbackDeepEvent> {
        val channel = Channel<FeedbackDeepEvent>(Channel.UNLIMITED)
        channels += channel
        return channel.consumeAsFlow()
    }

    fun push(event: FeedbackDeepEvent) = channels.last().trySend(event)

    fun end() = channels.last().close()
}

private fun conceptualBridge() =
    FeedbackDeepEvent.Section.ConceptualBridge(
        ConceptualBridgeDto(
            literalTranslation = "직역",
            explanation = "설명",
            venn =
                VennDto(
                    "안내",
                    VennCircleDto("get", listOf("얻다")),
                    VennCircleDto("order", listOf("주문")),
                    VennIntersectionDto(listOf("받다")),
                ),
        ),
    )

private fun toneStyle() =
    FeedbackDeepEvent.Section.ToneStyle(ToneStyleDto(2, (0..4).map { ToneLevelDto(it, "s$it", "번역$it") }))

private fun paraphrasing() =
    FeedbackDeepEvent.Section.Paraphrasing(ParaphrasingDto(listOf(ParaphraseItemDto(1, "Beginner", "p1", "번역1"))))

@OptIn(ExperimentalCoroutinesApi::class)
class DeepFeedbackLatencyTest {
    private fun TestScope.coordScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private fun DeepFeedbackCoordinator.begin() =
        start(
            sessionId = "s1",
            turnIndex = 0,
            koreanPrompt = "커피 주세요",
            userEnglish = "One coffee",
            referenceEnglish = "ref",
            level = "normal",
        )

    @Test
    fun `all 3 blocks arriving logs deep_latency_ms successful`() =
        runTest {
            val stream = LatencyFakeDeepStream()
            val clock = FakeElapsedClock(now = 5L)
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                DeepFeedbackCoordinator(
                    stream,
                    FakeSavedCardRepository(),
                    coordScope(),
                    NoOpSavedCardAnalytics(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            coordinator.begin()
            runCurrent()
            clock.advance(600L)
            stream.push(conceptualBridge())
            stream.push(toneStyle())
            stream.push(paraphrasing())
            runCurrent()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("deep", LatencyAnalytics.OUTCOME_SUCCESSFUL, 600L)),
                latency.calls,
            )
        }

    @Test
    fun `stream closing before all blocks arrive logs deep_latency_ms failed`() =
        runTest {
            val stream = LatencyFakeDeepStream()
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                DeepFeedbackCoordinator(
                    stream,
                    FakeSavedCardRepository(),
                    coordScope(),
                    NoOpSavedCardAnalytics(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            coordinator.begin()
            runCurrent()
            clock.advance(700L)
            stream.push(conceptualBridge())
            stream.end()
            advanceUntilIdle()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("deep", LatencyAnalytics.OUTCOME_FAILED, 700L)),
                latency.calls,
            )
        }

    @Test
    fun `cancel while Loading logs deep_latency_ms canceled`() =
        runTest {
            val stream = LatencyFakeDeepStream()
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                DeepFeedbackCoordinator(
                    stream,
                    FakeSavedCardRepository(),
                    coordScope(),
                    NoOpSavedCardAnalytics(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            coordinator.begin()
            runCurrent()
            clock.advance(90L)
            coordinator.cancel()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("deep", LatencyAnalytics.OUTCOME_CANCELED, 90L)),
                latency.calls,
            )
        }
}

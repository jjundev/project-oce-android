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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun conceptualBridge() =
    FeedbackDeepEvent.Section.ConceptualBridge(
        ConceptualBridgeDto(
            literalTranslation = "직역",
            explanation = "설명",
            venn =
                VennDto(
                    guide = "안내",
                    leftCircle = VennCircleDto("get", listOf("얻다")),
                    rightCircle = VennCircleDto("order", listOf("주문하다")),
                    intersection = VennIntersectionDto(listOf("받다")),
                ),
        ),
    )

private fun toneStyle() =
    FeedbackDeepEvent.Section.ToneStyle(
        ToneStyleDto(
            defaultLevel = 2,
            levels =
                (0..4).map { ToneLevelDto(it, "s$it", "번역$it") },
        ),
    )

private fun paraphrasing() =
    FeedbackDeepEvent.Section.Paraphrasing(
        ParaphrasingDto(
            items =
                listOf(
                    ParaphraseItemDto(1, "Beginner", "p1", "번역1"),
                    ParaphraseItemDto(2, "Intermediate", "p2", "번역2"),
                    ParaphraseItemDto(3, "Advanced", "p3", "번역3"),
                ),
        ),
    )

/** Fake stream: each events() call yields a fresh channel-backed cold flow the test drives. */
private class FakeDeepStream : DeepFeedbackStream {
    val requests = mutableListOf<FeedbackDeepRequest>()
    private val channels = mutableListOf<Channel<FeedbackDeepEvent>>()

    override fun events(request: FeedbackDeepRequest): Flow<FeedbackDeepEvent> {
        requests += request
        val channel = Channel<FeedbackDeepEvent>(Channel.UNLIMITED)
        channels += channel
        return channel.consumeAsFlow()
    }

    fun push(event: FeedbackDeepEvent) {
        channels.last().trySend(event)
    }

    fun pushAt(
        index: Int,
        event: FeedbackDeepEvent,
    ) {
        channels[index].trySend(event)
    }

    fun end() {
        channels.last().close()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DeepFeedbackCoordinatorTest {
    private fun TestScope.coordScope(): CoroutineScope =
        CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private fun DeepFeedbackCoordinator.begin() =
        start(
            sessionId = "s1",
            turnIndex = 4,
            koreanPrompt = "커피 주세요",
            userEnglish = "One coffee",
            referenceEnglish = "Can I get a coffee?",
            level = "normal",
        )

    @Test
    fun `blocks render progressively in order, promote to Ready when all arrive`() =
        runTest {
            val stream = FakeDeepStream()
            val coordinator = DeepFeedbackCoordinator(stream, coordScope())

            coordinator.begin()
            runCurrent()
            assertTrue(coordinator.state.value is DeepFeedbackState.Loading)

            stream.push(conceptualBridge())
            runCurrent()
            (coordinator.state.value as DeepFeedbackState.Loading).let {
                assertTrue(it.conceptualBridge != null)
                assertNull(it.toneStyle)
                assertNull(it.paraphrasing)
            }

            stream.push(toneStyle())
            stream.push(paraphrasing())
            runCurrent()

            (coordinator.state.value as DeepFeedbackState.Ready).let {
                assertEquals("직역", it.conceptualBridge.literalTranslation)
                assertEquals(5, it.toneStyle.levels.size)
                assertEquals(3, it.paraphrasing.items.size)
            }
        }

    @Test
    fun `start is a no-op once past Idle (same-turn 1x cache, P3)`() =
        runTest {
            val stream = FakeDeepStream()
            val coordinator = DeepFeedbackCoordinator(stream, coordScope())

            coordinator.begin()
            runCurrent()
            coordinator.begin() // collapse/expand re-call — must NOT open a second stream
            runCurrent()
            assertEquals(1, stream.requests.size)

            stream.push(conceptualBridge())
            stream.push(toneStyle())
            stream.push(paraphrasing())
            runCurrent()
            coordinator.begin() // even after Ready → still cached
            runCurrent()
            assertEquals(1, stream.requests.size)
        }

    @Test
    fun `stream end before all blocks arrive fails the region but keeps arrived blocks (sticky)`() =
        runTest {
            val stream = FakeDeepStream()
            val coordinator = DeepFeedbackCoordinator(stream, coordScope())

            coordinator.begin()
            runCurrent()
            stream.push(conceptualBridge())
            stream.end() // toneStyle + paraphrasing never arrived
            runCurrent()

            (coordinator.state.value as DeepFeedbackState.Error).let {
                assertTrue("conceptualBridge preserved", it.conceptualBridge != null)
                assertNull(it.toneStyle)
                assertNull(it.paraphrasing)
            }
        }

    @Test
    fun `retry from Error re-runs the whole call and clears ephemeral bookmarks`() =
        runTest {
            val stream = FakeDeepStream()
            val coordinator = DeepFeedbackCoordinator(stream, coordScope())

            coordinator.begin()
            runCurrent()
            stream.push(paraphrasing())
            val p = (coordinator.state.value as DeepFeedbackState.Loading).paraphrasing!!.items.first()
            val bookmark = coordinator.toggleBookmark(p)
            assertEquals(4, bookmark.turnIndex) // seam carries turnIndex for M2-04 cardId
            assertTrue(1 in coordinator.bookmarkedLevels.value)

            stream.end() // cb + tone missing → Error
            runCurrent()
            assertTrue(coordinator.state.value is DeepFeedbackState.Error)

            coordinator.retry()
            runCurrent()
            assertEquals(2, stream.requests.size)
            assertTrue(coordinator.state.value is DeepFeedbackState.Loading)
            assertTrue("bookmarks cleared on retry", coordinator.bookmarkedLevels.value.isEmpty())
        }

    @Test
    fun `retry is a no-op unless in Error`() =
        runTest {
            val stream = FakeDeepStream()
            val coordinator = DeepFeedbackCoordinator(stream, coordScope())

            coordinator.begin()
            runCurrent()
            coordinator.retry() // Loading, not Error → no-op
            runCurrent()
            assertEquals(1, stream.requests.size)
        }

    @Test
    fun `a late event from a superseded stream is dropped (stale guard)`() =
        runTest {
            val stream = FakeDeepStream()
            val coordinator = DeepFeedbackCoordinator(stream, coordScope())

            coordinator.begin()
            runCurrent()
            coordinator.reset() // Idle, bumps token, cancels first collect
            coordinator.begin() // channel index 1
            runCurrent()
            assertEquals(2, stream.requests.size)

            stream.pushAt(0, conceptualBridge()) // late event on superseded channel — dropped
            runCurrent()
            (coordinator.state.value as DeepFeedbackState.Loading).let {
                assertNull(it.conceptualBridge)
            }

            stream.pushAt(1, conceptualBridge())
            runCurrent()
            assertTrue((coordinator.state.value as DeepFeedbackState.Loading).conceptualBridge != null)
        }

    @Test
    fun `the idle watchdog fails the region while keeping arrived blocks`() =
        runTest {
            val stream = FakeDeepStream()
            val coordinator = DeepFeedbackCoordinator(stream, coordScope())

            coordinator.begin()
            runCurrent()
            stream.push(conceptualBridge()) // arrives; tone + para stall
            runCurrent()

            advanceUntilIdle() // fire the idle watchdog

            (coordinator.state.value as DeepFeedbackState.Error).let {
                assertTrue(it.conceptualBridge != null) // sticky
            }
        }

    @Test
    fun `cap rejection routes to a request-level QuotaBlocked`() =
        runTest {
            val stream = FakeDeepStream()
            val coordinator = DeepFeedbackCoordinator(stream, coordScope())

            coordinator.begin()
            runCurrent()
            stream.push(FeedbackDeepEvent.QuotaExceeded(0))
            runCurrent()

            assertTrue(coordinator.state.value is DeepFeedbackState.QuotaBlocked)
        }

    @Test
    fun `reset returns to Idle so a fresh turn re-opens deep`() =
        runTest {
            val stream = FakeDeepStream()
            val coordinator = DeepFeedbackCoordinator(stream, coordScope())

            coordinator.begin()
            runCurrent()
            coordinator.reset()
            assertTrue(coordinator.state.value is DeepFeedbackState.Idle)

            coordinator.begin()
            runCurrent()
            assertEquals(2, stream.requests.size) // Idle → start works again
        }

    @Test
    fun `toggleBookmark flips the ephemeral level set`() =
        runTest {
            val stream = FakeDeepStream()
            val coordinator = DeepFeedbackCoordinator(stream, coordScope())

            coordinator.begin()
            runCurrent()
            stream.push(paraphrasing())
            runCurrent()
            val item = (coordinator.state.value as DeepFeedbackState.Loading).paraphrasing!!.items[1]

            coordinator.toggleBookmark(item)
            assertTrue(2 in coordinator.bookmarkedLevels.value)
            coordinator.toggleBookmark(item)
            assertTrue(2 !in coordinator.bookmarkedLevels.value)
        }
}

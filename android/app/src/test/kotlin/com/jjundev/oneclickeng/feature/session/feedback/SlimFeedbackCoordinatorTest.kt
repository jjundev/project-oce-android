package com.jjundev.oneclickeng.feature.session.feedback

import com.jjundev.oneclickeng.core.network.CorrectedSentenceDto
import com.jjundev.oneclickeng.core.network.FeedbackEvent
import com.jjundev.oneclickeng.core.network.FeedbackRequest
import com.jjundev.oneclickeng.core.network.FeedbackSegmentDto
import com.jjundev.oneclickeng.core.network.FeedbackStream
import com.jjundev.oneclickeng.core.network.GrammarDto
import com.jjundev.oneclickeng.core.network.NaturalExpressionDto
import com.jjundev.oneclickeng.core.network.ReasonDto
import com.jjundev.oneclickeng.core.network.WritingScoreDto
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun writingScore(score: Int = 85) =
    FeedbackEvent.Section.WritingScore(WritingScoreDto(score, "잘했어요!"))

private fun grammar(segments: List<FeedbackSegmentDto>) =
    FeedbackEvent.Section.Grammar(GrammarDto(CorrectedSentenceDto(segments), "좋아요."))

private fun natural(segments: List<FeedbackSegmentDto>) =
    FeedbackEvent.Section.NaturalExpression(NaturalExpressionDto(segments, ReasonDto("k", "d")))

private fun seg(
    text: String,
    type: String,
) = FeedbackSegmentDto(text, type)

/** Fake stream: each events() call yields a fresh channel-backed cold flow the test drives. */
private class FakeFeedbackStream : FeedbackStream {
    val requests = mutableListOf<FeedbackRequest>()
    private val channels = mutableListOf<Channel<FeedbackEvent>>()

    override fun events(request: FeedbackRequest): Flow<FeedbackEvent> {
        requests += request
        val channel = Channel<FeedbackEvent>(Channel.UNLIMITED)
        channels += channel
        return channel.consumeAsFlow()
    }

    fun push(event: FeedbackEvent) {
        channels.last().trySend(event)
    }

    fun pushAt(
        index: Int,
        event: FeedbackEvent,
    ) {
        channels[index].trySend(event)
    }

    fun end() {
        channels.last().close()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SlimFeedbackCoordinatorTest {
    private fun TestScope.coordScope(): CoroutineScope =
        CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private fun SlimFeedbackCoordinator.begin() =
        start(
            sessionId = "s1",
            koreanPrompt = "커피 주세요",
            userEnglish = "One coffee",
            referenceEnglish = "Can I get a coffee?",
            level = "normal",
        )

    private fun active(coordinator: SlimFeedbackCoordinator): SlimFeedbackState.Active {
        val state = coordinator.state.value
        assertTrue("expected Active, was $state", state is SlimFeedbackState.Active)
        return state as SlimFeedbackState.Active
    }

    @Test
    fun `sections render progressively in order and next enables only when all settled`() =
        runTest {
            val stream = FakeFeedbackStream()
            val coordinator = SlimFeedbackCoordinator(stream, coordScope())

            coordinator.begin()
            runCurrent()
            active(coordinator).let {
                assertTrue(it.writingScore is SectionState.Loading)
                assertEquals(RecapHeader("커피 주세요", "One coffee"), it.header)
                assertFalse(it.nextEnabled)
            }

            stream.push(writingScore(85))
            runCurrent()
            assertTrue(active(coordinator).writingScore is SectionState.Ready)
            assertFalse(active(coordinator).nextEnabled)

            stream.push(grammar(listOf(seg("ok", "normal"))))
            stream.push(natural(listOf(seg("x", "normal"))))
            runCurrent()

            active(coordinator).let {
                assertTrue(it.grammar is SectionState.Ready)
                assertTrue(it.natural is SectionState.Ready)
                assertTrue(it.nextEnabled)
                assertEquals(85, it.writingScore.readyValueOrNull()?.score)
            }
        }

    @Test
    fun `a late event from a superseded stream is dropped (stale guard)`() =
        runTest {
            val stream = FakeFeedbackStream()
            val coordinator = SlimFeedbackCoordinator(stream, coordScope())

            coordinator.begin()
            runCurrent()
            coordinator.begin() // supersede — bumps token, cancels the first collect
            runCurrent()
            assertEquals(2, stream.requests.size)

            // A late event on the FIRST (superseded) channel must be dropped by the stale guard.
            stream.pushAt(0, writingScore(99))
            runCurrent()
            assertTrue(active(coordinator).writingScore is SectionState.Loading)

            // The current (second) stream still drives state.
            stream.pushAt(1, writingScore(70))
            runCurrent()
            assertEquals(70, active(coordinator).writingScore.readyValueOrNull()?.score)
        }

    @Test
    fun `a section that never arrives fails on stream end, then retry fills it`() =
        runTest {
            val stream = FakeFeedbackStream()
            val coordinator = SlimFeedbackCoordinator(stream, coordScope())

            coordinator.begin()
            runCurrent()
            stream.push(writingScore(85))
            stream.push(natural(listOf(seg("x", "normal"))))
            stream.end() // grammar never arrived
            runCurrent()

            active(coordinator).grammar.let {
                assertTrue(it is SectionState.Failed)
                assertEquals(1, (it as SectionState.Failed).attempts)
                assertTrue(it.canRetry)
            }

            coordinator.retry(SlimSection.Grammar)
            runCurrent()
            assertTrue(active(coordinator).grammar is SectionState.Loading)

            stream.push(grammar(listOf(seg("ok", "normal"))))
            runCurrent()
            assertTrue(active(coordinator).grammar is SectionState.Ready)
        }

    @Test
    fun `two failures exhaust retries then skip settles the section and nulls its buffer key`() =
        runTest {
            val stream = FakeFeedbackStream()
            val coordinator = SlimFeedbackCoordinator(stream, coordScope())

            coordinator.begin()
            runCurrent()
            stream.push(writingScore(85))
            stream.push(natural(listOf(seg("x", "normal"))))
            stream.end() // grammar Failed(1)
            runCurrent()

            coordinator.retry(SlimSection.Grammar)
            runCurrent()
            stream.end() // grammar Failed(2) — retries exhausted
            runCurrent()

            active(coordinator).grammar.let {
                assertTrue(it is SectionState.Failed)
                assertEquals(2, (it as SectionState.Failed).attempts)
                assertFalse(it.canRetry)
            }
            assertFalse(active(coordinator).nextEnabled) // Failed is not settled

            coordinator.skip(SlimSection.Grammar)
            runCurrent()
            assertTrue(active(coordinator).grammar is SectionState.Skipped)
            assertTrue(active(coordinator).nextEnabled) // Skipped counts as settled

            assertNull(coordinator.bufferSnapshot().correctedText)
        }

    @Test
    fun `buffer snapshot flattens grammar segments and maps all-normal natural`() =
        runTest {
            val stream = FakeFeedbackStream()
            val coordinator = SlimFeedbackCoordinator(stream, coordScope())

            coordinator.begin()
            runCurrent()
            stream.push(writingScore(90))
            stream.push(
                grammar(
                    listOf(
                        seg("I ", "normal"),
                        seg("goed", "incorrect"),
                        seg("went", "correction"),
                        seg(" home", "normal"),
                    ),
                ),
            )
            stream.push(natural(listOf(seg("I went home", "normal"))))
            runCurrent()

            val buffer = coordinator.bufferSnapshot()
            assertEquals(90, buffer.slimScore)
            assertEquals("I went home", buffer.correctedText) // `incorrect` excluded (§17)
            assertEquals("I went home", buffer.naturalExpression)

            // all-normal natural → already-natural (reason hidden by UI, not null in data).
            val nat = active(coordinator).natural.readyValueOrNull()
            assertTrue(nat!!.isAlreadyNatural)
        }

    @Test
    fun `cap rejection routes to a request-level QuotaBlocked, not per-section failures`() =
        runTest {
            val stream = FakeFeedbackStream()
            val coordinator = SlimFeedbackCoordinator(stream, coordScope())

            coordinator.begin()
            runCurrent()
            stream.push(FeedbackEvent.QuotaExceeded(0))
            runCurrent()

            val state = coordinator.state.value
            assertTrue("expected QuotaBlocked, was $state", state is SlimFeedbackState.QuotaBlocked)
            assertEquals(RecapHeader("커피 주세요", "One coffee"), (state as SlimFeedbackState.QuotaBlocked).header)
        }

    @Test
    fun `the idle watchdog fails still-loading sections while keeping arrived ones`() =
        runTest {
            val stream = FakeFeedbackStream()
            val coordinator = SlimFeedbackCoordinator(stream, coordScope())

            coordinator.begin()
            runCurrent()
            stream.push(writingScore(85)) // arrives; grammar + natural stall
            runCurrent()

            advanceUntilIdle() // fire the idle watchdog

            active(coordinator).let {
                assertTrue(it.writingScore is SectionState.Ready) // sticky
                assertTrue(it.grammar is SectionState.Failed)
                assertTrue(it.natural is SectionState.Failed)
            }
        }
}

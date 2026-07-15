package com.jjundev.oneclickeng.feature.session.summary

import com.jjundev.oneclickeng.core.network.CoachingDto
import com.jjundev.oneclickeng.core.network.ExpressionItemDto
import com.jjundev.oneclickeng.core.network.FutureSelfFeedbackDto
import com.jjundev.oneclickeng.core.network.SectionOutcome
import com.jjundev.oneclickeng.core.network.SummaryEvent
import com.jjundev.oneclickeng.core.network.SummaryRequest
import com.jjundev.oneclickeng.core.network.SummaryStream
import com.jjundev.oneclickeng.core.network.WordExampleDto
import com.jjundev.oneclickeng.core.network.WordItemDto
import com.jjundev.oneclickeng.feature.gamification.AccrualSnapshot
import com.jjundev.oneclickeng.feature.gamification.StudytimeRepository
import com.jjundev.oneclickeng.feature.session.feedback.TurnFeedbackBuffer
import com.jjundev.oneclickeng.feature.session.saved.CardType
import com.jjundev.oneclickeng.feature.session.saved.FakeSavedCardRepository
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
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

private fun expressionItem() =
    ExpressionItemDto("natural", "커피 주세요", "One coffee", "Could I grab a coffee?", "가벼워요.")

private fun wordItem() =
    WordItemDto("grab", "잽싸게", "verb", "B1", WordExampleDto("Let me grab it.", "제가 가져올게요."))

private fun expressionCard() = SummaryEvent.Card.Expression(listOf(expressionItem()))

private fun wordCard() = SummaryEvent.Card.Word(listOf(wordItem()))

private fun coachingCard() =
    SummaryEvent.Card.Coaching(CoachingDto(FutureSelfFeedbackDto("끝까지 했어요.", "과거형을 노려봐요.")))

private fun done(
    expressions: SectionOutcome = SectionOutcome.Ok,
    words: SectionOutcome = SectionOutcome.Ok,
    coaching: SectionOutcome = SectionOutcome.Ok,
) = SummaryEvent.Done(expressions, words, coaching)

/** Fake stream: each events() call yields a fresh channel-backed cold flow the test drives. */
private class FakeSummaryStream : SummaryStream {
    val requests = mutableListOf<SummaryRequest>()
    private val channels = mutableListOf<Channel<SummaryEvent>>()

    override fun events(request: SummaryRequest): Flow<SummaryEvent> {
        requests += request
        val channel = Channel<SummaryEvent>(Channel.UNLIMITED)
        channels += channel
        return channel.consumeAsFlow()
    }

    fun push(event: SummaryEvent) = channels.last().trySend(event)

    fun pushAt(
        index: Int,
        event: SummaryEvent,
    ) = channels[index].trySend(event)

    fun end() = channels.last().close()
}

private class FakeBookmarkSource(private val cards: List<BookmarkCard> = emptyList()) : BookmarkSource {
    override suspend fun latestSentences(
        sessionId: String,
        limit: Int,
    ): List<BookmarkCard> = cards.take(limit)
}

private class FakeLedger : CompletionLedger {
    val calls = mutableListOf<Triple<String, String, String>>()

    override fun recordCompletion(
        sessionId: String,
        difficulty: String,
        modeId: String,
    ) {
        calls += Triple(sessionId, difficulty, modeId)
    }
}

private class FakeStudytimeRepository(
    private val snapshot: AccrualSnapshot =
        AccrualSnapshot(todaySeconds = 600, streak = 3, todaySecondsBefore = 0, streakStatic = false),
) : StudytimeRepository {
    val sessions = mutableListOf<Triple<String, Long, String>>()

    override suspend fun recordSession(
        sessionId: String,
        elapsedSeconds: Long,
        dayKey: String,
    ): AccrualSnapshot {
        sessions += Triple(sessionId, elapsedSeconds, dayKey)
        return snapshot
    }

    override suspend fun seedFromServerIfEmpty() = Unit

    override suspend fun drain() = Unit

    override suspend fun resetMetrics() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryCoordinatorTest {
    private fun TestScope.coordScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private fun store(): SessionTurnBufferStore =
        SessionTurnBufferStore().apply {
            startSession("s1")
            record(
                "커피 주세요",
                "One coffee",
                TurnFeedbackBuffer(slimScore = 80, correctedText = "a", naturalExpression = "b"),
            )
            record(
                "길 알려줘",
                "Where station",
                TurnFeedbackBuffer(slimScore = 90, correctedText = "c", naturalExpression = "d"),
            )
        }

    @Suppress("LongParameterList") // 코디네이터 seam 을 그대로 반영하는 테스트 팩토리 — 기본값 오버라이드용.
    private fun coordinator(
        scope: CoroutineScope,
        stream: FakeSummaryStream,
        bookmarks: FakeBookmarkSource = FakeBookmarkSource(),
        ledger: FakeLedger = FakeLedger(),
        savedCards: FakeSavedCardRepository = FakeSavedCardRepository(),
        studytime: FakeStudytimeRepository = FakeStudytimeRepository(),
    ) = SummaryCoordinator(stream, store(), bookmarks, ledger, savedCards, studytime, scope)

    private val accrual = AccrualStrip(streakDays = 3, xp = 40)

    private fun SummaryCoordinator.begin() =
        start(sessionId = "s1", difficulty = "normal", modeId = "default", accrual = accrual)

    private fun sectioned(coordinator: SummaryCoordinator): SectionBundle.Sectioned {
        val bundle = coordinator.state.value.bundle
        assertTrue("expected Sectioned, was $bundle", bundle is SectionBundle.Sectioned)
        return bundle as SectionBundle.Sectioned
    }

    @Test
    fun `local blocks compute immediately and bundle starts loading`() =
        runTest {
            val stream = FakeSummaryStream()
            val coordinator = coordinator(coordScope(), stream)

            coordinator.begin()
            runCurrent()

            coordinator.state.value.let {
                assertEquals(85, it.totalScore) // mean(80, 90)
                assertEquals(90, it.highlight?.score) // highest slim turn
                // accrual is recomputed from studytime (M3-05): streak + study seconds before→after + xp
                // (normal=20), and animate flips true so the strip counts up (M3-06).
                assertEquals(3, it.accrual.streakDays)
                assertEquals(20, it.accrual.xp)
                assertEquals(0, it.accrual.todayStudySecondsBefore)
                assertEquals(600, it.accrual.todayStudySecondsAfter)
                assertFalse(it.accrual.streakStatic)
                assertTrue(it.accrual.animate)
                assertTrue(it.bundle is SectionBundle.BundleLoading)
            }
        }

    @Test
    fun `start records the session's studytime once`() =
        runTest {
            val stream = FakeSummaryStream()
            val studytime = FakeStudytimeRepository()
            val coordinator = coordinator(coordScope(), stream, studytime = studytime)

            coordinator.begin()
            runCurrent()

            assertEquals(1, studytime.sessions.size)
            assertEquals("s1", studytime.sessions.first().first)
        }

    @Test
    fun `toggleSaveWord persists WORD by sourceIndex and toggles savedWordIndices`() =
        runTest {
            val stream = FakeSummaryStream()
            val repo = FakeSavedCardRepository()
            val coordinator = coordinator(coordScope(), stream, savedCards = repo)

            coordinator.begin() // sessionId=s1
            stream.push(wordCard()) // word[0]
            stream.push(done())
            runCurrent()

            coordinator.toggleSaveWord(0) // save
            runCurrent()
            assertTrue(0 in coordinator.state.value.savedWordIndices)
            assertEquals(1, repo.saves.size)
            assertEquals("s1__WORD__0", repo.saves.first().cardId)

            coordinator.toggleSaveWord(0) // unsave → tombstone
            runCurrent()
            assertFalse(0 in coordinator.state.value.savedWordIndices)
            assertEquals(1, repo.deletes.size)
            repo.deletes.first().let {
                assertEquals("s1__WORD__0", it.cardId)
                assertEquals(CardType.WORD, it.cardType)
                assertTrue(it.deleted)
            }
        }

    @Test
    fun `toggleSaveExpression persists EXPRESSION by sourceIndex and toggles savedExprIndices`() =
        runTest {
            val stream = FakeSummaryStream()
            val repo = FakeSavedCardRepository()
            val coordinator = coordinator(coordScope(), stream, savedCards = repo)

            coordinator.begin() // sessionId=s1
            stream.push(expressionCard()) // expression[0]
            stream.push(done())
            runCurrent()

            coordinator.toggleSaveExpression(0) // save
            runCurrent()
            assertTrue(0 in coordinator.state.value.savedExprIndices)
            assertEquals(1, repo.saves.size)
            assertEquals("s1__EXPRESSION__0", repo.saves.first().cardId)
            assertEquals(CardType.EXPRESSION, repo.saves.first().card.cardType)

            coordinator.toggleSaveExpression(0) // unsave → tombstone
            runCurrent()
            assertFalse(0 in coordinator.state.value.savedExprIndices)
            assertEquals(1, repo.deletes.size)
            repo.deletes.first().let {
                assertEquals("s1__EXPRESSION__0", it.cardId)
                assertEquals(CardType.EXPRESSION, it.cardType)
                assertTrue(it.deleted)
            }
        }

    @Test
    fun `toggleSaveWord is a no-op for an out-of-range index`() =
        runTest {
            val stream = FakeSummaryStream()
            val repo = FakeSavedCardRepository()
            val coordinator = coordinator(coordScope(), stream, savedCards = repo)

            coordinator.begin()
            stream.push(wordCard())
            stream.push(done())
            runCurrent()

            coordinator.toggleSaveWord(9) // no card at 9
            runCurrent()
            assertTrue(coordinator.state.value.savedWordIndices.isEmpty())
            assertTrue(repo.saves.isEmpty())
        }

    @Test
    fun `completion ledger create is attempted once on start`() =
        runTest {
            val ledger = FakeLedger()
            val coordinator = coordinator(coordScope(), FakeSummaryStream(), ledger = ledger)

            coordinator.begin()
            runCurrent()

            assertEquals(listOf(Triple("s1", "normal", "default")), ledger.calls)
        }

    @Test
    fun `done resolves per-section - arrived Ready, failed section Failed, empty-ok Ready`() =
        runTest {
            val stream = FakeSummaryStream()
            val coordinator = coordinator(coordScope(), stream)

            coordinator.begin()
            runCurrent()
            stream.push(expressionCard())
            stream.push(coachingCard())
            stream.push(done(words = SectionOutcome.Failed))
            runCurrent()

            sectioned(coordinator).let {
                assertEquals(1, it.expression.readyValueOrNull()?.size)
                assertTrue("word should be Failed", it.word is SummarySectionState.Failed)
                assertEquals(1, (it.word as SummarySectionState.Failed).attempts)
                assertTrue(it.coaching is SummarySectionState.Ready)
            }
        }

    @Test
    fun `retry re-requests naming only the failed section and fills it`() =
        runTest {
            val stream = FakeSummaryStream()
            val coordinator = coordinator(coordScope(), stream)

            coordinator.begin()
            runCurrent()
            stream.push(expressionCard())
            stream.push(coachingCard())
            stream.push(done(words = SectionOutcome.Failed))
            runCurrent()
            assertTrue(sectioned(coordinator).word is SummarySectionState.Failed)

            coordinator.retry(SummarySection.Word)
            runCurrent()
            assertTrue(sectioned(coordinator).word is SummarySectionState.Loading)
            // Initial call sent sections=null; the retry names only the failed section.
            assertNull(stream.requests[0].payload.sections)
            assertEquals(listOf("words"), stream.requests[1].payload.sections)

            stream.push(wordCard())
            stream.push(done())
            runCurrent()
            assertEquals(1, sectioned(coordinator).word.readyValueOrNull()?.size)
            // expression stayed sticky across the retry.
            assertEquals(1, sectioned(coordinator).expression.readyValueOrNull()?.size)
        }

    @Test
    fun `cap rejection routes to QuotaBlocked while keeping local blocks`() =
        runTest {
            val stream = FakeSummaryStream()
            val coordinator = coordinator(coordScope(), stream)

            coordinator.begin()
            runCurrent()
            stream.push(SummaryEvent.QuotaExceeded(0))
            runCurrent()

            coordinator.state.value.let {
                assertTrue("expected QuotaBlocked, was ${it.bundle}", it.bundle is SectionBundle.QuotaBlocked)
                assertEquals(85, it.totalScore) // local blocks retained
            }
        }

    @Test
    fun `the idle watchdog fails an unsettled bundle`() =
        runTest {
            val stream = FakeSummaryStream()
            val coordinator = coordinator(coordScope(), stream)

            coordinator.begin()
            runCurrent()
            stream.push(expressionCard()) // one card arrives; word + coaching stall, no done
            runCurrent()

            advanceUntilIdle() // fire the idle watchdog

            sectioned(coordinator).let {
                assertEquals(1, it.expression.readyValueOrNull()?.size) // sticky
                assertTrue(it.word is SummarySectionState.Failed)
                assertTrue(it.coaching is SummarySectionState.Failed)
            }
        }

    @Test
    fun `a late event from a superseded stream is dropped`() =
        runTest {
            val stream = FakeSummaryStream()
            val coordinator = coordinator(coordScope(), stream)

            coordinator.begin()
            runCurrent()
            coordinator.begin() // supersede — bumps token, cancels the first collect
            runCurrent()
            assertEquals(2, stream.requests.size)

            stream.pushAt(0, expressionCard()) // late event on the superseded channel
            runCurrent()
            assertTrue(coordinator.state.value.bundle is SectionBundle.BundleLoading)
        }

    @Test
    fun `bookmarks load asynchronously into the local block`() =
        runTest {
            val stream = FakeSummaryStream()
            val bookmarks =
                FakeBookmarkSource(
                    listOf(BookmarkCard("fixture-sentence-async", "I got lost.", "길을 잃었어요.")),
                )
            val coordinator = coordinator(coordScope(), stream, bookmarks = bookmarks)

            coordinator.begin()
            runCurrent()

            assertEquals(1, coordinator.state.value.bookmarks.size)
        }

    @Test
    fun toggleSaveBookmarkKeepsSentenceAndTogglesSentencePersistence() =
        runTest {
            val stream = FakeSummaryStream()
            val repo = FakeSavedCardRepository()
            val card = BookmarkCard(cardId = "s1__SENTENCE__0__2", english = "I got lost.", korean = "길을 잃었어요.")
            val coordinator =
                coordinator(
                    coordScope(),
                    stream,
                    bookmarks = FakeBookmarkSource(listOf(card)),
                    savedCards = repo,
                )

            coordinator.begin()
            runCurrent()
            assertEquals(listOf(card), coordinator.state.value.bookmarks)

            coordinator.toggleSaveBookmark(card.cardId)
            runCurrent()

            assertEquals(listOf(card), coordinator.state.value.bookmarks)
            assertEquals(setOf(card.cardId), coordinator.state.value.unsavedBookmarkIds)
            assertEquals(1, repo.deletes.size)
            assertEquals(card.cardId, repo.deletes.single().cardId)
            assertEquals(CardType.SENTENCE, repo.deletes.single().cardType)
            assertTrue(repo.deletes.single().deleted)

            coordinator.toggleSaveBookmark(card.cardId)
            runCurrent()

            assertEquals(listOf(card), coordinator.state.value.bookmarks)
            assertTrue(coordinator.state.value.unsavedBookmarkIds.isEmpty())
            assertEquals(1, repo.saves.size)
            assertEquals(card.cardId, repo.saves.single().cardId)
            assertEquals(SavedCard.Sentence(card.english, card.korean), repo.saves.single().card)
        }

    @Test
    fun `initial request projects expression candidates and sentences from the buffer`() =
        runTest {
            val stream = FakeSummaryStream()
            val coordinator = coordinator(coordScope(), stream)
            coordinator.begin()
            runCurrent()
            val payload = stream.requests.first().payload
            // store() seeds two turns with correctedText/naturalExpression → non-empty candidates + sentences.
            assertTrue("expected projected expression candidates", payload.expressionCandidates.isNotEmpty())
            assertTrue("expected projected sentences", payload.sentences.isNotEmpty())
            assertEquals(listOf("One coffee", "Where station"), payload.userOriginalSentences)
            assertNull(payload.sections)
        }

    @Test
    fun `mid-stream cap keeps arrived sections and terminates the rest without QuotaBlocked`() =
        runTest {
            val stream = FakeSummaryStream()
            val coordinator = coordinator(coordScope(), stream)

            coordinator.begin()
            runCurrent()
            stream.push(expressionCard()) // one card arrives...
            stream.push(SummaryEvent.QuotaExceeded(0)) // ...then cap hits mid-stream
            runCurrent()

            sectioned(coordinator).let {
                // arrived section is kept (not discarded by a blanket QuotaBlocked)
                assertEquals(1, it.expression.readyValueOrNull()?.size)
                // un-arrived sections terminate with no retry (cap blocks retry)
                assertTrue(it.word is SummarySectionState.Failed)
                assertFalse((it.word as SummarySectionState.Failed).canRetry)
                assertTrue(it.coaching is SummarySectionState.Failed)
                assertFalse((it.coaching as SummarySectionState.Failed).canRetry)
            }
            assertEquals(85, coordinator.state.value.totalScore) // local blocks retained
        }

    @Test
    fun `two failures exhaust retries so the section can no longer retry (no skip)`() =
        runTest {
            val stream = FakeSummaryStream()
            val coordinator = coordinator(coordScope(), stream)

            coordinator.begin()
            runCurrent()
            stream.end() // nothing arrived → all Failed(1)
            runCurrent()
            (sectioned(coordinator).word as SummarySectionState.Failed).let {
                assertEquals(1, it.attempts)
                assertTrue(it.canRetry)
            }

            coordinator.retry(SummarySection.Word)
            runCurrent()
            stream.end() // retry stream closes empty → Failed(2)
            runCurrent()
            (sectioned(coordinator).word as SummarySectionState.Failed).let {
                assertEquals(2, it.attempts)
                assertFalse(it.canRetry) // MAX_ATTEMPTS reached — retry disabled, no Skipped state
            }
        }

    @Test
    fun `card lists are truncated to their display caps`() =
        runTest {
            val stream = FakeSummaryStream()
            val coordinator = coordinator(coordScope(), stream)

            coordinator.begin()
            runCurrent()
            stream.push(SummaryEvent.Card.Expression((1..12).map { expressionItem() })) // > cap 8
            stream.push(SummaryEvent.Card.Word((1..20).map { wordItem() })) // > cap 12
            stream.push(done())
            runCurrent()

            sectioned(coordinator).let {
                assertEquals(8, it.expression.readyValueOrNull()?.size)
                assertEquals(12, it.word.readyValueOrNull()?.size)
            }
        }

    @Test
    fun `concurrent retries merge both failed sections into one re-request`() =
        runTest {
            val stream = FakeSummaryStream()
            val coordinator = coordinator(coordScope(), stream)

            coordinator.begin()
            runCurrent()
            stream.push(coachingCard())
            stream.push(done(expressions = SectionOutcome.Failed, words = SectionOutcome.Failed))
            runCurrent()

            coordinator.retry(SummarySection.Expression) // fires re-request naming [expression]
            runCurrent()
            coordinator.retry(SummarySection.Word) // merges → re-request naming [expression, word]
            runCurrent()

            assertEquals(listOf("expressions", "words"), stream.requests.last().payload.sections)
        }
}

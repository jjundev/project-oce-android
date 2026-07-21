package com.jjundev.oneclickeng.feature.session.summary

import com.jjundev.oneclickeng.core.network.SectionOutcome
import com.jjundev.oneclickeng.core.network.SummaryEvent
import com.jjundev.oneclickeng.core.network.SummaryRequest
import com.jjundev.oneclickeng.core.network.SummaryStream
import com.jjundev.oneclickeng.core.network.WordExampleDto
import com.jjundev.oneclickeng.core.network.WordItemDto
import com.jjundev.oneclickeng.core.settings.FakeSummarySaveSettingsRepository
import com.jjundev.oneclickeng.feature.gamification.AccrualSnapshot
import com.jjundev.oneclickeng.feature.gamification.StudytimeRepository
import com.jjundev.oneclickeng.feature.reminder.ReminderOrchestrator
import com.jjundev.oneclickeng.feature.reminder.ReminderPromptDecision
import com.jjundev.oneclickeng.feature.reminder.ReminderRunResult
import com.jjundev.oneclickeng.feature.reminder.data.ReminderConfig
import com.jjundev.oneclickeng.feature.session.analytics.NoOpSavedCardAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.NoOpSessionFunnelAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.RecordingSavedCardAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.SavedCardAnalytics
import com.jjundev.oneclickeng.feature.session.saved.CardType
import com.jjundev.oneclickeng.feature.session.saved.FakeSavedCardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

// Prefixed `SavedCardFake*` — top-level `private` classes collide by name across files in the same
// package in Kotlin (Task 5/6 precedent: SessionComplete*/PartialFailure* in the sibling test files).

/** Fake stream: each events() call yields a fresh channel-backed cold flow the test drives. */
private class SavedCardFakeSummaryStream : SummaryStream {
    private val channels = mutableListOf<Channel<SummaryEvent>>()

    override fun events(request: SummaryRequest): Flow<SummaryEvent> {
        val channel = Channel<SummaryEvent>(Channel.UNLIMITED)
        channels += channel
        return channel.consumeAsFlow()
    }

    fun push(event: SummaryEvent) = channels.last().trySend(event)
}

private class SavedCardFakeBookmarkSource : BookmarkSource {
    override suspend fun latestSentences(
        sessionId: String,
        limit: Int,
    ): List<BookmarkCard> = emptyList()
}

private class SavedCardFakeLedger : CompletionLedger {
    override fun recordCompletion(
        sessionId: String,
        difficulty: String,
        modeId: String,
    ) = Unit
}

private class SavedCardFakeStudytimeRepository : StudytimeRepository {
    override suspend fun recordSession(
        sessionId: String,
        elapsedSeconds: Long,
        dayKey: String,
    ): AccrualSnapshot = AccrualSnapshot(todaySeconds = 0, streak = 0, todaySecondsBefore = 0, streakStatic = false)

    override suspend fun seedFromServerIfEmpty() = Unit

    override suspend fun drain() = Unit

    override suspend fun reconcileAfterMerge() = Unit

    override suspend fun resetMetrics() = Unit
}

private class SavedCardFakeReminderOrchestrator : ReminderOrchestrator {
    override val config: Flow<ReminderConfig> = MutableStateFlow(ReminderConfig.DISABLED)

    override suspend fun evaluateOptInPrompt(): ReminderPromptDecision = ReminderPromptDecision.DoNotShow

    override suspend fun acceptOptIn() = Unit

    override suspend fun dismissOptIn() = Unit

    override suspend fun enableReminder() = Unit

    override suspend fun disableReminder() = Unit

    override suspend fun setReminderTime(
        hour: Int,
        minute: Int,
    ) = Unit

    override suspend fun markPermissionAsked() = Unit

    override suspend fun repairSchedule() = Unit

    override suspend fun handleTimezoneChanged() = Unit

    override suspend fun runDueReminder(): ReminderRunResult = ReminderRunResult.DisabledNoOp

    override suspend fun recordSessionCompleted(
        streak: Int,
        lastStudyDate: LocalDate,
    ) = Unit

    override suspend fun recordSavedReviewText(text: String) = Unit

    override suspend fun clearProgressCache() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class SummarySavedCardAnalyticsTest {
    /**
     * Mirrors `SummaryCoordinatorTest.coordinator(...)`'s construction (that helper is a private member
     * of that test class, unreachable here — same constraint Task 5/6 hit) with minimal fakes, threading
     * [savedCardAnalytics] into the (now +1-arg) constructor.
     */
    private fun newSummarySavedCardCoordinator(
        stream: SavedCardFakeSummaryStream,
        savedCardAnalytics: SavedCardAnalytics = NoOpSavedCardAnalytics(),
    ): SummaryCoordinator {
        val scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher())
        return SummaryCoordinator(
            stream,
            SessionTurnBufferStore(),
            SavedCardFakeBookmarkSource(),
            SavedCardFakeLedger(),
            FakeSavedCardRepository(),
            FakeSummarySaveSettingsRepository(),
            SavedCardFakeStudytimeRepository(),
            SavedCardFakeReminderOrchestrator(),
            scope,
            NoOpSessionFunnelAnalytics(),
            savedCardAnalytics,
        )
    }

    private fun wordItem() = WordItemDto("grab", "잽싸게", "verb", "B1", WordExampleDto("Let me grab it.", "제가 가져올게요."))

    /** Drives [coordinator] to a Sectioned bundle with one Ready WORD card at index 0 (mirrors
     * `SummaryCoordinatorTest`'s `toggleSaveWord persists WORD by sourceIndex...` setup). */
    private fun driveReadyWordSection(
        coordinator: SummaryCoordinator,
        stream: SavedCardFakeSummaryStream,
        sessionId: String,
    ) {
        coordinator.start(
            sessionId = sessionId,
            difficulty = "normal",
            modeId = "default",
            accrual = AccrualStrip(streakDays = 0, xp = 0),
        )
        stream.push(SummaryEvent.Card.Word(listOf(wordItem())))
        stream.push(SummaryEvent.Done(SectionOutcome.Ok, SectionOutcome.Ok, SectionOutcome.Ok))
    }

    @Test
    fun `saving a word card logs one saved_card_create summary WORD, unsave logs nothing`() =
        runTest {
            val saved = RecordingSavedCardAnalytics()
            val stream = SavedCardFakeSummaryStream()
            val coordinator = newSummarySavedCardCoordinator(stream, savedCardAnalytics = saved)

            driveReadyWordSection(coordinator, stream, sessionId = "s1")
            runCurrent()

            coordinator.toggleSaveWord(0) // added -> logs
            coordinator.toggleSaveWord(0) // unsave -> no log
            runCurrent()

            assertEquals(
                listOf(RecordingSavedCardAnalytics.Call("s1", SavedCardAnalytics.SURFACE_SUMMARY, CardType.WORD)),
                saved.calls,
            )
        }
}

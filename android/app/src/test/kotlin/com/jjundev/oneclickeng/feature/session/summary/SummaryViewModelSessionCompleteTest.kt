package com.jjundev.oneclickeng.feature.session.summary

import com.jjundev.oneclickeng.core.network.SummaryEvent
import com.jjundev.oneclickeng.core.network.SummaryRequest
import com.jjundev.oneclickeng.core.network.SummaryStream
import com.jjundev.oneclickeng.core.settings.FakeSummarySaveSettingsRepository
import com.jjundev.oneclickeng.feature.gamification.AccrualSnapshot
import com.jjundev.oneclickeng.feature.gamification.StudytimeRepository
import com.jjundev.oneclickeng.feature.reminder.ReminderOrchestrator
import com.jjundev.oneclickeng.feature.reminder.ReminderPromptDecision
import com.jjundev.oneclickeng.feature.reminder.ReminderRunResult
import com.jjundev.oneclickeng.feature.reminder.data.ReminderConfig
import com.jjundev.oneclickeng.feature.session.analytics.RecordingSessionFunnelAnalytics
import com.jjundev.oneclickeng.feature.session.feedback.TurnFeedbackBuffer
import com.jjundev.oneclickeng.feature.session.saved.FakeSavedCardRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Test

// Prefixed `SessionComplete*` — top-level `private` classes still collide by name across files in the
// same package in Kotlin, and `SummaryCoordinatorTest.kt` already declares `FakeSummaryStream` etc.

/** Fake stream: SummaryViewModel.start() session_complete assertions never need real events. */
private class SessionCompleteFakeSummaryStream : SummaryStream {
    override fun events(request: SummaryRequest): Flow<SummaryEvent> = kotlinx.coroutines.flow.emptyFlow()
}

private class SessionCompleteFakeBookmarkSource : BookmarkSource {
    override suspend fun latestSentences(
        sessionId: String,
        limit: Int,
    ): List<BookmarkCard> = emptyList()
}

private class SessionCompleteFakeLedger : CompletionLedger {
    override fun recordCompletion(
        sessionId: String,
        difficulty: String,
        modeId: String,
    ) = Unit
}

private class SessionCompleteFakeStudytimeRepository : StudytimeRepository {
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

private class SessionCompleteFakeReminderOrchestrator : ReminderOrchestrator {
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
class SummaryViewModelSessionCompleteTest {
    /**
     * Mirrors `SummaryCoordinatorTest.coordinator(...)`'s construction (that helper is a private member of
     * that test class, unreachable here) with minimal fakes — this test only asserts VM-level analytics
     * sequencing, not coordinator behavior, so the fakes are no-ops rather than the assertion-bearing
     * fixtures that file uses.
     */
    private fun newSummaryCoordinatorForTest(): SummaryCoordinator {
        val scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher())
        return SummaryCoordinator(
            SessionCompleteFakeSummaryStream(),
            SessionTurnBufferStore(),
            SessionCompleteFakeBookmarkSource(),
            SessionCompleteFakeLedger(),
            FakeSavedCardRepository(),
            FakeSummarySaveSettingsRepository(),
            SessionCompleteFakeStudytimeRepository(),
            SessionCompleteFakeReminderOrchestrator(),
            scope,
        )
    }

    @Test
    fun `start logs session_complete once with turn_count and is_first`() {
        val analytics = RecordingSessionFunnelAnalytics()
        val turnBuffer = SessionTurnBufferStore()
        turnBuffer.startSession("s1")
        turnBuffer.record(
            "q1",
            "a1",
            TurnFeedbackBuffer(slimScore = 80, correctedText = null, naturalExpression = null),
        )
        turnBuffer.record(
            "q2",
            "a2",
            TurnFeedbackBuffer(slimScore = 90, correctedText = null, naturalExpression = null),
        )
        val vm =
            SummaryViewModel(
                coordinator = newSummaryCoordinatorForTest(),
                sessionFunnel = analytics,
                turnBuffer = turnBuffer,
            )
        val accrual = AccrualStrip(streakDays = 0, xp = 0)

        vm.start(sessionId = "s1", difficulty = "easy", modeId = "m", accrual = accrual, isFirstSession = true)
        // idempotent
        vm.start(sessionId = "s1", difficulty = "easy", modeId = "m", accrual = accrual, isFirstSession = true)

        val expectedArgs = mapOf("session_id" to "s1", "turn_count" to 2, "is_first" to true)
        assertEquals(
            listOf(RecordingSessionFunnelAnalytics.Call("session_complete", expectedArgs)),
            analytics.calls,
        )
    }
}

package com.jjundev.oneclickeng.feature.session.summary

import com.jjundev.oneclickeng.core.network.CoachingDto
import com.jjundev.oneclickeng.core.network.ExpressionItemDto
import com.jjundev.oneclickeng.core.network.FutureSelfFeedbackDto
import com.jjundev.oneclickeng.core.network.SectionOutcome
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
import com.jjundev.oneclickeng.feature.session.analytics.NoOpSavedCardAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.NoOpSessionFunnelAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.RecordingSessionFunnelAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.SessionFunnelAnalytics
import com.jjundev.oneclickeng.feature.session.saved.FakeSavedCardRepository
import java.time.LocalDate
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

// Prefixed `PartialFailure*` — top-level `private` classes collide by name across files in the same
// package in Kotlin, and `SummaryCoordinatorTest.kt` already declares `FakeSummaryStream` etc. (Task 5
// hit this with `SessionComplete*`; same fix here).

/** Fake stream: each events() call yields a fresh channel-backed cold flow the test drives. */
private class PartialFailureFakeSummaryStream : SummaryStream {
    private val channels = mutableListOf<Channel<SummaryEvent>>()

    override fun events(request: SummaryRequest): Flow<SummaryEvent> {
        val channel = Channel<SummaryEvent>(Channel.UNLIMITED)
        channels += channel
        return channel.consumeAsFlow()
    }

    fun push(event: SummaryEvent) = channels.last().trySend(event)
}

private class PartialFailureFakeBookmarkSource : BookmarkSource {
    override suspend fun latestSentences(
        sessionId: String,
        limit: Int,
    ): List<BookmarkCard> = emptyList()
}

private class PartialFailureFakeLedger : CompletionLedger {
    override fun recordCompletion(
        sessionId: String,
        difficulty: String,
        modeId: String,
    ) = Unit
}

private class PartialFailureFakeStudytimeRepository : StudytimeRepository {
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

private class PartialFailureFakeReminderOrchestrator : ReminderOrchestrator {
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
class SummaryPartialFailureAnalyticsTest {
    /**
     * Mirrors `SummaryCoordinatorTest.coordinator(...)`'s construction (that helper is a private member
     * of that test class, unreachable here — same constraint Task 5 hit) with minimal fakes, threading
     * [sessionFunnel] into the constructor.
     */
    private fun newSummaryCoordinatorForTest(
        stream: PartialFailureFakeSummaryStream,
        sessionFunnel: SessionFunnelAnalytics = NoOpSessionFunnelAnalytics(),
    ): SummaryCoordinator {
        val scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher())
        return SummaryCoordinator(
            stream,
            SessionTurnBufferStore(),
            PartialFailureFakeBookmarkSource(),
            PartialFailureFakeLedger(),
            FakeSavedCardRepository(),
            FakeSummarySaveSettingsRepository(),
            PartialFailureFakeStudytimeRepository(),
            PartialFailureFakeReminderOrchestrator(),
            scope,
            sessionFunnel,
            NoOpSavedCardAnalytics(),
        )
    }

    private fun expressionItem() =
        ExpressionItemDto("natural", "커피 주세요", "One coffee", "Could I grab a coffee?", "가벼워요.")

    private fun coachingDto() = CoachingDto(FutureSelfFeedbackDto("끝까지 했어요.", "과거형을 노려봐요."))

    @Test
    fun `partial failure logs summary_partial_failure once with the failed-section count`() =
        runTest {
            val analytics = RecordingSessionFunnelAnalytics()
            val stream = PartialFailureFakeSummaryStream()
            val coordinator = newSummaryCoordinatorForTest(stream, sessionFunnel = analytics)

            coordinator.start(
                sessionId = "s1",
                difficulty = "easy",
                modeId = "m",
                accrual = AccrualStrip(streakDays = 0, xp = 0),
            )
            runCurrent()

            // Drive one section to fail while the others succeed (mirrors SummaryCoordinatorTest's
            // "done resolves per-section" scenario): expression + coaching cards arrive, word fails.
            stream.push(SummaryEvent.Card.Expression(listOf(expressionItem())))
            stream.push(SummaryEvent.Card.Coaching(coachingDto()))
            stream.push(SummaryEvent.Done(SectionOutcome.Ok, SectionOutcome.Failed, SectionOutcome.Ok))
            runCurrent()

            val failures = analytics.calls.filter { it.name == "summary_partial_failure" }
            assertEquals(1, failures.size)
            assertEquals("s1", failures.single().args["session_id"])
            assertEquals(1, failures.single().args["sections_failed"])
        }
}

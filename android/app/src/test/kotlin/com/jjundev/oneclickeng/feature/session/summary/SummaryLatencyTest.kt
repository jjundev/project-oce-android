package com.jjundev.oneclickeng.feature.session.summary

import com.jjundev.oneclickeng.core.network.SectionOutcome
import com.jjundev.oneclickeng.core.network.SummaryEvent
import com.jjundev.oneclickeng.core.network.SummaryRequest
import com.jjundev.oneclickeng.core.network.SummaryStream
import com.jjundev.oneclickeng.core.settings.FakeSummarySaveSettingsRepository
import com.jjundev.oneclickeng.core.time.FakeElapsedClock
import com.jjundev.oneclickeng.feature.gamification.AccrualSnapshot
import com.jjundev.oneclickeng.feature.gamification.StudytimeRepository
import com.jjundev.oneclickeng.feature.reminder.ReminderOrchestrator
import com.jjundev.oneclickeng.feature.reminder.ReminderPromptDecision
import com.jjundev.oneclickeng.feature.reminder.ReminderRunResult
import com.jjundev.oneclickeng.feature.reminder.data.ReminderConfig
import com.jjundev.oneclickeng.feature.session.analytics.LatencyAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.NoOpSavedCardAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.NoOpSessionFunnelAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.RecordingLatencyAnalytics
import com.jjundev.oneclickeng.feature.session.feedback.TurnFeedbackBuffer
import com.jjundev.oneclickeng.feature.session.saved.FakeSavedCardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

private class LatencyFakeSummaryStream : SummaryStream {
    private val channels = mutableListOf<Channel<SummaryEvent>>()

    override fun events(request: SummaryRequest): Flow<SummaryEvent> {
        val channel = Channel<SummaryEvent>(Channel.UNLIMITED)
        channels += channel
        return channel.consumeAsFlow()
    }

    fun push(event: SummaryEvent) = channels.last().trySend(event)

    fun end() = channels.last().close()
}

private class LatencyFakeBookmarkSource : BookmarkSource {
    override suspend fun latestSentences(
        sessionId: String,
        limit: Int,
    ): List<BookmarkCard> = emptyList()
}

private class LatencyFakeLedger : CompletionLedger {
    override fun recordCompletion(
        sessionId: String,
        difficulty: String,
        modeId: String,
    ) = Unit
}

private class LatencyFakeStudytimeRepository : StudytimeRepository {
    override suspend fun recordSession(
        sessionId: String,
        elapsedSeconds: Long,
        dayKey: String,
    ) = AccrualSnapshot(todaySeconds = 0, streak = 0, todaySecondsBefore = 0, streakStatic = false)

    override suspend fun seedFromServerIfEmpty() = Unit

    override suspend fun drain() = Unit

    override suspend fun reconcileAfterMerge() = Unit

    override suspend fun resetMetrics() = Unit
}

private class LatencyFakeReminderOrchestrator : ReminderOrchestrator {
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
class SummaryLatencyTest {
    private fun TestScope.coordScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private fun store(): SessionTurnBufferStore =
        SessionTurnBufferStore().apply {
            startSession("s1")
            record(
                "커피 주세요",
                "One coffee",
                TurnFeedbackBuffer(slimScore = 80, correctedText = "a", naturalExpression = "b"),
            )
        }

    private fun coordinator(
        scope: CoroutineScope,
        stream: LatencyFakeSummaryStream,
        clock: FakeElapsedClock,
        latency: RecordingLatencyAnalytics,
    ) = SummaryCoordinator(
        stream,
        store(),
        LatencyFakeBookmarkSource(),
        LatencyFakeLedger(),
        FakeSavedCardRepository(),
        FakeSummarySaveSettingsRepository(),
        LatencyFakeStudytimeRepository(),
        LatencyFakeReminderOrchestrator(),
        scope,
        NoOpSessionFunnelAnalytics(),
        NoOpSavedCardAnalytics(),
        clock,
        latency,
    )

    private fun done() = SummaryEvent.Done(SectionOutcome.Ok, SectionOutcome.Ok, SectionOutcome.Ok)

    @Test
    fun `first attempt done logs summary_latency_ms successful`() =
        runTest {
            val stream = LatencyFakeSummaryStream()
            val clock = FakeElapsedClock(now = 50L)
            val latency = RecordingLatencyAnalytics()
            val coordinator = coordinator(coordScope(), stream, clock, latency)

            coordinator.start(
                sessionId = "s1",
                difficulty = "normal",
                modeId = "default",
                accrual = AccrualStrip(streakDays = 1, xp = 10),
            )
            runCurrent()
            clock.advance(1_800L)
            stream.push(done())
            runCurrent()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("summary", LatencyAnalytics.OUTCOME_SUCCESSFUL, 1_800L)),
                latency.calls,
            )
        }

    @Test
    fun `first attempt stream closing with nothing arrived logs summary_latency_ms failed`() =
        runTest {
            val stream = LatencyFakeSummaryStream()
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator = coordinator(coordScope(), stream, clock, latency)

            coordinator.start(
                sessionId = "s1",
                difficulty = "normal",
                modeId = "default",
                accrual = AccrualStrip(streakDays = 1, xp = 10),
            )
            runCurrent()
            clock.advance(2_500L)
            stream.end()
            advanceUntilIdle()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("summary", LatencyAnalytics.OUTCOME_FAILED, 2_500L)),
                latency.calls,
            )
        }

    @Test
    fun `retry after first attempt does not log a second summary_latency_ms`() =
        runTest {
            val stream = LatencyFakeSummaryStream()
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator = coordinator(coordScope(), stream, clock, latency)

            coordinator.start(
                sessionId = "s1",
                difficulty = "normal",
                modeId = "default",
                accrual = AccrualStrip(streakDays = 1, xp = 10),
            )
            runCurrent()
            stream.push(SummaryEvent.Done(SectionOutcome.Failed, SectionOutcome.Ok, SectionOutcome.Ok))
            runCurrent()
            assertEquals(1, latency.calls.size)

            coordinator.retry(SummarySection.Expression)
            runCurrent()
            stream.push(done())
            runCurrent()

            assertEquals(1, latency.calls.size)
        }
}

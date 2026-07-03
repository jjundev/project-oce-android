package com.jjundev.oneclickeng.feature.reminder

import com.jjundev.oneclickeng.feature.reminder.data.ReminderCache
import com.jjundev.oneclickeng.feature.reminder.data.ReminderConfig
import com.jjundev.oneclickeng.feature.reminder.data.ReminderStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReminderOrchestratorTest {
    @Test
    fun `config exposes store config`() =
        runTest {
            val store = FakeReminderStore(configValue = ReminderConfig(enabled = true, hour = 8, minute = 30))
            val orchestrator = orchestrator(store)

            assertEquals(store.configValue, orchestrator.config.first())
        }

    @Test
    fun `prompt hidden does not log analytics`() =
        runTest {
            val store = FakeReminderStore(prompt = false)
            val analytics = RecordingReminderAnalytics()
            val orchestrator = orchestrator(store, analytics = analytics)

            assertEquals(ReminderPromptDecision.DoNotShow, orchestrator.evaluateOptInPrompt())
            assertTrue(analytics.promptShownCounts.isEmpty())
        }

    @Test
    fun `prompt shown logs analytics once for the evaluation`() =
        runTest {
            val store = FakeReminderStore(prompt = true)
            val analytics = RecordingReminderAnalytics()
            val orchestrator = orchestrator(store, analytics = analytics)

            assertEquals(ReminderPromptDecision.ShowPrompt(), orchestrator.evaluateOptInPrompt())
            assertEquals(listOf(2), analytics.promptShownCounts)
        }

    @Test
    fun `accept resolves opt-in only`() =
        runTest {
            val store = FakeReminderStore()

            orchestrator(store).acceptOptIn()

            assertTrue(store.optInResolved)
        }

    @Test
    fun `dismiss resolves opt-in and logs cached streak`() =
        runTest {
            val store = FakeReminderStore(cacheValue = ReminderCache(lastStudyDate = null, streak = 7))
            val analytics = RecordingReminderAnalytics()

            orchestrator(store, analytics = analytics).dismissOptIn()

            assertTrue(store.optInResolved)
            assertEquals(listOf(OptInCall(enabled = false, streak = 7)), analytics.optInCalls)
        }

    @Test
    fun `enable resolves opt-in enables schedules and logs streak`() =
        runTest {
            val store =
                FakeReminderStore(
                    configValue = ReminderConfig(enabled = false, hour = 21, minute = 15),
                    cacheValue = ReminderCache(lastStudyDate = null, streak = 4),
                )
            val schedule = RecordingReminderSchedule()
            val analytics = RecordingReminderAnalytics()

            orchestrator(store, schedule = schedule, analytics = analytics).enableReminder()

            assertTrue(store.optInResolved)
            assertEquals(true, store.configValue.enabled)
            assertEquals(listOf(ScheduleCall(21, 15)), schedule.scheduleCalls)
            assertEquals(listOf(OptInCall(enabled = true, streak = 4)), analytics.optInCalls)
        }

    @Test
    fun `disable turns off and cancels schedule`() =
        runTest {
            val store = FakeReminderStore(configValue = ReminderConfig(enabled = true, hour = 20, minute = 0))
            val schedule = RecordingReminderSchedule()

            orchestrator(store, schedule = schedule).disableReminder()

            assertEquals(false, store.configValue.enabled)
            assertEquals(1, schedule.cancelCalls)
        }

    @Test
    fun `time change persists logs and reschedules when enabled`() =
        runTest {
            val store = FakeReminderStore(configValue = ReminderConfig(enabled = true, hour = 20, minute = 0))
            val schedule = RecordingReminderSchedule()
            val analytics = RecordingReminderAnalytics()

            orchestrator(store, schedule = schedule, analytics = analytics).setReminderTime(8, 30)

            assertEquals(ReminderConfig(enabled = true, hour = 8, minute = 30), store.configValue)
            assertEquals(listOf(TimeSetCall(8, 30)), analytics.timeSetCalls)
            assertEquals(listOf(ScheduleCall(8, 30)), schedule.scheduleCalls)
        }

    @Test
    fun `time change does not reschedule when disabled`() =
        runTest {
            val store = FakeReminderStore(configValue = ReminderConfig.DISABLED)
            val schedule = RecordingReminderSchedule()

            orchestrator(store, schedule = schedule).setReminderTime(8, 30)

            assertTrue(schedule.scheduleCalls.isEmpty())
        }

    @Test
    fun `permission asked write delegates to store`() =
        runTest {
            val store = FakeReminderStore()
            val orchestrator = orchestrator(store)

            orchestrator.markPermissionAsked()
            assertEquals(true, store.permissionAsked)
        }

    @Test
    fun `repair schedule is no-op when disabled`() =
        runTest {
            val store = FakeReminderStore(configValue = ReminderConfig.DISABLED)
            val schedule = RecordingReminderSchedule()

            orchestrator(store, schedule = schedule).repairSchedule()

            assertTrue(schedule.scheduleCalls.isEmpty())
        }

    @Test
    fun `repair schedule schedules when enabled`() =
        runTest {
            val store = FakeReminderStore(configValue = ReminderConfig(enabled = true, hour = 9, minute = 45))
            val schedule = RecordingReminderSchedule()

            orchestrator(store, schedule = schedule).repairSchedule()

            assertEquals(listOf(ScheduleCall(9, 45)), schedule.scheduleCalls)
        }

    @Test
    fun `timezone receiver uses dedicated timezone repair action`() =
        runTest {
            val store = FakeReminderStore(configValue = ReminderConfig(enabled = true, hour = 7, minute = 5))
            val schedule = RecordingReminderSchedule()

            orchestrator(store, schedule = schedule).handleTimezoneChanged()

            assertEquals(listOf(ScheduleCall(7, 5)), schedule.scheduleCalls)
        }

    @Test
    fun `due reminder disabled is a no-op`() =
        runTest {
            val store = FakeReminderStore(configValue = ReminderConfig.DISABLED)
            val schedule = RecordingReminderSchedule()
            val notifications = RecordingNotificationSink()

            val result = orchestrator(store, schedule = schedule, notifications = notifications).runDueReminder()

            assertEquals(ReminderRunResult.DisabledNoOp, result)
            assertTrue(schedule.scheduleCalls.isEmpty())
            assertTrue(notifications.posts.isEmpty())
        }

    @Test
    fun `due reminder skips studied today but reschedules`() =
        runTest {
            val today = LocalDate.now(ReminderLogic.KST)
            val store =
                FakeReminderStore(
                    configValue = ReminderConfig(enabled = true, hour = 20, minute = 0),
                    cacheValue = ReminderCache(lastStudyDate = today, streak = 3),
                )
            val schedule = RecordingReminderSchedule()
            val analytics = RecordingReminderAnalytics()
            val notifications = RecordingNotificationSink()

            val result =
                orchestrator(store, schedule = schedule, notifications = notifications, analytics = analytics)
                    .runDueReminder()

            assertEquals(ReminderRunResult.SkippedStudiedToday, result)
            assertEquals(listOf(ReminderSkipReason.STUDIED_TODAY), analytics.skippedReasons)
            assertTrue(notifications.posts.isEmpty())
            assertEquals(listOf(ScheduleCall(20, 0)), schedule.scheduleCalls)
        }

    @Test
    fun `due reminder cache miss logs posts and reschedules`() =
        runTest {
            val store =
                FakeReminderStore(
                    configValue = ReminderConfig(enabled = true, hour = 20, minute = 0),
                    cacheValue = ReminderCache(lastStudyDate = null, streak = null),
                )
            val schedule = RecordingReminderSchedule()
            val analytics = RecordingReminderAnalytics()
            val notifications = RecordingNotificationSink()

            val result =
                orchestrator(store, schedule = schedule, notifications = notifications, analytics = analytics)
                    .runDueReminder()

            assertEquals(ReminderRunResult.FiredCacheMiss, result)
            assertEquals(listOf(ReminderSkipReason.CACHE_MISS), analytics.skippedReasons)
            assertEquals(1, notifications.posts.size)
            assertEquals(listOf(ScheduleCall(20, 0)), schedule.scheduleCalls)
        }

    @Test
    fun `due reminder normal fire posts and reschedules`() =
        runTest {
            val today = LocalDate.now(ReminderLogic.KST)
            val store =
                FakeReminderStore(
                    configValue = ReminderConfig(enabled = true, hour = 20, minute = 0),
                    cacheValue = ReminderCache(lastStudyDate = today.minusDays(1), streak = 3),
                )
            val schedule = RecordingReminderSchedule()
            val analytics = RecordingReminderAnalytics()
            val notifications = RecordingNotificationSink()

            val result =
                orchestrator(store, schedule = schedule, notifications = notifications, analytics = analytics)
                    .runDueReminder()

            assertEquals(ReminderRunResult.Fired, result)
            assertTrue(analytics.skippedReasons.isEmpty())
            assertEquals(1, notifications.posts.size)
            assertEquals(listOf(ScheduleCall(20, 0)), schedule.scheduleCalls)
        }

    @Test
    fun `record session completion delegates to store`() =
        runTest {
            val store = FakeReminderStore()
            val date = LocalDate.of(2026, 7, 3)

            orchestrator(store).recordSessionCompleted(streak = 5, lastStudyDate = date)

            assertEquals(listOf(CompletionCall(streak = 5, date = date)), store.completionCalls)
        }

    private fun orchestrator(
        store: FakeReminderStore = FakeReminderStore(),
        schedule: RecordingReminderSchedule = RecordingReminderSchedule(),
        notifications: RecordingNotificationSink = RecordingNotificationSink(),
        analytics: RecordingReminderAnalytics = RecordingReminderAnalytics(),
    ): DefaultReminderOrchestrator = DefaultReminderOrchestrator(store, schedule, notifications, analytics)
}

private data class ScheduleCall(val hour: Int, val minute: Int)

private data class TimeSetCall(val hour: Int, val minute: Int)

private data class OptInCall(val enabled: Boolean, val streak: Int)

private data class CompletionCall(val streak: Int, val date: LocalDate)

private data class NotificationPost(val cache: ReminderCache, val today: LocalDate)

private class FakeReminderStore(
    configValue: ReminderConfig = ReminderConfig.DISABLED,
    var cacheValue: ReminderCache = ReminderCache(lastStudyDate = null, streak = null),
    var prompt: Boolean = false,
) : ReminderStore {
    private val configFlow = MutableStateFlow(configValue)
    var optInResolved = false
    var permissionAsked = false
    val completionCalls = mutableListOf<CompletionCall>()

    var configValue: ReminderConfig
        get() = configFlow.value
        set(value) {
            configFlow.value = value
        }

    override val config: Flow<ReminderConfig> = configFlow

    override suspend fun currentConfig(): ReminderConfig = configValue

    override suspend fun recordSessionCompleted(
        streak: Int,
        lastStudyDate: LocalDate,
    ) {
        completionCalls += CompletionCall(streak, lastStudyDate)
    }

    override suspend fun shouldPromptOptIn(): Boolean = prompt

    override suspend fun markOptInResolved() {
        optInResolved = true
    }

    override suspend fun setEnabled(enabled: Boolean) {
        configValue = configValue.copy(enabled = enabled)
    }

    override suspend fun setTime(
        hour: Int,
        minute: Int,
    ) {
        configValue = configValue.copy(hour = hour, minute = minute)
    }

    override suspend fun wasPermissionAsked(): Boolean = permissionAsked

    override suspend fun markPermissionAsked() {
        permissionAsked = true
    }

    override suspend fun cacheSnapshot(): ReminderCache = cacheValue
}

private class RecordingReminderSchedule : ReminderSchedule {
    val scheduleCalls = mutableListOf<ScheduleCall>()
    var cancelCalls = 0

    override fun schedule(
        hour: Int,
        minute: Int,
    ) {
        scheduleCalls += ScheduleCall(hour, minute)
    }

    override fun cancel() {
        cancelCalls += 1
    }
}

private class RecordingNotificationSink : ReminderNotificationSink {
    val posts = mutableListOf<NotificationPost>()

    override fun post(
        cache: ReminderCache,
        today: LocalDate,
    ) {
        posts += NotificationPost(cache, today)
    }
}

private class RecordingReminderAnalytics : ReminderAnalytics {
    val promptShownCounts = mutableListOf<Int>()
    val optInCalls = mutableListOf<OptInCall>()
    val timeSetCalls = mutableListOf<TimeSetCall>()
    val skippedReasons = mutableListOf<String>()

    override fun promptShown(completedSessionCount: Int) {
        promptShownCounts += completedSessionCount
    }

    override fun optInResult(
        enabled: Boolean,
        referencedStreak: Int,
    ) {
        optInCalls += OptInCall(enabled, referencedStreak)
    }

    override fun timeSet(
        hour: Int,
        minute: Int,
    ) {
        timeSetCalls += TimeSetCall(hour, minute)
    }

    override fun fireSkipped(reason: String) {
        skippedReasons += reason
    }
}

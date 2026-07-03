package com.jjundev.oneclickeng.feature.reminder

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.jjundev.oneclickeng.feature.reminder.data.ReminderCache
import com.jjundev.oneclickeng.feature.reminder.data.ReminderConfig
import com.jjundev.oneclickeng.feature.reminder.data.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * ReminderWorker 와이어링 검증(notification-reminder.md §4). 스킵/발화 분기, 항상 재예약, 항상 success.
 * SDK 26 로 고정(런타임 알림 권한 없음 → notifier 즉시 게시). WorkManagerTestInitHelper 로 실제
 * 재예약 enqueue 를 관측한다.
 */
@RunWith(RobolectricTestRunner::class)
// 스텁 Application 사용 — 실 OceApp 은 onCreate 에서 FirebaseApp.getInstance()/Hilt 주입을 건드려
// Robolectric 격리 환경에서 크래시하므로 배제한다.
@Config(sdk = [26], application = Application::class)
class ReminderWorkerTest {
    private lateinit var context: Context
    private lateinit var repository: FakeReminderRepository
    private lateinit var analytics: RecordingReminderAnalytics
    private lateinit var notifier: ReminderNotifier
    private lateinit var scheduler: ReminderScheduler

    private val today = LocalDate.now(ReminderLogic.KST)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().build(),
        )
        analytics = RecordingReminderAnalytics()
        notifier = ReminderNotifier(context)
        scheduler = ReminderScheduler(context)
        repository =
            FakeReminderRepository(
                configValue = ReminderConfig(enabled = true, hour = 20, minute = 0),
                cacheValue = ReminderCache(lastStudyDate = null, streak = null),
            )
    }

    private fun runWorker(): ListenableWorker.Result {
        val worker =
            TestListenableWorkerBuilder<ReminderWorker>(context)
                .setWorkerFactory(
                    object : WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker =
                            ReminderWorker(
                                appContext,
                                workerParameters,
                                repository,
                                notifier,
                                scheduler,
                                analytics,
                            )
                    },
                )
                .build()
        return runBlocking { worker.doWork() }
    }

    private fun postedCount(): Int {
        val nm = context.getSystemService(NotificationManager::class.java)
        return shadowOf(nm).size()
    }

    private fun rescheduled(): Boolean {
        val infos =
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(ReminderScheduler.UNIQUE_NAME)
                .get()
        return infos.any { it.state == WorkInfo.State.ENQUEUED }
    }

    @Test
    fun `studied today skips notification but still reschedules`() {
        repository.cacheValue = ReminderCache(lastStudyDate = today, streak = 3)

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf(ReminderWorker.REASON_STUDIED_TODAY), analytics.skipped)
        assertEquals(0, postedCount())
        assertTrue(rescheduled())
    }

    @Test
    fun `not studied today fires and reschedules`() {
        repository.cacheValue = ReminderCache(lastStudyDate = today.minusDays(1), streak = 3)

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(analytics.skipped.isEmpty())
        assertEquals(1, postedCount())
        assertTrue(rescheduled())
    }

    @Test
    fun `cache miss fires and logs cache_miss and reschedules`() {
        repository.cacheValue = ReminderCache(lastStudyDate = null, streak = null)

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf(ReminderWorker.REASON_CACHE_MISS), analytics.skipped)
        assertEquals(1, postedCount())
        assertTrue(rescheduled())
    }

    @Test
    fun `disabled reminder does nothing`() {
        repository.configValue = ReminderConfig(enabled = false, hour = 20, minute = 0)
        repository.cacheValue = ReminderCache(lastStudyDate = today.minusDays(1), streak = 3)

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(analytics.skipped.isEmpty())
        assertEquals(0, postedCount())
        assertEquals(false, rescheduled())
    }
}

private class RecordingReminderAnalytics : ReminderAnalytics {
    val skipped = mutableListOf<String>()

    override fun promptShown(completedSessionCount: Int) = Unit

    override fun optInResult(
        enabled: Boolean,
        referencedStreak: Int,
    ) = Unit

    override fun timeSet(
        hour: Int,
        minute: Int,
    ) = Unit

    override fun fireSkipped(reason: String) {
        skipped += reason
    }
}

private class FakeReminderRepository(
    var configValue: ReminderConfig,
    var cacheValue: ReminderCache,
) : ReminderRepository {
    override val config: Flow<ReminderConfig> get() = flowOf(configValue)

    override suspend fun currentConfig(): ReminderConfig = configValue

    override suspend fun recordSessionCompleted(
        streak: Int,
        lastStudyDate: LocalDate,
    ) = Unit

    override suspend fun shouldPromptOptIn(): Boolean = false

    override suspend fun markOptInResolved() = Unit

    override suspend fun setEnabled(enabled: Boolean) {
        configValue = configValue.copy(enabled = enabled)
    }

    override suspend fun setTime(
        hour: Int,
        minute: Int,
    ) {
        configValue = configValue.copy(hour = hour, minute = minute)
    }

    override suspend fun wasPermissionAsked(): Boolean = false

    override suspend fun markPermissionAsked() = Unit

    override suspend fun cacheSnapshot(): ReminderCache = cacheValue
}

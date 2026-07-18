package com.jjundev.oneclickeng.feature.reminder

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.jjundev.oneclickeng.feature.reminder.data.ReminderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Worker adapter test: product policy is covered by [ReminderOrchestratorTest]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class ReminderWorkerTest {
    @Test
    fun `doWork delegates to orchestrator and returns success`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        val orchestrator = RecordingReminderOrchestrator()
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
                                orchestrator,
                            )
                    },
                )
                .build()

        val result = runBlocking { worker.doWork() }

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, orchestrator.runDueCalls)
    }
}

private class RecordingReminderOrchestrator : ReminderOrchestrator {
    var runDueCalls = 0

    override val config: Flow<ReminderConfig> = flowOf(ReminderConfig.DISABLED)

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

    override suspend fun runDueReminder(): ReminderRunResult {
        runDueCalls += 1
        return ReminderRunResult.DisabledNoOp
    }

    override suspend fun recordSessionCompleted(
        streak: Int,
        lastStudyDate: LocalDate,
    ) = Unit

    override suspend fun recordSavedReviewText(text: String) = Unit

    override suspend fun clearProgressCache() = Unit
}

package com.jjundev.oneclickeng.feature.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** WorkManager adapter for the daily reminder run. Product policy lives in [ReminderOrchestrator]. */
@HiltWorker
class ReminderWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val reminderOrchestrator: ReminderOrchestrator,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            reminderOrchestrator.runDueReminder()
            return Result.success()
        }
    }

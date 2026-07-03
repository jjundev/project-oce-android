package com.jjundev.oneclickeng.feature.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 리마인더 WorkManager 스케줄러(notification-reminder.md §4). 단일 고유작업 [UNIQUE_NAME] 을
 * OneTime + REPLACE 로 예약한다. 정확알람(SCHEDULE_EXACT_ALARM) 미사용 — Doze/OEM 배터리에서 ~1-2h
 * 지연 수용(결정 #11).
 *
 * 다음 발화 시각은 **기기 로컬 wall-clock** 기준으로 계산한다([ReminderLogic.computeInitialDelay], 결정 #6).
 * [nowProvider] 는 테스트 주입용 seam.
 */
@Singleton
class ReminderScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ReminderSchedule {
        /** 테스트에서 고정 시각을 주입하기 위한 seam. 프로덕션은 기기 로컬 현재시각. */
        var nowProvider: () -> ZonedDateTime = { ZonedDateTime.now() }

        override fun schedule(
            hour: Int,
            minute: Int,
        ) {
            val delay = ReminderLogic.computeInitialDelay(nowProvider(), hour, minute)
            val request =
                OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(delay)
                    .addTag(TAG)
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        override fun cancel() {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        companion object {
            const val UNIQUE_NAME = "daily_reminder"
            const val TAG = "reminder"
        }
    }

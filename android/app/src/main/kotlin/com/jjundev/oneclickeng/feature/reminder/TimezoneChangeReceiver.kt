package com.jjundev.oneclickeng.feature.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 시간대 변경 시 재예약(notification-reminder.md §4). 영속된 다음-발화 시각이 시간대 이동으로 어긋나므로,
 * `reminderEnabled` 면 고유작업을 다시 예약한다. **부팅 리시버는 두지 않는다** — WorkManager 가 영속
 * OneTime 을 부팅 후 자동 재enqueue 한다(결정 #12).
 */
@AndroidEntryPoint
class TimezoneChangeReceiver : BroadcastReceiver() {
    @Inject lateinit var reminderOrchestrator: ReminderOrchestrator

    @Inject lateinit var appScope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED) return
        val pending = goAsync()
        appScope.launch {
            try {
                reminderOrchestrator.handleTimezoneChanged()
            } finally {
                pending.finish()
            }
        }
    }
}

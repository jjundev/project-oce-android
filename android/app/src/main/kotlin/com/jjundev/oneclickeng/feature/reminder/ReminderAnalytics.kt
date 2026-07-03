package com.jjundev.oneclickeng.feature.reminder

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 리마인더 계측 seam(notification-reminder.md §8). FirebaseAnalytics 를 직접 감싸는 얇은 인터페이스로,
 * 이벤트명·파라미터 계약을 한곳에 두고 Worker/ViewModel 을 계측 백엔드 없이 단위 테스트할 수 있게 한다
 * (결정 #5 의 "새 계측 인프라 없음"은 유지 — 싱크는 여전히 FirebaseAnalytics, 이건 DI/테스트용 seam).
 */
interface ReminderAnalytics {
    fun promptShown(completedSessionCount: Int)

    fun optInResult(
        enabled: Boolean,
        referencedStreak: Int,
    )

    fun timeSet(
        hour: Int,
        minute: Int,
    )

    fun fireSkipped(reason: String)
}

@Singleton
class FirebaseReminderAnalytics
    @Inject
    constructor(
        private val analytics: FirebaseAnalytics,
    ) : ReminderAnalytics {
        override fun promptShown(completedSessionCount: Int) {
            analytics.logEvent(
                "reminder_prompt_shown",
                Bundle().apply { putLong("completed_session_count", completedSessionCount.toLong()) },
            )
        }

        override fun optInResult(
            enabled: Boolean,
            referencedStreak: Int,
        ) {
            analytics.logEvent(
                "reminder_opt_in_result",
                Bundle().apply {
                    putBoolean("enabled", enabled)
                    putLong("referenced_streak", referencedStreak.toLong())
                },
            )
        }

        override fun timeSet(
            hour: Int,
            minute: Int,
        ) {
            analytics.logEvent(
                "reminder_time_set",
                Bundle().apply {
                    putLong("hour", hour.toLong())
                    putLong("minute", minute.toLong())
                },
            )
        }

        override fun fireSkipped(reason: String) {
            analytics.logEvent(
                "reminder_fire_skipped",
                Bundle().apply { putString("reason", reason) },
            )
        }
    }

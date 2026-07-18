package com.jjundev.oneclickeng.feature.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jjundev.oneclickeng.MainActivity
import com.jjundev.oneclickeng.R
import com.jjundev.oneclickeng.feature.home.topic.TopicCatalog
import com.jjundev.oneclickeng.feature.reminder.data.ReminderCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 리마인더 알림 게시(notification-reminder.md §5). 채널 lazy 생성, 캐시 기반 body 분기, 탭→홈 진입
 * PendingIntent 를 빌드한다. best-effort — 권한/게시 실패는 조용히 삼킨다(worker 는 항상 success 반환).
 */
@Singleton
class ReminderNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ReminderNotificationSink {
        /** 채널 생성(멱등). 최초 게시 전에 호출한다. */
        fun ensureChannel() {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    // DEFAULT: 알림센터+소리는 주되 heads-up 팝업은 띄우지 않는다(불안 페르소나 비침습, §5).
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            manager.createNotificationChannel(channel)
        }

        /**
         * 캐시 기준 콘텐츠로 알림 게시. [today] 는 KST 오늘(worker 가 [ReminderLogic.KST] 로 계산해 전달).
         * 33+ 에서 권한 미보유면 게시하지 않는다(best-effort).
         */
        override fun post(
            cache: ReminderCache,
            today: LocalDate,
        ) {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            ensureChannel()
            val situationTitle =
                TopicCatalog.recommended(dayIndex = today.toEpochDay(), count = 1).firstOrNull()?.titleKo
            val content =
                ReminderLogic.buildContent(
                    streak = cache.streak,
                    lastStudyDate = cache.lastStudyDate,
                    today = today,
                    milestoneStreak = cache.milestoneStreak,
                    lastSavedReviewText = cache.lastSavedReviewText,
                    recommendedSituationTitle = situationTitle,
                )
            val notification =
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_local_fire_department)
                    .setContentTitle(content.title)
                    .setContentText(content.body)
                    .setColor(ContextCompat.getColor(context, R.color.reminder_accent))
                    .setContentIntent(homePendingIntent())
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
                // Best effort: permission can be revoked between the check and notify.
            }
        }

        /** 탭 시 MainActivity 로 진입 + nav=home extra. 앱 내부 자족 계약(§5, 딥링크 스킴 불필요). */
        private fun homePendingIntent(): PendingIntent {
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_NAV, MainActivity.NAV_HOME)
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        companion object {
            const val CHANNEL_ID = "learning_reminder"
            const val CHANNEL_NAME = "학습 리마인더"
            const val NOTIFICATION_ID = 3007
        }
    }

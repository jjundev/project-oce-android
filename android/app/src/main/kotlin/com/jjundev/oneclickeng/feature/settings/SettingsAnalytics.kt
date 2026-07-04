package com.jjundev.oneclickeng.feature.settings

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 설정 계측 seam(M3-09, settings-data-account.md §10). FirebaseAnalytics 를 얇게 감싸 이벤트명·파라미터 계약을
 * 한곳에 두고 VM 을 계측 백엔드 없이 단위 테스트할 수 있게 한다(ReminderAnalytics 미러). 리마인더 on/off 는
 * `reminder_opt_in_result` 를 ReminderOrchestrator 가 이미 emit 하므로 여기서 중복 발화하지 않는다.
 */
interface SettingsAnalytics {
    fun ttsQualityChanged(provider: String)

    fun ttsSpeedChanged(speed: Float)

    fun muteToggled(muted: Boolean)

    fun metricsReset()

    fun cardsPurged(
        scope: String,
        count: Int,
    )

    fun accountDeleted()

    fun logout()
}

@Singleton
class FirebaseSettingsAnalytics
    @Inject
    constructor(
        private val analytics: FirebaseAnalytics,
    ) : SettingsAnalytics {
        override fun ttsQualityChanged(provider: String) {
            analytics.logEvent("settings_tts_quality_changed", Bundle().apply { putString("provider", provider) })
        }

        override fun ttsSpeedChanged(speed: Float) {
            analytics.logEvent("tts_speed_changed", Bundle().apply { putDouble("speed", speed.toDouble()) })
        }

        override fun muteToggled(muted: Boolean) {
            analytics.logEvent("mute_toggled", Bundle().apply { putBoolean("muted", muted) })
        }

        override fun metricsReset() {
            analytics.logEvent("metrics_reset", Bundle())
        }

        override fun cardsPurged(
            scope: String,
            count: Int,
        ) {
            analytics.logEvent(
                "cards_purged",
                Bundle().apply {
                    putString("scope", scope)
                    putLong("count", count.toLong())
                },
            )
        }

        override fun accountDeleted() {
            analytics.logEvent("account_deleted", Bundle())
        }

        override fun logout() {
            analytics.logEvent("logout", Bundle())
        }
    }

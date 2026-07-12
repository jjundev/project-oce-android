package com.jjundev.oneclickeng.feature.settings

import com.jjundev.oneclickeng.core.settings.TtsQuality
import com.jjundev.oneclickeng.core.settings.TtsSettings
import com.jjundev.oneclickeng.feature.reminder.data.ReminderConfig
import com.jjundev.oneclickeng.feature.settings.data.PurgeScope

/**
 * 설정 화면 상태(M3-09). 음성/알림 필드는 각 저장소 Flow 에서, 프로필/계정 필드는 일회성 로드에서 채워진다.
 * [voiceControlsEnabled] 는 음소거 파생값을 VM 이 소유(Composable 은 렌더만 — D4 준수).
 */
data class SettingsUiState(
    val loading: Boolean = true,
    val nickname: String = "",
    val ttsQuality: TtsQuality = TtsQuality.SERVER,
    val speechRate: Float = TtsSettings.DEFAULT_SPEECH_RATE,
    val ttsMuted: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = ReminderConfig.DEFAULT_HOUR,
    val reminderMinute: Int = ReminderConfig.DEFAULT_MINUTE,
    val isGuest: Boolean = true,
    /** 게스트→Google 이관 미완(pendingGuestMerge 마커 존재) → "진도 다시 옮기기" 행 노출. */
    val showRetryMerge: Boolean = false,
    /** null 이 아니면 카드 정리 확인 다이얼로그를 띄운다(범위 + 영향 건수). */
    val purgeConfirm: PurgeConfirm? = null,
    /** 카드 정리 시트가 열릴 때 3범위 카운트를 선로딩(null=미로딩/로딩중). */
    val purgeCounts: Map<PurgeScope, Int>? = null,
    val purgeInFlight: Boolean = false,
    val metricsResetInFlight: Boolean = false,
    val deleteInFlight: Boolean = false,
    /** 일회성 사용자 메시지(스낵바). 표시 후 [SettingsViewModel.consumeMessage] 로 소비. */
    val message: SettingsMessage? = null,
) {
    /** 음소거 중이면 음질·속도 컨트롤 비활성(회색). */
    val voiceControlsEnabled: Boolean get() = !ttsMuted
}

/** 카드 정리 확인 상태: 선택 범위 + 실행 직전 재쿼리한 영향 건수. */
data class PurgeConfirm(
    val scope: PurgeScope,
    val count: Int,
)

/** 설정 동작 결과 메시지. Composable 이 문자열 리소스로 매핑한다(문자열은 strings.xml 소유). */
sealed interface SettingsMessage {
    data class CardsPurged(val count: Int) : SettingsMessage

    data object PurgeFailed : SettingsMessage

    data object MetricsResetFailed : SettingsMessage

    data object DeleteFailed : SettingsMessage

    data object LogoutFailed : SettingsMessage
}

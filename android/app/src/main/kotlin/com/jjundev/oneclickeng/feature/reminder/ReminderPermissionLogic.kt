package com.jjundev.oneclickeng.feature.reminder

import android.os.Build

/**
 * POST_NOTIFICATIONS 권한 상태기계의 **순수 판정**(notification-reminder.md §3). 안드로이드 API 를
 * 직접 호출하지 않고 이미 조회한 값(sdk/granted/asked/rationale)만 받아 다음 단계를 정한다 →
 * 13+/이하 분기를 JVM 단위 테스트로 반증가능하게 한다(결정 #8·#18).
 */
object ReminderPermissionLogic {
    /** 사용자가 리마인더를 켜려 할 때(opt-in 탭/토글 on)의 최초 진입 판정. */
    enum class InitialStep {
        /** API 33 미만 — 런타임 권한 없음. 즉시 켜고 채널만 만든다(§3 하단). */
        ENABLE_NO_RUNTIME,

        /** 이미 허용됨 — 즉시 켠다. */
        GRANT_NOW,

        /** 미허용 — priming 시트 먼저, 그 다음 시스템 다이얼로그(§3.3). */
        SHOW_PRIMING,
    }

    /** 시스템 다이얼로그 거부 콜백 이후의 판정(§3.4). 이 시점 `notifPermissionAsked` 는 true 로 set 된다. */
    enum class DeniedStep {
        /** rationale 표시 가능 — 토글 off 복귀 + 인라인 안내(재요청 가능). */
        SHOW_RATIONALE_INLINE,

        /** asked==true && rationale==false → 영구거부. 앱 알림 설정 딥링크 안내. */
        PERMANENTLY_DENIED,
    }

    /** API 33(TIRAMISU)부터 POST_NOTIFICATIONS 런타임 권한이 존재한다. */
    const val RUNTIME_PERMISSION_SDK = Build.VERSION_CODES.TIRAMISU

    fun initialStep(
        sdkInt: Int,
        hasPermission: Boolean,
    ): InitialStep =
        when {
            sdkInt < RUNTIME_PERMISSION_SDK -> InitialStep.ENABLE_NO_RUNTIME
            hasPermission -> InitialStep.GRANT_NOW
            else -> InitialStep.SHOW_PRIMING
        }

    fun deniedStep(showRationale: Boolean): DeniedStep =
        if (showRationale) {
            DeniedStep.SHOW_RATIONALE_INLINE
        } else {
            DeniedStep.PERMANENTLY_DENIED
        }
}

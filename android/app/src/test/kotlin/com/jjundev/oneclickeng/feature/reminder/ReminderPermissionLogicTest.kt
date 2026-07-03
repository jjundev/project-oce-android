package com.jjundev.oneclickeng.feature.reminder

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * POST_NOTIFICATIONS 상태기계 순수 판정(notification-reminder.md §3). 13+/이하 분기와 거부 후
 * rationale/영구거부 분기를 값만으로 검증한다(결정 #8). Build.VERSION_CODES 는 컴파일 상수라 순수 JVM.
 */
class ReminderPermissionLogicTest {
    private val below33 = Build.VERSION_CODES.S // 31
    private val at33 = Build.VERSION_CODES.TIRAMISU // 33

    @Test
    fun `below 33 enables without runtime request`() {
        assertEquals(
            ReminderPermissionLogic.InitialStep.ENABLE_NO_RUNTIME,
            ReminderPermissionLogic.initialStep(sdkInt = below33, hasPermission = false),
        )
    }

    @Test
    fun `below 33 ignores permission state entirely`() {
        assertEquals(
            ReminderPermissionLogic.InitialStep.ENABLE_NO_RUNTIME,
            ReminderPermissionLogic.initialStep(sdkInt = below33, hasPermission = true),
        )
    }

    @Test
    fun `33 plus with permission grants immediately`() {
        assertEquals(
            ReminderPermissionLogic.InitialStep.GRANT_NOW,
            ReminderPermissionLogic.initialStep(sdkInt = at33, hasPermission = true),
        )
    }

    @Test
    fun `33 plus without permission shows priming first`() {
        assertEquals(
            ReminderPermissionLogic.InitialStep.SHOW_PRIMING,
            ReminderPermissionLogic.initialStep(sdkInt = at33, hasPermission = false),
        )
    }

    @Test
    fun `denial with rationale shows inline retry`() {
        assertEquals(
            ReminderPermissionLogic.DeniedStep.SHOW_RATIONALE_INLINE,
            ReminderPermissionLogic.deniedStep(showRationale = true),
        )
    }

    @Test
    fun `denial without rationale is permanent`() {
        assertEquals(
            ReminderPermissionLogic.DeniedStep.PERMANENTLY_DENIED,
            ReminderPermissionLogic.deniedStep(showRationale = false),
        )
    }
}

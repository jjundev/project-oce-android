package com.jjundev.oneclickeng.ui.root

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 진입 게이트 라우팅 결정의 순수 검증(M3-02, 결정 2·3). 수용기준 "profile.level 있으면 홈, 없으면 온보딩"과
 * "레벨 부재/판독불가 → fail-open 온보딩"을 반증가능하게 고정한다.
 */
class BootStateTest {
    @Test
    fun `absent level routes to onboarding`() {
        assertEquals(BootState.NeedsOnboarding, bootStateForLevel(null))
    }

    @Test
    fun `blank level routes to onboarding (fail-open)`() {
        assertEquals(BootState.NeedsOnboarding, bootStateForLevel(""))
        assertEquals(BootState.NeedsOnboarding, bootStateForLevel("   "))
    }

    @Test
    fun `saved level routes straight to main`() {
        listOf("easy", "normal", "hard").forEach { level ->
            assertEquals(BootState.MainReady, bootStateForLevel(level))
        }
    }
}

package com.jjundev.oneclickeng.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 진리표 검증(daily-limit-ux.md §7·§2). 라이브 스냅샷 보유 게스트는 온보딩 여부와 무관하게
 * `dialogue_start_gate` (스냅샷 재개는 시작 게이트를 안 거치므로 온보딩 첫 세션 맥락이 아님).
 */
class SelectLimitSurfaceTest {
    @Test
    fun `onboarding without snapshot uses onboarding_first_session`() {
        assertEquals(
            LimitSurface.OnboardingFirstSession,
            selectLimitSurface(isOnboarding = true, hasLiveSnapshot = false),
        )
    }

    @Test
    fun `onboarding with live snapshot falls through to dialogue_start_gate`() {
        assertEquals(
            LimitSurface.DialogueStartGate,
            selectLimitSurface(isOnboarding = true, hasLiveSnapshot = true),
        )
    }

    @Test
    fun `non-onboarding without snapshot uses dialogue_start_gate`() {
        assertEquals(
            LimitSurface.DialogueStartGate,
            selectLimitSurface(isOnboarding = false, hasLiveSnapshot = false),
        )
    }

    @Test
    fun `non-onboarding with snapshot uses dialogue_start_gate`() {
        assertEquals(
            LimitSurface.DialogueStartGate,
            selectLimitSurface(isOnboarding = false, hasLiveSnapshot = true),
        )
    }
}

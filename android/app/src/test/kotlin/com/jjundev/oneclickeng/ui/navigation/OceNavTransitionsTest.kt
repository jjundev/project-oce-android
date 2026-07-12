package com.jjundev.oneclickeng.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 전환 계약 검증 — 컨테이너는 즉시 교체(무전환)라 잔상이 없어야 한다. enter/exit 모두 None 상수임을 고정한다.
 */
class OceNavTransitionsTest {
    @Test
    fun enter_isInstantNoTransition() {
        assertSame(EnterTransition.None, oceScreenEnter)
    }

    @Test
    fun exit_isHardCut() {
        assertSame(ExitTransition.None, oceScreenExit)
    }
}

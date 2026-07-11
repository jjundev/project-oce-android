package com.jjundev.oneclickeng.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import com.jjundev.oneclickeng.ui.theme.OceMotion
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 전환 팩토리 순수 검증(Compose 무관). 실제 프레임(비트맵)은 대상 아니고, reduce-motion 게이트와
 * 퇴장 하드컷 계약만 반증가능하게 고정한다. offsetY8Px 는 density=3 기준 8dp=24px 예시값.
 */
class OceNavTransitionsTest {
    private val motion = OceMotion()

    @Test
    fun reduceMotion_enter_isNone() {
        assertSame(EnterTransition.None, oceScreenEnter(motion, offsetY8Px = 24, reduceMotion = true))
    }

    @Test
    fun normalMotion_enter_isNotNone() {
        assertNotSame(EnterTransition.None, oceScreenEnter(motion, offsetY8Px = 24, reduceMotion = false))
    }

    @Test
    fun exit_isAlwaysHardCut() {
        assertSame(ExitTransition.None, oceScreenExit)
    }
}

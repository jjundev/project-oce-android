package com.jjundev.oneclickeng.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import org.junit.Assert.assertNotSame
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

    // --- 세션 요약 핸드오프 슬라이드 토큰 ---

    private val sessionRoute = "home/session?level={level}"
    private val summaryRoute = "home/summary?sessionId={sessionId}&level={level}"

    @Test
    fun summaryHandoffEnter_reduceMotion_isNoTransition() {
        assertSame(EnterTransition.None, summaryHandoffEnter(reduceMotion = true))
    }

    @Test
    fun summaryHandoffEnter_motionOn_slides() {
        assertNotSame(EnterTransition.None, summaryHandoffEnter(reduceMotion = false))
    }

    @Test
    fun sessionHandoffExit_reduceMotion_isHardCut() {
        assertSame(ExitTransition.None, sessionHandoffExit(reduceMotion = true))
    }

    @Test
    fun sessionHandoffExit_motionOn_slides() {
        assertNotSame(ExitTransition.None, sessionHandoffExit(reduceMotion = false))
    }

    @Test
    fun summaryEnterFor_fromSession_motionOn_slides() {
        assertNotSame(
            EnterTransition.None,
            summaryEnterFor(sourceRoute = sessionRoute, sessionRoute = sessionRoute, reduceMotion = false),
        )
    }

    @Test
    fun summaryEnterFor_fromOtherRoute_isNoTransition() {
        assertSame(
            EnterTransition.None,
            summaryEnterFor(sourceRoute = "other", sessionRoute = sessionRoute, reduceMotion = false),
        )
    }

    @Test
    fun summaryEnterFor_nullSource_isNoTransition() {
        assertSame(
            EnterTransition.None,
            summaryEnterFor(sourceRoute = null, sessionRoute = sessionRoute, reduceMotion = false),
        )
    }

    @Test
    fun sessionExitFor_toSummary_motionOn_slides() {
        assertNotSame(
            ExitTransition.None,
            sessionExitFor(targetRoute = summaryRoute, summaryRoute = summaryRoute, reduceMotion = false),
        )
    }

    @Test
    fun sessionExitFor_toOtherRoute_isHardCut() {
        assertSame(
            ExitTransition.None,
            sessionExitFor(targetRoute = "main_tabs", summaryRoute = summaryRoute, reduceMotion = false),
        )
    }

    @Test
    fun sessionExitFor_toSummary_reduceMotion_isHardCut() {
        assertSame(
            ExitTransition.None,
            sessionExitFor(targetRoute = summaryRoute, summaryRoute = summaryRoute, reduceMotion = true),
        )
    }
}

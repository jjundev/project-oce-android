package com.jjundev.oneclickeng.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.jjundev.oneclickeng.feature.home.HomeScreen
import com.jjundev.oneclickeng.feature.records.RecordsScreen
import com.jjundev.oneclickeng.feature.settings.SettingsScreen
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 3탭 내비게이션 그래프. 각 탭은 빈 화면 플레이스홀더로 전환된다(M0-09).
 *
 * **전환 훅(F4 미결 · 잠정 기본값):** F4(전역 Nav 전환 패턴)는 SoT에서 여전히 미결(🟠)이고 F8 스코프
 * 밖이다. 이슈는 "F4 훅"만 요구하므로, 여기서는 **교체 가능한 전환 seam** 에 사용자 승인 잠정 기본값
 * (크로스페이드 `durationBaseMs` 200ms · `easingStandard`)을 주입한다. F4 확정 시 이 enter/exit 만
 * 교체한다(SoT 재결정 아님 — 잠정 훅).
 *
 * **reduce-motion:** [reduceMotion] seam(기본값 [rememberReduceMotion])이 true 면 전환을 정적 스냅
 * ([EnterTransition.None]/[ExitTransition.None])으로 대체한다(수용기준 #4). 테스트는 이 인자를 직접
 * 주입해 정적 대체를 반증가능하게 검증한다.
 */
@Composable
fun OceNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotion(),
) {
    val durationMs = OceTheme.motion.durationBaseMs
    val easing = OceTheme.motion.easingStandard
    NavHost(
        navController = navController,
        startDestination = OceTab.Start.route,
        modifier = modifier,
        enterTransition = {
            if (reduceMotion) EnterTransition.None else fadeIn(tween(durationMs, easing = easing))
        },
        exitTransition = {
            if (reduceMotion) ExitTransition.None else fadeOut(tween(durationMs, easing = easing))
        },
    ) {
        composable(OceTab.Home.route) { HomeScreen() }
        composable(OceTab.Records.route) { RecordsScreen() }
        composable(OceTab.Settings.route) { SettingsScreen() }
    }
}

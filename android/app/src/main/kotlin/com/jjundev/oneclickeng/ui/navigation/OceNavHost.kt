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
 * 3탭 내비게이션 그래프(F8). 학습(홈)·기록·설정 탭을 전환한다. 세션 루프(생성→대화→요약)는 이 inner
 * NavHost 가 아니라 outer NavHost 의 `homeSessionGraph` 형제가 소유한다(M3-08, 풀스크린) — 홈 히어로·추천
 * 행/이어하기는 [onStartSession]/[onResume] 람다(outer NavController 배선)로 진입한다.
 *
 * **전환 훅(F4 미결 · 잠정 기본값):** 교체 가능한 전환 seam 에 잠정 기본값(크로스페이드 200ms)을 주입한다.
 * [reduceMotion] 이 true 면 정적 스냅으로 대체한다(수용기준).
 */
@Composable
fun OceNavHost(
    navController: NavHostController,
    onStartSession: (promptSeed: String, level: String, length: Int) -> Unit,
    onResume: () -> Unit,
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
        composable(OceTab.Home.route) {
            HomeScreen(
                onStartSession = onStartSession,
                onResume = onResume,
                onViewRecords = {
                    // at-limit 보조 고지 "기록 보기" → 기록 탭으로(inner 백스택 유지).
                    navController.navigate(OceTab.Records.route) {
                        launchSingleTop = true
                        popUpTo(navController.graph.startDestinationId) { inclusive = false }
                    }
                },
            )
        }
        composable(OceTab.Records.route) { RecordsScreen() }
        composable(OceTab.Settings.route) { SettingsScreen() }
    }
}

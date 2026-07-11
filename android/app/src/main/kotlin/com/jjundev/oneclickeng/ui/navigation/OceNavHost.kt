package com.jjundev.oneclickeng.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
 * **전환(F4 확정):** 프로토 `oc-fade-up`(fade + 8dp 상승, 진입만) 정합 — 공유 [oceScreenEnter]/[oceScreenExit]
 * 를 소비한다. 진입=fade+8dp↑, 퇴장=하드 컷([oceScreenExit]). [reduceMotion] 이 true 면 진입도 정적(None).
 */
@Composable
fun OceNavHost(
    navController: NavHostController,
    onStartSession: (promptSeed: String, topicLabel: String, topicEmoji: String, level: String, length: Int) -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotion(),
) {
    val motion = OceTheme.motion
    val offsetY8Px = with(LocalDensity.current) { 8.dp.roundToPx() }
    NavHost(
        navController = navController,
        startDestination = OceTab.Start.route,
        modifier = modifier,
        enterTransition = { oceScreenEnter(motion, offsetY8Px, reduceMotion) },
        exitTransition = { oceScreenExit },
        popEnterTransition = { oceScreenEnter(motion, offsetY8Px, reduceMotion) },
        popExitTransition = { oceScreenExit },
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

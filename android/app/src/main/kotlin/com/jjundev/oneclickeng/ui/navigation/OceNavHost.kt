package com.jjundev.oneclickeng.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.jjundev.oneclickeng.feature.home.HomeScreen
import com.jjundev.oneclickeng.feature.records.RecordsScreen
import com.jjundev.oneclickeng.feature.settings.SettingsScreen

/**
 * 3탭 내비게이션 그래프(F8). 학습(홈)·기록·설정 탭을 전환한다. 세션 루프(생성→대화→요약)와 복습 플로우는
 * 이 inner NavHost 가 아니라 outer NavHost 의 `homeSessionGraph`/`reviewGraph` 형제가 소유한다(M3-08/T12,
 * 풀스크린) — 홈 히어로·추천 행/이어하기는 [onStartSession]/[onResume], 기록 탭 복습 배너는 [onEnterReview]
 * 람다(outer NavController 배선)로 진입한다.
 *
 * **전환:** 컨테이너 = 즉시 교체(무전환 — 잔상 제거), 화면 등장감 = 섹션 stagger.
 */
@Composable
fun OceNavHost(
    navController: NavHostController,
    onStartSession: (promptSeed: String, topicLabel: String, topicEmoji: String, level: String, length: Int) -> Unit,
    onResume: () -> Unit,
    onEnterReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = OceTab.Start.route,
        modifier = modifier,
        enterTransition = { oceScreenEnter },
        exitTransition = { oceScreenExit },
        popEnterTransition = { oceScreenEnter },
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
        composable(OceTab.Records.route) { RecordsScreen(onEnterReview = onEnterReview) }
        composable(OceTab.Settings.route) { SettingsScreen() }
    }
}

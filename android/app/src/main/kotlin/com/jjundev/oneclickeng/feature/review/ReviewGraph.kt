package com.jjundev.oneclickeng.feature.review

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation

/** outer NavHost 에 등록되는 복습 그래프 route. */
const val REVIEW_GRAPH_ROUTE = "review_graph"

private const val REVIEW_ROUTE = "review/flow"

/** 복습 진입 route(인자 없음 — 풀은 [ReviewViewModel] 이 로드). */
fun reviewStartRoute(): String = REVIEW_ROUTE

/**
 * 복습 플로우 그래프(Task 11 [ReviewFlowScreen]) — outer NavHost 에 [MAIN_TABS_ROUTE][com.jjundev.oneclickeng.ui.root.MAIN_TABS_ROUTE]
 * 형제로 등록되는 3탭 밖 풀스크린 그래프(ADR-0008). 단일 목적지 — 인자 없이 진입하고 풀은 VM 이 자체 로드한다.
 * 닫기(`onClose`) = 그래프 pop 으로 기존 3탭 셸 복귀(홈 세션 그래프의 `exitToTabs` 패턴과 동일).
 */
fun NavGraphBuilder.reviewGraph(navController: NavHostController) {
    navigation(startDestination = REVIEW_ROUTE, route = REVIEW_GRAPH_ROUTE) {
        composable(route = REVIEW_ROUTE) {
            ReviewFlowScreen(
                onClose = { navController.popBackStack(REVIEW_GRAPH_ROUTE, inclusive = true) },
            )
        }
    }
}

package com.jjundev.oneclickeng.ui.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jjundev.oneclickeng.MainActivity
import com.jjundev.oneclickeng.feature.home.HOME_SESSION_GRAPH_ROUTE
import com.jjundev.oneclickeng.feature.home.homeSessionGraph
import com.jjundev.oneclickeng.feature.home.homeSessionResumeRoute
import com.jjundev.oneclickeng.feature.home.homeTopicRoute
import com.jjundev.oneclickeng.feature.onboarding.ONBOARDING_ROUTE
import com.jjundev.oneclickeng.feature.onboarding.onboardingGraph
import com.jjundev.oneclickeng.ui.component.OneClickOfflineBanner
import com.jjundev.oneclickeng.ui.component.OneClickProgressRing
import com.jjundev.oneclickeng.ui.component.ProgressRingMode
import com.jjundev.oneclickeng.ui.foundation.OceBottomNav
import com.jjundev.oneclickeng.ui.navigation.OceNavHost
import com.jjundev.oneclickeng.ui.navigation.OceTab

/** 3탭 셸(하단 내비 + [OceNavHost])을 담는 바깥 그래프 목적지 경로. */
const val MAIN_TABS_ROUTE = "main_tabs"

/**
 * 앱 루트 컴포저블(F8 스캐폴드). 단일 Activity 위에 **바깥 NavHost** 를 세운다. 세 목적지가 형제로 산다:
 * 3탭 셸([MAIN_TABS_ROUTE]) · 온보딩 그래프([ONBOARDING_ROUTE]) · 홈 주도 세션 그래프([HOME_SESSION_GRAPH_ROUTE]).
 *
 * **홈 = 유일 정본 진입(M3-08):** M1-09 개발 하니스 진입점은 제거됐다. 시작 목적지는 부트 게이트가 정한다
 * ([AppViewModel.uiState]): Loading=splash / NeedsOnboarding=온보딩 / MainReady=3탭. 홈 CTA·이어하기는 outer
 * NavController 를 통해 세션 그래프로 진입하고, 종료 시 그래프만 pop 해 3탭 셸을 보존한다.
 *
 * @param startRoute 명시 시작 목적지 override(테스트 seam). null 이면 부트 게이트로 결정한다.
 * @param pendingNav 알림 탭 nav 명령(M3-07). [MainTabsScaffold] 로 전달돼 홈 이동으로 소비된다.
 */
@Composable
fun AppRoot(
    startRoute: String? = null,
    pendingNav: String? = null,
    onNavConsumed: () -> Unit = {},
) {
    val appViewModel = hiltViewModel<AppViewModel>()
    val bootState by appViewModel.uiState.collectAsStateWithLifecycle()
    val isOnline by appViewModel.isOnline.collectAsStateWithLifecycle()

    val resolvedStart: String? =
        when {
            startRoute != null -> startRoute
            else ->
                when (bootState) {
                    BootState.Loading -> null // 부트 확정 전 — NavHost 미컴포즈, splash 만.
                    BootState.NeedsOnboarding -> ONBOARDING_ROUTE
                    BootState.MainReady -> MAIN_TABS_ROUTE
                }
        }

    if (resolvedStart == null) {
        BootSplash()
        return
    }

    val outerNavController = rememberNavController()
    NavHost(navController = outerNavController, startDestination = resolvedStart) {
        composable(MAIN_TABS_ROUTE) {
            MainTabsScaffold(
                isOnline = isOnline,
                onStartLearning = { outerNavController.navigate(homeTopicRoute()) },
                onResume = { outerNavController.navigate(homeSessionResumeRoute()) },
                pendingNav = pendingNav,
                onNavConsumed = onNavConsumed,
            )
        }
        // 온보딩 그래프(M3-02): 3탭 밖 풀스크린 형제.
        onboardingGraph(outerNavController)
        // 홈 주도 세션 그래프(M3-08): 3탭 밖 풀스크린 형제(주제→생성→대화→요약).
        homeSessionGraph(outerNavController)
    }
}

/** 부트 게이트 대기 화면 — 중앙 인디케이터. boot 확정 시 NavHost 로 교체된다. */
@Composable
private fun BootSplash() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        OneClickProgressRing(mode = ProgressRingMode.Indeterminate)
    }
}

/**
 * 3탭 셸(F8). 전역 [Scaffold] 골격이 하단 3탭([OceBottomNav])과 3탭 그래프([OceNavHost])를 소유한다.
 * 탭 선택 지속은 자체 [rememberNavController] 백스택이 담당한다(회전/복귀 시 상태 유지).
 *
 * [isOnline]=false 면 상단에 글로벌 오프라인 배너(C4)를 노출한다(M3-08 A4). [onStartLearning]/[onResume] 는
 * outer NavController 로 세션 그래프에 진입하는 람다다(홈 CTA·이어하기).
 *
 * [pendingNav] 는 알림 탭에서 온 nav 명령(M3-07 §5). `home` 이면 홈 탭으로 옮기고 소비를 통지한다.
 */
@Composable
private fun MainTabsScaffold(
    isOnline: Boolean,
    onStartLearning: () -> Unit,
    onResume: () -> Unit,
    pendingNav: String?,
    onNavConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    LaunchedEffect(pendingNav) {
        if (pendingNav == MainActivity.NAV_HOME) {
            navController.navigate(OceTab.Home.route) {
                launchSingleTop = true
                popUpTo(navController.graph.startDestinationId) { inclusive = false }
            }
            onNavConsumed()
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { OceBottomNav(navController) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            OneClickOfflineBanner(visible = !isOnline)
            OceNavHost(
                navController = navController,
                onStartLearning = onStartLearning,
                onResume = onResume,
            )
        }
    }
}

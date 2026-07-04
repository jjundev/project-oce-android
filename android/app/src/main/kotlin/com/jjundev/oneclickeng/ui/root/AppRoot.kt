package com.jjundev.oneclickeng.ui.root

import androidx.compose.foundation.layout.Box
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
import com.jjundev.oneclickeng.dev.harnessGraph
import com.jjundev.oneclickeng.dev.harnessStartRoute
import com.jjundev.oneclickeng.feature.onboarding.ONBOARDING_ROUTE
import com.jjundev.oneclickeng.feature.onboarding.onboardingGraph
import com.jjundev.oneclickeng.ui.component.OneClickProgressRing
import com.jjundev.oneclickeng.ui.component.ProgressRingMode
import com.jjundev.oneclickeng.ui.foundation.OceBottomNav
import com.jjundev.oneclickeng.ui.navigation.OceNavHost
import com.jjundev.oneclickeng.ui.navigation.OceTab

/** 3탭 셸(하단 내비 + [OceNavHost])을 담는 바깥 그래프 목적지 경로. */
const val MAIN_TABS_ROUTE = "main_tabs"

/**
 * 앱 루트 컴포저블(F8 스캐폴드 · M0-09). 단일 Activity 위에 **바깥 NavHost** 를 세운다. 세 목적지가 형제로
 * 산다: 3탭 셸([MAIN_TABS_ROUTE]) · 온보딩 그래프([ONBOARDING_ROUTE]) · (debug) 하니스 그래프.
 *
 * **진입 게이트(M3-02):** 시작 목적지는 게스트 부트 이후 비동기로 정해진다([AppViewModel.uiState]). Compose
 * Navigation 의 `startDestination` 은 첫 컴포지션에 고정되므로, boot 이 확정될 때까지 **NavHost 를 아예
 * 컴포즈하지 않고** splash 만 띄운 뒤, 확정된 시작 목적지로 그때 NavHost 를 최초 컴포즈한다(값만 바꾸면
 * 동결돼 라우트가 안 바뀌는 함정 회피).
 *
 * **우선순위:** ① [startRoute] 명시 주입(테스트) → ② debug [harnessStartRoute]("dev_harness", 게이트 우회 —
 * 개발은 splash/Firestore 왕복 없이 곧장 하니스) → ③ boot 게이트(Loading=splash / NeedsOnboarding=온보딩 /
 * MainReady=3탭). release 에선 ②가 null 이라 항상 ③이 동작한다.
 *
 * @param startRoute 명시 시작 목적지 override(테스트/하니스 격리 seam). null 이면 위 ②③으로 결정한다.
 *   기존 3탭 스모크는 이 인자에 [MAIN_TABS_ROUTE] 를 주입해 게이트를 우회한다.
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

    val harnessRoute = harnessStartRoute()
    val resolvedStart: String? =
        when {
            startRoute != null -> startRoute
            harnessRoute != null -> harnessRoute
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
            MainTabsScaffold(pendingNav = pendingNav, onNavConsumed = onNavConsumed)
        }
        // 온보딩 그래프(M3-02): 3탭 밖 풀스크린 형제. 완주/스킵 시 MAIN_TABS 로 이탈하며 그래프를 pop.
        onboardingGraph(outerNavController)
        // debug: 하니스 라우트 등록 / release: no-op(하니스 부재).
        harnessGraph(outerNavController)
    }
}

/** 부트 게이트 대기 화면(AnonymousStarting) — 중앙 인디케이터. boot 확정 시 NavHost 로 교체된다. */
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
 * 탭 선택 지속은 자체 [rememberNavController] 백스택이 담당한다(회전/복귀 시 상태 유지 · M0-09 수용기준 #3).
 *
 * [pendingNav] 는 알림 탭에서 온 nav 명령(M3-07 §5). `home` 이면 이 스코프의 NavController 를 홈 탭으로
 * 옮기고 [onNavConsumed] 로 소비를 통지한다.
 */
@Composable
private fun MainTabsScaffold(
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
        OceNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

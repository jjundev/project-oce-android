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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jjundev.oneclickeng.MainActivity
import com.jjundev.oneclickeng.feature.home.HOME_SESSION_GRAPH_ROUTE
import com.jjundev.oneclickeng.feature.home.homeSessionGraph
import com.jjundev.oneclickeng.feature.home.homeSessionResumeRoute
import com.jjundev.oneclickeng.feature.home.homeSessionStartRoute
import com.jjundev.oneclickeng.feature.onboarding.ONBOARDING_ROUTE
import com.jjundev.oneclickeng.feature.onboarding.onboardingGraph
import com.jjundev.oneclickeng.ui.component.OneClickOfflineBanner
import com.jjundev.oneclickeng.ui.component.OneClickProgressRing
import com.jjundev.oneclickeng.ui.component.ProgressRingMode
import com.jjundev.oneclickeng.ui.foundation.OceBottomNav
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.navigation.OceNavHost
import com.jjundev.oneclickeng.ui.navigation.OceTab
import com.jjundev.oneclickeng.ui.navigation.oceScreenEnter
import com.jjundev.oneclickeng.ui.navigation.oceScreenExit
import com.jjundev.oneclickeng.ui.theme.OceTheme

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
    val reduceMotion = rememberReduceMotion()
    val motion = OceTheme.motion
    val offsetY8Px = with(LocalDensity.current) { 8.dp.roundToPx() }
    NavHost(
        navController = outerNavController,
        startDestination = resolvedStart,
        enterTransition = { oceScreenEnter(motion, offsetY8Px, reduceMotion) },
        exitTransition = { oceScreenExit },
        popEnterTransition = { oceScreenEnter(motion, offsetY8Px, reduceMotion) },
        popExitTransition = { oceScreenExit },
    ) {
        composable(MAIN_TABS_ROUTE) {
            // 프로토 완전 정합: 홈이 상황·레벨·길이를 확정하고(인라인 설정·상황 시트 소유) 히어로 탭 시
            // 바로 생성 라우트로 진입한다(세션 설정 화면 폐기).
            MainTabsScaffold(
                isOnline = isOnline,
                onStartSession = { promptSeed, topicLabel, topicEmoji, level, length ->
                    outerNavController.navigate(
                        homeSessionStartRoute(
                            level = level,
                            topic = promptSeed,
                            length = length,
                            topicLabel = topicLabel,
                            topicEmoji = topicEmoji,
                        ),
                    )
                },
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
 * [isOnline]=false 면 상단에 글로벌 오프라인 배너(C4)를 노출한다(M3-08 A4). [onStartSession]/[onResume] 는
 * outer NavController 로 세션 그래프에 진입하는 람다다(홈 히어로·추천 행·이어하기).
 *
 * [pendingNav] 는 알림 탭에서 온 nav 명령(M3-07 §5). `home` 이면 홈 탭으로 옮기고 소비를 통지한다.
 */
@Composable
private fun MainTabsScaffold(
    isOnline: Boolean,
    onStartSession: (promptSeed: String, topicLabel: String, topicEmoji: String, level: String, length: Int) -> Unit,
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
                onStartSession = onStartSession,
                onResume = onResume,
            )
        }
    }
}

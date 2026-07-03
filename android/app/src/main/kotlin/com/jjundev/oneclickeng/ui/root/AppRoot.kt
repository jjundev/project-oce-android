package com.jjundev.oneclickeng.ui.root

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jjundev.oneclickeng.MainActivity
import com.jjundev.oneclickeng.dev.harnessGraph
import com.jjundev.oneclickeng.dev.harnessStartRoute
import com.jjundev.oneclickeng.ui.foundation.OceBottomNav
import com.jjundev.oneclickeng.ui.navigation.OceNavHost
import com.jjundev.oneclickeng.ui.navigation.OceTab

/** 3탭 셸(하단 내비 + [OceNavHost])을 담는 바깥 그래프 목적지 경로. */
const val MAIN_TABS_ROUTE = "main_tabs"

/**
 * 앱 루트 컴포저블(F8 스캐폴드 · M0-09). 단일 Activity 위에 **바깥 NavHost** 를 세운다. 평시(릴리즈)엔
 * 목적지가 [MAIN_TABS_ROUTE] 하나뿐이라 사실상 예전과 동일하게 3탭 셸([MainTabsScaffold])만 뜬다.
 *
 * **하니스 seam(M1-09):** [startRoute] 기본값과 [harnessGraph] 호출이 개발 하니스의 유일한 진입 seam
 * 이다. debug 변이에서만 [harnessStartRoute] 가 `dev_harness` 를 반환하고 [harnessGraph] 가 하니스
 * 라우트(런처·세션 생성·대화턴)를 등록한다. release 변이에선 둘 다 no-op(startRoute=null→[MAIN_TABS_ROUTE],
 * harnessGraph 미등록)이라 하니스 표면이 물리적으로 부재한다. 세션 라우트를 이 바깥 그래프에 두어
 * 3탭 Scaffold **밖** 풀스크린으로 띄운다(M3-08 완료 시 이 래핑을 해제해 제거).
 *
 * @param startRoute 바깥 그래프 시작 목적지. 테스트/하니스 격리를 위한 주입 seam(기본: 하니스 여부에 따라
 *   `dev_harness` 또는 [MAIN_TABS_ROUTE]). 기존 3탭 스모크는 이 인자에 [MAIN_TABS_ROUTE] 를 주입한다.
 * @param pendingNav 알림 탭 nav 명령(M3-07). [MainTabsScaffold] 로 전달돼 홈 이동으로 소비된다.
 */
@Composable
fun AppRoot(
    startRoute: String = harnessStartRoute() ?: MAIN_TABS_ROUTE,
    pendingNav: String? = null,
    onNavConsumed: () -> Unit = {},
) {
    hiltViewModel<AppViewModel>()
    val outerNavController = rememberNavController()
    NavHost(navController = outerNavController, startDestination = startRoute) {
        composable(MAIN_TABS_ROUTE) {
            MainTabsScaffold(pendingNav = pendingNav, onNavConsumed = onNavConsumed)
        }
        // debug: 하니스 라우트 등록 / release: no-op(하니스 부재).
        harnessGraph(outerNavController)
    }
}

/**
 * 3탭 셸(F8). 전역 [Scaffold] 골격이 하단 3탭([OceBottomNav])과 3탭 그래프([OceNavHost])를 소유한다.
 * 탭 선택 지속은 자체 [rememberNavController] 백스택이 담당한다(회전/복귀 시 상태 유지 · M0-09 수용기준 #3).
 *
 * [pendingNav] 는 알림 탭에서 온 nav 명령(M3-07 §5). `home` 이면 이 스코프의 NavController 를 홈 탭으로
 * 옮기고 [onNavConsumed] 로 소비를 통지한다. inner NavController 와 이 효과는 3탭 셸에 함께 묶여 있어,
 * 하니스 화면이 포그라운드일 땐 이 목적지가 컴포즈되지 않으므로 소비가 지연될 수 있으나(드롭 아님 —
 * [MainActivity] 의 pendingNav 값이 살아 있어 복귀 시 1회 발화), 하니스는 비노출 개발 전용이라 허용한다.
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

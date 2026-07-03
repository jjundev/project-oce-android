package com.jjundev.oneclickeng.ui.root

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.jjundev.oneclickeng.MainActivity
import com.jjundev.oneclickeng.ui.foundation.OceBottomNav
import com.jjundev.oneclickeng.ui.navigation.OceNavHost
import com.jjundev.oneclickeng.ui.navigation.OceTab

/**
 * 앱 루트 컴포저블(F8 스캐폴드 · M0-09). 단일 Activity 위에 전역 [Scaffold] 골격을 세운다.
 * Scaffold 는 하단 3탭([OceBottomNav])과 내비게이션 그래프([OceNavHost])만 소유하고, 상단 타이틀은
 * 각 화면이 인라인으로 그린다(F8 #3 — TopAppBar 없음).
 *
 * 탭 선택 지속은 [rememberNavController] 백스택(구성변경 생존)이, 화면별 스크롤은 각 화면의
 * rememberLazyListState 내장 Saver 가 담당한다(회전/복귀 시 상태 유지 — 수용기준 #3).
 *
 * [AppViewModel] 인스턴스화가 게스트 부트스트랩(익명 로그인 + 프로필 생성 · M3-01)을 비차단으로
 * 시작한다. UI 게이팅 없이 아래 [Scaffold] 는 즉시 렌더된다(FR-1 — 로그인 화면 없음).
 *
 * [pendingNav] 는 알림 탭에서 온 nav 명령(M3-07 §5). `home` 이면 NavController 를 홈 탭으로 옮기고
 * [onNavConsumed] 로 소비를 통지한다(중복 이동 방지).
 */
@Composable
fun AppRoot(
    pendingNav: String? = null,
    onNavConsumed: () -> Unit = {},
) {
    hiltViewModel<AppViewModel>()
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

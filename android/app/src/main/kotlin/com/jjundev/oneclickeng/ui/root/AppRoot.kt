package com.jjundev.oneclickeng.ui.root

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.jjundev.oneclickeng.ui.foundation.OceBottomNav
import com.jjundev.oneclickeng.ui.navigation.OceNavHost

/**
 * 앱 루트 컴포저블(F8 스캐폴드 · M0-09). 단일 Activity 위에 전역 [Scaffold] 골격을 세운다.
 * Scaffold 는 하단 3탭([OceBottomNav])과 내비게이션 그래프([OceNavHost])만 소유하고, 상단 타이틀은
 * 각 화면이 인라인으로 그린다(F8 #3 — TopAppBar 없음).
 *
 * 탭 선택 지속은 [rememberNavController] 백스택(구성변경 생존)이, 화면별 스크롤은 각 화면의
 * rememberLazyListState 내장 Saver 가 담당한다(회전/복귀 시 상태 유지 — 수용기준 #3).
 */
@Composable
fun AppRoot() {
    val navController = rememberNavController()
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

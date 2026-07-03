package com.jjundev.oneclickeng.ui.foundation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.jjundev.oneclickeng.ui.navigation.OceTab
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 하단 3탭 내비게이션(F8 #2). M3 [NavigationBar] 를 감싸되 elevation 기조는 플랫(0) +
 * `border.hairline` 이다.
 *
 * **외형 정본(F8 #2 · `elevation-nav`):** 정본 토큰은 `0 -1px 0 hairline, 0 -8px 24px rgba(…,.06)`
 * 즉 위쪽(-Y)으로만 향하는 CSS box-shadow 다. Compose [shadow] 모디파이어는 방향/오프셋 인자가 없어
 * 이 방향성 그림자를 정확히 재현하지 못한다. 따라서 **재현 가능한 상단 hairline([HorizontalDivider])을
 * 1차 요소로 확정**하고, 소프트 상승 블러는 화면 최하단이라 좌우·하단 그림자가 화면 밖으로 클립되는
 * 점을 이용한 best-effort 로만 [shadow]([OceElevation.nav]=8dp)로 근사한다. 정밀 시각 대조는 F6.
 */
@Composable
fun OceBottomNav(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Column(
        // best-effort 상승 그림자(위 KDoc 참조). shape 은 nav bar 직사각 형상이라 RectangleShape,
        // clip=false 로 상단 hairline·콘텐츠가 그림자에 잘리지 않게 한다.
        modifier =
            modifier.shadow(
                elevation = OceTheme.elevation.nav,
                shape = RectangleShape,
                clip = false,
            ),
    ) {
        // 1차 외형 요소: 상단 hairline(F8 #2, 정본과 정확 일치하는 재현 가능 부분).
        HorizontalDivider(
            thickness = Dp.Hairline,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        NavigationBar(
            // 0dp — elevation-nav 은 M3 tonalElevation 이 아님(F8 #2).
            tonalElevation = OceTheme.elevation.default,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            OceTab.entries.forEach { tab ->
                val selected = currentDestination.isOn(tab)
                NavigationBarItem(
                    selected = selected,
                    onClick = { navController.navigateToTab(tab) },
                    icon = {
                        // 선택 상태는 부모(NavigationBarItem)의 selected 시맨틱 + 라벨이 전달 → 아이콘은 장식.
                        OneClickIcon(
                            icon = if (selected) tab.iconActive else tab.iconInactive,
                            contentDescription = null,
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(tab.titleRes),
                            style = if (selected) OceTheme.typography.tabActive else OceTheme.typography.tabInactive,
                        )
                    },
                    colors =
                        NavigationBarItemDefaults.colors(
                            // 플랫 기조: 알약형 인디케이터 비활성, 색으로만 선택 표현.
                            indicatorColor = Color.Transparent,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                )
            }
        }
    }
}

/** 현재 목적지가 [tab] 에 속하는지(중첩 그래프 대비 hierarchy 검사). */
private fun NavDestination?.isOn(tab: OceTab): Boolean {
    return this?.hierarchy?.any { it.route == tab.route } == true
}

/** 탭 재선택/전환 표준 패턴: 시작 목적지까지 pop + 상태 저장/복원, 단일 인스턴스(F8 · #10). */
private fun NavController.navigateToTab(tab: OceTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

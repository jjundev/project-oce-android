package com.jjundev.oneclickeng.ui.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.jjundev.oneclickeng.ui.navigation.OceTab
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** 활성 탭 pill 하이라이트 배경 알파(브랜드색 위 연한 톤). */
private const val NAV_INDICATOR_ALPHA = 0.12f

/**
 * Permanent-tab floating-navigation dimensions derived from Prototype Flow.
 * Scroll surfaces use this trailing clearance so their final item can move above
 * the bar even though the bar itself overlays the viewport.
 */
object OceBottomNavDefaults {
    val overlayContentBottomPadding: Dp = 104.dp
}

/**
 * 하단 3탭 내비게이션 — **플로팅 라운드 바**(realization-SoT `prototype/Prototype Flow` 홈 하단, ADR-0006 시각
 * 우선). 좌우/하단 여백을 두고 떠 있는 `radius.24` 컨테이너 + 상승 그림자([OceElevation.nav])이며, 활성 탭은
 * 아이콘+라벨을 감싸는 연한 브랜드색 pill([radius18])로 표시한다.
 *
 * 표준 M3 [androidx.compose.material3.NavigationBar](엣지투엣지·플랫·상단 hairline)에서 이 형태로 전환했다
 * — F8 #2 문서 스펙은 이 realization 에 맞춰 갱신 대상. 실제 blur(글래스) 표면은 Compose 렌더러 제약으로
 * 미적용, 흰 서피스 + 그림자로 근사한다.
 */
@Composable
fun OceBottomNav(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = OceTheme.spacing.lg, vertical = OceTheme.spacing.md)
                .shadow(elevation = OceTheme.elevation.nav, shape = OceTheme.shapes.radius24, clip = false)
                .clip(OceTheme.shapes.radius24)
                .background(MaterialTheme.colorScheme.surface)
                .padding(OceTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OceTab.entries.forEach { tab ->
            NavItem(
                tab = tab,
                selected = currentDestination.isOn(tab),
                onClick = { navController.navigateToTab(tab) },
            )
        }
    }
}

/** 탭 1칸 — 아이콘+라벨 세로 스택. 활성이면 pill 배경 + 브랜드색, 비활성이면 중립색. */
@Composable
private fun RowScope.NavItem(
    tab: OceTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier =
            Modifier
                .weight(1f)
                .clip(OceTheme.shapes.radius18)
                .then(
                    if (selected) {
                        Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = NAV_INDICATOR_ALPHA))
                    } else {
                        Modifier
                    },
                )
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .padding(vertical = OceTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
    ) {
        // 선택 상태는 부모(selectable)의 selected 시맨틱 + 라벨이 전달 → 아이콘은 장식.
        OneClickIcon(
            icon = if (selected) tab.iconActive else tab.iconInactive,
            contentDescription = null,
            tint = tint,
        )
        Text(
            text = stringResource(tab.titleRes),
            style = if (selected) OceTheme.typography.tabActive else OceTheme.typography.tabInactive,
            color = tint,
        )
    }
}

/** 현재 목적지가 [tab] 에 속하는지(중첩 그래프 대비 hierarchy 검사). */
private fun NavDestination?.isOn(tab: OceTab): Boolean {
    return this?.hierarchy?.any { it.route == tab.route } == true
}

/** 탭 재선택/전환 표준 패턴: 시작 목적지까지 pop + 단일 인스턴스(F8 · #10). */
private fun NavController.navigateToTab(tab: OceTab) {
    // 설정은 진입할 때마다 최상단부터 다시 읽는다. 이 화면의 LazyListState 를 저장/복원하면 다른 탭을
    // 거친 뒤에도 중간 스크롤 위치가 남는다. 나머지 탭은 기존대로 상태를 보존한다.
    val leavingSettings = currentDestination.isOn(OceTab.Settings)
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = !leavingSettings }
        launchSingleTop = true
        restoreState = tab != OceTab.Settings
    }
}

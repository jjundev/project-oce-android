package com.jjundev.oneclickeng.ui.foundation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 상시탭 화면 공통 골격(F8). 상단에 [PinnedTabHeader](48dp 고정 중앙 헤더, 프로토 정합)를 두고 그 아래
 * **단일 [LazyColumn] 호스트**로 콘텐츠를 스크롤한다. 타이틀은 스크롤과 무관하게 고정된다(설정 탭과 동일).
 *
 * - 가로 거터 20dp = `OceTheme.spacing.xl`(F8 #1) — 헤더는 전폭 중앙, 콘텐츠 리스트에만 적용한다.
 * - 하단은 오버레이 플로팅 내비([OceBottomNavDefaults.overlayContentBottomPadding])만큼 띄워 마지막
 *   항목이 내비 뒤에 가리지 않게 한다.
 * - 헤더 heading 시맨틱은 [PinnedTabHeader] 가 소유한다.
 * - 스크롤 상태([rememberLazyListState])는 내장 Saver 로 회전에 생존하고, NavHost restoreState 로
 *   탭 전환 간에도 복원된다.
 */
@Composable
fun TabScreenScaffold(
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        PinnedTabHeader(titleRes = titleRes)
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = OceTheme.spacing.xl),
            state = rememberLazyListState(),
            contentPadding =
                PaddingValues(bottom = OceBottomNavDefaults.overlayContentBottomPadding),
        ) {
            content()
        }
    }
}

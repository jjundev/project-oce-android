@file:Suppress("MatchingDeclarationName") // 파일은 OceSheetDefaults + OneClickBottomSheet 묶음(단일 선언 아님).

package com.jjundev.oneclickeng.ui.component.primitive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 공용 시트 스페이싱 정책(PR D). 값은 [OceSheetDefaults] 하나가 소유 — 콜러/스크린샷이 하드코딩하지 않는다.
 *  - 핸들→첫 제목 갭 = `sheetHandleGap`(12dp). M3 드래그 핸들 자체 여백 위에 얹힌다.
 *  - 최하단 콘텐츠 하단 = `navigationBarsPadding()`(제스처바 인셋) + `sheetContentBottom`(24dp).
 *  - 가로 거터 = `sheetPadding`(24dp).
 * ModalBottomSheet 는 기본 `windowInsets` 가 top 만 처리하므로 하단 nav bar 는 여기서 명시 흡수한다.
 */
object OceSheetDefaults {
    val contentPadding: PaddingValues
        @Composable get() =
            PaddingValues(
                start = OceTheme.spacing.sheetPadding,
                end = OceTheme.spacing.sheetPadding,
                top = OceTheme.spacing.sheetHandleGap,
                bottom = OceTheme.spacing.sheetContentBottom,
            )
}

/**
 * 비파일럿 프리미티브 = M3 [ModalBottomSheet] 얇은 래핑 + 토큰. 상단 `radius.24`·`surface`·핸들 유지.
 * content 는 [OceSheetDefaults.contentPadding] + `navigationBarsPadding()` 을 두른 [Column] 안에 렌더돼
 * 모든 시트가 동일 세로 리듬을 갖는다. 특수 시트는 [contentPadding] 를 넘겨 오버라이드할 수 있다.
 * C13(권한 프라이밍)·C19(리마인더)·주제 선택·Google 저장·설정 정리 시트가 이 프리미티브를 재사용한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneClickBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    contentPadding: PaddingValues = OceSheetDefaults.contentPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = OceTheme.shapes.radius24,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(contentPadding),
            content = content,
        )
    }
}

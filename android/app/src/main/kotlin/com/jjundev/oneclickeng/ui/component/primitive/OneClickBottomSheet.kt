@file:Suppress("MatchingDeclarationName") // 파일은 OceSheetDefaults + OneClickBottomSheet 묶음(단일 선언 아님).

package com.jjundev.oneclickeng.ui.component.primitive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 공용 시트 스페이싱 정책(PR D). 값은 [OceSheetDefaults] 하나가 소유 — 콜러/스크린샷이 하드코딩하지 않는다.
 *  - 핸들→첫 제목 갭 = `sheetHandleGap`(12dp). M3 드래그 핸들 자체 여백 위에 얹힌다.
 *  - 최하단 콘텐츠 하단 = `navigationBarsPadding()`(제스처바 인셋) + `sheetContentBottom`(24dp).
 *  - 가로 거터 = `sheetPadding`(24dp).
 * ModalBottomSheet 의 기본 `windowInsets`(safeDrawing 하단)가 하단 제스처바 인셋을 이미 소비하므로
 * 버튼이 제스처바를 클리어한다. 아래 `navigationBarsPadding()` 은 그 위의 중복 안전망(기본 인셋이
 * 소비돼 실제로는 0dp)이며, `contentPadding.bottom`(24dp)이 그 위에 얹힌다.
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
 *
 * [draggable]=false 면 드래그로 시트를 줄이거나 늘릴 수 없다. **외형(기본 핸들·여백 리듬)은 그대로**이고
 * 핸들·콘텐츠를 [blockSheetDrag] 컨테이너 안에서 슬롯 레이아웃 그대로 재현해 드래그만 막는다(스크림 탭·
 * 뒤로가기 닫기는 유지). 리마인더 시간·주제 선택 시트가 이 모드를 쓴다(설정 정리 시트와 룩 동일).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneClickBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    // 콘텐츠-hug 시트라 항상 완전 펼침으로 연다. skipPartiallyExpanded=false(M3 기본)면 콘텐츠가 화면 50%를
    // 넘길 때 show()가 절반 detent(PartiallyExpanded)에서 멈춰 "완전히 안 펼쳐지는" 버그가 난다(예: 권한/리마인더
    // 시트가 긴 화면에서). 절반 detent 드래그 UX가 필요한 시트는 없다.
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    contentPadding: PaddingValues = OceSheetDefaults.contentPadding,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    draggable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = OceTheme.shapes.radius24,
        containerColor = MaterialTheme.colorScheme.surface,
        // 비드래그 시트는 M3 슬롯 핸들을 비운다(슬롯 핸들 영역은 시트 Surface 드래그 대상이라). 핸들은
        // 아래 blockSheetDrag 컨테이너 안에서 장식용으로 재현한다.
        dragHandle = if (draggable) dragHandle else null,
    ) {
        if (draggable) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(contentPadding),
                content = content,
            )
        } else {
            // 드래그 차단: 핸들+콘텐츠를 한 컨테이너로 감싸 blockSheetDrag 로 시트 이동을 막는다. 슬롯 핸들
            // 레이아웃(핸들 → contentPadding 콘텐츠)을 그대로 재현해 드래그 가능 시트와 외형이 동일하다.
            Column(modifier = Modifier.fillMaxWidth().blockSheetDrag()) {
                if (dragHandle != null) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        dragHandle()
                    }
                }
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
    }
}

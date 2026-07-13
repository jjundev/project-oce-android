package com.jjundev.oneclickeng.ui.component.primitive

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * 시트 콘텐츠를 M3 [androidx.compose.material3.ModalBottomSheet] 의 드래그(리사이즈·스와이프-닫기)로부터
 * 격리한다. Material3 1.3.1 은 시트 제스처를 끄는 파라미터가 없고, 드래그는 콘텐츠까지 감싸는 시트 Surface 의
 * `.draggable` + `.nestedScroll(ConsumeSwipe…)` 에 붙는다. 핸들은 별도로 `dragHandle = null` 로 없애고, 이
 * 모디파이어를 콘텐츠를 감싸는 Box 에 적용하면 시트가 어떤 드래그에도 (실질 콘텐츠 영역에서) 움직이지 않는다:
 *  - no-op [draggable] 이 세로 직접 드래그를 소비 → 상위 Surface 의 `.draggable` 로 전파 차단.
 *  - [nestedScroll] 커넥션이 내부 스크롤러(리스트·휠)의 경계 오버스크롤/플링 leftover 를 **post 단계**
 *    (자식→부모, innermost-first)에서 전량 소비 → Surface 로 새어 시트를 끄는 것 차단. pre 단계는 시트가
 *    완전 펼침(`skipPartiallyExpanded = true`)이라 스스로 0 만 소비하므로 손대지 않는다.
 *
 * 내부 스크롤러는 더 깊은 노드라 자체 스크롤을 유지하고, 탭은 터치 슬롭 미만이라 영향 없다. 스크림 탭·뒤로가기
 * 닫기(`onDismissRequest`)는 드래그와 독립이라 유지된다.
 *
 * 알려진 한계: 시트 Surface 최하단의 시스템 제스처 인셋 스트립은 이 Box 바깥(M3 프리미티브 레벨)이라 덮지 못한다.
 */
@Composable
internal fun Modifier.blockSheetDrag(): Modifier {
    val consumeLeftover =
        remember {
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                    available

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
            }
        }
    val noOpDrag = rememberDraggableState { /* no-op: 시트 이동 없음 */ }
    return this
        .nestedScroll(consumeLeftover)
        .draggable(state = noOpDrag, orientation = Orientation.Vertical)
}

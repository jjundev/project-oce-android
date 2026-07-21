package com.jjundev.oneclickeng.feature.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 복습 화면 하단 버튼 영역을 시트처럼 감싼다 — 상단만 둥근 모서리(24dp) + 핸들바([BottomSheetDefaults.DragHandle],
 * 순수 장식이며 [OneClickBottomSheet][com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet] 같은
 * 모달이 아니다 — 스크림·드래그·뒤로가기 닫기 없음) + `surface` 배경.
 *
 * 이 컴포저블이 새로 컴포지션에 들어올 때마다(예: 앞면→뒷면 전환은 서로 다른 if/else 분기라 구조적으로 새
 * 인스턴스) 아래에서 슬라이드+페이드로 올라오는 진입 애니메이션을 1회 재생한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReviewButtonSheet(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
    ) {
        Column(
            modifier =
                modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.surface),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BottomSheetDefaults.DragHandle()
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = OceTheme.spacing.sheetPadding)
                        .padding(top = OceTheme.spacing.sm, bottom = OceTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
                content = content,
            )
        }
    }
}

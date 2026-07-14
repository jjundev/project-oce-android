package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon

/**
 * 스크롤 보조 FAB 원형 버튼(프로토 summaryFab) — 흰 서피스 + hairline 보더 + 원형 그림자, 중앙 chevron.
 * [atEnd] 면 위 방향(맨 위로 복귀 어포던스), 아니면 아래 방향(더 보기/page-down). 표시 여부·스크롤 판정·
 * 실제 스크롤 동작은 호출부가 소유하고, 이 컴포넌트는 시각+회전만 책임진다(요약 화면과 기록 탭이 공유).
 *
 * elevation 정본("그림자 금지, 깊이=surface+hairline")의 명시적 예외 — 프로토 summaryFab 가 떠 있는 원형
 * 버튼이라 6dp 그림자를 쓴다(surface+hairline 위에 그림자만 더함).
 */
@Composable
fun OneClickScrollFab(
    atEnd: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(ScrollFabSize)
                .shadow(ScrollFabElevation, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        OneClickIcon(
            icon = OceIcon.ExpandMore,
            contentDescription = if (atEnd) "맨 위로" else "아래로 스크롤",
            tint = MaterialTheme.colorScheme.primary,
            size = ScrollFabIconSize,
            modifier = Modifier.rotate(if (atEnd) 180f else 0f),
        )
    }
}

/** 스크롤 보조 FAB 지름(프로토 summaryFab 48px 원형). */
private val ScrollFabSize = 48.dp

/** FAB 내부 chevron 아이콘 크기(프로토 26px). */
private val ScrollFabIconSize = 26.dp

/** 원형 그림자 높이(프로토 summaryFab). */
private val ScrollFabElevation = 6.dp

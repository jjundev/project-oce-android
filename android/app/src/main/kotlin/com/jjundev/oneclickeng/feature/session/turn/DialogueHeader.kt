package com.jjundev.oneclickeng.feature.session.turn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** 진행 점 지름·간격(프로토타입 정합). */
private val ProgressDotSize = 7.dp
private val ProgressDotGap = 6.dp

/** 뒤로가기 아이콘 ↔ 주제 아바타 간격(프로토타입 rect 정합, 스케일 스냅 밖 값). */
private val BackToAvatarGap = 14.dp

/** 주제 아바타 이모지 크기(프로토타입 32px). */
private val TopicAvatarSize = 28.sp

/**
 * 48dp 세션 앱바. 뒤로가기 + 주제 아바타 + 제목/레벨 + 진행 점(우측). 프로토타입 `session` 헤더 정합.
 * 뒤로가기·진행 배선은 실 라우트(M1-01) 소관으로 지금은 정적 렌더(뒤로 콜백 no-op).
 */
@Composable
internal fun DialogueHeader(
    state: DialogueHeaderState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = OceTheme.spacing.lg, end = OceTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OneClickIcon(
            icon = OceIcon.ArrowBack,
            contentDescription = "뒤로",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onBack),
        )
        Spacer(Modifier.width(BackToAvatarGap))
        Text(text = state.topicEmoji, fontSize = TopicAvatarSize)
        Spacer(Modifier.width(OceTheme.spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.title,
                style = OceTheme.typography.sectionLabel,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.levelLabel,
                style = OceTheme.typography.accrualLabel.copy(fontWeight = FontWeight.SemiBold),
                color = OceTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ProgressDotGap)) {
            repeat(state.totalTurns) { index ->
                val color =
                    if (index < state.completedTurns) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                Box(
                    modifier =
                        Modifier
                            .size(ProgressDotSize)
                            .clip(CircleShape)
                            .background(color),
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 393)
@Composable
private fun DialogueHeaderPreview() {
    OceTheme {
        DialogueHeader(
            state =
                DialogueHeaderState(
                    topicEmoji = "☕",
                    title = "카페에서 주문하기",
                    levelLabel = "easy(A2) · 5턴 균일",
                ),
        )
    }
}

package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 리마인더 켜짐 확인 배너(프로토 reminderBanner) — 홈 상단 초록 틴트 카드: X 닫기 + ✓ "리마인더를 켰어요" +
 * 시각 안내 + `시간 바꾸기`. 노출/해제 상태와 시각 변경 다이얼로그는 소비처(M3-07 리마인더 VM)가 소유한다.
 */
@Composable
fun OneClickReminderEnabledBanner(
    hour: Int,
    minute: Int,
    onDismiss: () -> Unit,
    onChangeTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(OceTheme.shapes.radius16)
                .background(OceTheme.colors.feedbackNaturalBg)
                .padding(horizontal = OceTheme.spacing.sm, vertical = OceTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDismiss) {
            OneClickIcon(
                icon = OceIcon.Close,
                contentDescription = "배너 닫기",
                tint = OceTheme.colors.feedbackNaturalAccent,
                size = OceIconSize.FeedbackInline,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
            ) {
                OneClickIcon(
                    icon = OceIcon.CheckCircle,
                    contentDescription = null,
                    tint = OceTheme.colors.feedbackNaturalAccent,
                    size = OceIconSize.FeedbackInline,
                )
                Text(
                    text = "리마인더를 켰어요",
                    style = OceTheme.typography.helper.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "매일 ${reminderTimeLabel(hour, minute)}에 살짝 알려드릴게요.",
                style = OceTheme.typography.helper,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onChangeTime) {
            Text(
                text = "시간 바꾸기",
                style = OceTheme.typography.helper.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 한국어 시각 라벨(프로토 "저녁 8:00") — 시간대 접두 + 12시간제. */
private fun reminderTimeLabel(
    hour: Int,
    minute: Int,
): String {
    val period =
        when (hour) {
            in 0..5 -> "새벽"
            in 6..11 -> "아침"
            in 12..17 -> "오후"
            else -> "저녁"
        }
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return "$period %d:%02d".format(h12, minute)
}

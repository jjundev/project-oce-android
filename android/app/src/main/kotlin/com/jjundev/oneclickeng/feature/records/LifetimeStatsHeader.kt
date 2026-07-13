package com.jjundev.oneclickeng.feature.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.jjundev.oneclickeng.ui.component.OneClickCountUp
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * ① 평생 통계 헤더(카드 아님, R1) — `누적 N XP · 총 N시간 N분 · N일 학습`. XP·학습일·학습시간 모두
 * [OneClickCountUp] 시그니처 카운트업(I3)을 통과한다. 학습시간은 총 분 단일값을 굴리고 프레임마다
 * [formatStudyTime] 로 "N시간 N분" 을 재도출해 60분 경계에서 분→시간 롤오버가 자연히 나타난다.
 *
 * [lifetime] 이 null 이면(M3-05 배선 전 스텁) 0 지표를 **정적**으로 렌더한다 — [animate] 와 무관하게 스냅해
 * 0→0 죽은 애니메이션을 막는다. 실데이터가 붙고([lifetime] 비-null) 세션 최초 진입일 때만 [animate] 로 롤업한다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LifetimeStatsHeader(
    lifetime: LifetimeStats?,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val stats = lifetime ?: LifetimeStats(xp = 0, studyMinutes = 0, studyDays = 0)
    val static = lifetime == null || !animate

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
    ) {
        Text(
            text = "평생 통계",
            style = OceTheme.typography.sectionLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
        ) {
            Metric(
                icon = OceIcon.Bolt,
                iconTint = MaterialTheme.colorScheme.primary,
                value = stats.xp,
                unit = "XP",
                static = static,
            )
            Dot()
            TimeMetric(
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                totalMinutes = stats.studyMinutes,
                static = static,
            )
            Dot()
            Metric(
                icon = OceIcon.LocalFireDepartment,
                iconTint = OceTheme.colors.gameStreak,
                value = stats.studyDays,
                unit = "일 학습",
                static = static,
            )
        }
    }
}

@Composable
private fun Metric(
    icon: OceIcon,
    iconTint: Color,
    value: Int,
    unit: String,
    static: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
    ) {
        OneClickIcon(
            icon = icon,
            contentDescription = null,
            tint = iconTint,
            size = OceIconSize.FeedbackInline,
        )
        OneClickCountUp(
            target = value,
            unit = " $unit",
            static = static,
            style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TimeMetric(
    iconTint: Color,
    totalMinutes: Int,
    static: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
    ) {
        OneClickIcon(
            icon = OceIcon.Schedule,
            contentDescription = null,
            tint = iconTint,
            size = OceIconSize.FeedbackInline,
        )
        OneClickCountUp(
            target = totalMinutes,
            from = 0,
            format = ::formatStudyTime,
            static = static,
            style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Dot() {
    Text(
        text = "·",
        style = OceTheme.typography.body,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val MINUTES_PER_HOUR = 60

/** 총 학습 분 → "N시간 N분" 복합 표기(시간 0이어도 유지 — 기존 정적 렌더와 동일). 카운트업 프레임 포매터. */
internal fun formatStudyTime(totalMinutes: Int): String =
    "${totalMinutes / MINUTES_PER_HOUR}시간 ${totalMinutes % MINUTES_PER_HOUR}분"

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun LifetimeStatsHeaderPreview() {
    OceTheme {
        LifetimeStatsHeader(
            lifetime = LifetimeStats(xp = 1240, studyMinutes = 135, studyDays = 12),
            animate = false,
            modifier = Modifier.padding(OceTheme.spacing.xl),
        )
    }
}

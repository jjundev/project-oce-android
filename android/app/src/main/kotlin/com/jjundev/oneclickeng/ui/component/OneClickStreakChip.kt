package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

private val StreakChipIconSize = 16.dp

/**
 * C14 streak 칩 = pill Row 로 🔥 아이콘 + `N일` 텍스트 합성. 정본: 02-shared-components.md:110 ·
 * gamification-emphasis.md.
 *
 * 색 `game.streak`, `radius.pill`. **이중신호(A2):** 색 외에 🔥 아이콘 + "일" 텍스트가 의미를
 * 전달한다 — 빨강/하강 표현 금지(게이미피케이션 톤). XP 카운터와 달리 홈 surface 에 상시 노출된다.
 *
 * 주: DS `OneClickBadge`(비파일럿) 는 컨테이너가 `primary` 로 고정돼 `game.streak` 를 표현할 수 없어,
 * 그 프리미티브가 KDoc 으로 예고한 대로 여기서 pill 변형을 **합성**한다(아이콘 노드는 실제 [OneClickIcon]).
 *
 * 숫자는 [OneClickCountUp](C16)으로 굴린다. [static] 기본값은 true 라 홈(M3-08)·한도 패널 등 상시 노출 surface
 * 는 정적 유지(ADR-0003 홈 제외)하고, 완주 보상 스트립만 `static=false` 로 슬롯머신 롤업한다(M3-06).
 */
@Composable
fun OneClickStreakChip(
    days: Int,
    modifier: Modifier = Modifier,
    static: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .clip(OceTheme.shapes.pill)
                .background(OceTheme.colors.gameStreak)
                .padding(horizontal = OceTheme.spacing.md, vertical = OceTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
    ) {
        OneClickIcon(
            icon = OceIcon.LocalFireDepartment,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            size = StreakChipIconSize,
        )
        OneClickCountUp(
            target = days,
            unit = " 일",
            static = static,
            style = OceTheme.typography.sectionLabel,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * C14 XP 카운터(백스테이지) = `· N XP` 롤업. 완주 보상·기록 탭 헤더에서만 노출(홈 비노출, 05-open-decisions P1).
 * 숫자는 [OneClickCountUp](C16) 으로 굴린다. same-day 등 정적 표시는 [static] 으로 스냅.
 */
@Composable
fun OneClickXpChip(
    xp: Int,
    modifier: Modifier = Modifier,
    static: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
    ) {
        Text(
            text = "·",
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OneClickCountUp(
            target = xp,
            unit = " XP",
            static = static,
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 240)
@Composable
private fun OneClickStreakChipPreview() {
    OceTheme {
        Row(
            modifier = Modifier.padding(OceTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OneClickStreakChip(days = 7)
            OneClickXpChip(xp = 120, static = true)
        }
    }
}

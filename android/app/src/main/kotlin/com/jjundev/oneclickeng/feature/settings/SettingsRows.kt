package com.jjundev.oneclickeng.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.R
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

internal val SettingsRowIconBox = 40.dp
internal val SettingsDividerInset = 68.dp
private val RowMinHeight = 56.dp
private val RowLabelGap = 2.dp
private val TrimmedLineHeight =
    LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)

/**
 * 설정 항법 행(프로토 정합) — 40dp tinted 아이콘박스(solid `surface-background`) + (제목 15/600 + 설명 12.5/500
 * tertiary) + 우측 [trailing]. 기본 trailing 은 chevron_right. 계정 특수 행은 [iconBg]/[iconTint]/[titleColor] 로
 * 틴트를 덮는다. [onClick] 이 있으면 행 전체 클릭 + press ripple.
 */
@Composable
internal fun SettingsNavRow(
    icon: OceIcon,
    title: String,
    modifier: Modifier = Modifier,
    desc: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconBg: Color = MaterialTheme.colorScheme.background,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = {
        OneClickIcon(
            icon = OceIcon.ChevronRight,
            contentDescription = null,
            tint = OceTheme.colors.textTertiary,
            size = OceIconSize.ListDisclosure,
        )
    },
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = RowMinHeight)
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(SettingsRowIconBox).clip(OceTheme.shapes.radius12).background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            OneClickIcon(icon = icon, contentDescription = null, tint = iconTint, size = OceIconSize.ListDisclosure)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style =
                    OceTheme.typography.body.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        lineHeightStyle = TrimmedLineHeight,
                    ),
                color = titleColor,
            )
            if (desc != null) {
                Text(
                    text = desc,
                    style =
                        OceTheme.typography.helper.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.5f.sp,
                            lineHeightStyle = TrimmedLineHeight,
                        ),
                    color = OceTheme.colors.textTertiary,
                    modifier = Modifier.padding(top = RowLabelGap),
                )
            }
        }
        trailing?.invoke()
    }
}

/** 섹션 헤더(프로토 정합) — ExtraBold 14sp · text.tertiary · 좌측 4dp 인셋. */
@Composable
internal fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = OceTheme.typography.sectionLabel.copy(fontWeight = FontWeight.ExtraBold),
        color = OceTheme.colors.textTertiary,
        modifier = modifier.padding(start = 4.dp),
    )
}

/** 카드 내부 hairline 구분선 — 아이콘 폭만큼 좌측 인셋(프로토 정합). */
@Composable
internal fun SettingsCardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = SettingsDividerInset),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** 계정 상태 pill 배지(프로토 정합) — 게스트=중립, 로그인=natural-accent. */
@Composable
internal fun SettingsAccountBadge(
    isGuest: Boolean,
    modifier: Modifier = Modifier,
) {
    val labelRes = if (isGuest) R.string.settings_account_badge_guest else R.string.settings_account_badge_member
    val fg = if (isGuest) MaterialTheme.colorScheme.onSurfaceVariant else OceTheme.colors.feedbackNaturalAccent
    val bg = if (isGuest) MaterialTheme.colorScheme.background else OceTheme.colors.feedbackNaturalBg
    Box(
        modifier =
            modifier
                .clip(OceTheme.shapes.pill)
                .background(bg)
                .padding(horizontal = OceTheme.spacing.sm, vertical = 3.dp),
    ) {
        Text(
            text = stringResource(labelRes),
            style = OceTheme.typography.tabInactive.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
            color = fg,
        )
    }
}

/** 시스템 알림 차단 배너(프로토 정합) — feedback-correct-bg 안 notifications_off + 카피 + "시스템 설정 열기". */
@Composable
internal fun NotificationBlockedBanner(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(OceTheme.colors.feedbackCorrectBg)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OneClickIcon(
                icon = OceIcon.Notifications,
                contentDescription = null,
                tint = OceTheme.colors.feedbackCorrectAccent,
                size = OceIconSize.ListDisclosure,
            )
            Text(
                text = stringResource(R.string.settings_reminder_blocked_body),
                style = OceTheme.typography.helper.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier =
                Modifier
                    .clip(OceTheme.shapes.radius12)
                    .border(1.dp, OceTheme.colors.borderStrong, OceTheme.shapes.radius12)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = OceTheme.spacing.lg, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
            ) {
                OneClickIcon(
                    OceIcon.OpenInNew,
                    null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    size = OceIconSize.FeedbackInline,
                )
                Text(
                    text = stringResource(R.string.settings_reminder_blocked_action),
                    style = OceTheme.typography.tabActive,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

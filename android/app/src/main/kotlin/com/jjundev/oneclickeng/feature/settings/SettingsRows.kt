package com.jjundev.oneclickeng.feature.settings

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                    style = OceTheme.typography.helper.copy(fontSize = 12.5f.sp, lineHeightStyle = TrimmedLineHeight),
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

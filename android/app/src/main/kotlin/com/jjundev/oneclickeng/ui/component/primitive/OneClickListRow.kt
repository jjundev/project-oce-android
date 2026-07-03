package com.jjundev.oneclickeng.ui.component.primitive

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 비파일럿 프리미티브 = M3 [ListItem] 얇은 래핑 + 토큰만. 정본 disclosure chevron 은 `OceIcon.ChevronRight`
 * @[OceIconSize.ListDisclosure](22dp). 터치 타깃 ≥48dp(A1) — 06-accessibility-impl.md:32 권장대로
 * [minimumInteractiveComponentSize] 로 시각 크기 유지하며 터치만 확장. [onClick] 이 있으면 행 전체가 클릭 대상.
 */
@Composable
fun OneClickListRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier =
        modifier
            .minimumInteractiveComponentSize()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }

    ListItem(
        modifier = rowModifier,
        headlineContent = {
            Text(text = headline, style = OceTheme.typography.body)
        },
        supportingContent =
            supporting?.let {
                {
                    Text(
                        text = it,
                        style = OceTheme.typography.helper,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        trailingContent = {
            OneClickIcon(
                icon = OceIcon.ChevronRight,
                contentDescription = null,
                size = OceIconSize.ListDisclosure,
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

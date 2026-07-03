package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * C5 빈 상태 = 중앙정렬 96dp 아이콘 + 제목 + 보조 + 선택적 CTA. 정본: 02-shared-components.md:65.
 *
 * 아이콘은 `text.tertiary` 톤, 제목 `body`, 보조 `helper`/`text.secondary`, padding `huge`(40).
 * [ctaStrength] 이 [EmptyStateCtaStrength.Ghost] 이고 [ctaLabel]·[onCta] 가 모두 있을 때만 ghost 버튼을 낸다.
 * "한 번도 동기화 안 된 빈 캐시"는 오프라인이 아니라 이 빈 상태로 표현한다.
 */
@Composable
fun OneClickEmptyState(
    icon: OceIcon,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    ctaStrength: EmptyStateCtaStrength = EmptyStateCtaStrength.None,
    ctaLabel: String? = null,
    onCta: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(OceTheme.spacing.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
    ) {
        OneClickIcon(
            icon = icon,
            contentDescription = null,
            tint = OceTheme.colors.textTertiary,
            size = OceIconSize.EmptyState,
        )
        Text(
            text = title,
            style = OceTheme.typography.body,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (ctaStrength == EmptyStateCtaStrength.Ghost && ctaLabel != null && onCta != null) {
            TextButton(onClick = onCta) {
                Text(
                    text = ctaLabel,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** C5 빈 상태 CTA 유도 강도. 기록/홈은 약하게(None 또는 Ghost) 쓴다. */
enum class EmptyStateCtaStrength {
    None,
    Ghost,
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun OneClickEmptyStatePreview() {
    OceTheme {
        OneClickEmptyState(
            icon = OceIcon.Notes,
            title = "아직 저장한 표현이 없어요",
            subtitle = "대화하면서 마음에 드는 표현을 저장해보세요.",
            ctaStrength = EmptyStateCtaStrength.Ghost,
            ctaLabel = "대화 시작하기",
            onCta = {},
        )
    }
}

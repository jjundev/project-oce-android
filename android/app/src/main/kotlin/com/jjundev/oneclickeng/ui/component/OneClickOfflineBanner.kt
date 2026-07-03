package com.jjundev.oneclickeng.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.theme.OceTheme

private val OfflineBannerHeight = 28.dp
private val OfflineBannerIconSize = 16.dp
private val OfflineBannerHairline = 1.dp

/** exception-states.md 표면 [D] 카피(단일 출처). */
private const val OFFLINE_BANNER_COPY = "오프라인이에요. 일부 기능이 제한돼요."

/**
 * C4 글로벌 오프라인 배너 = scratch 상단 지속형 오버레이. 정본: 02-shared-components.md:60.
 *
 * 28dp 고정 높이, **중립 톤**(`surface.card` + 하단 `border.hairline` + `text.secondary`) — 에러 빨강 아님
 * (오프라인=상태 표시). 진입 슬라이드다운 200ms, reduce-motion 시 즉시(A7). polite announce(A6).
 *
 * 상태 없음: [visible] 을 주입받을 뿐 `Connectivity` 감지는 소유하지 않는다(구현 가정, exception #21·#22).
 * 모든 클래스와 공존하도록 소비처가 최상단 z-순서에 배치한다(exception §3 D).
 */
@Composable
fun OneClickOfflineBanner(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val slideMs = OceTheme.motion.durationBaseMs
    val hairlineColor = MaterialTheme.colorScheme.outlineVariant

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter =
            if (reduceMotion) {
                EnterTransition.None
            } else {
                slideInVertically(animationSpec = tween(slideMs)) { -it }
            },
        exit =
            if (reduceMotion) {
                ExitTransition.None
            } else {
                slideOutVertically(animationSpec = tween(slideMs)) { -it }
            },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(OfflineBannerHeight)
                    .background(MaterialTheme.colorScheme.surface)
                    .drawBehind {
                        drawLine(
                            color = hairlineColor,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = OfflineBannerHairline.toPx(),
                        )
                    }
                    .padding(horizontal = OceTheme.spacing.lg)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
        ) {
            OneClickIcon(
                icon = OceIcon.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = OfflineBannerIconSize,
            )
            Text(
                text = OFFLINE_BANNER_COPY,
                style = OceTheme.typography.helper,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun OneClickOfflineBannerPreview() {
    OceTheme {
        OneClickOfflineBanner(visible = true)
    }
}

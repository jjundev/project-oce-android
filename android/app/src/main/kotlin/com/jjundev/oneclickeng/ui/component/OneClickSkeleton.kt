package com.jjundev.oneclickeng.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.theme.OceTheme

// 시머 스윕은 폭을 몰라도 도는 상수 트래블/밴드폭으로 근사한다(TileMode.Clamp 기본).
private const val SHIMMER_TRAVEL_PX = 1400f
private const val SHIMMER_BAND_PX = 400f
private val SkeletonHairline = 1.dp
private val SkeletonLineHeight = 16.dp
private val SkeletonCardHeight = 96.dp
private val SkeletonSectionHeight = 120.dp

/** C6 스켈레톤 형태 변형. 소비처(slim/deep/요약)가 자리표시자 모양을 고른다. */
enum class SkeletonShape {
    Line,
    Card,
    Section,
}

/**
 * C6 로딩 스켈레톤/시머 = scratch primitive. 정본: 02-shared-components.md:70.
 *
 * `surface.card`+`border.hairline` 톤에 1200ms(`shimmerLoopMs`) 시머 스윕. [reduceMotion] 이 true 면
 * 스윕 없는 **정적 플레이스홀더**(A7). 기본값은 시스템 설정([rememberReduceMotion])을 읽고, 테스트/프리뷰는
 * 값을 주입해 분기를 강제할 수 있다.
 */
@Composable
fun OneClickSkeleton(
    shape: SkeletonShape,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotion(),
) {
    val cornerShape =
        when (shape) {
            SkeletonShape.Line -> OceTheme.shapes.radius4
            SkeletonShape.Card -> OceTheme.shapes.radius8
            SkeletonShape.Section -> OceTheme.shapes.radius8
        }
    val highlight = MaterialTheme.colorScheme.outlineVariant
    val brush =
        rememberSkeletonBrush(
            base = MaterialTheme.colorScheme.surface,
            highlight = highlight,
            reduceMotion = reduceMotion,
        )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(shape.height())
                .clip(cornerShape)
                .background(brush)
                .border(BorderStroke(SkeletonHairline, highlight), cornerShape),
    )
}

/**
 * 임의 크기 시머 조각(프로토 `.oc-sk`) — 카드 프레임 안의 아이콘/텍스트 자리표시자용. [OneClickSkeleton] 과 달리
 * 테두리·고정 높이가 없고, 크기·모서리는 호출부 [modifier]/[shape] 가 정한다. base 는 `background`(카드 위에서
 * 보이는 회색), 스윕은 hairline. [reduceMotion] 이면 정적(A7).
 */
@Composable
fun OneClickShimmerPiece(
    shape: Shape,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotion(),
) {
    val brush =
        rememberSkeletonBrush(
            base = MaterialTheme.colorScheme.background,
            highlight = MaterialTheme.colorScheme.outlineVariant,
            reduceMotion = reduceMotion,
        )
    Box(modifier.clip(shape).background(brush))
}

/** 시머 스윕 브러시(1200ms `shimmerLoopMs` 좌향 스윕). [reduceMotion] 이면 정적 [SolidColor]. */
@Composable
private fun rememberSkeletonBrush(
    base: Color,
    highlight: Color,
    reduceMotion: Boolean,
): Brush =
    if (reduceMotion) {
        SolidColor(base)
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val offset by transition.animateFloat(
            initialValue = 0f,
            targetValue = SHIMMER_TRAVEL_PX,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(OceTheme.motion.shimmerLoopMs, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "shimmer",
        )
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(offset - SHIMMER_BAND_PX, 0f),
            end = Offset(offset, 0f),
        )
    }

private fun SkeletonShape.height(): Dp =
    when (this) {
        SkeletonShape.Line -> SkeletonLineHeight
        SkeletonShape.Card -> SkeletonCardHeight
        SkeletonShape.Section -> SkeletonSectionHeight
    }

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 320)
@Composable
private fun OneClickSkeletonPreview() {
    OceTheme {
        OneClickSkeleton(shape = SkeletonShape.Card)
    }
}

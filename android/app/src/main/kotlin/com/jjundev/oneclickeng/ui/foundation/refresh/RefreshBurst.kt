@file:Suppress("MatchingDeclarationName") // 파일은 BurstParticle + RefreshBurst 컴포저블 묶음(단일 선언 아님).

package com.jjundev.oneclickeng.ui.foundation.refresh

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 방사형 입자 한 개의 결정적 파라미터(진행도 0..1 로 렌더링 시 위치/크기/투명도 계산). */
data class BurstParticle(
    val angleRad: Float,
    val distFraction: Float,
    val sizeFraction: Float,
    val delayFraction: Float,
    val colorIndex: Int,
)

/** 투명 폭죽 3색(프로토타입): 파랑 .40 / 흰색 .55 / 파랑 .32. */
val BurstColors: List<Color> =
    listOf(
        Color(0.47f, 0.63f, 1.0f, 0.40f),
        Color(1.0f, 1.0f, 1.0f, 0.55f),
        Color(0.35f, 0.55f, 0.96f, 0.32f),
    )

/** [seed] 로 결정적인 입자 배열 생성 — 균등 각도 + 소량 지터, 거리/크기/지연은 난수 프랙션. */
fun burstParticles(
    count: Int = OverscrollDefaults.BURST_COUNT,
    seed: Int,
): List<BurstParticle> {
    val rng = Random(seed)
    return (0 until count).map { i ->
        val base = (2f * PI.toFloat()) * (i.toFloat() / count)
        val jitter = (rng.nextFloat() - 0.5f) * 0.5f
        BurstParticle(
            angleRad = (base + jitter).let { if (it < 0f) it + 2f * PI.toFloat() else it },
            distFraction = rng.nextFloat(),
            sizeFraction = rng.nextFloat(),
            delayFraction = rng.nextFloat() * (45f / OverscrollDefaults.BURST_FLY_MS),
            colorIndex = i % BurstColors.size,
        )
    }
}

/**
 * 상단 인디케이터 지점에서 터지는 투명 폭죽 오버레이. [fireKey] 가 0 초과의 새 값으로 바뀔 때마다 1회 재생.
 * 입자는 각자 진행도에 따라 방사형 이동 + 확대 + 페이드아웃(18% 에서 최대 투명, 끝에서 0).
 */
@Composable
fun RefreshBurst(
    fireKey: Int,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(1f) } // 1 = 종료(비표시)
    val particles = remember(fireKey) { burstParticles(seed = fireKey) }
    val density = LocalDensity.current
    val minDist = with(density) { OverscrollDefaults.BurstMinDist.toPx() }
    val maxDist = with(density) { OverscrollDefaults.BurstMaxDist.toPx() }
    val minSize = with(density) { OverscrollDefaults.BurstMinSize.toPx() }
    val maxSize = with(density) { OverscrollDefaults.BurstMaxSize.toPx() }

    LaunchedEffect(fireKey) {
        if (fireKey <= 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(OverscrollDefaults.BURST_FLY_MS))
    }

    Canvas(modifier = modifier) {
        val p0 = progress.value
        if (p0 >= 1f) return@Canvas
        val center = Offset(size.width / 2f, 0f)
        particles.forEach { pt ->
            val local = ((p0 - pt.delayFraction) / (1f - pt.delayFraction)).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) return@forEach
            val dist = (minDist + (maxDist - minDist) * pt.distFraction) * local
            val radius = (minSize + (maxSize - minSize) * pt.sizeFraction) / 2f * (0.35f + 0.65f * local)
            // 투명도: 0 → .peak(18%) → 0
            val alpha = if (local < 0.18f) local / 0.18f else 1f - (local - 0.18f) / 0.82f
            val base = BurstColors[pt.colorIndex]
            drawCircle(
                color = base.copy(alpha = base.alpha * alpha.coerceIn(0f, 1f)),
                radius = radius,
                center = center + Offset(cos(pt.angleRad) * dist, sin(pt.angleRad) * dist * 0.9f),
            )
        }
    }
}

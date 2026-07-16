@file:Suppress("MatchingDeclarationName") // 파일은 OverscrollDefaults + rubberBand/inverseRubberBand 묶음(단일 선언 아님).

package com.jjundev.oneclickeng.ui.foundation.refresh

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 당겨서 새로고침(오버스크롤) 애니메이션의 중앙 상수. 값은 확정된 HTML 프로토타입
 * (`prototype/experiments/overscroll-top-refresh.html`) 을 dp/ms 로 옮긴 출발점이며 Task 9 에서 시각 대조로 미세조정한다.
 */
object OverscrollDefaults {
    val MaxPull: Dp = 180.dp
    val Threshold: Dp = 64.dp
    val HoldOffset: Dp = 56.dp
    val IndicatorSize: Dp = 40.dp
    val IndicatorTop: Dp = 14.dp
    val IndicatorFadeAt: Dp = 70.dp
    const val SnapToHoldMs: Int = 140
    const val MinVisibleMs: Long = 450L
    const val WaveDurationMs: Float = 520f
    const val WaveStaggerMs: Float = 36f
    val WaveCardPeak: Dp = 11.dp
    val WaveHeaderPeak: Dp = 4.5.dp
    const val BurstCount: Int = 13
    const val BurstFlyMs: Int = 680
    val BurstMinDist: Dp = 42.dp
    val BurstMaxDist: Dp = 80.dp
    val BurstMinSize: Dp = 5.dp
    val BurstMaxSize: Dp = 10.dp
    const val SpringDampingRatio: Float = 0.32f
    const val SpringStiffness: Float = 220f
}

/**
 * 고무줄 저항: 손가락 이동량 [rawDragPx] 가 커질수록 실제 오프셋은 [maxPx] 로 점근 수렴한다.
 * 프로토타입: `MAX * (1 - 1/(1 + d/MAX))`.
 */
fun rubberBand(rawDragPx: Float, maxPx: Float): Float =
    if (rawDragPx <= 0f) 0f else maxPx * (1f - 1f / (1f + rawDragPx / maxPx))

/** [rubberBand] 의 역함수 — 현재 오프셋에서 누적 드래그를 복원(드래그 재개 시 연속성 유지). */
fun inverseRubberBand(offsetPx: Float, maxPx: Float): Float =
    if (offsetPx <= 0f) 0f else offsetPx / (1f - offsetPx / maxPx)

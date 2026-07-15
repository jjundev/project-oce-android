@file:Suppress("MatchingDeclarationName") // 파일은 OverscrollRefreshBox 컴포저블 + private RefreshIndicator 묶음(단일 선언 아님).

package com.jjundev.oneclickeng.ui.foundation.refresh

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 확정된 프로토타입 당겨서-새로고침 애니메이션을 재생하는 재사용 컨테이너.
 * 헤더+리스트가 [content] 로 함께 들어와 한 덩어리로 하강한다. [isRefreshing] 이 true 인 동안 스프링 복귀를 미루고
 * (최소 표시 시간 [OverscrollDefaults.MinVisibleMs] 병행), 완료되면 통통 스프링으로 복귀한다.
 */
@Composable
fun OverscrollRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: OverscrollRefreshState = rememberOverscrollRefreshState(),
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val holdPx = with(density) { OverscrollDefaults.HoldOffset.toPx() }
    val fadeAtPx = with(density) { OverscrollDefaults.IndicatorFadeAt.toPx() }
    val thresholdPx = with(density) { OverscrollDefaults.Threshold.toPx() }
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    val currentRefreshing by rememberUpdatedState(isRefreshing)

    LaunchedEffect(state.releaseRequest) {
        if (state.releaseRequest <= 0) return@LaunchedEffect
        // (A) 로딩 위치로 스냅
        state.offset.animateTo(holdPx, tween(OverscrollDefaults.SnapToHoldMs, easing = FastOutSlowInEasing))
        // (B) 물결 + 폭죽 + 새로고침 트리거
        currentOnRefresh()
        state.fireBurst()
        val waveJob = launch {
            val total = OverscrollDefaults.WaveDurationMs + 8 * OverscrollDefaults.WaveStaggerMs
            val clock = Animatable(0f)
            clock.animateTo(total, tween(total.roundToInt(), easing = LinearEasing)) {
                state.wave.clockMs = value
            }
            state.wave.clockMs = -1f
        }
        // (C) 로딩 완료까지 대기(+ 최소 표시 시간). 테스트는 isRefreshing=false 라 최소 시간만 적용.
        val minVisible = launch { delay(OverscrollDefaults.MinVisibleMs) }
        snapshotFlow { currentRefreshing }.filter { !it }.first()
        minVisible.join()
        waveJob.join()
        // (D) 통통 스프링 복귀
        val bounceBackSpring = spring<Float>(
            dampingRatio = OverscrollDefaults.SpringDampingRatio,
            stiffness = OverscrollDefaults.SpringStiffness,
        )
        state.offset.animateTo(0f, bounceBackSpring)
        state.onCycleFinished()
    }

    Box(modifier = modifier.nestedScroll(state.nestedScrollConnection)) {
        // 상단 원형 인디케이터(하강한 틈에서 노출). spinning 은 busy 동안.
        RefreshIndicator(state = state, fadeAtPx = fadeAtPx, thresholdPx = thresholdPx)

        CompositionLocalProvider(LocalRefreshWave provides state.wave) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = state.offset.value },
                content = content,
            )
        }

        // 폭죽은 상단 인디케이터 지점(고정)에서 터진다 — 프로토타입처럼 당김량에 비례해 움직이지 않는다.
        RefreshBurst(
            fireKey = state.burstKey,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, OverscrollDefaults.IndicatorTop.toPx().roundToInt()) },
        )
    }
}

@Composable
private fun BoxScope.RefreshIndicator(state: OverscrollRefreshState, fadeAtPx: Float, thresholdPx: Float) {
    val density = LocalDensity.current
    val topPx = with(density) { OverscrollDefaults.IndicatorTop.toPx() }
    val spin = if (state.busy) {
        rememberInfiniteTransition(label = "spin").animateFloat(
            0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spinAngle",
        ).value
    } else {
        0f
    }

    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset { IntOffset(0, topPx.roundToInt()) }
            .graphicsLayer {
                val pull = state.offset.value
                alpha = (pull / fadeAtPx).coerceIn(0f, 1f)
                val p = (pull / thresholdPx).coerceIn(0f, 1f)
                scaleX = 0.55f + 0.45f * p
                scaleY = scaleX
                rotationZ = spin
            }
            .size(OverscrollDefaults.IndicatorSize)
            .clip(CircleShape)
            .drawBehind {
                drawCircle(color = Color(0xFFE5E8EB))
                if (state.busy) {
                    drawArc(
                        color = Color(0xFFC6CDD5),
                        startAngle = -90f,
                        sweepAngle = 90f,
                        useCenter = false,
                        style = Stroke(width = size.width * 0.08f),
                    )
                }
            },
    )
}

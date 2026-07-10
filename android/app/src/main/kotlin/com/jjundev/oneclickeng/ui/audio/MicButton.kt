package com.jjundev.oneclickeng.ui.audio

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlin.math.min

// --- 시각 상수(정본: 03-signature-interactions.md I1) ---
private const val CORE_FRACTION = 0.62f // 코어원 지름 = 96dp × 0.62
private const val RIPPLE_START_SCALE = 0.7f
private const val RIPPLE_END_SCALE = 1.35f
private const val RIPPLE_START_ALPHA = 0.55f
private const val REDUCED_RIPPLE_SCALE = 1.15f
private const val REDUCED_RIPPLE_ALPHA = 0.3f
private const val ARC_SWEEP_DEG = 90f
private const val ARC_START_DEG = -90f // 12시
private const val PRESS_SCALE = 0.96f
private const val DISABLED_ALPHA = 0.38f
// Analyzing(로딩 스피너)·Complete(완료 체크)는 탭 불가지만 의미 있는 활성 상태라 흐리지 않는다 — 프로토타입처럼
// 코어/아크를 solid 로 보인다. DISABLED_ALPHA 는 진짜 비활성(탭 불가 + 무의미) 전용.
private fun MicState.rendersOpaque(enabled: Boolean): Boolean =
    enabled || this == MicState.Analyzing || this == MicState.Complete

private const val ARC_ROTATION_MS = 900 // 0.9s linear
// 아래 링/리플 상수는 **전체 반경**(radius = D/2 = 48dp) 대비 분수다(코어 반경 대비 아님).
// 프로토 측정(96dp 기준)을 전체 반경 분수로 환산: 지름 fraction은 ÷2, stroke dp는 ÷48.
private const val READY_RING_RADIUS_FRACTION = 0.6875f // Ready hollow 링 지름 0.6875×D(반경 33dp) = 전체 반경×0.6875
private const val READY_RING_STROKE_FRACTION = 0.0625f // Ready 링 stroke 3dp (=3/48)
private const val RIPPLE_STROKE_FRACTION = 0.0417f // Recording 리플 stroke 2dp (=2/48)
private const val ANALYSIS_RING_RADIUS_FRACTION = 0.875f // Analyzing 스피너 링 지름 0.875×D(반경 42dp) = 전체 반경×0.875
private const val ANALYSIS_RING_STROKE_FRACTION = 0.0625f // Analyzing 링 stroke 3dp (=3/48)

/**
 * 96dp 음성 4상태 마이크(I1) — scratch Compose Canvas. 정본: [docs/ui/03-signature-interactions.md] I1.
 *
 * 무상태 컴포저블: 정착 [MicState] 를 입력받아 그린다. 상태 루프(녹음→분석→완료)는 소비처(ViewModel)가 소유하고
 * 여기서는 시각/시맨틱만 담당한다. 4상태는 색 **그리고** 형태로 구분된다(A2 비색 신호): Ready 동심 얇은 링 /
 * Recording 리플 3겹 / Analyzing 회전 아크 / Complete 체크. 배경·코어 색은 상태 전환 시 200ms 크로스페이드.
 *
 * 분석 아크는 M3 [androidx.compose.material3.CircularProgressIndicator] 가 아닌 자체 [DrawScope.drawArc] 다:
 * (a) M3 indeterminate 는 가변 sweep 이라 스펙의 고정 ~90° 아크 회전을 재현 못 하고, (b) 200ms 상태
 * 크로스페이드가 코어/링/리플/아크를 **단일 Canvas 한 draw pass** 로 합성하길 요구해 별도 M3 컴포저블(자체 draw
 * scope)로 분리할 수 없다.
 *
 * a11y: `role=Button` + `stateDescription`([MicState.description]) + 전환마다 **assertive** liveRegion — 문구는
 * stateDescription 재사용(A3·A6). dp 고정이라 fontScale 무관(A7). Analyzing 은 탭 비활성([enabled]=false 로
 * 전달)해 분석 중 재탭을 차단한다(마이크발 분석취소는 v1 미지원).
 *
 * @param onTap 탭 콜백. Ready=녹음 시작, Recording=정지. Analyzing/Complete 에서는 [enabled]=false 로 무시.
 * @param reduceMotion true 면 리플 정적 1겹·아크 회전정지·크로스페이드 없음(단일 게이트, A7).
 */
@Composable
fun MicButton(
    state: MicState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reduceMotion: Boolean = false,
) {
    val bgColor by animateColorAsState(
        targetValue = state.outerColor(),
        animationSpec = if (reduceMotion) tween(0) else tween(OceTheme.motion.durationBaseMs),
        label = "micBg",
    )
    val coreColor by animateColorAsState(
        targetValue = state.coreColor(),
        animationSpec = if (reduceMotion) tween(0) else tween(OceTheme.motion.durationBaseMs),
        label = "micCore",
    )
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val glyphColor = MaterialTheme.colorScheme.onPrimary

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(OceTheme.motion.durationFastMs),
        label = "micPress",
    )

    // 무한 위상: 리플 loop(600ms)·아크 회전(900ms). reduce-motion 이면 정적값으로 대체(구독 없음).
    val infinite = rememberInfiniteTransition(label = "mic")
    val rippleT by if (reduceMotion) {
        remember { mutableFloatStateOf(0f) }
    } else {
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    tween(OceTheme.motion.rippleLoopMs, easing = LinearEasing),
                    RepeatMode.Restart,
                ),
            label = "ripple",
        )
    }
    val arcRotation by if (reduceMotion) {
        remember { mutableFloatStateOf(0f) }
    } else {
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(ARC_ROTATION_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "arc",
        )
    }

    Canvas(
        modifier =
            modifier
                .size(96.dp)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                    alpha = if (state.rendersOpaque(enabled)) 1f else DISABLED_ALPHA
                }
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onTap,
                )
                .semantics {
                    contentDescription = "마이크"
                    stateDescription = state.description
                    liveRegion = LiveRegionMode.Assertive
                },
    ) {
        val diameter = min(size.width, size.height)
        val radius = diameter / 2f
        val coreRadius = radius * CORE_FRACTION

        // 외륜(배경 원).
        drawCircle(color = bgColor, radius = radius, center = center)

        when (state) {
            MicState.Recording ->
                drawRipples(center, radius, coreColor, rippleT, reduceMotion)
            else -> Unit
        }

        // 코어원 — Ready 는 속을 비운 슬레이트 링(프로토타입/스펙 I1 "동심 링" 정합), 나머지 상태는 채운 코어.
        if (state != MicState.Ready) {
            drawCircle(color = coreColor, radius = coreRadius, center = center)
        }

        when (state) {
            MicState.Ready ->
                // 슬레이트 링(속 비움, 비색 형태 신호 — "탭 대기"). 지름 0.6875×D(66dp)·stroke 3dp, 옅은 외륜 위 hollow.
                drawCircle(
                    color = coreColor,
                    radius = radius * READY_RING_RADIUS_FRACTION,
                    center = center,
                    style = Stroke(width = radius * READY_RING_STROKE_FRACTION),
                )
            MicState.Analyzing ->
                drawAnalysisArc(center, radius, coreColor, trackColor, arcRotation)
            MicState.Complete ->
                drawCheck(center, coreRadius, glyphColor)
            MicState.Recording -> Unit
        }
    }
}

/**
 * Recording 리플 3겹(loop 600ms, 각 겹 1/3 위상 stagger). reduce-motion 이면 정적 1겹.
 * 프로토 정합: 채운 원이 아니라 **stroked 2dp 링**, 기준 반경 = 전체 반경(48dp)에서 scale.
 */
private fun DrawScope.drawRipples(
    center: Offset,
    radius: Float,
    color: Color,
    t: Float,
    reduceMotion: Boolean,
) {
    val stroke = radius * RIPPLE_STROKE_FRACTION // 2dp 테두리
    if (reduceMotion) {
        drawCircle(
            color = color.copy(alpha = REDUCED_RIPPLE_ALPHA),
            radius = radius * REDUCED_RIPPLE_SCALE,
            center = center,
            style = Stroke(width = stroke),
        )
        return
    }
    for (i in 0 until RIPPLE_COUNT) {
        val phase = (t + i.toFloat() / RIPPLE_COUNT) % 1f
        val scale = lerpF(RIPPLE_START_SCALE, RIPPLE_END_SCALE, phase)
        val alpha = lerpF(RIPPLE_START_ALPHA, 0f, phase)
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = radius * scale,
            center = center,
            style = Stroke(width = stroke),
        )
    }
}

/** Analyzing 고정 ~90° 아크(track 위, -90°=12시 시작, 0.9s linear 회전). */
private fun DrawScope.drawAnalysisArc(
    center: Offset,
    radius: Float,
    arcColor: Color,
    trackColor: Color,
    rotation: Float,
) {
    val arcRadius = radius * ANALYSIS_RING_RADIUS_FRACTION // 지름 0.875×D(반경 42dp)
    val stroke = radius * ANALYSIS_RING_STROKE_FRACTION // 3dp
    val topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
    val arcSize = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f)
    drawCircle(color = trackColor, radius = arcRadius, center = center, style = Stroke(width = stroke))
    drawArc(
        color = arcColor,
        startAngle = ARC_START_DEG + rotation,
        sweepAngle = ARC_SWEEP_DEG,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}

/** Complete 체크 글리프(비색 형태 신호 — "완료"). */
private fun DrawScope.drawCheck(
    center: Offset,
    coreRadius: Float,
    color: Color,
) {
    val s = coreRadius * 0.55f
    val stroke = coreRadius * 0.14f
    val p1 = Offset(center.x - s * 0.6f, center.y + s * 0.05f)
    val p2 = Offset(center.x - s * 0.15f, center.y + s * 0.5f)
    val p3 = Offset(center.x + s * 0.65f, center.y - s * 0.45f)
    drawLine(color, p1, p2, strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, p2, p3, strokeWidth = stroke, cap = StrokeCap.Round)
}

private const val RIPPLE_COUNT = 3

private fun lerpF(
    start: Float,
    stop: Float,
    fraction: Float,
): Float = start + (stop - start) * fraction

@Composable
private fun MicState.outerColor(): Color =
    when (this) {
        MicState.Ready -> OceTheme.colors.voiceReadyOuter
        MicState.Recording -> OceTheme.colors.voiceRecordingOuter
        MicState.Analyzing -> OceTheme.colors.voiceAnalyzing.copy(alpha = 0.18f)
        MicState.Complete -> OceTheme.colors.voiceComplete.copy(alpha = 0.18f)
    }

@Composable
private fun MicState.coreColor(): Color =
    when (this) {
        MicState.Ready -> OceTheme.colors.voiceReadyCenter
        MicState.Recording -> OceTheme.colors.voiceRecordingCenter
        MicState.Analyzing -> OceTheme.colors.voiceAnalyzing
        MicState.Complete -> OceTheme.colors.voiceComplete
    }

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 140, heightDp = 560)
@Composable
private fun MicButtonAllStatesPreview() =
    OceTheme {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            MicButton(state = MicState.Ready, onTap = {})
            MicButton(state = MicState.Recording, onTap = {})
            MicButton(state = MicState.Analyzing, onTap = {}, enabled = false)
            MicButton(state = MicState.Complete, onTap = {}, enabled = false)
        }
    }

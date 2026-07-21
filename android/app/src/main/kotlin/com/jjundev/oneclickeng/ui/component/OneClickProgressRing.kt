package com.jjundev.oneclickeng.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.R
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlin.math.min

private val ProgressRingDefaultSize = 96.dp
private val ProgressRingInlineSize = 48.dp
private val DialogueLoadingIndicatorSize = 96.dp
private val DialogueLoadingIconSize = 44.dp
private val DialogueLoadingStrokeWidth = 4.dp
private const val DIALOGUE_LOADING_ARC_SWEEP_DEGREES = 90f
private const val DIALOGUE_LOADING_ARC_START_DEGREES = -90f
private const val DIALOGUE_LOADING_ROTATION_MS = 900
private const val DIALOGUE_LOADING_HEARTBEAT_MS = 1_200

/** 분석 중(A3/A6) 라이브 리전 카피. */
private const val ANALYZING_DESCRIPTION = "분석 중"

/** C7 프로그레스 링 표현 모드. */
sealed interface ProgressRingMode {
    /** 진행률 미상(분석 중 무한 회전). */
    data object Indeterminate : ProgressRingMode

    /** 진행률 확정. [fraction] 0f~1f. */
    data class Determinate(val fraction: Float) : ProgressRingMode
}

/**
 * C7 프로그레스 링 = M3 [CircularProgressIndicator] 래핑. 정본: 02-shared-components.md:75.
 *
 * 96dp(마이크 Analyzing) / 48dp(인라인, 터치 무관 표시용). 색은 `voice.analyzing` 기본.
 *
 * a11y: [ProgressRingMode.Determinate] 는 M3 내장 `progressBarRangeInfo`(퍼센트 announce)에 위임하고
 * 중복 `stateDescription` 을 얹지 않는다. [ProgressRingMode.Indeterminate] 는 범위 정보가 없어
 * `contentDescription="분석 중"` 을 부여한다. 링 자체는 live region 을 소유하지 않고, 상태 전이 announce 는
 * 소비처(MicButton A3)가 담당한다.
 */
@Composable
fun OneClickProgressRing(
    mode: ProgressRingMode,
    modifier: Modifier = Modifier,
    size: Dp = ProgressRingDefaultSize,
    color: Color = OceTheme.colors.voiceAnalyzing,
) {
    when (mode) {
        is ProgressRingMode.Determinate ->
            CircularProgressIndicator(
                progress = { mode.fraction },
                modifier = modifier.size(size),
                color = color,
            )

        ProgressRingMode.Indeterminate ->
            CircularProgressIndicator(
                modifier =
                    modifier
                        .size(size)
                        .semantics { contentDescription = ANALYZING_DESCRIPTION },
                color = color,
            )
    }
}

/**
 * 앱 전역 로딩 인디케이터. 프로토타입 Generating 표면과 같이 96dp 트랙 위를 4dp 브랜드 아크가
 * 0.9초마다 회전하고, 가운데에는 이모지 대신 앱의 대화 마크를 표시한다. 범용 [OneClickProgressRing]과
 * 분리해 마이크 분석·복습 완료 링의 기존 표현을 바꾸지 않는다.
 *
 * @param contentDescription 현재 로딩 작업을 설명하는 접근성 레이블.
 * @param reduceMotion true면 회전 없이 12시 아크를 고정해 시스템 동작 줄이기 설정을 따른다.
 */
@Composable
fun OneClickAppLoadingIndicator(
    modifier: Modifier = Modifier,
    contentDescription: String,
    reduceMotion: Boolean = rememberReduceMotion(),
) {
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val arcColor = MaterialTheme.colorScheme.primary
    val infiniteTransition = rememberInfiniteTransition(label = "dialogue-loading-indicator")
    val rotation =
        if (reduceMotion) {
            0f
        } else {
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(DIALOGUE_LOADING_ROTATION_MS, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "dialogue-loading-arc",
            ).value
        }
    val iconScale =
        if (reduceMotion) {
            1f
        } else {
            infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation =
                            keyframes {
                                durationMillis = DIALOGUE_LOADING_HEARTBEAT_MS
                                1.10f at 120
                                1f at 260
                                1.06f at 390
                                1f at 540
                            },
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "dialogue-loading-icon-heartbeat",
            ).value
        }

    Box(
        modifier =
            modifier
                .size(DialogueLoadingIndicatorSize)
                .semantics { this.contentDescription = contentDescription },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = min(size.width, size.height)
            val strokeWidth = DialogueLoadingStrokeWidth.toPx()
            val radius = diameter / 2f - strokeWidth / 2f
            val topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius)
            val arcSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)

            drawCircle(
                color = trackColor,
                radius = radius,
                center = center,
                style = stroke,
            )
            drawArc(
                color = arcColor,
                startAngle = DIALOGUE_LOADING_ARC_START_DEGREES + rotation,
                sweepAngle = DIALOGUE_LOADING_ARC_SWEEP_DEGREES,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_dialogue_loading_app_icon),
            contentDescription = null,
            modifier =
                Modifier
                    .size(DialogueLoadingIconSize)
                    .align(androidx.compose.ui.Alignment.Center)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 200)
@Composable
private fun OneClickProgressRingPreview() {
    OceTheme {
        OneClickProgressRing(mode = ProgressRingMode.Determinate(fraction = 0.6f))
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 200)
@Composable
private fun OneClickProgressRingInlinePreview() {
    OceTheme {
        OneClickProgressRing(
            mode = ProgressRingMode.Indeterminate,
            size = ProgressRingInlineSize,
        )
    }
}

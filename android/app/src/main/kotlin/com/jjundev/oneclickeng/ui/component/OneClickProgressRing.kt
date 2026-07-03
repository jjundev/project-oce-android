package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.theme.OceTheme

private val ProgressRingDefaultSize = 96.dp
private val ProgressRingInlineSize = 48.dp

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

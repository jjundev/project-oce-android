package com.jjundev.oneclickeng.ui.audio

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jjundev.oneclickeng.core.audio.AudioMath
import com.jjundev.oneclickeng.ui.theme.OceTheme
import com.jjundev.oneclickeng.ui.theme.WaveformBottom
import com.jjundev.oneclickeng.ui.theme.WaveformTop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.sin

private val WaveformHeight = 48.dp
private val BarGap = 4.dp
private val BarRadius = 4.dp
private const val REDUCE_MOTION_LEVEL = 0.18f
private const val MIN_BAR_FACTOR = 2f

/**
 * 실시간 파형(I2 crackle). [RecordingController.waveform] 를 관찰해 40바를 그린다.
 *
 * 높이 48dp 고정(fontScale 무관, A7). 장식 요소이므로 [clearAndSetSemantics] 로
 * 접근성 트리에서 제외한다(상태 전달은 마이크가 단독 — M1-08). reduce-motion 시 정적 트레이스.
 * 정본: [docs/design/audio-pipeline.md] §6, [docs/ui/03-signature-interactions.md] I2.
 */
@Composable
fun WaveformCanvas(
    waveform: StateFlow<FloatArray>,
    modifier: Modifier = Modifier,
) {
    val bars by waveform.collectAsStateWithLifecycle()
    WaveformCanvas(bars = bars, modifier = modifier)
}

@Composable
fun WaveformCanvas(
    bars: FloatArray,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val frame = if (reduceMotion) staticFrame() else bars
    val topColor = WaveformTop
    val bottomColor = WaveformBottom

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(WaveformHeight)
                .clearAndSetSemantics {},
    ) {
        val count = frame.size
        if (count == 0) return@Canvas
        val gapPx = BarGap.toPx()
        val radiusPx = BarRadius.toPx()
        val totalGap = gapPx * (count - 1)
        val barWidth = (size.width - totalGap) / count
        if (barWidth <= 0f) return@Canvas

        val brush = Brush.verticalGradient(listOf(topColor, bottomColor))
        val corner = CornerRadius(radiusPx, radiusPx)
        for (i in 0 until count) {
            val amp = frame[i].coerceIn(0f, 1f)
            val barHeight = maxOf(size.height * amp, radiusPx * MIN_BAR_FACTOR)
            val left = i * (barWidth + gapPx)
            val top = (size.height - barHeight) / 2f
            drawRoundRect(
                brush = brush,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = corner,
            )
        }
    }
}

@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

private fun staticFrame(): FloatArray = FloatArray(AudioMath.BAR_COUNT) { REDUCE_MOTION_LEVEL }

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 320)
@Composable
private fun WaveformCanvasPreview() {
    val sample = FloatArray(AudioMath.BAR_COUNT) { 0.3f + 0.5f * abs(sin(it * 0.4f)) }
    OceTheme {
        WaveformCanvas(bars = sample)
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 320)
@Composable
private fun WaveformCanvasFlowPreview() {
    val flow = MutableStateFlow(AudioMath.floorFrame())
    OceTheme {
        WaveformCanvas(waveform = flow)
    }
}

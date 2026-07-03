package com.jjundev.oneclickeng.ui.audio

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.core.audio.AudioMath
import org.junit.Rule
import org.junit.Test

class WaveformCanvasTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** A7: 파형 높이는 폰트스케일과 무관하게 48dp 로 고정. */
    @Test
    fun waveformHeightIsFixedRegardlessOfFontScale() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 2f, fontScale = 2f),
            ) {
                Box(modifier = Modifier.testTag("waveform")) {
                    WaveformCanvas(bars = FloatArray(AudioMath.BAR_COUNT) { 0.5f })
                }
            }
        }

        composeRule.onNodeWithTag("waveform").assertHeightIsEqualTo(48.dp)
    }
}

package com.jjundev.oneclickeng.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class OceThemeColorContractTest {
    @Test
    fun `dark ColorScheme matches Prototype Flow semantic tokens`() {
        assertEquals(Color(0xFF39A0ED), DarkColorScheme.primary)
        assertEquals(Color(0xFFFFFFFF), DarkColorScheme.onPrimary)
        assertEquals(Color(0xFF0E0F12), DarkColorScheme.background)
        assertEquals(Color(0xFF1A1B20), DarkColorScheme.surface)
        assertEquals(Color(0xFFF2F3F5), DarkColorScheme.onBackground)
        assertEquals(Color(0xFFF2F3F5), DarkColorScheme.onSurface)
        assertEquals(Color(0xFFA9ADB6), DarkColorScheme.onSurfaceVariant)
        assertEquals(Color(0xFF2A2C32), DarkColorScheme.outlineVariant)
        assertEquals(Color(0xFFFF8A80), DarkColorScheme.error)
        assertEquals(Color(0xFFFFFFFF), DarkColorScheme.onError)
    }

    @Test
    fun `dark custom colors match Prototype Flow semantic tokens`() {
        assertEquals(Color(0xFF2B7FBB), DarkOceColors.primaryPressed)
        assertEquals(Color(0xFF7C818C), DarkOceColors.textTertiary)
        assertEquals(Color(0xFF3A3D45), DarkOceColors.borderStrong)
        assertEquals(Color(0x99000000), DarkOceColors.scrim)
        assertEquals(Color(0xFF009B72), DarkOceColors.feedbackNaturalAccent)
        assertEquals(Color(0xFF0F2A22), DarkOceColors.feedbackNaturalBg)
        assertEquals(Color(0xFFEF767A), DarkOceColors.feedbackCorrectAccent)
        assertEquals(Color(0xFF321B21), DarkOceColors.feedbackCorrectBg)
        assertEquals(Color(0xFF8E96A1), DarkOceColors.voiceReadyCenter)
        assertEquals(Color(0xFF2A2C32), DarkOceColors.voiceReadyOuter)
        assertEquals(Color(0xFFFF6B66), DarkOceColors.voiceRecordingCenter)
        assertEquals(Color(0xFF3A1F22), DarkOceColors.voiceRecordingOuter)
        assertEquals(Color(0xFFB0BEC5), DarkOceColors.voiceAnalyzing)
        assertEquals(Color(0xFF66BB6A), DarkOceColors.voiceComplete)
        assertEquals(Color(0xFFFF7A33), DarkOceColors.gameStreak)
        assertEquals(Color(0xFFFFD24D), DarkOceColors.gameSaveGold)
        assertEquals(Color(0xFF39A0ED), DarkOceColors.gradientStart)
        assertEquals(Color(0xFF2B7FBB), DarkOceColors.gradientEnd)
        assertEquals(Color(0xFF9E9E9E), DarkOceColors.waveformTop)
        assertEquals(Color(0xFF757575), DarkOceColors.waveformBottom)
    }
}

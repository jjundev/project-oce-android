package com.jjundev.oneclickeng.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * M3 [androidx.compose.material3.ColorScheme] 슬롯에 대응 자리가 없는 §2.2 의미색 + 부록 C 보조색을
 * 담는 커스텀 확장. 값 정본은 design-tokens.md 부록 B.2·C.1 (여기서 재정의 금지, 값은 [Color] 팔레트 참조).
 */
@Suppress("LongParameterList")
@Immutable
data class OceColors(
    // brand.primaryPressed — 그라데이션 종점(정적 키; SoT B.1 인터랙션-레이어 규정과 배치 다름, 의도적)
    val primaryPressed: Color,
    val textTertiary: Color,
    val borderStrong: Color,
    val scrim: Color,
    val feedbackNaturalAccent: Color,
    val feedbackNaturalBg: Color,
    val feedbackCorrectAccent: Color,
    val feedbackCorrectBg: Color,
    val voiceReadyCenter: Color,
    val voiceReadyOuter: Color,
    val voiceRecordingCenter: Color,
    val voiceRecordingOuter: Color,
    val voiceAnalyzing: Color,
    val voiceComplete: Color,
    val gameStreak: Color,
    val gameSaveGold: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val waveformTop: Color,
    val waveformBottom: Color,
) {
    /** 브랜드 그라데이션(135° 선형, start→end). §2.2 카드 배경. */
    fun brandGradient(): Brush = Brush.linearGradient(listOf(gradientStart, gradientEnd))

    /** crackle 파형 세로 그라데이션(top→bottom). 렌더/anatomy 정본은 §6 / I2. */
    fun waveformGradient(): Brush = Brush.verticalGradient(listOf(waveformTop, waveformBottom))
}

internal val LightOceColors =
    OceColors(
        primaryPressed = BrandBluePressed,
        textTertiary = TextTertiaryLight,
        borderStrong = BorderStrongLight,
        scrim = ScrimLight,
        feedbackNaturalAccent = FeedbackNaturalAccent,
        feedbackNaturalBg = FeedbackNaturalBgLight,
        feedbackCorrectAccent = FeedbackCorrectAccent,
        feedbackCorrectBg = FeedbackCorrectBgLight,
        voiceReadyCenter = VoiceReadyCenterLight,
        voiceReadyOuter = VoiceReadyOuterLight,
        voiceRecordingCenter = VoiceRecordingCenterLight,
        voiceRecordingOuter = VoiceRecordingOuterLight,
        voiceAnalyzing = VoiceAnalyzingLight,
        voiceComplete = VoiceCompleteLight,
        gameStreak = GameStreakLight,
        gameSaveGold = GameSaveGoldLight,
        gradientStart = BrandBlue,
        gradientEnd = BrandBluePressed,
        waveformTop = WaveformTop,
        waveformBottom = WaveformBottom,
    )

internal val DarkOceColors =
    OceColors(
        primaryPressed = BrandBluePressed,
        textTertiary = TextTertiaryDark,
        borderStrong = BorderStrongDark,
        scrim = ScrimDark,
        feedbackNaturalAccent = FeedbackNaturalAccent,
        feedbackNaturalBg = FeedbackNaturalBgDark,
        feedbackCorrectAccent = FeedbackCorrectAccent,
        feedbackCorrectBg = FeedbackCorrectBgDark,
        voiceReadyCenter = VoiceReadyCenterDark,
        voiceReadyOuter = VoiceReadyOuterDark,
        voiceRecordingCenter = VoiceRecordingCenterDark,
        voiceRecordingOuter = VoiceRecordingOuterDark,
        voiceAnalyzing = VoiceAnalyzingDark,
        voiceComplete = VoiceCompleteDark,
        gameStreak = GameStreakDark,
        gameSaveGold = GameSaveGoldDark,
        gradientStart = BrandBlue,
        gradientEnd = BrandBluePressed,
        waveformTop = WaveformTop,
        waveformBottom = WaveformBottom,
    )

val LocalOceColors = staticCompositionLocalOf<OceColors> { error("OceColors 가 제공되지 않았습니다. OceTheme 안에서 사용하세요.") }

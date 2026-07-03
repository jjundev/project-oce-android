package com.jjundev.oneclickeng.ui.theme

import androidx.compose.ui.graphics.Color

// 이 파일은 raw hex 색상 리터럴을 선언하는 "유일한" 허용 지점이다(hex 가드 allowlist).
// 값 정본: docs/design/design_system_src/design-tokens.md §2.1·2.2·부록 C
//         + product-design-system-buildspec.md Part A.1(의미색 다크값).
// 다른 코드는 Color(0x…)를 직접 쓰지 말고 OceColors / MaterialTheme.colorScheme 토큰만 참조한다.

// --- 브랜드 & 공통 ---
internal val White = Color(0xFFFFFFFF)
internal val BrandBlue = Color(0xFF39A0ED)
internal val BrandBluePressed = Color(0xFF2B7FBB)

// --- 표면 / 텍스트 / 보더 (라이트/다크) — §2.1 ---
internal val BackgroundLight = Color(0xFFF3F4F6)
internal val BackgroundDark = Color(0xFF0E0F12)
internal val CardLight = White
internal val CardDark = Color(0xFF1A1B20)
internal val TextPrimaryLight = Color(0xFF353C45)
internal val TextPrimaryDark = Color(0xFFF2F3F5)
internal val TextSecondaryLight = Color(0xFF676B73)
internal val TextSecondaryDark = Color(0xFFA9ADB6)
internal val TextTertiaryLight = Color(0xFF8E9399)
internal val TextTertiaryDark = Color(0xFF7C818C)
internal val HairlineLight = Color(0xFFE8EAED)
internal val HairlineDark = Color(0xFF2A2C32)
internal val BorderStrongLight = Color(0xFFC9CDD2) // 부록 C
internal val BorderStrongDark = Color(0xFF3A3D45)

// --- 의미색 (§2.2 + buildspec A.1 다크값) ---
internal val FeedbackNaturalAccent = Color(0xFF009B72) // 라이트 = 다크 (hue 보존)
internal val FeedbackNaturalBgLight = Color(0xFFE6F5F0)
internal val FeedbackNaturalBgDark = Color(0xFF0F2A22)
internal val FeedbackCorrectAccent = Color(0xFFEF767A) // 라이트 = 다크
internal val FeedbackCorrectBgLight = Color(0xFFFDEEEE)
internal val FeedbackCorrectBgDark = Color(0xFF321B21)
internal val StateErrorLight = Color(0xFFE53935)
internal val StateErrorDark = Color(0xFFFF8A80)

// 음성 4상태 (중앙 / 외륜)
internal val VoiceReadyCenterLight = Color(0xFF55606C)
internal val VoiceReadyCenterDark = Color(0xFF8E96A1)
internal val VoiceReadyOuterLight = HairlineLight // #E8EAED
internal val VoiceReadyOuterDark = HairlineDark // #2A2C32
internal val VoiceRecordingCenterLight = StateErrorLight // #E53935
internal val VoiceRecordingCenterDark = Color(0xFFFF6B66)
internal val VoiceRecordingOuterLight = Color(0xFFFCE4EC)
internal val VoiceRecordingOuterDark = Color(0xFF3A1F22)
internal val VoiceAnalyzingLight = Color(0xFF6B7684)
internal val VoiceAnalyzingDark = Color(0xFFB0BEC5)
internal val VoiceCompleteLight = Color(0xFF4CAF50)
internal val VoiceCompleteDark = Color(0xFF66BB6A)

// 게임화
internal val GameStreakLight = Color(0xFFFF5C00)
internal val GameStreakDark = Color(0xFFFF7A33)
internal val GameSaveGoldLight = Color(0xFFFFC107)
internal val GameSaveGoldDark = Color(0xFFFFD24D)

// 스크림 / 파형 (부록 C) — waveform 은 라이트/다크 공통 그레이
internal val ScrimLight = Color(0x6B0E0F12)
internal val ScrimDark = Color(0x99000000)
internal val WaveformTop = Color(0xFF9E9E9E)
internal val WaveformBottom = Color(0xFF757575)

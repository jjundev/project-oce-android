package com.jjundev.oneclickeng.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * elevation 토큰. 기본 0dp(플랫), 유일 예외 하단 내비 8dp. 값 정본: design-tokens.md §4.4.
 * 그 외 깊이는 surface 레이어 + border.hairline 로 표현한다.
 */
@Immutable
data class OceElevation(
    val default: Dp = 0.dp,
    val nav: Dp = 8.dp,
)

internal val OceElevationTokens = OceElevation()

val LocalOceElevation = staticCompositionLocalOf { OceElevationTokens }

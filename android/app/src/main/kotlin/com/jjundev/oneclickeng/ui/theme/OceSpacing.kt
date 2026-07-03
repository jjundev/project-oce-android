package com.jjundev.oneclickeng.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 간격 스케일(4dp 기반) 7단 + 시맨틱 별칭. 값 정본: design-tokens.md §4.2.
 */
@Immutable
data class OceSpacing(
    val xs: Dp = 6.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val huge: Dp = 40.dp,
    // 시맨틱 별칭
    val sheetPadding: Dp = 24.dp,
    val sectionGap: Dp = 24.dp,
    val actionGap: Dp = 12.dp,
    val loadingPadding: Dp = 40.dp,
)

internal val OceSpacingTokens = OceSpacing()

val LocalOceSpacing = staticCompositionLocalOf { OceSpacingTokens }

package com.jjundev.oneclickeng.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.R

// Pretendard 5웨이트(400/500/600/700/800) 전역 폰트. archive 9종 중 이 5종만 의도적으로 번들한다
// (thin/extra_light/light/black 제외 — buildspec Part C "5종 확정, 9종 비목표").
private val Pretendard =
    FontFamily(
        Font(R.font.pretendard_regular, FontWeight.Normal),
        Font(R.font.pretendard_medium, FontWeight.Medium),
        Font(R.font.pretendard_semi_bold, FontWeight.SemiBold),
        Font(R.font.pretendard_bold, FontWeight.Bold),
        Font(R.font.pretendard_extra_bold, FontWeight.ExtraBold),
    )

// lineHeight 는 배수(em), letterSpacing 은 em 으로 표현해 fontScale 을 존중한다.
private fun oceStyle(
    weight: FontWeight,
    size: TextUnit,
    lineHeightEm: Float,
    letterSpacingEm: Float = 0f,
): TextStyle =
    TextStyle(
        fontFamily = Pretendard,
        fontWeight = weight,
        fontSize = size,
        lineHeight = lineHeightEm.em,
        letterSpacing = letterSpacingEm.em,
    )

/**
 * 타이포 토큰 9종. 값 정본: design-tokens.md §4.1 + buildspec A.3(lineHeight/letterSpacing).
 * turnScore 는 screenTitle 과 값이 같으나(28sp Bold) 시맨틱 분리(점수 ≠ 화면 제목, 향후 독립 조정).
 */
@Suppress("LongParameterList")
@Immutable
data class OceTypography(
    val screenTitle: TextStyle,
    val scoreDisplay: TextStyle,
    val turnScore: TextStyle,
    val dialogHeader: TextStyle,
    val body: TextStyle,
    val sectionLabel: TextStyle,
    val helper: TextStyle,
    val tabActive: TextStyle,
    val tabInactive: TextStyle,
)

internal val OceTypographyTokens =
    OceTypography(
        screenTitle = oceStyle(FontWeight.Bold, 28.sp, 1.2f, -0.02f),
        scoreDisplay = oceStyle(FontWeight.Bold, 56.sp, 1.2f, -0.02f),
        turnScore = oceStyle(FontWeight.Bold, 28.sp, 1.2f, -0.02f),
        dialogHeader = oceStyle(FontWeight.Bold, 22.sp, 1.35f, -0.02f),
        body = oceStyle(FontWeight.Normal, 16.sp, 1.45f),
        sectionLabel = oceStyle(FontWeight.Bold, 14.sp, 1.2f),
        helper = oceStyle(FontWeight.Normal, 13.sp, 1.45f),
        tabActive = oceStyle(FontWeight.Bold, 13.sp, 1.2f),
        tabInactive = oceStyle(FontWeight.Normal, 11.sp, 1.2f),
    )

val LocalOceTypography = staticCompositionLocalOf { OceTypographyTokens }

// M3 스톡 컴포넌트(Text·Button·TextField 등)가 고정 슬롯명으로 읽는 최소 브릿지.
// 앱 자체 컴포넌트는 OceTheme.typography 를 직접 소비한다.
internal val OceM3Typography =
    Typography(
        // Text 기본(16sp)
        bodyLarge = OceTypographyTokens.body,
        // Button 라벨(14sp Bold)
        labelLarge = OceTypographyTokens.sectionLabel,
        // helper(13sp)
        bodySmall = OceTypographyTokens.helper,
    )

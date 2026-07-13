package com.jjundev.oneclickeng.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

// M3 ColorScheme 10슬롯 write(8 semantic 토큰 + 2 흰색 리터럴 onPrimary/onError). 나머지 ~38 슬롯은 M3 default.
// 매핑 정본: docs/ui/01-foundations.md F2 rev.3 #3 · design-tokens.md 부록 B.1.
internal val LightColorScheme =
    lightColorScheme(
        primary = BrandBlue,
        onPrimary = White,
        background = BackgroundLight,
        surface = CardLight,
        onBackground = TextPrimaryLight,
        onSurface = TextPrimaryLight,
        onSurfaceVariant = TextSecondaryLight,
        outlineVariant = HairlineLight,
        error = StateErrorLight,
        onError = White,
    )

internal val DarkColorScheme =
    darkColorScheme(
        primary = BrandBlue,
        onPrimary = White,
        background = BackgroundDark,
        surface = CardDark,
        onBackground = TextPrimaryDark,
        onSurface = TextPrimaryDark,
        onSurfaceVariant = TextSecondaryDark,
        outlineVariant = HairlineDark,
        error = StateErrorDark,
        onError = White,
    )

/**
 * 앱 전역 테마 진입점. 고정 브랜드 팔레트를 싣고(다이내믹 컬러 OFF — 의미 색 보존, PRD §11),
 * 라이트/다크는 color provider 만 스왑한다. 타이포/형태/간격/모션/elevation 은 테마 불변 단일 인스턴스.
 * 슬롯 없는 의미색·타이포·형태 등은 CompositionLocal 로 provide 하고 [OceTheme] 접근자로 읽는다.
 */
@Composable
fun OceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val oceColors = if (darkTheme) DarkOceColors else LightOceColors
    CompositionLocalProvider(
        LocalOceColors provides oceColors,
        LocalOceTypography provides OceTypographyTokens,
        LocalOceShapes provides OceShapesTokens,
        LocalOceSpacing provides OceSpacingTokens,
        LocalOceMotion provides OceMotionTokens,
        LocalOceElevation provides OceElevationTokens,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = OceM3Typography,
            shapes = OceM3Shapes,
            content = content,
        )
    }
}

/**
 * 테마 토큰 접근자. M3 [MaterialTheme] 관용구(동명 함수 + 객체)와 동일 패턴.
 * 색은 MaterialTheme.colorScheme 와 OceTheme.colors 로 나뉜다(슬롯 유/무 기준).
 */
object OceTheme {
    val colors: OceColors
        @Composable @ReadOnlyComposable
        get() = LocalOceColors.current

    val typography: OceTypography
        @Composable @ReadOnlyComposable
        get() = LocalOceTypography.current

    val shapes: OceShapes
        @Composable @ReadOnlyComposable
        get() = LocalOceShapes.current

    val spacing: OceSpacing
        @Composable @ReadOnlyComposable
        get() = LocalOceSpacing.current

    val motion: OceMotion
        @Composable @ReadOnlyComposable
        get() = LocalOceMotion.current

    val elevation: OceElevation
        @Composable @ReadOnlyComposable
        get() = LocalOceElevation.current
}

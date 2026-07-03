package com.jjundev.oneclickeng.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(primary = BrandBlue)
private val DarkColors = darkColorScheme(primary = BrandBlue)

/**
 * 앱 전역 M3 테마 스텁.
 * 고정 브랜드 팔레트·라이트/다크 완전 토큰셋은 M0-03 에서 확정한다.
 * 다이내믹 컬러는 의미 색 보존을 위해 사용하지 않는다(PRD §11).
 */
@Composable
fun OceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = OceTypography,
        content = content,
    )
}

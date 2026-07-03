package com.jjundev.oneclickeng.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// 검증용 프리뷰(라이트/다크 2종). 의미색·타이포 토큰이 양 테마에서 보존되는지 육안 확인.
// hex 리터럴을 쓰지 않고 토큰만 참조한다(hex 가드 준수).

@Composable
private fun Swatch(color: Color) {
    Column(
        modifier =
            Modifier
                .size(40.dp)
                .background(color),
        content = {},
    )
}

@Composable
private fun ThemeTokensSample() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(OceTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
        ) {
            Text(
                text = "화면 제목",
                style = OceTheme.typography.screenTitle,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "88",
                style = OceTheme.typography.scoreDisplay,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "본문 텍스트 — Pretendard 16sp",
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onBackground,
            )
            // 의미색 스와치: 자연/정확/음성(녹음)/streak/save-gold
            Row(horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
                Swatch(OceTheme.colors.feedbackNaturalAccent)
                Swatch(OceTheme.colors.feedbackCorrectAccent)
                Swatch(OceTheme.colors.voiceRecordingCenter)
                Swatch(OceTheme.colors.gameStreak)
                Swatch(OceTheme.colors.gameSaveGold)
            }
        }
    }
}

@Preview(name = "Tokens · Light")
@Composable
private fun ThemeTokensLightPreview() {
    OceTheme(darkTheme = false) { ThemeTokensSample() }
}

@Preview(name = "Tokens · Dark")
@Composable
private fun ThemeTokensDarkPreview() {
    OceTheme(darkTheme = true) { ThemeTokensSample() }
}

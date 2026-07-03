package com.jjundev.oneclickeng.ui.component.primitive

import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 비파일럿 프리미티브 = M3 [Badge] 얇은 래핑 + 토큰만. 기본 컨테이너 `brand.primary`/`onPrimary`.
 *
 * 주: C14 streak 칩(`radius.pill` + 🔥아이콘 + 텍스트, `game.streak` 색)의 pill 변형은 제품특화라
 * M0-06(`OneClickStreakChip`)에서 이 프리미티브를 재사용/합성해 만든다 — 여기서는 M3 기본 슬롯까지만.
 */
@Composable
fun OneClickBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Badge(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Text(text = text)
    }
}

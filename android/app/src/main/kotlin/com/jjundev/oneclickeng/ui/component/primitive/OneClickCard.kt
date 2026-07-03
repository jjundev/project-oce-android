package com.jjundev.oneclickeng.ui.component.primitive

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 비파일럿 프리미티브 = M3 [Card] 얇은 래핑 + 토큰만(독자 anatomy 신설 금지, buildspec 비파일럿 스텁).
 * `surface.card` 컨테이너 · `radius.16` · 플랫 elevation(기본 0dp, 깊이는 surface+hairline 로 표현).
 */
@Composable
fun OneClickCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = OceTheme.shapes.radius16,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = OceTheme.elevation.default),
        content = content,
    )
}

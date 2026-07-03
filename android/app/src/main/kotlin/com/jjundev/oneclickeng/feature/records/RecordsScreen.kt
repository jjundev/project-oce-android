package com.jjundev.oneclickeng.feature.records

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jjundev.oneclickeng.R
import com.jjundev.oneclickeng.ui.foundation.TabScreenScaffold
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 기록 탭. M0-09 단계에서는 F8 골격만 세운 빈 화면이다. 실제 콘텐츠는 M2-05 가 채운다.
 */
@Composable
fun RecordsScreen(modifier: Modifier = Modifier) {
    TabScreenScaffold(titleRes = R.string.tab_records, modifier = modifier) {
        item {
            Text(
                text = "준비 중",
                style = OceTheme.typography.body,
            )
        }
    }
}

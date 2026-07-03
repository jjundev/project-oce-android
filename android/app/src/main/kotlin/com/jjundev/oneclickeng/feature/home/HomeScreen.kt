package com.jjundev.oneclickeng.feature.home

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jjundev.oneclickeng.R
import com.jjundev.oneclickeng.ui.foundation.TabScreenScaffold
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 학습(홈) 탭. M0-09 단계에서는 F8 골격(단일 LazyColumn·인라인 타이틀·거터)만 세운 빈 화면이다.
 * 실제 콘텐츠는 M3-08 이 이 자리를 채운다. ViewModel 은 콘텐츠 이슈에서 도입(뼈대=Route/Screen 규약).
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    TabScreenScaffold(titleRes = R.string.tab_home, modifier = modifier) {
        item {
            Text(
                text = "준비 중",
                style = OceTheme.typography.body,
            )
        }
    }
}

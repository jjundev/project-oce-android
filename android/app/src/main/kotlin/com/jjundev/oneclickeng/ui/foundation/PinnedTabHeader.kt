package com.jjundev.oneclickeng.ui.foundation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 상시탭 공통 타이틀바(프로토 정합) — 48dp 고정 높이 중앙 헤더, 스크롤과 무관하게 상단에 고정된다.
 * 설정·기록 탭이 공유해 동일한 헤더를 보장한다([SettingsContent], [TabScreenScaffold]).
 * `TopAppBar` 를 두지 않으므로 `semantics { heading() }` 로 화면 제목 heading 랜드마크를 대체한다.
 */
@Composable
fun PinnedTabHeader(
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(titleRes),
            style = OceTheme.typography.summaryHeadline.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
    }
}

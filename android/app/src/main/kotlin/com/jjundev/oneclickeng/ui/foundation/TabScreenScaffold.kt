package com.jjundev.oneclickeng.ui.foundation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 상시탭 화면 공통 골격(F8 #1·#3·#4). 화면당 **단일 [LazyColumn] 호스트**를 세우고,
 * 첫 item 으로 인라인 대형 타이틀([OceTypography.screenTitle], 28sp)을 스크롤 동반 배치한다.
 *
 * - 가로 거터 20dp = `OceTheme.spacing.xl`(F8 #1).
 * - 타이틀에 `semantics { heading() }` 부여 — `TopAppBar` 를 두지 않으므로(F8 #3 인라인 타이틀)
 *   사라진 heading 랜드마크를 대체해 TalkBack 이 화면 제목을 헤딩으로 노출한다. 전면 스크린리더
 *   경로 검증은 F7 로 이연.
 * - 스크롤 상태([rememberLazyListState])는 내장 Saver 로 회전에 생존하고, NavHost restoreState 로
 *   탭 전환 간에도 복원된다.
 *
 * 실제 콘텐츠 화면(M3-08 홈 · M2-05 기록 · M3-09 설정)은 이 골격을 채택해 [content] 에 item 을 채운다.
 */
@Composable
fun TabScreenScaffold(
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit = {},
) {
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = OceTheme.spacing.xl),
        state = rememberLazyListState(),
    ) {
        item(key = "title") {
            Text(
                text = stringResource(titleRes),
                style = OceTheme.typography.screenTitle,
                modifier =
                    Modifier
                        .padding(top = OceTheme.spacing.xxl, bottom = OceTheme.spacing.lg)
                        .semantics { heading() },
            )
        }
        content()
    }
}

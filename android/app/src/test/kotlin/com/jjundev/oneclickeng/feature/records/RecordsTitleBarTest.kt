package com.jjundev.oneclickeng.feature.records

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jjundev.oneclickeng.ui.foundation.PinnedTabHeader
import com.jjundev.oneclickeng.R
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 회귀: 기록 탭 타이틀바는 설정 탭과 동일한 [PinnedTabHeader] 를 쓴다. 헤더 컴포저블이 존재하고
 * 제목 문자열을 렌더하는지 스모크로 검증한다(중앙정렬·고정은 시각 캡처로 대조).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RecordsTitleBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pinned_header_renders_title() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PinnedTabHeader(titleRes = R.string.tab_records)
                }
            }
        }
        composeRule.onNodeWithText("기록").assertIsDisplayed()
    }
}

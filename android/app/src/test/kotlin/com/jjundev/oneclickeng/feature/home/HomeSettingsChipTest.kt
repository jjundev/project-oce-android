package com.jjundev.oneclickeng.feature.home

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 회귀: 홈 인라인 설정 칩은 난이도·턴수를 붙이지 않고 "설정 변경" 단일 라벨만 노출한다.
 * level 해소(easy)·이어하기 없음 조건에서 칩이 렌더되고, 정확 텍스트 "설정 변경" 노드가 존재해야 한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HomeSettingsChipTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settings_chip_shows_single_label() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                HomeContent(
                    state =
                        HomeUiState(
                            isOnline = true,
                            hasResume = false,
                            level = "easy",
                            length = 5,
                            selectedSituation = SelectedSituation("id", "카페에서 주문하기", "seed"),
                            situations = emptyList(),
                        ),
                    onStartLearning = {},
                    onResumeContinue = {},
                    onResumeStartNew = {},
                    onViewRecords = {},
                    onOfflineBlocked = {},
                )
            }
        }
        // 인라인 설정 칩까지 스크롤(라지 뷰포트 비의존). 스크롤 대상은 부분일치로 찾는다.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("설정 변경", substring = true))
        // 정확 텍스트 매칭 — 구버전 "설정 변경 · 쉬움 · 5턴" 이면 정확일치 실패(RED).
        composeRule.onNodeWithText("설정 변경").assertIsDisplayed()
    }
}

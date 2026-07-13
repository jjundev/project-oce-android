package com.jjundev.oneclickeng.feature.home

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 회귀: 홈 지표 스트립의 "오늘 N분"·"N일 연속" 은 [com.jjundev.oneclickeng.ui.component.OneClickCountUp]
 * 슬롯머신 카운트업을 통과한다. 카운트업은 최종 라벨을 contentDescription 으로 싣으므로(clearAndSetSemantics),
 * reduceMotion 으로 즉시 스냅시킨 뒤 두 라벨이 그 값으로 노출되는지 본다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HomeStatsStripTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun study_time_and_streak_render_as_count_up_labels() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                HomeContent(
                    state =
                        HomeUiState(
                            isOnline = true,
                            hasResume = false,
                            level = "easy",
                            length = 5,
                            studyMinutes = 2,
                            streak = 1,
                            selectedSituation = SelectedSituation("id", "카페에서 주문하기", "seed"),
                            situations = emptyList(),
                        ),
                    onStartLearning = {},
                    onResumeContinue = {},
                    onResumeStartNew = {},
                    onViewRecords = {},
                    onOfflineBlocked = {},
                    reduceMotion = true,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("오늘 2분").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("1일 연속").assertIsDisplayed()
    }
}

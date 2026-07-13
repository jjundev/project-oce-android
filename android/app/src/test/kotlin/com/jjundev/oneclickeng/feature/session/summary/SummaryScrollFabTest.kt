package com.jjundev.oneclickeng.feature.session.summary

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class SummaryScrollFabTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows down chevron at top`() {
        setScreen(onDone = {})

        composeRule.onNodeWithContentDescription("아래로 스크롤").assertIsDisplayed()
    }

    @Test
    fun `down tap pages the scroll until it reaches the bottom`() {
        setScreen(onDone = {})
        // FAB 버튼 자체로 page-down 을 반복해 끝까지 스크롤되는지 검증(스와이프 아님).
        repeat(15) {
            val down = composeRule.onAllNodesWithContentDescription("아래로 스크롤")
            if (down.fetchSemanticsNodes().isEmpty()) return@repeat
            down.onFirst().performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithContentDescription("맨 위로").assertIsDisplayed()
    }

    @Test
    fun `flips to up chevron at bottom and returns to top on tap`() {
        setScreen(onDone = {})

        composeRule.onNodeWithTag(SUMMARY_SCROLL_CONTENT_TAG).performTouchInput {
            repeat(8) { swipeUp(durationMillis = 1) }
        }
        composeRule.onNodeWithContentDescription("맨 위로").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("맨 위로").performClick()
        composeRule.onNodeWithContentDescription("아래로 스크롤").assertIsDisplayed()
    }

    @Test
    fun `hides fab when the done footer is absent`() {
        setScreen(onDone = null)

        composeRule.onNodeWithContentDescription("아래로 스크롤").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("맨 위로").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [26], application = Application::class, qualifiers = "w411dp-h2000dp")
    fun `hides fab when content is not scrollable`() {
        setScreen(onDone = {}, state = shortState())

        composeRule.onNodeWithContentDescription("아래로 스크롤").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("맨 위로").assertDoesNotExist()
    }

    private fun setScreen(
        onDone: (() -> Unit)?,
        state: SummaryState = tallState(),
    ) {
        composeRule.setContent {
            OceTheme {
                Surface {
                    SummaryScreen(
                        state = state,
                        onRetry = {},
                        onToggleSaveWord = {},
                        onToggleSaveExpression = {},
                        onDone = onDone,
                    )
                }
            }
        }
    }

    private fun tallState() =
        SummaryState(
            totalScore = 85,
            highlight = HighlightTurn("커피 주세요", "Could I get a latte?", 92),
            bookmarks = List(8) { BookmarkCard("I got lost on the way.", "오는 길에 길을 잃었어요.") },
            accrual = AccrualStrip(streakDays = 1, xp = 20),
            bundle =
                SectionBundle.Sectioned(
                    expression =
                        SummarySectionState.Ready(
                            List(20) {
                                ExpressionCard(
                                    ExpressionType.Natural,
                                    "커피 주세요",
                                    "One coffee",
                                    "Could I grab a coffee?",
                                    "가볍게 주문할 때 자연스러워요.",
                                )
                            },
                        ),
                    word =
                        SummarySectionState.Ready(
                            List(12) {
                                WordCard("grab", "잽싸게 가져오다", "verb", "B1", "Let me grab it.", "제가 가져올게요.")
                            },
                        ),
                    coaching =
                        SummarySectionState.Ready(Coaching("끝까지 대화를 이어갔어요.", "과거형을 한 번 써볼까요?")),
                ),
        )

    /** 뷰포트에 다 들어가 스크롤 불가(maxValue<=0)여야 하는 최소 콘텐츠 — FAB 숨김 검증용. */
    private fun shortState() =
        SummaryState(
            totalScore = null,
            highlight = null,
            bookmarks = emptyList(),
            accrual = AccrualStrip(streakDays = 0, xp = 0),
            bundle = SectionBundle.BundleLoading,
        )
}

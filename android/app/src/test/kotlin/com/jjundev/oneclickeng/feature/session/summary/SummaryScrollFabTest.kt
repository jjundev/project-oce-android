package com.jjundev.oneclickeng.feature.session.summary

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
    fun `shows down chevron at top and stays down after a single page-down`() {
        setScreen(onDone = {})

        composeRule.onNodeWithContentDescription("아래로 스크롤").assertIsDisplayed()

        // 한 번 page-down 해도 20개 표현 카드라 끝에 못 닿음 → 여전히 "아래로 스크롤".
        composeRule.onNodeWithContentDescription("아래로 스크롤").performClick()
        composeRule.onNodeWithContentDescription("아래로 스크롤").assertIsDisplayed()
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

    private fun setScreen(onDone: (() -> Unit)?) {
        composeRule.setContent {
            OceTheme {
                Surface {
                    SummaryScreen(
                        state = tallState(),
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
}

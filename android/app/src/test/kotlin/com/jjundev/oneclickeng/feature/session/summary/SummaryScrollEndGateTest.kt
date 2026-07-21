package com.jjundev.oneclickeng.feature.session.summary

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class SummaryScrollEndGateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `lower bound invokes callback only after 500 milliseconds`() {
        composeRule.mainClock.autoAdvance = false
        var callbackCount = 0
        setScreen { callbackCount += 1 }

        scrollToBottom()
        val lowerBoundTime = composeRule.mainClock.currentTime
        advanceTo(lowerBoundTime + GOOGLE_SAVE_PROMPT_DELAY_MS - 1)
        composeRule.runOnIdle { assertEquals(0, callbackCount) }

        advanceTo(lowerBoundTime + GOOGLE_SAVE_PROMPT_DELAY_MS)
        composeRule.runOnIdle { assertEquals(1, callbackCount) }
    }

    @Test
    fun `leaving the lower bound cancels the pending callback`() {
        composeRule.mainClock.autoAdvance = false
        var callbackCount = 0
        setScreen { callbackCount += 1 }

        scrollToBottom()
        composeRule.mainClock.advanceTimeBy(250, ignoreFrameDuration = true)
        composeRule.onNodeWithTag(SUMMARY_SCROLL_CONTENT_TAG).performTouchInput { swipeDown() }
        composeRule.mainClock.advanceTimeBy(GOOGLE_SAVE_PROMPT_DELAY_MS, ignoreFrameDuration = true)

        composeRule.runOnIdle { assertEquals(0, callbackCount) }
    }

    @Test
    fun `content shrinking while still at the lower bound preserves the pending delay`() {
        composeRule.mainClock.autoAdvance = false
        var callbackCount = 0
        var expressionCount by mutableIntStateOf(24)
        composeRule.setContent {
            OceTheme {
                Surface {
                    SummaryScreen(
                        state = tallState(expressionCount),
                        onRetry = {},
                        onToggleSaveWord = {},
                        onToggleSaveExpression = {},
                        onToggleSaveBookmark = {},
                        onScrollEndReached = { callbackCount += 1 },
                    )
                }
            }
        }

        composeRule.onAllNodesWithText("더보기")[0].performClick()
        scrollToBottom()
        composeRule.mainClock.advanceTimeBy(250, ignoreFrameDuration = true)
        composeRule.runOnIdle { expressionCount = 8 }
        composeRule.mainClock.advanceTimeBy(250, ignoreFrameDuration = true)

        composeRule.runOnIdle { assertEquals(1, callbackCount) }
    }

    private fun setScreen(onScrollEndReached: () -> Unit) {
        composeRule.setContent {
            OceTheme {
                Surface {
                    SummaryScreen(
                        state = tallState(),
                        onRetry = {},
                        onToggleSaveWord = {},
                        onToggleSaveExpression = {},
                        onToggleSaveBookmark = {},
                        onScrollEndReached = onScrollEndReached,
                    )
                }
            }
        }
    }

    private fun scrollToBottom() {
        composeRule.onNodeWithTag(SUMMARY_SCROLL_CONTENT_TAG).performTouchInput {
            repeat(6) { swipeUp(durationMillis = 1) }
        }
    }

    private fun advanceTo(targetTimeMs: Long) {
        composeRule.mainClock.advanceTimeBy(
            targetTimeMs - composeRule.mainClock.currentTime,
            ignoreFrameDuration = true,
        )
    }

    private fun tallState(expressionCount: Int = 20) =
        SummaryState(
            totalScore = 85,
            highlight = HighlightTurn("커피 주세요", "Could I get a latte?", 92),
            bookmarks =
                List(8) { index ->
                    BookmarkCard("fixture-sentence-$index", "I got lost on the way.", "오는 길에 길을 잃었어요.")
                },
            accrual = AccrualStrip(streakDays = 1, xp = 20),
            bundle =
                SectionBundle.Sectioned(
                    expression =
                        SummarySectionState.Ready(
                            List(expressionCount) {
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
                    coaching = SummarySectionState.Ready(Coaching("끝까지 대화를 이어갔어요.", "과거형을 한 번 써볼까요?")),
                ),
        )
}

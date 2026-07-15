package com.jjundev.oneclickeng.feature.session.summary

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w411dp-h2000dp", application = Application::class)
class SummaryInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun moreButtonExpandsOnceAndNeverExposesCollapseAction() {
        setContent(expressionCount = 4)

        composeRule.onNodeWithText("표현 4").assertDoesNotExist()
        composeRule.onNodeWithText("더보기").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("표현 4").assertExists()
        composeRule.onNodeWithText("더보기").assertDoesNotExist()
        composeRule.onNodeWithText("접기").assertDoesNotExist()
    }

    @Test
    fun expressionAndWordSectionsExpandIndependently() {
        setContent(expressionCount = 4, wordCount = 4)

        composeRule.onAllNodesWithText("더보기").assertCountEquals(2)
        composeRule.onAllNodesWithText("더보기").onFirst().performClick()
        composeRule.onNodeWithText("표현 4").assertExists()
        composeRule.onNodeWithText("단어 4").assertDoesNotExist()
        composeRule.onAllNodesWithText("더보기").assertCountEquals(1)
        composeRule.onAllNodesWithText("더보기").onFirst().performClick()
        composeRule.onNodeWithText("단어 4").assertExists()
        composeRule.onNodeWithText("표현 4").assertExists()
        composeRule.onNodeWithText("더보기").assertDoesNotExist()
    }

    @Test
    fun bookmarkSentenceToggleKeepsCardVisibleAndCanBeReenabled() {
        var unsavedBookmarkIds by mutableStateOf(emptySet<String>())
        composeRule.setContent {
            OceTheme {
                Surface {
                    SummaryScreen(
                        state = state(0, 0, unsavedBookmarkIds),
                        onRetry = {},
                        onToggleSaveWord = {},
                        onToggleSaveExpression = {},
                        onToggleSaveBookmark = { cardId ->
                            unsavedBookmarkIds =
                                if (cardId in unsavedBookmarkIds) unsavedBookmarkIds - cardId
                                else unsavedBookmarkIds + cardId
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("저장 해제").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("I got lost.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("저장").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("저장 해제").assertIsDisplayed()
    }

    private fun setContent(
        expressionCount: Int = 0,
        wordCount: Int = 0,
        bookmarkToggle: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            OceTheme {
                Surface {
                    SummaryScreen(
                        state = state(expressionCount, wordCount, emptySet()),
                        onRetry = {},
                        onToggleSaveWord = {},
                        onToggleSaveExpression = {},
                        onToggleSaveBookmark = bookmarkToggle,
                    )
                }
            }
        }
    }

    private fun state(
        expressionCount: Int,
        wordCount: Int,
        unsavedBookmarkIds: Set<String>,
    ) =
        SummaryState(
            totalScore = null,
            highlight = null,
            bookmarks =
                listOf(
                    BookmarkCard(
                        cardId = "s1__SENTENCE__0__2",
                        english = "I got lost.",
                        korean = "길을 잃었어요.",
                    ),
                ),
            accrual = AccrualStrip(streakDays = 0, xp = 0),
            bundle =
                SectionBundle.Sectioned(
                    expression =
                        SummarySectionState.Ready(
                            List(expressionCount) { index ->
                                ExpressionCard(
                                    type = ExpressionType.Natural,
                                    koreanPrompt = "표현 ${index + 1}",
                                    before = "",
                                    after = "표현 ${index + 1}",
                                    explanation = "",
                                )
                            },
                        ),
                    word =
                        SummarySectionState.Ready(
                            List(wordCount) { index ->
                                WordCard(
                                    en = "단어 ${index + 1}",
                                    ko = "뜻 ${index + 1}",
                                    partOfSpeech = "noun",
                                    level = "A1",
                                    exampleEn = "Example ${index + 1}",
                                    exampleKo = "예문 ${index + 1}",
                                )
                            },
                        ),
                    coaching = SummarySectionState.Ready(Coaching(positive = "", toImprove = "")),
                ),
            unsavedBookmarkIds = unsavedBookmarkIds,
        )
}

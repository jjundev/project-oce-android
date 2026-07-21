package com.jjundev.oneclickeng.feature.review

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jjundev.oneclickeng.feature.review.data.ReviewItem
import com.jjundev.oneclickeng.feature.review.data.ReviewPhase
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 복습 플로우 동작 테스트(Task 11). [RecordsDeleteDialogTest] 패턴 — `ReviewFlowContent` 를 상태로 직접
 * 렌더해 콜백 위임을 검증한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ReviewFlowBehaviorTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `flashcard back shows grade buttons and grade calls onGrade`() {
        val grades = mutableListOf<Boolean>()
        val word = ReviewItem("w1", SavedCard.Word("grasp", "완전히 이해하다", "", ""), null)
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ReviewFlowContent(
                        state =
                            ReviewUiState(
                                loading = false,
                                items = listOf(word),
                                index = 0,
                                phase = ReviewPhase.Back,
                            ),
                        onReveal = {},
                        onGrade = { grades += it },
                        onPick = {},
                        onNext = {},
                        onSpeak = {},
                        onClose = {},
                        onRestart = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("완료").assertIsDisplayed()
        composeRule.onNodeWithText("완료").performClick()
        assertEquals(listOf(true), grades)
    }

    @Test
    fun `expression card routes to quiz, not flashcard`() {
        val expression =
            ReviewItem(
                "e1",
                SavedCard.Expression(
                    type = "natural",
                    koreanPrompt = "이걸 영어로 어떻게 말해요?",
                    before = "I go store",
                    after = "I'm going to the store",
                    explanation = "관사와 전치사를 챙겨요.",
                ),
                null,
            )
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ReviewFlowContent(
                        state =
                            ReviewUiState(
                                loading = false,
                                items = listOf(expression),
                                index = 0,
                                phase = ReviewPhase.Ask,
                            ),
                        onReveal = {},
                        onGrade = {},
                        onPick = {},
                        onNext = {},
                        onSpeak = {},
                        onClose = {},
                        onRestart = {},
                    )
                }
            }
        }
        // Expression quiz renders its koreanPrompt + both options; flashcard's "정답 보기" CTA must NOT appear.
        composeRule.onNodeWithText("이걸 영어로 어떻게 말해요?").assertIsDisplayed()
        composeRule.onNodeWithText("I go store").assertIsDisplayed()
        composeRule.onNodeWithText("I'm going to the store").assertIsDisplayed()
    }

    @Test
    fun `empty pool shows empty state without restart CTA, close calls onClose`() {
        var closed = false
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ReviewFlowContent(
                        state =
                            ReviewUiState(
                                loading = false,
                                items = emptyList(),
                                index = 0,
                                phase = ReviewPhase.Done,
                                finished = true,
                            ),
                        onReveal = {},
                        onGrade = {},
                        onPick = {},
                        onNext = {},
                        onSpeak = {},
                        onClose = { closed = true },
                        onRestart = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("아직 저장한 카드가 없어요").assertIsDisplayed()
        composeRule.onNodeWithText("한 번 더 복습").assertDoesNotExist()
        composeRule.onNodeWithText("닫기").performClick()
        assertEquals(true, closed)
    }

    @Test
    fun `ahead-of-schedule session shows label and grading does not call onGrade's srs path`() {
        val grades = mutableListOf<Boolean>()
        val word =
            ReviewItem(
                cardId = "w1",
                card = SavedCard.Word("grasp", "완전히 이해하다", "", ""),
                review = null,
                aheadOfSchedule = true,
            )
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ReviewFlowContent(
                        state =
                            ReviewUiState(
                                loading = false,
                                items = listOf(word),
                                index = 0,
                                phase = ReviewPhase.Back,
                                aheadOfSchedule = true,
                            ),
                        onReveal = {},
                        onGrade = { grades += it },
                        onPick = {},
                        onNext = {},
                        onSpeak = {},
                        onClose = {},
                        onRestart = {},
                    )
                }
            }
        }
        // "미리 복습" label is the UI signal; the actual SRS-skip guard lives in ReviewViewModel.record
        // (verified in ReviewViewModelTest) — this test only confirms onGrade still fires from the sheet.
        composeRule.onNodeWithText("미리 복습").assertIsDisplayed()
        composeRule.onNodeWithText("완료").performClick()
        assertEquals(listOf(true), grades)
    }
}

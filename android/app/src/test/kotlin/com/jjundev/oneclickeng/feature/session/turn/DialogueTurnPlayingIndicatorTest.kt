package com.jjundev.oneclickeng.feature.session.turn

import android.app.Application
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [DialogueTurnContent] 가 `playingOpponentText`/`playingLearnerOrdinal` 을 받아 **일치하는 말풍선 하나만**
 * 재생 중 표시(`재생 중지` 라벨)로 렌더하는지 검증한다. 두 상대역 말풍선이 같은 화면에 있어도 텍스트가 일치하는
 * 쪽만 켜진다(식별 기준 = 영문 텍스트/학습자 순번, Task 3 의 [PlayingIndicatorState] 와 동일 키).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class DialogueTurnPlayingIndicatorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val messages =
        listOf(
            DialogueMessage.Opponent("First line"),
            DialogueMessage.Learner("My answer"),
            DialogueMessage.Opponent("Second line"),
        )

    @Test
    fun `only the opponent bubble matching playingOpponentText shows the stop affordance`() {
        composeRule.setContent {
            OceTheme {
                Surface {
                    DialogueTurnContent(
                        messages = messages,
                        turnPhase = TurnPhase.LearnerTurn,
                        sessionPhase = SessionPhase.InTurn,
                        currentTask = null,
                        listState = rememberLazyListState(),
                        onSubmitStub = {},
                        onViewSummary = {},
                        playingOpponentText = "Second line",
                    )
                }
            }
        }

        composeRule.onAllNodesWithContentDescription("재생 중지").assertCountEquals(1)
    }

    @Test
    fun `the learner bubble matching playingLearnerOrdinal shows the stop affordance`() {
        composeRule.setContent {
            OceTheme {
                Surface {
                    DialogueTurnContent(
                        messages = messages,
                        turnPhase = TurnPhase.LearnerTurn,
                        sessionPhase = SessionPhase.InTurn,
                        currentTask = null,
                        listState = rememberLazyListState(),
                        onSubmitStub = {},
                        onViewSummary = {},
                        learnerClipIndices = setOf(0),
                        playingLearnerOrdinal = 0,
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("재생 중지").assertExists()
    }

    @Test
    fun `no bubble shows the stop affordance when nothing is playing`() {
        composeRule.setContent {
            OceTheme {
                Surface {
                    DialogueTurnContent(
                        messages = messages,
                        turnPhase = TurnPhase.LearnerTurn,
                        sessionPhase = SessionPhase.InTurn,
                        currentTask = null,
                        listState = rememberLazyListState(),
                        onSubmitStub = {},
                        onViewSummary = {},
                    )
                }
            }
        }

        composeRule.onAllNodesWithContentDescription("재생 중지").assertCountEquals(0)
    }
}

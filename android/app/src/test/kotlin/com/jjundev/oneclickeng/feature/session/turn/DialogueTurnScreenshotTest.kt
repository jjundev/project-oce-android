package com.jjundev.oneclickeng.feature.session.turn

import android.app.Application
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 대화 턴 스크린샷 캡처(프로토타입 `session`·`session_wrong` 대조). [DialogueTurnContent] 를 위상별 고정 상태로 렌더한다.
 * opponent = 상대 발화 턴, learner = 사용자 발화(마이크 입력) 턴.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class DialogueTurnScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val opponentMessages =
        listOf(DialogueMessage.Opponent("Hi! What can I get for you?"))

    @Test
    fun session_opponent_light() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DialogueTurnContent(
                        messages = opponentMessages,
                        turnPhase = TurnPhase.OpponentTurn,
                        sessionPhase = SessionPhase.InTurn,
                        currentTask = null,
                        listState = rememberLazyListState(),
                        onSubmitStub = {},
                        onViewSummary = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/session_opponent_light.png")
    }

    @Test
    fun session_learner_light() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DialogueTurnContent(
                        messages = opponentMessages,
                        turnPhase = TurnPhase.LearnerTurn,
                        sessionPhase = SessionPhase.InTurn,
                        currentTask = ScaffoldTask("라떼 한 잔을 주문해보세요"),
                        listState = rememberLazyListState(),
                        onSubmitStub = {},
                        onViewSummary = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/session_learner_light.png")
    }
}

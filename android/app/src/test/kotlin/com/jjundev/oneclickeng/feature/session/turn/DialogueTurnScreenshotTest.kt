package com.jjundev.oneclickeng.feature.session.turn

import android.app.Application
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.ui.audio.MicState
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 대화 턴 스크린샷 캡처(프로토타입 `session` 대조). [DialogueTurnContent] 를 위상별 고정 상태로 렌더한다.
 * opponent = 상대 발화 턴, learner = 사용자 발화(마이크 입력) 턴. 학습자 턴은 실 [MicDock] 을 Ready 상태로
 * 주입해 프로토타입 마이크-우선 입력 독과 대조한다(스텁 도크 아님).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class DialogueTurnScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val opponentMessages =
        listOf(DialogueMessage.Opponent("Hi! What can I get for you?", "안녕하세요! 무엇을 드릴까요?"))

    // 40바 음성형 파형(녹음 상태 시각 검증용) — 중앙이 높고 양끝이 낮은 발화 엔벨로프.
    private val sampleWaveform =
        FloatArray(40) { i ->
            val env = 1f - kotlin.math.abs(i - 20) / 20f
            (0.28f + 0.62f * env * (0.55f + 0.45f * kotlin.math.abs(kotlin.math.sin(i * 1.7f)))).coerceIn(0.12f, 1f)
        }

    private val header =
        DialogueHeaderState(
            topicEmoji = "☕",
            title = "카페에서 주문하기",
            levelLabel = "easy(A2) · 5턴 균일",
            totalTurns = 5,
            completedTurns = 0,
        )

    @Test
    fun session_opponent_light() {
        captureOpponent(name = "session_opponent_light", dark = false)
    }

    @Test
    fun session_opponent_dark() = captureOpponent(name = "session_opponent_dark", dark = true)

    @Test
    fun session_opponent_translated_light() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                // 형제 opponent 캡처(capture 헬퍼)와 동일한 배경색으로 맞춰 육안 대조 시 tint 차이를 없앤다.
                Surface(color = MaterialTheme.colorScheme.background) {
                    DialogueTurnContent(
                        messages = opponentMessages,
                        turnPhase = TurnPhase.OpponentTurn,
                        sessionPhase = SessionPhase.InTurn,
                        currentTask = null,
                        listState = rememberLazyListState(),
                        onSubmitStub = {},
                        onViewSummary = {},
                        header = header,
                    )
                }
            }
        }
        composeRule.onNodeWithText("해석 보기").performClick()
        composeRule.onRoot().captureRoboImage(
            "build/outputs/roborazzi/session_opponent_translated_light.png",
        )
    }

    @Test
    fun session_learner_light() {
        captureLearner(name = "session_learner_light", dark = false, textMode = false)
    }

    @Test
    fun session_learner_dark() = captureLearner(name = "session_learner_dark", dark = true, textMode = false)

    @Test
    fun session_textinput_light() {
        captureLearner(name = "session_textinput_light", dark = false, textMode = true)
    }

    @Test
    fun session_skeleton_light() {
        capture(name = "session_skeleton_light", dark = false) {
            DialogueTurnContent(
                messages = emptyList(),
                turnPhase = TurnPhase.OpponentTurn,
                sessionPhase = SessionPhase.InTurn,
                currentTask = null,
                listState = rememberLazyListState(),
                onSubmitStub = {},
                onViewSummary = {},
                header = header,
                opponentTyping = true,
            )
        }
    }

    @Test
    fun session_recording_light() {
        captureRecording(name = "session_recording_light", dark = false)
    }

    @Test
    fun session_recording_dark() = captureRecording(name = "session_recording_dark", dark = true)

    private fun captureOpponent(name: String, dark: Boolean) {
        capture(name, dark) {
            DialogueTurnContent(
                messages = opponentMessages,
                turnPhase = TurnPhase.OpponentTurn,
                sessionPhase = SessionPhase.InTurn,
                currentTask = null,
                listState = rememberLazyListState(),
                onSubmitStub = {},
                onViewSummary = {},
                header = header,
            )
        }
    }

    private fun captureLearner(name: String, dark: Boolean, textMode: Boolean) {
        capture(name, dark) {
            DialogueTurnContent(
                messages = opponentMessages,
                turnPhase = TurnPhase.LearnerTurn,
                sessionPhase = SessionPhase.InTurn,
                currentTask = ScaffoldTask("라떼 한 잔을 주문해보세요"),
                listState = rememberLazyListState(),
                onSubmitStub = {},
                onViewSummary = {},
                header = header,
                dock = { task ->
                    MicDock(
                        task = task,
                        micState = MicState.Ready,
                        waveform = MutableStateFlow(FloatArray(0)),
                        textMode = textMode,
                        textValue = if (textMode) "Can I get a latte" else "",
                        retryHint = null,
                        permanentlyDenied = false,
                        reduceMotion = true,
                        onMicTap = {},
                        onAdvance = {},
                        onCancelSpeaking = {},
                        onToggleTextMode = {},
                        onTextChange = {},
                        onSubmitText = {},
                    )
                },
            )
        }
    }

    private fun captureRecording(name: String, dark: Boolean) {
        capture(name, dark) {
            DialogueTurnContent(
                messages = opponentMessages,
                turnPhase = TurnPhase.LearnerTurn,
                sessionPhase = SessionPhase.InTurn,
                currentTask = ScaffoldTask("라떼 한 잔을 주문해보세요"),
                listState = rememberLazyListState(),
                onSubmitStub = {},
                onViewSummary = {},
                header = header,
                dock = { task ->
                    MicDock(
                        task = task,
                        micState = MicState.Recording,
                        waveform = MutableStateFlow(sampleWaveform),
                        textMode = false,
                        textValue = "",
                        retryHint = null,
                        permanentlyDenied = false,
                        reduceMotion = true,
                        onMicTap = {},
                        onAdvance = {},
                        onCancelSpeaking = {},
                        onToggleTextMode = {},
                        onTextChange = {},
                        onSubmitText = {},
                    )
                },
            )
        }
    }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        composeRule.setContent {
            OceTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    content()
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }
}

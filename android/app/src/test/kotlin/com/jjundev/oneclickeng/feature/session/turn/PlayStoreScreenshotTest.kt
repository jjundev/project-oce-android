package com.jjundev.oneclickeng.feature.session.turn

import android.app.Application
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
import kotlin.math.abs
import kotlin.math.sin

/**
 * Google Play 업로드용 9:16 말하기 화면 캡처.
 *
 * 기존 Pixel 5 visual test(1080×2340)는 실제 기기 정합용이다. 이 테스트는 Play Console이 받는
 * 9:16 프레임(1080×1920)을 직접 렌더해, 사후 크롭으로 마이크 독을 잃지 않도록 한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-480dpi", application = Application::class)
class PlayStoreScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun guidedSpeakingRecording() {
        val waveform =
            FloatArray(40) { index ->
                val envelope = 1f - abs(index - 20) / 20f
                (0.28f + 0.62f * envelope * (0.55f + 0.45f * abs(sin(index * 1.7f))))
                    .coerceIn(0.12f, 1f)
            }

        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DialogueTurnContent(
                        messages =
                            listOf(
                                DialogueMessage.Opponent(
                                    english = "Hi! What can I get for you?",
                                    korean = "안녕하세요! 무엇을 드릴까요?",
                                ),
                            ),
                        turnPhase = TurnPhase.LearnerTurn,
                        sessionPhase = SessionPhase.InTurn,
                        currentTask = ScaffoldTask("라떼 한 잔을 주문해보세요"),
                        listState = rememberLazyListState(),
                        onSubmitStub = {},
                        onViewSummary = {},
                        header =
                            DialogueHeaderState(
                                topicEmoji = "☕",
                                title = "카페에서 주문하기",
                                levelLabel = "easy(A2) · 5턴 균일",
                                totalTurns = 5,
                                completedTurns = 0,
                            ),
                        dock = { task ->
                            MicDock(
                                task = task,
                                micState = MicState.Recording,
                                waveform = MutableStateFlow(waveform),
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
        }

        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/play_guided_speaking.png")
    }
}

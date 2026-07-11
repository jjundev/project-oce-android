package com.jjundev.oneclickeng.feature.session.dialogue

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.ui.component.previewWaitQuizItems
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 한도 게이트 스크린샷 캡처(프로토타입 `limit` [C] 대조). 생성 화면의 [DialogueGenState.QuotaBlocked] 종단 상태를 렌더한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class DialogueGeneratingScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun limit_light() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DialogueGeneratingScreen(
                        state = DialogueGenState.QuotaBlocked(remaining = 0),
                        quizItems = previewWaitQuizItems(),
                        onStartConversation = {},
                        onRetry = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/limit_light.png")
    }

    /** 생성 중 + 게이트(1s) 통과 후 대기 퀴즈(프로토 "기다리는 동안 가볍게" 카드) 표면. */
    @Test
    fun generating_quiz_light() {
        captureAfterGate(DialogueGenState.Generating, "generating_quiz_light")
    }

    /** 준비 완료(첫 상대턴 수신) — 퀴즈 + "대화 시작하기" CTA(프로토 준비 배너+CTA) 표면. */
    @Test
    fun generating_ready_light() {
        captureAfterGate(
            DialogueGenState.Ready(sessionId = "s", remaining = 2, meta = null, turns = emptyList()),
            "generating_ready_light",
        )
    }

    /**
     * 1s 지연 게이트를 테스트 클록으로 넘긴 뒤 캡처(게이트 전엔 중립 로딩만 렌더).
     *
     * 대기 퀴즈 링은 [DialogueGenState.Generating]에서 rememberInfiniteTransition으로 회전한다. 무한 전이가
     * 살아있으면 autoAdvance=true + waitForIdle()은 절대 idle에 도달하지 못해 행(hang)한다. 그래서
     * autoAdvance=false를 유지한 채 advanceTimeBy로 고정 프레임(게이트 통과 + 결정적 회전 위상)까지 진행한
     * 뒤 그대로 캡처한다(waitForIdle 미호출). Ready 골든은 loading=false라 무한 전이가 없어 동일 경로로 안전.
     */
    private fun captureAfterGate(
        state: DialogueGenState,
        name: String,
    ) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DialogueGeneratingScreen(
                        state = state,
                        quizItems = previewWaitQuizItems(),
                        onStartConversation = {},
                        onRetry = {},
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(GATE_ADVANCE_MS)
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        const val GATE_ADVANCE_MS = 1_200L
    }
}

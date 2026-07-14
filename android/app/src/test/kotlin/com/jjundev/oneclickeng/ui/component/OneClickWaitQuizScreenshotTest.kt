package com.jjundev.oneclickeng.ui.component

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
 * C20 WaitQuiz 리빌(정답/오답) 표면. 프로토 "기다리는 동안 가볍게" 카드 answered 상태 정합 확인용.
 * 커밋 골든 없음 — 프로토 대조는 `-Proborazzi.record` 후 육안(docs/adr/0006). reduceMotion=true·loading=false 라
 * 무한 링 전이가 없어 클릭→캡처가 행(hang) 없이 안전하다(OneClickWaitQuiz KDoc @param loading 주의).
 *
 * previewWaitQuizItems() 1번 문항: optionA "I have a plan." = 정답(correctIndex 0), optionB "I have plan." = 오답.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class OneClickWaitQuizScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reveal_correct_light() = captureReveal("I have a plan.", "quiz_reveal_correct_light", dark = false)

    @Test
    fun reveal_correct_dark() = captureReveal("I have a plan.", "quiz_reveal_correct_dark", dark = true)

    @Test
    fun reveal_wrong_light() = captureReveal("I have plan.", "quiz_reveal_wrong_light", dark = false)

    @Test
    fun reveal_wrong_dark() = captureReveal("I have plan.", "quiz_reveal_wrong_dark", dark = true)

    /** 오답을 눌러도 비처벌 리빌 카피와 "다음" 어포던스가 뜬다(리빌 구조 회귀 가드). */
    @Test
    fun wrong_answer_reveals_non_punitive_copy_and_next() {
        composeRule.setContent {
            OceTheme {
                OneClickWaitQuiz(items = previewWaitQuizItems(), loading = false, reduceMotion = true)
            }
        }
        composeRule.onNodeWithText("I have plan.").performClick()
        composeRule.onNodeWithText("괜찮아요. \"a plan\" 처럼 관사를 붙여요.").assertIsDisplayed()
        composeRule.onNodeWithText("다음").assertIsDisplayed()
    }

    private fun captureReveal(optionText: String, name: String, dark: Boolean) {
        composeRule.setContent {
            OceTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    OneClickWaitQuiz(items = previewWaitQuizItems(), loading = false, reduceMotion = true)
                }
            }
        }
        composeRule.onNodeWithText(optionText).performClick()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }
}

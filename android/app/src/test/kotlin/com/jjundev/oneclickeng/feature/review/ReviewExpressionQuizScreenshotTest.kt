package com.jjundev.oneclickeng.feature.review

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [ReviewExpressionQuiz] 렌더 대조 — ask(미선택) / reveal-correct(정답 선택, 초록 체크) /
 * reveal-wrong(오답 선택, 빨강 X + 정답 초록 체크 + 설명) 3상태.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class ReviewExpressionQuizScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private val card =
        SavedCard.Expression(
            type = "natural",
            koreanPrompt = "이 문제에 대해 어떻게 생각하세요?",
            before = "How do you think about this problem?",
            after = "What are your thoughts on this?",
            explanation = "'What are your thoughts on ~'가 더 자연스러운 표현이에요.",
        )

    private fun render(
        name: String,
        dark: Boolean,
        revealed: Boolean,
        pick: Int?,
    ) {
        composeRule.setContent {
            OceTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ReviewExpressionQuiz(
                        card = card,
                        counter = "7 / 12",
                        revealed = revealed,
                        pick = pick,
                        onPick = {},
                        onNext = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun ask_light() = render("review_quiz_ask_light", dark = false, revealed = false, pick = null)

    @Test
    fun reveal_correct_light() = render("review_quiz_reveal_correct_light", dark = false, revealed = true, pick = 1)

    @Test fun reveal_wrong_dark() = render("review_quiz_reveal_wrong_dark", dark = true, revealed = true, pick = 0)
}

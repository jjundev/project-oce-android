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
 * 복습 플립 카드 스크린샷(Task 9). 앞면(한국어+정답보기)/뒷면(영어+예문+다시/완료) 3장을 캡처한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class ReviewFlashcardScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private val word = SavedCard.Word("grasp", "완전히 이해하다", "I finally grasped it.", "드디어 이해했다.")

    private fun render(
        name: String,
        dark: Boolean,
        revealed: Boolean,
    ) {
        composeRule.setContent {
            OceTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ReviewFlashcard(card = word, revealed = revealed, onReveal = {}, onGrade = {}, onSpeak = {})
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun front_light() = render("review_flashcard_front_light", dark = false, revealed = false)

    @Test fun back_light() = render("review_flashcard_back_light", dark = false, revealed = true)

    @Test fun back_dark() = render("review_flashcard_back_dark", dark = true, revealed = true)
}

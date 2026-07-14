package com.jjundev.oneclickeng.feature.review

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.feature.review.data.ReviewItem
import com.jjundev.oneclickeng.feature.review.data.ReviewPhase
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class ReviewFlowScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private val items = List(6) { ReviewItem("s$it", SavedCard.Sentence("s$it", "문장$it"), null) }

    @Test
    fun summary_light() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ReviewFlowContent(
                        state = ReviewUiState(
                            loading = false,
                            items = items,
                            index = 6,
                            phase = ReviewPhase.Done,
                            done = 5,
                            again = 1,
                            finished = true,
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
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/review_summary_light.png")
    }
}

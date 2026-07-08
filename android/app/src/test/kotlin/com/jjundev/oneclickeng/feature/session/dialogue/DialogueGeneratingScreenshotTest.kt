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
}

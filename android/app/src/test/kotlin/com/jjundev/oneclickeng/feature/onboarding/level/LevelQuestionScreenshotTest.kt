package com.jjundev.oneclickeng.feature.onboarding.level

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 온보딩 레벨 문항 스크린샷 캡처(프로토타입 대조 파일럿). [LevelQuestionContent] 를 VM 없이 강제 렌더한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class LevelQuestionScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(
        name: String,
        dark: Boolean,
    ) {
        composeRule.setContent {
            OceTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    LevelQuestionContent(onLevelSelected = {}, onReauthTapped = {}, reduceMotion = true)
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun onboarding_level_light() = capture(name = "onboarding_level_light", dark = false)

    @Test
    fun onboarding_level_dark() = capture(name = "onboarding_level_dark", dark = true)

    @Test
    fun existing_account_text_invokes_reauth_callback() {
        var taps = 0
        composeRule.setContent {
            OceTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    LevelQuestionContent(
                        onLevelSelected = {},
                        onReauthTapped = { taps++ },
                        reduceMotion = true,
                    )
                }
            }
        }

        composeRule.onNodeWithText("Google로 로그인").assertDoesNotExist()
        composeRule.onNodeWithText("이미 계정이 있나요?").performClick()

        assertEquals(1, taps)
    }
}

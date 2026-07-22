package com.jjundev.oneclickeng.feature.onboarding.google

import android.app.Application
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.ui.component.primitive.OceSheetDefaults
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 재인증 시트 버튼 군 스크린샷 캡처. [GoogleReauthActions] 를 VM 없이 강제 렌더한다
 * ([com.jjundev.oneclickeng.feature.onboarding.level.LevelQuestionScreenshotTest] 와 동일 패턴).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class GoogleReauthActionsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(
        name: String,
        dark: Boolean,
    ) {
        composeRule.setContent {
            OceTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    GoogleReauthActions(
                        linking = false,
                        primaryLabel = "Google로 로그인",
                        onPrimary = {},
                        onCancel = {},
                        modifier = Modifier.padding(OceSheetDefaults.contentPadding),
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun google_reauth_actions_light() = capture(name = "google_reauth_actions_light", dark = false)

    @Test
    fun google_reauth_actions_dark() = capture(name = "google_reauth_actions_dark", dark = true)
}

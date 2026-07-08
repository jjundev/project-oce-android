package com.jjundev.oneclickeng.feature.home.settings

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
 * 세션 설정(레벨·턴수) 스크린샷 캡처. 프로토타입은 이 설정을 홈 hero 의 "설정 변경" 인라인으로 노출하므로
 * 픽셀 1:1 대상이 아니라 IA/컴포넌트 대조용이다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class SessionSettingsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun session_settings_light() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SessionSettingsScreen(
                        defaultLevel = "normal",
                        onStart = { _, _ -> },
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/session_settings_light.png")
    }
}

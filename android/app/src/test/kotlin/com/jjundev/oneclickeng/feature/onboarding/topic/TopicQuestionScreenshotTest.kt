package com.jjundev.oneclickeng.feature.onboarding.topic

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
 * 온보딩 상황 문항 스크린샷 캡처(프로토타입 `topic_onboarding` 대조). [TopicQuestionContent] 를 VM 없이 렌더한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class TopicQuestionScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onboarding_topic_light() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    TopicQuestionContent(onTopicSelected = {}, onBack = {}, reduceMotion = true)
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/onboarding_topic_light.png")
    }
}

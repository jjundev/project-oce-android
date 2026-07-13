package com.jjundev.oneclickeng.feature.onboarding.topic

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.ui.theme.OceTheme
import com.jjundev.oneclickeng.ui.foundation.OceIcon
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

    private fun capture(name: String, dark: Boolean) {
        composeRule.setContent {
            OceTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    TopicQuestionContent(
                        topics = screenshotTopics,
                        onTopicSelected = {},
                        onBack = {},
                        reduceMotion = true,
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun onboarding_topic_light() = capture(name = "onboarding_topic_light", dark = false)

    @Test
    fun onboarding_topic_dark() = capture(name = "onboarding_topic_dark", dark = true)
}

private val screenshotTopics =
    listOf(
        OnboardingTopic("cafe-order", "카페에서 주문하기", "ordering at a café", OceIcon.LocalCafe, "☕"),
        OnboardingTopic("weather-smalltalk", "날씨로 스몰토크", "weather small talk", OceIcon.PartlyCloudyDay, "🌤️"),
        OnboardingTopic("hotel-checkin", "호텔 체크인", "checking in", OceIcon.Hotel, "🏨"),
    )

package com.jjundev.oneclickeng.feature.home.topic

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 홈 위 바텀시트에서도 일상 주제 여러 개를 스크롤 전부터 비교할 수 있는지 검증한다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class TopicSelectVisibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `daily group shows four choices before scrolling`() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        TopicSelectSheetContent(
                            topics = dailyTopics,
                            onTopicChosen = { _, _ -> },
                            onDismiss = {},
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(TOPIC_SHEET_HEIGHT_FRACTION)
                                    .padding(OceTheme.spacing.xxl),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("레스토랑 주문·예약").assertIsDisplayed()
    }
}

private val dailyTopics =
    listOf(
        topic("cafe-order", "카페에서 주문하기", OceIcon.LocalCafe),
        topic("weather-smalltalk", "날씨로 스몰토크", OceIcon.PartlyCloudyDay),
        topic("hobby-intro", "취미·자기소개", OceIcon.Interests),
        topic("restaurant", "레스토랑 주문·예약", OceIcon.Restaurant),
        topic("greeting-neighbor", "이웃에게 인사하기", OceIcon.WavingHand),
        topic("weekend-plans", "주말 계획 묻기", OceIcon.Event),
    )

private fun topic(id: String, titleKo: String, icon: OceIcon) =
    Topic(
        id = id,
        emoji = "",
        titleKo = titleKo,
        group = TopicGroup.Daily,
        beginnerFriendly = false,
        promptSeed = titleKo,
        icon = icon,
    )

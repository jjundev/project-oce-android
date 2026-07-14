package com.jjundev.oneclickeng.feature.home

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = Application::class)
class HomeSettingsSliderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `expanded panel shows selected level label and description`() {
        compose.setContent {
            OceTheme {
                SettingsInline(level = "normal", length = 10, onSetLevel = {}, onSetLength = {})
            }
        }
        compose.onNodeWithText("설정 변경").performClick()
        compose.onNodeWithText("중간").assertIsDisplayed()
        compose.onNodeWithText("일상 대화를 자연스럽게 이어가요").assertIsDisplayed()
        compose.onNodeWithText("10턴").assertIsDisplayed()
    }
}

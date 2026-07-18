package com.jjundev.oneclickeng.ui.component

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OneClickUpdateGateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tapping_the_primary_action_invokes_onUpdateNow() {
        var tapped = false
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                OneClickUpdateGate(onUpdateNow = { tapped = true })
            }
        }

        composeRule.onNodeWithText("지금 업데이트").performClick()

        assertTrue(tapped)
    }

    @Test
    fun renders_the_forced_update_title() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                OneClickUpdateGate(onUpdateNow = {})
            }
        }

        composeRule.onNodeWithText("새 버전이 나왔어요").assertExists()
    }
}

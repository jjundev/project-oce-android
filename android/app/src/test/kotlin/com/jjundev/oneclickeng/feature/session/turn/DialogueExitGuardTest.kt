package com.jjundev.oneclickeng.feature.session.turn

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-560dpi", application = Application::class)
class DialogueExitGuardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun pressBack() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun systemBack_showsAbortSheet() {
        composeRule.setContent {
            OceTheme {
                DialogueExitGuard(onExit = {}) { _ -> Text("대화내용") }
            }
        }
        composeRule.onNodeWithText("대화를 그만할까요?").assertDoesNotExist()

        pressBack()

        composeRule.onNodeWithText("대화를 그만할까요?").assertIsDisplayed()
    }

    @Test
    fun leave_invokesOnExit_andClosesSheet() {
        var exited = 0
        composeRule.setContent {
            OceTheme {
                DialogueExitGuard(onExit = { exited++ }) { _ -> Text("대화내용") }
            }
        }
        pressBack()

        composeRule.onNodeWithText("그만하기").performClick()
        composeRule.waitForIdle()

        assertEquals(1, exited)
        composeRule.onNodeWithText("대화를 그만할까요?").assertDoesNotExist()
    }

    @Test
    fun stay_closesSheet_withoutExit() {
        var exited = 0
        composeRule.setContent {
            OceTheme {
                DialogueExitGuard(onExit = { exited++ }) { _ -> Text("대화내용") }
            }
        }
        pressBack()

        composeRule.onNodeWithText("계속 이어하기").performClick()
        composeRule.waitForIdle()

        assertEquals(0, exited)
        composeRule.onNodeWithText("대화를 그만할까요?").assertDoesNotExist()
    }

    @Test
    fun headerBackRequest_showsAbortSheet() {
        composeRule.setContent {
            OceTheme {
                DialogueExitGuard(onExit = {}) { onBackRequest ->
                    Button(onClick = onBackRequest) { Text("헤더뒤로") }
                }
            }
        }
        composeRule.onNodeWithText("헤더뒤로").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("대화를 그만할까요?").assertIsDisplayed()
    }
}

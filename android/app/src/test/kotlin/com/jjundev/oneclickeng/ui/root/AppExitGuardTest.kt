package com.jjundev.oneclickeng.ui.root

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
class AppExitGuardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun pressBack() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun backOnHome_showsExitSheet() {
        composeRule.setContent {
            OceTheme {
                AppExitGuard(enabled = true, onExitApp = {}) { Text("탭내용") }
            }
        }
        composeRule.onNodeWithText("앱을 종료할까요?").assertDoesNotExist()

        pressBack()

        composeRule.onNodeWithText("앱을 종료할까요?").assertIsDisplayed()
    }

    @Test
    fun exit_invokesOnExitApp_andClosesSheet() {
        var exited = 0
        composeRule.setContent {
            OceTheme {
                AppExitGuard(enabled = true, onExitApp = { exited++ }) { Text("탭내용") }
            }
        }
        pressBack()

        composeRule.onNodeWithText("종료").performClick()
        composeRule.waitForIdle()

        assertEquals(1, exited)
        composeRule.onNodeWithText("앱을 종료할까요?").assertDoesNotExist()
    }

    @Test
    fun stay_closesSheet_withoutExit() {
        var exited = 0
        composeRule.setContent {
            OceTheme {
                AppExitGuard(enabled = true, onExitApp = { exited++ }) { Text("탭내용") }
            }
        }
        pressBack()

        composeRule.onNodeWithText("계속 사용하기").performClick()
        composeRule.waitForIdle()

        assertEquals(0, exited)
        composeRule.onNodeWithText("앱을 종료할까요?").assertDoesNotExist()
    }

    @Test
    fun backWhenDisabled_passesThroughAndDoesNotShowSheet() {
        // enabled=false(홈 탭 아님) → 가드는 뒤로가기를 소비하지 않고, 바깥의 폴백 핸들러로 넘어간다.
        // 폴백보다 나중에 컴포즈되는 가드가 우선순위가 높으므로(disabled 라 스킵), 폴백이 발화한다.
        var fallback = 0
        composeRule.setContent {
            OceTheme {
                BackHandler(enabled = true) { fallback++ }
                AppExitGuard(enabled = false, onExitApp = {}) { Text("탭내용") }
            }
        }
        pressBack()

        assertEquals(1, fallback)
        composeRule.onNodeWithText("앱을 종료할까요?").assertDoesNotExist()
    }
}

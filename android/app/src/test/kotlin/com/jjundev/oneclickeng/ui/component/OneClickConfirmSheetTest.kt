package com.jjundev.oneclickeng.ui.component

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-560dpi", application = Application::class)
class OneClickConfirmSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersTitleMessageLabels_andStayInvokesOnStayOnly() {
        var stayCount = 0
        var leaveCount = 0
        composeRule.setContent {
            OceTheme {
                OneClickConfirmSheetContent(
                    title = "대화를 그만할까요?",
                    message = "지금 나가면 이 대화는 저장되지 않아요. 상황은 다시 고를 수 있어요.",
                    stayLabel = "계속 이어하기",
                    leaveLabel = "그만하기",
                    onStay = { stayCount++ },
                    onLeave = { leaveCount++ },
                )
            }
        }

        composeRule.onNodeWithText("대화를 그만할까요?").assertIsDisplayed()
        composeRule
            .onNodeWithText("지금 나가면 이 대화는 저장되지 않아요. 상황은 다시 고를 수 있어요.")
            .assertIsDisplayed()

        composeRule.onNodeWithText("계속 이어하기").performClick()
        assertEquals(1, stayCount)
        assertEquals(0, leaveCount)
    }

    @Test
    fun leaveLabel_invokesOnLeave() {
        var leaveCount = 0
        composeRule.setContent {
            OceTheme {
                OneClickConfirmSheetContent(
                    title = "앱을 종료할까요?",
                    message = null,
                    stayLabel = "계속 사용하기",
                    leaveLabel = "종료",
                    onStay = {},
                    onLeave = { leaveCount++ },
                )
            }
        }

        composeRule.onNodeWithText("종료").performClick()
        assertEquals(1, leaveCount)
    }
}

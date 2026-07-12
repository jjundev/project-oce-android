package com.jjundev.oneclickeng.feature.onboarding.google

import android.app.Application
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jjundev.oneclickeng.ui.component.SheetPrimaryHeight
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class GoogleSaveActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun GoogleSaveActions_usesSharedButtonHeight_andShowsGoogleLogo() {
        composeRule.setContent {
            OceTheme {
                GoogleSaveActions(
                    linking = false,
                    primaryLabel = "Google로 진도 저장",
                    onPrimary = {},
                    onOneMore = {},
                    onSkip = {},
                )
            }
        }

        composeRule.onNodeWithText("Google로 진도 저장").assertHeightIsEqualTo(SheetPrimaryHeight)
        composeRule.onNodeWithText("한 번 더 하기").assertHeightIsEqualTo(SheetPrimaryHeight)
        composeRule.onNodeWithTag(GOOGLE_SAVE_LOGO_TAG, useUnmergedTree = true).assertIsDisplayed()
    }
}

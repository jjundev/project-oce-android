package com.jjundev.oneclickeng.ui.root

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val MAIN_TABS_CONTENT_TAG = "main_tabs_content"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class MainTabsOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tab_content_extends_behind_floating_navigation() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                val navController = rememberNavController()
                MainTabsOverlay(navController = navController, isOnline = true) { contentModifier ->
                    Box(
                        modifier =
                            contentModifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .testTag(MAIN_TABS_CONTENT_TAG),
                    )
                }
            }
        }

        composeRule.onNodeWithTag(MAIN_TABS_CONTENT_TAG).assertIsDisplayed()
        val contentBottom =
            composeRule.onNodeWithTag(MAIN_TABS_CONTENT_TAG).getUnclippedBoundsInRoot().bottom
        val navLabelTop =
            composeRule
                .onNodeWithText("학습", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
                .top

        assertTrue(
            "Floating navigation must overlap full-height tab content; " +
                "contentBottom=${contentBottom}, navLabelTop=${navLabelTop}",
            navLabelTop < contentBottom,
        )
    }
}

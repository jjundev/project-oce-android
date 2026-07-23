package com.jjundev.oneclickeng.feature.session.dialogue

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.jjundev.oneclickeng.ui.component.previewWaitQuizItems
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class DialogueReadyBottomSheetSpacingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ready_sheet_uses_shared_horizontal_and_bottom_spacing() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            OceTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DialogueGeneratingScreen(
                        state =
                            DialogueGenState.Ready(
                                sessionId = "s",
                                remaining = 2,
                                meta = null,
                                turns = emptyList(),
                            ),
                        quizItems = previewWaitQuizItems(),
                        onStartConversation = {},
                        onRetry = {},
                    )
                }
            }
        }

        // Pass the 1000ms quiz gate and allow the ready-sheet enter transition to settle.
        composeRule.mainClock.advanceTimeBy(2_000L)

        val sheet = composeRule.onNodeWithTag(READY_BOTTOM_SHEET_TEST_TAG).getUnclippedBoundsInRoot()
        val cta = composeRule.onNodeWithTag(READY_BOTTOM_SHEET_CTA_TEST_TAG).getUnclippedBoundsInRoot()

        assertEquals(24f, (cta.left - sheet.left).value, 0.5f)
        assertEquals(24f, (sheet.right - cta.right).value, 0.5f)
        assertTrue(
            "CTA must keep at least the shared 24dp bottom content padding, " +
                "including any navigation-bar inset",
            (sheet.bottom - cta.bottom).value >= 24f,
        )
    }
}

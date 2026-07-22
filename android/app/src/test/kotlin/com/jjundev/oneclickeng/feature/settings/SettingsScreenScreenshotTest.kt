package com.jjundev.oneclickeng.feature.settings

import android.app.Application
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 설정 탭 스크린샷 캡처(프로토타입 대조 파일럿). [SettingsContent] 를 VM/Activity 없이 고정 상태로 렌더한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class SettingsScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsMessageIsConsumedWhenSnackbarEffectIsCancelledAndDoesNotReplayAfterReentry() {
        val snackbarStarted = CompletableDeferred<Unit>()
        val releaseSnackbar = CompletableDeferred<Unit>()
        var visible by mutableStateOf(true)
        var message by mutableStateOf<String?>("카드를 삭제했어요.")
        var snackbarCalls = 0

        composeRule.setContent {
            if (visible) {
                SettingsMessageEffect(
                    messageText = message,
                    showSnackbar = {
                        snackbarCalls += 1
                        snackbarStarted.complete(Unit)
                        releaseSnackbar.await()
                        SnackbarResult.Dismissed
                    },
                    consumeMessage = { message = null },
                )
            }
        }

        composeRule.waitUntil { snackbarStarted.isCompleted }
        visible = false
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertNull(message)
            assertEquals(1, snackbarCalls)
        }

        visible = true
        composeRule.waitForIdle()
        assertEquals(1, snackbarCalls)

        releaseSnackbar.complete(Unit)
    }

    @Test
    fun `settings list returns to top when reentry key changes`() {
        var scrollResetKey by mutableIntStateOf(0)
        lateinit var listState: LazyListState

        composeRule.setContent {
            listState = rememberLazyListState()
            OceTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsContent(
                        state = SettingsUiState(loading = false, nickname = "준영", isGuest = true),
                        versionLabel = "1.0.0 (1)",
                        notificationsBlocked = false,
                        onNicknameChange = {},
                        onQualityChange = {},
                        onSpeedChange = {},
                        onMuteChange = {},
                        onReminderToggle = {},
                        onReminderTimeClick = {},
                        onOpenNotificationSettings = {},
                        onPurgeClick = {},
                        onResetClick = {},
                        onSummarySaveDefaultChange = {},
                        onGoogleSave = {},
                        onLogoutClick = {},
                        onDeleteClick = {},
                        onRetryMerge = {},
                        onPrivacy = {},
                        onTerms = {},
                        listState = listState,
                        scrollResetKey = scrollResetKey,
                        reduceMotion = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SETTINGS_SCROLL_CONTENT_TAG).performTouchInput {
            swipeUp(startY = 900f, endY = 100f, durationMillis = 300)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(
                listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0,
            )
            scrollResetKey += 1
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(0, listState.firstVisibleItemIndex)
            assertEquals(0, listState.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun `settings list stays at top when account section moves after account state loads`() {
        var state by mutableStateOf(SettingsUiState(loading = false, nickname = "준영", isGuest = true))
        lateinit var listState: LazyListState

        composeRule.setContent {
            listState = rememberLazyListState()
            OceTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsContent(
                        state = state,
                        versionLabel = "1.0.0 (1)",
                        notificationsBlocked = false,
                        onNicknameChange = {},
                        onQualityChange = {},
                        onSpeedChange = {},
                        onMuteChange = {},
                        onReminderToggle = {},
                        onReminderTimeClick = {},
                        onOpenNotificationSettings = {},
                        onPurgeClick = {},
                        onResetClick = {},
                        onSummarySaveDefaultChange = {},
                        onGoogleSave = {},
                        onLogoutClick = {},
                        onDeleteClick = {},
                        onRetryMerge = {},
                        onPrivacy = {},
                        onTerms = {},
                        listState = listState,
                        reduceMotion = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        state = state.copy(isGuest = false)
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(0, listState.firstVisibleItemIndex)
            assertEquals(0, listState.firstVisibleItemScrollOffset)
        }
    }

    private fun renderSettings(
        state: SettingsUiState,
        dark: Boolean,
        blocked: Boolean,
        name: String,
        isGoogleSaveLoading: Boolean = false,
    ) {
        composeRule.setContent {
            OceTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsContent(
                        state = state,
                        versionLabel = "1.0.0 (1)",
                        notificationsBlocked = blocked,
                        onNicknameChange = {},
                        onQualityChange = {},
                        onSpeedChange = {},
                        onMuteChange = {},
                        onReminderToggle = {},
                        onReminderTimeClick = {},
                        onOpenNotificationSettings = {},
                        onPurgeClick = {},
                        onResetClick = {},
                        onSummarySaveDefaultChange = {},
                        onGoogleSave = {},
                        onLogoutClick = {},
                        onDeleteClick = {},
                        onRetryMerge = {},
                        onPrivacy = {},
                        onTerms = {},
                        reduceMotion = true,
                        isGoogleSaveLoading = isGoogleSaveLoading,
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun settings_light_guest() =
        renderSettings(
            SettingsUiState(loading = false, nickname = "준영", isGuest = true),
            dark = false,
            blocked = false,
            name = "settings_light_guest",
        )

    @Test fun settings_light_guest_google_saving() =
        renderSettings(
            SettingsUiState(loading = false, nickname = "준영", isGuest = true),
            dark = false,
            blocked = false,
            name = "settings_light_guest_google_saving",
            isGoogleSaveLoading = true,
        )

    @Test fun settings_light_member() =
        renderSettings(
            SettingsUiState(loading = false, nickname = "준영", isGuest = false, reminderEnabled = true),
            dark = false,
            blocked = false,
            name = "settings_light_member",
        )

    @Test fun settings_dark_guest() =
        renderSettings(
            SettingsUiState(loading = false, nickname = "준영", isGuest = true),
            dark = true,
            blocked = false,
            name = "settings_dark_guest",
        )

    @Test fun settings_dark_member() =
        renderSettings(
            SettingsUiState(loading = false, nickname = "준영", isGuest = false, reminderEnabled = true),
            dark = true,
            blocked = false,
            name = "settings_dark_member",
        )

    @Test
    fun settings_confirm_delete_dark() {
        composeRule.setContent {
            OceTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    DialogButtonRow(
                        modifier = Modifier.padding(24.dp),
                        confirmLabel = "삭제",
                        confirmColor = MaterialTheme.colorScheme.error,
                        confirmEnabled = true,
                        onConfirm = {},
                        onDismiss = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/settings_confirm_delete_dark.png")
    }

    @Test fun settings_notif_blocked() =
        renderSettings(
            SettingsUiState(loading = false, nickname = "준영", isGuest = true, reminderEnabled = false),
            dark = false,
            blocked = true,
            name = "settings_notif_blocked",
        )

    @Test fun settings_member_blocked() =
        renderSettings(
            SettingsUiState(loading = false, nickname = "준영", isGuest = false, reminderEnabled = true),
            dark = false,
            blocked = true,
            name = "settings_member_blocked",
        )

    @Test fun reminder_time_sheet() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    ReminderTimeSheetContent(initialHour = 20, initialMinute = 0, onConfirm = { _, _ -> })
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/reminder_time_sheet.png")
    }

    @Test
    fun accountSection_showsLoadingSpinner_andDisablesClick_whenGoogleSaveLoading() {
        var clicked = 0
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsContent(
                        state = SettingsUiState(loading = false, nickname = "준영", isGuest = true),
                        versionLabel = "1.0.0 (1)",
                        notificationsBlocked = false,
                        onNicknameChange = {},
                        onQualityChange = {},
                        onSpeedChange = {},
                        onMuteChange = {},
                        onReminderToggle = {},
                        onReminderTimeClick = {},
                        onOpenNotificationSettings = {},
                        onPurgeClick = {},
                        onResetClick = {},
                        onSummarySaveDefaultChange = {},
                        onGoogleSave = { clicked += 1 },
                        onLogoutClick = {},
                        onDeleteClick = {},
                        onRetryMerge = {},
                        onPrivacy = {},
                        onTerms = {},
                        isGoogleSaveLoading = true,
                        reduceMotion = true,
                    )
                }
            }
        }

        composeRule.onNodeWithTag(GOOGLE_SAVE_LOADING_TAG, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Google로 진도 저장").assertHasNoClickAction()
        assertEquals(0, clicked)
    }

    @Test
    fun accountSection_showsChevron_andAllowsClick_whenGoogleSaveNotLoading() {
        var clicked = 0
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsContent(
                        state = SettingsUiState(loading = false, nickname = "준영", isGuest = true),
                        versionLabel = "1.0.0 (1)",
                        notificationsBlocked = false,
                        onNicknameChange = {},
                        onQualityChange = {},
                        onSpeedChange = {},
                        onMuteChange = {},
                        onReminderToggle = {},
                        onReminderTimeClick = {},
                        onOpenNotificationSettings = {},
                        onPurgeClick = {},
                        onResetClick = {},
                        onSummarySaveDefaultChange = {},
                        onGoogleSave = { clicked += 1 },
                        onLogoutClick = {},
                        onDeleteClick = {},
                        onRetryMerge = {},
                        onPrivacy = {},
                        onTerms = {},
                        isGoogleSaveLoading = false,
                        reduceMotion = true,
                    )
                }
            }
        }

        composeRule.onNodeWithTag(GOOGLE_SAVE_LOADING_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Google로 진도 저장").assertHasClickAction()
        composeRule.onNodeWithText("Google로 진도 저장").performClick()
        composeRule.waitForIdle()
        assertEquals(1, clicked)
    }

    @Test
    fun infoSection_showsPolicyRowsWithExternalLinks_andInvokesEachCallback() {
        var privacyCalls = 0
        var termsCalls = 0

        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsContent(
                        state = SettingsUiState(loading = false, nickname = "준영", isGuest = true),
                        versionLabel = "1.0.0 (1)",
                        notificationsBlocked = false,
                        onNicknameChange = {},
                        onQualityChange = {},
                        onSpeedChange = {},
                        onMuteChange = {},
                        onReminderToggle = {},
                        onReminderTimeClick = {},
                        onOpenNotificationSettings = {},
                        onPurgeClick = {},
                        onResetClick = {},
                        onSummarySaveDefaultChange = {},
                        onGoogleSave = {},
                        onLogoutClick = {},
                        onDeleteClick = {},
                        onRetryMerge = {},
                        onPrivacy = { privacyCalls += 1 },
                        onTerms = { termsCalls += 1 },
                        reduceMotion = true,
                    )
                }
            }
        }

        repeat(8) {
            composeRule.onRoot().performTouchInput { swipeUp() }
            composeRule.waitForIdle()
        }
        val privacy = composeRule.onNodeWithText("개인정보처리방침")
        val terms = composeRule.onNodeWithText("이용약관")
        privacy.assertIsDisplayed().assertHasClickAction()
        terms.assertIsDisplayed().assertHasClickAction()
        composeRule.onAllNodesWithText("계정 삭제").assertCountEquals(0)
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/settings_info_policy_links.png")

        privacy.performClick()
        terms.performClick()
        composeRule.waitForIdle()
        assertEquals(1, privacyCalls)
        assertEquals(1, termsCalls)
    }
}

package com.jjundev.oneclickeng.feature.settings

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.ui.theme.OceTheme
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

    private fun renderSettings(state: SettingsUiState, dark: Boolean, blocked: Boolean, name: String) {
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
                        onGoogleSave = {},
                        onLogoutClick = {},
                        onDeleteClick = {},
                        onRetryMerge = {},
                        onPrivacy = {},
                        onTerms = {},
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
}

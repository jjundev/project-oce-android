package com.jjundev.oneclickeng.ui.foundation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.jjundev.oneclickeng.feature.settings.SettingsContent
import com.jjundev.oneclickeng.feature.settings.SettingsUiState
import com.jjundev.oneclickeng.ui.navigation.OceTab
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 탭 전환이 설정 [LazyListState]를 복원하지 않고 항상 처음부터 시작하는지 검증한다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class OceBottomNavScrollStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `returning to settings starts the list at the top`() {
        lateinit var settingsListState: LazyListState

        composeRule.setContent {
            val navController = rememberNavController()
            OceTheme {
                Box(Modifier.fillMaxSize()) {
                    NavHost(navController = navController, startDestination = OceTab.Home.route) {
                        composable(OceTab.Home.route) { EmptyTab() }
                        composable(OceTab.Records.route) { EmptyTab() }
                        composable(OceTab.Settings.route) {
                            settingsListState = rememberLazyListState()
                            SettingsContent(
                                state = SettingsUiState(loading = false, nickname = "준영", isGuest = false),
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
                                listState = settingsListState,
                                reduceMotion = true,
                            )
                        }
                    }
                    OceBottomNav(navController = navController, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }

        composeRule.onNodeWithText("설정").performClick()
        composeRule.runOnIdle { runBlocking { settingsListState.scrollToItem(5) } }
        composeRule.runOnIdle { assertTrue(settingsListState.firstVisibleItemIndex > 0) }

        composeRule.onNodeWithText("학습").performClick()
        composeRule.onNodeWithText("설정").performClick()

        composeRule.runOnIdle {
            assertEquals(0, settingsListState.firstVisibleItemIndex)
            assertEquals(0, settingsListState.firstVisibleItemScrollOffset)
        }
    }

    @Composable
    private fun EmptyTab() {
        Box(Modifier.fillMaxSize())
    }
}

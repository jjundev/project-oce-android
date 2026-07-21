package com.jjundev.oneclickeng.feature.home

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HomeScrollResetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `home list returns to top when tab reentry key changes`() {
        var scrollResetKey by mutableIntStateOf(0)
        lateinit var listState: LazyListState

        composeRule.setContent {
            listState = rememberLazyListState()
            OceTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    HomeContent(
                        state =
                            HomeUiState(
                                studyMinutes = 5,
                                streak = 2,
                                level = "easy",
                                selectedSituation = SelectedSituation("cafe", "카페에서 주문하기", "카페에서 주문하기"),
                                situations =
                                    (1..12).map { index ->
                                        HomeSituation("situation-$index", "상황 $index")
                                    },
                            ),
                        onStartLearning = {},
                        onResumeContinue = {},
                        onResumeStartNew = {},
                        onViewRecords = {},
                        onOfflineBlocked = {},
                        listState = listState,
                        scrollResetKey = scrollResetKey,
                        reduceMotion = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(HOME_SCROLL_CONTENT_TAG).performTouchInput {
            swipeUp()
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
}

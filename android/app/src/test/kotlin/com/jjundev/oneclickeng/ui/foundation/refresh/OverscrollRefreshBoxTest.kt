package com.jjundev.oneclickeng.ui.foundation.refresh

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// 저장소 관례(TopicSelectDragBlockTest 등): Compose 터치 제스처 테스트는 Robolectric 위에서 돈다.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OverscrollRefreshBoxTest {
    @get:Rule val rule = createComposeRule()

    @Test fun swipeDownAtTop_invokesOnRefresh() {
        var refreshCount = 0
        rule.setContent {
            OverscrollRefreshBox(
                isRefreshing = false,
                onRefresh = { refreshCount++ },
                modifier = Modifier.fillMaxSize().testTag("box"),
            ) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items((1..3).toList()) { Text("row $it", Modifier.height(80.dp)) }
                }
            }
        }
        rule.onNodeWithTag("box").performTouchInput {
            swipeDown(startY = 100f, endY = 900f, durationMillis = 300)
        }
        rule.waitForIdle()
        assertTrue("onRefresh fired at least once", refreshCount >= 1)
    }
}

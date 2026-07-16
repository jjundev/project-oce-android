package com.jjundev.oneclickeng.ui.foundation.refresh

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 당겨서-새로고침(오버스크롤) "당겨진 + 인디케이터 노출" held 상태 스크린샷 캡처.
 *
 * 결정적 프레임을 얻기 위해 실제 제스처(nestedScroll)나 [OverscrollRefreshBox] 의 release
 * 시퀀스(스냅→물결→폭죽→스프링 복귀, [OverscrollRefreshState.releaseRequest] 로 구동)를 전혀
 * 건드리지 않는다 — `releaseRequest` 는 `onPreFling` 을 통해서만 증가하므로, 제스처를 보내지 않는 한
 * `isRefreshing` 값과 무관하게 release LaunchedEffect 는 절대 실행되지 않는다. 대신 [state.offset] 을
 * [OverscrollDefaults.HoldOffset] 픽셀로 직접 snap 해 "당겨진 채 정지" 프레임을 만든다. `busy` 도 false로
 * 유지되므로(제스처를 보내지 않았으므로) 인디케이터의 무한 스핀 트랜지션도 시작되지 않아 캡처가 안정적이다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class OverscrollRefreshScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun overscroll_heldState() {
        lateinit var state: OverscrollRefreshState
        lateinit var density: Density
        composeRule.setContent {
            OceTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    state = rememberOverscrollRefreshState()
                    density = LocalDensity.current
                    OverscrollRefreshBox(
                        isRefreshing = false,
                        onRefresh = {},
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        SampleList()
                    }
                }
            }
        }

        composeRule.runOnIdle {
            runBlocking {
                state.offset.snapTo(with(density) { OverscrollDefaults.HoldOffset.toPx() })
            }
        }
        composeRule.waitForIdle()

        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/overscroll_held_state.png")
    }

    @Composable
    private fun SampleList() {
        val items = remember {
            listOf(
                "카페에서 주문하기",
                "처음 만나 자기소개하기",
                "친구와 약속 잡기",
                "호텔 체크인하기",
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            items(items) { title ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(text = title)
                }
            }
        }
    }
}

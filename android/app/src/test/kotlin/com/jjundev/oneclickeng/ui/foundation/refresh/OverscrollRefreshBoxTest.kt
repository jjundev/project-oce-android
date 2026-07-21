package com.jjundev.oneclickeng.ui.foundation.refresh

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    // 회귀 테스트: 릴리스 시퀀스는 최소 표시 플로어를 지난 뒤에도 isRefreshing 이 true 인 동안
    // 스프링 복귀를 미뤄야 한다(기록 탭의 느린 Firebase 재조회를 흉내낸 시나리오).
    // 구버전 버그(스텝 C 에서 snapshotFlow 를 최소 표시 시간 이전에 구독)에서는 이 테스트가 실패한다:
    // 실제 앱처럼 onRefresh() 호출 시점에는 아직 isRefreshing 이 false 이고, 그 상태를 snapshotFlow 가
    // 즉시 방출해버려 filter{!it}.first() 가 곧장 통과하기 때문에 로딩 완료를 전혀 기다리지 않는다.
    @Test fun releaseHoldsUntilRefreshingClears_pastMinVisibleFloor() {
        val refreshing = mutableStateOf(false)
        lateinit var capturedState: OverscrollRefreshState
        rule.mainClock.autoAdvance = false

        rule.setContent {
            val state = rememberOverscrollRefreshState()
            capturedState = state
            OverscrollRefreshBox(
                isRefreshing = refreshing.value,
                // 실제 화면은 onRefresh() 가 비동기 로딩을 트리거할 뿐, 이 콜백 안에서 동기적으로
                // isRefreshing 을 뒤집지 않는다 — 아래에서 그 지연을 흉내낸다(버그 재현의 핵심 타이밍).
                onRefresh = {},
                modifier = Modifier.fillMaxSize().testTag("box"),
                state = state,
            ) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items((1..3).toList()) { Text("row $it", Modifier.height(80.dp)) }
                }
            }
        }
        rule.waitForIdle()

        // 스와이프 제스처는 클럭이 정지된 상태에서 불안정하므로, onPostScroll 로 임계값 이상까지
        // 당김을 만든 뒤 onPreFling 을 호출해 릴리스 시퀀스를 결정적으로 트리거한다.
        runBlocking {
            capturedState.nestedScrollConnection.onPostScroll(
                consumed = Offset.Zero,
                available = Offset(0f, 2000f),
                source = NestedScrollSource.UserInput,
            )
            capturedState.nestedScrollConnection.onPreFling(Velocity.Zero)
        }
        rule.waitForIdle()

        // (A) 스냅 애니메이션(140ms) 진행 — 아직 최소 표시 플로어 이전.
        rule.mainClock.advanceTimeBy(200L)
        rule.waitForIdle()

        // 이 시점에야 로딩 플래그가 true 로 뒤집힌다(비동기 새로고침 흉내).
        refreshing.value = true
        rule.waitForIdle()

        // 최소 표시 시간(450ms) + 물결(~808ms) 을 넉넉히 지나도록 진행한다. isRefreshing 은 여전히 true.
        rule.mainClock.advanceTimeBy(2500L)
        rule.waitForIdle()

        assertTrue("still busy while refresh is in flight past the floor", capturedState.busy)
        assertTrue("indicator still held, not sprung back", capturedState.offset.value > 0f)

        // 로딩 완료 신호 — 이제서야 스프링 복귀가 진행되어야 한다.
        refreshing.value = false
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2500L)
        rule.waitForIdle()

        assertTrue("cycle completes once refreshing clears", !capturedState.busy)
        assertEquals("offset springs back to rest", 0f, capturedState.offset.value, 1f)
    }

    // 회귀 테스트: Task 1 이후 드래그 중엔 dragOffsetPx 가 값을 들고 있고 offset.value 는 0으로
    // 남는다. OverscrollRefreshBox 가 여전히 state.offset.value 를 직접 읽으면 드래그 도중 콘텐츠가
    // 손가락을 따라오지 않는다(당김이 시각적으로 전혀 안 보임) — currentPullPx() 를 읽어야 한다.
    @Test fun dragWithoutRelease_indicatorFollowsDragOffsetPx() {
        lateinit var capturedState: OverscrollRefreshState
        rule.setContent {
            val state = rememberOverscrollRefreshState()
            capturedState = state
            OverscrollRefreshBox(
                isRefreshing = false,
                onRefresh = {},
                modifier = Modifier.fillMaxSize().testTag("box"),
                state = state,
            ) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items((1..3).toList()) { Text("row $it", Modifier.height(80.dp)) }
                }
            }
        }
        rule.waitForIdle()

        runBlocking {
            capturedState.nestedScrollConnection.onPostScroll(
                consumed = Offset.Zero,
                // below threshold — stays a pure drag, no release
                available = Offset(0f, 40f),
                source = NestedScrollSource.UserInput,
            )
        }
        rule.waitForIdle()

        assertTrue("dragOffsetPx reflects the live drag", capturedState.dragOffsetPx > 0f)
        assertEquals(
            "currentPullPx() must be read by the box while dragging (offset.value alone stays 0)",
            capturedState.dragOffsetPx,
            capturedState.currentPullPx(),
            0.5f,
        )
    }
}

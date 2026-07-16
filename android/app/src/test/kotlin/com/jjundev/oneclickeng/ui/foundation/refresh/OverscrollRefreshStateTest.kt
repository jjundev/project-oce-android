package com.jjundev.oneclickeng.ui.foundation.refresh

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OverscrollRefreshStateTest {

    private fun state() = OverscrollRefreshState(thresholdPx = 64f, maxPullPx = 180f)

    @Test fun dragDownAtTop_growsOffsetWithResistance() = runTest {
        val s = state()
        // 위에서 아래로 100px 드래그(available.y>0), onPostScroll 이 소비
        val consumed = s.nestedScrollConnection.onPostScroll(
            consumed = Offset.Zero,
            available = Offset(0f, 100f),
            source = NestedScrollSource.UserInput,
        )
        assertTrue("consumed the downward drag", consumed.y > 0f)
        assertTrue("offset grew but under raw drag (rubber band)", s.dragOffsetPx in 1f..100f)
    }

    @Test fun releaseBelowThreshold_doesNotRequestRefresh() = runTest {
        val s = state()
        s.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 40f), NestedScrollSource.UserInput)
        val before = s.releaseRequest
        s.nestedScrollConnection.onPreFling(Velocity.Zero)
        assertEquals("no refresh requested below threshold", before, s.releaseRequest)
        assertEquals("sprang back to rest", 0f, s.currentPullPx(), 0.5f)
    }

    @Test fun releasePastThreshold_requestsRefresh() = runTest {
        val s = state()
        s.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 400f), NestedScrollSource.UserInput)
        assertTrue("pulled past threshold", s.dragOffsetPx >= 64f)
        val before = s.releaseRequest
        s.nestedScrollConnection.onPreFling(Velocity.Zero)
        assertEquals("refresh requested once", before + 1, s.releaseRequest)
    }

    @Test fun dragUpWhilePulled_shrinksOffset() = runTest {
        val s = state()
        s.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 200f), NestedScrollSource.UserInput)
        val pulled = s.dragOffsetPx
        val consumed = s.nestedScrollConnection.onPreScroll(Offset(0f, -30f), NestedScrollSource.UserInput)
        assertTrue("consumed upward drag while pulled", consumed.y < 0f)
        assertTrue("offset shrank", s.dragOffsetPx < pulled)
    }

    // 회귀 테스트 — 실사용자 버그: 아래로 스크롤하다 빠르게 위로 튕기면(빠른 방향 반전) 인디케이터가
    // 열린 채로 멈춘다. 구버전은 onPostScroll/onPreScroll 이 offset(Animatable)을
    // scope.launch { offset.snapTo(...) } 로 "비동기" 반영했고, onPreFling 이 그 사이 아직 반영되지
    // 않은(stale) offset.value 를 "동기" 로 읽어 오판했다. 이 테스트는 advanceUntilIdle() 없이 세
    // 호출을 연달아 실행해(실제 빠른 제스처처럼 코루틴 갭을 주지 않고) 그 레이스를 재현한다 — 매 호출
    // 뒤 advanceUntilIdle() 을 넣던 구버전 테스트 스타일로는 큐가 항상 비워져 재현되지 않는다.
    @Test fun fastDownThenUpReversal_leavesNoStuckOffset() = runTest {
        val s = state()
        s.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 300f), NestedScrollSource.UserInput)
        s.nestedScrollConnection.onPreScroll(Offset(0f, -3000f), NestedScrollSource.UserInput)
        assertEquals("reversal fully closes the pull before release", 0f, s.dragOffsetPx, 0.5f)

        val before = s.releaseRequest
        val consumedFling = s.nestedScrollConnection.onPreFling(Velocity.Zero)
        assertEquals("no refresh requested — the pull was already closed", before, s.releaseRequest)
        assertEquals("nothing left for this connection to consume", Velocity.Zero, consumedFling)
        assertEquals("indicator not left open", 0f, s.currentPullPx(), 0.5f)
        assertFalse("not stuck busy", s.busy)
    }

    // 회귀 테스트: 임계값을 넘긴 채로 손을 떼면 릴리스 시퀀스가 정확히 당김 위치에서 이어받아야
    // 한다 — 구버전은 여기서 stale 값을 스냅해 콘텐츠가 튀거나(잘못된 시작점) 멈춘 것처럼 보였다.
    @Test fun releaseSequenceHandsOffExactlyFromDragPosition() = runTest {
        val s = state()
        s.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 500f), NestedScrollSource.UserInput)
        val dragAtRelease = s.dragOffsetPx
        s.nestedScrollConnection.onPreFling(Velocity.Zero)
        assertTrue("busy once past threshold", s.busy)
        assertEquals("no visual jump at hand-off", dragAtRelease, s.offset.value, 0.01f)
        assertEquals("drag bookkeeping cleared once the animatable owns the value", 0f, s.dragOffsetPx, 0.01f)
    }

    // 회귀 테스트 — 실기기(Galaxy S23, adb 터치 주입)로 재현·확인된 진짜 근본 원인. "리스트 안쪽에서
    // 빠르게 위로 튕기는" 제스처는: (1) fling 시작 시점엔 리스트가 아직 상단 경계에 안 닿아 있어
    // onPreFling 이 dragOffsetPx==0 인 채로 정상 통과(bail)하고, (2) 그 같은 fling 이 감속하며 상단
    // 경계에 부딪히면 nested-scroll 이 그 잔여 스크롤을 onPostScroll(source=SideEffect) 로 여러 프레임에
    // 걸쳐 흘려보내 dragOffsetPx 를 연다 — 이 구간엔 onPreFling 이 다시 호출되지 않는다. 이 시나리오의
    // 유일한 종료 이벤트는 onPostFling 이다. 실기기 로그(logcat, DEBUGfling 태그)로 정확히 이 순서를
    // 확인했다: onPreFling(dragOffsetPx=0, bail) -> onPostScroll(SideEffect) x100+ -> onPostFling
    // (dragOffsetPx=504.66, settling=false) — onPostFling 오버라이드가 없던 구버전은 여기서 아무 것도
    // 하지 않아 인디케이터가 열린 채로 영원히 고착됐다.
    @Test fun flingHitsBoundaryMidFlight_onPostFlingResolvesTheOpenedGap() = runTest {
        val s = state()

        // (1) fling 시작 — 리스트가 아직 안쪽이라 당김이 없다. onPreFling 은 정상적으로 통과(bail)한다.
        val preFlingConsumed = s.nestedScrollConnection.onPreFling(Velocity(0f, 19695f))
        assertEquals("nothing pulled yet, defer entirely to the scrollable", Velocity.Zero, preFlingConsumed)
        assertEquals("no release triggered from onPreFling alone", 0, s.releaseRequest)

        // (2) 그 같은 fling 이 상단 경계에 부딪혀 감속하며, 여러 프레임에 걸쳐 SideEffect 스크롤로
        // 틈을 연다(실기기 로그의 감쇠 패턴을 그대로 재현).
        var velocity = 200f
        repeat(30) {
            s.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, velocity), NestedScrollSource.SideEffect)
            velocity *= 0.85f
        }
        assertTrue("gap opened via SideEffect scroll, past threshold", s.dragOffsetPx >= 64f)
        assertEquals("onPostScroll alone never triggers release", 0, s.releaseRequest)

        // (3) fling 이 완전히 멈춘다 — 이게 이 시나리오의 유일한 종료 이벤트다.
        val before = s.releaseRequest
        val postFlingConsumed = s.nestedScrollConnection.onPostFling(Velocity.Zero, Velocity.Zero)
        assertEquals("release triggered from onPostFling, not left stuck open", before + 1, s.releaseRequest)
        assertEquals("indicator not left open", 0f, s.dragOffsetPx, 0.01f)
        assertTrue("busy once released", s.busy)
        assertEquals(Velocity.Zero, postFlingConsumed)
    }

    // 회귀 테스트: 위 시나리오에서 fling 이 경계에 살짝만 부딪혀 임계값 미달로 멈추면, onPostFling 이
    // 릴리스가 아니라 스냅백을 트리거해야 한다(onPreFling 의 else 분기와 대칭).
    @Test fun flingHitsBoundaryMidFlight_belowThreshold_snapsBackViaOnPostFling() = runTest {
        val s = state()
        s.nestedScrollConnection.onPreFling(Velocity(0f, 5000f))
        s.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 20f), NestedScrollSource.SideEffect)
        assertTrue("opened but below threshold", s.dragOffsetPx in 1f..63f)

        val before = s.releaseRequest
        s.nestedScrollConnection.onPostFling(Velocity.Zero, Velocity.Zero)
        assertEquals("no refresh requested below threshold", before, s.releaseRequest)
        assertEquals("sprang back to rest via onPostFling", 0f, s.currentPullPx(), 0.5f)
        assertFalse("not stuck busy", s.busy)
    }
}

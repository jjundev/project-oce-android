package com.jjundev.oneclickeng.ui.foundation.refresh

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OverscrollRefreshStateTest {

    private fun state(scope: TestScope) =
        OverscrollRefreshState(scope, thresholdPx = 64f, maxPullPx = 180f)

    @Test fun dragDownAtTop_growsOffsetWithResistance() = runTest {
        val s = state(this)
        // 위에서 아래로 100px 드래그(available.y>0), onPostScroll 이 소비
        val consumed = s.nestedScrollConnection.onPostScroll(
            consumed = Offset.Zero,
            available = Offset(0f, 100f),
            source = NestedScrollSource.UserInput,
        )
        advanceUntilIdle()
        assertTrue("consumed the downward drag", consumed.y > 0f)
        assertTrue("offset grew but under raw drag (rubber band)", s.offset.value in 1f..100f)
    }

    @Test fun releaseBelowThreshold_doesNotRequestRefresh() = runTest {
        val s = state(this)
        s.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 40f), NestedScrollSource.UserInput)
        advanceUntilIdle()
        val before = s.releaseRequest
        s.nestedScrollConnection.onPreFling(Velocity.Zero)
        advanceUntilIdle()
        assertEquals("no refresh requested below threshold", before, s.releaseRequest)
        assertEquals("sprang back to rest", 0f, s.offset.value, 0.5f)
    }

    @Test fun releasePastThreshold_requestsRefresh() = runTest {
        val s = state(this)
        s.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 400f), NestedScrollSource.UserInput)
        advanceUntilIdle()
        assertTrue("pulled past threshold", s.offset.value >= 64f)
        val before = s.releaseRequest
        s.nestedScrollConnection.onPreFling(Velocity.Zero)
        advanceUntilIdle()
        assertEquals("refresh requested once", before + 1, s.releaseRequest)
    }

    @Test fun dragUpWhilePulled_shrinksOffset() = runTest {
        val s = state(this)
        s.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 200f), NestedScrollSource.UserInput)
        advanceUntilIdle()
        val pulled = s.offset.value
        val consumed = s.nestedScrollConnection.onPreScroll(Offset(0f, -30f), NestedScrollSource.UserInput)
        advanceUntilIdle()
        assertTrue("consumed upward drag while pulled", consumed.y < 0f)
        assertTrue("offset shrank", s.offset.value < pulled)
    }
}

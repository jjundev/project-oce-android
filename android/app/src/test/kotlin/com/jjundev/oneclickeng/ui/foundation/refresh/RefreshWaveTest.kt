package com.jjundev.oneclickeng.ui.foundation.refresh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshWaveTest {
    @Test fun waveBob_keyframes() {
        assertEquals(0f, waveBob(0f), 0.001f)
        assertEquals(1f, waveBob(0.32f), 0.02f)
        assertEquals(-0.27f, waveBob(0.66f), 0.02f)
        assertEquals(0f, waveBob(1f), 0.001f)
    }

    @Test fun waveBob_clampsOutsideRange() {
        assertEquals(0f, waveBob(-0.5f), 0.001f)
        assertEquals(0f, waveBob(1.5f), 0.001f)
    }

    @Test fun translationY_idleClockIsZero() {
        val state = RefreshWaveState()
        assertEquals(0f, state.translationYPx(index = 0, amplitudePx = 11f), 0.001f)
    }

    @Test fun translationY_laterIndexIsDelayed() {
        val state = RefreshWaveState()
        // clock at the first item's peak time (0.32 * 520ms ≈ 166ms)
        state.clockMs = 0.32f * OverscrollDefaults.WAVE_DURATION_MS
        val first = state.translationYPx(index = 0, amplitudePx = 11f)
        val third = state.translationYPx(index = 2, amplitudePx = 11f)
        assertTrue("item 0 is near its peak", first > 9f)
        assertTrue("item 2 lags behind item 0", third < first)
    }
}

package com.jjundev.oneclickeng.ui.foundation.refresh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverscrollMathTest {
    @Test fun rubberBand_zeroOrNegative_isZero() {
        assertEquals(0f, rubberBand(0f, 180f), 0.001f)
        assertEquals(0f, rubberBand(-50f, 180f), 0.001f)
    }

    @Test fun rubberBand_isMonotonicAndBoundedByMax() {
        val max = 180f
        val a = rubberBand(100f, max)
        val b = rubberBand(400f, max)
        assertTrue("resistance grows with drag", b > a)
        assertTrue("never reaches max", b < max)
        assertTrue("large drag approaches max", b > max * 0.6f)
    }

    @Test fun inverseRubberBand_roundTrips() {
        val max = 180f
        val raw = 250f
        val offset = rubberBand(raw, max)
        assertEquals(raw, inverseRubberBand(offset, max), 0.5f)
    }
}

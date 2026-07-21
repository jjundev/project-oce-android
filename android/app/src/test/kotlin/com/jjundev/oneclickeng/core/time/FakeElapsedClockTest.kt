package com.jjundev.oneclickeng.core.time

import org.junit.Assert.assertEquals
import org.junit.Test

class FakeElapsedClockTest {
    @Test
    fun `nowMillis reflects the initial value and each advance`() {
        val clock = FakeElapsedClock(now = 100L)
        assertEquals(100L, clock.nowMillis())

        clock.advance(250L)

        assertEquals(350L, clock.nowMillis())
    }
}

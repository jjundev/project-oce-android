package com.jjundev.oneclickeng.ui.foundation

import org.junit.Assert.assertEquals
import org.junit.Test

/** 스태거 지연 순수 검증 — 스낵 시퀀스(20/80/140…680)와 캡. */
class ScreenEntranceTest {
    @Test
    fun delays_match_snappy_sequence() {
        assertEquals(20, staggerDelayMs(0))
        assertEquals(80, staggerDelayMs(1))
        assertEquals(140, staggerDelayMs(2))
        assertEquals(200, staggerDelayMs(3))
        assertEquals(680, staggerDelayMs(11))
    }

    @Test
    fun delay_is_capped_at_max_index() {
        assertEquals(staggerDelayMs(11), staggerDelayMs(50))
    }

    @Test
    fun negative_index_clamps_to_base() {
        assertEquals(20, staggerDelayMs(-3))
    }
}

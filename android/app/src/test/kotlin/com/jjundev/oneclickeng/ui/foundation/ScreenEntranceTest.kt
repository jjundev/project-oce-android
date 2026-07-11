package com.jjundev.oneclickeng.ui.foundation

import org.junit.Assert.assertEquals
import org.junit.Test

/** 스태거 지연 순수 검증 — 프로토 .oc-home-stagger nth-child 시퀀스(40/150/260…1250)와 캡. */
class ScreenEntranceTest {
    @Test
    fun delays_match_prototype_sequence() {
        assertEquals(40, staggerDelayMs(0))
        assertEquals(150, staggerDelayMs(1))
        assertEquals(260, staggerDelayMs(2))
        assertEquals(370, staggerDelayMs(3))
        assertEquals(1250, staggerDelayMs(11))
    }

    @Test
    fun delay_is_capped_at_max_index() {
        assertEquals(staggerDelayMs(11), staggerDelayMs(50))
    }

    @Test
    fun negative_index_clamps_to_base() {
        assertEquals(40, staggerDelayMs(-3))
    }
}

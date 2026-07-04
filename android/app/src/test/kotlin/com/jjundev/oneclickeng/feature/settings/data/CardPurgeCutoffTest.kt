package com.jjundev.oneclickeng.feature.settings.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [PurgeScope.cutoffMillis] 순수 로직(M3-09, FR-22): 30/90일 프리셋 컷오프 · 전체=컷오프 없음. */
class CardPurgeCutoffTest {
    private val now = 1_700_000_000_000L
    private val dayMillis = 24L * 60L * 60L * 1000L

    @Test
    fun `30-day preset cuts off exactly 30 days before now`() {
        assertEquals(now - 30L * dayMillis, PurgeScope.LAST_30_DAYS.cutoffMillis(now))
    }

    @Test
    fun `90-day preset cuts off exactly 90 days before now`() {
        assertEquals(now - 90L * dayMillis, PurgeScope.LAST_90_DAYS.cutoffMillis(now))
    }

    @Test
    fun `ALL scope has no cutoff`() {
        assertNull(PurgeScope.ALL.cutoffMillis(now))
    }
}

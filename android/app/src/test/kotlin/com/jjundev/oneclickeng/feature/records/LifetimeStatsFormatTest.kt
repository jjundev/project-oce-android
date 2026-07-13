package com.jjundev.oneclickeng.feature.records

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [formatStudyTime] 은 총 분을 "N시간 N분" 복합 표기로 만든다(시간 0이어도 "0시간" 유지 — 기존 렌더 동일).
 * 카운트업이 이 함수를 프레임마다 통과시켜 60분 경계에서 분→시간 롤오버가 자연히 나타난다.
 */
class LifetimeStatsFormatTest {
    @Test
    fun formats_total_minutes_as_hours_and_minutes() {
        assertEquals("2시간 15분", formatStudyTime(135))
        assertEquals("1시간 0분", formatStudyTime(60))
        assertEquals("0시간 59분", formatStudyTime(59))
        assertEquals("0시간 45분", formatStudyTime(45))
        assertEquals("0시간 0분", formatStudyTime(0))
    }
}

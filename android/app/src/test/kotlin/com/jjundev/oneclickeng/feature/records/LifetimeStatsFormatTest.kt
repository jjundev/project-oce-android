package com.jjundev.oneclickeng.feature.records

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [formatStudyTime] 은 총 분을 표기한다. 시간이 0이면 "N분"만 보이고(0시간 라벨 숨김), 시간이 1 이상이면
 * "N시간 N분" 복합 표기를 쓴다. 카운트업이 이 함수를 프레임마다 통과시켜 60분 경계에서 "59분"→"1시간 0분"
 * 롤오버가 자연히 나타난다.
 */
class LifetimeStatsFormatTest {
    @Test
    fun formats_total_minutes_as_hours_and_minutes() {
        assertEquals("2시간 15분", formatStudyTime(135))
        assertEquals("2시간 0분", formatStudyTime(120))
        assertEquals("1시간 0분", formatStudyTime(60))
        assertEquals("59분", formatStudyTime(59))
        assertEquals("45분", formatStudyTime(45))
        assertEquals("0분", formatStudyTime(0))
    }
}

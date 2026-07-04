package com.jjundev.oneclickeng.feature.gamification

import com.jjundev.oneclickeng.feature.gamification.GamificationTime.STUDYTIME_SESSION_CAP_SECONDS
import org.junit.Assert.assertEquals
import org.junit.Test

/** 순수 게임화 헬퍼 검증(M3-05) — DataStore/Firestore 무관, JVM 단독. */
class GamificationTimeTest {
    @Test
    fun `xp map mirrors the server constant`() {
        assertEquals(mapOf("easy" to 10, "normal" to 20, "hard" to 35), GamificationTime.XP_BY_DIFFICULTY)
    }

    @Test
    fun `elapsedStudySeconds clamps to zero and cap`() {
        // 5분 경과
        assertEquals(300L, GamificationTime.elapsedStudySeconds(startMillis = 1_000_000, nowMillis = 1_300_000))
        // 시작 null → 0
        assertEquals(0L, GamificationTime.elapsedStudySeconds(startMillis = null, nowMillis = 1_300_000))
        // 음수(시계 역행) → 0
        assertEquals(0L, GamificationTime.elapsedStudySeconds(startMillis = 2_000_000, nowMillis = 1_000_000))
        // 상한 초과(2시간) → 캡(1800s)
        val twoHoursLater = 2L * 60 * 60 * 1000
        assertEquals(
            STUDYTIME_SESSION_CAP_SECONDS,
            GamificationTime.elapsedStudySeconds(startMillis = 0, nowMillis = twoHoursLater),
        )
    }

    @Test
    fun `advanceStreak follows the server recurrence`() {
        fun advance(
            streak: Int,
            last: String?,
            day: String,
        ) = GamificationTime.advanceStreak(streak, last, day)

        assertEquals("최초 학습 → 1", 1, advance(0, null, "2026-07-04"))
        assertEquals("연속일 → +1", 6, advance(5, "2026-07-03", "2026-07-04"))
        assertEquals("1일 유예(gap 2) → 평탄", 5, advance(5, "2026-07-02", "2026-07-04"))
        assertEquals("2일+ 미스 → 1", 1, advance(5, "2026-07-01", "2026-07-04"))
        assertEquals("같은 날 재세션 → 불변", 5, advance(5, "2026-07-04", "2026-07-04"))
    }

    @Test
    fun `kstDayKey buckets to Asia Seoul calendar day`() {
        fun dayKeyOf(iso: String) = GamificationTime.kstDayKey(java.time.Instant.parse(iso).toEpochMilli())

        // 2026-07-03T20:00:00Z = 2026-07-04 05:00 KST
        assertEquals("2026-07-04", dayKeyOf("2026-07-03T20:00:00Z"))
        // 2026-07-03T14:00:00Z = 2026-07-03 23:00 KST
        assertEquals("2026-07-03", dayKeyOf("2026-07-03T14:00:00Z"))
    }

    @Test
    fun `studyTimeLabel renders whole minutes`() {
        assertEquals("오늘 0분", GamificationTime.studyTimeLabel(30))
        assertEquals("오늘 1분", GamificationTime.studyTimeLabel(90))
        assertEquals("오늘 10분", GamificationTime.studyTimeLabel(600))
    }
}

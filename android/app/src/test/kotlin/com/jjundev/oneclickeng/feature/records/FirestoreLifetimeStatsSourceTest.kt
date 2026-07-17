package com.jjundev.oneclickeng.feature.records

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [toLifetimeStats] pure-combine coverage (M3-05 실데이터 배선). The Firestore-touching
 * [FirestoreLifetimeStatsSource] wrapper itself has no unit test — this codebase has no mocking harness
 * for `FirebaseFirestore`/`DocumentSnapshot` (see `StudytimeStoreTest`/merge.ts precedent of testing pure
 * decision functions only); all the branching logic worth testing lives in this pure function instead.
 */
class FirestoreLifetimeStatsSourceTest {
    @Test
    fun `maps server xp and studyDays with local total seconds converted to minutes`() {
        val stats = toLifetimeStats(progressXp = 1240L, progressStudyDays = 12L, localTotalSeconds = 8100L)

        assertEquals(1240, stats.xp)
        assertEquals(12, stats.studyDays)
        assertEquals(135, stats.studyMinutes) // 8100s / 60 = 135m
    }

    @Test
    fun `absent progress fields default to zero`() {
        val stats = toLifetimeStats(progressXp = null, progressStudyDays = null, localTotalSeconds = 0L)

        assertEquals(0, stats.xp)
        assertEquals(0, stats.studyDays)
        assertEquals(0, stats.studyMinutes)
    }

    @Test
    fun `sub-minute local total truncates down to zero minutes`() {
        val stats = toLifetimeStats(progressXp = 5L, progressStudyDays = 1L, localTotalSeconds = 45L)

        assertEquals(0, stats.studyMinutes)
    }
}

package com.jjundev.oneclickeng.feature.gamification

import org.junit.Assert.assertEquals
import org.junit.Test

class GamificationTimeXpTest {
    @Test
    fun `client XP map mirrors the 5-tier server table`() {
        assertEquals(
            mapOf("starter" to 5, "easy" to 10, "normal" to 20, "hard" to 35, "expert" to 55),
            GamificationTime.XP_BY_DIFFICULTY,
        )
    }
}

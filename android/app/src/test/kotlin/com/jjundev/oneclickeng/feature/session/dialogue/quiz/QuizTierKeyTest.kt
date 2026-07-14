package com.jjundev.oneclickeng.feature.session.dialogue.quiz

import org.junit.Assert.assertEquals
import org.junit.Test

class QuizTierKeyTest {
    @Test
    fun `starter maps to easy and expert maps to hard`() {
        assertEquals("easy", mapTierKey("starter"))
        assertEquals("hard", mapTierKey("expert"))
    }

    @Test
    fun `known 3 tiers pass through and blank falls back to easy`() {
        assertEquals("easy", mapTierKey("easy"))
        assertEquals("normal", mapTierKey("normal"))
        assertEquals("hard", mapTierKey("hard"))
        assertEquals("easy", mapTierKey("  "))
        assertEquals("easy", mapTierKey(""))
    }
}

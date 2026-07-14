package com.jjundev.oneclickeng.core.session

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionLevelTest {
    @Test
    fun `entries are ordered easiest to hardest`() {
        assertEquals(
            listOf("starter", "easy", "normal", "hard", "expert"),
            SessionLevel.entries.map { it.token },
        )
    }

    @Test
    fun `fromToken resolves known tokens`() {
        assertEquals(SessionLevel.STARTER, SessionLevel.fromToken("starter"))
        assertEquals(SessionLevel.EXPERT, SessionLevel.fromToken("expert"))
        assertEquals(SessionLevel.NORMAL, SessionLevel.fromToken("normal"))
    }

    @Test
    fun `fromToken falls back to NORMAL for unknown or null`() {
        assertEquals(SessionLevel.NORMAL, SessionLevel.fromToken("A2"))
        assertEquals(SessionLevel.NORMAL, SessionLevel.fromToken(null))
        assertEquals(SessionLevel.NORMAL, SessionLevel.fromToken(""))
    }

    @Test
    fun `labels descriptions and xp match the ratified spec`() {
        assertEquals("중간", SessionLevel.NORMAL.labelKo)
        assertEquals("매우 어려움", SessionLevel.EXPERT.labelKo)
        assertEquals("단어와 짧은 문장부터 천천히 시작해요", SessionLevel.STARTER.descKo)
        assertEquals(5, SessionLevel.STARTER.xp)
        assertEquals(55, SessionLevel.EXPERT.xp)
    }
}

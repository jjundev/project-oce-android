package com.jjundev.oneclickeng.feature.session.saved

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** 결정적 cardId 파생(ADR-0001 · NFR-8). */
class SavedCardIdTest {
    @Test
    fun `summary id is deterministic for the same source tuple`() {
        val a = SavedCardId.forSummary("sess-1", CardType.WORD, 2)
        val b = SavedCardId.forSummary("sess-1", CardType.WORD, 2)
        assertEquals("sess-1__WORD__2", a)
        assertEquals(a, b)
    }

    @Test
    fun `summary id separates cardType and index`() {
        assertNotEquals(
            SavedCardId.forSummary("sess-1", CardType.WORD, 0),
            SavedCardId.forSummary("sess-1", CardType.EXPRESSION, 0),
        )
        assertNotEquals(
            SavedCardId.forSummary("sess-1", CardType.WORD, 0),
            SavedCardId.forSummary("sess-1", CardType.WORD, 1),
        )
    }

    @Test
    fun `sentence id includes turnIndex so same level in different turns does not collide`() {
        val turn0 = SavedCardId.forSentence("sess-1", turnIndex = 0, level = 2)
        val turn1 = SavedCardId.forSentence("sess-1", turnIndex = 1, level = 2)
        assertEquals("sess-1__SENTENCE__0__2", turn0)
        assertNotEquals("level alone must not collide across turns", turn0, turn1)
    }

    @Test
    fun `sentence id is deterministic`() {
        assertEquals(
            SavedCardId.forSentence("s", 3, 1),
            SavedCardId.forSentence("s", 3, 1),
        )
    }

    @Test
    fun `forSummary rejects SENTENCE`() {
        assertThrows(IllegalArgumentException::class.java) {
            SavedCardId.forSummary("sess-1", CardType.SENTENCE, 0)
        }
    }
}

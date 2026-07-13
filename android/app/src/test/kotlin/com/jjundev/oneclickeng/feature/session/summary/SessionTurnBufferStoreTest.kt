package com.jjundev.oneclickeng.feature.session.summary

import com.jjundev.oneclickeng.feature.session.feedback.TurnFeedbackBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionTurnBufferStoreTest {
    private fun seeded() =
        SessionTurnBufferStore().apply {
            startSession("s1")
            record(
                "커피 주세요",
                "One coffee",
                TurnFeedbackBuffer(
                    slimScore = 80,
                    correctedText = "Could I get a coffee?",
                    naturalExpression = "Can I grab a coffee?",
                ),
            )
            record(
                "길 알려줘",
                "Where station",
                TurnFeedbackBuffer(slimScore = 90, correctedText = null, naturalExpression = null),
            )
        }

    @Test
    fun `bufferedTurns preserves raw per-turn fields`() {
        val turns = seeded().bufferedTurns()
        assertEquals(2, turns.size)
        assertEquals("One coffee", turns[0].userText)
        assertEquals("Could I get a coffee?", turns[0].correctedText)
        assertEquals("Can I grab a coffee?", turns[0].naturalExpression)
        assertEquals(80, turns[0].slimScore)
        assertNull(turns[1].correctedText)
        assertNull(turns[1].naturalExpression)
    }

    @Test
    fun `totalScore averages slim scores and highlight picks the top turn`() {
        val store = seeded()
        assertEquals(85, store.totalScore())
        assertEquals("Where station", store.highlightBase()?.userText)
    }

    @Test
    fun `new session clears the previous buffer`() {
        val store = seeded()
        store.startSession("s2")
        assertEquals(0, store.bufferedTurns().size)
    }
}

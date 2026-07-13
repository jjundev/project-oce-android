package com.jjundev.oneclickeng.feature.session.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryPayloadProjectorTest {
    private val turns =
        listOf(
            // 교정 O + 자연스러운 표현 O, 둘 다 원문과 다름 → accurate + natural 후보 각 1.
            BufferedTurn(
                "커피 주세요",
                "One coffee",
                correctedText = "Could I get a coffee?",
                naturalExpression = "Can I grab a coffee?",
                slimScore = 80,
            ),
            // 교정 없음(원문 정답) + 자연스러운 표현만 → natural 후보 1, after=자연스러운 표현.
            BufferedTurn(
                "고마워",
                "Thank you",
                correctedText = null,
                naturalExpression = "Thanks a lot!",
                slimScore = 90,
            ),
            // 교정==원문(변화 없음) → 후보 없음.
            BufferedTurn("네", "Yes", correctedText = "Yes", naturalExpression = null, slimScore = 70),
        )

    @Test
    fun `turns map to backend before-after-score shape`() {
        val p = SummaryPayloadProjector.project(turns, totalScore = 80)
        assertEquals(3, p.turns.size)
        assertEquals("One coffee", p.turns[0].before)
        assertEquals("Could I get a coffee?", p.turns[0].after) // corrected wins over natural
        assertEquals(80, p.turns[0].score)
        assertEquals("Thanks a lot!", p.turns[1].after) // no correction → natural
        assertEquals("Yes", p.turns[2].after)
    }

    @Test
    fun `expression candidates split accurate and natural, dropping no-change`() {
        val p = SummaryPayloadProjector.project(turns, totalScore = 80)
        // turn0: accurate + natural; turn1: natural; turn2: none (corrected==original, natural null)
        assertEquals(3, p.expressionCandidates.size)
        val t0 = p.expressionCandidates.filter { it.before == "One coffee" }
        assertEquals(setOf("accurate", "natural"), t0.map { it.type }.toSet())
        assertEquals("Could I get a coffee?", t0.first { it.type == "accurate" }.after)
        assertTrue(p.expressionCandidates.none { it.before == "Yes" })
    }

    @Test
    fun `sentences are improved forms, originals are user text, words empty`() {
        val p = SummaryPayloadProjector.project(turns, totalScore = 80)
        assertEquals(listOf("One coffee", "Thank you", "Yes"), p.userOriginalSentences)
        assertTrue(p.sentences.contains("Could I get a coffee?"))
        assertTrue(p.sentences.contains("Can I grab a coffee?"))
        assertTrue(p.sentences.contains("Thanks a lot!"))
        assertTrue(p.words.isEmpty())
        assertEquals(80, p.totalScore)
        assertNull(p.sections)
    }
}

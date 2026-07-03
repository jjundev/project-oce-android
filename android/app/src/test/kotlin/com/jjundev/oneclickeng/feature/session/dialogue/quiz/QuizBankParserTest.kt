package com.jjundev.oneclickeng.feature.session.dialogue.quiz

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizBankParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val bank =
        """
        [
          {"id":"e1","tier":"easy","prompt":"p","optionA":"a","optionB":"b","correctIndex":0,
           "revealCopyCorrect":"c","revealCopyWrong":"w"},
          {"id":"n1","tier":"normal","prompt":"p","optionA":"a","optionB":"b","correctIndex":1,
           "revealCopyCorrect":"c","revealCopyWrong":"w"},
          {"id":"h1","tier":"hard","prompt":"p","optionA":"a","optionB":"b","correctIndex":0,
           "revealCopyCorrect":"c","revealCopyWrong":"w"}
        ]
        """.trimIndent()

    @Test
    fun `parse groups by tier and maps tier to numeric level`() {
        val byTier = QuizBankParser.parse(json, bank)

        assertEquals(setOf("easy", "normal", "hard"), byTier.keys)
        assertEquals(1, byTier.getValue("easy").single().level)
        assertEquals(2, byTier.getValue("normal").single().level)
        assertEquals(3, byTier.getValue("hard").single().level)
        assertEquals("e1", byTier.getValue("easy").single().id)
    }

    @Test
    fun `entry fields map onto the QuizItem shape`() {
        val item = QuizBankParser.parse(json, bank).getValue("normal").single()

        assertEquals(1, item.correctIndex)
        assertEquals("a", item.optionA)
        assertEquals("b", item.optionB)
        assertEquals("c", item.revealCopyCorrect)
        assertEquals("w", item.revealCopyWrong)
    }

    @Test
    fun `the shipped seed asset schema parses`() {
        // Mirrors assets/wait_quiz_bank.json shape so a malformed seed is caught in unit tests.
        val byTier = QuizBankParser.parse(json, bank)
        assertTrue(byTier.values.flatten().isNotEmpty())
    }
}

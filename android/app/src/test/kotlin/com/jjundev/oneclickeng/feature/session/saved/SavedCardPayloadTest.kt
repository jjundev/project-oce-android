package com.jjundev.oneclickeng.feature.session.saved

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 3종 저장 카드 payload 가 firestore-schema §3 필드와 정합하는지(수용 기준 3). */
class SavedCardPayloadTest {
    @Test
    fun `word maps to schema field names`() {
        val card =
            SavedCard.Word(
                english = "grab",
                korean = "잽싸게",
                exampleEnglish = "grab a bite",
                exampleKorean = "간단히",
            )
        assertEquals(CardType.WORD, card.cardType)
        assertEquals(
            mapOf(
                "english" to "grab",
                "korean" to "잽싸게",
                "exampleEnglish" to "grab a bite",
                "exampleKorean" to "간단히",
            ),
            card.contentMap(),
        )
    }

    @Test
    fun `expression maps to schema fields and omits afterHighlights`() {
        val card =
            SavedCard.Expression(
                type = "accurate",
                koreanPrompt = "길을 잃었어요",
                before = "I lost",
                after = "I got lost",
                explanation = "get lost",
            )
        assertEquals(CardType.EXPRESSION, card.cardType)
        assertEquals(
            mapOf(
                "type" to "accurate",
                "koreanPrompt" to "길을 잃었어요",
                "before" to "I lost",
                "after" to "I got lost",
                "explanation" to "get lost",
            ),
            card.contentMap(),
        )
        // afterHighlights 는 소스 필드가 없어 요약-출처 저장에선 항상 생략(rev-3 #6).
        assertFalse(card.contentMap().containsKey("afterHighlights"))
    }

    @Test
    fun `sentence maps translation to korean`() {
        val card = SavedCard.Sentence(english = "Can I get a coffee?", korean = "커피 한 잔 주세요.")
        assertEquals(CardType.SENTENCE, card.cardType)
        assertEquals(
            mapOf("english" to "Can I get a coffee?", "korean" to "커피 한 잔 주세요."),
            card.contentMap(),
        )
    }

    @Test
    fun `content map never carries common fields (repo injects them)`() {
        val maps =
            listOf(
                SavedCard.Word("a", "b", "c", "d").contentMap(),
                SavedCard.Expression("natural", "a", "b", "c", "d").contentMap(),
                SavedCard.Sentence("a", "b").contentMap(),
            )
        maps.forEach { map ->
            assertFalse(map.containsKey("cardType"))
            assertFalse(map.containsKey("createdAt"))
            assertFalse(map.containsKey("deletedAt"))
        }
    }
}

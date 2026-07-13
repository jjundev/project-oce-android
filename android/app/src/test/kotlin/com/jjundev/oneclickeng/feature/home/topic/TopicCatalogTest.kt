package com.jjundev.oneclickeng.feature.home.topic

import com.jjundev.oneclickeng.feature.onboarding.topic.ONBOARDING_TOPICS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M3-08 주제 카탈로그 + 결정적 추천 회전(A3) 검증 — 순수 함수라 클럭/DI 없이 단언한다. */
class TopicCatalogTest {
    @Test
    fun `catalog holds 120 topics with 30 in every group`() {
        assertEquals(120, TopicCatalog.ALL.size)
        TopicGroup.entries.forEach { group ->
            assertEquals(30, TopicCatalog.inGroup(group).size)
        }
        assertEquals(120, TopicGroup.entries.sumOf { TopicCatalog.inGroup(it).size })
    }

    @Test
    fun `every bundled topic has stable complete presentation and generation data`() {
        val topics = TopicCatalog.ALL
        assertEquals(topics.size, topics.map { it.id }.toSet().size)
        assertTrue(topics.all { it.id.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*")) })
        assertTrue(topics.all { it.titleKo.isNotBlank() })
        assertTrue(topics.all { it.emoji.isNotBlank() })
        assertTrue(topics.all { it.promptSeed.isNotBlank() && '\n' !in it.promptSeed })
    }

    @Test
    fun `beginnerFriendly is exactly the 6 onboarding candidates`() {
        assertEquals(6, TopicCatalog.beginnerFriendly.size)
        assertEquals(
            TopicCatalog.beginnerFriendly.map { it.id },
            ONBOARDING_TOPICS.map { it.id },
        )
        // 첫 후보는 카페 주문(비강조, seed order).
        assertEquals("cafe-order", ONBOARDING_TOPICS.first().id)
    }

    @Test
    fun `recommended returns 6 distinct topics`() {
        val picks = TopicCatalog.recommended(dayIndex = 20000, refresh = 0)
        assertEquals(6, picks.size)
        assertEquals(6, picks.map { it.id }.toSet().size)
    }

    @Test
    fun `recommended is deterministic for the same day and refresh`() {
        assertEquals(
            TopicCatalog.recommended(dayIndex = 19876, refresh = 2).map { it.id },
            TopicCatalog.recommended(dayIndex = 19876, refresh = 2).map { it.id },
        )
    }

    @Test
    fun `refresh advances the window`() {
        assertNotEquals(
            TopicCatalog.recommended(dayIndex = 19876, refresh = 0).map { it.id },
            TopicCatalog.recommended(dayIndex = 19876, refresh = 1).map { it.id },
        )
    }

    @Test
    fun `recommended always fills the window even across the 16 to 6 wraparound`() {
        // 16 은 6 의 배수가 아니라 창이 경계를 감는다 — 항상 6개(순환 모듈로), 음수 인덱스 없음.
        for (day in listOf(0L, 1L, 2L, 15L, 16L, 99999L)) {
            val picks = TopicCatalog.recommended(dayIndex = day)
            assertEquals(6, picks.size)
            assertTrue(picks.all { it in TopicCatalog.ALL })
        }
    }
}

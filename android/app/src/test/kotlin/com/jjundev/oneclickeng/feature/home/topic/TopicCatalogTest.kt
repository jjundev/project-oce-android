package com.jjundev.oneclickeng.feature.home.topic

import com.jjundev.oneclickeng.feature.onboarding.topic.ONBOARDING_TOPICS
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M3-08 주제 카탈로그 + 결정적 추천 회전(A3) 검증 — 순수 함수라 클럭/DI 없이 단언한다. */
class TopicCatalogTest {
    @Before
    fun installBundledCatalog() {
        TopicCatalog.install(TopicCatalogAssetParser.parse(TEST_JSON, bundledAsset()))
    }

    @Test
    fun `catalog holds 300 topics with 75 in every group`() {
        assertEquals(300, TopicCatalog.ALL.size)
        TopicGroup.entries.forEach { group ->
            assertEquals(75, TopicCatalog.inGroup(group).size)
        }
        assertEquals(300, TopicGroup.entries.sumOf { TopicCatalog.inGroup(it).size })
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
    fun `new topics expose a situation-specific blue preview icon`() {
        val iconById = TopicCatalog.ALL.associate { it.id to it.icon }

        assertEquals(OceIcon.WavingHand, iconById.getValue("greeting-neighbor"))
        assertEquals(OceIcon.Hub, iconById.getValue("baggage-claim"))
        assertEquals(OceIcon.Send, iconById.getValue("email-followup"))
        assertEquals(OceIcon.Call, iconById.getValue("emergency-call"))
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
    fun `recommended always fills the window across catalog boundaries`() {
        for (day in listOf(0L, 1L, 2L, 74L, 75L, 99999L)) {
            val picks = TopicCatalog.recommended(dayIndex = day)
            assertEquals(6, picks.size)
            assertTrue(picks.all { it in TopicCatalog.ALL })
        }
    }

    private companion object {
        val TEST_JSON = Json { ignoreUnknownKeys = true }
    }
}

private fun bundledAsset(): String =
    checkNotNull(TopicCatalogTest::class.java.classLoader)
        .getResourceAsStream("topics.json")
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: error(
        "topics.json is not available on the JVM test classpath"
    )

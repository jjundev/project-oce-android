package com.jjundev.oneclickeng.feature.home.topic

import com.jjundev.oneclickeng.ui.foundation.OceIcon
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contract tests for the canonical local 300-topic asset and its strict parser. */
class TopicCatalogAssetParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `shipped asset has all required records groups onboarding ids and icons`() {
        val snapshot = parse(validAsset())

        assertEquals(300, snapshot.all.size)
        TopicGroup.entries.forEach { assertEquals(75, snapshot.inGroup(it).size) }
        assertEquals(
            setOf("cafe-order", "weather-smalltalk", "hobby-intro", "restaurant", "hotel-checkin", "taxi"),
            snapshot.beginnerFriendly.map { it.id }.toSet(),
        )
        assertTrue(snapshot.all.all { it.icon in OceIcon.entries })
        assertEquals(OceIcon.WavingHand, snapshot.all.single { it.id == "greeting-neighbor" }.icon)
    }

    @Test
    fun `parser rejects malformed assets`() {
        val cases = listOf(
            "unknown root field" to validAsset().replaceFirst("{", "{\"unexpected\":true,"),
            "wrong version" to validAsset().replaceFirst("\"version\": 1", "\"version\": 2"),
            "missing row field" to validAsset().replaceFirst("\"emoji\": \"☕\",", ""),
            "invalid id" to validAsset().replaceFirst("\"id\": \"cafe-order\"", "\"id\": \"bad id\""),
            "blank title" to validAsset().replaceFirst("\"titleKo\": \"카페에서 주문하기\"", "\"titleKo\": \"\""),
            "multiline prompt" to
                validAsset().replaceFirst(
                    "ordering a drink and a snack at a café counter",
                    "line one\\nline two",
                ),
            "duplicate id" to validAsset().replaceFirst("\"id\": \"weather-smalltalk\"", "\"id\": \"cafe-order\""),
            "unknown group" to validAsset().replaceFirst("\"group\": \"daily\"", "\"group\": \"unknown\""),
            "unknown icon" to validAsset().replaceFirst("\"icon\": \"LocalCafe\"", "\"icon\": \"NoSuchIcon\""),
            "wrong group quota" to validAsset().replaceFirst("\"group\": \"daily\"", "\"group\": \"travel\""),
            "wrong onboarding ids" to
                validAsset().replaceFirst(
                    "\"beginnerFriendly\": true",
                    "\"beginnerFriendly\": false",
                ),
        )

        cases.forEach { (name, malformed) ->
            assertThrows(name, IllegalArgumentException::class.java) { parse(malformed) }
        }
    }

    @Test
    fun `catalog installation is idempotent only for an equal snapshot`() {
        val snapshot = parse(validAsset())
        TopicCatalog.install(snapshot)
        TopicCatalog.install(snapshot.copy(all = snapshot.all.toList()))

        val distinct = snapshot.copy(all = snapshot.all.dropLast(1) + snapshot.all.last().copy(titleKo = "다른 제목"))
        assertThrows(IllegalStateException::class.java) { TopicCatalog.install(distinct) }
    }

    private fun parse(text: String): TopicCatalogSnapshot = TopicCatalogAssetParser.parse(json, text)
}

/** A valid 300-record fixture seeded from the exact classpath asset, then mutated per test case. */
private fun validAsset(): String =
    checkNotNull(TopicCatalogAssetParserTest::class.java.classLoader)
        .getResourceAsStream("topics.json")
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: error(
        "topics.json is not available on the JVM test classpath"
    )

package com.jjundev.oneclickeng.feature.session.dialogue.loading

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LoadingMessageParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val asset =
        """
        {
          "onboarding": "첫 대화를 준비하고 있어요",
          "returning": ["다음 대화를 준비하고 있어요", "오늘의 연습을 준비하고 있어요"]
        }
        """.trimIndent()

    @Test
    fun `parse preserves the onboarding copy and returning pool`() {
        val catalog = LoadingMessageParser.parse(json, asset)

        assertEquals("첫 대화를 준비하고 있어요", catalog.onboarding)
        assertEquals(
            listOf("다음 대화를 준비하고 있어요", "오늘의 연습을 준비하고 있어요"),
            catalog.returning,
        )
    }

    @Test
    fun `selector always keeps the exact onboarding copy`() {
        val catalog = LoadingMessageParser.parse(json, asset)

        assertEquals(
            "첫 대화를 준비하고 있어요",
            LoadingMessageSelector.select(catalog, isOnboarding = true, random = Random(0)),
        )
    }

    @Test
    fun `selector chooses a returning message from the JSON pool`() {
        val catalog = LoadingMessageParser.parse(json, asset)

        val selected = LoadingMessageSelector.select(catalog, isOnboarding = false, random = Random(0))

        assertTrue(selected in catalog.returning)
    }

    @Test
    fun `shipped asset contains the exact onboarding copy and multiple returning messages`() {
        val assetFile =
            sequenceOf(
                java.io.File("src/main/assets/loading_messages.json"),
                java.io.File("app/src/main/assets/loading_messages.json"),
            ).first { it.isFile }
        val catalog = LoadingMessageParser.parse(json, assetFile.readText())

        assertEquals("첫 대화를 준비하고 있어요", catalog.onboarding)
        assertTrue(catalog.returning.size >= 2)
    }
}

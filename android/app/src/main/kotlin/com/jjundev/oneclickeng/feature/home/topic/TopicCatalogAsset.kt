package com.jjundev.oneclickeng.feature.home.topic

import android.content.Context
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Thin Android asset reader; all parsing and validation stay in [TopicCatalogAssetParser]. */
@Singleton
class TopicCatalogAssetRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val networkJson: Json,
    ) {
        private val snapshot: TopicCatalogSnapshot by lazy {
            val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            TopicCatalogAssetParser.parse(networkJson, text)
        }

        fun load(): TopicCatalogSnapshot = snapshot

        private companion object {
            const val ASSET_NAME = "topics.json"
        }
    }

/** Strict, pure JSON-to-domain parser for the canonical bundled topic asset. */
object TopicCatalogAssetParser {
    private const val EXPECTED_VERSION = 1
    private const val EXPECTED_TOPIC_COUNT = 300
    private const val EXPECTED_GROUP_COUNT = 75
    private val expectedBeginnerIds =
        setOf("cafe-order", "weather-smalltalk", "hobby-intro", "restaurant", "hotel-checkin", "taxi")
    private val groupByWire =
        mapOf(
            "daily" to TopicGroup.Daily,
            "travel" to TopicGroup.Travel,
            "work" to TopicGroup.Work,
            "life" to TopicGroup.Life,
        )
    private val iconByName = OceIcon.entries.associateBy { it.name }
    private val idPattern = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")

    fun parse(
        networkJson: Json,
        text: String,
    ): TopicCatalogSnapshot {
        // The network instance tolerates future wire fields; this local app asset must not.
        val strictJson = Json(from = networkJson) { ignoreUnknownKeys = false }
        val asset = strictJson.decodeFromString<TopicCatalogAssetDto>(text)
        require(asset.version == EXPECTED_VERSION) { "Unsupported topics asset version: ${asset.version}" }
        require(asset.topics.size == EXPECTED_TOPIC_COUNT) {
            "Expected $EXPECTED_TOPIC_COUNT topics, got ${asset.topics.size}"
        }

        val ids = asset.topics.map { it.id }
        require(ids.size == ids.toSet().size) { "Topic ids must be unique" }

        val topics = asset.topics.map { row -> row.toTopic() }
        require(TopicGroup.entries.all { group -> topics.count { it.group == group } == EXPECTED_GROUP_COUNT }) {
            "Each topic group must contain $EXPECTED_GROUP_COUNT topics"
        }
        require(topics.filter { it.beginnerFriendly }.map { it.id }.toSet() == expectedBeginnerIds) {
            "The bundled onboarding topic ids do not match the required six"
        }
        return TopicCatalogSnapshot(topics)
    }

    private fun TopicAssetRowDto.toTopic(): Topic {
        require(id.matches(idPattern)) { "Invalid topic id: $id" }
        require(emoji.isNotBlank()) { "Topic $id has a blank emoji" }
        require(titleKo.isNotBlank()) { "Topic $id has a blank Korean title" }
        require(promptSeed.isNotBlank() && '\n' !in promptSeed && '\r' !in promptSeed) {
            "Topic $id must have a one-line prompt seed"
        }
        val resolvedGroup = requireNotNull(groupByWire[group]) { "Unknown topic group: $group" }
        val resolvedIcon = requireNotNull(iconByName[icon]) { "Unknown topic icon: $icon" }
        return Topic(id, emoji, titleKo, resolvedGroup, beginnerFriendly, promptSeed, resolvedIcon)
    }
}

@Serializable
private data class TopicCatalogAssetDto(
    val version: Int,
    val topics: List<TopicAssetRowDto>,
)

@Serializable
private data class TopicAssetRowDto(
    val id: String,
    val emoji: String,
    val icon: String,
    val titleKo: String,
    val group: String,
    val beginnerFriendly: Boolean,
    val promptSeed: String,
)

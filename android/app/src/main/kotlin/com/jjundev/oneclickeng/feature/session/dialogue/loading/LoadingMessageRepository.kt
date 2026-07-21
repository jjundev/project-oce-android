package com.jjundev.oneclickeng.feature.session.dialogue.loading

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

interface LoadingMessageSource {
    fun forSession(isOnboarding: Boolean): String
}

@Serializable
data class LoadingMessageCatalog(
    val onboarding: String,
    val returning: List<String>,
)

object LoadingMessageParser {
    fun parse(json: Json, text: String): LoadingMessageCatalog =
        json.decodeFromString(LoadingMessageCatalog.serializer(), text)
}

object LoadingMessageSelector {
    fun select(
        catalog: LoadingMessageCatalog,
        isOnboarding: Boolean,
        random: Random = Random.Default,
    ): String =
        if (isOnboarding) {
            catalog.onboarding
        } else {
            catalog.returning.random(random)
        }
}

@Singleton
class LoadingMessageRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val json: Json,
    ) : LoadingMessageSource {
        private val catalog: LoadingMessageCatalog by lazy {
            val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            LoadingMessageParser.parse(json, text)
        }

        override fun forSession(isOnboarding: Boolean): String =
            LoadingMessageSelector.select(catalog, isOnboarding)

        private companion object {
            const val ASSET = "loading_messages.json"
        }
    }

package com.jjundev.oneclickeng.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads TTS settings for the playback coordinator. Kept as an interface so the
 * coordinator can be tested with a fixed-value fake. The edit UI (M3-09) will add writes.
 */
interface TtsSettingsRepository {
    /** live settings stream. */
    val settings: Flow<TtsSettings>

    /** one-shot snapshot for a single playback decision. */
    suspend fun current(): TtsSettings
}

/** DataStore-backed implementation. Missing keys fall back to [TtsSettings] defaults. */
@Singleton
class DataStoreTtsSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : TtsSettingsRepository {
        override val settings: Flow<TtsSettings> = dataStore.data.map(::toSettings)

        override suspend fun current(): TtsSettings = toSettings(dataStore.data.first())

        private fun toSettings(prefs: Preferences): TtsSettings {
            val quality =
                prefs[KEY_QUALITY]?.let { name ->
                    runCatching { TtsQuality.valueOf(name) }.getOrNull()
                } ?: TtsQuality.SERVER
            val rate =
                prefs[KEY_SPEECH_RATE]?.coerceIn(
                    TtsSettings.MIN_SPEECH_RATE,
                    TtsSettings.MAX_SPEECH_RATE,
                ) ?: TtsSettings.DEFAULT_SPEECH_RATE
            val muted = prefs[KEY_MUTED] ?: false
            return TtsSettings(quality = quality, speechRate = rate, muted = muted)
        }

        companion object {
            val KEY_QUALITY = stringPreferencesKey("tts_quality")
            val KEY_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
            val KEY_MUTED = booleanPreferencesKey("tts_muted")
        }
    }

package com.jjundev.oneclickeng.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes TTS settings. Reads feed the playback coordinator (M1-05); writes back the
 * settings screen (M3-09). Kept as an interface so the coordinator can be tested with a fake.
 */
interface TtsSettingsRepository {
    /** live settings stream. */
    val settings: Flow<TtsSettings>

    /** one-shot snapshot for a single playback decision. */
    suspend fun current(): TtsSettings

    /** Persist the 음질 toggle (server/device). */
    suspend fun setQuality(quality: TtsQuality)

    /** Persist the 말하기 속도, clamped to [TtsSettings.MIN_SPEECH_RATE]..[TtsSettings.MAX_SPEECH_RATE]. */
    suspend fun setSpeechRate(rate: Float)

    /** Persist the 전체 음소거 toggle. */
    suspend fun setMuted(muted: Boolean)
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

        override suspend fun setQuality(quality: TtsQuality) {
            dataStore.edit { it[KEY_QUALITY] = quality.name }
        }

        override suspend fun setSpeechRate(rate: Float) {
            val clamped = rate.coerceIn(TtsSettings.MIN_SPEECH_RATE, TtsSettings.MAX_SPEECH_RATE)
            dataStore.edit { it[KEY_SPEECH_RATE] = clamped }
        }

        override suspend fun setMuted(muted: Boolean) {
            dataStore.edit { it[KEY_MUTED] = muted }
        }

        private fun toSettings(prefs: Preferences): TtsSettings {
            val quality =
                prefs[KEY_QUALITY]?.let { name ->
                    runCatching { TtsQuality.valueOf(name) }.getOrNull()
                } ?: TtsQuality.DEVICE
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

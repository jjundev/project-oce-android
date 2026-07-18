package com.jjundev.oneclickeng.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 세션 요약 화면의 새 표현/단어 카드 저장 기본값을 읽고 쓴다. 설정 화면(쓰기)과
 * [com.jjundev.oneclickeng.feature.session.summary.SummaryCoordinator](세션 시작 시 1회 읽기) 양쪽이 이
 * 인터페이스를 직접 주입한다(`TtsSettingsRepository` 와 동일한 cross-feature 공유 패턴 — 별도
 * orchestrator 레이어를 두지 않는다).
 */
interface SummarySaveSettingsRepository {
    /** 라이브 설정 스트림(설정 화면 구독용). */
    val settings: Flow<SummarySaveSettings>

    /** 요약 코디네이터가 세션 시작 시 1회 읽는 스냅샷. */
    suspend fun current(): SummarySaveSettings

    suspend fun setSaveByDefault(saveByDefault: Boolean)
}

/** DataStore 구현. 누락 키는 [SummarySaveSettings] 기본값(true)으로 폴백. */
@Singleton
class DataStoreSummarySaveSettingsRepository
    @Inject
    constructor(
        @SummarySavePrefs private val dataStore: DataStore<Preferences>,
    ) : SummarySaveSettingsRepository {
        override val settings: Flow<SummarySaveSettings> = dataStore.data.map(::toSettings)

        override suspend fun current(): SummarySaveSettings = toSettings(dataStore.data.first())

        override suspend fun setSaveByDefault(saveByDefault: Boolean) {
            dataStore.edit { it[KEY_SAVE_BY_DEFAULT] = saveByDefault }
        }

        private fun toSettings(prefs: Preferences): SummarySaveSettings =
            SummarySaveSettings(saveByDefault = prefs[KEY_SAVE_BY_DEFAULT] ?: true)

        companion object {
            val KEY_SAVE_BY_DEFAULT = booleanPreferencesKey("summary_save_by_default")
        }
    }

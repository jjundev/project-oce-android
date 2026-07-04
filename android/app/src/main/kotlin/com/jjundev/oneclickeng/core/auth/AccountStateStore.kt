package com.jjundev.oneclickeng.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "삭제 진행 중" 로컬 플래그(M3-09, FR-23, settings-data-account.md:148). 계정 삭제는 비원자 배치라 중간
 * 종료 시 Auth-orphan 이 남을 수 있어, 이 플래그로 다음 앱 진입 시 멱등 재호출을 트리거한다.
 *
 * **전용 DataStore(`settings_prefs`, [SettingsPrefs]):** 이 플래그는 로그아웃/삭제가 비우는 다른 기능
 * DataStore 들과 반드시 **분리**돼야 한다 — [LocalStateCleaner] 의 전체삭제가 이 스토어는 건드리지 않으므로
 * cold-start 재개 신호가 삭제 흐름 도중에도 살아남는다.
 */
@Singleton
class AccountStateStore
    @Inject
    constructor(
        @SettingsPrefs private val dataStore: DataStore<Preferences>,
    ) {
        /** 삭제 흐름 진입 시 true, 완료 시 false. */
        suspend fun setDeleteInProgress(inProgress: Boolean) {
            dataStore.edit { it[KEY_DELETE_IN_PROGRESS] = inProgress }
        }

        /** cold-start 재개 판정용 스냅샷. */
        suspend fun isDeleteInProgress(): Boolean = dataStore.data.first()[KEY_DELETE_IN_PROGRESS] ?: false

        private companion object {
            val KEY_DELETE_IN_PROGRESS = booleanPreferencesKey("account_delete_in_progress")
        }
    }

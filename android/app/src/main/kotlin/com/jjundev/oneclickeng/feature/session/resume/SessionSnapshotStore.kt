package com.jjundev.oneclickeng.feature.session.resume

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jjundev.oneclickeng.feature.session.turn.SessionTurnSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Durable local snapshot of an in-progress session (M3-08, home-learning-entry.md §2.5 확정 계약).
 *
 * §2.5 mandates a **local recoverable snapshot** with "시간 만료 없음 · 새 세션 시작 시에만 폐기" — a
 * process-scoped in-memory holder would lose it on app kill, which the 계약 forbids. So the same
 * [SessionTurnSnapshot] the session VM already serializes for `SavedStateHandle` (rotation / same-screen
 * process-kill) is mirrored here to DataStore, surviving app termination. Home reads [recoverable] to
 * decide whether to show the 이어하기 prompt; the session route restores from [read] when its
 * `SavedStateHandle` has no in-screen snapshot (i.e. a fresh entry reached from home).
 *
 * **Discard policy:** cleared only by a NEW session's generation start ([clear] from the generation
 * layer) and when a session reaches completion (a finished dialogue is not a "미완" resume candidate,
 * §2.5 title). No time expiry — this is the whole point of the durable contract.
 */
@Singleton
class SessionSnapshotStore
    @Inject
    constructor(
        @SessionResumePrefs private val dataStore: DataStore<Preferences>,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        /** `true` while a persisted, schema-current snapshot exists. Home observes this reactively. */
        val recoverable: Flow<Boolean> = dataStore.data.map { it[KEY_SNAPSHOT] != null }

        /** Persist (overwrite) the current snapshot. Called on session progress. */
        suspend fun write(snapshot: SessionTurnSnapshot) {
            dataStore.edit { it[KEY_SNAPSHOT] = json.encodeToString(snapshot) }
        }

        /** Decode the persisted snapshot, or null when absent / undecodable / a stale schema version. */
        suspend fun read(): SessionTurnSnapshot? =
            dataStore.data.first()[KEY_SNAPSHOT]
                ?.let { runCatching { json.decodeFromString<SessionTurnSnapshot>(it) }.getOrNull() }
                ?.takeIf { it.schemaVersion == SessionTurnSnapshot.SCHEMA_VERSION }

        /** Discard the snapshot (new session start / completion). */
        suspend fun clear() {
            dataStore.edit { it.remove(KEY_SNAPSHOT) }
        }

        private companion object {
            val KEY_SNAPSHOT = stringPreferencesKey("session_snapshot_json")
        }
    }

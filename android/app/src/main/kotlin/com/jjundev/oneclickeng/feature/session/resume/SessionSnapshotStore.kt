package com.jjundev.oneclickeng.feature.session.resume

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jjundev.oneclickeng.feature.session.turn.SessionPhase
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
 * process-kill) is mirrored here to DataStore, surviving app termination. Home reads [resumeInfo] to
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

        /**
         * 이어하기 프롬프트용 검증·해석 스냅샷. `recoverable`(key 존재만 검사)의 팬텀을 근절한다:
         * 디코드 성공 + 스키마 일치 + 미완(sessionPhase != Completed) + 실제 렌더 메시지 1개 이상 +
         * 표시 가능한 제목이 모두 성립할 때만 [ResumeInfo]. [SessionTurnSnapshot.messages]는 렌더된
         * 말풍선만 담으므로, 비어 있으면 아직 타이핑 스켈레톤 상태여서 제외한다. 진행 단위(doneTurns)는
         * 완료한 학습자 턴 수로, 세션 헤더 `completedTurns`(GeneratedDialogueSession) 및 `totalTurns` 와 같은 축이다.
         */
        val resumeInfo: Flow<ResumeInfo?> =
            dataStore.data.map { prefs ->
                val snap =
                    prefs[KEY_SNAPSHOT]
                        ?.let { runCatching { json.decodeFromString<SessionTurnSnapshot>(it) }.getOrNull() }
                        ?.takeIf { it.schemaVersion == SessionTurnSnapshot.SCHEMA_VERSION }
                        ?: return@map null
                val title = snap.topicTitle
                val done = snap.messages.count { it.isLearner }
                if (
                    title.isNullOrBlank() ||
                    snap.messages.isEmpty() ||
                    snap.sessionPhase == SessionPhase.Completed.name
                ) {
                    return@map null
                }
                ResumeInfo(
                    topicTitle = title,
                    doneTurns = done,
                    totalTurns = snap.totalTurns ?: DEFAULT_TOTAL_TURNS,
                )
            }

        /**
         * Store exactly one recoverable session state. A completed session is terminal, so this removes
         * any older in-progress JSON rather than leaving a stale resume candidate behind.
         */
        suspend fun persist(snapshot: SessionTurnSnapshot) {
            if (snapshot.sessionPhase == SessionPhase.Completed.name) {
                clear()
            } else {
                write(snapshot)
            }
        }

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

            /** totalTurns 미기재 스냅샷의 폴백(세션 기본 길이, GeneratedDialogueSession.DEFAULT_TOTAL_TURNS 정합). */
            const val DEFAULT_TOTAL_TURNS = 5
        }
    }

/**
 * 홈 이어하기 프롬프트가 필요로 하는 스냅샷 요약 — 표시 제목과 진행(완료 학습자 턴 / 전체 턴).
 * 이 타입이 존재한다는 것 자체가 "이어갈 미완 세션이 실재한다"는 계약이다(null = 없음).
 */
data class ResumeInfo(
    val topicTitle: String,
    val doneTurns: Int,
    val totalTurns: Int,
)

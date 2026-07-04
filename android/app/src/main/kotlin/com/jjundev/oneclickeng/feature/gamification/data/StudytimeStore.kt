package com.jjundev.oneclickeng.feature.gamification.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jjundev.oneclickeng.feature.gamification.GamificationTime
import com.jjundev.oneclickeng.feature.gamification.di.GamificationPrefs
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local gamification state — the client authority for study time + an optimistic streak mirror
 * (M3-05, ADR-0002 "DataStore write-ahead 큐"). This is the WRITE-AHEAD LOG for the studytime layer:
 * a completed session is committed here FIRST (durable, survives process death), then pushed to
 * Firestore by [com.jjundev.oneclickeng.feature.gamification.StudytimeRepository]; a crash between
 * the two is recovered by the app-start drain.
 *
 * Why a local accumulator rather than Firestore-native offline writes (unlike saved_cards, ADR-0002):
 * `studytime.totalSeconds` is a read-modify-write (add this session's seconds to the running total).
 * Offline, Firestore's cached value is stale, so several offline sessions would each read the same
 * total and overwrite instead of accumulate. Here the local total is authoritative and monotonic, and
 * the Firestore write is an idempotent `set` of that total (the rule enforces `totalSeconds >=`).
 *
 * Idempotency: [accrue] is keyed by the immutable server `sessionId` via a bounded settled-set, so a
 * WAQ replay (or a summary re-entry) of the same session accrues exactly once. The optimistic streak
 * advances in the same guarded write, so it too moves once per session (and never on a same-day repeat).
 */
@Singleton
class StudytimeStore
    @Inject
    constructor(
        @GamificationPrefs private val dataStore: DataStore<Preferences>,
    ) {
        /** A snapshot of the local gamification state. */
        data class State(
            val totalSeconds: Long,
            val todayDayKey: String?,
            val todaySeconds: Long,
            val streak: Int,
            val lastStudyDate: String?,
            val unsynced: Boolean,
        )

        /** [state] after an [accrue]; [changed] is false when the session was already settled (no-op). */
        data class AccrualResult(
            val state: State,
            val changed: Boolean,
        )

        /**
         * Accrue one completed session, guarded by the settled-set. On a fresh session: adds [seconds]
         * to the running total and to today's bucket (resetting the bucket on a KST day rollover),
         * advances the optimistic streak for [dayKey], records the session id (bounded LRU), and marks
         * the state unsynced. A session already in the settled-set is a no-op (`changed = false`).
         */
        suspend fun accrue(
            sessionId: String,
            seconds: Long,
            dayKey: String,
        ): AccrualResult {
            var changed = false
            val prefs =
                dataStore.edit { p ->
                    val settled = decodeIds(p[KEY_SETTLED])
                    if (sessionId in settled) return@edit // already accrued — leave everything untouched
                    changed = true

                    p[KEY_TOTAL] = (p[KEY_TOTAL] ?: 0L) + seconds
                    if (p[KEY_TODAY_KEY] == dayKey) {
                        p[KEY_TODAY_SECONDS] = (p[KEY_TODAY_SECONDS] ?: 0L) + seconds
                    } else {
                        p[KEY_TODAY_KEY] = dayKey
                        p[KEY_TODAY_SECONDS] = seconds
                    }

                    p[KEY_STREAK] = GamificationTime.advanceStreak(p[KEY_STREAK] ?: 0, p[KEY_LAST_STUDY_DATE], dayKey)
                    p[KEY_LAST_STUDY_DATE] = dayKey

                    p[KEY_SETTLED] = encodeIds((settled + sessionId).takeLast(MAX_SETTLED))
                    p[KEY_UNSYNCED] = true
                }
            return AccrualResult(toState(prefs), changed)
        }

        /** Current snapshot (drain / display reads). */
        suspend fun snapshot(): State = toState(dataStore.data.first())

        /**
         * Seed the local authority from the server ONCE, only when this device has no local gamification
         * state yet (fresh install / reinstall). After seeding, the local total/streak are authoritative.
         */
        suspend fun seedIfEmpty(
            serverTotalSeconds: Long,
            serverStreak: Int?,
            serverLastStudyDate: String?,
        ) {
            dataStore.edit { p ->
                if (p[KEY_TOTAL] == null) p[KEY_TOTAL] = serverTotalSeconds
                if (p[KEY_STREAK] == null && serverStreak != null) p[KEY_STREAK] = serverStreak
                if (p[KEY_LAST_STUDY_DATE] == null && serverLastStudyDate != null) {
                    p[KEY_LAST_STUDY_DATE] = serverLastStudyDate
                }
            }
        }

        /** Mark the state as pushed to Firestore (the drain / recordSession clears the write-ahead flag). */
        suspend fun markSynced() {
            dataStore.edit { it[KEY_UNSYNCED] = false }
        }

        private fun toState(prefs: Preferences): State =
            State(
                totalSeconds = prefs[KEY_TOTAL] ?: 0L,
                todayDayKey = prefs[KEY_TODAY_KEY],
                todaySeconds = prefs[KEY_TODAY_SECONDS] ?: 0L,
                streak = prefs[KEY_STREAK] ?: 0,
                lastStudyDate = prefs[KEY_LAST_STUDY_DATE],
                unsynced = prefs[KEY_UNSYNCED] ?: false,
            )

        // Session ids are UUIDs (no newline), so a newline-joined, insertion-ordered list is a safe
        // bounded-LRU encoding — membership dedups, `takeLast` evicts the oldest.
        private fun decodeIds(encoded: String?): List<String> =
            encoded?.split("\n")?.filter { it.isNotEmpty() }.orEmpty()

        private fun encodeIds(ids: List<String>): String = ids.joinToString("\n")

        companion object {
            /** Recent-session dedup window. Ample for WAQ replay (offline→online) without unbounded growth. */
            const val MAX_SETTLED = 200

            val KEY_TOTAL = longPreferencesKey("studytime_total_seconds")
            val KEY_TODAY_KEY = stringPreferencesKey("studytime_today_day_key")
            val KEY_TODAY_SECONDS = longPreferencesKey("studytime_today_seconds")
            val KEY_STREAK = intPreferencesKey("gamification_streak")
            val KEY_LAST_STUDY_DATE = stringPreferencesKey("gamification_last_study_date")
            val KEY_SETTLED = stringPreferencesKey("studytime_settled_session_ids")
            val KEY_UNSYNCED = booleanPreferencesKey("studytime_unsynced")
        }
    }

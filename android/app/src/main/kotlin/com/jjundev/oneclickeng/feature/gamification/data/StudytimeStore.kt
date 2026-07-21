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

        /**
         * [state] after an [accrue]; [changed] is false when the session was already settled (no-op).
         *
         * [todaySecondsBefore]/[sameDayRepeat] are the accrual-strip count-up baseline (M3-06): the study
         * time slot rolls its today bucket [todaySecondsBefore]→[State.todaySeconds], and the streak stays
         * static on a same-day repeat ([sameDayRepeat]). Both are captured BEFORE the settled-set gate, so
         * on an idempotent replay `before == after` (the settled session is already folded into the bucket)
         * and [sameDayRepeat] is true — the strip snaps static, which is the desired replay behaviour.
         */
        data class AccrualResult(
            val state: State,
            val changed: Boolean,
            val todaySecondsBefore: Long,
            val sameDayRepeat: Boolean,
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
            var todaySecondsBefore = 0L
            var sameDayRepeat = false
            val prefs =
                dataStore.edit { p ->
                    // Capture the pre-accrual baseline BEFORE the settled-set gate (M3-06 count-up):
                    // today's bucket is the study-time roll's "before" (0 on a KST day rollover), and a
                    // same-day repeat holds the streak static.
                    todaySecondsBefore = if (p[KEY_TODAY_KEY] == dayKey) p[KEY_TODAY_SECONDS] ?: 0L else 0L
                    sameDayRepeat = p[KEY_LAST_STUDY_DATE] == dayKey

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
            return AccrualResult(toState(prefs), changed, todaySecondsBefore, sameDayRepeat)
        }

        /** Current snapshot (drain / display reads). */
        suspend fun snapshot(): State = toState(dataStore.data.first())

        /**
         * Seed the local authority from the server ONCE, only when this device has no local gamification
         * state yet (fresh install / reinstall). After seeding, the local total/streak are authoritative.
         *
         * All three fields are gated on `KEY_TOTAL == null` together (truly-empty local), NOT each on its
         * own key: a metrics reset ([reset]) writes `KEY_TOTAL = 0` (non-null) but clears
         * `KEY_LAST_STUDY_DATE`/`KEY_STREAK`, so a per-key guard would re-seed lastStudyDate/streak from a
         * not-yet-reset server and silently revive them. Seeding atomically on the total's presence closes
         * that revival path (M3-09 §reset).
         */
        suspend fun seedIfEmpty(
            serverTotalSeconds: Long,
            serverStreak: Int?,
            serverLastStudyDate: String?,
        ) {
            dataStore.edit { p ->
                if (p[KEY_TOTAL] != null) return@edit // local already authoritative (studied or reset)
                p[KEY_TOTAL] = serverTotalSeconds
                if (serverStreak != null) p[KEY_STREAK] = serverStreak
                if (serverLastStudyDate != null) p[KEY_LAST_STUDY_DATE] = serverLastStudyDate
            }
        }

        /**
         * Reconcile the local authority with the server's post-merge total (M3-03 게스트→Google 이관).
         * The server merge is ADDITIVE — target's post-merge total = pre-existing target total + this
         * device's guest total ([functions/src/merge/merge.ts] `resolveStudytimeTotal`) — so this device's
         * local total only ever reflects ITS OWN portion; after a merge the server total can exceed it.
         * Adopts [serverTotalSeconds] only when it's the LARGER value (never regresses a local total that
         * has since grown further from new sessions) and marks the state synced when it does (the local
         * total now already matches what's on the server, so the next [drain]/push has nothing new to send).
         */
        suspend fun reconcileFromServer(serverTotalSeconds: Long) {
            dataStore.edit { p ->
                val local = p[KEY_TOTAL] ?: 0L
                if (serverTotalSeconds > local) {
                    p[KEY_TOTAL] = serverTotalSeconds
                    p[KEY_UNSYNCED] = false
                }
            }
        }

        /**
         * Zero the local gamification authority for a 누적 기록 초기화 (M3-09, FR-22). Sets `KEY_TOTAL = 0`
         * (non-null → blocks any re-seed, see [seedIfEmpty]), zeroes today/streak, clears the last-study
         * date, and marks the state SYNCED (`KEY_UNSYNCED = false`) so the next [State.unsynced]-gated
         * drain does NOT re-push the pre-reset total onto the freshly-reset server (the monotonic
         * `totalSeconds >=` rule would otherwise ACCEPT such a re-push and revive it). `KEY_SETTLED` is
         * left intact so a late WAQ replay of an already-settled pre-reset session cannot re-accrue.
         *
         * Called BEFORE the `resetMetrics` callable (local-first): local-first + `unsynced=false` +
         * non-null total means neither drain nor seed can revive, regardless of whether/when the server
         * callable lands.
         */
        suspend fun reset() {
            dataStore.edit { p ->
                p[KEY_TOTAL] = 0L
                p.remove(KEY_TODAY_KEY)
                p[KEY_TODAY_SECONDS] = 0L
                p[KEY_STREAK] = 0
                p.remove(KEY_LAST_STUDY_DATE)
                p[KEY_UNSYNCED] = false
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
        private fun decodeIds(encoded: String?): List<String> {
            return encoded?.split("\n")?.filter { it.isNotEmpty() }.orEmpty()
        }

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

package com.jjundev.oneclickeng.feature.gamification

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.feature.gamification.data.StudytimeStore
import com.jjundev.oneclickeng.feature.reminder.ReminderOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Display values for the summary accrual strip after a session is recorded. [todaySecondsBefore]/
 * [streakStatic] drive the M3-06 count-up: the study-time slot rolls [todaySecondsBefore]→[todaySeconds]
 * and the streak stays static on a same-day repeat.
 */
data class AccrualSnapshot(
    val todaySeconds: Long,
    val streak: Int,
    val todaySecondsBefore: Long,
    val streakStatic: Boolean,
)

/**
 * Seam for the studytime layer (M3-05). Owns the write-ahead flow: accrue a completed session into the
 * local [StudytimeStore] (source of truth), push the running total to `gamification/studytime`
 * (idempotent monotonic set), and — once per session — feed the reminder cache the optimistic streak.
 * `point_ledger` (XP) stays on its own Firestore-native fire-and-forget seam (CompletionLedger); this
 * seam is studytime-only, per the A2 decision.
 */
interface StudytimeRepository {
    /**
     * Record a completed session's study time and advance the optimistic streak. Idempotent by
     * `sessionId`: a replay / re-entry returns the current display values without double-accruing.
     * Returns the values for the accrual strip.
     */
    suspend fun recordSession(
        sessionId: String,
        elapsedSeconds: Long,
        dayKey: String,
    ): AccrualSnapshot

    /** First-launch seed of the local authority from the server (no-op once local state exists). */
    suspend fun seedFromServerIfEmpty()

    /**
     * Re-push any write-ahead state that never reached Firestore. Called at app start AND on an
     * offline→online reconnect (M4-04): the reactive [com.jjundev.oneclickeng.core.connectivity.ConnectivityObserver]
     * transition drives it so a device that regains connectivity without a process restart re-syncs
     * without waiting for the next launch.
     */
    suspend fun drain()

    /**
     * Force-reconcile the local studytime authority after a successful guest→Google merge (M3-03). The
     * server-side merge adds this device's (guest) total onto the target account's pre-existing total
     * (`merge.ts` `resolveStudytimeTotal`) — a total this device never otherwise observes. Re-reads the
     * server's post-merge `totalSeconds` and adopts it if larger than the local total. A no-op if signed
     * out or offline; the display may understate the merged total until a later successful call.
     */
    suspend fun reconcileAfterMerge()

    /**
     * 누적 기록 초기화(M3-09, FR-22). Local-first: zero the local authority (StudytimeStore) and the
     * reminder cache mirror FIRST, then call the `resetMetrics` callable to zero the server. Throws if the
     * callable fails — the caller surfaces an error; the local state is already zeroed (user sees the
     * reset) and the server reconciles on a retry (the callable is idempotent). Local-first + a synced,
     * non-null-total local state means neither drain nor seed can revive the pre-reset values regardless of
     * when the server callable lands.
     */
    suspend fun resetMetrics()
}

/**
 * Firestore-backed implementation. Firestore writes are fire-and-forget on [appScope] (a failed push
 * leaves the state unsynced for the next [drain]); the reminder-cache update is best-effort.
 * Mirrors the FirestoreCompletionLedger fire-and-forget seam. The write-ahead queue / drain mechanism
 * itself is new — no prior codebase pattern to copy (built per ADR-0002).
 */
@Singleton
class FirestoreStudytimeRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val functions: FirebaseFunctions,
        private val authRepository: AuthRepository,
        private val store: StudytimeStore,
        private val reminderOrchestrator: ReminderOrchestrator,
        private val appScope: CoroutineScope,
    ) : StudytimeRepository {
        override suspend fun recordSession(
            sessionId: String,
            elapsedSeconds: Long,
            dayKey: String,
        ): AccrualSnapshot {
            val result = store.accrue(sessionId, elapsedSeconds, dayKey)
            val state = result.state
            if (result.changed) {
                pushStudytime(state)
                // Reminder cache mirror — once per session (gated by `changed`). streak/lastStudyDate are
                // the M3-05 optimistic values; the Worker only keys skip decisions on lastStudyDate.
                runCatching { reminderOrchestrator.recordSessionCompleted(state.streak, LocalDate.parse(dayKey)) }
                    .onFailure { Log.d(TAG, "reminder cache update skipped: ${it.message}") }
            }
            return AccrualSnapshot(
                todaySeconds = state.todaySeconds,
                streak = state.streak,
                todaySecondsBefore = result.todaySecondsBefore,
                streakStatic = result.sameDayRepeat,
            )
        }

        override suspend fun seedFromServerIfEmpty() {
            val uid = authRepository.currentUid ?: return
            val local = store.snapshot()
            if (local.totalSeconds > 0 || local.lastStudyDate != null) return // local already authoritative
            @Suppress("TooGenericExceptionCaught")
            try {
                val gamification = firestore.collection(USERS).document(uid).collection(GAMIFICATION)
                val studytime = gamification.document(STUDYTIME).get().await()
                val progress = gamification.document(PROGRESS).get().await()
                store.seedIfEmpty(
                    serverTotalSeconds = studytime.getLong(FIELD_TOTAL_SECONDS) ?: 0L,
                    serverStreak = progress.getLong(FIELD_STREAK)?.toInt(),
                    serverLastStudyDate = progress.getString(FIELD_LAST_STUDY_DATE),
                )
            } catch (e: Exception) {
                Log.d(TAG, "studytime seed skipped (offline/absent): ${e.message}")
            }
        }

        override suspend fun drain() {
            val state = store.snapshot()
            if (state.unsynced && state.todayDayKey != null) pushStudytime(state)
        }

        @Suppress("TooGenericExceptionCaught")
        override suspend fun reconcileAfterMerge() {
            val uid = authRepository.currentUid ?: return
            try {
                val studytime =
                    firestore
                        .collection(USERS).document(uid)
                        .collection(GAMIFICATION).document(STUDYTIME)
                        .get().await()
                store.reconcileFromServer(studytime.getLong(FIELD_TOTAL_SECONDS) ?: 0L)
            } catch (e: Exception) {
                Log.d(TAG, "post-merge studytime reconcile skipped (offline/permission): ${e.message}")
            }
        }

        override suspend fun resetMetrics() {
            // Local-first (revival-proof): zero the local authority + reminder cache before the server call.
            store.reset()
            runCatching { reminderOrchestrator.clearProgressCache() }
                .onFailure { Log.d(TAG, "reminder cache reset skipped: ${it.message}") }
            // Server reset (Admin-only: progress/studytime are rule-protected). Awaited — a failure
            // propagates so the caller can surface an error; local is already zeroed and the callable is
            // idempotent, so a retry reconciles the server.
            functions.getHttpsCallable(FN_RESET_METRICS).call().await()
        }

        // Fire-and-forget idempotent monotonic set; clears the write-ahead flag only on success.
        @Suppress("TooGenericExceptionCaught")
        private fun pushStudytime(state: StudytimeStore.State) {
            val uid = authRepository.currentUid ?: return
            appScope.launch {
                try {
                    firestore
                        .collection(USERS).document(uid)
                        .collection(GAMIFICATION).document(STUDYTIME)
                        .set(
                            mapOf(
                                FIELD_TOTAL_SECONDS to state.totalSeconds,
                                FIELD_TODAY to
                                    mapOf(
                                        FIELD_DAY_KEY to state.todayDayKey,
                                        FIELD_SECONDS to state.todaySeconds,
                                    ),
                                FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                            ),
                            SetOptions.merge(),
                        ).await()
                    store.markSynced()
                } catch (e: Exception) {
                    // Stays unsynced → drain re-pushes on next launch/reconnect (write-ahead recovery).
                    Log.d(TAG, "studytime push skipped (offline/permission): ${e.message}")
                }
            }
        }

        private companion object {
            const val TAG = "StudytimeRepository"
            const val FN_RESET_METRICS = "resetMetrics"
            const val USERS = "users"
            const val GAMIFICATION = "gamification"
            const val STUDYTIME = "studytime"
            const val PROGRESS = "progress"
            const val FIELD_TOTAL_SECONDS = "totalSeconds"
            const val FIELD_TODAY = "today"
            const val FIELD_DAY_KEY = "dayKey"
            const val FIELD_SECONDS = "seconds"
            const val FIELD_UPDATED_AT = "updatedAt"
            const val FIELD_STREAK = "streak"
            const val FIELD_LAST_STUDY_DATE = "lastStudyDate"
        }
    }

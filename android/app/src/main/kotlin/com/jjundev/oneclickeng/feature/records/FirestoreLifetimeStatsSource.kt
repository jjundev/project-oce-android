package com.jjundev.oneclickeng.feature.records

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.feature.gamification.data.StudytimeStore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val SECONDS_PER_MINUTE = 60L

/**
 * Real [LifetimeStatsSource] (M3-05 실데이터 배선 — replaces the former `StubLifetimeStatsSource`). XP and
 * study-days come from the server-authoritative `gamification/progress` document (Functions-only write,
 * firestore-schema.md §5); study minutes come from the LOCAL [StudytimeStore] instead of a second Firestore
 * read, because studytime's client copy is the documented authority (StudytimeStore.kt) and is always at
 * least as fresh as the server (server push is fire-and-forget and can lag).
 */
@Singleton
class FirestoreLifetimeStatsSource
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val authRepository: AuthRepository,
        private val studytimeStore: StudytimeStore,
    ) : LifetimeStatsSource {
        @Suppress("TooGenericExceptionCaught")
        override suspend fun lifetime(): LifetimeStats? {
            val uid = authRepository.currentUid ?: return null
            return try {
                val progress =
                    firestore
                        .collection(USERS).document(uid)
                        .collection(GAMIFICATION).document(PROGRESS)
                        .get().await()
                toLifetimeStats(
                    progressXp = progress.getLong(FIELD_XP),
                    progressStudyDays = progress.getLong(FIELD_STUDY_DAYS),
                    localTotalSeconds = studytimeStore.snapshot().totalSeconds,
                )
            } catch (e: Exception) {
                Log.d(TAG, "lifetime stats read skipped (offline/absent): ${e.message}")
                null
            }
        }

        private companion object {
            const val TAG = "LifetimeStatsSource"
            const val USERS = "users"
            const val GAMIFICATION = "gamification"
            const val PROGRESS = "progress"
            const val FIELD_XP = "xp"
            const val FIELD_STUDY_DAYS = "studyDays"
        }
    }

/** Pure combine — server `progress` fields (nullable: absent for a brand-new, not-yet-completed profile). */
internal fun toLifetimeStats(
    progressXp: Long?,
    progressStudyDays: Long?,
    localTotalSeconds: Long,
): LifetimeStats =
    LifetimeStats(
        xp = (progressXp ?: 0L).toInt(),
        studyMinutes = (localTotalSeconds / SECONDS_PER_MINUTE).toInt(),
        studyDays = (progressStudyDays ?: 0L).toInt(),
    )

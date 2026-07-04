package com.jjundev.oneclickeng.core.auth

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates the guest's `users/{uid}` profile document (M3-01). At guest-start the profile
 * holds only `createdAt`/`updatedAt` (serverTimestamp); `nickname`/`level` are written
 * later by onboarding (M3-02), and `isGuest` is never stored (derived from the token,
 * firestore-schema.md:59).
 */
interface ProfileRepository {
    /**
     * Ensures a profile exists for [uid]. Creates it only when absent and never rewrites
     * `createdAt` (the security rule enforces `createdAt` immutability on update,
     * firestore-schema.md:213). Safe to call on every app start.
     */
    suspend fun ensureProfile(uid: String)

    /**
     * Persists the self-reported onboarding [level] to `users/{uid}.level` (M3-02, FR-2). A
     * merge write so `createdAt` and any other fields survive (the update rule forbids touching
     * `createdAt`, firestore-schema.md:213). The onboarding caller invokes this fire-and-forget on
     * the level tap without gating navigation: Firestore's on-disk write queue persists and retries
     * the mutation across process death, so the value is not discarded even offline — the "폐기 안
     * 함" guarantee rests on that queue, not on awaiting the round-trip. The first session is still
     * forced to `easy`; this stored level is what session #2 onward reads (consumption is M3-08).
     */
    suspend fun saveLevel(
        uid: String,
        level: String,
    )

    /**
     * Reads `users/{uid}.level`, or null when the profile has no level yet (never onboarded). The
     * app-entry gate uses this to route: a returning user with a level goes to Home, an absent
     * level goes to onboarding. On Android, Firestore's default on-disk cache answers this from the
     * last synced snapshot when offline, so a returning-but-offline user still resolves their level
     * rather than being re-onboarded.
     */
    suspend fun readLevel(uid: String): String?

    /**
     * Persists the settings nickname to `users/{uid}.nickname` + `updatedAt` (M3-09, FR-21). A merge
     * write so `createdAt`/`level` survive (the update rule forbids touching `createdAt`,
     * firestore-schema.md:213). Fire-and-forget-safe: Firestore's on-disk write queue persists across
     * process death, so the value is not discarded offline. [nickname] is the already-validated value
     * (1–20 chars trimmed, empty allowed) — server-side length is NOT rule-enforced (client trust
     * boundary; the update rule imposes no field constraint on `nickname`, firestore.rules:9).
     */
    suspend fun saveNickname(
        uid: String,
        nickname: String,
    )

    /** Reads `users/{uid}.nickname`, or null when unset. Firestore's on-disk cache answers offline. */
    suspend fun readNickname(uid: String): String?
}

/**
 * Firestore-backed implementation. Uses a transaction so the read-then-conditional-create
 * is atomic (no get-then-set TOCTOU): concurrent starts cannot both create the document.
 * On an absent doc the write is judged by the `create` rule (`allow create: if owner(uid)`,
 * firestore-schema.md:212); when the doc already exists this is a no-op.
 */
@Singleton
class FirestoreProfileRepository
    @Inject
    constructor(
        private val db: FirebaseFirestore,
    ) : ProfileRepository {
        override suspend fun ensureProfile(uid: String) {
            val ref = db.collection(COLLECTION_USERS).document(uid)
            db.runTransaction<Unit> { txn ->
                if (!txn.get(ref).exists()) {
                    txn.set(
                        ref,
                        mapOf(
                            FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                            FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                        ),
                    )
                }
            }.await()
        }

        override suspend fun saveLevel(
            uid: String,
            level: String,
        ) {
            db.collection(COLLECTION_USERS)
                .document(uid)
                .set(
                    mapOf(
                        FIELD_LEVEL to level,
                        FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                    ),
                    // merge keeps createdAt and any other fields (the update rule forbids touching
                    // createdAt, firestore-schema.md:213). The queued write survives process death.
                    SetOptions.merge(),
                ).await()
        }

        override suspend fun readLevel(uid: String): String? =
            db.collection(COLLECTION_USERS)
                .document(uid)
                .get()
                .await()
                .getString(FIELD_LEVEL)

        override suspend fun saveNickname(
            uid: String,
            nickname: String,
        ) {
            db.collection(COLLECTION_USERS)
                .document(uid)
                .set(
                    mapOf(
                        FIELD_NICKNAME to nickname,
                        FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                    ),
                    // merge keeps createdAt/level (the update rule forbids touching createdAt,
                    // firestore-schema.md:213). The queued write survives process death.
                    SetOptions.merge(),
                ).await()
        }

        override suspend fun readNickname(uid: String): String? =
            db.collection(COLLECTION_USERS)
                .document(uid)
                .get()
                .await()
                .getString(FIELD_NICKNAME)

        private companion object {
            const val COLLECTION_USERS = "users"
            const val FIELD_CREATED_AT = "createdAt"
            const val FIELD_UPDATED_AT = "updatedAt"
            const val FIELD_LEVEL = "level"
            const val FIELD_NICKNAME = "nickname"
        }
    }

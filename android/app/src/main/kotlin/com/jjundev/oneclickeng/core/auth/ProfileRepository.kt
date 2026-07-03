package com.jjundev.oneclickeng.core.auth

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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

        private companion object {
            const val COLLECTION_USERS = "users"
            const val FIELD_CREATED_AT = "createdAt"
            const val FIELD_UPDATED_AT = "updatedAt"
        }
    }

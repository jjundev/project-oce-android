package com.jjundev.oneclickeng.core.auth

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the guest identity. On first launch the app signs in anonymously (FR-1, no login
 * screen); Firebase persists that session to disk, so a relaunch resolves the same UID
 * synchronously and [ensureSignedIn] is a no-op. `isGuest` is NOT tracked here — it is
 * derived from the token claim `firebase.sign_in_provider` per the schema SoT
 * (firestore-schema.md:59), never stored.
 *
 * Kept behind an interface so [FirebaseTokenProvider] and the bootstrap [AppViewModel]
 * depend on the seam, not FirebaseAuth directly.
 */
interface AuthRepository {
    /** current guest UID, or null before the first sign-in completes. */
    val currentUid: String?

    /**
     * Returns the guest UID, signing in anonymously if there is no current user.
     * Idempotent and concurrency-safe: overlapping callers (bootstrap + the per-request
     * `AuthInterceptor`) share one sign-in via single-flight rather than racing two.
     * Throws if anonymous sign-in fails (e.g. offline first launch, or the Anonymous
     * provider is disabled in the Firebase console → `auth/admin-restricted-operation`);
     * callers decide whether to degrade or retry.
     */
    suspend fun ensureSignedIn(): String
}

/**
 * [FirebaseAuth]-backed implementation.
 *
 * [appScope] is the app-wide `SupervisorJob` scope (see `TtsProvideModule.provideAppScope`):
 * the shared sign-in runs there, not on a caller's scope, so cancelling one caller does
 * not abort a sign-in another caller still awaits. The `currentUser` fast path is checked
 * outside the single-flight lock (standard double-check) and again inside the shared block.
 */
@Singleton
class FirebaseAuthRepository
    @Inject
    constructor(
        private val auth: FirebaseAuth,
        appScope: CoroutineScope,
    ) : AuthRepository {
        private val signIn = SingleFlight<String>(appScope)

        override val currentUid: String?
            get() = auth.currentUser?.uid

        override suspend fun ensureSignedIn(): String {
            auth.currentUser?.uid?.let { return it }
            return signIn.run {
                // Re-check inside the shared run: a concurrent caller may have just signed in.
                auth.currentUser?.uid
                    ?: auth.signInAnonymously().await().user?.uid
                    ?: error("Anonymous sign-in returned a null user")
            }
        }
    }

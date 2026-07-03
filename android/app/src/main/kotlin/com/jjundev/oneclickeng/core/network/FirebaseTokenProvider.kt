package com.jjundev.oneclickeng.core.network

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.jjundev.oneclickeng.core.auth.AuthRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Real [TokenProvider] backed by Firebase Anonymous auth (M3-01), replacing the earlier
 * M1 stub. Ensures a guest session exists, then returns the current ID token used as the
 * `/llm` Bearer credential.
 *
 * Any failure is logged and reduced to a null token: the request then goes out
 * unauthenticated and the server answers 401, which the client already handles
 * ([AuthInterceptor]) — the token layer never crashes a request. Offline first-launch and
 * a misconfigured Firebase project (Anonymous provider disabled) are distinguished only in
 * the log; both degrade the same way. Active retry/backoff is out of M3-01 scope (M4-03/04).
 */
class FirebaseTokenProvider
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val auth: FirebaseAuth,
    ) : TokenProvider {
        override suspend fun idToken(): String? =
            runCatching {
                authRepository.ensureSignedIn()
                auth.currentUser?.getIdToken(false)?.await()?.token
            }.getOrElse { throwable ->
                val reason =
                    if (throwable is FirebaseAuthException) {
                        "auth error ${throwable.errorCode}"
                    } else {
                        "network/other (${throwable.javaClass.simpleName})"
                    }
                Log.w(TAG, "ID token unavailable ($reason) — request proceeds unauthenticated", throwable)
                null
            }

        private companion object {
            const val TAG = "FirebaseTokenProvider"
        }
    }

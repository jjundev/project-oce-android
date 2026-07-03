package com.jjundev.oneclickeng.core.network

/**
 * Supplies the Firebase ID token used as the `/llm` Bearer credential
 * (backend-functions.md:49). Kept behind an interface so the token source can evolve
 * without touching call sites: M1 consumed a token without real auth, and M3-01 wired
 * the real Firebase Anonymous sign-in behind `FirebaseTokenProvider`.
 */
fun interface TokenProvider {
    /** current ID token, or null when no auth is available (request goes out unauthenticated). */
    suspend fun idToken(): String?
}

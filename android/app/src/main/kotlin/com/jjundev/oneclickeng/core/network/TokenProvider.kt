package com.jjundev.oneclickeng.core.network

/**
 * Supplies the Firebase ID token used as the `/llm` Bearer credential
 * (backend-functions.md:49). Kept behind an interface so M1 can inject a dev-harness
 * token (per ratified B3, PRD.md:363 — M1 entry is a build-flag harness, not real auth)
 * while the real Firebase Anonymous sign-in lands in M3-01. M1-05 only *consumes* a
 * token; standing up Firebase Auth is M0-02's job.
 */
fun interface TokenProvider {
    /** current ID token, or null when no auth is available (request goes out unauthenticated). */
    suspend fun idToken(): String?
}

/**
 * Placeholder token source for M1. Returns null until the M1-09 dev harness or M0-02
 * Firebase wiring supplies a real token. Deliberately does NOT touch Firebase — that
 * dependency is not present in the app yet (owned by M0-02).
 */
class StubTokenProvider : TokenProvider {
    override suspend fun idToken(): String? = null
}

package com.jjundev.oneclickeng.core.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single-flight de-duplicator: while one invocation of [run]'s work is in flight, any
 * concurrent caller shares that same [Deferred] instead of starting a second one. This
 * is the concurrency guard for anonymous sign-in (M3-01, grill-review 4/9) — the app
 * bootstrap ([AppViewModel]) and the per-request `AuthInterceptor` (which calls
 * `TokenProvider.idToken()` under `runBlocking` on OkHttp's thread) can both reach
 * `ensureSignedIn()` before `currentUser` is set; without single-flight that races two
 * `signInAnonymously()` calls.
 *
 * The shared work runs on [scope], not the caller's coroutine, so cancelling one caller
 * (e.g. a ViewModel whose scope closes) does not cancel the sign-in the other caller is
 * still awaiting. [scope] is expected to carry a `SupervisorJob` so a failed run does not
 * tear the scope down.
 *
 * **Failure reset (grill-review F1):** the in-flight reference is cleared in a `finally`
 * on *both* success and failure. On failure this lets the next caller retry the work
 * (matching decision 10's "next call re-attempts lazily"); on success the next caller
 * short-circuits before reaching here anyway (e.g. `currentUser` is now non-null).
 */
class SingleFlight<T>(
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var inFlight: Deferred<T>? = null

    suspend fun run(block: suspend () -> T): T {
        val deferred =
            mutex.withLock {
                inFlight ?: scope.async { block() }.also { inFlight = it }
            }
        return try {
            deferred.await()
        } finally {
            mutex.withLock {
                if (inFlight === deferred) inFlight = null
            }
        }
    }
}

package com.jjundev.oneclickeng.core.time

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monotonic elapsed-time seam (M4-01f) for latency measurement — never wall-clock, so NTP/timezone
 * jumps can't produce a negative duration. Real impl wraps [SystemClock.elapsedRealtime]; tests
 * supply `FakeElapsedClock` (test sources) to assert exact `latency_ms` deterministically.
 */
interface ElapsedClock {
    fun nowMillis(): Long
}

/**
 * Always-`0L` stub. Safe as a constructor-default for hand-built test/preview coordinator
 * instances that don't care about latency — mirrors
 * [com.jjundev.oneclickeng.core.connectivity.OnlineConnectivityObserver]: a pure-Kotlin fallback
 * with zero Android dependency, distinct from the real Hilt-bound impl below. **Do not call
 * [SystemElapsedClock] as a constructor default** — none of the 5 latency
 * coordinator test files run under Robolectric, so a real `SystemClock` call would crash them.
 */
object NoOpElapsedClock : ElapsedClock {
    override fun nowMillis(): Long = 0L
}

/** Real dispatch: wraps [SystemClock.elapsedRealtime], Hilt-bound via [TimeModule]. */
@Singleton
class SystemElapsedClock
    @Inject
    constructor() : ElapsedClock {
        override fun nowMillis(): Long = SystemClock.elapsedRealtime()
    }

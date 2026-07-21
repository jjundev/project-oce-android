package com.jjundev.oneclickeng.core.time

/** Deterministic clock for latency assertions (repo convention = fakes, not mockk). */
class FakeElapsedClock(var now: Long = 0L) : ElapsedClock {
    override fun nowMillis(): Long = now

    fun advance(byMs: Long) {
        now += byMs
    }
}

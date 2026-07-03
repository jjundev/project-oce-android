package com.jjundev.oneclickeng.core.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the anonymous sign-in concurrency guard (M3-01, grill-review 4/9 Blocker + F1).
 * The [SupervisorJob] scope mirrors the production app scope so a failing run does not
 * tear down the scope, and [UnconfinedTestDispatcher] runs coroutines eagerly to the
 * first suspension so the "both callers reached the shared work" state is observable
 * without manual scheduler stepping.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SingleFlightTest {
    @Test
    fun `concurrent callers share a single execution`() =
        runTest(UnconfinedTestDispatcher()) {
            val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val sf = SingleFlight<Int>(scope)
            var calls = 0
            val gate = CompletableDeferred<Unit>()
            val block: suspend () -> Int = {
                calls++
                gate.await()
                42
            }

            val a = async { sf.run(block) }
            val b = async { sf.run(block) }

            // Both callers have reached the in-flight Deferred but the work is still gated.
            assertEquals("block must run exactly once while in flight", 1, calls)

            gate.complete(Unit)
            assertEquals(42, a.await())
            assertEquals(42, b.await())
            assertEquals("second caller must not start a second run", 1, calls)

            scope.cancel()
        }

    @Test
    fun `failure clears the in-flight ref so the next call retries`() =
        runTest(UnconfinedTestDispatcher()) {
            val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val sf = SingleFlight<Int>(scope)
            var calls = 0
            val block: suspend () -> Int = {
                calls++
                if (calls == 1) error("boom")
                7
            }

            val first = runCatching { sf.run(block) }
            assertTrue("first run must surface the failure", first.isFailure)

            // In-flight reference cleared on failure -> the next call re-runs the work.
            assertEquals(7, sf.run(block))
            assertEquals(2, calls)

            scope.cancel()
        }

    @Test
    fun `each sequential call runs fresh after completion`() =
        runTest(UnconfinedTestDispatcher()) {
            val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val sf = SingleFlight<Int>(scope)
            var calls = 0
            val block: suspend () -> Int = { ++calls }

            assertEquals(1, sf.run(block))
            assertEquals(2, sf.run(block))

            scope.cancel()
        }
}

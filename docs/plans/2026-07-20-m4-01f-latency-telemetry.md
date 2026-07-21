# M4-01f (Phase 2, Slice 3b) — Latency Telemetry (5 of 6 ops) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fire the `*_latency_ms` auxiliary telemetry series for **5 of the 6** timed operations — `script_gen`, `speaking_analyze`, `slim`, `deep`, `summary` — with an exact millisecond duration, from the real start/terminal transition points of their coordinators. `tts_latency_ms` is explicitly deferred to a follow-up slice (see "Not in this plan" below).

**Architecture:** A new monotonic **`ElapsedClock`** seam (`core/time`) replaces ad-hoc timing so tests can assert an exact `latency_ms` under virtual/fake time. A new shared **`LatencyAnalytics`** seam (`feature/session/analytics`, one method covering all 5 operations, dispatching through the existing `AnalyticsSink`) is the single place that maps `(operation, outcome, latencyMs)` to a GA4 event. Both are injected into the 5 already-Hilt-singleton coordinators (`DialogueGenerationCoordinator`, `SpeakingAnalysisCoordinator`, `SlimFeedbackCoordinator`, `DeepFeedbackCoordinator`, `SummaryCoordinator`) as **trailing constructor params with safe defaults**, so the ~54 existing hand-built test/preview construction sites across this codebase keep compiling unchanged (see Global Constraints — this is NOT the "update every call site" pattern from earlier slices; read the note before Task 3).

**Tech Stack:** Kotlin 2.1.20, Hilt (KSP), Firebase Analytics via `AnalyticsSink` (M4-01a), JUnit4 + kotlinx-coroutines-test (`UnconfinedTestDispatcher` + `runTest`/`runCurrent`). No Robolectric in any of the 5 touched coordinator test files (see Global Constraints — this drives the clock-default design).

**Scope:** Phase 2, Slice 3b of M4-01 (issue [M4-01](../../issues/M4-01-analytics-instrumentation.md)). Product decisions below were confirmed by the user via `AskUserQuestion` before this plan was written — do not re-litigate them.

**Product decisions (confirmed):**
1. **`tts_latency_ms` — OUT OF SCOPE for this plan.** `TtsPlaybackCoordinator`'s shared `obtainAudio`/`synthesize` core with join semantics + zero-latency cache hits needs its own scoping pass. Do not touch `TtsPlaybackCoordinator` in this plan.
2. **`summary_latency_ms` measures the FIRST attempt only** (`start()` → that attempt's first terminal settle). `retry()`-triggered re-attempts (which re-issue `launchAttempt` per failed section) are NOT measured.
3. **Coverage this plan:** `script_gen`, `speaking_analyze`, `slim`, `deep`, `summary` — 5 events. `tts` is a separate follow-up slice.
4. **Event schema:** `outcome ∈ {successful, failed, canceled}` (not every operation emits all 3 — see Event Decision Table for which apply where); duration is carried as a `latency_ms` **param** (not the event's bare numeric value).
5. **Clock source:** `SystemClock.elapsedRealtime()` (monotonic, Android-framework) for the **real, Hilt-bound** implementation. See Global Constraints for why the constructor-default fallback used by hand-built test sites is a *different*, pure-Kotlin no-op — this is a test-safety detail, not a change to decision #5's production behavior.

## Global Constraints

- **minSdk 26**, JDK 17, Kotlin 2.1.20. No mockk/Mockito — hand-written fakes only (repo convention).
- **GA4 snake_case** ids/params. **PII boundary:** only `operation`/`outcome` enums + `latency_ms` (long) — never free text.
- **Reuse `AnalyticsSink`** as the single dispatch path.
- **detekt `MaxLineLength` = 120 on BOTH main and test sources; `ReturnCount` max = 2.** After each task run `./scripts/verify-android.sh :app:detekt` and confirm the files YOU touched report zero findings (ignore the ~30 pre-existing `OceThemeColorContractTest.kt` findings — unrelated).
- **Verify with `./scripts/verify-android.sh :app:testDebugUnitTest --tests "..."`** then `./scripts/verify-android.sh :app:compileDebugKotlin`. Do NOT run the full `check`/`testReleaseUnitTest` (pre-existing unrelated failures: ~30 detekt + ~9 release-variant Roborazzi).
- **Critical discovery — none of the 5 touched coordinator test files use `@RunWith(RobolectricTestRunner)`.** They are plain JUnit4 JVM tests (confirmed by grep: `DialogueGenerationCoordinatorTest.kt`, `SpeakingAnalysisCoordinatorTest.kt`, `SlimFeedbackCoordinatorTest.kt`, `DeepFeedbackCoordinatorTest.kt`, `SummaryCoordinatorTest.kt` all lack the annotation). Any real `android.os.*` framework call executed by these tests throws `RuntimeException: ... not mocked` (the stock Android stub jar). This is why `ElapsedClock`'s constructor-default value must NOT be the real `SystemClock`-backed impl — see Task 1's `NoOpElapsedClock`. This mirrors the existing precedent: `ConnectivityObserver`'s constructor default is `OnlineConnectivityObserver` (a pure Kotlin stub), while the real Hilt binding is `AndroidConnectivityObserver` (`core/connectivity/ConnectivityModule.kt`) — same split, same reason.
- **Trailing-defaulted-param strategy (read before Task 3).** Earlier M4-01 slices' "positional-ctor breakage" gotcha (update every call site) applied when a NEW REQUIRED param was inserted. This plan instead appends `clock: ElapsedClock = NoOpElapsedClock` and `latencyAnalytics: LatencyAnalytics = NoOpLatencyAnalytics()` as the LAST two constructor params on each of the 5 coordinators. Kotlin lets positional call sites that don't supply trailing defaulted params compile unchanged. **Do not reorder existing params, and do not insert the new ones anywhere but the end** — inserting them earlier breaks every positional call site exactly like the earlier gotcha describes.
- **Same-package top-level `private class` test fakes collide by name.** Each new dedicated test file below declares its own fakes with a `Latency`-prefixed name (`LatencyFakeDialogueStream`, etc.) — grep the target package first if you add more, to avoid redeclaration errors against sibling test files.
- **`FakeSavedCardRepository`** (`feature/session/saved/FakeSavedCardRepository.kt`), **`NoOpSavedCardAnalytics`** (main sources, `feature/session/analytics`), and **`FakeSummarySaveSettingsRepository`** (`core/settings/FakeSummarySaveSettingsRepository.kt`) are shared, non-private test fixtures — reuse them directly, do not redeclare.
- **Event-id authority** is `docs/ux/analytics-events.md` §10 and `docs/ux/dialogue-learning-flow.md` §13 (line ~294–320). This plan finalizes the 5 net-new event names (`script_gen_latency_ms`, `slim_latency_ms`, `deep_latency_ms`, `summary_latency_ms`) and reuses the already-pinned `speaking_analyze_latency_ms`.

## Event Decision Table

| Event | Params | Fires at | `outcome` values used | Source |
|---|---|---|---|---|
| `script_gen_latency_ms` | `outcome`→str, `latency_ms`→long | `DialogueGenerationCoordinator`: start=`launchAttempt()`; end=first `Turn`→Ready transition (successful) / `fail(token)` (failed) | `successful`, `failed` | net-new, this plan finalizes the name |
| `speaking_analyze_latency_ms` | same | `SpeakingAnalysisCoordinator`: start=`analyze()` entry; end=terminal `_state.value` assignment (`Result`/`Empty`→successful, `null` response→failed) / `reset()` while `Analyzing` (canceled) | `successful`, `failed`, `canceled` | Pinned id `dialogue-learning-flow.md:310`; 3-way split pinned `dialogue-learning-flow.md:320` |
| `slim_latency_ms` | same | `SlimFeedbackCoordinator`: start=`launchAttempt()` (covers both `start()` and `retry()`); end=all 3 sections settled (`afterSection`/`failLoadingSections`) | `successful`, `failed` | net-new |
| `deep_latency_ms` | same | `DeepFeedbackCoordinator`: start=`beginAttempt()`; end=`readyState()` (successful) / `Error` via `settleOnClose` (failed) / explicit `cancel()` while `Loading` (canceled) | `successful`, `failed`, `canceled` | net-new — `Canceled` is an existing distinct terminal state, a clean anchor |
| `summary_latency_ms` | same | `SummaryCoordinator`: start=`launchAttempt(sections = null)` (the FIRST attempt only, per decision #2); end=`applyDone`/`onQuotaExceeded`/`failLoadingSections` for that SAME token | `successful`, `failed` | net-new, first-attempt-only |

**Outcome-computation notes (simplifications, deliberate — these are an auxiliary/observability series, not a strict pass/fail gate per `analytics-events.md` §10):**
- **`speaking_analyze`:** a completed round-trip with a blank transcript (`Empty`) still counts as `successful` — the network call worked; only a `null` response (network/timeout/exception) is `failed`. **Known gap (reviewer-flagged, accepted):** if `analyze()` is called again while a prior analysis is still in flight, the superseded attempt's token guard silently drops it — no `canceled` log fires for it (only an explicit `reset()` mid-flight logs `canceled`). Acceptable because the mic loop always `reset()`s before re-analyzing; revisit if that invariant ever changes.
- **`slim`:** `failed` only if the attempt had to force-fail at least one section via `failLoadingSections` (stream closed/timeout without full delivery); if every section that was `Loading` at attempt-start arrived normally, it's `successful` — even if a still-`Failed` section from a PRIOR attempt is sitting there sticky (retry only re-measures the retried section's own round trip).
- **`summary`:** `failed` if the first attempt's pre-gate quota block fires (`quota=true`, zero sections arrived) OR all 3 sections end `Failed`; otherwise `successful` (a normal `applyDone`, even with some individually-failed sections — that's already separately captured by the existing `summary_partial_failure` event).

## Not in this plan (follow-up slice)

- `tts_latency_ms` (`TtsPlaybackCoordinator`) — needs its own scope decision (see the superseded handoff doc, decision #1) once this slice's `ElapsedClock`/`LatencyAnalytics` seams exist to reuse.
- GA4 DebugView manual verification + `analytics-events.md` §10 back-fill — see Manual Checkpoint at the end.

---

## Task 1: `ElapsedClock` seam (`core/time`)

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/time/ElapsedClock.kt`
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/time/TimeModule.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/time/FakeElapsedClock.kt` (test double, not a `Test` class itself)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/time/FakeElapsedClockTest.kt`

**Interfaces:**
- Produces: `interface ElapsedClock { fun nowMillis(): Long }`; `object NoOpElapsedClock : ElapsedClock` (always returns `0L`, constructor-default-safe, no Android dependency); `class SystemElapsedClock : ElapsedClock` (real Hilt-bound impl, `SystemClock.elapsedRealtime()`); `class FakeElapsedClock(var now: Long = 0L) : ElapsedClock` with `fun advance(byMs: Long)` (test double, consumed by Tasks 3–7).

- [ ] **Step 1: Write the failing test for `FakeElapsedClock`**

```kotlin
// android/app/src/test/kotlin/com/jjundev/oneclickeng/core/time/FakeElapsedClockTest.kt
package com.jjundev.oneclickeng.core.time

import org.junit.Assert.assertEquals
import org.junit.Test

class FakeElapsedClockTest {
    @Test
    fun `nowMillis reflects the initial value and each advance`() {
        val clock = FakeElapsedClock(now = 100L)
        assertEquals(100L, clock.nowMillis())

        clock.advance(250L)

        assertEquals(350L, clock.nowMillis())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*.FakeElapsedClockTest"`
Expected: FAIL — `Unresolved reference: FakeElapsedClock` (neither `ElapsedClock.kt` nor `FakeElapsedClock.kt` exist yet).

- [ ] **Step 3: Write the seam + fake**

```kotlin
// android/app/src/main/kotlin/com/jjundev/oneclickeng/core/time/ElapsedClock.kt
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
```

```kotlin
// android/app/src/main/kotlin/com/jjundev/oneclickeng/core/time/TimeModule.kt
package com.jjundev.oneclickeng.core.time

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the monotonic clock seam to its real impl (M4-01f). */
@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {
    @Binds
    @Singleton
    abstract fun bindElapsedClock(impl: SystemElapsedClock): ElapsedClock
}
```

```kotlin
// android/app/src/test/kotlin/com/jjundev/oneclickeng/core/time/FakeElapsedClock.kt
package com.jjundev.oneclickeng.core.time

/** Deterministic clock for latency assertions (repo convention = fakes, not mockk). */
class FakeElapsedClock(var now: Long = 0L) : ElapsedClock {
    override fun nowMillis(): Long = now

    fun advance(byMs: Long) {
        now += byMs
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*.FakeElapsedClockTest"`
Expected: PASS

- [ ] **Step 5: detekt check**

Run: `./scripts/verify-android.sh :app:detekt`
Expected: zero findings in the 3 files touched this task.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/core/time/ElapsedClock.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/core/time/TimeModule.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/core/time/FakeElapsedClock.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/core/time/FakeElapsedClockTest.kt
git commit -m "feat(analytics): add ElapsedClock monotonic-time seam"
```

---

## Task 2: `LatencyAnalytics` seam + Firebase impl + recording fake + contract test

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/analytics/LatencyAnalytics.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/analytics/SessionFunnelModule.kt`
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/analytics/RecordingLatencyAnalytics.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/analytics/LatencyAnalyticsDispatchTest.kt`

**Interfaces:**
- Consumes: `AnalyticsSink.log(event: String, params: Map<String, Any>)` (existing, `core/analytics/AnalyticsSink.kt`).
- Produces: `interface LatencyAnalytics { fun latency(operation: String, outcome: String, latencyMs: Long) }` with companion constants `OPERATION_SCRIPT_GEN`, `OPERATION_SPEAKING_ANALYZE`, `OPERATION_SLIM`, `OPERATION_DEEP`, `OPERATION_SUMMARY`, `OUTCOME_SUCCESSFUL`, `OUTCOME_FAILED`, `OUTCOME_CANCELED`; `NoOpLatencyAnalytics`; `FirebaseLatencyAnalytics(sink: AnalyticsSink)`. Consumed by Tasks 3–7.

- [ ] **Step 1: Write the failing contract test**

```kotlin
// android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/analytics/LatencyAnalyticsDispatchTest.kt
package com.jjundev.oneclickeng.feature.session.analytics

import com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink
import org.junit.Assert.assertEquals
import org.junit.Test

class LatencyAnalyticsDispatchTest {
    private val sink = RecordingAnalyticsSink()
    private val analytics = FirebaseLatencyAnalytics(sink)

    @Test
    fun `logs script_gen_latency_ms with outcome and latency_ms`() {
        analytics.latency(LatencyAnalytics.OPERATION_SCRIPT_GEN, LatencyAnalytics.OUTCOME_SUCCESSFUL, 850L)
        assertEquals(
            RecordingAnalyticsSink.Event(
                "script_gen_latency_ms",
                mapOf("outcome" to "successful", "latency_ms" to 850L),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `logs speaking_analyze_latency_ms for the pinned operation id`() {
        analytics.latency(LatencyAnalytics.OPERATION_SPEAKING_ANALYZE, LatencyAnalytics.OUTCOME_FAILED, 1200L)
        assertEquals(
            RecordingAnalyticsSink.Event(
                "speaking_analyze_latency_ms",
                mapOf("outcome" to "failed", "latency_ms" to 1200L),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `logs slim_latency_ms`() {
        analytics.latency(LatencyAnalytics.OPERATION_SLIM, LatencyAnalytics.OUTCOME_SUCCESSFUL, 400L)
        assertEquals(
            RecordingAnalyticsSink.Event("slim_latency_ms", mapOf("outcome" to "successful", "latency_ms" to 400L)),
            sink.events.single(),
        )
    }

    @Test
    fun `logs deep_latency_ms with canceled outcome`() {
        analytics.latency(LatencyAnalytics.OPERATION_DEEP, LatencyAnalytics.OUTCOME_CANCELED, 300L)
        assertEquals(
            RecordingAnalyticsSink.Event("deep_latency_ms", mapOf("outcome" to "canceled", "latency_ms" to 300L)),
            sink.events.single(),
        )
    }

    @Test
    fun `logs summary_latency_ms`() {
        analytics.latency(LatencyAnalytics.OPERATION_SUMMARY, LatencyAnalytics.OUTCOME_SUCCESSFUL, 2000L)
        assertEquals(
            RecordingAnalyticsSink.Event("summary_latency_ms", mapOf("outcome" to "successful", "latency_ms" to 2000L)),
            sink.events.single(),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*.LatencyAnalyticsDispatchTest"`
Expected: FAIL — `Unresolved reference: LatencyAnalytics` / `FirebaseLatencyAnalytics`.

- [ ] **Step 3: Write the seam**

```kotlin
// android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/analytics/LatencyAnalytics.kt
package com.jjundev.oneclickeng.feature.session.analytics

import com.jjundev.oneclickeng.core.analytics.AnalyticsSink
import javax.inject.Inject

/**
 * Shared latency telemetry seam (M4-01f, `analytics-events.md` §10) for the `*_latency_ms` series —
 * auxiliary round-trip timing, NOT a 5대 지표 numerator/denominator. One method covers every timed
 * operation so there is a single dispatch impl + one contract test for all operation ids. PII:
 * enum/duration only.
 */
interface LatencyAnalytics {
    fun latency(operation: String, outcome: String, latencyMs: Long)

    companion object {
        const val OPERATION_SCRIPT_GEN = "script_gen"
        const val OPERATION_SPEAKING_ANALYZE = "speaking_analyze"
        const val OPERATION_SLIM = "slim"
        const val OPERATION_DEEP = "deep"
        const val OPERATION_SUMMARY = "summary"

        const val OUTCOME_SUCCESSFUL = "successful"
        const val OUTCOME_FAILED = "failed"
        const val OUTCOME_CANCELED = "canceled"
    }
}

/** Default no-op binding (test/fallback). */
class NoOpLatencyAnalytics
    @Inject
    constructor() : LatencyAnalytics {
        override fun latency(operation: String, outcome: String, latencyMs: Long) = Unit
    }

/** Firebase dispatch via the shared [AnalyticsSink] (M4-01a). Event id = `"${operation}_latency_ms"`. */
class FirebaseLatencyAnalytics
    @Inject
    constructor(
        private val sink: AnalyticsSink,
    ) : LatencyAnalytics {
        override fun latency(operation: String, outcome: String, latencyMs: Long) =
            sink.log("${operation}_latency_ms", mapOf("outcome" to outcome, "latency_ms" to latencyMs))
    }
```

```kotlin
// android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/analytics/RecordingLatencyAnalytics.kt
package com.jjundev.oneclickeng.feature.session.analytics

/** Records latency calls for emit-site behavior tests (repo convention = fakes). */
class RecordingLatencyAnalytics : LatencyAnalytics {
    data class Call(val operation: String, val outcome: String, val latencyMs: Long)

    val calls = mutableListOf<Call>()

    override fun latency(operation: String, outcome: String, latencyMs: Long) {
        calls += Call(operation, outcome, latencyMs)
    }
}
```

Extend the existing shared analytics DI module with the new binding:

```kotlin
// android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/analytics/SessionFunnelModule.kt
// ADD inside the existing `abstract class SessionFunnelModule { ... }`, after bindMicPermissionAnalytics:
    @Binds
    @Singleton
    abstract fun bindLatencyAnalytics(impl: FirebaseLatencyAnalytics): LatencyAnalytics
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*.LatencyAnalyticsDispatchTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: detekt + full compile check**

Run: `./scripts/verify-android.sh :app:detekt :app:compileDebugKotlin`
Expected: zero findings in touched files, compile succeeds (Hilt module change alone doesn't touch any coordinator yet).

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/analytics/LatencyAnalytics.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/analytics/SessionFunnelModule.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/analytics/RecordingLatencyAnalytics.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/analytics/LatencyAnalyticsDispatchTest.kt
git commit -m "feat(analytics): add LatencyAnalytics dispatch seam, Firebase impl, recording fake"
```

---

## Task 3: `script_gen_latency_ms` — `DialogueGenerationCoordinator`

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationCoordinator.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationScriptGenLatencyTest.kt` (new file)

**Interfaces:**
- Consumes: `ElapsedClock.nowMillis()`, `LatencyAnalytics.latency(operation, outcome, latencyMs)` (Tasks 1–2). `FakeElapsedClock`, `RecordingLatencyAnalytics` (test doubles).
- Produces: no new public API — internal instrumentation only.

- [ ] **Step 1: Write the failing tests**

```kotlin
// test/.../feature/session/dialogue/DialogueGenerationScriptGenLatencyTest.kt
package com.jjundev.oneclickeng.feature.session.dialogue

import com.jjundev.oneclickeng.core.network.DialogueEvent
import com.jjundev.oneclickeng.core.network.DialogueRequest
import com.jjundev.oneclickeng.core.network.DialogueStream
import com.jjundev.oneclickeng.core.network.DialogueTurn
import com.jjundev.oneclickeng.core.time.FakeElapsedClock
import com.jjundev.oneclickeng.feature.session.analytics.LatencyAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.RecordingLatencyAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class LatencyFakeDialogueStream : DialogueStream {
    private val channels = mutableListOf<Channel<DialogueEvent>>()

    override fun events(request: DialogueRequest): Flow<DialogueEvent> {
        val channel = Channel<DialogueEvent>(Channel.UNLIMITED)
        channels += channel
        return channel.consumeAsFlow()
    }

    fun push(event: DialogueEvent) = channels.last().trySend(event)

    fun end() = channels.last().close()
}

@OptIn(ExperimentalCoroutinesApi::class)
class DialogueGenerationScriptGenLatencyTest {
    private fun TestScope.coordScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `first turn logs script_gen_latency_ms successful with exact elapsed ms`() =
        runTest {
            val stream = LatencyFakeDialogueStream()
            val clock = FakeElapsedClock(now = 1_000L)
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                DialogueGenerationCoordinator(stream, coordScope(), clock = clock, latencyAnalytics = latency)

            coordinator.start("easy", "coffee", 5, firstSession = true)
            runCurrent()
            clock.advance(750L)
            stream.push(DialogueEvent.Turn(DialogueTurn(ko = "안녕", en = "Hi", role = "model")))
            runCurrent()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("script_gen", LatencyAnalytics.OUTCOME_SUCCESSFUL, 750L)),
                latency.calls,
            )
        }

    @Test
    fun `second turn does not re-log latency`() =
        runTest {
            val stream = LatencyFakeDialogueStream()
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                DialogueGenerationCoordinator(stream, coordScope(), clock = clock, latencyAnalytics = latency)

            coordinator.start("easy", "coffee", 5, firstSession = true)
            runCurrent()
            stream.push(DialogueEvent.Turn(DialogueTurn(ko = "안녕", en = "Hi", role = "model")))
            runCurrent()
            stream.push(DialogueEvent.Turn(DialogueTurn(ko = "잘가", en = "Bye", role = "model")))
            runCurrent()

            assertEquals(1, latency.calls.size)
        }

    @Test
    fun `done before any turn logs script_gen_latency_ms failed`() =
        runTest {
            val stream = LatencyFakeDialogueStream()
            val clock = FakeElapsedClock(now = 500L)
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                DialogueGenerationCoordinator(stream, coordScope(), clock = clock, latencyAnalytics = latency)

            coordinator.start("easy", "coffee", 5, firstSession = true)
            runCurrent()
            clock.advance(1_500L)
            stream.push(DialogueEvent.Done("ok"))
            runCurrent()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("script_gen", LatencyAnalytics.OUTCOME_FAILED, 1_500L)),
                latency.calls,
            )
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*.DialogueGenerationScriptGenLatencyTest"`
Expected: FAIL to compile — `No value passed for parameter 'clock'` (constructor doesn't accept `clock`/`latencyAnalytics` yet).

- [ ] **Step 3: Instrument the coordinator**

In `DialogueGenerationCoordinator.kt`, add imports:

```kotlin
import com.jjundev.oneclickeng.core.time.ElapsedClock
import com.jjundev.oneclickeng.core.time.NoOpElapsedClock
import com.jjundev.oneclickeng.feature.session.analytics.LatencyAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.NoOpLatencyAnalytics
```

Append 2 trailing constructor params (after `connectivity`, keep everything else unchanged):

```kotlin
        private val connectivity: ConnectivityObserver = OnlineConnectivityObserver,
        private val clock: ElapsedClock = NoOpElapsedClock,
        private val latencyAnalytics: LatencyAnalytics = NoOpLatencyAnalytics(),
    ) {
```

Add 2 private fields near the other per-attempt accumulators:

```kotlin
        private var attemptStartMs = 0L
        private var scriptGenLatencyLogged = false
```

In `launchAttempt(request)`, right after `clearAccumulators()`:

```kotlin
            attemptStartMs = clock.nowMillis()
            scriptGenLatencyLogged = false
```

Add a private helper near `fail`:

```kotlin
        private fun logScriptGenLatency(outcome: String) {
            if (scriptGenLatencyLogged) return
            scriptGenLatencyLogged = true
            latencyAnalytics.latency(LatencyAnalytics.OPERATION_SCRIPT_GEN, outcome, clock.nowMillis() - attemptStartMs)
        }
```

In `onEvent`'s `is DialogueEvent.Turn ->` branch, after `_state.value = readySnapshot()`:

```kotlin
                    logScriptGenLatency(LatencyAnalytics.OUTCOME_SUCCESSFUL)
```

In `fail(token)`, right after the `if (token != sessionToken) return` guard and `watchdogJob?.cancel()`:

```kotlin
            logScriptGenLatency(LatencyAnalytics.OUTCOME_FAILED)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*.DialogueGenerationScriptGenLatencyTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Run the FULL existing dialogue test suite (regression check for the trailing-default strategy)**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "com.jjundev.oneclickeng.feature.session.dialogue.*"`
Expected: PASS — all pre-existing tests in `DialogueGenerationCoordinatorTest.kt`, `DialogueGenerationViewModelTest.kt`, `DialogueGenerationFunnelAnalyticsTest.kt`, `WaitQuizShownEndedEmitTest.kt` still compile and pass unchanged (they never supply `clock`/`latencyAnalytics`, so they get `NoOpElapsedClock`/`NoOpLatencyAnalytics()` — zero behavior change, zero crash risk since neither touches Android framework).

- [ ] **Step 6: detekt**

Run: `./scripts/verify-android.sh :app:detekt`
Expected: zero findings in touched files.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationCoordinator.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationScriptGenLatencyTest.kt
git commit -m "feat(analytics): fire script_gen_latency_ms from DialogueGenerationCoordinator"
```

---

## Task 4: `speaking_analyze_latency_ms` — `SpeakingAnalysisCoordinator`

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/speaking/SpeakingAnalysisCoordinator.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/speaking/SpeakingAnalysisLatencyTest.kt` (new file)

**Interfaces:**
- Consumes: `ElapsedClock`, `LatencyAnalytics` (Tasks 1–2).

- [ ] **Step 1: Write the failing tests**

```kotlin
// android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/speaking/SpeakingAnalysisLatencyTest.kt
package com.jjundev.oneclickeng.feature.session.speaking

import com.jjundev.oneclickeng.core.audio.RecordingResult
import com.jjundev.oneclickeng.core.network.LlmApi
import com.jjundev.oneclickeng.core.network.SpeakingRequest
import com.jjundev.oneclickeng.core.network.SpeakingResponse
import com.jjundev.oneclickeng.core.network.TtsRequest
import com.jjundev.oneclickeng.core.network.TtsResponse
import com.jjundev.oneclickeng.core.time.FakeElapsedClock
import com.jjundev.oneclickeng.feature.session.analytics.LatencyAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.RecordingLatencyAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private val PCM = byteArrayOf(0, 1, 2, 3)

private fun captured() = RecordingResult.Captured(pcm = PCM, sampleRate = 16000, durationMs = 1000)

private class LatencyFakeLlmApi(
    var response: SpeakingResponse? = SpeakingResponse(transcript = "hello", feedbackMessage = "좋아요"),
    var delayMs: Long = 0,
) : LlmApi {
    override suspend fun tts(body: TtsRequest): TtsResponse = error("unused")

    override suspend fun speaking(body: SpeakingRequest): SpeakingResponse {
        if (delayMs > 0) delay(delayMs)
        return response ?: throw java.io.IOException("boom")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SpeakingAnalysisLatencyTest {
    private fun TestScope.coordScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `successful analysis logs speaking_analyze_latency_ms successful`() =
        runTest {
            val clock = FakeElapsedClock(now = 200L)
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                SpeakingAnalysisCoordinator(
                    LatencyFakeLlmApi(),
                    coordScope(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            clock.advance(900L)
            coordinator.analyze(captured(), "s1")
            advanceUntilIdle()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("speaking_analyze", LatencyAnalytics.OUTCOME_SUCCESSFUL, 900L)),
                latency.calls,
            )
        }

    @Test
    fun `network failure logs speaking_analyze_latency_ms failed`() =
        runTest {
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                SpeakingAnalysisCoordinator(
                    LatencyFakeLlmApi(response = null),
                    coordScope(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            clock.advance(300L)
            coordinator.analyze(captured(), "s1")
            advanceUntilIdle()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("speaking_analyze", LatencyAnalytics.OUTCOME_FAILED, 300L)),
                latency.calls,
            )
        }

    @Test
    fun `reset while analyzing logs speaking_analyze_latency_ms canceled`() =
        runTest {
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                SpeakingAnalysisCoordinator(
                    LatencyFakeLlmApi(delayMs = 10_000),
                    coordScope(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            coordinator.analyze(captured(), "s1")
            clock.advance(150L)
            coordinator.reset()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("speaking_analyze", LatencyAnalytics.OUTCOME_CANCELED, 150L)),
                latency.calls,
            )
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*.SpeakingAnalysisLatencyTest"`
Expected: FAIL to compile — constructor doesn't accept `clock`/`latencyAnalytics` yet.

- [ ] **Step 3: Instrument the coordinator**

Add imports (same 4 as Task 3, adjusted package unaffected). Append trailing params:

```kotlin
        private val api: LlmApi,
        private val scope: CoroutineScope,
        private val clock: ElapsedClock = NoOpElapsedClock,
        private val latencyAnalytics: LatencyAnalytics = NoOpLatencyAnalytics(),
    ) {
```

Add a field:

```kotlin
        private var analyzeStartMs = 0L
```

In `analyze(captured, sessionId)`, right after `val token = ++sessionToken`:

```kotlin
            analyzeStartMs = clock.nowMillis()
```

Right before the terminal `_state.value = when { ... }` assignment (inside the coroutine, after the second `if (token != sessionToken) return@launch`):

```kotlin
                    val outcome =
                        if (response == null) LatencyAnalytics.OUTCOME_FAILED else LatencyAnalytics.OUTCOME_SUCCESSFUL
                    latencyAnalytics.latency(
                        LatencyAnalytics.OPERATION_SPEAKING_ANALYZE,
                        outcome,
                        clock.nowMillis() - analyzeStartMs,
                    )
```

In `reset()`, capture whether an analysis was in flight BEFORE mutating state, and log after:

```kotlin
        fun reset() {
            val wasAnalyzing = _state.value is SpeakingAnalysisState.Analyzing
            sessionToken++
            currentJob?.cancel()
            currentJob = null
            lastTranscript = null
            _state.value = SpeakingAnalysisState.Idle
            if (wasAnalyzing) {
                latencyAnalytics.latency(
                    LatencyAnalytics.OPERATION_SPEAKING_ANALYZE,
                    LatencyAnalytics.OUTCOME_CANCELED,
                    clock.nowMillis() - analyzeStartMs,
                )
            }
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*.SpeakingAnalysisLatencyTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Regression check**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "com.jjundev.oneclickeng.feature.session.speaking.*"`
Expected: PASS — pre-existing `SpeakingAnalysisCoordinatorTest.kt` unaffected.

- [ ] **Step 6: detekt**

Run: `./scripts/verify-android.sh :app:detekt`
Expected: zero findings in touched files.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/speaking/SpeakingAnalysisCoordinator.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/speaking/SpeakingAnalysisLatencyTest.kt
git commit -m "feat(analytics): fire speaking_analyze_latency_ms from SpeakingAnalysisCoordinator"
```

---

## Task 5: `slim_latency_ms` — `SlimFeedbackCoordinator`

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackCoordinator.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackLatencyTest.kt` (new file)

**Interfaces:**
- Consumes: `ElapsedClock`, `LatencyAnalytics` (Tasks 1–2).

- [ ] **Step 1: Write the failing tests**

```kotlin
// android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackLatencyTest.kt
package com.jjundev.oneclickeng.feature.session.feedback

import com.jjundev.oneclickeng.core.network.FeedbackEvent
import com.jjundev.oneclickeng.core.network.FeedbackRequest
import com.jjundev.oneclickeng.core.network.FeedbackStream
import com.jjundev.oneclickeng.core.network.GrammarDto
import com.jjundev.oneclickeng.core.network.CorrectedSentenceDto
import com.jjundev.oneclickeng.core.network.NaturalExpressionDto
import com.jjundev.oneclickeng.core.network.ReasonDto
import com.jjundev.oneclickeng.core.network.WritingScoreDto
import com.jjundev.oneclickeng.core.time.FakeElapsedClock
import com.jjundev.oneclickeng.feature.session.analytics.LatencyAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.RecordingLatencyAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class LatencyFakeFeedbackStream : FeedbackStream {
    private val channels = mutableListOf<Channel<FeedbackEvent>>()

    override fun events(request: FeedbackRequest): Flow<FeedbackEvent> {
        val channel = Channel<FeedbackEvent>(Channel.UNLIMITED)
        channels += channel
        return channel.consumeAsFlow()
    }

    fun push(event: FeedbackEvent) = channels.last().trySend(event)

    fun end() = channels.last().close()
}

private fun writingScore() = FeedbackEvent.Section.WritingScore(WritingScoreDto(85, "잘했어요!"))

private fun grammar() = FeedbackEvent.Section.Grammar(GrammarDto(CorrectedSentenceDto(emptyList()), "좋아요."))

private fun natural() = FeedbackEvent.Section.NaturalExpression(NaturalExpressionDto(emptyList(), ReasonDto("k", "d")))

@OptIn(ExperimentalCoroutinesApi::class)
class SlimFeedbackLatencyTest {
    private fun TestScope.coordScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private fun SlimFeedbackCoordinator.begin() =
        start(
            sessionId = "s1",
            koreanPrompt = "커피 주세요",
            userEnglish = "One coffee",
            referenceEnglish = "ref",
            level = "normal",
        )

    @Test
    fun `all 3 sections arriving logs slim_latency_ms successful`() =
        runTest {
            val stream = LatencyFakeFeedbackStream()
            val clock = FakeElapsedClock(now = 10L)
            val latency = RecordingLatencyAnalytics()
            val coordinator = SlimFeedbackCoordinator(stream, coordScope(), clock = clock, latencyAnalytics = latency)

            coordinator.begin()
            runCurrent()
            clock.advance(120L)
            stream.push(writingScore())
            stream.push(grammar())
            stream.push(natural())
            runCurrent()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("slim", LatencyAnalytics.OUTCOME_SUCCESSFUL, 120L)),
                latency.calls,
            )
        }

    @Test
    fun `stream closing before all sections arrive logs slim_latency_ms failed`() =
        runTest {
            val stream = LatencyFakeFeedbackStream()
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator = SlimFeedbackCoordinator(stream, coordScope(), clock = clock, latencyAnalytics = latency)

            coordinator.begin()
            runCurrent()
            clock.advance(200L)
            stream.push(writingScore())
            stream.end()
            advanceUntilIdle()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("slim", LatencyAnalytics.OUTCOME_FAILED, 200L)),
                latency.calls,
            )
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*.SlimFeedbackLatencyTest"`
Expected: FAIL to compile — constructor doesn't accept `clock`/`latencyAnalytics` yet.

- [ ] **Step 3: Instrument the coordinator**

Add the 4 imports. Append trailing params:

```kotlin
        private val stream: FeedbackStream,
        private val scope: CoroutineScope,
        private val clock: ElapsedClock = NoOpElapsedClock,
        private val latencyAnalytics: LatencyAnalytics = NoOpLatencyAnalytics(),
    ) {
```

Add 3 fields near the other accumulators:

```kotlin
        private var attemptStartMs = 0L
        private var slimLatencyLogged = false
        private var attemptHadForcedFailure = false
```

In `launchAttempt()`, right after `val token = ++sessionToken`:

```kotlin
            attemptStartMs = clock.nowMillis()
            slimLatencyLogged = false
            attemptHadForcedFailure = false
```

Add a helper near `failLoadingSections`:

```kotlin
        private fun maybeLogSlimLatency() {
            if (slimLatencyLogged || anyLoading) return
            slimLatencyLogged = true
            val outcome =
                if (attemptHadForcedFailure) LatencyAnalytics.OUTCOME_FAILED else LatencyAnalytics.OUTCOME_SUCCESSFUL
            latencyAnalytics.latency(LatencyAnalytics.OPERATION_SLIM, outcome, clock.nowMillis() - attemptStartMs)
        }
```

At the end of `afterSection(token)` (after the existing `if (anyLoading) { armWatchdog(token) } else { watchdogJob?.cancel() }`):

```kotlin
            maybeLogSlimLatency()
```

In `failLoadingSections(token)`, set the flag on each forced fail and call the helper at the end:

```kotlin
        private fun failLoadingSections(token: Long) {
            if (token != sessionToken) return
            watchdogJob?.cancel()
            if (writingScore is SectionState.Loading) {
                attemptsWs++
                writingScore = SectionState.Failed(attemptsWs)
                attemptHadForcedFailure = true
            }
            if (grammar is SectionState.Loading) {
                attemptsGr++
                grammar = SectionState.Failed(attemptsGr)
                attemptHadForcedFailure = true
            }
            if (natural is SectionState.Loading) {
                attemptsNat++
                natural = SectionState.Failed(attemptsNat)
                attemptHadForcedFailure = true
            }
            emit()
            maybeLogSlimLatency()
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*.SlimFeedbackLatencyTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: Regression check**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "com.jjundev.oneclickeng.feature.session.feedback.SlimFeedbackCoordinatorTest"`
Expected: PASS unchanged.

- [ ] **Step 6: detekt**

Run: `./scripts/verify-android.sh :app:detekt`
Expected: zero findings in touched files.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackCoordinator.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackLatencyTest.kt
git commit -m "feat(analytics): fire slim_latency_ms from SlimFeedbackCoordinator"
```

---

## Task 6: `deep_latency_ms` — `DeepFeedbackCoordinator`

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackCoordinator.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackLatencyTest.kt` (new file)

**Interfaces:**
- Consumes: `ElapsedClock`, `LatencyAnalytics` (Tasks 1–2); `FakeSavedCardRepository`, `NoOpSavedCardAnalytics` (existing shared fixtures).

- [ ] **Step 1: Write the failing tests**

```kotlin
// android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackLatencyTest.kt
package com.jjundev.oneclickeng.feature.session.feedback

import com.jjundev.oneclickeng.core.network.ConceptualBridgeDto
import com.jjundev.oneclickeng.core.network.DeepFeedbackStream
import com.jjundev.oneclickeng.core.network.FeedbackDeepEvent
import com.jjundev.oneclickeng.core.network.FeedbackDeepRequest
import com.jjundev.oneclickeng.core.network.ParaphraseItemDto
import com.jjundev.oneclickeng.core.network.ParaphrasingDto
import com.jjundev.oneclickeng.core.network.ToneLevelDto
import com.jjundev.oneclickeng.core.network.ToneStyleDto
import com.jjundev.oneclickeng.core.network.VennCircleDto
import com.jjundev.oneclickeng.core.network.VennDto
import com.jjundev.oneclickeng.core.network.VennIntersectionDto
import com.jjundev.oneclickeng.core.time.FakeElapsedClock
import com.jjundev.oneclickeng.feature.session.analytics.LatencyAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.NoOpSavedCardAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.RecordingLatencyAnalytics
import com.jjundev.oneclickeng.feature.session.saved.FakeSavedCardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class LatencyFakeDeepStream : DeepFeedbackStream {
    private val channels = mutableListOf<Channel<FeedbackDeepEvent>>()

    override fun events(request: FeedbackDeepRequest): Flow<FeedbackDeepEvent> {
        val channel = Channel<FeedbackDeepEvent>(Channel.UNLIMITED)
        channels += channel
        return channel.consumeAsFlow()
    }

    fun push(event: FeedbackDeepEvent) = channels.last().trySend(event)

    fun end() = channels.last().close()
}

private fun conceptualBridge() =
    FeedbackDeepEvent.Section.ConceptualBridge(
        ConceptualBridgeDto(
            literalTranslation = "직역",
            explanation = "설명",
            venn =
                VennDto(
                    "안내",
                    VennCircleDto("get", listOf("얻다")),
                    VennCircleDto("order", listOf("주문")),
                    VennIntersectionDto(listOf("받다")),
                ),
        ),
    )

private fun toneStyle() =
    FeedbackDeepEvent.Section.ToneStyle(ToneStyleDto(2, (0..4).map { ToneLevelDto(it, "s$it", "번역$it") }))

private fun paraphrasing() =
    FeedbackDeepEvent.Section.Paraphrasing(ParaphrasingDto(listOf(ParaphraseItemDto(1, "Beginner", "p1", "번역1"))))

@OptIn(ExperimentalCoroutinesApi::class)
class DeepFeedbackLatencyTest {
    private fun TestScope.coordScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private fun DeepFeedbackCoordinator.begin() =
        start(
            sessionId = "s1",
            turnIndex = 0,
            koreanPrompt = "커피 주세요",
            userEnglish = "One coffee",
            referenceEnglish = "ref",
            level = "normal",
        )

    @Test
    fun `all 3 blocks arriving logs deep_latency_ms successful`() =
        runTest {
            val stream = LatencyFakeDeepStream()
            val clock = FakeElapsedClock(now = 5L)
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                DeepFeedbackCoordinator(
                    stream,
                    FakeSavedCardRepository(),
                    coordScope(),
                    NoOpSavedCardAnalytics(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            coordinator.begin()
            runCurrent()
            clock.advance(600L)
            stream.push(conceptualBridge())
            stream.push(toneStyle())
            stream.push(paraphrasing())
            runCurrent()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("deep", LatencyAnalytics.OUTCOME_SUCCESSFUL, 600L)),
                latency.calls,
            )
        }

    @Test
    fun `stream closing before all blocks arrive logs deep_latency_ms failed`() =
        runTest {
            val stream = LatencyFakeDeepStream()
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                DeepFeedbackCoordinator(
                    stream,
                    FakeSavedCardRepository(),
                    coordScope(),
                    NoOpSavedCardAnalytics(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            coordinator.begin()
            runCurrent()
            clock.advance(700L)
            stream.push(conceptualBridge())
            stream.end()
            advanceUntilIdle()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("deep", LatencyAnalytics.OUTCOME_FAILED, 700L)),
                latency.calls,
            )
        }

    @Test
    fun `cancel while Loading logs deep_latency_ms canceled`() =
        runTest {
            val stream = LatencyFakeDeepStream()
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                DeepFeedbackCoordinator(
                    stream,
                    FakeSavedCardRepository(),
                    coordScope(),
                    NoOpSavedCardAnalytics(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            coordinator.begin()
            runCurrent()
            clock.advance(90L)
            coordinator.cancel()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("deep", LatencyAnalytics.OUTCOME_CANCELED, 90L)),
                latency.calls,
            )
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*.DeepFeedbackLatencyTest"`
Expected: FAIL to compile — constructor doesn't accept `clock`/`latencyAnalytics` yet.

- [ ] **Step 3: Instrument the coordinator**

Add the 4 imports. Append trailing params (after `savedCardAnalytics`):

```kotlin
        private val savedCardAnalytics: SavedCardAnalytics,
        private val clock: ElapsedClock = NoOpElapsedClock,
        private val latencyAnalytics: LatencyAnalytics = NoOpLatencyAnalytics(),
    ) {
```

Add 2 fields:

```kotlin
        private var attemptStartMs = 0L
        private var deepLatencyLogged = false
```

In `beginAttempt()`, right after `val token = ++sessionToken`:

```kotlin
            attemptStartMs = clock.nowMillis()
            deepLatencyLogged = false
```

Add a helper near `readyState`:

```kotlin
        private fun logDeepLatency(outcome: String) {
            if (deepLatencyLogged) return
            deepLatencyLogged = true
            latencyAnalytics.latency(LatencyAnalytics.OPERATION_DEEP, outcome, clock.nowMillis() - attemptStartMs)
        }
```

In `afterSection(token)`, in the `allArrived` branch:

```kotlin
        private fun afterSection(token: Long) {
            if (token != sessionToken) return
            if (allArrived) {
                watchdogJob?.cancel()
                _state.value = readyState()
                logDeepLatency(LatencyAnalytics.OUTCOME_SUCCESSFUL)
            } else {
                _state.value = DeepFeedbackState.Loading(cb, tone, para)
                armWatchdog(token)
            }
        }
```

In `settleOnClose(token)`:

```kotlin
        private fun settleOnClose(token: Long) {
            if (token != sessionToken) return
            watchdogJob?.cancel()
            if (isTerminalNeutral) return
            if (allArrived) {
                _state.value = readyState()
                logDeepLatency(LatencyAnalytics.OUTCOME_SUCCESSFUL)
            } else {
                _state.value = DeepFeedbackState.Error(cb, tone, para)
                logDeepLatency(LatencyAnalytics.OUTCOME_FAILED)
            }
        }
```

In `cancel()`, capture in-flight status BEFORE mutating state:

```kotlin
        fun cancel() {
            val wasInFlight = _state.value is DeepFeedbackState.Loading
            sessionToken++
            currentJob?.cancel()
            watchdogJob?.cancel()
            currentJob = null
            clearAccumulators()
            _bookmarkedLevels.value = emptySet()
            _state.value = DeepFeedbackState.Canceled
            if (wasInFlight) logDeepLatency(LatencyAnalytics.OUTCOME_CANCELED)
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*.DeepFeedbackLatencyTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Regression check**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "com.jjundev.oneclickeng.feature.session.feedback.*"`
Expected: PASS — `DeepFeedbackCoordinatorTest.kt`, `DeepFeedbackSavedCardAnalyticsTest.kt`, `SlimFeedbackCoordinatorTest.kt`, and Task 5's test all still pass.

- [ ] **Step 6: detekt**

Run: `./scripts/verify-android.sh :app:detekt`
Expected: zero findings in touched files.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackCoordinator.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackLatencyTest.kt
git commit -m "feat(analytics): fire deep_latency_ms from DeepFeedbackCoordinator"
```

---

## Task 7: `summary_latency_ms` (first attempt only) — `SummaryCoordinator`

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinator.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryLatencyTest.kt` (new file)

**Interfaces:**
- Consumes: `ElapsedClock`, `LatencyAnalytics` (Tasks 1–2); `FakeSummarySaveSettingsRepository` (shared fixture — its `current()` returns synchronously with no suspension point, so under `UnconfinedTestDispatcher` `beginAttempt`'s `scope.launch` runs eagerly to `launchAttempt` in the same `start()` call — no `runCurrent()` needed to reach the first `launchAttempt`).

- [ ] **Step 1: Write the failing tests**

```kotlin
// android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryLatencyTest.kt
package com.jjundev.oneclickeng.feature.session.summary

import com.jjundev.oneclickeng.core.network.SectionOutcome
import com.jjundev.oneclickeng.core.network.SummaryEvent
import com.jjundev.oneclickeng.core.network.SummaryRequest
import com.jjundev.oneclickeng.core.network.SummaryStream
import com.jjundev.oneclickeng.core.settings.FakeSummarySaveSettingsRepository
import com.jjundev.oneclickeng.core.time.FakeElapsedClock
import com.jjundev.oneclickeng.feature.gamification.AccrualSnapshot
import com.jjundev.oneclickeng.feature.gamification.StudytimeRepository
import com.jjundev.oneclickeng.feature.reminder.ReminderOrchestrator
import com.jjundev.oneclickeng.feature.reminder.ReminderPromptDecision
import com.jjundev.oneclickeng.feature.reminder.ReminderRunResult
import com.jjundev.oneclickeng.feature.reminder.data.ReminderConfig
import com.jjundev.oneclickeng.feature.session.analytics.LatencyAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.NoOpSavedCardAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.NoOpSessionFunnelAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.RecordingLatencyAnalytics
import com.jjundev.oneclickeng.feature.session.feedback.TurnFeedbackBuffer
import com.jjundev.oneclickeng.feature.session.saved.FakeSavedCardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

private class LatencyFakeSummaryStream : SummaryStream {
    private val channels = mutableListOf<Channel<SummaryEvent>>()

    override fun events(request: SummaryRequest): Flow<SummaryEvent> {
        val channel = Channel<SummaryEvent>(Channel.UNLIMITED)
        channels += channel
        return channel.consumeAsFlow()
    }

    fun push(event: SummaryEvent) = channels.last().trySend(event)

    fun end() = channels.last().close()
}

private class LatencyFakeBookmarkSource : BookmarkSource {
    override suspend fun latestSentences(sessionId: String, limit: Int): List<BookmarkCard> = emptyList()
}

private class LatencyFakeLedger : CompletionLedger {
    override fun recordCompletion(sessionId: String, difficulty: String, modeId: String) = Unit
}

private class LatencyFakeStudytimeRepository : StudytimeRepository {
    override suspend fun recordSession(sessionId: String, elapsedSeconds: Long, dayKey: String) =
        AccrualSnapshot(todaySeconds = 0, streak = 0, todaySecondsBefore = 0, streakStatic = false)

    override suspend fun seedFromServerIfEmpty() = Unit
    override suspend fun drain() = Unit
    override suspend fun reconcileAfterMerge() = Unit
    override suspend fun resetMetrics() = Unit
}

private class LatencyFakeReminderOrchestrator : ReminderOrchestrator {
    override val config: Flow<ReminderConfig> = MutableStateFlow(ReminderConfig.DISABLED)
    override suspend fun evaluateOptInPrompt(): ReminderPromptDecision = ReminderPromptDecision.DoNotShow
    override suspend fun acceptOptIn() = Unit
    override suspend fun dismissOptIn() = Unit
    override suspend fun enableReminder() = Unit
    override suspend fun disableReminder() = Unit
    override suspend fun setReminderTime(hour: Int, minute: Int) = Unit
    override suspend fun markPermissionAsked() = Unit
    override suspend fun repairSchedule() = Unit
    override suspend fun handleTimezoneChanged() = Unit
    override suspend fun runDueReminder(): ReminderRunResult = ReminderRunResult.DisabledNoOp
    override suspend fun recordSessionCompleted(streak: Int, lastStudyDate: LocalDate) = Unit
    override suspend fun recordSavedReviewText(text: String) = Unit
    override suspend fun clearProgressCache() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryLatencyTest {
    private fun TestScope.coordScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private fun store(): SessionTurnBufferStore =
        SessionTurnBufferStore().apply {
            startSession("s1")
            record(
                "커피 주세요",
                "One coffee",
                TurnFeedbackBuffer(slimScore = 80, correctedText = "a", naturalExpression = "b"),
            )
        }

    private fun coordinator(
        scope: CoroutineScope,
        stream: LatencyFakeSummaryStream,
        clock: FakeElapsedClock,
        latency: RecordingLatencyAnalytics,
    ) = SummaryCoordinator(
            stream,
            store(),
            LatencyFakeBookmarkSource(),
            LatencyFakeLedger(),
            FakeSavedCardRepository(),
            FakeSummarySaveSettingsRepository(),
            LatencyFakeStudytimeRepository(),
            LatencyFakeReminderOrchestrator(),
            scope,
            NoOpSessionFunnelAnalytics(),
            NoOpSavedCardAnalytics(),
            clock,
            latency,
        )

    private fun done() = SummaryEvent.Done(SectionOutcome.Ok, SectionOutcome.Ok, SectionOutcome.Ok)

    @Test
    fun `first attempt done logs summary_latency_ms successful`() =
        runTest {
            val stream = LatencyFakeSummaryStream()
            val clock = FakeElapsedClock(now = 50L)
            val latency = RecordingLatencyAnalytics()
            val coordinator = coordinator(coordScope(), stream, clock, latency)

            coordinator.start(
                sessionId = "s1",
                difficulty = "normal",
                modeId = "default",
                accrual = AccrualStrip(streakDays = 1, xp = 10),
            )
            runCurrent()
            clock.advance(1_800L)
            stream.push(done())
            runCurrent()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("summary", LatencyAnalytics.OUTCOME_SUCCESSFUL, 1_800L)),
                latency.calls,
            )
        }

    @Test
    fun `first attempt stream closing with nothing arrived logs summary_latency_ms failed`() =
        runTest {
            val stream = LatencyFakeSummaryStream()
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator = coordinator(coordScope(), stream, clock, latency)

            coordinator.start(
                sessionId = "s1",
                difficulty = "normal",
                modeId = "default",
                accrual = AccrualStrip(streakDays = 1, xp = 10),
            )
            runCurrent()
            clock.advance(2_500L)
            stream.end()
            advanceUntilIdle()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("summary", LatencyAnalytics.OUTCOME_FAILED, 2_500L)),
                latency.calls,
            )
        }

    @Test
    fun `retry after first attempt does not log a second summary_latency_ms`() =
        runTest {
            val stream = LatencyFakeSummaryStream()
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator = coordinator(coordScope(), stream, clock, latency)

            coordinator.start(
                sessionId = "s1",
                difficulty = "normal",
                modeId = "default",
                accrual = AccrualStrip(streakDays = 1, xp = 10),
            )
            runCurrent()
            stream.push(SummaryEvent.Done(SectionOutcome.Failed, SectionOutcome.Ok, SectionOutcome.Ok))
            runCurrent()
            assertEquals(1, latency.calls.size)

            coordinator.retry(SummarySection.Expression)
            runCurrent()
            stream.push(done())
            runCurrent()

            assertEquals(1, latency.calls.size)
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*.SummaryLatencyTest"`
Expected: FAIL to compile — constructor doesn't accept `clock`/`latencyAnalytics` yet.

- [ ] **Step 3: Instrument the coordinator**

Add the 4 imports. Append trailing params (after `savedCardAnalytics`):

```kotlin
        private val savedCardAnalytics: SavedCardAnalytics,
        private val clock: ElapsedClock = NoOpElapsedClock,
        private val latencyAnalytics: LatencyAnalytics = NoOpLatencyAnalytics(),
    ) {
```

Add 3 fields near `partialFailureLogged`:

```kotlin
        // summary_latency_ms — first attempt only (M4-01f, decision #2). null token = no first attempt yet.
        private var firstAttemptToken: Long? = null
        private var firstAttemptStartMs = 0L
        private var firstAttemptLatencyLogged = false
```

In `start(...)`, reset the 3 fields alongside the other per-start resets (near `partialFailureLogged = false`):

```kotlin
            firstAttemptToken = null
            firstAttemptLatencyLogged = false
```

In `launchAttempt(sections)`, right after `val token = ++sessionToken`:

```kotlin
            if (sections == null) {
                firstAttemptToken = token
                firstAttemptStartMs = clock.nowMillis()
            }
```

Add a helper near `maybeLogPartialFailure`:

```kotlin
        // summary_latency_ms — only the FIRST attempt's token logs (decision #2); retries are silent.
        private fun maybeLogFirstAttemptLatency(token: Long, forcedFailure: Boolean) {
            if (token != firstAttemptToken || firstAttemptLatencyLogged) return
            firstAttemptLatencyLogged = true
            val outcome =
                if (forcedFailure) LatencyAnalytics.OUTCOME_FAILED else LatencyAnalytics.OUTCOME_SUCCESSFUL
            latencyAnalytics.latency(
                LatencyAnalytics.OPERATION_SUMMARY,
                outcome,
                clock.nowMillis() - firstAttemptStartMs,
            )
        }
```

In `applyDone(token, done)`, at the very end (after the existing `emit()`):

```kotlin
            val allFailed = SummarySection.entries.all { sectionState(it) is SummarySectionState.Failed }
            maybeLogFirstAttemptLatency(token, forcedFailure = allFailed)
```

In `onQuotaExceeded(token)`, at the very end (after the existing `emit()`). **Reviewer-flagged fix:** the mid-stream quota branch (`anyArrived == true`) already has ≥1 section that arrived normally — that's a `successful` round trip cut short by a business rule, not a technical failure; only the pre-gate branch (nothing arrived) is `failed`:

```kotlin
            maybeLogFirstAttemptLatency(token, forcedFailure = !anyArrived)
```

In `failLoadingSections(token)`, at the very end (after the existing `emit()`):

```kotlin
            maybeLogFirstAttemptLatency(token, forcedFailure = true)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*.SummaryLatencyTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Regression check**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "com.jjundev.oneclickeng.feature.session.summary.*"`
Expected: PASS — `SummaryCoordinatorTest.kt`, `SummaryPartialFailureAnalyticsTest.kt`, `SummarySavedCardAnalyticsTest.kt`, `SummaryViewModelSessionCompleteTest.kt` all unaffected.

- [ ] **Step 6: detekt**

Run: `./scripts/verify-android.sh :app:detekt`
Expected: zero findings in touched files.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinator.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryLatencyTest.kt
git commit -m "feat(analytics): fire summary_latency_ms (first attempt only) from SummaryCoordinator"
```

---

## Task 8: Full-suite regression + compile verification

**Files:** none (verification-only task).

- [ ] **Step 1: Run every touched package's test suite together**

Run:
```bash
./scripts/verify-android.sh :app:testDebugUnitTest --tests "com.jjundev.oneclickeng.core.time.*" \
  --tests "com.jjundev.oneclickeng.feature.session.analytics.*" \
  --tests "com.jjundev.oneclickeng.feature.session.dialogue.*" \
  --tests "com.jjundev.oneclickeng.feature.session.speaking.*" \
  --tests "com.jjundev.oneclickeng.feature.session.feedback.*" \
  --tests "com.jjundev.oneclickeng.feature.session.summary.*"
```
Expected: PASS, all suites.

- [ ] **Step 2: Full debug compile**

Run: `./scripts/verify-android.sh :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: detekt across the whole diff**

Run: `./scripts/verify-android.sh :app:detekt`
Expected: only the ~30 pre-existing `OceThemeColorContractTest.kt` findings remain; zero new findings.

- [ ] **Step 4: No commit** (verification task; nothing to stage).

---

## Manual Checkpoint (final — human, GA4 DebugView)

- [ ] Enable DebugView: `adb shell setprop debug.firebase.analytics.app com.jjundev.oneclickeng`, launch a debug build.
- [ ] Trigger a real session through generation → speaking → slim → deep ("더 보기") → summary; confirm `script_gen_latency_ms`, `speaking_analyze_latency_ms`, `slim_latency_ms`, `deep_latency_ms`, `summary_latency_ms` each fire once per operation with a sane `latency_ms` and correct `outcome`.
- [ ] Back-fill `docs/ux/analytics-events.md` §10 with the 4 finalized net-new event names (`script_gen_latency_ms`, `slim_latency_ms`, `deep_latency_ms`, `summary_latency_ms`) and confirm the `outcome` enum/param key against what actually appears in DebugView.
- [ ] This clears every item on M4-01's overall DebugView checkpoint EXCEPT `tts_latency_ms` (separate follow-up slice) and the earlier slices' back-fills already tracked in their own plans.

## Follow-up (separate plan): `tts_latency_ms`

`TtsPlaybackCoordinator`'s `obtainAudio`/`synthesize` core has join semantics (a `prefetch` and a later `playTurn` for the same `(text, gender)` key share one `Deferred`) and zero-latency cache hits — this needs its own scope decision (measure only real `synthesize()` round-trips vs. `playTurn` end-to-end) before a plan can be written. Reuse `ElapsedClock`/`LatencyAnalytics` from this slice; no new seam needed.

# M4-01g (Phase 2, Slice 3b follow-up) — tts_latency_ms — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fire `tts_latency_ms` — the 6th and final operation in the M4-01 `*_latency_ms` series — from the one place a real TTS network round-trip actually happens, without disturbing `TtsPlaybackCoordinator`'s cache/join/prefetch/warm-up behavior.

**Architecture:** Reuses the two seams M4-01f already shipped on this branch — `ElapsedClock` (`core/time/ElapsedClock.kt`) and `LatencyAnalytics` (`feature/session/analytics/LatencyAnalytics.kt`) — no new seam infrastructure. Adds one constant (`OPERATION_TTS`) to the existing `LatencyAnalytics` companion, then wraps `TtsPlaybackCoordinator`'s private `synthesize()` — the single function where a real `/llm` network call happens — in a small measuring wrapper called only from the one call site that represents a genuine, non-cached, non-duplicate attempt (`obtainAudio`'s `scope.async` block). `TtsPlaybackCoordinator` gets the same trailing-defaulted-constructor-param treatment (`clock`, `latencyAnalytics`) as the 5 coordinators M4-01f already instrumented, so its ~51 existing test/preview construction sites keep compiling unchanged.

**Tech Stack:** Kotlin 2.1.20, Hilt (KSP), Firebase Analytics via `AnalyticsSink` (M4-01a), JUnit4 + kotlinx-coroutines-test (`UnconfinedTestDispatcher` + `runTest`/`runCurrent`/`advanceUntilIdle`). No Robolectric in `TtsPlaybackCoordinatorTest.kt` or any sibling test file that constructs `TtsPlaybackCoordinator` (confirmed by grep) — this is why `clock`'s constructor default must be `NoOpElapsedClock`, never `SystemElapsedClock`, exactly as in M4-01f.

**Scope:** Phase 2, Slice 3b follow-up of M4-01 (issue [M4-01](../../issues/M4-01-analytics-instrumentation.md)). This is the LAST of the 6 `*_latency_ms` operations — after this plan, M4-01's only remaining work is the human GA4 DebugView checkpoint + `analytics-events.md` §10 back-fill (shared across all 6 operations, see Manual Checkpoint at the end).

**Product decisions (confirmed via `AskUserQuestion` before this plan was written):**
1. **Measurement boundary: instrument `synthesize()` only**, not `obtainAudio()` and not `playTurn()` end-to-end. `obtainAudio()` returns instantly on a cache hit or by joining an already-in-flight `Deferred` — neither path makes a network call. `synthesize()` is the ONLY function that ever calls `api.tts(...)`, and because of `obtainAudio`'s join semantics (`inFlight[key] ?: scope.async { synthesize(...) }`), it is invoked **at most once per cache key per attempt** no matter how many callers (`prefetch`/`awaitWarm`/`playFromServer`) end up awaiting the same `Deferred` — so wrapping `synthesize()`'s one real call site needs no double-fire guard at all (unlike Deep/Summary in M4-01f).
2. **`warmUpModel()`'s preheat synthesis is EXCLUDED.** It calls `synthesize()` directly (bypassing `obtainAudio`/cache entirely, by design — see its KDoc) with a fixed throwaway string (`"Hi"`), on an app-lifecycle cadence unrelated to any real dialogue turn. Mixing that into per-turn TTS latency stats would make the metric harder to interpret. Implementation: a new private wrapper `synthesizeMeasured()` calls-and-times `synthesize()`, and is called ONLY from `obtainAudio`'s `scope.async` block; `warmUpModel()` keeps calling `synthesize()` directly, unmeasured, unchanged.
3. **No `canceled` outcome for `tts`.** The only cancellation path that could reach an in-flight `synthesize()` call is `clearCache()`'s `inFlight.values.forEach { it.cancel() }` (screen exit while a prefetch is mid-flight) — a narrow, low-value case. Matches the majority pattern already established by `script_gen`/`slim`/`summary` in M4-01f (which also emit only `successful`/`failed`). A cancelled attempt simply logs nothing (consistent with how those coordinators handle abandoned attempts too).

## Global Constraints

- **minSdk 26**, JDK 17, Kotlin 2.1.20. No mockk/Mockito — hand-written fakes only.
- **GA4 snake_case** ids/params. **PII boundary:** only `outcome` enum + `latency_ms` (long).
- **detekt `MaxLineLength` = 120 on BOTH main and test sources; `ReturnCount` max = 2.** After each task run `./scripts/verify-android.sh :app:detekt` and confirm the files YOU touched report zero findings.
- **Verify with `./scripts/verify-android.sh :app:testDebugUnitTest --tests "..."`** then `./scripts/verify-android.sh :app:compileDebugKotlin`. Do NOT run the full `check`/`testReleaseUnitTest` (pre-existing unrelated failures, same as M4-01f).
- **Trailing-defaulted-param strategy (same as M4-01f, read before Task 2).** Append `clock: ElapsedClock = NoOpElapsedClock` and `latencyAnalytics: LatencyAnalytics = NoOpLatencyAnalytics()` as the LAST two constructor params on `TtsPlaybackCoordinator` (after the existing `scope`). Do NOT reorder existing params or insert the new ones anywhere but the end — there are **~51 existing positional construction sites** across `TtsPlaybackCoordinatorTest.kt`, `DialogueGenerationViewModelTest.kt`, `DialogueGenerationFunnelAnalyticsTest.kt`, and `WaitQuizShownEndedEmitTest.kt` that must keep compiling unchanged.
- **`NoOpElapsedClock` (not `SystemElapsedClock`) is the safe constructor default** — none of the files above run under Robolectric, and a real `SystemClock` call would crash them. `NoOpElapsedClock`/`SystemElapsedClock`/`NoOpLatencyAnalytics` already exist from M4-01f; this plan does not touch `core/time/`.
- **Repo convention: unique-prefix same-package test fakes, even though Kotlin's top-level `private` is file-scoped (no actual redeclaration error across files).** `feature/session/tts/TtsPlaybackCoordinatorTest.kt` already declares private `FakeLlmApi`, `FakePcmPlayer`, `FakeDeviceTts`, `FakeSettings` — this plan's new test file follows the established repo pattern (seen throughout M4-01f) of `Latency`-prefixed fakes (`LatencyFakeLlmApi`, `LatencyFakePcmPlayer`, `LatencyFakeDeviceTts`, `LatencyFakeTtsSettings`) purely for readability/searchability, not because it's technically required.
- **Event id authority:** `docs/ux/analytics-events.md` §10 and `docs/ux/dialogue-learning-flow.md` §13 list `tts` as one of the 6 `request_latency` operations; this plan finalizes the net-new event name `tts_latency_ms`.
- **Timing pattern (avoid the M4-01f Task 4 bug):** when writing a test that asserts an *exact* `latency_ms` value, always call the triggering action (e.g. `coordinator.prefetch(...)`) BEFORE advancing the fake clock, and use a fake `LlmApi` with a real `delayMs > 0` so the coroutine genuinely suspends — advancing the clock before a call that runs synchronously to completion under `UnconfinedTestDispatcher` makes any measured elapsed always `0` regardless of instrumentation. See `TtsLatencyTest.kt`'s tests below for the correct shape.

## Event Decision Table

| Event | Params | Fires at | `outcome` values used | Source |
|---|---|---|---|---|
| `tts_latency_ms` | `outcome`→str, `latency_ms`→long | `TtsPlaybackCoordinator`: wraps the private `synthesize(text, gender)` call made from `obtainAudio`'s `scope.async` block (the one place a real `/llm` TTS network round-trip happens); NOT `warmUpModel()`'s direct `synthesize()` call (excluded by design, decision #2) | `successful` (non-null `CachedAudio` returned), `failed` (`null` — watchdog timeout, network/HTTP exception, or undecodable payload) | net-new, this plan finalizes the name |

**Outcome-computation note:** `synthesize()` already returns `null` uniformly for every failure mode (timeout via `withTimeoutOrNull`, caught `Exception` from `api.tts()`, or a caught `IllegalArgumentException` from base64 decode) and a non-null `CachedAudio` on success — so the wrapper's outcome mapping is a simple null-check, no new failure-classification logic needed.

---

## Task 1: `OPERATION_TTS` constant + contract test

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/analytics/LatencyAnalytics.kt`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/analytics/LatencyAnalyticsDispatchTest.kt`

**Interfaces:**
- Produces: `LatencyAnalytics.OPERATION_TTS = "tts"`, consumed by Task 2.

- [ ] **Step 1: Write the failing test**

Add this test to the existing `LatencyAnalyticsDispatchTest.kt` (alongside its 5 existing `@Test` methods — do not remove or rename any of them):

```kotlin
    @Test
    fun `logs tts_latency_ms`() {
        analytics.latency(LatencyAnalytics.OPERATION_TTS, LatencyAnalytics.OUTCOME_FAILED, 900L)
        assertEquals(
            RecordingAnalyticsSink.Event("tts_latency_ms", mapOf("outcome" to "failed", "latency_ms" to 900L)),
            sink.events.single(),
        )
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*.LatencyAnalyticsDispatchTest"`
Expected: FAIL — `Unresolved reference: OPERATION_TTS`.

- [ ] **Step 3: Add the constant**

In `LatencyAnalytics.kt`, inside the existing `companion object` (add after `OPERATION_SUMMARY`, keep everything else unchanged):

```kotlin
        const val OPERATION_SUMMARY = "summary"
        const val OPERATION_TTS = "tts"
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*.LatencyAnalyticsDispatchTest"`
Expected: PASS (6 tests — the 5 pre-existing + this new one)

- [ ] **Step 5: detekt**

Run: `./scripts/verify-android.sh :app:detekt`
Expected: zero findings in the 2 files touched this task.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/analytics/LatencyAnalytics.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/analytics/LatencyAnalyticsDispatchTest.kt
git commit -m "feat(analytics): add OPERATION_TTS to the LatencyAnalytics contract"
```

---

## Task 2: `tts_latency_ms` — `TtsPlaybackCoordinator`

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsLatencyTest.kt` (new file)

**Interfaces:**
- Consumes: `ElapsedClock`/`NoOpElapsedClock` (`core/time`, from M4-01f), `LatencyAnalytics`/`NoOpLatencyAnalytics`/`OPERATION_TTS` (from Task 1).
- Produces: no new public API — internal instrumentation only.

- [ ] **Step 1: Write the failing tests**

```kotlin
// android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsLatencyTest.kt
package com.jjundev.oneclickeng.feature.session.tts

import com.jjundev.oneclickeng.core.audio.PcmPlayer
import com.jjundev.oneclickeng.core.network.LlmApi
import com.jjundev.oneclickeng.core.network.SpeakingRequest
import com.jjundev.oneclickeng.core.network.SpeakingResponse
import com.jjundev.oneclickeng.core.network.TtsRequest
import com.jjundev.oneclickeng.core.network.TtsResponse
import com.jjundev.oneclickeng.core.settings.TtsQuality
import com.jjundev.oneclickeng.core.settings.TtsSettings
import com.jjundev.oneclickeng.core.settings.TtsSettingsRepository
import com.jjundev.oneclickeng.core.time.FakeElapsedClock
import com.jjundev.oneclickeng.feature.session.analytics.LatencyAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.RecordingLatencyAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.util.Base64

private val PCM_BYTES = byteArrayOf(1, 2, 3, 4)

private fun okResponse() =
    TtsResponse(
        pcmBase64 = Base64.getEncoder().encodeToString(PCM_BYTES),
        sampleRate = 24000,
        mimeType = "audio/L16;rate=24000",
    )

private class LatencyFakeLlmApi(
    var response: TtsResponse? = okResponse(),
    var delayMs: Long = 0,
) : LlmApi {
    var callCount = 0

    override suspend fun tts(body: TtsRequest): TtsResponse {
        callCount++
        if (delayMs > 0) delay(delayMs)
        return response ?: throw IOException("boom")
    }

    override suspend fun speaking(body: SpeakingRequest): SpeakingResponse = error("unused")
}

private class LatencyFakePcmPlayer : PcmPlayer {
    override suspend fun play(pcm: ByteArray, sampleRateHz: Int, speed: Float) = Unit
    override fun stop() = Unit
}

private class LatencyFakeDeviceTts : DeviceTts {
    override suspend fun speak(
        text: String,
        gender: String?,
        speechRate: Float,
        onStart: () -> Unit,
    ): DeviceTtsResult = DeviceTtsResult.COMPLETED

    override fun stop() = Unit
}

private class LatencyFakeTtsSettings(
    private val value: TtsSettings = TtsSettings(quality = TtsQuality.SERVER),
) : TtsSettingsRepository {
    override val settings: Flow<TtsSettings> = flowOf(value)
    override suspend fun current(): TtsSettings = value
    override suspend fun setQuality(quality: TtsQuality) = Unit
    override suspend fun setSpeechRate(rate: Float) = Unit
    override suspend fun setMuted(muted: Boolean) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class TtsLatencyTest {
    private fun TestScope.coordScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `prefetch success logs tts_latency_ms successful with exact elapsed`() =
        runTest {
            val api = LatencyFakeLlmApi(delayMs = 500)
            val clock = FakeElapsedClock(now = 10L)
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                TtsPlaybackCoordinator(
                    api,
                    LatencyFakePcmPlayer(),
                    LatencyFakeDeviceTts(),
                    LatencyFakeTtsSettings(),
                    coordScope(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            // Real in-flight gap (mirrors the M4-01f Task 3/4 pattern): the trigger runs first,
            // then the clock advances, then advanceUntilIdle() lets the delayed fake resolve.
            coordinator.prefetch("Hello", "male")
            runCurrent()
            clock.advance(500L)
            advanceUntilIdle()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("tts", LatencyAnalytics.OUTCOME_SUCCESSFUL, 500L)),
                latency.calls,
            )
        }

    @Test
    fun `network failure logs tts_latency_ms failed`() =
        runTest {
            val api = LatencyFakeLlmApi(response = null, delayMs = 500)
            val clock = FakeElapsedClock(now = 20L)
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                TtsPlaybackCoordinator(
                    api,
                    LatencyFakePcmPlayer(),
                    LatencyFakeDeviceTts(),
                    LatencyFakeTtsSettings(),
                    coordScope(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            coordinator.prefetch("Hello", "male")
            runCurrent()
            clock.advance(300L)
            advanceUntilIdle()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("tts", LatencyAnalytics.OUTCOME_FAILED, 300L)),
                latency.calls,
            )
        }

    @Test
    fun `second prefetch for an already-cached line does not log again`() =
        runTest {
            val api = LatencyFakeLlmApi()
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                TtsPlaybackCoordinator(
                    api,
                    LatencyFakePcmPlayer(),
                    LatencyFakeDeviceTts(),
                    LatencyFakeTtsSettings(),
                    coordScope(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            coordinator.prefetch("Hello", "male")
            advanceUntilIdle()
            assertEquals(1, latency.calls.size)

            coordinator.prefetch("Hello", "male") // now a cache hit — no network call, no new log
            advanceUntilIdle()

            assertEquals(1, latency.calls.size)
            assertEquals(1, api.callCount)
        }

    @Test
    fun `concurrent callers for the same line join a single synthesize call and a single log`() =
        runTest {
            val api = LatencyFakeLlmApi(delayMs = 1_000)
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                TtsPlaybackCoordinator(
                    api,
                    LatencyFakePcmPlayer(),
                    LatencyFakeDeviceTts(),
                    LatencyFakeTtsSettings(),
                    coordScope(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            coordinator.prefetch("Hello", "male")
            runCurrent() // the in-flight Deferred is created and suspended inside the 1s delay

            var warmResult = false
            launch { warmResult = coordinator.awaitWarm("Hello", "male") }
            runCurrent()

            advanceUntilIdle()

            assertEquals(1, api.callCount)
            assertEquals(1, latency.calls.size)
            assertEquals(true, warmResult)
        }

    @Test
    fun `warmUpModel does not log tts_latency_ms`() =
        runTest {
            val api = LatencyFakeLlmApi()
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                TtsPlaybackCoordinator(
                    api,
                    LatencyFakePcmPlayer(),
                    LatencyFakeDeviceTts(),
                    LatencyFakeTtsSettings(),
                    coordScope(),
                    clock = clock,
                    latencyAnalytics = latency,
                )

            coordinator.warmUpModel()
            advanceUntilIdle()

            assertEquals(1, api.callCount) // the preheat call did happen
            assertEquals(emptyList<RecordingLatencyAnalytics.Call>(), latency.calls) // but nothing logged
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*.TtsLatencyTest"`
Expected: FAIL to compile — `No value passed for parameter 'clock'` (constructor doesn't accept `clock`/`latencyAnalytics` yet).

- [ ] **Step 3: Instrument the coordinator**

In `TtsPlaybackCoordinator.kt`, add imports:

```kotlin
import com.jjundev.oneclickeng.core.time.ElapsedClock
import com.jjundev.oneclickeng.core.time.NoOpElapsedClock
import com.jjundev.oneclickeng.feature.session.analytics.LatencyAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.NoOpLatencyAnalytics
```

Append 2 trailing constructor params (after `scope`, keep everything else unchanged):

```kotlin
        private val scope: CoroutineScope,
        private val clock: ElapsedClock = NoOpElapsedClock,
        private val latencyAnalytics: LatencyAnalytics = NoOpLatencyAnalytics(),
    ) {
```

Add a private wrapper near `synthesize` (do NOT modify `synthesize()` itself, and do NOT change `warmUpModel()` — it must keep calling `synthesize(...)` directly, unmeasured, per decision #2):

```kotlin
        /** Times the one real network round-trip in [synthesize] for `tts_latency_ms`. Called
         *  ONLY from [obtainAudio]'s `scope.async` block — the single place a given cache key's
         *  synthesis actually runs (join semantics mean concurrent callers share this one call,
         *  so no double-fire guard is needed here, unlike the M4-01f coordinators). Deliberately
         *  NOT used by [warmUpModel]'s direct [synthesize] call (M4-01g decision #2 — preheat is
         *  excluded from this metric). */
        private suspend fun synthesizeMeasured(
            text: String,
            gender: String?,
        ): CachedAudio? {
            val start = clock.nowMillis()
            val result = synthesize(text, gender)
            val outcome = if (result != null) LatencyAnalytics.OUTCOME_SUCCESSFUL else LatencyAnalytics.OUTCOME_FAILED
            latencyAnalytics.latency(LatencyAnalytics.OPERATION_TTS, outcome, clock.nowMillis() - start)
            return result
        }
```

In `obtainAudio(text, gender)`, change the `scope.async` block's inner call from `synthesize(text, gender)` to `synthesizeMeasured(text, gender)` (this is the ONLY line in `obtainAudio` that changes):

```kotlin
            val deferred =
                inFlight[key] ?: scope.async {
                    synthesizeMeasured(text, gender)?.also { cache[key] = it }
                }.also { d ->
                    inFlight[key] = d
                    d.invokeOnCompletion { inFlight.remove(key, d) } // identity remove; tied to the job, not awaiters
                }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*.TtsLatencyTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Run the FULL existing tts test suite (regression check for the trailing-default strategy)**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "com.jjundev.oneclickeng.feature.session.tts.*"`
Expected: PASS — all pre-existing tests in `TtsPlaybackCoordinatorTest.kt` still compile and pass unchanged (they never supply `clock`/`latencyAnalytics`, so they get `NoOpElapsedClock`/`NoOpLatencyAnalytics()` — zero behavior change).

- [ ] **Step 6: Run the dialogue-package regression (3 sibling files also construct `TtsPlaybackCoordinator`)**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "com.jjundev.oneclickeng.feature.session.dialogue.*"`
Expected: PASS — `DialogueGenerationViewModelTest.kt`, `DialogueGenerationFunnelAnalyticsTest.kt`, `WaitQuizShownEndedEmitTest.kt` all construct `TtsPlaybackCoordinator` positionally and must be unaffected.

- [ ] **Step 7: detekt**

Run: `./scripts/verify-android.sh :app:detekt`
Expected: zero findings in touched files.

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsLatencyTest.kt
git commit -m "feat(analytics): fire tts_latency_ms from TtsPlaybackCoordinator"
```

---

## Task 3: Full-suite regression + compile verification

**Files:** none (verification-only task).

- [ ] **Step 1: Run every touched package's test suite together**

Run:
```bash
./scripts/verify-android.sh :app:testDebugUnitTest --tests "com.jjundev.oneclickeng.feature.session.analytics.*" \
  --tests "com.jjundev.oneclickeng.feature.session.tts.*" \
  --tests "com.jjundev.oneclickeng.feature.session.dialogue.*"
```
Expected: PASS, all suites.

- [ ] **Step 2: Full debug compile**

Run: `./scripts/verify-android.sh :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: detekt across the whole diff**

Run: `./scripts/verify-android.sh :app:detekt`
Expected: zero new findings.

- [ ] **Step 4: No commit** (verification task; nothing to stage).

---

## Manual Checkpoint (final — human, GA4 DebugView)

- [ ] Enable DebugView: `adb shell setprop debug.firebase.analytics.app com.jjundev.oneclickeng`, launch a debug build.
- [ ] Trigger a real session where TTS actually synthesizes (SERVER quality, unmuted, a fresh line not already cached) and confirm `tts_latency_ms` fires once with a sane `latency_ms` and `outcome=successful`.
- [ ] Confirm a cache-hit replay (e.g. "다시 듣기") does NOT fire a second `tts_latency_ms`.
- [ ] Confirm app-foreground warm-up does NOT fire `tts_latency_ms` (per decision #2).
- [ ] Back-fill `docs/ux/analytics-events.md` §10 with `tts_latency_ms` — this is the LAST of the 6 `*_latency_ms` events, so this back-fill can cover the full series (`script_gen`, `speaking_analyze`, `slim`, `deep`, `summary`, `tts`) in one pass if the earlier 5 weren't already recorded there.
- [ ] This clears the M4-01 latency series entirely. Cross-check the earlier M4-01a-f plans' own DebugView checkpoints for anything else still outstanding on the overall M4-01 issue.

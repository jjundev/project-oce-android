# M4-01d (Phase 2, Slice 3a) — Mic-Permission + Wait-Quiz Telemetry — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fire the UI-event auxiliary telemetry — `mic_permission_requested`/`mic_permission_result` (RECORD_AUDIO grant funnel) and `wait_quiz_shown`/`wait_quiz_ended` (loading-quiz dwell/engagement) — from their real transition points.

**Architecture:** A new `MicPermissionAnalytics` seam (dispatch via the existing `AnalyticsSink`) fired from `MicSessionDock`'s permission launcher through the already-passed `GeneratedDialogueSessionViewModel`; and two new methods on the existing `WaitQuizAnalytics` seam (already Firebase-wired for `cardAnswered`) fired from `DialogueGenerationViewModel`, driven by shown/ended edges the generating screen detects.

**Tech Stack:** Kotlin 2.1.20, Hilt (KSP), Firebase Analytics via `AnalyticsSink` (M4-01a), JUnit4 + Robolectric + kotlinx-coroutines-test.

**Scope:** Phase 2, Slice 3a of M4-01 (issue [M4-01](../../issues/M4-01-analytics-instrumentation.md)). **Out of scope (Slice 3b):** the `*_latency_ms` series ×6 — it needs a fake-able Clock/TimeSource seam as a shared prerequisite plus tts/summary scope decisions, so it is a separate plan.

**Product decision (confirmed):** `wait_quiz_ended.reason` in v1 is `ready` or `failed` only. **`skipped` (user abandons the generating screen before ready) is DEFERRED** — it needs a new screen-exit lifecycle hook with fuzzy semantics; do not implement it here.

## Global Constraints

- **minSdk 26**, JDK 17, Kotlin 2.1.20. No mockk/Mockito — hand-written fakes only.
- **GA4 snake_case** ids/params. **PII boundary:** enum/bool/count/id/duration only — never quiz text, transcript, or free text.
- **Reuse `AnalyticsSink`** as the single dispatch path (top-of-file `import`, not inline FQN).
- **detekt `MaxLineLength` = 120 on BOTH main and test sources.** Wrap every added line to ≤120 chars. After each task run `./scripts/verify-android.sh :app:detekt` and confirm the files YOU touched report zero findings (ignore the ~30 pre-existing `OceThemeColorContractTest.kt` findings). `ReturnCount` max = 2 is also active.
- **Verify with `./scripts/verify-android.sh :app:testDebugUnitTest --tests "..."`** then `./scripts/verify-android.sh :app:compileDebugKotlin`. Do NOT run the full `check`/`testReleaseUnitTest` (pre-existing unrelated failures).
- **Timing uses `System.currentTimeMillis()` directly** (no shared Clock seam — that's Slice 3b). Tests assert the event fires with correct id/enum/count params and that `dwell_ms`/`delay_ms_at_show` are `Long` ≥ 0, NOT an exact value; exact durations are DebugView-verified.
- **`GeneratedDialogueSessionViewModel` has NO JVM unit-test harness** (9 injected coordinators; covered only by Compose instrumentation) — do not build one. `DialogueGenerationViewModel` DOES have a harness (`DialogueGenerationViewModelTest.kt` / `DialogueGenerationFunnelAnalyticsTest.kt`).
- **Event-id authority** is `docs/ux/analytics-events.md` §4/§6.6. This plan finalizes: `mic_permission_requested {source}` / `mic_permission_result {source, granted}` with `source="session"` (single call site today); `wait_quiz` `surface` ∈ `{onboarding_first_session, home}` per §6.5. Back-fill/confirm at DebugView.

## Event Decision Table

| Event | Params (key → type) | Fires at | Source |
|---|---|---|---|
| `mic_permission_requested` | `source`→str(`"session"`) | `launcher.launch(RECORD_AUDIO)` in `MicSessionDock` | Pinned §4 (source finalized) |
| `mic_permission_result` | `source`→str, `granted`→bool | the permission launcher callback | Pinned §4 |
| `wait_quiz_shown` | `session_id`→str (omit if null), `surface`→str, `delay_ms_at_show`→long | quiz surface first becomes visible | Pinned §4/§6.6 |
| `wait_quiz_ended` | `session_id`→str (omit if null), `surface`→str, `reason`→str(`ready`\|`failed`), `cards_answered`→long, `dwell_ms`→long | user proceeds (ready) or generation fails (failed) | Pinned §4; `skipped` deferred |

**`surface`** = `if (isOnboarding) "onboarding_first_session" else "home"` (§6.5 shared enum; wait-quiz reuses these two values — NOT `dialogue_start_gate`).

---

## Task 1: `MicPermissionAnalytics` seam + Firebase impl + recording fake

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/analytics/MicPermissionAnalytics.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/analytics/SessionFunnelModule.kt` (add a third `@Binds`)
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/analytics/RecordingMicPermissionAnalytics.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/analytics/MicPermissionAnalyticsDispatchTest.kt`

**Interfaces:**
- Consumes: `AnalyticsSink.log(event, params)`.
- Produces: `interface MicPermissionAnalytics { fun requested(source: String); fun result(source: String, granted: Boolean) }` with a `SOURCE_SESSION = "session"` companion constant; `NoOpMicPermissionAnalytics`; `FirebaseMicPermissionAnalytics(sink: AnalyticsSink)`; test `RecordingMicPermissionAnalytics`.

- [ ] **Step 1: Write the failing contract test** (`MicPermissionAnalyticsDispatchTest.kt`):

```kotlin
package com.jjundev.oneclickeng.feature.session.analytics

import com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink
import org.junit.Assert.assertEquals
import org.junit.Test

class MicPermissionAnalyticsDispatchTest {
    private val sink = RecordingAnalyticsSink()
    private val analytics = FirebaseMicPermissionAnalytics(sink)

    @Test
    fun `requested logs mic_permission_requested with source`() {
        analytics.requested(MicPermissionAnalytics.SOURCE_SESSION)
        assertEquals(
            RecordingAnalyticsSink.Event("mic_permission_requested", mapOf("source" to "session")),
            sink.events.single(),
        )
    }

    @Test
    fun `result logs mic_permission_result with source and granted`() {
        analytics.result(MicPermissionAnalytics.SOURCE_SESSION, granted = true)
        assertEquals(
            RecordingAnalyticsSink.Event("mic_permission_result", mapOf("source" to "session", "granted" to true)),
            sink.events.single(),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*MicPermissionAnalyticsDispatchTest"`
Expected: FAIL — `FirebaseMicPermissionAnalytics` unresolved.

- [ ] **Step 3: Write the seam** (`MicPermissionAnalytics.kt`):

```kotlin
package com.jjundev.oneclickeng.feature.session.analytics

import com.jjundev.oneclickeng.core.analytics.AnalyticsSink
import javax.inject.Inject

/**
 * RECORD_AUDIO permission telemetry seam (M4-01d, analytics-events.md §4). `source` is a single
 * constant today (the in-session mic request); the param exists for a future settings re-request path.
 * PII: only source enum + granted bool.
 */
interface MicPermissionAnalytics {
    fun requested(source: String)

    fun result(source: String, granted: Boolean)

    companion object {
        const val SOURCE_SESSION = "session"
    }
}

/** Default no-op binding (test/fallback). */
class NoOpMicPermissionAnalytics
    @Inject
    constructor() : MicPermissionAnalytics {
        override fun requested(source: String) = Unit

        override fun result(source: String, granted: Boolean) = Unit
    }

/** Firebase dispatch via the shared [AnalyticsSink] (M4-01a). */
class FirebaseMicPermissionAnalytics
    @Inject
    constructor(
        private val sink: AnalyticsSink,
    ) : MicPermissionAnalytics {
        override fun requested(source: String) = sink.log("mic_permission_requested", mapOf("source" to source))

        override fun result(source: String, granted: Boolean) =
            sink.log("mic_permission_result", mapOf("source" to source, "granted" to granted))
    }
```

- [ ] **Step 4: Add the `@Binds`** to `SessionFunnelModule.kt` (a third bind method inside the existing `abstract class`):

```kotlin
    @Binds
    @Singleton
    abstract fun bindMicPermissionAnalytics(impl: FirebaseMicPermissionAnalytics): MicPermissionAnalytics
```

- [ ] **Step 5: Write the recording fake** (`RecordingMicPermissionAnalytics.kt`, test source set):

```kotlin
package com.jjundev.oneclickeng.feature.session.analytics

/** Records mic-permission calls for emit-site behavior tests (repo convention = fakes). */
class RecordingMicPermissionAnalytics : MicPermissionAnalytics {
    data class Call(val name: String, val source: String, val granted: Boolean?)

    val calls = mutableListOf<Call>()

    override fun requested(source: String) {
        calls += Call("mic_permission_requested", source, null)
    }

    override fun result(source: String, granted: Boolean) {
        calls += Call("mic_permission_result", source, granted)
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*MicPermissionAnalyticsDispatchTest"` then `./scripts/verify-android.sh :app:detekt` (touched files clean) then `./scripts/verify-android.sh :app:compileDebugKotlin`.
Expected: PASS (2 tests); detekt clean; Hilt graph compiles with the third binding.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/analytics/MicPermissionAnalytics.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/analytics/SessionFunnelModule.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/analytics/RecordingMicPermissionAnalytics.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/analytics/MicPermissionAnalyticsDispatchTest.kt
git commit -m "feat(analytics): add MicPermissionAnalytics seam, Firebase dispatch, recording fake"
```

---

## Task 2: mic-permission emit-sites (`GeneratedDialogueSessionViewModel` + `MicSessionDock`)

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDock.kt`

**Interfaces:**
- Consumes: `MicPermissionAnalytics` (Task 1).
- Produces: `GeneratedDialogueSessionViewModel.onMicPermissionRequested()` and `onMicPermissionResult(granted: Boolean)`.

> **Testing note — no new unit test.** These fire from `GeneratedDialogueSessionViewModel`, which has no JVM harness (Compose-instrumentation only), and from `MicSessionDock`, a Composable using `rememberLauncherForActivityResult`. The id/params are unit-tested by Task 1's dispatch test; these thin call-site insertions are verified by compile + self-review + DebugView (same pattern as M4-01b's session-VM emit-sites). Do NOT build a VM harness.

- [ ] **Step 1: Inject the seam into the VM.** Add to `GeneratedDialogueSessionViewModel`'s constructor (after the `sessionFunnel` param added in M4-01b, before `savedStateHandle`):

```kotlin
        private val micPermissionAnalytics: com.jjundev.oneclickeng.feature.session.analytics.MicPermissionAnalytics,
```

(Use a top-of-file import.) Add the two methods (near the other public VM actions, e.g. by `onToggleTextMode`):

```kotlin
        /** RECORD_AUDIO permission requested (priming sheet → OS dialog). M4-01d. */
        fun onMicPermissionRequested() =
            micPermissionAnalytics.requested(
                com.jjundev.oneclickeng.feature.session.analytics.MicPermissionAnalytics.SOURCE_SESSION,
            )

        /** RECORD_AUDIO permission result (granted/denied). M4-01d. */
        fun onMicPermissionResult(granted: Boolean) =
            micPermissionAnalytics.result(
                com.jjundev.oneclickeng.feature.session.analytics.MicPermissionAnalytics.SOURCE_SESSION,
                granted,
            )
```

(Prefer top-of-file imports for `MicPermissionAnalytics` so these lines fit ≤120 chars.)

- [ ] **Step 2: Compile the VM change** (Hilt resolves the new param from Task 1's binding):

Run: `./scripts/verify-android.sh :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Fire from `MicSessionDock`.** In `MicDock.kt`:
- In the launcher callback (line ~81, `rememberLauncherForActivityResult(...) { granted -> ... }`), add `viewModel.onMicPermissionResult(granted)` as the FIRST line of the lambda (before the `if (granted)` branch), so both grant and deny paths report:

```kotlin
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onMicPermissionResult(granted)
            if (granted) {
```

- At the OS request site (line ~131-134, `onRequest = { showPriming = false; launcher.launch(Manifest.permission.RECORD_AUDIO) }`), add `viewModel.onMicPermissionRequested()` right before `launcher.launch(...)`:

```kotlin
            onRequest = {
                showPriming = false
                viewModel.onMicPermissionRequested()
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            },
```

> Fire `requested` only at the actual OS `launcher.launch` (not at `showPriming = true`, which is the app's own priming sheet). This matches `mic_permission_requested` = the system dialog request.

- [ ] **Step 4: Verify compile + no regression**

Run: `./scripts/verify-android.sh :app:compileDebugKotlin` then `./scripts/verify-android.sh :app:detekt` (touched files clean) then `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*MicDock*"`
Expected: BUILD SUCCESSFUL; detekt clean; the existing `MicDock*` Compose tests still pass (the added VM calls don't change dock UI behavior).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDock.kt
git commit -m "feat(analytics): fire mic_permission_requested and mic_permission_result"
```

---

## Task 3: `WaitQuizAnalytics` — add `waitQuizShown` + `waitQuizEnded`

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/network/WaitQuizAnalytics.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/network/WaitQuizShownEndedDispatchTest.kt`

**Interfaces:**
- Consumes: `AnalyticsSink` (already used by `FirebaseWaitQuizAnalytics`).
- Produces: two new methods on `WaitQuizAnalytics` (and both impls): `fun waitQuizShown(sessionId: String?, surface: String, delayMsAtShow: Long)`, `fun waitQuizEnded(sessionId: String?, surface: String, reason: String, cardsAnswered: Int, dwellMs: Long)`.

- [ ] **Step 1: Write the failing contract test** (`WaitQuizShownEndedDispatchTest.kt`):

```kotlin
package com.jjundev.oneclickeng.core.network

import com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink
import org.junit.Assert.assertEquals
import org.junit.Test

class WaitQuizShownEndedDispatchTest {
    private val sink = RecordingAnalyticsSink()
    private val analytics = FirebaseWaitQuizAnalytics(sink)

    @Test
    fun `wait_quiz_shown carries surface and delay; omits null session_id`() {
        analytics.waitQuizShown(sessionId = null, surface = "home", delayMsAtShow = 1000L)
        assertEquals(
            RecordingAnalyticsSink.Event(
                "wait_quiz_shown",
                mapOf("surface" to "home", "delay_ms_at_show" to 1000L),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `wait_quiz_ended carries reason cards_answered dwell and session_id when present`() {
        analytics.waitQuizEnded(
            sessionId = "s1",
            surface = "onboarding_first_session",
            reason = "ready",
            cardsAnswered = 2,
            dwellMs = 3400L,
        )
        assertEquals(
            mapOf(
                "session_id" to "s1",
                "surface" to "onboarding_first_session",
                "reason" to "ready",
                "cards_answered" to 2L,
                "dwell_ms" to 3400L,
            ),
            sink.events.single().params,
        )
        assertEquals("wait_quiz_ended", sink.events.single().name)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*WaitQuizShownEndedDispatchTest"`
Expected: FAIL — `waitQuizShown`/`waitQuizEnded` unresolved.

- [ ] **Step 3: Add the two methods** to the `WaitQuizAnalytics` interface, `NoOpWaitQuizAnalytics`, and `FirebaseWaitQuizAnalytics`. Interface (after `cardAnswered`):

```kotlin
    fun waitQuizShown(
        sessionId: String?,
        surface: String,
        delayMsAtShow: Long,
    )

    fun waitQuizEnded(
        sessionId: String?,
        surface: String,
        reason: String,
        cardsAnswered: Int,
        dwellMs: Long,
    )
```

`NoOpWaitQuizAnalytics` — add both as `= Unit`. `FirebaseWaitQuizAnalytics`:

```kotlin
        override fun waitQuizShown(
            sessionId: String?,
            surface: String,
            delayMsAtShow: Long,
        ) = sink.log(
            "wait_quiz_shown",
            buildMap {
                sessionId?.let { put("session_id", it) }
                put("surface", surface)
                put("delay_ms_at_show", delayMsAtShow)
            },
        )

        override fun waitQuizEnded(
            sessionId: String?,
            surface: String,
            reason: String,
            cardsAnswered: Int,
            dwellMs: Long,
        ) = sink.log(
            "wait_quiz_ended",
            buildMap {
                sessionId?.let { put("session_id", it) }
                put("surface", surface)
                put("reason", reason)
                put("cards_answered", cardsAnswered.toLong())
                put("dwell_ms", dwellMs)
            },
        )
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*WaitQuizShownEndedDispatchTest"` then `./scripts/verify-android.sh :app:detekt` (touched file clean) then `./scripts/verify-android.sh :app:compileDebugKotlin`.
Expected: PASS (2 tests); detekt clean; compiles.

> **Widening the interface breaks every test fake that implements `WaitQuizAnalytics`** — add the two new overrides (as `= Unit`, or recording them if the test asserts on them) to each. Known implementers to update: `RecordingAnalytics` in `DialogueGenerationViewModelTest.kt` (~line 80, constructed at 10+ call sites) and `FunnelRecordingWaitQuizAnalytics` in `DialogueGenerationFunnelAnalyticsTest.kt`. Run `./scripts/verify-android.sh :app:compileDebugUnitTestKotlin` to surface any others the compiler flags; don't stop after the first.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/core/network/WaitQuizAnalytics.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/core/network/WaitQuizShownEndedDispatchTest.kt
git commit -m "feat(analytics): add wait_quiz_shown and wait_quiz_ended to the WaitQuiz seam"
```

---

## Task 4: wait-quiz emit-sites (`DialogueGenerationViewModel` + `DialogueGeneratingScreen`)

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModel.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGeneratingScreen.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/WaitQuizShownEndedEmitTest.kt`

**Interfaces:**
- Consumes: the extended `WaitQuizAnalytics` (Task 3) — already injected as `analytics` in `DialogueGenerationViewModel`; the VM's `isOnboarding`, `answeredCount`, `coordinator.sessionId()`.
- Produces: `DialogueGenerationViewModel.onQuizShown()`, `onQuizEnded(reason: String)`; the screen/Route wiring; reason constants `REASON_READY`/`REASON_FAILED`.

- [ ] **Step 1: Write the failing test** (`WaitQuizShownEndedEmitTest.kt`) — mirror the LOCAL VM-construction helper from `DialogueGenerationFunnelAnalyticsTest.kt` (read it; build a local `newGenerationViewModel(...)` with uniquely-named fakes and a recording `WaitQuizAnalytics`). Assert: `start(...)` then `onQuizShown()` fires `wait_quiz_shown{surface, delay_ms_at_show>=0}` once (a second `onQuizShown()` is a no-op); `onQuizEnded("ready")` fires `wait_quiz_ended{reason=ready, cards_answered, dwell_ms>=0}` once; `onQuizEnded` with no prior `onQuizShown` fires nothing:

```kotlin
package com.jjundev.oneclickeng.feature.session.dialogue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
// ... same test infra as DialogueGenerationFunnelAnalyticsTest.kt ...

class WaitQuizShownEndedEmitTest {
    @Test
    fun `revisit generation logs wait_quiz_shown once then wait_quiz_ended ready once`() {
        val quiz = RecordingWaitQuizForShownEnded() // local fake WaitQuizAnalytics (unique name)
        val vm = newGenerationViewModel(waitQuiz = quiz, sessionId = "s1")
        vm.start(level = "normal", topic = "cafe", length = 10, firstSession = false, isOnboarding = false)

        vm.onQuizShown()
        vm.onQuizShown() // idempotent
        vm.onQuizEnded("ready")
        vm.onQuizEnded("ready") // idempotent

        assertEquals(listOf("wait_quiz_shown", "wait_quiz_ended"), quiz.calls.map { it.name })
        val shown = quiz.calls.first { it.name == "wait_quiz_shown" }
        assertEquals("home", shown.surface)
        assertTrue((shown.delayMs ?: -1L) >= 0L)
        val ended = quiz.calls.first { it.name == "wait_quiz_ended" }
        assertEquals("ready", ended.reason)
        assertTrue((ended.dwellMs ?: -1L) >= 0L)
    }

    @Test
    fun `onQuizEnded without a prior onQuizShown fires nothing`() {
        val quiz = RecordingWaitQuizForShownEnded()
        val vm = newGenerationViewModel(waitQuiz = quiz, sessionId = "s1")
        vm.start(level = "normal", topic = "cafe", length = 10, firstSession = false, isOnboarding = false)
        vm.onQuizEnded("failed")
        assertEquals(emptyList<String>(), quiz.calls.map { it.name })
    }
}
```

> Build a local `RecordingWaitQuizForShownEnded : WaitQuizAnalytics` (all 3 methods; record `name`/`surface`/`reason`/`delayMs`/`dwellMs`/`cardsAnswered`, `cardAnswered` no-op) with a UNIQUE name. Read `DialogueGenerationFunnelAnalyticsTest.kt` for the local `newGenerationViewModel(...)` construction (fake coordinator whose `sessionId()` you can control, `FakeConnectivity`, the other analytics seams) and add a `waitQuiz` param. Drive the coordinator so `sessionId()` returns `"s1"` the way that file's tests do.

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*WaitQuizShownEndedEmitTest"`
Expected: FAIL — `onQuizShown`/`onQuizEnded` unresolved.

- [ ] **Step 3: Add the VM timing state + methods** to `DialogueGenerationViewModel`. Add fields near `answeredCount` (line ~63):

```kotlin
        // wait_quiz shown/ended timing (M4-01d). Real clock; exact dwell/delay DebugView-verified.
        private var generationStartMillis = 0L
        private var quizShownMillis: Long? = null
        private var quizEnded = false
```

In `start()`'s `StartOutcome.Started` branch (where `answeredCount = 0` is set), reset the wait-quiz state:

```kotlin
                    answeredCount = 0
                    generationStartMillis = System.currentTimeMillis()
                    quizShownMillis = null
                    quizEnded = false
```

Add the methods (near `onQuizAnswered`):

Add `import com.jjundev.oneclickeng.ui.component.LimitSurface` to `DialogueGenerationViewModel.kt` (the file imports the `selectLimitSurface` FUNCTION but NOT the `LimitSurface` enum type — the bare `LimitSurface` reference below needs its own import or it won't resolve). Then:

```kotlin
        // Reuse the existing shared LimitSurface enum values (§6.5) instead of duplicating
        // "onboarding_first_session"/"home" literals. (Requires the LimitSurface import above.)
        private fun waitQuizSurface(): String =
            if (isOnboarding) LimitSurface.OnboardingFirstSession.value else LimitSurface.Home.value

        /** The wait-quiz surface first became visible (gate passed while generating). Once per generation. */
        fun onQuizShown() {
            if (quizShownMillis != null) return
            val now = System.currentTimeMillis()
            quizShownMillis = now
            analytics.waitQuizShown(coordinator.sessionId(), waitQuizSurface(), now - generationStartMillis)
        }

        /** The wait-quiz ended: [reason] = [REASON_READY] (user proceeded) or [REASON_FAILED]. Once; no-op if
         *  never shown. `skipped` is deferred (v1). */
        fun onQuizEnded(reason: String) {
            val shownAt = quizShownMillis ?: return
            if (quizEnded) return
            quizEnded = true
            analytics.waitQuizEnded(
                sessionId = coordinator.sessionId(),
                surface = waitQuizSurface(),
                reason = reason,
                cardsAnswered = answeredCount,
                dwellMs = System.currentTimeMillis() - shownAt,
            )
        }
```

> No new VM `companion object` constants are needed: `waitQuizSurface()` reuses `LimitSurface.OnboardingFirstSession.value`/`LimitSurface.Home.value` (enum members confirmed at `ui/component/OneClickLimitReachedPanel.kt`) — but you MUST add `import com.jjundev.oneclickeng.ui.component.LimitSurface` (the file imports only the `selectLimitSurface` function, not the type). The `reason` string is passed IN to `onQuizEnded` from the screen/Route (below), so `"ready"`/`"failed"` live as screen-local constants there — the VM never references them, which sidesteps the VM's `private companion object` (its members are not accessible from the screen/Route class).

- [ ] **Step 4: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*WaitQuizShownEndedEmitTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Wire the screen edges.** In `DialogueGeneratingScreen.kt`:
- Add two params to the `DialogueGeneratingScreen` composable (near `onLimitReached`, line ~89): `onQuizShown: () -> Unit = {}`, `onQuizEnded: (reason: String) -> Unit = {}`.
- Compute the shown condition and drive `onQuizShown` on its rising edge (place near the `gatePassed` `LaunchedEffect`, after line 103's `conversationReady`):

```kotlin
    val quizVisible = quizEnabled && gatePassed &&
        (state is DialogueGenState.Generating || state is DialogueGenState.Ready)
    LaunchedEffect(quizVisible) { if (quizVisible) onQuizShown() }
    LaunchedEffect(state) { if (state is DialogueGenState.Failed) onQuizEnded(DIALOGUE_QUIZ_REASON_FAILED) }
```

Add two file-level (top-level `private const`) constants in `DialogueGeneratingScreen.kt` — `private const val DIALOGUE_QUIZ_REASON_FAILED = "failed"` and `private const val DIALOGUE_QUIZ_REASON_READY = "ready"`. Screen-local constants are required because `DialogueGenerationViewModel`'s `companion object` is `private` (its members are inaccessible from this file). The VM's `onQuizEnded` no-ops if the quiz was never shown, so the `Failed`-before-shown case is safe.

- [ ] **Step 6: Wire the Route.** In `DialogueGeneratingRoute` (`DialogueGeneratingScreen.kt` ~line 263), pass the two new callbacks to the screen, and extend the `onStartConversation` wrap (added in M4-01b) to end the quiz as `ready`:

```kotlin
        onStartConversation = {
            viewModel.onConversationStarted()
            viewModel.onQuizEnded(DIALOGUE_QUIZ_REASON_READY)
            onStartConversation()
        },
        onQuizShown = viewModel::onQuizShown,
        onQuizEnded = viewModel::onQuizEnded,
```

> `onStartConversation` fires when the user proceeds (auto-start once ready, or CTA tap) → `ready`. If the quiz was never shown (ready-before-gate), `onQuizEnded` no-ops. The screen's `Failed` effect covers `failed`. `skipped` is intentionally not wired (deferred).

- [ ] **Step 7: Verify compile + no regression**

Run: `./scripts/verify-android.sh :app:compileDebugKotlin`, then `./scripts/verify-android.sh :app:detekt` (touched files clean), then `./scripts/verify-android.sh :app:testDebugUnitTest` (FULL debug suite — last task; the new emit test passes and existing dialogue-generation tests still green).
Expected: BUILD SUCCESSFUL across all three.

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModel.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGeneratingScreen.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/WaitQuizShownEndedEmitTest.kt
git commit -m "feat(analytics): fire wait_quiz_shown and wait_quiz_ended (ready/failed)"
```

---

## Manual Checkpoint (final — human, GA4 DebugView)

- [ ] Enable DebugView (`adb shell setprop debug.firebase.analytics.app com.jjundev.oneclickeng`, debug build).
- [ ] First mic tap → priming sheet → allow: confirm `mic_permission_requested {source=session}` then `mic_permission_result {source=session, granted=true}`; repeat and deny → `granted=false`.
- [ ] Start a session slow enough to see the loading quiz (>1s): confirm `wait_quiz_shown {surface, delay_ms_at_show}` once when the quiz appears, then `wait_quiz_ended {reason=ready, cards_answered, dwell_ms}` when you proceed. Force a generation failure → `wait_quiz_ended {reason=failed}`. Confirm no `skipped` fires (deferred). Verify `dwell_ms`/`delay_ms_at_show` look sane.
- [ ] Back-fill `docs/ux/analytics-events.md`: mic `source="session"` finalization, and confirm `wait_quiz` `surface` ∈ `{onboarding_first_session, home}` + that `reason=skipped` is deferred to a later slice.

## Slice 3b (latency) — not in this plan

The `*_latency_ms` series ×6 (`script_gen`/`tts`/`speaking`/`slim`/`deep`/`summary`) needs a fake-able `Clock`/`TimeSource` seam (none exists — timing is `System.currentTimeMillis()` today) so the `runTest` virtual-clock harnesses can assert durations deterministically, plus product decisions: `tts_latency` has no single start point (`playTurn`/`prefetch`/`warmUpModel`/`awaitWarm` share `obtainAudio`/`synthesize`; cache hits are 0-latency), and `summary_latency`'s scope (whole multi-attempt session vs first attempt) given `retry()` re-issues `launchAttempt`.

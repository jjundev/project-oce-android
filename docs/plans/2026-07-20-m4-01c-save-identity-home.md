# M4-01c (Phase 2, Slice 2) — Save-rate + Cohort + Home Funnel Completion — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the 5-지표 backbone and D1/D7 cohort stitching by firing `saved_card_create` (save-rate metric), the link-time `setUserId`/`auth_state` re-call (§3b cohort stitching), and the home `topic_selected` / `session_setting_changed` emit-sites (the two Phase-1 seams that were wired to dispatch but had no caller).

**Architecture:** One new `SavedCardAnalytics` seam (dispatching through the existing `AnalyticsSink`) fired from both save surfaces; a narrow `AnalyticsSink.setUserId`/`setUserProperty` re-call added to `GoogleLinkViewModel`'s link-success branches; and inline calls to the already-injected, already-Firebase-bound `HomeAnalytics` from the home ViewModel (settings debounced via the slider's existing `onValueChangeFinished`).

**Tech Stack:** Kotlin 2.1.20, Hilt (KSP), Firebase Analytics via `AnalyticsSink` (M4-01a), JUnit4 + Robolectric + kotlinx-coroutines-test.

**Scope:** Phase 2, Slice 2 of M4-01 (issue [M4-01](../../issues/M4-01-analytics-instrumentation.md)). **Out of scope (Slice 3 — aux telemetry):** `wait_quiz_shown`/`wait_quiz_ended`, `mic_permission_*`, the `*_latency_ms` series (that slice needs a testable Clock seam + product decisions).

## Global Constraints

- **minSdk 26**, JDK 17, Kotlin 2.1.20. No mockk/Mockito — hand-written fakes only.
- **GA4 snake_case** ids/params. **PII boundary:** enum/bool/count/id only — never free text (no saved-card body, no topic text; `custom` is a bool, the typed topic string is never logged).
- **Reuse `AnalyticsSink`** (`com.jjundev.oneclickeng.core.analytics.AnalyticsSink`) as the single dispatch path — top-of-file `import`, not inline FQN.
- **`card_type` value = `CardType.wire`** (matches the existing `FirebaseHistoryAnalytics`, `feature/records/HistoryAnalytics.kt:35` — do NOT invent a new encoding). `CardType` (`feature/session/saved/SavedCard.kt:7`) has `WORD`/`EXPRESSION`/`SENTENCE`, each with a `.wire` string.
- **detekt `MaxLineLength` = 120 is active on BOTH main and test sources.** Wrap every added line to ≤120 chars. After each task run `./scripts/verify-android.sh :app:detekt` and confirm the files YOU touched report zero findings (ignore the ~30 pre-existing `OceThemeColorContractTest.kt` findings). `ReturnCount` max = 2 is also active.
- **Verify with `./scripts/verify-android.sh :app:testDebugUnitTest --tests "..."`** then `./scripts/verify-android.sh :app:compileDebugKotlin`. Do NOT run the full `check`/`testReleaseUnitTest` (pre-existing unrelated failures).
- **Adding a param to a `@Singleton`/positional constructor breaks every manual (test) construction site** — the compiler lists them; update each (default the new arg to the `NoOp*` where it keeps existing cases unaffected). Same-package top-level `private class` test fakes collide by name — give new local fakes UNIQUE names.
- **Event-id authority** is `docs/ux/analytics-events.md` §4/§6.3. This plan's one finalization: **`saved_card_create` fires for BOTH explicit saves and save-by-default auto-saves** (see the Save decision below) — to be back-filled/confirmed at the DebugView checkpoint.

## Save-instrumentation decision (finalized here)

`saved_card_create` fires on the **client-side "a card was saved" action**, for every save:
- **Explicit saves:** the `added == true` branch of `toggleSaveWord`/`toggleSaveExpression`/`toggleSaveBookmark` (summary) and `toggleBookmark` (deep feedback).
- **Auto-saves:** `autoSaveExpressions`/`autoSaveWords` (fired per item) when the user's save-by-default setting is on. **Rationale:** §6.3's save-rate counts sessions with ≥1 `saved_card_create`; auto-saved cards are genuinely persisted saved cards, so excluding them would make save-by-default users appear as non-savers and understate save rate. Both paths route through one logging call so neither is missed.
- **No per-card dedup:** a save→unsave→save re-emits (each is a real save action). §6.3's rate is ≥1-per-session so re-emits don't distort it; raw counts decompose by `surface`×`card_type`.
- **Never fires on unsave** (the `else`/`setDeleted` branches) — that path is `saved_card_delete` (already wired in `HistoryAnalytics`), out of scope here.

## Event Decision Table

| Event | Params (key → type) | Fires at | Source |
|---|---|---|---|
| `saved_card_create` | `session_id`→str, `surface`→str(`summary`\|`deep_feedback`), `card_type`→`CardType.wire` | each save action (explicit + auto) | Pinned §4/§6.3 |
| (user id) `setUserId(newUid)` + property `auth_state="linked"` | — | guest→Google link/merge success | Pinned §3b |
| `topic_selected` | `topic_id`→str (omit if null), `custom`→bool | home situation/topic pick | Home seam already Firebase-wired (M4-01a); finalized ids per §10 |
| `session_setting_changed` | `level`→str, `length`→long | slider drag COMMIT (not per-tick) | Home seam already wired; finalized per §10 |

---

## Task 1: `SavedCardAnalytics` seam + Firebase impl + recording fake

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/analytics/SavedCardAnalytics.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/analytics/SessionFunnelModule.kt` (add a second `@Binds` for the new seam — the module already lives here)
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/analytics/RecordingSavedCardAnalytics.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/analytics/SavedCardAnalyticsDispatchTest.kt`

**Interfaces:**
- Consumes: `AnalyticsSink.log(event, params)`; `com.jjundev.oneclickeng.feature.session.saved.CardType` (`.wire`).
- Produces: `interface SavedCardAnalytics { fun savedCardCreate(sessionId: String, surface: String, cardType: CardType) }` with `SURFACE_SUMMARY`/`SURFACE_DEEP_FEEDBACK` constants; `NoOpSavedCardAnalytics`; `FirebaseSavedCardAnalytics(sink: AnalyticsSink)`; test `RecordingSavedCardAnalytics`.

- [ ] **Step 1: Write the failing contract test** (`SavedCardAnalyticsDispatchTest.kt`):

```kotlin
package com.jjundev.oneclickeng.feature.session.analytics

import com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink
import com.jjundev.oneclickeng.feature.session.saved.CardType
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedCardAnalyticsDispatchTest {
    private val sink = RecordingAnalyticsSink()
    private val analytics = FirebaseSavedCardAnalytics(sink)

    @Test
    fun `summary word save logs saved_card_create with card_type wire`() {
        analytics.savedCardCreate("s1", SavedCardAnalytics.SURFACE_SUMMARY, CardType.WORD)
        assertEquals(
            RecordingAnalyticsSink.Event(
                "saved_card_create",
                mapOf("session_id" to "s1", "surface" to "summary", "card_type" to CardType.WORD.wire),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `deep feedback sentence save logs the deep_feedback surface`() {
        analytics.savedCardCreate("s2", SavedCardAnalytics.SURFACE_DEEP_FEEDBACK, CardType.SENTENCE)
        assertEquals(
            RecordingAnalyticsSink.Event(
                "saved_card_create",
                mapOf("session_id" to "s2", "surface" to "deep_feedback", "card_type" to CardType.SENTENCE.wire),
            ),
            sink.events.single(),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*SavedCardAnalyticsDispatchTest"`
Expected: FAIL — `FirebaseSavedCardAnalytics` unresolved.

- [ ] **Step 3: Write the seam** (`SavedCardAnalytics.kt`):

```kotlin
package com.jjundev.oneclickeng.feature.session.analytics

import com.jjundev.oneclickeng.core.analytics.AnalyticsSink
import com.jjundev.oneclickeng.feature.session.saved.CardType
import javax.inject.Inject

/**
 * `saved_card_create` telemetry seam (M4-01c, analytics-events.md §4/§6.3). Fires on each save action
 * (explicit toggle-add + save-by-default auto-save) from both surfaces. `card_type` = [CardType.wire]
 * (same encoding as FirebaseHistoryAnalytics). PII: only session_id/surface enum/card_type enum.
 */
interface SavedCardAnalytics {
    fun savedCardCreate(sessionId: String, surface: String, cardType: CardType)

    companion object {
        const val SURFACE_SUMMARY = "summary"
        const val SURFACE_DEEP_FEEDBACK = "deep_feedback"
    }
}

/** Default no-op binding (test/fallback). */
class NoOpSavedCardAnalytics
    @Inject
    constructor() : SavedCardAnalytics {
        override fun savedCardCreate(sessionId: String, surface: String, cardType: CardType) = Unit
    }

/** Firebase dispatch via the shared [AnalyticsSink] (M4-01a). */
class FirebaseSavedCardAnalytics
    @Inject
    constructor(
        private val sink: AnalyticsSink,
    ) : SavedCardAnalytics {
        override fun savedCardCreate(sessionId: String, surface: String, cardType: CardType) =
            sink.log(
                "saved_card_create",
                mapOf("session_id" to sessionId, "surface" to surface, "card_type" to cardType.wire),
            )
    }
```

- [ ] **Step 4: Add the `@Binds`** to `SessionFunnelModule.kt` (the existing module — add a second bind method inside the same `abstract class`):

```kotlin
    @Binds
    @Singleton
    abstract fun bindSavedCardAnalytics(impl: FirebaseSavedCardAnalytics): SavedCardAnalytics
```

- [ ] **Step 5: Write the recording fake** (`RecordingSavedCardAnalytics.kt`, test source set):

```kotlin
package com.jjundev.oneclickeng.feature.session.analytics

import com.jjundev.oneclickeng.feature.session.saved.CardType

/** Records saved-card calls for emit-site behavior tests (repo convention = fakes). */
class RecordingSavedCardAnalytics : SavedCardAnalytics {
    data class Call(val sessionId: String, val surface: String, val cardType: CardType)

    val calls = mutableListOf<Call>()

    override fun savedCardCreate(sessionId: String, surface: String, cardType: CardType) {
        calls += Call(sessionId, surface, cardType)
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*SavedCardAnalyticsDispatchTest"` then `./scripts/verify-android.sh :app:detekt` (touched files 0 findings) then `./scripts/verify-android.sh :app:compileDebugKotlin`.
Expected: PASS (2 tests); detekt clean on the new files; Hilt graph compiles with the second binding.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/analytics/SavedCardAnalytics.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/analytics/SessionFunnelModule.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/analytics/RecordingSavedCardAnalytics.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/analytics/SavedCardAnalyticsDispatchTest.kt
git commit -m "feat(analytics): add SavedCardAnalytics seam, Firebase dispatch, recording fake"
```

---

## Task 2: `saved_card_create` from `SummaryCoordinator` (5 sites)

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinator.kt`
- Modify (positional-ctor construction sites — compiler lists them): `SummaryCoordinatorTest.kt`, `SummaryViewModelSessionCompleteTest.kt`'s local helper, `SummaryPartialFailureAnalyticsTest.kt`'s local helper
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummarySavedCardAnalyticsTest.kt`

**Interfaces:**
- Consumes: `SavedCardAnalytics` (Task 1); the coordinator's `sessionId` field; `CardType`.
- Produces: `SummaryCoordinator(..., savedCardAnalytics)`; a private `logSaved(cardType)` helper.

- [ ] **Step 1: Write the failing test** (`SummarySavedCardAnalyticsTest.kt`) — build a LOCAL coordinator helper (uniquely-named fakes, per the Task-5/6 precedent in the M4-01b plan) and drive a WORD save + an EXPRESSION unsave, asserting exactly one `saved_card_create{summary, WORD}`:

```kotlin
package com.jjundev.oneclickeng.feature.session.summary

import com.jjundev.oneclickeng.feature.session.analytics.RecordingSavedCardAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.SavedCardAnalytics
import com.jjundev.oneclickeng.feature.session.saved.CardType
import org.junit.Assert.assertEquals
import org.junit.Test

class SummarySavedCardAnalyticsTest {
    @Test
    fun `saving a word card logs one saved_card_create summary WORD; unsave logs nothing`() {
        val saved = RecordingSavedCardAnalytics()
        val coordinator = newSummarySavedCardCoordinator(savedCardAnalytics = saved)
        // Drive the coordinator to a Ready word section for "s1" the way SummaryCoordinatorTest does,
        // then toggle-save index 0 (added), then toggle it again (unsave).
        driveReadyWordSection(coordinator, sessionId = "s1")
        coordinator.toggleSaveWord(0) // added -> logs
        coordinator.toggleSaveWord(0) // unsave -> no log

        assertEquals(
            listOf(RecordingSavedCardAnalytics.Call("s1", SavedCardAnalytics.SURFACE_SUMMARY, CardType.WORD)),
            saved.calls,
        )
    }
}
```

> Read `SummaryCoordinatorTest.kt` for its stream-driving + Ready-section setup and mirror it into `newSummarySavedCardCoordinator(...)` / `driveReadyWordSection(...)` in THIS file with unique fake names (e.g. `SavedCardFake*`). The helper adds `savedCardAnalytics: SavedCardAnalytics = NoOpSavedCardAnalytics()` and threads it into the (now +1-arg) `SummaryCoordinator(...)`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*SummarySavedCardAnalyticsTest"`
Expected: FAIL — coordinator has no `savedCardAnalytics` param.

- [ ] **Step 3: Inject the seam + add the helper.** Add `private val savedCardAnalytics: SavedCardAnalytics` to `SummaryCoordinator`'s constructor (after `sessionFunnel`, added in M4-01b). Add the helper (private):

```kotlin
        // saved_card_create — summary surface always; sessionId guaranteed non-null at the save call sites (M4-01c).
        private fun logSaved(cardType: CardType) {
            sessionId?.let { savedCardAnalytics.savedCardCreate(it, SavedCardAnalytics.SURFACE_SUMMARY, cardType) }
        }
```

- [ ] **Step 4: Call `logSaved` at the five save sites** — inside the `if (added)` create branch of each toggle, and once per item in each auto-save loop. In `toggleSaveWord` (line ~253) add `logSaved(CardType.WORD)` inside the `if (added) { ... }` after `savedCardRepository.save(...)`:

```kotlin
            if (added) {
                savedCardRepository.save(cardId, card.toSavedCard())
                logSaved(CardType.WORD)
            } else {
```

Do the same in `toggleSaveExpression` (`logSaved(CardType.EXPRESSION)` after its `save`), `toggleSaveBookmark` (`logSaved(CardType.SENTENCE)` after its `save`), and inside the `forEachIndexed` loops of `autoSaveExpressions` (`logSaved(CardType.EXPRESSION)` after each `save`) and `autoSaveWords` (`logSaved(CardType.WORD)` after each `save`). Do NOT add anything to the `else`/`setDeleted` branches.

- [ ] **Step 5: Update the other construction sites.** The positional ctor change breaks the local helpers added by M4-01b: update `SummaryCoordinatorTest.kt`'s `coordinator(...)` factory, `SummaryViewModelSessionCompleteTest.kt`'s `newSummaryCoordinatorForTest()`, and `SummaryPartialFailureAnalyticsTest.kt`'s `newSummaryCoordinatorForTest()` — append `savedCardAnalytics` (default `NoOpSavedCardAnalytics()`) so existing cases stay green. The compiler lists any you miss.

- [ ] **Step 6: Run tests to verify they pass**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*SummarySavedCardAnalyticsTest" --tests "*SummaryCoordinatorTest" --tests "*SummaryViewModelSessionCompleteTest" --tests "*SummaryPartialFailureAnalyticsTest"` then `./scripts/verify-android.sh :app:detekt` (touched files clean) then `./scripts/verify-android.sh :app:compileDebugKotlin`.
Expected: PASS — new test green; the three pre-existing suites still green after the ctor change; detekt clean; compiles.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinator.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary
git commit -m "feat(analytics): fire saved_card_create from summary saves (incl. auto-save)"
```

---

## Task 3: `saved_card_create` from `DeepFeedbackCoordinator`

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackCoordinator.kt`
- Modify (construction sites): `DeepFeedbackCoordinatorTest.kt` (and any other test that constructs `DeepFeedbackCoordinator` — compiler lists them)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackSavedCardAnalyticsTest.kt`

**Interfaces:**
- Consumes: `SavedCardAnalytics` (Task 1); the coordinator's `lastRequest?.sessionId` + `turnIndex`.
- Produces: `DeepFeedbackCoordinator(stream, savedCardRepository, scope, savedCardAnalytics)` (new arg appended LAST).

- [ ] **Step 1: Write the failing test** (`DeepFeedbackSavedCardAnalyticsTest.kt`) — reuse `DeepFeedbackCoordinatorTest.kt`'s construction/drive approach (build a LOCAL helper with uniquely-named fakes), drive to a state with a `lastRequest` (sessionId set), toggle-bookmark a paraphrase (added), assert one `saved_card_create{deep_feedback, SENTENCE}`; toggle again (unsave) → no additional log:

```kotlin
package com.jjundev.oneclickeng.feature.session.feedback

import com.jjundev.oneclickeng.feature.session.analytics.RecordingSavedCardAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.SavedCardAnalytics
import com.jjundev.oneclickeng.feature.session.saved.CardType
import org.junit.Assert.assertEquals
import org.junit.Test

class DeepFeedbackSavedCardAnalyticsTest {
    @Test
    fun `bookmarking a paraphrase logs one saved_card_create deep_feedback SENTENCE; unbookmark logs nothing`() {
        val saved = RecordingSavedCardAnalytics()
        val coordinator = newDeepCoordinator(savedCardAnalytics = saved)
        val paraphrase = driveToDeepReadyWithParaphrase(coordinator, sessionId = "s1")
        coordinator.toggleBookmark(paraphrase) // added -> logs
        coordinator.toggleBookmark(paraphrase) // remove -> no log

        assertEquals(
            listOf(RecordingSavedCardAnalytics.Call("s1", SavedCardAnalytics.SURFACE_DEEP_FEEDBACK, CardType.SENTENCE)),
            saved.calls,
        )
    }
}
```

> Read `DeepFeedbackCoordinatorTest.kt` for how it constructs the coordinator (fake `DeepFeedbackStream`, `FakeSavedCardRepository`) and drives it to a Ready state carrying paraphrases + a `lastRequest.sessionId`. Mirror that into `newDeepCoordinator(...)` / `driveToDeepReadyWithParaphrase(...)` in THIS file with unique fake names. Add `savedCardAnalytics: SavedCardAnalytics = NoOpSavedCardAnalytics()` and thread it in.

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*DeepFeedbackSavedCardAnalyticsTest"`
Expected: FAIL — `DeepFeedbackCoordinator` has no `savedCardAnalytics` param.

- [ ] **Step 3: Inject the seam + fire it.** Add `private val savedCardAnalytics: SavedCardAnalytics` to `DeepFeedbackCoordinator`'s constructor as the LAST param (after `scope`). In `toggleBookmark`'s `if (added)` branch (line ~154), after `savedCardRepository.save(...)`, add:

```kotlin
                if (added) {
                    savedCardRepository.save(
                        cardId,
                        SavedCard.Sentence(
                            english = paraphrase.sentence,
                            korean = paraphrase.sentenceTranslation,
                        ),
                    )
                    savedCardAnalytics.savedCardCreate(sessionId, SavedCardAnalytics.SURFACE_DEEP_FEEDBACK, CardType.SENTENCE)
                } else {
```

(`sessionId` is the non-null value from the enclosing `lastRequest?.sessionId?.let { sessionId -> ... }`.) Do NOT touch the `else`/`setDeleted` branch.

- [ ] **Step 4: Update construction sites.** `DeepFeedbackCoordinatorTest.kt` has **~11 separate inline `DeepFeedbackCoordinator(stream, repo, coordScope())` call sites** (no single factory) — append `NoOpSavedCardAnalytics()` as the new last arg to EVERY one (the compiler lists each; don't stop after the first). Do the same for any other manual construction site the compiler flags. Production is Hilt `@Inject` — the new binding from Task 1 resolves it. Import `NoOpSavedCardAnalytics` in the test file.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*DeepFeedbackSavedCardAnalyticsTest" --tests "*DeepFeedbackCoordinatorTest"` then `./scripts/verify-android.sh :app:detekt` (touched files clean) then `./scripts/verify-android.sh :app:compileDebugKotlin`.
Expected: PASS — new test green; `DeepFeedbackCoordinatorTest` still green; detekt clean; compiles.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackCoordinator.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback
git commit -m "feat(analytics): fire saved_card_create from deep-feedback bookmark save"
```

---

## Task 4: Link-time `setUserId` re-call (`GoogleLinkViewModel`)

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/google/GoogleLinkViewModel.kt`
- Modify (construction sites): `GoogleLinkViewModelTest.kt`
- Test: add a case to `GoogleLinkViewModelTest.kt`

**Interfaces:**
- Consumes: `AnalyticsSink` (M4-01a) — `setUserId`/`setUserProperty`; `AuthRepository.currentUid` (M4-01a added `isAnonymous`; `currentUid` predates it).
- Produces: `GoogleLinkViewModel(linker, analytics, accountResetBus, analyticsSink, authRepository)`; a private `stitchLinkedIdentity()`.

- [ ] **Step 1: Write the failing test** — add to `GoogleLinkViewModelTest.kt`. On a `LinkOutcome.Promoted` (or `Merged`), after `linkGoogle`, the sink must have `userId == <post-link uid>` and `auth_state == "linked"`. Read the file first for its existing fake `GoogleAccountLinker` (it already drives `LinkOutcome`s) and its VM-construction; add a `RecordingAnalyticsSink` + a `FakeAuthRepository(currentUid = "linked-uid")`:

```kotlin
    @Test
    fun `successful link stitches the linked identity into analytics`() =
        runTest {
            val sink = com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink()
            val vm = newGoogleLinkViewModel(
                linker = FakeLinker(outcome = LinkOutcome.Promoted),
                analyticsSink = sink,
                authRepository = FakeAuthRepository(uid = "linked-uid"),
            )
            vm.linkGoogle("token", sessionId = "sess-1")
            advanceUntilIdle()

            assertEquals("linked-uid", sink.userId)
            assertEquals("linked", sink.userProperties["auth_state"])
        }
```

> Adapt the helper/fake names to what `GoogleLinkViewModelTest.kt` already defines (it has a fake linker and constructs the VM). `RecordingAnalyticsSink` (M4-01a) and a minimal `FakeAuthRepository` implementing `AuthRepository` (`currentUid` + `isAnonymous` + `ensureSignedIn`) are what you add. If the file already has an `AuthRepository` fake, reuse it.

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*GoogleLinkViewModelTest"`
Expected: FAIL — VM has no `analyticsSink`/`authRepository` params.

- [ ] **Step 3: Inject + stitch.** Add two params to `GoogleLinkViewModel`'s constructor (after `accountResetBus`):

```kotlin
        private val analyticsSink: com.jjundev.oneclickeng.core.analytics.AnalyticsSink,
        private val authRepository: com.jjundev.oneclickeng.core.auth.AuthRepository,
```

(Use top-of-file imports.) Add the helper:

```kotlin
        /** §3b cohort stitching — after a link/merge resolves, re-point the analytics identity to the
         *  now-linked uid so D1/D7 cohorts stay continuous across the anon→linked boundary. */
        private fun stitchLinkedIdentity() {
            analyticsSink.setUserId(authRepository.currentUid)
            analyticsSink.setUserProperty("auth_state", "linked")
        }
```

Call `stitchLinkedIdentity()` in the three success branches, right after the existing analytics call:
- `linkGoogle` → `LinkOutcome.Promoted` branch (after `analytics.googleLinkSucceeded(sessionId)`),
- `linkGoogle` → `LinkOutcome.Merged` branch (after `analytics.googleLinkConflictMerged(sessionId)`),
- `retryMerge` → `LinkOutcome.Merged` branch (after `analytics.googleLinkConflictMerged(sessionId)`).

Do NOT call `accountResetBus.signal()` here (that resets the boot gate and would eject the user from the mid-onboarding UI — it is correct only for the reauth flows that run outside an active nav flow).

- [ ] **Step 4: Update construction sites** in `GoogleLinkViewModelTest.kt` — every `GoogleLinkViewModel(...)` construction gains the two new args (a `RecordingAnalyticsSink` and an `AuthRepository` fake; existing cases that don't assert analytics can pass a throwaway `RecordingAnalyticsSink()` + a fake with any `currentUid`).

- [ ] **Step 5: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*GoogleLinkViewModelTest"` then `./scripts/verify-android.sh :app:detekt` (touched files clean) then `./scripts/verify-android.sh :app:compileDebugKotlin`.
Expected: PASS — new case green, existing cases green; detekt clean; Hilt resolves `AnalyticsSink`/`AuthRepository` (both already bound).

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/google/GoogleLinkViewModel.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/google/GoogleLinkViewModelTest.kt
git commit -m "feat(analytics): re-stitch analytics identity on Google link success"
```

---

## Task 5: Home `topic_selected` + `session_setting_changed` emit-sites

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeViewModel.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `HomeAnalytics` (already injected as `analytics` in `HomeViewModel`) — `topicSelected(topicId: String?, custom: Boolean)`, `sessionSettingChanged(level: String, length: Int)`.
- Produces: `HomeViewModel.onSessionSettingCommitted()`; a new `onSessionSettingCommitted: () -> Unit` param threaded through `HomeScreen` to both sliders' `onValueChangeFinished`.

> **Testing note — no new unit test for this task.** `HomeViewModel` has **no JVM unit-test harness** and one is genuinely expensive to build: it takes 7 constructor deps, several of which are concrete `@Singleton` classes needing a real `DataStore<Preferences>` (`StudytimeStore`, `SessionSnapshotStore`) and a `SessionLimitHolder` that itself needs a `DialogueGenerationCoordinator`, plus `viewModelScope.launch` in `init` requires `Dispatchers.setMain` + Robolectric. `HomeSituationTapTest.kt` documents this deliberately ("스테이트풀 래퍼(hiltViewModel)는 이 단위테스트 범위 밖") and tests the stateless `HomeContent` composable instead. This task follows the same accepted pattern the M4-01b session-VM emit-sites used: the **dispatch (event id + params) is already unit-tested by `HomeAnalyticsDispatchTest.kt`** (which pins `topic_selected`/`session_setting_changed` on `FirebaseHomeAnalytics` via `RecordingAnalyticsSink`, from M4-01a), and these thin call-site insertions are verified by compile + careful self-review + the DebugView checkpoint. Do NOT attempt to construct `HomeViewModel` in a test.

- [ ] **Step 1: Fire `topic_selected` in the three selection methods** of `HomeViewModel` (lines ~164-176):

```kotlin
        fun selectSituation(topic: Topic) {
            selected.value = topic.toSelected()
            analytics.topicSelected(topicId = topic.id, custom = false)
        }

        fun selectSituationById(id: String) {
            TopicCatalog.ALL.firstOrNull { it.id == id }?.let {
                selected.value = it.toSelected()
                analytics.topicSelected(topicId = it.id, custom = false)
            }
        }

        fun selectCustomSituation(text: String) {
            selected.value = SelectedSituation(topicId = null, labelKo = text, promptSeed = text)
            analytics.topicSelected(topicId = null, custom = true)
        }
```

- [ ] **Step 2: Add `onSessionSettingCommitted()` to `HomeViewModel`** (near `setLevel`/`setLength`). It reads the committed level/length and fires once:

```kotlin
        /** Slider drag COMMIT (onValueChangeFinished) — one session_setting_changed per settle, not per tick. */
        fun onSessionSettingCommitted() {
            analytics.sessionSettingChanged(
                level = levelOverride.value ?: defaultLevel.value ?: FALLBACK_LEVEL,
                length = length.value,
            )
        }
```

- [ ] **Step 3: Wire the commit callback through `HomeScreen`.** The two sliders (`HomeScreen.kt:854` level, `:891` length) currently pass only `onValueChange` and leave `OneClickSlider.onValueChangeFinished` (default null) unset. Thread a new callback:
  - `HomeRoute` (line ~213-214, where `onSetLevel = viewModel::setLevel` etc.): add `onSessionSettingCommitted = viewModel::onSessionSettingCommitted`.
  - Add `onSessionSettingCommitted: () -> Unit = {}` to the `HomeContent` param list (near `onSetLevel`/`onSetLength`, lines ~294-295) and forward it (line ~414-415 area) to the inner composable that owns the sliders (add the param there too, lines ~787-788).
  - On BOTH `OneClickSlider` calls (level `:854`, length `:891`) add `onValueChangeFinished = onSessionSettingCommitted`.

> This fires exactly once when the user lifts their finger, not per drag frame (the flood the raw `onValueChange` would cause). The typed level/length come from VM state, never from the slider event payload.

- [ ] **Step 4: Full verification (whole debug suite — last task)**

Run: `./scripts/verify-android.sh :app:compileDebugKotlin`, then `./scripts/verify-android.sh :app:detekt` (confirm the two touched home files report zero findings), then `./scripts/verify-android.sh :app:testDebugUnitTest`.
Expected: BUILD SUCCESSFUL — compiles (Hilt graph resolves the new `SavedCardAnalytics` binding + the `GoogleLinkViewModel` params); detekt clean on the touched files; full debug unit suite green (no regression — the emit-site insertions add no new test but must not break existing home tests like `HomeSituationTapTest`/`HomeSettingsSliderTest`).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeViewModel.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt
git commit -m "feat(analytics): fire home topic_selected and session_setting_changed"
```

---

## Manual Checkpoint (final — human, GA4 DebugView)

- [ ] Enable DebugView (`adb shell setprop debug.firebase.analytics.app com.jjundev.oneclickeng`, debug build).
- [ ] Save a WORD and an EXPRESSION card on the summary screen and a SENTENCE via "더보기" bookmark → confirm `saved_card_create {surface, card_type}` for each; unsave one → confirm NO extra `saved_card_create` (it's a delete). With save-by-default ON, complete a session → confirm auto-saved cards each emit `saved_card_create`.
- [ ] Link a guest to Google → confirm the analytics user id switches to the linked uid and `auth_state` becomes `linked` (DebugView user properties).
- [ ] On home, pick a curated situation and a custom-typed one → `topic_selected {topic_id?, custom}` (no typed text in params); drag the level/length sliders → exactly ONE `session_setting_changed` per settle (not per drag frame).
- [ ] Back-fill `docs/ux/analytics-events.md`: confirm the finalized decision that `saved_card_create` includes auto-saves, and the home `topic_selected`/`session_setting_changed` ids.

## Slice 3 (aux telemetry) — not in this plan

`wait_quiz_shown`/`wait_quiz_ended` (needs the shown/ended logic moved out of Compose-local state + a `skipped`-reason product decision), `mic_permission_requested`/`_result` (new seam, Compose launcher in `MicDock`), and the `*_latency_ms` series (needs a fake-able Clock/TimeSource seam as a shared prerequisite + tts/summary latency-scope product decisions).

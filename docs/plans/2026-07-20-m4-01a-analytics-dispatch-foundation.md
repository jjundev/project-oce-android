# M4-01a — Analytics Dispatch Foundation + Boot Identity + Ambient-Seam Wiring — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn on Firebase Analytics dispatch for the events whose emit-sites already exist — boot-time identity stitching (D1/D7 cohort) plus the onboarding-funnel, home, limit, wait-quiz, and offline seams that are currently bound to No-Op — so those events reach GA4/DebugView with contract-exact ids and params.

**Architecture:** Introduce one thin `AnalyticsSink` seam (`log` / `setUserId` / `setUserProperty`) with a Firebase-backed impl and a recording test fake. Each currently-No-Op `*Analytics` interface gets a `Firebase*Analytics` impl that maps its typed calls to GA4 `snake_case` events **through the sink**, so the event-name/param mapping is unit-testable without mockk. Emit-sites are untouched — they already call the seams; we only swap the Hilt `@Binds` target from `NoOp*` to `Firebase*`. Boot identity is wired into `AppViewModel.bootstrap()`.

**Tech Stack:** Kotlin 2.1.20, Hilt (KSP), Firebase Analytics (already a dependency, `libs.firebase.analytics`), JUnit4 + Robolectric + kotlinx-coroutines-test (JVM unit tests). No mockk/Mockito.

**Scope note — this is Phase 1 of M4-01 (issue [M4-01](../../issues/M4-01-analytics-instrumentation.md)).** It covers every analytics event whose emit-site *already exists* (the 5 currently-No-Op seams + boot identity). It explicitly does **not** cover events that require new emit-sites inside feature ViewModels — `session_complete`, `turn_started`, `turn_completed`, `speaking_analyze_result`, `deep_feedback_opened`, `first_session_*`, `learning_session_started`, `saved_card_create`, `mic_permission_*`, `summary_partial_failure`, the `*_latency_ms` series, and the link-time `setUserId` re-call. Those are **Phase 2 (M4-01b)** — a separate plan, because each needs surgery in a different feature VM and the contract test attaches to that VM's transition, not to a dispatch wrapper. Phase 1 is shippable and DebugView-verifiable on its own.

## Global Constraints

- **minSdk 26**, targetSdk 36, JDK 17, Kotlin 2.1.20. Every new file compiles under these.
- **No mockk/Mockito** — the repo tests with hand-written fakes (`OnboardingViewModelTest` header: "레포 관례 = mockk 미사용"). New tests use fakes only.
- **GA4 snake_case** for every event name and parameter key (`analytics-events.md` §2). Never reuse GA4-reserved `session_start` / `first_open` for app events.
- **PII boundary:** only enum / boolean / count / duration / id may be logged — never free text (transcript, user text, korean prompt, saved-card body, directly-typed topic text). `analytics-events.md` §7.
- **Do not touch emit-sites in Phase 1.** ViewModels/Workers already call the seams; this plan only adds dispatch impls and flips `@Binds`. If a grep shows an emit-site missing, stop and record it — do not add the call here.
- **Verify with `scripts/verify-android.sh`** (worktree gradle wrapper — CLAUDE.md / `docs/agents/android-verification.md`), never a bare `./gradlew` against the shared cache.
- **Event-id authority is `docs/ux/analytics-events.md`.** Where that doc pins an id (§4 matrix), use it verbatim. Where a seam's id is still "제안명" (home + google-link events, per `home-learning-entry.md` §10), this plan **finalizes** it following the §2 convention and records the decision in the Event-ID Decision Table below — that finalization *is* part of M4-01's mandate (P17).

## Event-ID Decision Table

Ids used by this plan. "Pinned" = verbatim from `analytics-events.md` §4/§6. "Finalized here" = this plan's P17 decision, convention-following, to be back-filled into `analytics-events.md`.

| Seam method | GA4 event | Params (keys) | Source |
|---|---|---|---|
| `OnboardingAnalytics.onboardingStarted` | `onboarding_started` | `is_returning` (bool) | Pinned §4 (`auth_state` is carried as the user property, not duplicated as a param) |
| `OnboardingAnalytics.levelSelected` | `level_selected` | `level` (str) | Pinned §4 |
| `OnboardingAnalytics.topicSelected` | `topic_selected` | `topic_id` (str), `beginner_friendly` (bool) | Pinned §4 |
| `OnboardingAnalytics.googleSavePromptShown` | `google_save_prompt_shown` | `session_id` | Finalized here |
| `OnboardingAnalytics.googleLinkSkipped` | `google_link_skipped` | `session_id` | Finalized here |
| `OnboardingAnalytics.googleLinkSucceeded` | `google_link_succeeded` | `session_id` | Finalized here |
| `OnboardingAnalytics.googleLinkConflictMerged` | `google_link_conflict_merged` | `session_id` | Finalized here |
| `OnboardingAnalytics.googleLinkFailed` | `google_link_failed` | `session_id` | Finalized here |
| `OnboardingAnalytics.reauthLinkSucceeded` | `reauth_link_succeeded` | — | Finalized here |
| `OnboardingAnalytics.reauthLinkConflictMerged` | `reauth_link_conflict_merged` | — | Finalized here |
| `OnboardingAnalytics.reauthLinkFailed` | `reauth_link_failed` | — | Finalized here |
| `HomeAnalytics.homeView` | `home_view` | — | Finalized here (§10 "홈 노출") |
| `HomeAnalytics.homeCtaTap` | `home_cta_tap` | — | Finalized here (§10 "홈 CTA 탭") |
| `HomeAnalytics.resumeContinue` | `resume_continue` | — | Finalized here (§10 "이어하기 탭") |
| `HomeAnalytics.resumeStartNew` | `resume_start_new` | — | Finalized here (§10 "새로 시작 탭") |
| `HomeAnalytics.topicSelected` | `topic_selected` | `topic_id` (str, nullable→omit), `custom` (bool) | Finalized here — same event as onboarding, `custom` is the home-only param; `beginner_friendly` simply absent on home fires |
| `HomeAnalytics.sessionSettingChanged` | `session_setting_changed` | `level` (str), `length` (long) | Finalized here (§10 "세션 설정 변경") |
| `HomeAnalytics.offlineBlocked` | `offline_blocked_action` | `surface` = `"home"` | Finalized here — event name from `exception-states.md` §9; the `surface` value `home` ∈ the shared enum in `analytics-events.md` §6.5 |
| `LimitAnalytics.limitReached` | `limit_reached` | `remaining` (long), `surface` (str) | Pinned §4/§6.5 |
| `WaitQuizAnalytics.cardAnswered` | `wait_quiz_card_answered` | `session_id` (nullable→omit), `card_id` (str), `chose_correct` (bool), `card_index` (long) | Pinned §4 |
| `OfflineAnalytics.connectivityChanged` | `connectivity_changed` | `online` (bool) | Finalized here — from `exception-states.md` §9 (provisional there; **not** in `analytics-events.md`, so it must be back-filled) |
| `OfflineAnalytics.offlineBlocked` | `offline_blocked_action` | `surface` (str) | Finalized here — from `exception-states.md` §9 / `exception-states.md:150` (**not** in `analytics-events.md`, so it must be back-filled) |
| user property | `auth_state` | `guest` \| `linked` | Pinned §3 |
| user property | `level` | current `profile.level` | Pinned §3 |
| user id | `setUserId(firebaseUid)` | — | Pinned §3 (boot-time call = §3a; link-time re-call = §3b is Phase 2) |

Nullable-id rule (applies to `topic_id`, `session_id`): when the seam passes `null`, **omit the key** from the params map — never log `"null"`.

## File Structure

**New (production):**
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/analytics/AnalyticsSink.kt` — the seam + `FirebaseAnalyticsSink` impl + `Map→Bundle` helper.
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/analytics/AnalyticsModule.kt` — Hilt `@Binds AnalyticsSink → FirebaseAnalyticsSink`.

**New (test):**
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/analytics/RecordingAnalyticsSink.kt` — shared recording fake.
- One `*AnalyticsDispatchTest.kt` per dispatch task (contract tests).

**Modified (production):**
- `core/auth/AuthRepository.kt` — add `val isAnonymous: Boolean` to the interface + `FirebaseAuthRepository`.
- `ui/root/AppViewModel.kt` — inject `AnalyticsSink`; set user id + `auth_state`/`level` properties in `bootstrap()`; add pure `authStateFor()` helper.
- `feature/onboarding/OnboardingAnalytics.kt` — add `FirebaseOnboardingAnalytics` (keep `NoOpOnboardingAnalytics`).
- `feature/onboarding/di/OnboardingModule.kt` — bind `Firebase…` instead of `NoOp…`.
- `feature/home/HomeAnalytics.kt` + `feature/home/di/HomeModule.kt` — same swap.
- `core/network/LimitAnalytics.kt`, `core/network/WaitQuizAnalytics.kt`, `core/network/DialogueModule.kt` — same swap (two seams, one module).
- `core/connectivity/OfflineAnalytics.kt` + `core/connectivity/ConnectivityModule.kt` — same swap.

**Modified (test):**
- All `AuthRepository` fakes gain `override val isAnonymous` (the compiler lists them; known: `AppViewModelTest`'s fake, `feature/onboarding/…` `FakeAuthRepository`).

---

## Manual Checkpoint A (prerequisite — human/console, no code)

> `analytics-events.md` §8 calls BigQuery export + KST timezone a **hard precondition** before instrumentation is meaningful. This is a Firebase/GA4 console task an agent cannot perform. Do it (or confirm it's done) before Task 1; the code tasks below do not depend on it to compile or unit-test, but DebugView verification (Checkpoint B) and cohort correctness do.

- [ ] In the Firebase console, link the project to BigQuery and **enable GA4 → BigQuery export**.
- [ ] Set the **GA4 property timezone and BigQuery export timezone to KST (Asia/Seoul)** so `event_date` day-boundaries align with the app's `usage/{yyyymmdd}` KST partition (`firestore-schema.md`).
- [ ] Confirm **Anonymous Auth is enabled** in the console (bootstrap sign-in depends on it — `AuthRepository` doc note) so DebugView has a real UID to attach.

---

## Task 1: `AnalyticsSink` seam + Firebase impl + recording fake

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/analytics/AnalyticsSink.kt`
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/analytics/AnalyticsModule.kt`
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/analytics/RecordingAnalyticsSink.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/analytics/FirebaseAnalyticsSinkBundleTest.kt`

**Interfaces:**
- Produces:
  - `interface AnalyticsSink { fun log(event: String, params: Map<String, Any> = emptyMap()); fun setUserId(userId: String?); fun setUserProperty(name: String, value: String?) }`
  - `class FirebaseAnalyticsSink @Inject constructor(analytics: FirebaseAnalytics) : AnalyticsSink`
  - `fun Map<String, Any>.toAnalyticsBundle(): Bundle` (internal, in the same file — the one piece the bundle test pins)
  - test-only `class RecordingAnalyticsSink : AnalyticsSink` exposing `val events: List<Event>`, `var userId: String?`, `val userProperties: Map<String, String?>`, where `data class Event(val name: String, val params: Map<String, Any>)`.

- [ ] **Step 1: Write the failing test** (`FirebaseAnalyticsSinkBundleTest.kt`) — the map→Bundle conversion is the only non-trivial thin part, so pin it:

> **Robolectric is required here:** this test constructs `android.os.Bundle`, which throws under a plain JVM runner (the repo does not set `unitTests.isReturnDefaultValues`). Use the same `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [26], application = Application::class)` pattern every framework-touching unit test in this repo uses (e.g. `OnboardingViewModelTest`). The `RecordingAnalyticsSink`-based contract tests in Tasks 3–6 only compare `Map` params and need **no** Robolectric.

```kotlin
package com.jjundev.oneclickeng.core.analytics

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class FirebaseAnalyticsSinkBundleTest {
    @Test
    fun `toAnalyticsBundle maps each supported type to the right Bundle slot`() {
        val bundle =
            mapOf(
                "s" to "text",
                "b" to true,
                "i" to 3,
                "l" to 7L,
                "d" to 1.5,
            ).toAnalyticsBundle()

        assertEquals("text", bundle.getString("s"))
        assertEquals(true, bundle.getBoolean("b"))
        assertEquals(3L, bundle.getLong("i")) // Int is widened to Long (GA4 numeric)
        assertEquals(7L, bundle.getLong("l"))
        assertEquals(1.5, bundle.getDouble("d"), 0.0)
    }

    @Test
    fun `empty map yields an empty bundle`() {
        assertEquals(0, emptyMap<String, Any>().toAnalyticsBundle().size())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*FirebaseAnalyticsSinkBundleTest"`
Expected: FAIL — `toAnalyticsBundle` unresolved (compile error).

- [ ] **Step 3: Write the production seam + impl** (`AnalyticsSink.kt`):

```kotlin
package com.jjundev.oneclickeng.core.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single dispatch seam for Firebase Analytics (M4-01). Feature-specific `Firebase*Analytics` impls
 * map their typed calls to GA4 events through this seam so the event-name/param contract is
 * unit-testable with [RecordingAnalyticsSink] (repo convention = no mockk). The Firebase wrapper
 * itself is thin and not unit-tested; only [toAnalyticsBundle] is pinned.
 *
 * PII boundary (analytics-events.md §7): callers pass only enum/bool/count/duration/id — never free text.
 */
interface AnalyticsSink {
    fun log(event: String, params: Map<String, Any> = emptyMap())

    fun setUserId(userId: String?)

    fun setUserProperty(name: String, value: String?)
}

@Singleton
class FirebaseAnalyticsSink
    @Inject
    constructor(
        private val analytics: FirebaseAnalytics,
    ) : AnalyticsSink {
        override fun log(event: String, params: Map<String, Any>) = analytics.logEvent(event, params.toAnalyticsBundle())

        override fun setUserId(userId: String?) = analytics.setUserId(userId)

        override fun setUserProperty(name: String, value: String?) = analytics.setUserProperty(name, value)
    }

/** GA4 params accept String/Long/Double (+Boolean via Bundle). Int/Float are widened. */
internal fun Map<String, Any>.toAnalyticsBundle(): Bundle =
    Bundle().apply {
        forEach { (key, value) ->
            when (value) {
                is String -> putString(key, value)
                is Boolean -> putBoolean(key, value)
                is Int -> putLong(key, value.toLong())
                is Long -> putLong(key, value)
                is Double -> putDouble(key, value)
                is Float -> putDouble(key, value.toDouble())
                else -> putString(key, value.toString())
            }
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*FirebaseAnalyticsSinkBundleTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Write the DI module** (`AnalyticsModule.kt`):

```kotlin
package com.jjundev.oneclickeng.core.analytics

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the single analytics dispatch seam to its Firebase impl (M4-01). */
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {
    @Binds
    @Singleton
    abstract fun bindAnalyticsSink(impl: FirebaseAnalyticsSink): AnalyticsSink
}
```

- [ ] **Step 6: Write the shared recording fake** (`RecordingAnalyticsSink.kt`, test source set):

```kotlin
package com.jjundev.oneclickeng.core.analytics

/** Records analytics calls for contract assertions (repo convention = fakes, not mockk). */
class RecordingAnalyticsSink : AnalyticsSink {
    data class Event(val name: String, val params: Map<String, Any>)

    val events = mutableListOf<Event>()
    var userId: String? = null
        private set
    val userProperties = mutableMapOf<String, String?>()

    override fun log(event: String, params: Map<String, Any>) {
        events.add(Event(event, params))
    }

    override fun setUserId(userId: String?) {
        this.userId = userId
    }

    override fun setUserProperty(name: String, value: String?) {
        userProperties[name] = value
    }
}
```

- [ ] **Step 7: Full compile + module test**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*FirebaseAnalyticsSinkBundleTest"`
Expected: PASS, and the `test` source set compiles (proves `RecordingAnalyticsSink` compiles).

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/core/analytics android/app/src/test/kotlin/com/jjundev/oneclickeng/core/analytics
git commit -m "feat(analytics): add AnalyticsSink dispatch seam, Firebase impl, recording fake"
```

---

## Task 2: Boot-time identity stitching (D1/D7 cohort)

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/auth/AuthRepository.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppViewModel.kt`
- Modify (test fakes — compiler lists them): `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/root/AppViewModelTest.kt`, `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/OnboardingViewModelTest.kt` (its `FakeAuthRepository`)
- Test: add cases to `AppViewModelTest.kt`

**Interfaces:**
- Consumes: `AnalyticsSink` (Task 1); `AuthRepository.currentUid`.
- Produces:
  - `AuthRepository.isAnonymous: Boolean` (new interface member).
  - `internal fun authStateFor(isAnonymous: Boolean): String` in `AppViewModel.kt` — `"guest"` when anonymous, else `"linked"`.
  - `AppViewModel` now takes an `AnalyticsSink` constructor param and, on a successful bootstrap, calls `setUserId(uid)` + `setUserProperty("auth_state", …)` + `setUserProperty("level", level)`.

- [ ] **Step 1: Extend the `appViewModel(...)` test factory** (lines ~106-120 of `AppViewModelTest.kt`) with an `analytics` param and pass it to the constructor:

```kotlin
    private fun appViewModel(
        studytime: StudytimeRepository,
        connectivity: ConnectivityObserver,
        offlineAnalytics: OfflineAnalytics,
        authRepository: AuthRepository = FakeAuth,
        analytics: com.jjundev.oneclickeng.core.analytics.AnalyticsSink =
            com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink(),
    ) = AppViewModel(
        authRepository = authRepository,
        profileRepository = FakeProfile,
        googleAccountLinker = FakeLinker,
        studytimeRepository = studytime,
        accountRepository = FakeAccount,
        accountResetBus = AccountResetBus(),
        connectivity = connectivity,
        offlineAnalytics = offlineAnalytics,
        analytics = analytics,
    )
```

- [ ] **Step 1b: Write the failing test** — add to `AppViewModelTest.kt`. `FakeAuth` resolves uid `"uid"` and (after Step 3b) is a guest; `FakeProfile.readLevel` returns `"easy"`:

```kotlin
    @Test
    fun `bootstrap stitches identity — user id plus auth_state and level properties`() =
        runTest {
            val analytics = com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink()
            appViewModel(
                studytime = RecordingStudytime(),
                connectivity = MutableConnectivity(Connectivity.Online),
                offlineAnalytics = RecordingOfflineAnalytics(),
                analytics = analytics,
            )
            advanceUntilIdle()

            assertEquals("uid", analytics.userId)
            assertEquals("guest", analytics.userProperties["auth_state"])
            assertEquals("easy", analytics.userProperties["level"])
        }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*AppViewModelTest"`
Expected: FAIL — `AppViewModel` has no `AnalyticsSink` param / `isAnonymous` unresolved on the fake.

- [ ] **Step 3a: Add `isAnonymous` to `AuthRepository`** — interface member + impl:

In the `interface AuthRepository` block, after `val currentUid: String?`:

```kotlin
    /**
     * True when the current session is the anonymous guest (no linked provider). Derived, never
     * stored — mirrors the `firebase.sign_in_provider` claim note. Drives the `auth_state`
     * analytics user property (guest|linked, analytics-events.md §3). Null user ⇒ treated as guest.
     */
    val isAnonymous: Boolean
```

In `FirebaseAuthRepository`, after `override val currentUid`:

```kotlin
        override val isAnonymous: Boolean
            get() = auth.currentUser?.isAnonymous ?: true
```

- [ ] **Step 3b: Update the AuthRepository fakes** the compiler flags. Known fakes:
  - `AppViewModelTest.kt` `object FakeAuth` → add `override val isAnonymous: Boolean = true` (guest — makes the identity test assert `auth_state == "guest"`).
  - `AppViewModelTest.kt` `class FailOnceAuth` → add `override val isAnonymous: Boolean = true`.
  - The onboarding-test `FakeAuthRepository(uid = …, ensuredUid = …)` (used by `OnboardingViewModelTest`) → add an `isAnonymous: Boolean = true` constructor param and `override val isAnonymous: Boolean = isAnonymous`.
  - `SettingsViewModelTest.kt`'s `FakeAuth` and `SavedCardReadAuthTest.kt`'s `FakeAuth` → add `override val isAnonymous: Boolean = true`.
  - Any other fake the compiler flags → add `override val isAnonymous: Boolean = true`. (Grep to be sure: `grep -rn "AuthRepository" android/app/src/test android/app/src/androidTest`.)

- [ ] **Step 3c: Wire identity into `AppViewModel`.** Add the constructor param (after `offlineAnalytics`):

```kotlin
        private val analytics: com.jjundev.oneclickeng.core.analytics.AnalyticsSink,
```

Add the pure helper next to `bootStateForLevel` (bottom of file):

```kotlin
/** guest|linked auth_state for the analytics user property (analytics-events.md §3). */
internal fun authStateFor(isAnonymous: Boolean): String = if (isAnonymous) "guest" else "linked"
```

Inside `bootstrap()`, set the user id + `auth_state` right after the pending-merge retry (so a resumed FR-3b merge's post-merge identity is the one stitched — §3b), and set `level` in `onSuccess`. Change the `runCatching { … }.onSuccess { … }` block to:

```kotlin
            runCatching {
                val uid = authRepository.ensureSignedIn()
                if (!resumedDelete) {
                    runCatching { googleAccountLinker.retryPendingMerge() }
                }
                // Identity as early as the first custom event (§3a): after any resumed merge so the
                // effective identity is stitched, before profile reads that could emit.
                analytics.setUserId(authRepository.currentUid ?: uid)
                analytics.setUserProperty("auth_state", authStateFor(authRepository.isAnonymous))
                profileRepository.ensureProfile(uid)
                profileRepository.readLevel(uid)
            }.onSuccess { level ->
                analytics.setUserProperty("level", level)
                _uiState.value = bootStateForLevel(level)
            }.onFailure {
                _uiState.value = BootState.AuthFailed
                runCatching { Log.w(TAG, "Guest bootstrap failed — showing retry gate", it) }
            }
```

> Preserves the existing structure exactly — the only additions are the three `analytics.*` lines. Do not otherwise reorder `ensureProfile`/`readLevel`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*AppViewModelTest"`
Expected: PASS, including the new identity test and all pre-existing `AppViewModelTest` cases.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/core/auth/AuthRepository.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppViewModel.kt android/app/src/test
git commit -m "feat(analytics): stitch boot identity (setUserId + auth_state/level properties)"
```

---

## Task 3: Onboarding-funnel dispatch (`FirebaseOnboardingAnalytics`)

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/OnboardingAnalytics.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/di/OnboardingModule.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/OnboardingAnalyticsDispatchTest.kt`

**Interfaces:**
- Consumes: `AnalyticsSink` (Task 1), the existing `OnboardingAnalytics` interface (unchanged).
- Produces: `class FirebaseOnboardingAnalytics @Inject constructor(sink: AnalyticsSink) : OnboardingAnalytics`. `NoOpOnboardingAnalytics` stays (fallback / other tests).

- [ ] **Step 0 (guard, not a code change): confirm the emit-site already calls the seam.**

Run: `grep -rn "onboardingAnalytics\.\|analytics\.levelSelected\|analytics\.topicSelected" android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding`
Expected: hits inside `OnboardingViewModel` / google-link VM. (`OnboardingViewModelTest` already asserts `levelSelected` fires — Phase 1 assumption holds.) If a method has **no** caller, note it in the commit body; still implement the mapping (the interface requires it).

- [ ] **Step 1: Write the failing contract test** (`OnboardingAnalyticsDispatchTest.kt`):

```kotlin
package com.jjundev.oneclickeng.feature.onboarding

import com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingAnalyticsDispatchTest {
    private val sink = RecordingAnalyticsSink()
    private val analytics = FirebaseOnboardingAnalytics(sink)

    @Test
    fun `onboarding_started carries is_returning`() {
        analytics.onboardingStarted(isReturning = true)
        assertEquals(
            RecordingAnalyticsSink.Event("onboarding_started", mapOf("is_returning" to true)),
            sink.events.single(),
        )
    }

    @Test
    fun `level_selected carries level`() {
        analytics.levelSelected("hard")
        assertEquals(
            RecordingAnalyticsSink.Event("level_selected", mapOf("level" to "hard")),
            sink.events.single(),
        )
    }

    @Test
    fun `topic_selected carries topic_id and beginner_friendly`() {
        analytics.topicSelected(topicId = "cafe_order", beginnerFriendly = true)
        assertEquals(
            RecordingAnalyticsSink.Event(
                "topic_selected",
                mapOf("topic_id" to "cafe_order", "beginner_friendly" to true),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `google_link_succeeded carries session_id`() {
        analytics.googleLinkSucceeded(sessionId = "sess-9")
        assertEquals(
            RecordingAnalyticsSink.Event("google_link_succeeded", mapOf("session_id" to "sess-9")),
            sink.events.single(),
        )
    }

    @Test
    fun `reauth_link_failed has no params`() {
        analytics.reauthLinkFailed()
        assertEquals(
            RecordingAnalyticsSink.Event("reauth_link_failed", emptyMap()),
            sink.events.single(),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*OnboardingAnalyticsDispatchTest"`
Expected: FAIL — `FirebaseOnboardingAnalytics` unresolved.

- [ ] **Step 3: Add `FirebaseOnboardingAnalytics`** to `OnboardingAnalytics.kt` (below `NoOpOnboardingAnalytics`, keep the `@Suppress("TooManyFunctions")`):

```kotlin
/** Firebase dispatch for the onboarding funnel (M4-01). Ids per plan Event-ID Decision Table. */
@Suppress("TooManyFunctions")
class FirebaseOnboardingAnalytics
    @Inject
    constructor(
        private val sink: com.jjundev.oneclickeng.core.analytics.AnalyticsSink,
    ) : OnboardingAnalytics {
        override fun onboardingStarted(isReturning: Boolean) =
            sink.log("onboarding_started", mapOf("is_returning" to isReturning))

        override fun levelSelected(level: String) = sink.log("level_selected", mapOf("level" to level))

        override fun topicSelected(
            topicId: String,
            beginnerFriendly: Boolean,
        ) = sink.log("topic_selected", mapOf("topic_id" to topicId, "beginner_friendly" to beginnerFriendly))

        override fun googleSavePromptShown(sessionId: String) =
            sink.log("google_save_prompt_shown", mapOf("session_id" to sessionId))

        override fun googleLinkSkipped(sessionId: String) =
            sink.log("google_link_skipped", mapOf("session_id" to sessionId))

        override fun googleLinkSucceeded(sessionId: String) =
            sink.log("google_link_succeeded", mapOf("session_id" to sessionId))

        override fun googleLinkConflictMerged(sessionId: String) =
            sink.log("google_link_conflict_merged", mapOf("session_id" to sessionId))

        override fun googleLinkFailed(sessionId: String) =
            sink.log("google_link_failed", mapOf("session_id" to sessionId))

        override fun reauthLinkSucceeded() = sink.log("reauth_link_succeeded")

        override fun reauthLinkConflictMerged() = sink.log("reauth_link_conflict_merged")

        override fun reauthLinkFailed() = sink.log("reauth_link_failed")
    }
```

- [ ] **Step 4: Flip the binding** in `OnboardingModule.kt` — change the import + bind target:

```kotlin
import com.jjundev.oneclickeng.feature.onboarding.FirebaseOnboardingAnalytics
```
```kotlin
    @Binds
    @Singleton
    abstract fun bindOnboardingAnalytics(impl: FirebaseOnboardingAnalytics): OnboardingAnalytics
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*OnboardingAnalyticsDispatchTest"`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/OnboardingAnalyticsDispatchTest.kt
git commit -m "feat(analytics): dispatch onboarding-funnel events to Firebase"
```

---

## Task 4: Home dispatch (`FirebaseHomeAnalytics`)

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeAnalytics.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/di/HomeModule.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeAnalyticsDispatchTest.kt`

**Interfaces:**
- Consumes: `AnalyticsSink`, existing `HomeAnalytics` interface.
- Produces: `class FirebaseHomeAnalytics @Inject constructor(sink: AnalyticsSink) : HomeAnalytics`.

- [ ] **Step 0 (guard):** `grep -rn "homeAnalytics\.\|Analytics" android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home` — confirm the Home VM injects & calls `HomeAnalytics`. Record any uncalled method in the commit body.

- [ ] **Step 1: Write the failing contract test** (`HomeAnalyticsDispatchTest.kt`) — covers the nullable-`topic_id` omit rule and the shared `offline_blocked_action{surface=home}`:

```kotlin
package com.jjundev.oneclickeng.feature.home

import com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAnalyticsDispatchTest {
    private val sink = RecordingAnalyticsSink()
    private val analytics = FirebaseHomeAnalytics(sink)

    @Test
    fun `paramless home events log the right ids`() {
        analytics.homeView()
        analytics.homeCtaTap()
        analytics.resumeContinue()
        analytics.resumeStartNew()
        assertEquals(
            listOf("home_view", "home_cta_tap", "resume_continue", "resume_start_new"),
            sink.events.map { it.name },
        )
        assertEquals(emptyMap<String, Any>(), sink.events.first().params)
    }

    @Test
    fun `topic_selected omits topic_id when null and carries custom`() {
        analytics.topicSelected(topicId = null, custom = true)
        assertEquals(
            RecordingAnalyticsSink.Event("topic_selected", mapOf("custom" to true)),
            sink.events.single(),
        )
    }

    @Test
    fun `topic_selected includes topic_id for a curated seed`() {
        analytics.topicSelected(topicId = "cafe_order", custom = false)
        assertEquals(
            RecordingAnalyticsSink.Event(
                "topic_selected",
                mapOf("topic_id" to "cafe_order", "custom" to false),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `session_setting_changed carries level and length`() {
        analytics.sessionSettingChanged(level = "normal", length = 10)
        assertEquals(
            RecordingAnalyticsSink.Event(
                "session_setting_changed",
                mapOf("level" to "normal", "length" to 10L),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `offlineBlocked reuses offline_blocked_action with surface home`() {
        analytics.offlineBlocked()
        assertEquals(
            RecordingAnalyticsSink.Event("offline_blocked_action", mapOf("surface" to "home")),
            sink.events.single(),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*HomeAnalyticsDispatchTest"`
Expected: FAIL — `FirebaseHomeAnalytics` unresolved.

- [ ] **Step 3: Add `FirebaseHomeAnalytics`** to `HomeAnalytics.kt` (below `NoOpHomeAnalytics`):

```kotlin
/** Firebase dispatch for the home entry funnel (M4-01). Ids per plan Event-ID Decision Table. */
class FirebaseHomeAnalytics
    @Inject
    constructor(
        private val sink: com.jjundev.oneclickeng.core.analytics.AnalyticsSink,
    ) : HomeAnalytics {
        override fun homeView() = sink.log("home_view")

        override fun homeCtaTap() = sink.log("home_cta_tap")

        override fun resumeContinue() = sink.log("resume_continue")

        override fun resumeStartNew() = sink.log("resume_start_new")

        override fun topicSelected(
            topicId: String?,
            custom: Boolean,
        ) = sink.log(
            "topic_selected",
            buildMap {
                topicId?.let { put("topic_id", it) } // null ⇒ omit key (never log "null")
                put("custom", custom)
            },
        )

        override fun sessionSettingChanged(
            level: String,
            length: Int,
        ) = sink.log("session_setting_changed", mapOf("level" to level, "length" to length.toLong()))

        override fun offlineBlocked() = sink.log("offline_blocked_action", mapOf("surface" to "home"))
    }
```

- [ ] **Step 4: Flip the binding** in `HomeModule.kt`:

```kotlin
import com.jjundev.oneclickeng.feature.home.FirebaseHomeAnalytics
```
```kotlin
    @Binds
    @Singleton
    abstract fun bindHomeAnalytics(impl: FirebaseHomeAnalytics): HomeAnalytics
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*HomeAnalyticsDispatchTest"`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeAnalyticsDispatchTest.kt
git commit -m "feat(analytics): dispatch home entry-funnel events to Firebase"
```

---

## Task 5: Limit + WaitQuiz dispatch (`core/network`)

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/network/LimitAnalytics.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/network/WaitQuizAnalytics.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/network/DialogueModule.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/network/LimitWaitQuizAnalyticsDispatchTest.kt`

**Interfaces:**
- Consumes: `AnalyticsSink`, existing `LimitAnalytics` + `WaitQuizAnalytics` interfaces.
- Produces: `class FirebaseLimitAnalytics @Inject constructor(sink: AnalyticsSink) : LimitAnalytics`, `class FirebaseWaitQuizAnalytics @Inject constructor(sink: AnalyticsSink) : WaitQuizAnalytics`.

- [ ] **Step 0 (guard):** `grep -rn "limitAnalytics\.\|waitQuizAnalytics\.\|LimitAnalytics\|WaitQuizAnalytics" android/app/src/main/kotlin/com/jjundev/oneclickeng/feature android/app/src/main/kotlin/com/jjundev/oneclickeng/core/network` — confirm both seams have callers. Record uncalled methods in the commit body.

- [ ] **Step 1: Write the failing contract test** (`LimitWaitQuizAnalyticsDispatchTest.kt`) — pins `limit_reached` params and the wait-quiz nullable-`session_id` omit rule:

```kotlin
package com.jjundev.oneclickeng.core.network

import com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink
import org.junit.Assert.assertEquals
import org.junit.Test

class LimitWaitQuizAnalyticsDispatchTest {
    private val sink = RecordingAnalyticsSink()

    @Test
    fun `limit_reached carries remaining and surface`() {
        FirebaseLimitAnalytics(sink).limitReached(remaining = 0, surface = "dialogue_start_gate")
        assertEquals(
            RecordingAnalyticsSink.Event(
                "limit_reached",
                mapOf("remaining" to 0L, "surface" to "dialogue_start_gate"),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `wait_quiz_card_answered carries all fields and omits null session_id`() {
        FirebaseWaitQuizAnalytics(sink).cardAnswered(
            sessionId = null,
            cardId = "q7",
            choseCorrect = true,
            cardIndex = 2,
        )
        assertEquals(
            RecordingAnalyticsSink.Event(
                "wait_quiz_card_answered",
                mapOf("card_id" to "q7", "chose_correct" to true, "card_index" to 2L),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `wait_quiz_card_answered includes session_id when present`() {
        FirebaseWaitQuizAnalytics(sink).cardAnswered(
            sessionId = "sess-3",
            cardId = "q1",
            choseCorrect = false,
            cardIndex = 0,
        )
        assertEquals(
            mapOf(
                "session_id" to "sess-3",
                "card_id" to "q1",
                "chose_correct" to false,
                "card_index" to 0L,
            ),
            sink.events.single().params,
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*LimitWaitQuizAnalyticsDispatchTest"`
Expected: FAIL — `FirebaseLimitAnalytics` / `FirebaseWaitQuizAnalytics` unresolved.

- [ ] **Step 3a: Add `FirebaseLimitAnalytics`** to `LimitAnalytics.kt` (below `NoOpLimitAnalytics`):

```kotlin
/** Firebase dispatch (M4-01). `limit_reached {remaining, surface}` — analytics-events.md §4/§6.5. */
class FirebaseLimitAnalytics
    @Inject
    constructor(
        private val sink: com.jjundev.oneclickeng.core.analytics.AnalyticsSink,
    ) : LimitAnalytics {
        override fun limitReached(
            remaining: Int,
            surface: String,
        ) = sink.log("limit_reached", mapOf("remaining" to remaining.toLong(), "surface" to surface))
    }
```

- [ ] **Step 3b: Add `FirebaseWaitQuizAnalytics`** to `WaitQuizAnalytics.kt` (below `NoOpWaitQuizAnalytics`):

```kotlin
/** Firebase dispatch (M4-01). `wait_quiz_card_answered` — analytics-events.md §4. */
class FirebaseWaitQuizAnalytics
    @Inject
    constructor(
        private val sink: com.jjundev.oneclickeng.core.analytics.AnalyticsSink,
    ) : WaitQuizAnalytics {
        override fun cardAnswered(
            sessionId: String?,
            cardId: String,
            choseCorrect: Boolean,
            cardIndex: Int,
        ) = sink.log(
            "wait_quiz_card_answered",
            buildMap {
                sessionId?.let { put("session_id", it) }
                put("card_id", cardId)
                put("chose_correct", choseCorrect)
                put("card_index", cardIndex.toLong())
            },
        )
    }
```

- [ ] **Step 4: Flip both bindings** in `DialogueModule.kt` — change the two bind targets from `NoOp…` to `Firebase…` (add imports as needed):

```kotlin
    @Binds
    @Singleton
    abstract fun bindWaitQuizAnalytics(impl: FirebaseWaitQuizAnalytics): WaitQuizAnalytics

    @Binds
    @Singleton
    abstract fun bindLimitAnalytics(impl: FirebaseLimitAnalytics): LimitAnalytics
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*LimitWaitQuizAnalyticsDispatchTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/core/network android/app/src/test/kotlin/com/jjundev/oneclickeng/core/network/LimitWaitQuizAnalyticsDispatchTest.kt
git commit -m "feat(analytics): dispatch limit_reached and wait_quiz_card_answered to Firebase"
```

---

## Task 6: Offline dispatch (`FirebaseOfflineAnalytics`)

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/connectivity/OfflineAnalytics.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/connectivity/ConnectivityModule.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/connectivity/OfflineAnalyticsDispatchTest.kt`

**Interfaces:**
- Consumes: `AnalyticsSink`, existing `OfflineAnalytics` interface (emit-site is `AppViewModel.observeConnectivity()` — already calls `connectivityChanged`, confirmed in Task 2's file).
- Produces: `class FirebaseOfflineAnalytics @Inject constructor(sink: AnalyticsSink) : OfflineAnalytics`.

- [ ] **Step 1: Write the failing contract test** (`OfflineAnalyticsDispatchTest.kt`):

```kotlin
package com.jjundev.oneclickeng.core.connectivity

import com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineAnalyticsDispatchTest {
    private val sink = RecordingAnalyticsSink()
    private val analytics = FirebaseOfflineAnalytics(sink)

    @Test
    fun `connectivity_changed carries online flag`() {
        analytics.connectivityChanged(online = false)
        assertEquals(
            RecordingAnalyticsSink.Event("connectivity_changed", mapOf("online" to false)),
            sink.events.single(),
        )
    }

    @Test
    fun `offline_blocked_action carries surface`() {
        analytics.offlineBlocked(surface = "dialogue_start_gate")
        assertEquals(
            RecordingAnalyticsSink.Event(
                "offline_blocked_action",
                mapOf("surface" to "dialogue_start_gate"),
            ),
            sink.events.single(),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*OfflineAnalyticsDispatchTest"`
Expected: FAIL — `FirebaseOfflineAnalytics` unresolved.

- [ ] **Step 3: Add `FirebaseOfflineAnalytics`** to `OfflineAnalytics.kt` (below `NoOpOfflineAnalytics`):

```kotlin
/** Firebase dispatch (M4-01). `connectivity_changed` + `offline_blocked_action` — exception-states.md §9. */
class FirebaseOfflineAnalytics
    @Inject
    constructor(
        private val sink: com.jjundev.oneclickeng.core.analytics.AnalyticsSink,
    ) : OfflineAnalytics {
        override fun connectivityChanged(online: Boolean) =
            sink.log("connectivity_changed", mapOf("online" to online))

        override fun offlineBlocked(surface: String) =
            sink.log("offline_blocked_action", mapOf("surface" to surface))
    }
```

- [ ] **Step 4: Flip the binding** in `ConnectivityModule.kt`:

```kotlin
    @Binds
    @Singleton
    abstract fun bindOfflineAnalytics(impl: FirebaseOfflineAnalytics): OfflineAnalytics
```

Also update the module's doc comment line (`- [OfflineAnalytics] → [NoOpOfflineAnalytics] …`) to point at `FirebaseOfflineAnalytics`.

- [ ] **Step 5: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*OfflineAnalyticsDispatchTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Full verification (build + all unit tests + detekt/ktlint via the check task)**

Run: `./scripts/verify-android.sh check`
Expected: BUILD SUCCESSFUL — all unit tests green, no detekt/ktlint regressions, Hilt graph compiles with every analytics binding resolved (proves no `NoOp*`/`Firebase*` duplicate-binding or missing-`AnalyticsSink` error).

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/core/connectivity android/app/src/test/kotlin/com/jjundev/oneclickeng/core/connectivity/OfflineAnalyticsDispatchTest.kt
git commit -m "feat(analytics): dispatch connectivity_changed and offline_blocked_action to Firebase"
```

---

## Manual Checkpoint B (final — human, GA4 DebugView)

> M4-01 acceptance criterion: "디버그뷰에서 이벤트 도달 확인." Requires a device/emulator + the Firebase console; an agent cannot do it. Run after Task 6.

- [ ] Enable DebugView: `adb shell setprop debug.firebase.analytics.app com.jjundev.oneclickeng`, launch a debug build.
- [ ] Fresh-install path: confirm **user id is set** and user properties `auth_state=guest`, `level` appear once onboarding completes.
- [ ] Walk the funnel and confirm each event arrives with the Decision-Table id + params (no free text): `onboarding_started`, `level_selected`, `topic_selected`, `home_view`, `home_cta_tap`, `resume_continue`/`resume_start_new`, `session_setting_changed`, `limit_reached`, `wait_quiz_card_answered`, `connectivity_changed` (toggle airplane mode), `offline_blocked_action`.
- [ ] Back-fill **every "Finalized here" id** into `docs/ux/analytics-events.md` so the doc stays the single source of truth: the google-link rows, the home rows (`home_view`/`home_cta_tap`/`resume_continue`/`resume_start_new`/`session_setting_changed`/home `topic_selected{custom}`), and — importantly — the two offline events **`connectivity_changed` and `offline_blocked_action`, which currently live only in `exception-states.md` §9 and are absent from `analytics-events.md`**. Record any DebugView-driven id correction there too.

---

## Phase 2 (M4-01b) — not in this plan, for the follow-up

New emit-sites inside feature VMs, each with its own transition contract (`analytics-events.md` §4/§5). **Note (surfaced during Phase 1 Task 4):** the home seam's `topicSelected` and `sessionSettingChanged` were wired to dispatch in Phase 1 but have **no caller in the home ViewModel yet** — their emit-sites (the re-visit topic picker + the collapsed session-settings control firing `HomeAnalytics.topicSelected`/`sessionSettingChanged`) belong here. Until then those two `home` events do not fire. Full list: `first_session_generation_started`, `first_session_started`, `learning_session_started`, `turn_started`, `turn_completed` (+`writing_score`, `input_mode`), `speaking_analyze_result`, `deep_feedback_opened`, `session_complete` (+`turn_count`, `is_first`), `summary_partial_failure`, `saved_card_create`, `mic_permission_requested`/`_result`, `wait_quiz_shown`/`wait_quiz_ended`, the `*_latency_ms` series, and the **link-time `setUserId` re-call** (§3b, at `google_link_succeeded`/merge-done). Each reuses the `AnalyticsSink` from Task 1.

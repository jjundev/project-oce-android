# Lifetime Stats Real-Data Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the 기록(Records) tab's lifetime XP / cumulative study-time / study-days header show real, server-reconciled values instead of a hardcoded stub, and close the one confirmed gap where those values can permanently diverge from the server (a guest device that links to an existing Google account).

**Architecture:** Three independent seams, wired bottom-up:
1. `StudytimeStore` (local DataStore, the client authority for studytime) gets a `reconcileFromServer` operation that adopts the server's total when it is larger than the local one.
2. `GoogleAccountLinker` calls that reconciliation (via `StudytimeRepository.reconcileAfterMerge()`) right after a successful guest→Google merge, because the server-side merge is additive and this device never otherwise learns about the target account's pre-existing total.
3. `LifetimeStatsSource` gets a real Firestore-backed implementation (`FirestoreLifetimeStatsSource`) that reads XP/studyDays from the server-authoritative `gamification/progress` document and combines them with the local (client-authoritative) studytime total — replacing `StubLifetimeStatsSource`, which is deleted. `RecordsViewModel.refresh()` is extended to re-fetch lifetime stats (not just cards) so a pull-to-refresh / tab re-entry converges to the latest server truth.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt DI, Firebase Firestore (Android SDK), DataStore Preferences, JUnit4 + Robolectric (existing test stack, no new libraries).

## Global Constraints

- `users/{uid}/gamification/progress` is Functions-only (`allow write: if false`); client may only **read** it. Fields: `xp`(number), `streak`(number), `studyDays`(number), `lastStudyDate`(string `yyyy-MM-dd`). [docs/design/firestore-schema.md:92-98]
- `users/{uid}/gamification/studytime` is client RW. Fields: `totalSeconds`(number, monotonic — security rule enforces `request.resource.data.totalSeconds >= resource.data.totalSeconds` on update), `today:{dayKey,seconds}`(display-only). [docs/design/firestore-schema.md:104-108,228-231]
- The server-side guest→Google merge is **additive**: `resolveStudytimeTotal` sets the target's post-merge total to `target.totalSeconds + guest.totalSeconds`. It never touches `progress`/`progress_marks` directly — instead it copies `point_ledger` entries create-only so the target's own `onLedgerCreate` trigger re-derives `progress`. [functions/src/merge/merge.ts:75-88,131-138]
- `StudytimeStore` (android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/gamification/data/StudytimeStore.kt) is the documented **client authority** for studytime — read-modify-write semantics, not Firestore-native offline writes — precisely because several offline sessions must accumulate rather than overwrite. Do not change this ownership.
- This codebase's established test convention: classes that directly touch `FirebaseFirestore`/`FirebaseAuth` (e.g. `FirestoreStudytimeRepository`, `FirebaseGoogleAccountLinker`) have **no JVM unit tests** — there is no mocking harness for the Firestore SDK in this repo. Testable logic is always extracted into small pure functions (precedent: `GamificationTime.advanceStreak`, `resolvePendingMergeAction`, `resolveStudytimeTotal`) and unit-tested directly; the Firebase-touching wrapper stays untested at the unit level. Follow this precedent — do not attempt to mock `FirebaseFirestore`/`DocumentSnapshot`.
- Run tests via `scripts/verify-android.sh :app:testDebugUnitTest --tests '<Pattern>'` (see docs/agents/android-verification.md) — do not run bare `./gradlew` in this worktree (shared `~/.gradle` cache corrupts results).
- No new third-party dependencies.

---

### Task 1: `StudytimeStore.reconcileFromServer` — adopt a larger server total

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/gamification/data/StudytimeStore.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/gamification/data/StudytimeStoreTest.kt`

**Interfaces:**
- Produces: `StudytimeStore.reconcileFromServer(serverTotalSeconds: Long): Unit` (suspend). Adopts `serverTotalSeconds` into `KEY_TOTAL` and clears `KEY_UNSYNCED` **only if** `serverTotalSeconds > current local total`; otherwise a no-op. Consumed by Task 2's `StudytimeRepository.reconcileAfterMerge()`.

- [ ] **Step 1: Write the failing tests**

Append to `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/gamification/data/StudytimeStoreTest.kt`, just before the final closing `}` of the class:

```kotlin
    @Test
    fun `reconcileFromServer adopts a larger server total after a guest-to-google merge`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)
            store.accrue("s1", 300, "2026-07-04") // local total = 300 (this device's guest-only portion)

            // Server total after merge = pre-existing target total (2200s, studied on another device)
            // + this device's guest total (300s), per merge.ts:resolveStudytimeTotal.
            store.reconcileFromServer(serverTotalSeconds = 2500)

            val snap = store.snapshot()
            assertEquals(2500L, snap.totalSeconds)
            assertFalse("adopted value already matches server — no re-push needed", snap.unsynced)

            scope.cancel()
        }

    @Test
    fun `reconcileFromServer is a no-op when local total already covers the server value`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)
            store.accrue("s1", 300, "2026-07-04")
            store.accrue("s2", 400, "2026-07-05") // local total = 700, unsynced = true

            store.reconcileFromServer(serverTotalSeconds = 500) // stale/behind read

            val snap = store.snapshot()
            assertEquals(700L, snap.totalSeconds)
            assertTrue("local ahead of server — still needs its own push", snap.unsynced)

            scope.cancel()
        }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*StudytimeStoreTest*'`
Expected: **compile failure** — `reconcileFromServer` is unresolved (the method doesn't exist yet).

- [ ] **Step 3: Implement `reconcileFromServer`**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/gamification/data/StudytimeStore.kt`, insert this method immediately after `seedIfEmpty` (i.e., right before the `/** Zero the local gamification authority ... */` KDoc that precedes `reset()`):

```kotlin
        /**
         * Reconcile the local authority with the server's post-merge total (M3-03 게스트→Google 이관).
         * The server merge is ADDITIVE — target's post-merge total = pre-existing target total + this
         * device's guest total ([functions/src/merge/merge.ts] `resolveStudytimeTotal`) — so this device's
         * local total only ever reflects ITS OWN portion; after a merge the server total can exceed it.
         * Adopts [serverTotalSeconds] only when it's the LARGER value (never regresses a local total that
         * has since grown further from new sessions) and marks the state synced when it does (the local
         * total now already matches what's on the server, so the next [drain]/push has nothing new to send).
         */
        suspend fun reconcileFromServer(serverTotalSeconds: Long) {
            dataStore.edit { p ->
                val local = p[KEY_TOTAL] ?: 0L
                if (serverTotalSeconds > local) {
                    p[KEY_TOTAL] = serverTotalSeconds
                    p[KEY_UNSYNCED] = false
                }
            }
        }

```

- [ ] **Step 4: Run tests to verify they pass**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*StudytimeStoreTest*'`
Expected: PASS (all `StudytimeStoreTest` cases, including the 2 new ones).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/gamification/data/StudytimeStore.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/gamification/data/StudytimeStoreTest.kt
git commit -m "feat(gamification): add StudytimeStore.reconcileFromServer for post-merge sync"
```

---

### Task 2: `StudytimeRepository.reconcileAfterMerge()` wired into `GoogleAccountLinker`

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/gamification/StudytimeRepository.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/auth/GoogleAccountLinker.kt`

**Interfaces:**
- Consumes: `StudytimeStore.reconcileFromServer(serverTotalSeconds: Long)` (Task 1).
- Produces: `StudytimeRepository.reconcileAfterMerge(): Unit` (suspend), added to the `StudytimeRepository` interface and its `FirestoreStudytimeRepository` implementation. Called from `FirebaseGoogleAccountLinker` after every successful `mergeGuestData` call.

**No unit test for this task** — per the Global Constraints, this codebase has no JVM test harness for classes that call `FirebaseFirestore`/`FirebaseAuth` directly (`FirestoreStudytimeRepository` and `FirebaseGoogleAccountLinker` have none today; confirmed via repo search — no test file references either class). The reconciliation *logic* itself is already covered by Task 1's `StudytimeStoreTest`; this task is pure wiring. Verification is the detekt/compile pass in Step 3.

- [ ] **Step 1: Add `reconcileAfterMerge()` to `StudytimeRepository` and implement it**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/gamification/StudytimeRepository.kt`, add this method to the `StudytimeRepository` interface, immediately after `drain()` and before `resetMetrics()`:

```kotlin
    /**
     * Force-reconcile the local studytime authority after a successful guest→Google merge (M3-03). The
     * server-side merge adds this device's (guest) total onto the target account's pre-existing total
     * (`merge.ts` `resolveStudytimeTotal`) — a total this device never otherwise observes. Re-reads the
     * server's post-merge `totalSeconds` and adopts it if larger than the local total. A no-op if signed
     * out or offline; the display may understate the merged total until a later successful call (the next
     * app-start `seedFromServerIfEmpty`/`drain` pass does not retry this — see the follow-up note below).
     */
    suspend fun reconcileAfterMerge()
```

Then implement it in `FirestoreStudytimeRepository`, immediately after the `drain()` implementation and before `resetMetrics()`:

```kotlin
        @Suppress("TooGenericExceptionCaught")
        override suspend fun reconcileAfterMerge() {
            val uid = authRepository.currentUid ?: return
            try {
                val studytime =
                    firestore
                        .collection(USERS).document(uid)
                        .collection(GAMIFICATION).document(STUDYTIME)
                        .get().await()
                store.reconcileFromServer(studytime.getLong(FIELD_TOTAL_SECONDS) ?: 0L)
            } catch (e: Exception) {
                Log.d(TAG, "post-merge studytime reconcile skipped (offline/permission): ${e.message}")
            }
        }
```

(This reuses the existing `USERS`, `GAMIFICATION`, `STUDYTIME`, `FIELD_TOTAL_SECONDS` private companion constants already declared in this class — no new constants needed.)

- [ ] **Step 2: Wire the call into `GoogleAccountLinker`**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/auth/GoogleAccountLinker.kt`, add the import:

```kotlin
import com.jjundev.oneclickeng.feature.gamification.StudytimeRepository
```

Add a constructor parameter to `FirebaseGoogleAccountLinker`:

```kotlin
@Singleton
class FirebaseGoogleAccountLinker
    @Inject
    constructor(
        private val auth: FirebaseAuth,
        private val functions: FirebaseFunctions,
        private val pendingStore: PendingMergeStore,
        private val studytimeRepository: StudytimeRepository,
    ) : GoogleAccountLinker {
```

In `linkGuest()`, change the FR-3b(c)(d) block from:

```kotlin
            // FR-3b (c)(d): 이관 콜러블 호출 → 성공 시 마커 삭제.
            return try {
                callMergeGuestData(guestToken)
                pendingStore.clear()
                LinkOutcome.Merged
            } catch (e: Exception) {
```

to:

```kotlin
            // FR-3b (c)(d): 이관 콜러블 호출 → 성공 시 마커 삭제 + 로컬 studytime 재동기화.
            return try {
                callMergeGuestData(guestToken)
                pendingStore.clear()
                studytimeRepository.reconcileAfterMerge()
                LinkOutcome.Merged
            } catch (e: Exception) {
```

In `retryPendingMerge()`, change:

```kotlin
                PendingMergeAction.Merge ->
                    try {
                        callMergeGuestData(pending.guestToken)
                        pendingStore.clear()
                        LinkOutcome.Merged
                    } catch (e: Exception) {
```

to:

```kotlin
                PendingMergeAction.Merge ->
                    try {
                        callMergeGuestData(pending.guestToken)
                        pendingStore.clear()
                        studytimeRepository.reconcileAfterMerge()
                        LinkOutcome.Merged
                    } catch (e: Exception) {
```

- [ ] **Step 3: Verify the module compiles (no test harness for this class — see task note)**

Run: `scripts/verify-android.sh :app:detekt :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest --tests '*StudytimeStoreTest*'`
Expected: BUILD SUCCESSFUL, no detekt violations, `StudytimeStoreTest` still passes (confirms nothing in Task 1 regressed).

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/gamification/StudytimeRepository.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/core/auth/GoogleAccountLinker.kt
git commit -m "fix(auth): reconcile local studytime total after guest-to-Google merge"
```

---

### Task 3: `FirestoreLifetimeStatsSource` — real data for the Records-tab header

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/FirestoreLifetimeStatsSource.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/LifetimeStats.kt` (remove `StubLifetimeStatsSource`, update docs)
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsModule.kt` (rebind)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/FirestoreLifetimeStatsSourceTest.kt` (new)

**Interfaces:**
- Consumes: `LifetimeStats(xp: Int, studyMinutes: Int, studyDays: Int)` and `LifetimeStatsSource { suspend fun lifetime(): LifetimeStats? }` (both already exist in `LifetimeStats.kt`); `StudytimeStore.snapshot(): StudytimeStore.State` (already exists); `AuthRepository.currentUid: String?` (already exists).
- Produces: `internal fun toLifetimeStats(progressXp: Long?, progressStudyDays: Long?, localTotalSeconds: Long): LifetimeStats` (pure, unit-tested directly) and `class FirestoreLifetimeStatsSource : LifetimeStatsSource` (the Firebase-touching wrapper around it, consumed by `RecordsModule`'s DI binding and by Task 4's `RecordsViewModel`, which only ever calls the `LifetimeStatsSource.lifetime()` interface method — no direct dependency on this class's internals).

- [ ] **Step 1: Write the failing test for the pure mapping function**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/FirestoreLifetimeStatsSourceTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.records

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [toLifetimeStats] pure-combine coverage (M3-05 실데이터 배선). The Firestore-touching
 * [FirestoreLifetimeStatsSource] wrapper itself has no unit test — this codebase has no mocking harness
 * for `FirebaseFirestore`/`DocumentSnapshot` (see `StudytimeStoreTest`/merge.ts precedent of testing pure
 * decision functions only); all the branching logic worth testing lives in this pure function instead.
 */
class FirestoreLifetimeStatsSourceTest {
    @Test
    fun `maps server xp and studyDays with local total seconds converted to minutes`() {
        val stats = toLifetimeStats(progressXp = 1240L, progressStudyDays = 12L, localTotalSeconds = 8100L)

        assertEquals(1240, stats.xp)
        assertEquals(12, stats.studyDays)
        assertEquals(135, stats.studyMinutes) // 8100s / 60 = 135m
    }

    @Test
    fun `absent progress fields default to zero`() {
        val stats = toLifetimeStats(progressXp = null, progressStudyDays = null, localTotalSeconds = 0L)

        assertEquals(0, stats.xp)
        assertEquals(0, stats.studyDays)
        assertEquals(0, stats.studyMinutes)
    }

    @Test
    fun `sub-minute local total truncates down to zero minutes`() {
        val stats = toLifetimeStats(progressXp = 5L, progressStudyDays = 1L, localTotalSeconds = 45L)

        assertEquals(0, stats.studyMinutes)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*FirestoreLifetimeStatsSourceTest*'`
Expected: **compile failure** — `toLifetimeStats` is unresolved (doesn't exist yet).

- [ ] **Step 3: Create `FirestoreLifetimeStatsSource.kt`**

Create `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/FirestoreLifetimeStatsSource.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.records

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.feature.gamification.data.StudytimeStore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val SECONDS_PER_MINUTE = 60L

/**
 * Real [LifetimeStatsSource] (M3-05 실데이터 배선 — replaces the former `StubLifetimeStatsSource`). XP and
 * study-days come from the server-authoritative `gamification/progress` document (Functions-only write,
 * firestore-schema.md §5); study minutes come from the LOCAL [StudytimeStore] instead of a second Firestore
 * read, because studytime's client copy is the documented authority (StudytimeStore.kt) and is always at
 * least as fresh as the server (server push is fire-and-forget and can lag).
 */
@Singleton
class FirestoreLifetimeStatsSource
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val authRepository: AuthRepository,
        private val studytimeStore: StudytimeStore,
    ) : LifetimeStatsSource {
        @Suppress("TooGenericExceptionCaught")
        override suspend fun lifetime(): LifetimeStats? {
            val uid = authRepository.currentUid ?: return null
            return try {
                val progress =
                    firestore
                        .collection(USERS).document(uid)
                        .collection(GAMIFICATION).document(PROGRESS)
                        .get().await()
                toLifetimeStats(
                    progressXp = progress.getLong(FIELD_XP),
                    progressStudyDays = progress.getLong(FIELD_STUDY_DAYS),
                    localTotalSeconds = studytimeStore.snapshot().totalSeconds,
                )
            } catch (e: Exception) {
                Log.d(TAG, "lifetime stats read skipped (offline/absent): ${e.message}")
                null
            }
        }

        private companion object {
            const val TAG = "LifetimeStatsSource"
            const val USERS = "users"
            const val GAMIFICATION = "gamification"
            const val PROGRESS = "progress"
            const val FIELD_XP = "xp"
            const val FIELD_STUDY_DAYS = "studyDays"
        }
    }

/** Pure combine — server `progress` fields (nullable: absent for a brand-new, not-yet-completed profile). */
internal fun toLifetimeStats(
    progressXp: Long?,
    progressStudyDays: Long?,
    localTotalSeconds: Long,
): LifetimeStats =
    LifetimeStats(
        xp = (progressXp ?: 0L).toInt(),
        studyMinutes = (localTotalSeconds / SECONDS_PER_MINUTE).toInt(),
        studyDays = (progressStudyDays ?: 0L).toInt(),
    )
```

- [ ] **Step 4: Run test to verify it passes**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*FirestoreLifetimeStatsSourceTest*'`
Expected: PASS (all 3 cases).

- [ ] **Step 5: Remove the stub and update docs in `LifetimeStats.kt`**

Replace the entire contents of `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/LifetimeStats.kt` with:

```kotlin
package com.jjundev.oneclickeng.feature.records

/**
 * 기록 탭 누적 통계 3지표(gamification §8·04-screen-06-history.md R1). `누적 N XP · 총 N시간 N분 · N일 학습`.
 */
data class LifetimeStats(
    val xp: Int,
    val studyMinutes: Int,
    val studyDays: Int,
)

/**
 * 누적 통계 소스 seam. 실데이터는 [FirestoreLifetimeStatsSource]가 공급한다 — 서버 `gamification/progress`의
 * xp/studyDays(Functions 전용 권위) + 로컬 [com.jjundev.oneclickeng.feature.gamification.data.StudytimeStore]의
 * 학습시간(클라 권위)을 합성한다.
 */
interface LifetimeStatsSource {
    /** 오프라인이고 Firestore 캐시도 없는 등 데이터를 읽을 수 없으면 `null`(헤더 정적 0 스냅). 그 외엔 실제 누적 통계. */
    suspend fun lifetime(): LifetimeStats?
}
```

- [ ] **Step 6: Rebind `LifetimeStatsSource` in `RecordsModule.kt`**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsModule.kt`, change:

```kotlin
    @Binds
    @Singleton
    abstract fun bindLifetimeStatsSource(impl: StubLifetimeStatsSource): LifetimeStatsSource
```

to:

```kotlin
    @Binds
    @Singleton
    abstract fun bindLifetimeStatsSource(impl: FirestoreLifetimeStatsSource): LifetimeStatsSource
```

Also update the class KDoc comment above (currently `- [LifetimeStatsSource] → [StubLifetimeStatsSource] (M3-05 배선 전 스텁 — 헤더 정적 0).`) to:

```kotlin
 * - [LifetimeStatsSource] → [FirestoreLifetimeStatsSource] (서버 progress + 로컬 studytime 합성).
```

- [ ] **Step 7: Run the full Records + gamification test suites**

Run: `scripts/verify-android.sh :app:detekt :app:testDebugUnitTest --tests '*Records*' --tests '*Lifetime*' --tests '*Studytime*'`
Expected: BUILD SUCCESSFUL, all pass (confirms `RecordsViewModelTest`'s `FakeLifetimeStatsSource`-based cases are unaffected by the stub's removal, since they never referenced the stub directly).

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/FirestoreLifetimeStatsSource.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/LifetimeStats.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsModule.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/FirestoreLifetimeStatsSourceTest.kt
git commit -m "feat(records): wire real lifetime stats source, remove stub"
```

---

### Task 4: `RecordsViewModel.refresh()` re-fetches lifetime stats

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModel.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModelTest.kt`

**Interfaces:**
- Consumes: `LifetimeStatsSource.lifetime(): LifetimeStats?` (existing interface, now backed by Task 3's real implementation in production — irrelevant to this task's fakes).
- Produces: no new public API; `RecordsViewModel.refresh()`'s existing behavior (reload the current tab's first page) gains a side effect (re-fetch `lifetime`).

**Why this task:** `lifetime` is currently fetched exactly once, in `init`. If a `RecordsViewModel` instance survives a navigation away-and-back (same backstack entry) or the user pulls to refresh, XP/study-time earned since only shows after a fresh process start. `refresh()`/`refreshOnResume()` already exist as the tab's "re-sync with server" entry point for cards; this task extends the same entry point to `lifetime` so the header converges to server truth without requiring a process restart.

- [ ] **Step 1: Write the failing test**

In `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModelTest.kt`, first change the private fake's field from `val` to `var` so the test can mutate it after construction — replace:

```kotlin
private class FakeLifetimeStatsSource(private val value: LifetimeStats?) : LifetimeStatsSource {
    override suspend fun lifetime(): LifetimeStats? = value
}
```

with:

```kotlin
private class FakeLifetimeStatsSource(var value: LifetimeStats?) : LifetimeStatsSource {
    override suspend fun lifetime(): LifetimeStats? = value
}
```

Then add this test inside the `RecordsViewModelTest` class, after the `exposes due count from review source` test (before the closing `}` of the class):

```kotlin
    @Test
    fun `refresh refetches lifetime stats from the source`() =
        runTest(dispatcher) {
            val lifetimeSource = FakeLifetimeStatsSource(LifetimeStats(xp = 100, studyMinutes = 30, studyDays = 3))
            val viewModel =
                RecordsViewModel(
                    FakeQuerySource(),
                    FakeSavedCardRepository(),
                    lifetimeSource,
                    RecordingHistoryAnalytics(),
                    HistoryCountUpGate(),
                    com.jjundev.oneclickeng.feature.review.FakeReviewSource(),
                    object : com.jjundev.oneclickeng.feature.review.data.ReviewClock { override fun nowMs() = 0L },
                )
            advanceUntilIdle()
            assertEquals(100, viewModel.uiState.value.lifetime?.xp)

            lifetimeSource.value = LifetimeStats(xp = 250, studyMinutes = 60, studyDays = 5)
            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(250, viewModel.uiState.value.lifetime?.xp)
        }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*RecordsViewModelTest*'`
Expected: FAIL on the new test — `assertEquals(250, viewModel.uiState.value.lifetime?.xp)` fails because `lifetime` is still `100` (refresh doesn't touch it yet).

- [ ] **Step 3: Implement the refetch in `RecordsViewModel`**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModel.kt`, change `refresh()` from:

```kotlin
        fun refresh() {
            val cardType = selected
            val state = typeStates.getValue(cardType)
            if (state.loading) return

            refreshing = true
            typeStates[cardType] =
                state.copy(
                    cards = emptyList(),
                    cursor = null,
                    endReached = false,
                )
            loadFirstPage(cardType)
        }
```

to:

```kotlin
        fun refresh() {
            val cardType = selected
            val state = typeStates.getValue(cardType)
            if (state.loading) return

            refreshing = true
            typeStates[cardType] =
                state.copy(
                    cards = emptyList(),
                    cursor = null,
                    endReached = false,
                )
            loadFirstPage(cardType)
            refreshLifetime()
        }
```

Then add this private method right after `loadFirstPage` (before `loadPage`):

```kotlin
        /** 재진입/당겨서-새로고침마다 서버 확정 누적치로 갱신한다(카운트업 애니메이션은 건드리지 않음 — 세션당 1회 게이트). */
        private fun refreshLifetime() {
            viewModelScope.launch {
                lifetime = lifetimeStatsSource.lifetime()
                publish()
            }
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*RecordsViewModelTest*'`
Expected: PASS (all `RecordsViewModelTest` cases, including the new one).

- [ ] **Step 5: Run the full verification set**

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL across detekt, androidTest compile, and both unit-test variants.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModel.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModelTest.kt
git commit -m "fix(records): refetch lifetime stats on refresh, not just first load"
```

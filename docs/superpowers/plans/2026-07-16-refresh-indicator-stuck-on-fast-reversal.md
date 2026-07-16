# Refresh Indicator Stuck-Open on Fast Scroll-Reversal — Bug Fix Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the bug where, on the 학습(Home) and 기록(Records) tabs, scrolling down and then quickly flinging back up leaves the pull-to-refresh indicator open and the content permanently shifted down (frozen, non-spinning gray circle) instead of springing back to rest.

**Architecture:** `OverscrollRefreshState`'s `NestedScrollConnection` currently tracks the pull distance by launching a fire-and-forget `scope.launch { offset.snapTo(target) }` coroutine from `onPreScroll`/`onPostScroll`, while `onPreFling` synchronously reads `offset.value` to decide whether to open the refresh sequence or spring back. Under a fast down-then-up gesture, several of these launched coroutines are still queued (unexecuted) when `onPreFling` fires, so it reads a stale `offset.value`, bails out having done nothing, and the queued coroutines then apply a stale target afterward with nothing left to animate it back — the indicator is stuck open. The fix replaces the async fire-and-forget bookkeeping with a plain, synchronously-updated `dragOffsetPx` field for the drag phase (no coroutine involved, so no race is possible), and hands off to the existing `Animatable` (`offset`) only for the settle animations (spring-back / release sequence), seeding it from the always-accurate `dragOffsetPx` at the exact moment of hand-off.

**Tech Stack:** Kotlin, Jetpack Compose (`NestedScrollConnection`, `Animatable`), kotlinx-coroutines, JUnit4 + `kotlinx-coroutines-test` (`runTest`), Robolectric for the Compose-tree tests. No new dependencies.

## Global Constraints

- Package root: `com.jjundev.oneclickeng`; source root `android/app/src/main/kotlin/com/jjundev/oneclickeng/`; test root `android/app/src/test/kotlin/com/jjundev/oneclickeng/`. All commands run from the `android/` directory unless noted.
- Gradle verification MUST use `scripts/verify-android.sh` (repo root), NOT bare `./gradlew` — see `docs/agents/android-verification.md` (shared-`~/.gradle` cache pollution and missing `google-services.json` in worktrees otherwise give false "BUILD SUCCESSFUL" results).
- This is a bug fix, not a feature: keep the public API surface (`OverscrollRefreshBox`, `rememberOverscrollRefreshState`) unchanged. Only `OverscrollRefreshState`'s internals and its (already-internal-facing) `OverscrollRefreshState(...)` constructor signature change — the only call sites are `rememberOverscrollRefreshState()` and the test file, both fixed in this plan.
- Do not touch `OverscrollMath.kt`, `RefreshWave.kt`, `RefreshBurst.kt`, `HomeScreen.kt`, `RecordsScreen.kt`, or `RecordsViewModel.kt` — the bug is isolated to `OverscrollRefreshState.kt`'s gesture bookkeeping and the two places `OverscrollRefreshBox.kt` reads the pull distance.

---

## File Structure

- **Modify:** `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshState.kt` — replace the async `scope.launch { offset.snapTo(...) }` drag bookkeeping with a synchronous `dragOffsetPx` field; add `currentPullPx()` as `offset.value + dragOffsetPx`; collapse the reentrancy guard to one `settling` flag; drop the now-unused `scope: CoroutineScope` constructor parameter.
- **Modify:** `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshStateTest.kt` — update existing assertions to the new field names/semantics (they currently read `s.offset.value` for states that are, after the fix, only visible via `s.dragOffsetPx`/`s.currentPullPx()`), and add two regression tests that reproduce the reported bug.
- **Modify:** `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshBox.kt` — read `state.currentPullPx()` instead of `state.offset.value` at the two places that drive the visible pull (content `translationY`, indicator `alpha`/`scale`).
- **Modify:** `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshBoxTest.kt` — `releaseHoldsUntilRefreshingClears_pastMinVisibleFloor` currently seeds the pulled state via `capturedState.offset.snapTo(500f)`, bypassing drag bookkeeping entirely; switch it to drive the same state through the public gesture API (`onPostScroll` + `onPreFling`) so it still exercises a realistic path once `offset` is no longer written to during a raw drag.
- **No code change, verify only:** `OverscrollMath.kt`, `RefreshWave.kt`, `RefreshBurst.kt`, `OverscrollRefreshScreenshotTest.kt` (seeds `state.offset.snapTo(holdPx)` directly with no drag involved — `currentPullPx() = offset.value + dragOffsetPx` reduces to `offset.value` in that case since `dragOffsetPx` stays `0`, so the screenshot is unaffected, but Task 3 runs it to confirm), `HomeScreen.kt`, `RecordsScreen.kt`, `RecordsViewModel.kt`, `HomePullRefreshTest.kt`, `RecordsScreenPullRefreshTest.kt`, `RecordsScreenRefreshTest.kt`, `RecordsScreenScreenshotTest.kt` (none of these reference `OverscrollRefreshState` internals directly — confirmed by grep).

---

### Task 1: Synchronous drag bookkeeping in `OverscrollRefreshState` (closes the race)

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshState.kt`
- Modify (test): `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshStateTest.kt`

**Interfaces:**
- Consumes: `OverscrollDefaults`, `rubberBand` (`OverscrollMath.kt`, unchanged); `RefreshWaveState` (`RefreshWave.kt`, unchanged).
- Produces (replaces the previous public surface of this file):
  - `class OverscrollRefreshState(thresholdPx: Float, maxPullPx: Float)` — **no longer takes a `CoroutineScope`.**
  - `val offset: Animatable<Float>` — unchanged type/name, but now written to ONLY during settle animations (never during a raw drag).
  - `val dragOffsetPx: Float` (public read, private set) — **new.** Synchronously up to date on every `onPreScroll`/`onPostScroll` call; `0f` whenever a settle animation owns the value.
  - `fun currentPullPx(): Float` — **new formula:** `offset.value + dragOffsetPx` (exactly one of the two is non-zero at any time, so this always reads the value whichever side currently owns it — including in tests/screenshots that seed `offset` directly without going through a drag).
  - `var busy: Boolean` — unchanged (public var, no `private set`, matches the original declaration) — still the sole driver of the indicator's spin animation in `OverscrollRefreshBox.kt`; no longer used for gesture reentrancy (see `settling` below).
  - `var burstKey: Int` / `var releaseRequest: Int` — unchanged.
  - `fun fireBurst()` / `fun onCycleFinished()` / `suspend fun snapBackNoRefresh()` — unchanged names/behavior from the caller's point of view.
  - `val nestedScrollConnection: NestedScrollConnection` — unchanged type; internals rewritten.
  - `@Composable fun rememberOverscrollRefreshState(): OverscrollRefreshState` — unchanged signature; internals no longer create a `rememberCoroutineScope()`.

- [ ] **Step 1: Write the failing (regression) tests**

Replace the full contents of `OverscrollRefreshStateTest.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.foundation.refresh

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OverscrollRefreshStateTest {

    private fun state() = OverscrollRefreshState(thresholdPx = 64f, maxPullPx = 180f)

    @Test fun dragDownAtTop_growsOffsetWithResistance() = runTest {
        val s = state()
        // 위에서 아래로 100px 드래그(available.y>0), onPostScroll 이 소비
        val consumed = s.nestedScrollConnection.onPostScroll(
            consumed = Offset.Zero,
            available = Offset(0f, 100f),
            source = NestedScrollSource.UserInput,
        )
        assertTrue("consumed the downward drag", consumed.y > 0f)
        assertTrue("offset grew but under raw drag (rubber band)", s.dragOffsetPx in 1f..100f)
    }

    @Test fun releaseBelowThreshold_doesNotRequestRefresh() = runTest {
        val s = state()
        s.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 40f), NestedScrollSource.UserInput)
        val before = s.releaseRequest
        s.nestedScrollConnection.onPreFling(Velocity.Zero)
        assertEquals("no refresh requested below threshold", before, s.releaseRequest)
        assertEquals("sprang back to rest", 0f, s.currentPullPx(), 0.5f)
    }

    @Test fun releasePastThreshold_requestsRefresh() = runTest {
        val s = state()
        s.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 400f), NestedScrollSource.UserInput)
        assertTrue("pulled past threshold", s.dragOffsetPx >= 64f)
        val before = s.releaseRequest
        s.nestedScrollConnection.onPreFling(Velocity.Zero)
        assertEquals("refresh requested once", before + 1, s.releaseRequest)
    }

    @Test fun dragUpWhilePulled_shrinksOffset() = runTest {
        val s = state()
        s.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 200f), NestedScrollSource.UserInput)
        val pulled = s.dragOffsetPx
        val consumed = s.nestedScrollConnection.onPreScroll(Offset(0f, -30f), NestedScrollSource.UserInput)
        assertTrue("consumed upward drag while pulled", consumed.y < 0f)
        assertTrue("offset shrank", s.dragOffsetPx < pulled)
    }

    // 회귀 테스트 — 실사용자 버그: 아래로 스크롤하다 빠르게 위로 튕기면(빠른 방향 반전) 인디케이터가
    // 열린 채로 멈춘다. 구버전은 onPostScroll/onPreScroll 이 offset(Animatable)을
    // scope.launch { offset.snapTo(...) } 로 "비동기" 반영했고, onPreFling 이 그 사이 아직 반영되지
    // 않은(stale) offset.value 를 "동기" 로 읽어 오판했다. 이 테스트는 advanceUntilIdle() 없이 세
    // 호출을 연달아 실행해(실제 빠른 제스처처럼 코루틴 갭을 주지 않고) 그 레이스를 재현한다 — 매 호출
    // 뒤 advanceUntilIdle() 을 넣던 구버전 테스트 스타일로는 큐가 항상 비워져 재현되지 않는다.
    @Test fun fastDownThenUpReversal_leavesNoStuckOffset() = runTest {
        val s = state()
        s.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 300f), NestedScrollSource.UserInput)
        s.nestedScrollConnection.onPreScroll(Offset(0f, -3000f), NestedScrollSource.UserInput)
        assertEquals("reversal fully closes the pull before release", 0f, s.dragOffsetPx, 0.5f)

        val before = s.releaseRequest
        val consumedFling = s.nestedScrollConnection.onPreFling(Velocity.Zero)
        assertEquals("no refresh requested — the pull was already closed", before, s.releaseRequest)
        assertEquals("nothing left for this connection to consume", Velocity.Zero, consumedFling)
        assertEquals("indicator not left open", 0f, s.currentPullPx(), 0.5f)
        assertFalse("not stuck busy", s.busy)
    }

    // 회귀 테스트: 임계값을 넘긴 채로 손을 떼면 릴리스 시퀀스가 정확히 당김 위치에서 이어받아야
    // 한다 — 구버전은 여기서 stale 값을 스냅해 콘텐츠가 튀거나(잘못된 시작점) 멈춘 것처럼 보였다.
    @Test fun releaseSequenceHandsOffExactlyFromDragPosition() = runTest {
        val s = state()
        s.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 500f), NestedScrollSource.UserInput)
        val dragAtRelease = s.dragOffsetPx
        s.nestedScrollConnection.onPreFling(Velocity.Zero)
        assertTrue("busy once past threshold", s.busy)
        assertEquals("no visual jump at hand-off", dragAtRelease, s.offset.value, 0.01f)
        assertEquals("drag bookkeeping cleared once the animatable owns the value", 0f, s.dragOffsetPx, 0.01f)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.ui.foundation.refresh.OverscrollRefreshStateTest"`
Expected: FAIL to compile — `s.dragOffsetPx` and `s.currentPullPx()` are unresolved references (the field/method don't exist on the current `OverscrollRefreshState`), and `OverscrollRefreshState(thresholdPx = 64f, maxPullPx = 180f)` doesn't match the current 3-arg `(scope, thresholdPx, maxPullPx)` constructor.

- [ ] **Step 3: Rewrite the implementation**

Replace the full contents of `OverscrollRefreshState.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.foundation.refresh

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext

/**
 * 실제 컴포지션(rememberCoroutineScope)의 코루틴 컨텍스트에는 Recomposer 가 제공하는
 * MonotonicFrameClock 이 이미 있어 [Animatable.animateTo] 가 정상 동작한다. 반면 순수
 * `TestScope`(코루틴 단위 테스트)에는 프레임 클럭이 없어 즉시 [IllegalStateException] 이 난다.
 * 앰비언트 클럭이 없을 때만 합성 클럭으로 폴백해 애니메이션이 결정적으로 완료되게 한다 —
 * 프로덕션 경로(항상 앰비언트 클럭 보유)의 타이밍에는 영향이 없다.
 */
private val syntheticFrameClock: MonotonicFrameClock = object : MonotonicFrameClock {
    private var frameTimeNanos = 0L
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
        frameTimeNanos += 16_000_000L // 가상 16ms 틱
        return onFrame(frameTimeNanos)
    }
}

private suspend fun <R> withAnimationFrameClock(block: suspend () -> R): R =
    if (coroutineContext[MonotonicFrameClock] != null) block()
    else withContext(syntheticFrameClock) { block() }

/**
 * 오버스크롤 당김의 제스처/오프셋 상태 소유자.
 *
 * 드래그 중엔 [dragOffsetPx] 가 값의 유일한 소유자다 — onPreScroll/onPostScroll 안에서 매 이벤트마다
 * *동기적으로* 갱신되며 어떤 코루틴도 거치지 않는다. 정착(스냅백 또는 릴리스 시퀀스, [OverscrollRefreshBox]
 * 가 [releaseRequest] 를 보고 구동)이 시작되는 순간 [offset](Animatable)이 [dragOffsetPx] 의 마지막
 * 값을 그대로 이어받고 [dragOffsetPx] 는 0으로 비워진다 — 항상 둘 중 하나만 0이 아니므로
 * [currentPullPx] 는 단순 합으로 어느 쪽이 값을 들고 있든 정확히 읽힌다.
 *
 * (이전 구현은 onPreScroll/onPostScroll 이 `scope.launch { offset.snapTo(target) }` 로 오프셋을
 * *비동기* 반영했는데, onPreFling 이 그 사이 아직 반영되지 않은 offset.value 를 동기적으로 읽어
 * 오판할 수 있었다 — 빠르게 아래로 스크롤했다가 곧바로 위로 튕기면 이 레이스가 발생해 인디케이터가
 * 열린 채로 멈췄다. 드래그 소유값을 코루틴 없이 동기 필드로 옮겨 그 레이스를 원천 제거한다.)
 */
class OverscrollRefreshState(
    private val thresholdPx: Float,
    private val maxPullPx: Float,
) {
    val offset = Animatable(0f)
    val wave = RefreshWaveState()

    var burstKey: Int by mutableIntStateOf(0)
        private set
    var releaseRequest: Int by mutableIntStateOf(0)
        private set

    /** spin 인디케이터 표시 여부(릴리스 시퀀스 진행 중에만 true). 제스처 재진입 가드는 [settling] 이 맡는다. */
    var busy: Boolean by mutableStateOf(false)

    /** 드래그 중 매 이벤트마다 동기적으로 갱신되는 당김 오프셋(px). 정착 시퀀스가 값을 넘겨받는
     *  순간 0으로 비워진다. 자세한 설명은 클래스 문서 참고. */
    var dragOffsetPx: Float by mutableFloatStateOf(0f)
        private set

    /** 정착(스냅백/릴리스) 애니메이션이 진행 중인 동안 새 제스처가 끼어들지 못하게 막는 재진입 가드.
     *  release 시퀀스 동안엔 [busy] 도 함께 true 이지만, 임계값 미달의 스냅백에서는 [busy] 는 계속
     *  false 이므로 이 가드가 별도로 필요하다. */
    private var settling = false

    private var accumulatedDrag = 0f

    /** 콘텐츠/인디케이터가 그릴 현재 당김량. [offset]과 [dragOffsetPx] 중 항상 정확히 하나만
     *  0이 아니므로 단순 합으로 어느 쪽이 값을 들고 있든 정확히 읽힌다(테스트가 [offset] 을 직접
     *  스냅해 특정 시각 상태를 재현하는 경우에도 별도 분기 없이 자연히 반영됨). */
    fun currentPullPx(): Float = offset.value + dragOffsetPx

    fun fireBurst() {
        burstKey += 1
    }

    /** 릴리스 시퀀스 종료 시 Box 가 호출(다음 제스처 준비). */
    fun onCycleFinished() {
        busy = false
        settling = false
        accumulatedDrag = 0f
        dragOffsetPx = 0f
    }

    suspend fun snapBackNoRefresh() {
        settling = true
        try {
            withAnimationFrameClock {
                offset.snapTo(dragOffsetPx)
                dragOffsetPx = 0f
                val snapBackSpring = spring<Float>(
                    dampingRatio = OverscrollDefaults.SpringDampingRatio,
                    stiffness = OverscrollDefaults.SpringStiffness,
                )
                offset.animateTo(0f, snapBackSpring)
            }
        } finally {
            accumulatedDrag = 0f
            settling = false
        }
    }

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // 당겨져 있을 때 위로 드래그하면 먼저 틈을 닫는다.
            if (settling || available.y >= 0f || dragOffsetPx <= 0f) return Offset.Zero
            accumulatedDrag = (accumulatedDrag + available.y).coerceAtLeast(0f)
            val target = rubberBand(accumulatedDrag, maxPullPx)
            val delta = target - dragOffsetPx
            dragOffsetPx = target
            return Offset(0f, delta)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // 리스트가 더 못 내려갈 때(available.y>0) 남은 아래 방향 드래그로 틈을 연다.
            if (settling || available.y <= 0f) return Offset.Zero
            accumulatedDrag = (accumulatedDrag + available.y).coerceAtLeast(0f)
            dragOffsetPx = rubberBand(accumulatedDrag, maxPullPx)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (settling || dragOffsetPx <= 0f) return Velocity.Zero
            if (dragOffsetPx >= thresholdPx) {
                settling = true
                offset.snapTo(dragOffsetPx) // 릴리스 시퀀스가 정확히 이 당김 위치에서 이어받도록 시드
                dragOffsetPx = 0f
                busy = true
                releaseRequest += 1 // Box 가 전체 리프레시 시퀀스 구동
            } else {
                snapBackNoRefresh()
            }
            return available // 남은 fling 소비(리스트로 흘리지 않음)
        }
    }
}

@Composable
fun rememberOverscrollRefreshState(): OverscrollRefreshState {
    val density = LocalDensity.current
    val thresholdPx = with(density) { OverscrollDefaults.Threshold.toPx() }
    val maxPullPx = with(density) { OverscrollDefaults.MaxPull.toPx() }
    return remember { OverscrollRefreshState(thresholdPx, maxPullPx) }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.ui.foundation.refresh.OverscrollRefreshStateTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Confirm `snapBackNoRefresh()` has no other callers**

Run: `grep -rn "snapBackNoRefresh" android/app/src/main/kotlin android/app/src/test/kotlin`
Expected: only the definition (in `OverscrollRefreshState.kt`) and its one call site inside the same file's `onPreFling`. If any other caller turns up, stop and re-read it before proceeding — this plan assumes it's private to the gesture connection's decision logic.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshState.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshStateTest.kt
git commit -m "fix(refresh): track pull drag synchronously to close fast-reversal race

Fast scroll-down-then-up gestures left the pull-to-refresh indicator
stuck open: onPreScroll/onPostScroll applied the drag offset via a
fire-and-forget scope.launch { offset.snapTo(...) }, and onPreFling
read the not-yet-applied offset.value synchronously to decide whether
to release or snap back, so a fast reversal could see a stale value,
do nothing, and let a queued stale snapTo apply afterward with nothing
left to animate it back to rest.

Replace the async bookkeeping with a synchronous dragOffsetPx field
(no coroutine in the drag path, so no race), and hand off to the
existing offset Animatable only for settle animations, seeded from
the always-accurate dragOffsetPx at hand-off."
```

---

### Task 2: Point `OverscrollRefreshBox` at `currentPullPx()`

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshBox.kt:98-159` (the `OverscrollRefreshBox` composable body and the private `RefreshIndicator`)
- Modify (test): `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshBoxTest.kt`

**Interfaces:**
- Consumes: `OverscrollRefreshState.currentPullPx()` (Task 1).
- Produces: no new public surface — `OverscrollRefreshBox`'s signature is unchanged.

**Why this task is needed:** after Task 1, `offset.value` is only written to during settle animations — during a raw drag it stays `0f` and the live pull distance lives in `dragOffsetPx`. `OverscrollRefreshBox.kt` currently reads `state.offset.value` directly in two places (the content's `translationY` and the indicator's `alpha`/`scale`), so without this change the screen would stop visually following the finger during a drag (it would only animate during the settle phase) — the exact inverse-shaped bug.

- [ ] **Step 1: Write the failing test**

`OverscrollRefreshBoxTest.kt`'s existing `releaseHoldsUntilRefreshingClears_pastMinVisibleFloor` seeds the pulled state with `capturedState.offset.snapTo(500f)` — a direct poke that bypasses `dragOffsetPx` entirely. After Task 1 this still compiles (it's a legal direct call on the public `Animatable`), but it stops being a realistic reproduction of a real drag+release, and — more importantly — it hides whether `OverscrollRefreshBox.kt` was updated to read `currentPullPx()`, because seeding `offset` directly happens to also satisfy the *old* `state.offset.value` reads. Route it through the real gesture API instead so it fails clearly if the two `state.offset.value` reads in `OverscrollRefreshBox.kt` aren't updated to `state.currentPullPx()`.

In `OverscrollRefreshBoxTest.kt`, add these two imports:

```kotlin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
```

Then replace the seeding block inside `releaseHoldsUntilRefreshingClears_pastMinVisibleFloor`:

```kotlin
        // 스와이프 제스처는 클럭이 정지된 상태에서 불안정하므로, onPostScroll 로 임계값 이상까지
        // 당김을 만든 뒤 onPreFling 을 호출해 릴리스 시퀀스를 결정적으로 트리거한다.
        runBlocking {
            capturedState.nestedScrollConnection.onPostScroll(
                consumed = Offset.Zero,
                available = Offset(0f, 2000f),
                source = NestedScrollSource.UserInput,
            )
            capturedState.nestedScrollConnection.onPreFling(Velocity.Zero)
        }
```

(This replaces the old `capturedState.offset.snapTo(500f)` + `onPreFling` pair — same effect, but via the public gesture surface instead of poking the `Animatable` directly.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.ui.foundation.refresh.OverscrollRefreshBoxTest"`
Expected: FAIL — `releaseHoldsUntilRefreshingClears_pastMinVisibleFloor` fails on `assertTrue("indicator still held, not sprung back", capturedState.offset.value > 0f)` or a nearby assertion, because with `OverscrollRefreshBox.kt` still reading `state.offset.value` directly for the *content* offset it will still pass that particular assertion (offset.value genuinely is > 0 after the hand-off in Task 1) — the visible regression this task targets is a *visual* one (content/indicator not following `dragOffsetPx` during a live drag), which this specific test doesn't exercise. So: also add a small dedicated test that does.

Add this new test to `OverscrollRefreshBoxTest.kt`:

```kotlin
    // 회귀 테스트: Task 1 이후 드래그 중엔 dragOffsetPx 가 값을 들고 있고 offset.value 는 0으로
    // 남는다. OverscrollRefreshBox 가 여전히 state.offset.value 를 직접 읽으면 드래그 도중 콘텐츠가
    // 손가락을 따라오지 않는다(당김이 시각적으로 전혀 안 보임) — currentPullPx() 를 읽어야 한다.
    @Test fun dragWithoutRelease_indicatorFollowsDragOffsetPx() {
        lateinit var capturedState: OverscrollRefreshState
        rule.setContent {
            val state = rememberOverscrollRefreshState()
            capturedState = state
            OverscrollRefreshBox(
                isRefreshing = false,
                onRefresh = {},
                modifier = Modifier.fillMaxSize().testTag("box"),
                state = state,
            ) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items((1..3).toList()) { Text("row $it", Modifier.height(80.dp)) }
                }
            }
        }
        rule.waitForIdle()

        runBlocking {
            capturedState.nestedScrollConnection.onPostScroll(
                consumed = Offset.Zero,
                available = Offset(0f, 40f), // below threshold — stays a pure drag, no release
                source = NestedScrollSource.UserInput,
            )
        }
        rule.waitForIdle()

        assertTrue("dragOffsetPx reflects the live drag", capturedState.dragOffsetPx > 0f)
        assertEquals(
            "currentPullPx() must be read by the box while dragging (offset.value alone stays 0)",
            capturedState.dragOffsetPx,
            capturedState.currentPullPx(),
            0.5f,
        )
    }
```

This needs `assertEquals` imported (already present) and no other new imports beyond the two added above.

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.ui.foundation.refresh.OverscrollRefreshBoxTest"`
Expected: the new `dragWithoutRelease_indicatorFollowsDragOffsetPx` test PASSES already (it only asserts on the state object, not on rendered pixels — it's here to document the contract the next step relies on). The real visual regression (box reading stale `offset.value`) isn't assertable from a JVM test without pixel inspection; Task 3's screenshot-test run is the visual confirmation. Proceed to Step 3 regardless.

- [ ] **Step 3: Update `OverscrollRefreshBox.kt` to read `currentPullPx()`**

In `OverscrollRefreshBox.kt`, change the content box (around line 106):

```kotlin
        CompositionLocalProvider(LocalRefreshWave provides state.wave) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = state.currentPullPx() },
                content = content,
            )
        }
```

And in `RefreshIndicator` (around line 138):

```kotlin
            .graphicsLayer {
                val pull = state.currentPullPx()
                alpha = (pull / fadeAtPx).coerceIn(0f, 1f)
                val p = (pull / thresholdPx).coerceIn(0f, 1f)
                scaleX = 0.55f + 0.45f * p
                scaleY = scaleX
                rotationZ = spin
            }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.ui.foundation.refresh.OverscrollRefreshBoxTest"`
Expected: PASS (3 tests: `swipeDownAtTop_invokesOnRefresh`, `releaseHoldsUntilRefreshingClears_pastMinVisibleFloor`, `dragWithoutRelease_indicatorFollowsDragOffsetPx`).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshBox.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshBoxTest.kt
git commit -m "fix(refresh): read currentPullPx() so the box follows the drag offset

OverscrollRefreshState now tracks a live drag via a synchronous
dragOffsetPx field and only writes to the offset Animatable during
settle animations (see previous commit). OverscrollRefreshBox was
still reading state.offset.value directly for the content translation
and the indicator's alpha/scale, which would have stayed frozen at 0
during a raw drag. Route both through state.currentPullPx()."
```

---

### Task 3: Full regression verification

**Files:** none modified — this task only runs existing test suites and records the result.

**Interfaces:** none.

- [ ] **Step 1: Run the whole refresh package's test suite**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.ui.foundation.refresh.*"`
Expected: PASS — `OverscrollMathTest`, `RefreshWaveTest`, `RefreshBurstTest`, `OverscrollRefreshStateTest` (Task 1), `OverscrollRefreshBoxTest` (Task 2), `OverscrollRefreshScreenshotTest`, all green. `OverscrollRefreshScreenshotTest` is the important one to watch: it seeds `state.offset.snapTo(holdPx)` directly with no drag, so `currentPullPx() = holdPx + 0` — if the captured image (`build/outputs/roborazzi/overscroll_held_state.png`) differs from its checked-in golden, stop and re-examine Task 1/2 before continuing (it should NOT differ — this is a confirmation, not an expected diff).

- [ ] **Step 2: Run the Home and Records pull-refresh test suites**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.feature.home.HomePullRefreshTest" --tests "com.jjundev.oneclickeng.feature.records.RecordsScreenPullRefreshTest" --tests "com.jjundev.oneclickeng.feature.records.RecordsScreenRefreshTest" --tests "com.jjundev.oneclickeng.feature.records.RecordsScreenScreenshotTest" --tests "com.jjundev.oneclickeng.feature.records.RecordsRefreshingStateTest"`
Expected: PASS — none of these reference `OverscrollRefreshState`'s internals (confirmed via grep before writing this plan), so they exercise the fix only through the public `OverscrollRefreshBox` surface and should be unaffected.

- [ ] **Step 3: Full project verification sweep**

Run: `scripts/verify-android.sh` (from the repo root, no args — runs the default set: detekt + androidTest compile + both unit-test variants)
Expected: PASS. This catches any unused-import/lint issue from removing the `CoroutineScope`/`launch` imports in Task 1, and confirms nothing else in the app references the old `OverscrollRefreshState(scope, thresholdPx, maxPullPx)` 3-arg constructor.

- [ ] **Step 4: Manual smoke test of the reported gesture**

This bug was reported from a physical-device screen recording (fast scroll-down-then-up on 학습/기록), not from an emulator artifact, so a manual pass on a device or emulator is worth doing before calling this done:
1. Build and install the debug APK (or run from Android Studio).
2. On the 학습(Home) tab: scroll down through the list, then quickly fling back up past the top so the list bounces. Repeat several times, varying speed/timing.
3. Confirm the pull-to-refresh indicator either fully completes its animation (wave/burst/spin/spring-back) or never appears at all — it should never freeze half-open with the content shifted down.
4. Repeat steps 2-3 on the 기록(Records) tab.
5. If a stuck frame is still reproducible, capture a new screen recording and re-open investigation — do not consider this plan complete until step 3/4 are clean on-device.

No commit for this task (verification only).

---

## Self-Review

**Spec coverage:**
- Root cause (async `scope.launch { offset.snapTo(...) }` racing `onPreFling`'s synchronous `offset.value` read) — fixed in Task 1.
- Shared by both Home and Records (same `OverscrollRefreshBox`/`OverscrollRefreshState`) — fix lives in the shared component, so both screens are covered by construction; Task 3 explicitly re-runs both screens' existing suites to confirm no regression.
- Existing tests never caught this because they call `advanceUntilIdle()` after every step, serializing what's concurrent in production — the new regression tests in Task 1 deliberately omit `advanceUntilIdle()` between calls to reproduce it.
- `inverseRubberBand` (flagged during investigation as defined-but-unused) — not needed by this fix: `accumulatedDrag` is now the sole running total driving `dragOffsetPx`, updated synchronously on every event with no async path to drift from, so there's nothing left for `inverseRubberBand` to reconcile. Left as-is (still exercised by `OverscrollMathTest.inverseRubberBand_roundTrips`, untouched by this plan).

**Placeholder scan:** no TBD/TODO markers; every step has complete, runnable code.

**Type consistency:** `dragOffsetPx: Float`, `currentPullPx(): Float`, `settling: Boolean` (private) are used identically across Task 1's implementation and test file. `OverscrollRefreshState(thresholdPx, maxPullPx)` (2-arg, no `scope`) is used consistently in `rememberOverscrollRefreshState()` and in the test's `state()` helper.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-16-refresh-indicator-stuck-on-fast-reversal.md`. Two execution options:

**1. Subagent-Driven (recommended)** - dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**

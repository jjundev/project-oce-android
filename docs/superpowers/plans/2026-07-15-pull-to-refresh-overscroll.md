# Pull-to-Refresh Overscroll Animation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a custom pull-to-refresh (overscroll) gesture to the 학습(Home) and 기록(Records) tabs that plays the confirmed prototype animation — the whole screen (header + list) slides down as a block, a staggered "wave" ripples through the header text and list cards, a transparent particle "burst" fires from the top, and the content springs back with a bouncy overshoot — where 학습 refreshes only 추천 상황 and 기록 reloads its cards from Firebase.

**Architecture:** One reusable Compose component, `OverscrollRefreshBox` (package `com.jjundev.oneclickeng.ui.foundation.refresh`), owns a `NestedScrollConnection` + `Animatable`-driven pull offset, a release orchestration (snap → wave + burst + `onRefresh` → hold until refresh completes with a minimum-visible floor → bouncy spring back), a top circular indicator, and a burst overlay. List items opt into the wave via `Modifier.refreshWave(index)`. Home passes `isRefreshing = false` (its `refreshSituations()` is synchronous/local, so the min-visible floor governs timing); Records adds a `refreshing` flag to its UiState so the spring-back waits for the Firestore reload.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2025.01.00 → material3 1.3.1), Hilt, kotlinx-coroutines, JUnit4 + Robolectric (JVM unit tests), Roborazzi (screenshot tests). No new dependencies.

## Global Constraints

- Package root: `com.jjundev.oneclickeng`; source root `android/app/src/main/kotlin/com/jjundev/oneclickeng/`; test root `android/app/src/test/kotlin/com/jjundev/oneclickeng/`. All commands run from the `android/` directory.
- All new refresh components live in package `com.jjundev.oneclickeng.ui.foundation.refresh` (directory `ui/foundation/refresh/`).
- Gradle verification MUST use `scripts/verify-android.sh` (repo root), NOT bare `./gradlew` — the worktree relies on it to avoid shared-cache/`google-services.json` pitfalls (see `docs/agents/android-verification.md`). From the `android/` directory, the app module is `:app`.
- Compose Material3 `PullToRefreshBox` is intentionally NOT used — the prototype animation is fully custom. Use `androidx.compose.foundation.gestures` / `NestedScrollConnection` + `androidx.compose.animation.core.Animatable`.
- Animation constants are ports of the confirmed HTML prototype (`prototype/experiments/overscroll-top-refresh.html`). They are expressed as `dp`/`ms` starting points and MUST be centralized in `OverscrollDefaults` (Task 1) so they stay tunable and are visually reconciled against the prototype in Task 9.
- Existing Home behavior is preserved: `refreshSituations()` rotates 추천 상황 deterministically and MUST remain the ONLY thing Home's pull-to-refresh triggers (do not re-pull 오늘 N분 / streak / hero).
- Tests: Robolectric ViewModel tests inline their dispatcher rule (`Dispatchers.setMain` in `@Before`, `resetMain` in `@After`); no Turbine (assert `viewModel.uiState.value` after `advanceUntilIdle()`); hand-written fakes, not Mockito.

---

### Task 1: Overscroll math + centralized defaults

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollMath.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollMathTest.kt`

**Interfaces:**
- Produces:
  - `object OverscrollDefaults` with (all `Dp`/`Int`/`Float`): `MaxPull: Dp = 180.dp`, `Threshold: Dp = 64.dp`, `HoldOffset: Dp = 56.dp`, `IndicatorSize: Dp = 40.dp`, `IndicatorTop: Dp = 14.dp`, `IndicatorFadeAt: Dp = 70.dp`, `SnapToHoldMs: Int = 140`, `MinVisibleMs: Long = 450L`, `WaveDurationMs: Float = 520f`, `WaveStaggerMs: Float = 36f`, `WaveCardPeak: Dp = 11.dp`, `WaveHeaderPeak: Dp = 4.5.dp`, `BurstCount: Int = 13`, `BurstFlyMs: Int = 680`, `BurstMinDist: Dp = 42.dp`, `BurstMaxDist: Dp = 80.dp`, `BurstMinSize: Dp = 5.dp`, `BurstMaxSize: Dp = 10.dp`, `SpringDampingRatio: Float = 0.32f`, `SpringStiffness: Float = 220f`.
  - `fun rubberBand(rawDragPx: Float, maxPx: Float): Float`
  - `fun inverseRubberBand(offsetPx: Float, maxPx: Float): Float`

- [ ] **Step 1: Write the failing test**

Create `OverscrollMathTest.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.foundation.refresh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverscrollMathTest {
    @Test fun rubberBand_zeroOrNegative_isZero() {
        assertEquals(0f, rubberBand(0f, 180f), 0.001f)
        assertEquals(0f, rubberBand(-50f, 180f), 0.001f)
    }

    @Test fun rubberBand_isMonotonicAndBoundedByMax() {
        val max = 180f
        val a = rubberBand(100f, max)
        val b = rubberBand(400f, max)
        assertTrue("resistance grows with drag", b > a)
        assertTrue("never reaches max", b < max)
        assertTrue("large drag approaches max", b > max * 0.6f)
    }

    @Test fun inverseRubberBand_roundTrips() {
        val max = 180f
        val raw = 250f
        val offset = rubberBand(raw, max)
        assertEquals(raw, inverseRubberBand(offset, max), 0.5f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.ui.foundation.refresh.OverscrollMathTest"`
Expected: FAIL — unresolved reference `rubberBand` / `OverscrollMath`.

- [ ] **Step 3: Write minimal implementation**

Create `OverscrollMath.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.foundation.refresh

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 당겨서 새로고침(오버스크롤) 애니메이션의 중앙 상수. 값은 확정된 HTML 프로토타입
 * (`prototype/experiments/overscroll-top-refresh.html`) 을 dp/ms 로 옮긴 출발점이며 Task 9 에서 시각 대조로 미세조정한다.
 */
object OverscrollDefaults {
    val MaxPull: Dp = 180.dp
    val Threshold: Dp = 64.dp
    val HoldOffset: Dp = 56.dp
    val IndicatorSize: Dp = 40.dp
    val IndicatorTop: Dp = 14.dp
    val IndicatorFadeAt: Dp = 70.dp
    const val SnapToHoldMs: Int = 140
    const val MinVisibleMs: Long = 450L
    const val WaveDurationMs: Float = 520f
    const val WaveStaggerMs: Float = 36f
    val WaveCardPeak: Dp = 11.dp
    val WaveHeaderPeak: Dp = 4.5.dp
    const val BurstCount: Int = 13
    const val BurstFlyMs: Int = 680
    val BurstMinDist: Dp = 42.dp
    val BurstMaxDist: Dp = 80.dp
    val BurstMinSize: Dp = 5.dp
    val BurstMaxSize: Dp = 10.dp
    const val SpringDampingRatio: Float = 0.32f
    const val SpringStiffness: Float = 220f
}

/**
 * 고무줄 저항: 손가락 이동량 [rawDragPx] 가 커질수록 실제 오프셋은 [maxPx] 로 점근 수렴한다.
 * 프로토타입: `MAX * (1 - 1/(1 + d/MAX))`.
 */
fun rubberBand(rawDragPx: Float, maxPx: Float): Float =
    if (rawDragPx <= 0f) 0f else maxPx * (1f - 1f / (1f + rawDragPx / maxPx))

/** [rubberBand] 의 역함수 — 현재 오프셋에서 누적 드래그를 복원(드래그 재개 시 연속성 유지). */
fun inverseRubberBand(offsetPx: Float, maxPx: Float): Float =
    if (offsetPx <= 0f) 0f else offsetPx / (1f - offsetPx / maxPx)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.ui.foundation.refresh.OverscrollMathTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollMath.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollMathTest.kt
git commit -m "feat(refresh): overscroll rubber-band math + centralized defaults"
```

---

### Task 2: Wave state, curve, and opt-in modifier

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/RefreshWave.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/RefreshWaveTest.kt`

**Interfaces:**
- Consumes: `OverscrollDefaults` (Task 1).
- Produces:
  - `fun waveBob(t: Float): Float` — normalized bob curve, keyframes (0,0),(0.32,1),(0.66,-0.27),(1,0), clamped outside [0,1] to 0.
  - `class RefreshWaveState` with `var clockMs: Float` (backed by `mutableFloatStateOf`, `-1f` = idle) and `fun translationYPx(index: Int, amplitudePx: Float): Float`.
  - `val LocalRefreshWave: ProvidableCompositionLocal<RefreshWaveState>` (default: a permanently-idle instance).
  - `fun Modifier.refreshWave(index: Int, soft: Boolean = false): Modifier`.

- [ ] **Step 1: Write the failing test**

Create `RefreshWaveTest.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.foundation.refresh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshWaveTest {
    @Test fun waveBob_keyframes() {
        assertEquals(0f, waveBob(0f), 0.001f)
        assertEquals(1f, waveBob(0.32f), 0.02f)
        assertEquals(-0.27f, waveBob(0.66f), 0.02f)
        assertEquals(0f, waveBob(1f), 0.001f)
    }

    @Test fun waveBob_clampsOutsideRange() {
        assertEquals(0f, waveBob(-0.5f), 0.001f)
        assertEquals(0f, waveBob(1.5f), 0.001f)
    }

    @Test fun translationY_idleClockIsZero() {
        val state = RefreshWaveState()
        assertEquals(0f, state.translationYPx(index = 0, amplitudePx = 11f), 0.001f)
    }

    @Test fun translationY_laterIndexIsDelayed() {
        val state = RefreshWaveState()
        // clock at the first item's peak time (0.32 * 520ms ≈ 166ms)
        state.clockMs = 0.32f * OverscrollDefaults.WaveDurationMs
        val first = state.translationYPx(index = 0, amplitudePx = 11f)
        val third = state.translationYPx(index = 2, amplitudePx = 11f)
        assertTrue("item 0 is near its peak", first > 9f)
        assertTrue("item 2 lags behind item 0", third < first)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.ui.foundation.refresh.RefreshWaveTest"`
Expected: FAIL — unresolved `waveBob` / `RefreshWaveState`.

- [ ] **Step 3: Write minimal implementation**

Create `RefreshWave.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.foundation.refresh

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

/**
 * 정규화된 물결 bob 곡선. 프로토타입 waveBob 키프레임(0 → +1@32% → -0.27@66% → 0)을 구간 선형 보간.
 * [t] 는 아이템별 로컬 진행도[0,1], 반환값에 진폭(px)을 곱해 translationY 로 쓴다.
 */
fun waveBob(t: Float): Float {
    if (t <= 0f || t >= 1f) return 0f
    val keys = floatArrayOf(0f, 0.32f, 0.66f, 1f)
    val vals = floatArrayOf(0f, 1f, -0.27f, 0f)
    for (i in 0 until keys.size - 1) {
        if (t <= keys[i + 1]) {
            val span = keys[i + 1] - keys[i]
            val f = if (span == 0f) 0f else (t - keys[i]) / span
            return vals[i] + (vals[i + 1] - vals[i]) * f
        }
    }
    return 0f
}

/**
 * 물결 애니메이션 시계. [clockMs] 를 0 부터 (WaveDurationMs + maxIndex*WaveStaggerMs) 까지 진행시키면
 * 각 아이템이 인덱스만큼 지연돼 파도처럼 전파된다. -1 = 유휴(정지).
 */
class RefreshWaveState {
    var clockMs: Float by mutableFloatStateOf(-1f)

    fun translationYPx(index: Int, amplitudePx: Float): Float {
        val c = clockMs
        if (c < 0f) return 0f
        val local = ((c - index * OverscrollDefaults.WaveStaggerMs) / OverscrollDefaults.WaveDurationMs)
            .coerceIn(0f, 1f)
        return waveBob(local) * amplitudePx
    }
}

/** 물결 시계 제공자. 리프레시 박스가 실제 인스턴스를 제공하고, 그 밖에서는 유휴 인스턴스라 no-op. */
val LocalRefreshWave = staticCompositionLocalOf { RefreshWaveState() }

/**
 * 리스트/헤더 요소가 물결에 참여하도록 하는 opt-in 모디파이어.
 * [index] 는 물결 전파 순서(위→아래), [soft]=true 는 헤더용 작은 진폭.
 * graphicsLayer 람다에서 clock 을 읽어 draw 단계에서만 갱신(리컴포지션 없음).
 */
fun Modifier.refreshWave(index: Int, soft: Boolean = false): Modifier = composed {
    val wave = LocalRefreshWave.current
    val amplitudePx = with(LocalDensity.current) {
        (if (soft) OverscrollDefaults.WaveHeaderPeak else OverscrollDefaults.WaveCardPeak).toPx()
    }
    graphicsLayer { translationY = wave.translationYPx(index, amplitudePx) }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.ui.foundation.refresh.RefreshWaveTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/RefreshWave.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/RefreshWaveTest.kt
git commit -m "feat(refresh): staggered wave curve, state, and refreshWave modifier"
```

---

### Task 3: Transparent burst particles + overlay

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/RefreshBurst.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/RefreshBurstTest.kt`

**Interfaces:**
- Consumes: `OverscrollDefaults` (Task 1).
- Produces:
  - `data class BurstParticle(val angleRad: Float, val distFraction: Float, val sizeFraction: Float, val delayFraction: Float, val colorIndex: Int)`
  - `fun burstParticles(count: Int = OverscrollDefaults.BurstCount, seed: Int): List<BurstParticle>` — deterministic given `seed`.
  - `val BurstColors: List<Color>` — 3 translucent colors (blue .40, white .55, blue .32).
  - `@Composable fun RefreshBurst(fireKey: Int, modifier: Modifier = Modifier)` — plays one burst each time `fireKey` changes to a value > 0.

- [ ] **Step 1: Write the failing test**

Create `RefreshBurstTest.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.foundation.refresh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshBurstTest {
    @Test fun burstParticles_hasRequestedCount() {
        assertEquals(13, burstParticles(count = 13, seed = 1).size)
    }

    @Test fun burstParticles_isDeterministicForSeed() {
        assertEquals(burstParticles(count = 13, seed = 7), burstParticles(count = 13, seed = 7))
    }

    @Test fun burstParticles_fractionsAreInRange() {
        burstParticles(count = 13, seed = 3).forEach { p ->
            assertTrue(p.distFraction in 0f..1f)
            assertTrue(p.sizeFraction in 0f..1f)
            assertTrue(p.delayFraction in 0f..1f)
            assertTrue(p.colorIndex in 0..2)
        }
    }

    @Test fun burstParticles_anglesSpanFullCircle() {
        val angles = burstParticles(count = 13, seed = 2).map { it.angleRad }
        assertTrue("min angle near 0", angles.min() < 1f)
        assertTrue("max angle near 2pi", angles.max() > 5f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.ui.foundation.refresh.RefreshBurstTest"`
Expected: FAIL — unresolved `burstParticles` / `BurstParticle`.

- [ ] **Step 3: Write minimal implementation**

Create `RefreshBurst.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.foundation.refresh

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 방사형 입자 한 개의 결정적 파라미터(진행도 0..1 로 렌더링 시 위치/크기/투명도 계산). */
data class BurstParticle(
    val angleRad: Float,
    val distFraction: Float,
    val sizeFraction: Float,
    val delayFraction: Float,
    val colorIndex: Int,
)

/** 투명 폭죽 3색(프로토타입): 파랑 .40 / 흰색 .55 / 파랑 .32. */
val BurstColors: List<Color> = listOf(
    Color(0.47f, 0.63f, 1.0f, 0.40f),
    Color(1.0f, 1.0f, 1.0f, 0.55f),
    Color(0.35f, 0.55f, 0.96f, 0.32f),
)

/** [seed] 로 결정적인 입자 배열 생성 — 균등 각도 + 소량 지터, 거리/크기/지연은 난수 프랙션. */
fun burstParticles(count: Int = OverscrollDefaults.BurstCount, seed: Int): List<BurstParticle> {
    val rng = Random(seed)
    return (0 until count).map { i ->
        val base = (2f * PI.toFloat()) * (i.toFloat() / count)
        val jitter = (rng.nextFloat() - 0.5f) * 0.5f
        BurstParticle(
            angleRad = (base + jitter).let { if (it < 0f) it + 2f * PI.toFloat() else it },
            distFraction = rng.nextFloat(),
            sizeFraction = rng.nextFloat(),
            delayFraction = rng.nextFloat() * (45f / OverscrollDefaults.BurstFlyMs),
            colorIndex = i % BurstColors.size,
        )
    }
}

/**
 * 상단 인디케이터 지점에서 터지는 투명 폭죽 오버레이. [fireKey] 가 0 초과의 새 값으로 바뀔 때마다 1회 재생.
 * 입자는 각자 진행도에 따라 방사형 이동 + 확대 + 페이드아웃(18% 에서 최대 투명, 끝에서 0).
 */
@Composable
fun RefreshBurst(fireKey: Int, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) } // 1 = 종료(비표시)
    val particles = remember(fireKey) { burstParticles(seed = fireKey) }
    val density = LocalDensity.current
    val minDist = with(density) { OverscrollDefaults.BurstMinDist.toPx() }
    val maxDist = with(density) { OverscrollDefaults.BurstMaxDist.toPx() }
    val minSize = with(density) { OverscrollDefaults.BurstMinSize.toPx() }
    val maxSize = with(density) { OverscrollDefaults.BurstMaxSize.toPx() }

    LaunchedEffect(fireKey) {
        if (fireKey <= 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(OverscrollDefaults.BurstFlyMs))
    }

    Canvas(modifier = modifier) {
        val p0 = progress.value
        if (p0 >= 1f) return@Canvas
        val center = Offset(size.width / 2f, 0f)
        particles.forEach { pt ->
            val local = ((p0 - pt.delayFraction) / (1f - pt.delayFraction)).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) return@forEach
            val dist = (minDist + (maxDist - minDist) * pt.distFraction) * local
            val radius = (minSize + (maxSize - minSize) * pt.sizeFraction) / 2f * (0.35f + 0.65f * local)
            // 투명도: 0 → .peak(18%) → 0
            val alpha = if (local < 0.18f) local / 0.18f else 1f - (local - 0.18f) / 0.82f
            val base = BurstColors[pt.colorIndex]
            drawCircle(
                color = base.copy(alpha = base.alpha * alpha.coerceIn(0f, 1f)),
                radius = radius,
                center = center + Offset(cos(pt.angleRad) * dist, sin(pt.angleRad) * dist * 0.9f),
            )
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.ui.foundation.refresh.RefreshBurstTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/RefreshBurst.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/RefreshBurstTest.kt
git commit -m "feat(refresh): deterministic transparent burst particles + overlay"
```

---

### Task 4: Overscroll state + NestedScrollConnection

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshState.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshStateTest.kt`

**Interfaces:**
- Consumes: `OverscrollDefaults`, `rubberBand`, `inverseRubberBand` (Task 1); `RefreshWaveState` (Task 2).
- Produces:
  - `class OverscrollRefreshState(scope, thresholdPx, maxPullPx)` with public: `offset: Animatable<Float>`, `wave: RefreshWaveState`, `burstKey: Int` (state), `releaseRequest: Int` (state, increments when released past threshold), `nestedScrollConnection: NestedScrollConnection`, `suspend fun snapBackNoRefresh()`, `fun fireBurst()`, `fun currentPullPx(): Float`.
  - `@Composable fun rememberOverscrollRefreshState(): OverscrollRefreshState`.
- Note: the release orchestration (snap → wave/burst → onRefresh → hold → spring) lives in the `OverscrollRefreshBox` (Task 5) which reads `releaseRequest`. This task only handles drag accounting and exposes the release signal.

- [ ] **Step 1: Write the failing test**

Create `OverscrollRefreshStateTest.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.foundation.refresh

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OverscrollRefreshStateTest {

    private fun state(scope: TestScope) =
        OverscrollRefreshState(scope, thresholdPx = 64f, maxPullPx = 180f)

    @Test fun dragDownAtTop_growsOffsetWithResistance() = runTest {
        val s = state(this)
        // 위에서 아래로 100px 드래그(available.y>0), onPostScroll 이 소비
        val consumed = s.nestedScrollConnection.onPostScroll(
            consumed = Offset.Zero,
            available = Offset(0f, 100f),
            source = NestedScrollSource.UserInput,
        )
        advanceUntilIdle()
        assertTrue("consumed the downward drag", consumed.y > 0f)
        assertTrue("offset grew but under raw drag (rubber band)", s.offset.value in 1f..100f)
    }

    @Test fun releaseBelowThreshold_doesNotRequestRefresh() = runTest {
        val s = state(this)
        s.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 40f), NestedScrollSource.UserInput)
        advanceUntilIdle()
        val before = s.releaseRequest
        s.nestedScrollConnection.onPreFling(Velocity.Zero)
        advanceUntilIdle()
        assertEquals("no refresh requested below threshold", before, s.releaseRequest)
        assertEquals("sprang back to rest", 0f, s.offset.value, 0.5f)
    }

    @Test fun releasePastThreshold_requestsRefresh() = runTest {
        val s = state(this)
        s.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 400f), NestedScrollSource.UserInput)
        advanceUntilIdle()
        assertTrue("pulled past threshold", s.offset.value >= 64f)
        val before = s.releaseRequest
        s.nestedScrollConnection.onPreFling(Velocity.Zero)
        advanceUntilIdle()
        assertEquals("refresh requested once", before + 1, s.releaseRequest)
    }

    @Test fun dragUpWhilePulled_shrinksOffset() = runTest {
        val s = state(this)
        s.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 200f), NestedScrollSource.UserInput)
        advanceUntilIdle()
        val pulled = s.offset.value
        val consumed = s.nestedScrollConnection.onPreScroll(Offset(0f, -30f), NestedScrollSource.UserInput)
        advanceUntilIdle()
        assertTrue("consumed upward drag while pulled", consumed.y < 0f)
        assertTrue("offset shrank", s.offset.value < pulled)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.ui.foundation.refresh.OverscrollRefreshStateTest"`
Expected: FAIL — unresolved `OverscrollRefreshState`.

- [ ] **Step 3: Write minimal implementation**

Create `OverscrollRefreshState.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.foundation.refresh

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 오버스크롤 당김의 제스처/오프셋 상태 소유자. 드래그 회계(고무줄)와 릴리스 신호만 담당하고,
 * 릴리스 후 시퀀스(스냅→물결/폭죽→onRefresh→홀드→스프링)는 [OverscrollRefreshBox] 가 [releaseRequest] 를 보고 구동한다.
 */
class OverscrollRefreshState(
    private val scope: CoroutineScope,
    private val thresholdPx: Float,
    private val maxPullPx: Float,
) {
    val offset = Animatable(0f)
    val wave = RefreshWaveState()

    var burstKey by mutableIntStateOf(0)
        private set
    var releaseRequest by mutableIntStateOf(0)
        private set

    /** true 동안(릴리스 시퀀스 실행 중) 새 드래그를 무시한다. Box 가 시퀀스 시작/끝에서 토글.
     *  관측 가능(mutableStateOf)이라 인디케이터 spinning 갱신이 명시적으로 리컴포지션을 트리거한다. */
    var busy: Boolean by mutableStateOf(false)

    private var accumulatedDrag = 0f

    fun currentPullPx(): Float = offset.value

    fun fireBurst() {
        burstKey += 1
    }

    /** 릴리스 시퀀스 종료 시 Box 가 호출(다음 제스처 준비). */
    fun onCycleFinished() {
        busy = false
        accumulatedDrag = 0f
    }

    suspend fun snapBackNoRefresh() {
        offset.animateTo(0f, spring(dampingRatio = OverscrollDefaults.SpringDampingRatio, stiffness = OverscrollDefaults.SpringStiffness))
        accumulatedDrag = 0f
    }

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // 당겨져 있을 때 위로 드래그하면 먼저 틈을 닫는다.
            if (busy) return Offset.Zero
            if (available.y < 0f && offset.value > 0f) {
                accumulatedDrag = (accumulatedDrag + available.y).coerceAtLeast(0f)
                val target = rubberBand(accumulatedDrag, maxPullPx)
                val delta = target - offset.value
                scope.launch { offset.snapTo(target) }
                return Offset(0f, delta)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // 리스트가 더 못 내려갈 때(available.y>0) 남은 아래 방향 드래그로 틈을 연다.
            if (busy) return Offset.Zero
            if (available.y > 0f) {
                accumulatedDrag = (accumulatedDrag + available.y).coerceAtLeast(0f)
                val target = rubberBand(accumulatedDrag, maxPullPx)
                scope.launch { offset.snapTo(target) }
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (busy || offset.value <= 0f) return Velocity.Zero
            if (offset.value >= thresholdPx) {
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return remember { OverscrollRefreshState(scope, thresholdPx, maxPullPx) }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.ui.foundation.refresh.OverscrollRefreshStateTest"`
Expected: PASS (4 tests). If `onPostScroll` snapTo timing races the assertion, the test already calls `advanceUntilIdle()`; ensure `offset` uses the injected scope.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshState.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshStateTest.kt
git commit -m "feat(refresh): overscroll gesture state + nested-scroll connection"
```

---

### Task 5: OverscrollRefreshBox composable (orchestration + indicator)

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshBox.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshBoxTest.kt`

**Interfaces:**
- Consumes: `OverscrollRefreshState`, `rememberOverscrollRefreshState` (Task 4); `RefreshWave` / `LocalRefreshWave` (Task 2); `RefreshBurst` (Task 3); `OverscrollDefaults` (Task 1).
- Produces:
  - `@Composable fun OverscrollRefreshBox(isRefreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, state: OverscrollRefreshState = rememberOverscrollRefreshState(), content: @Composable BoxScope.() -> Unit)`.
- Behavior on `state.releaseRequest` change: (A) `offset.animateTo(HoldOffsetPx, tween(SnapToHoldMs))`; (B) call `onRefresh()`, run the wave clock 0→(WaveDurationMs + maxItems*WaveStaggerMs), `state.fireBurst()`; (C) wait for `!isRefreshing` AND `MinVisibleMs` elapsed; (D) `offset.animateTo(0f, spring(SpringDampingRatio, SpringStiffness))`; then `state.onCycleFinished()`.

- [ ] **Step 1: Write the failing test**

Create `OverscrollRefreshBoxTest.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.foundation.refresh

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OverscrollRefreshBoxTest {
    @get:Rule val rule = createComposeRule()

    @Test fun swipeDownAtTop_invokesOnRefresh() {
        var refreshCount = 0
        rule.setContent {
            OverscrollRefreshBox(
                isRefreshing = false,
                onRefresh = { refreshCount++ },
                modifier = Modifier.fillMaxSize().testTag("box"),
            ) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items((1..3).toList()) { Text("row $it", Modifier.height(80.dp)) }
                }
            }
        }
        rule.onNodeWithTag("box").performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertTrue("onRefresh fired at least once", refreshCount >= 1)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.ui.foundation.refresh.OverscrollRefreshBoxTest"`
Expected: FAIL — unresolved `OverscrollRefreshBox`.

- [ ] **Step 3: Write minimal implementation**

Create `OverscrollRefreshBox.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.foundation.refresh

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 확정된 프로토타입 당겨서-새로고침 애니메이션을 재생하는 재사용 컨테이너.
 * 헤더+리스트가 [content] 로 함께 들어와 한 덩어리로 하강한다. [isRefreshing] 이 true 인 동안 스프링 복귀를 미루고
 * (최소 표시 시간 [OverscrollDefaults.MinVisibleMs] 병행), 완료되면 통통 스프링으로 복귀한다.
 */
@Composable
fun OverscrollRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: OverscrollRefreshState = rememberOverscrollRefreshState(),
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val holdPx = with(density) { OverscrollDefaults.HoldOffset.toPx() }
    val fadeAtPx = with(density) { OverscrollDefaults.IndicatorFadeAt.toPx() }
    val thresholdPx = with(density) { OverscrollDefaults.Threshold.toPx() }
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    val currentRefreshing by rememberUpdatedState(isRefreshing)

    LaunchedEffect(state.releaseRequest) {
        if (state.releaseRequest <= 0) return@LaunchedEffect
        // try/finally 로 onCycleFinished() 를 항상 보장 → onRefresh() 예외 등으로 시퀀스가 중단돼도
        // busy 가 true 로 고착돼 이후 당김이 죽는 것을 막는다(방어적 하드닝).
        try {
            // (A) 로딩 위치로 스냅
            state.offset.animateTo(holdPx, tween(OverscrollDefaults.SnapToHoldMs, easing = FastOutSlowInEasing))
            // (B) 물결 + 폭죽 + 새로고침 트리거
            currentOnRefresh()
            state.fireBurst()
            val waveJob = launch {
                val total = OverscrollDefaults.WaveDurationMs + 8 * OverscrollDefaults.WaveStaggerMs
                val clock = Animatable(0f)
                clock.animateTo(total, tween(total.roundToInt(), easing = LinearEasing)) {
                    state.wave.clockMs = value
                }
                state.wave.clockMs = -1f
            }
            // (C) 최소 표시 시간을 먼저 확보한 뒤, 그 시점에 아직 로딩 중이면(isRefreshing==true) 완료될 때까지 대기.
            //   - 학습: isRefreshing 항상 false → 최소 시간 경과 후 first{!it} 즉시 통과.
            //   - 기록 빠른 로딩: 최소 시간 안에 이미 false → 즉시 통과.
            //   - 기록 느린 로딩: 최소 시간 후에도 true → false 로 바뀔 때까지 대기(= Firebase reload 완료 대기).
            // 순서가 핵심: snapshotFlow 는 수집 시작 시 현재값을 즉시 방출하므로, 반드시 min-floor 이후에 평가해야
            // "로딩 전 false" 를 즉시 통과해버리는 버그를 피한다. RecordsViewModel.refresh() 는 refreshing=true 를
            // 동기적으로 세팅하므로(다음 프레임에 파라미터 반영) min-floor(450ms) 경과 시점엔 로딩 상태가 정확히 반영돼 있다.
            // (기록 탭 전환으로 refresh 가 포기되면 RecordsViewModel.selectTab 이 refreshing=false 로 풀어줘 여기 대기가 해제된다.)
            kotlinx.coroutines.delay(OverscrollDefaults.MinVisibleMs)
            waveJob.join()
            snapshotFlow { currentRefreshing }.first { !it }
            // (D) 통통 스프링 복귀
            state.offset.animateTo(
                0f,
                spring(dampingRatio = OverscrollDefaults.SpringDampingRatio, stiffness = OverscrollDefaults.SpringStiffness),
            )
        } finally {
            state.onCycleFinished()
        }
    }

    Box(modifier = modifier.nestedScroll(state.nestedScrollConnection)) {
        // 상단 원형 인디케이터(하강한 틈에서 노출). spinning 은 busy 동안.
        RefreshIndicator(state = state, fadeAtPx = fadeAtPx, thresholdPx = thresholdPx)

        CompositionLocalProvider(LocalRefreshWave provides state.wave) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = state.offset.value },
                content = content,
            )
        }

        // 폭죽은 상단 인디케이터 지점(고정)에서 터진다 — 프로토타입처럼 당김량에 비례해 움직이지 않는다.
        RefreshBurst(
            fireKey = state.burstKey,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, OverscrollDefaults.IndicatorTop.toPx().roundToInt()) },
        )
    }
}

@Composable
private fun BoxScope.RefreshIndicator(state: OverscrollRefreshState, fadeAtPx: Float, thresholdPx: Float) {
    val density = LocalDensity.current
    val topPx = with(density) { OverscrollDefaults.IndicatorTop.toPx() }
    val spin = if (state.busy) {
        rememberInfiniteTransition(label = "spin").animateFloat(
            0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spinAngle",
        ).value
    } else 0f

    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset { IntOffset(0, topPx.roundToInt()) }
            .graphicsLayer {
                val pull = state.offset.value
                alpha = (pull / fadeAtPx).coerceIn(0f, 1f)
                val p = (pull / thresholdPx).coerceIn(0f, 1f)
                scaleX = 0.55f + 0.45f * p
                scaleY = scaleX
                rotationZ = spin
            }
            .size(OverscrollDefaults.IndicatorSize)
            .clip(CircleShape)
            .drawBehind {
                drawCircle(color = Color(0xFFE5E8EB))
                if (state.busy) {
                    drawArc(
                        color = Color(0xFFC6CDD5),
                        startAngle = -90f, sweepAngle = 90f, useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.width * 0.08f),
                    )
                }
            },
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.ui.foundation.refresh.OverscrollRefreshBoxTest"`
Expected: PASS (1 test). If `swipeDown` does not cross threshold, increase swipe distance via `swipeDown(startY = top, endY = bottom)` covering the full box height.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshBox.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshBoxTest.kt
git commit -m "feat(refresh): OverscrollRefreshBox orchestration + top indicator"
```

---

### Task 6: Records — add `refreshing` state and wire completion

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsUiState.kt:9-20`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModel.kt` (`refresh()` at ~:87, `loadPage()` completion at ~:125-143, `publish()` at ~:145)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsRefreshingStateTest.kt`

**Interfaces:**
- Consumes: existing `RecordsViewModel(querySource, savedCardRepository, lifetimeStatsSource, analytics, countUpGate, reviewSource, reviewClock)` and `SavedCardQuerySource.page(cardType, after, limit)`.
- Produces: `RecordsUiState.refreshing: Boolean` (default `false`) — true from `refresh()` start until the triggered reload's `loadPage` completes. Consumed by `RecordsScreen` (Task 7) as `OverscrollRefreshBox(isRefreshing = state.refreshing, ...)`.

- [ ] **Step 1: Write the failing test**

This test reuses the project's EXISTING reusable fakes (verified signatures) rather than hand-rolling new ones — only the query source is a small local fake that blocks the second (refresh) load so `refreshing == true` is observable mid-flight. Reused fakes: `FakeSavedCardRepository` (`feature.session.saved`, no-arg ctor), `FakeReviewSource` (`feature.review`, `FakeReviewSource(due: Int = 0)`), and the concrete `HistoryCountUpGate()`. `LifetimeStatsSource`, `HistoryAnalytics`, `ReviewClock` are tiny interfaces implemented inline with their exact real signatures.

Create `RecordsRefreshingStateTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.records

import com.google.firebase.firestore.DocumentSnapshot
import com.jjundev.oneclickeng.feature.review.FakeReviewSource
import com.jjundev.oneclickeng.feature.review.data.ReviewClock
import com.jjundev.oneclickeng.feature.session.saved.CardType
import com.jjundev.oneclickeng.feature.session.saved.FakeSavedCardRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsRefreshingStateTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    // 2번째(refresh) page() 호출을 gate 로 막아 refreshing==true 를 관측한다.
    private class BlockingQuerySource(private val gate: CompletableDeferred<Unit>) : SavedCardQuerySource {
        private var firstDone = false
        override suspend fun page(cardType: CardType, after: DocumentSnapshot?, limit: Int): SavedCardPage {
            if (firstDone) gate.await()
            firstDone = true
            return SavedCardPage(entries = emptyList(), cursor = null, endReached = true)
        }
    }

    private fun vm(query: SavedCardQuerySource) = RecordsViewModel(
        querySource = query,
        savedCardRepository = FakeSavedCardRepository(),
        lifetimeStatsSource = object : LifetimeStatsSource { override suspend fun lifetime(): LifetimeStats? = null },
        analytics = object : HistoryAnalytics {
            override fun tabView(cardType: CardType) {}
            override fun tabSwitch(cardType: CardType) {}
            override fun deleteCard(cardType: CardType, undone: Boolean) {}
        },
        countUpGate = HistoryCountUpGate(),
        reviewSource = FakeReviewSource(),
        reviewClock = object : ReviewClock { override fun nowMs() = 0L },
    )

    @Test fun refresh_setsRefreshingTrueThenFalseOnCompletion() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val viewModel = vm(BlockingQuerySource(gate))
        advanceUntilIdle()
        assertFalse("not refreshing after initial load", viewModel.uiState.value.refreshing)

        viewModel.refresh()
        advanceUntilIdle()
        assertTrue("refreshing while reload in flight", viewModel.uiState.value.refreshing)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse("refreshing cleared on completion", viewModel.uiState.value.refreshing)
    }
}
```

Verified real signatures used above: `SavedCardQuerySource.page(cardType: CardType, after: DocumentSnapshot?, limit: Int = PAGE_SIZE): SavedCardPage`; `SavedCardPage(entries, cursor, endReached)`; `LifetimeStatsSource.lifetime(): LifetimeStats?` (suspend); `HistoryAnalytics { tabView(CardType); tabSwitch(CardType); deleteCard(CardType, Boolean) }`; `ReviewClock.nowMs(): Long`; `HistoryCountUpGate()` (concrete). If any drift, read `RecordsViewModelTest.kt` and mirror its fakes exactly.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.feature.records.RecordsRefreshingStateTest"`
Expected: FAIL to compile — `RecordsUiState` has no `refreshing` property (the `viewModel.uiState.value.refreshing` references are unresolved). This is the only expected compile error; the fakes above match the real interfaces.

- [ ] **Step 3a: Add the field to `RecordsUiState.kt`**

In `RecordsUiState.kt`, add to the data class (after `dueCount`):

```kotlin
    /** 당겨서 새로고침 진행 여부(Firebase reload 인-플라이트). 스프링 복귀 대기용. */
    val refreshing: Boolean = false,
```

- [ ] **Step 3b: Track the flag in `RecordsViewModel.kt`**

Add a private field near the other mutable state (after `private var dueCount: Int = 0`):

```kotlin
        private var refreshing: Boolean = false
```

Set it true in `refresh()` — replace the current body:

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

Clear it when the reload completes — in the `viewModelScope.launch { ... }` block of `loadPage`, after the `typeStates[cardType] = state.copy(...)` assignment and before `publish()`, add a guard so an unrelated tab's `loadPage`/`loadMore` completion can't clear a refresh in flight on the currently-selected tab:

```kotlin
                if (cardType == selected) refreshing = false
```

Include the flag in `publish()` — add to the `RecordsUiState(...)` construction:

```kotlin
                    refreshing = refreshing,
```

Abandon the refresh on tab-switch — in `selectTab(cardType)`, after `selected = cardType` and before the load/publish branch, add `refreshing = false`. Without this, the `if (cardType == selected)` completion guard above strands `refreshing = true` forever when the user switches away from the tab mid-refresh (the in-flight load completes with `cardType != selected`, never clearing the flag), which permanently hangs the `OverscrollRefreshBox` (content stuck down, infinite spinner, dead gesture). Switching tabs abandons the pull gesture; the old tab's load still completes in the background.

```kotlin
        fun selectTab(cardType: CardType) {
            if (cardType == selected) return
            selected = cardType
            refreshing = false // 탭 전환 = 진행 중인 당겨서-새로고침 제스처 포기(박스 무한 대기 방지)
            analytics.tabSwitch(cardType)
            if (!typeStates.getValue(cardType).loaded) {
                loadFirstPage(cardType)
            } else {
                publish()
            }
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.feature.records.RecordsRefreshingStateTest"`
Expected: PASS. Also run the existing suite to confirm no regression: `../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.feature.records.RecordsViewModelTest"` → PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsUiState.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModel.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsRefreshingStateTest.kt
git commit -m "feat(records): expose refreshing flag for pull-to-refresh completion"
```

---

### Task 7: Records screen — wrap in OverscrollRefreshBox + wave + burst

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreen.kt` (`RecordsContent` at ~:102; the `TabScreenScaffold` usage at ~:118; card list rendering `cardList(...)` and `SavedCardRow` call site)
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/SavedCardRow.kt` (add optional `waveIndex` param) — OR apply `Modifier.refreshWave(index)` at the call site in `RecordsScreen.kt` (preferred: keep `SavedCardRow` unaware).
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreenPullRefreshTest.kt`

**Interfaces:**
- Consumes: `OverscrollRefreshBox` (Task 5), `Modifier.refreshWave` (Task 2), `RecordsUiState.refreshing` (Task 6), existing `RecordsViewModel.refresh()`.
- Produces: no new public API (screen wiring only).

- [ ] **Step 1: Write the failing test**

First read `RecordsScreen.kt` and `RecordsScreenRefreshTest.kt` to copy the exact test harness (TestLifecycleOwner, fake VM construction). Create `RecordsScreenPullRefreshTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.records

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
// ... same imports/fakes as RecordsScreenRefreshTest.kt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecordsScreenPullRefreshTest {
    @get:Rule val rule = createComposeRule()

    @Test fun pullDownAtTop_triggersReload() {
        // Build a fake RecordsViewModel whose querySource counts page() calls,
        // seeded with >1 card so the list is scrollable and starts at top.
        val query = CountingQuerySource(cardsPerPage = 3) // records call count
        val viewModel = buildRecordsViewModel(query) // helper mirroring RecordsScreenRefreshTest
        rule.setContent {
            com.jjundev.oneclickeng.ui.theme.OceTheme {
                RecordsScreen(viewModel = viewModel, modifier = Modifier.testTag("records"))
            }
        }
        rule.waitForIdle()
        val before = query.calls
        rule.onNodeWithTag("records").performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertTrue("pull triggered a reload page() call", query.calls > before)
    }
}
```

(Implement `CountingQuerySource` and `buildRecordsViewModel` inline, copying the fakes from `RecordsScreenRefreshTest.kt`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.feature.records.RecordsScreenPullRefreshTest"`
Expected: FAIL — no pull-to-refresh yet; `query.calls` unchanged after swipe.

- [ ] **Step 3: Wrap the Records content**

In `RecordsScreen.kt`, wrap the `TabScreenScaffold(...)` block inside `RecordsContent` with `OverscrollRefreshBox`, threading `refresh` through. Sketch:

```kotlin
// imports
import com.jjundev.oneclickeng.ui.foundation.refresh.OverscrollRefreshBox
import com.jjundev.oneclickeng.ui.foundation.refresh.refreshWave

@Composable
internal fun RecordsContent(
    state: RecordsUiState,
    onSelectTab: (CardType) -> Unit,
    onDelete: (SavedCardEntry) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,          // NEW — wire from RecordsScreen to viewModel::refresh
    modifier: Modifier = Modifier,
    // ...existing params
) {
    OverscrollRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        TabScreenScaffold(titleRes = R.string.tab_records, listState = listState) {
            // ...existing items...
            // In the card list, apply the wave per visible index:
            // itemsIndexed(state.cards) { index, entry ->
            //     SavedCardRow(entry, ..., modifier = Modifier.refreshWave(index))
            // }
        }
    }
}
```

In `RecordsScreen(...)`, pass `onRefresh = viewModel::refresh` into `RecordsContent`. The card list (`cardList(...)` in `RecordsScreen.kt`) already renders via `items(state.cards.size, key = { ... }) { index -> ... }`, so `index` is already in scope — just append `Modifier.refreshWave(index)` onto the `SavedCardRow`'s existing modifier at that call site (chain, don't replace). Do NOT change `SavedCardRow`'s internals — pass the modifier in. Import `com.jjundev.oneclickeng.ui.foundation.refresh.refreshWave`.

Note: `RecordsResumeEffect` (ON_RESUME → `refreshOnResume()`) stays as-is; pull-to-refresh is an additional trigger.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.feature.records.RecordsScreenPullRefreshTest"`
Expected: PASS. Re-run `RecordsScreenRefreshTest` to confirm resume-refresh still works: `../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.feature.records.RecordsScreenRefreshTest"` → PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreen.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreenPullRefreshTest.kt
git commit -m "feat(records): pull-to-refresh reloads cards from Firebase with wave+burst"
```

---

### Task 8: Home screen — pull-to-refresh refreshes 추천 상황 only

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt` (`HomeContent` at ~:252; the `LazyColumn` at ~:287; the `situationsCardItems` / `SituationRow` call at ~:440)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomePullRefreshTest.kt`

**Interfaces:**
- Consumes: `OverscrollRefreshBox` (Task 5), `Modifier.refreshWave` (Task 2), existing `HomeViewModel.refreshSituations()`.
- Produces: no new public API. Home's `isRefreshing` is always `false` (refresh is synchronous/local), so the `MinVisibleMs` floor governs the hold; only 추천 상황 rotates.

- [ ] **Step 1: Write the failing test**

Create `HomePullRefreshTest.kt` (mirror `HomeSituationTapTest.kt` harness — read it first):

```kotlin
package com.jjundev.oneclickeng.feature.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomePullRefreshTest {
    @get:Rule val rule = createComposeRule()

    @Test fun pullDown_refreshesSituationsOnly() {
        var refreshSituationsCalls = 0
        val state = HomeUiState(
            // icon defaults to OceIcon.Hub — do NOT pass an emoji string (3rd arg is OceIcon, not String).
            situations = List(4) { HomeSituation(id = "id$it", labelKo = "상황 $it", promptSeed = "seed$it") },
            // header/stats/hero fields left default
        )
        rule.setContent {
            com.jjundev.oneclickeng.ui.theme.OceTheme {
                HomeContent(
                    state = state,
                    onStartLearning = {},
                    onResumeContinue = {},
                    onResumeStartNew = {},
                    onViewRecords = {},
                    onOfflineBlocked = {},
                    onRefreshSituations = { refreshSituationsCalls++ },
                    modifier = Modifier.testTag("home"),
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("home").performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertTrue("pull refreshed 추천 상황", refreshSituationsCalls >= 1)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.feature.home.HomePullRefreshTest"`
Expected: FAIL — no pull-to-refresh; `refreshSituationsCalls` stays 0.

- [ ] **Step 3: Wrap the Home content**

In `HomeScreen.kt` `HomeContent`, wrap the `LazyColumn(...)` in `OverscrollRefreshBox`. The `onRefresh` calls the EXISTING `onRefreshSituations` (which triggers `viewModel.refreshSituations()`), preserving the existing skeleton flash. Do NOT reload gamification/profile. Sketch:

```kotlin
import com.jjundev.oneclickeng.ui.foundation.refresh.OverscrollRefreshBox
import com.jjundev.oneclickeng.ui.foundation.refresh.refreshWave

// inside HomeContent, replace `LazyColumn(...) { ... }` with:
OverscrollRefreshBox(
    isRefreshing = false,               // 추천 상황 회전은 동기/로컬 → 최소 표시 시간이 지배
    onRefresh = onRefreshSituations,    // 오직 추천 상황만 새로고침 (오늘 N분/streak/hero 불변)
    modifier = modifier,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = OceTheme.spacing.xl),
        contentPadding = PaddingValues(bottom = OceBottomNavDefaults.overlayContentBottomPadding),
    ) {
        // ...existing items unchanged...
    }
}
```

In `situationsCardItems` (HomeScreen.kt ~411-451), add the wave to BOTH render paths — grid AND list — by APPENDING `.refreshWave(index)` onto the EXISTING modifier chain (do not replace the `.staggerReveal(6 + index, entrance).padding(...)` chain, or you lose the entrance animation). Both `SituationRow` and `SituationCell` already accept a `modifier` param.

- List path (`gridMode == false`), at the `SituationRow(...)` call:

```kotlin
SituationRow(
    situation = situation,
    onClick = { onSituationSelected(situation) },
    modifier =
        Modifier
            .staggerReveal(6 + index, entrance)
            .padding(top = if (index == 0) OceTheme.spacing.lg else OceTheme.spacing.sm)
            .refreshWave(index),           // NEW — wave opt-in, chained after existing modifiers
)
```

- Grid path (`gridMode == true`), on the `Row(...)` that holds each pair — apply the wave once per row using the row `index` so both `SituationCell`s in a row ripple together:

```kotlin
Row(
    modifier =
        Modifier
            .staggerReveal(6 + index, entrance)
            .fillMaxWidth()
            .padding(top = if (index == 0) OceTheme.spacing.lg else OceTheme.spacing.sm)
            .height(IntrinsicSize.Min)
            .refreshWave(index),           // NEW — grid rows ripple too
    horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
) { /* ...existing SituationCell content unchanged... */ }
```

Import `com.jjundev.oneclickeng.ui.foundation.refresh.refreshWave` in `HomeScreen.kt`.

Note: the existing "새로고침" text control and `flashSituationsSkeleton` remain; pull-to-refresh is an additional entry point calling the same `onRefreshSituations`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.feature.home.HomePullRefreshTest"`
Expected: PASS. Re-run existing Home tests: `../scripts/verify-android.sh test --tests "com.jjundev.oneclickeng.feature.home.HomeSituationTapTest" --tests "com.jjundev.oneclickeng.feature.home.HomeSituationsSkeletonTest"` → PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomePullRefreshTest.kt
git commit -m "feat(home): pull-to-refresh rotates 추천 상황 with wave+burst"
```

---

### Task 9: Screenshot baseline (held state) + full verify + manual device check

**Files:**
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshScreenshotTest.kt`
- Reference: existing `*ScreenshotTest.kt` (Roborazzi) for the exact rule/config.

**Interfaces:**
- Consumes: `OverscrollRefreshBox`, `OverscrollRefreshState` (drive `offset` to a fixed held value for a deterministic frame).

- [ ] **Step 1: Write a deterministic held-state screenshot test**

Read an existing Roborazzi `*ScreenshotTest.kt` first to copy the `@get:Rule` / `captureRoboImage` setup. Create `OverscrollRefreshScreenshotTest.kt` that renders `OverscrollRefreshBox` with a sample list, snaps `state.offset` to `HoldOffset`, and captures — giving a stable frame of the "pulled + indicator visible" state (no mid-animation flake).

```kotlin
// Pattern (fill in from the repo's screenshot harness):
// @Test fun overscroll_heldState() {
//   lateinit var state: OverscrollRefreshState
//   composeRule.setContent {
//     OceTheme {
//       state = rememberOverscrollRefreshState()
//       OverscrollRefreshBox(isRefreshing = true, onRefresh = {}, state = state) { SampleList() }
//     }
//   }
//   composeRule.runOnIdle { runBlocking { state.offset.snapTo(with(density){OverscrollDefaults.HoldOffset.toPx()}) } }
//   composeRule.onRoot().captureRoboImage()
// }
```

- [ ] **Step 2: Generate the baseline**

Run: `cd android && ../scripts/verify-android.sh test -Proborazzi.test.record=true --tests "com.jjundev.oneclickeng.ui.foundation.refresh.OverscrollRefreshScreenshotTest"`
Expected: baseline PNG written under the module's Roborazzi output dir. Inspect it: the content is pushed down by ~56dp, the grey circular indicator is visible at the top.

- [ ] **Step 3: Full verification**

Run: `cd android && ../scripts/verify-android.sh test`
Expected: entire JVM test suite PASS (new refresh tests + unchanged Home/Records suites).

- [ ] **Step 4: Manual device/emulator verification (required — animation fidelity)**

Because the wave/burst/bouncy-spring timing is visual, verify on a device/emulator against the confirmed prototype (`prototype/experiments/overscroll-top-refresh.html`):
- 학습 탭: pull down from the top (including on the header title area) → whole screen slides down as a block, header text + situation cards wave top→down, transparent burst fires from the top indicator, content springs back with a visible bounce, and ONLY 추천 상황 changed (오늘 N분 / streak / hero unchanged). Toggle grid mode (`onToggleLayout`) and pull again — the grid rows must wave too (verifies the `SituationCell`/grid path from Task 8).
- 기록 탭: pull down → same animation; the indicator holds (spinning) until the Firestore reload returns, then bounces back; cards reflect the reloaded data; burst fires on completion.
- Reconcile any timing/offset mismatch by tuning ONLY `OverscrollDefaults` (Task 1) — do not scatter magic numbers. Re-run Task 9 Step 2 to update the baseline if the held offset changed.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshScreenshotTest.kt android/app/src/test/**/roborazzi/*Overscroll* android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollMath.kt
git commit -m "test(refresh): held-state screenshot baseline + tuned overscroll defaults"
```

---

## Notes for the implementer

- **Prototype is the visual source of truth.** All timing/geometry constants trace to `prototype/experiments/overscroll-top-refresh.html` (search its `OverscrollRefresh` class, `playWave`, `burst`, `springBack`, and the CSS `@keyframes waveBob/waveSoft/burstFly`). When in doubt about feel, open that file in a browser.
- **Density caveat.** Prototype constants are CSS px at a 390-wide phone. The `dp` values in `OverscrollDefaults` are 1:1 ports as a starting point; Task 9's manual check is where they get reconciled. Expect to nudge `MaxPull`, `Threshold`, `HoldOffset`, and `SpringStiffness`.
- **`@ExperimentalMaterial3Api` is NOT needed** — this implementation avoids `PullToRefreshBox` entirely and uses `NestedScrollConnection` + `Animatable`, which are stable APIs.
- **Burst on failure (기록).** The current `FirestoreSavedCardQuerySource` swallows errors into an empty page, so a failed reload is indistinguishable from an empty result; per the confirmed decision the burst fires on every completed refresh on both tabs. Surfacing load errors to suppress the burst is a future enhancement, out of scope here.

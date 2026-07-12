# 화면 진입 순차 스태거(oc-rise) + 렉 유발 slide 제거 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 프로토타입 `.oc-home-stagger`(섹션 카드가 위에서부터 110ms 간격으로 순차 상승·페이드하는 `oc-rise`)를 이식하고, 현재 렉("지지직")의 직접 원인인 화면 전체 `slideInVertically`를 제거해 전환을 부드럽게 만든다.

**Architecture:** 재사용 스태거 프리미티브(`ScreenEntrance.kt`)를 신설한다 — 진입 창(window) 동안 첫 컴포즈된 섹션만 `graphicsLayer`(합성 전용, relayout 없음)로 상승+페이드시키는 `Modifier.staggerReveal(index, entrance)`. NavHost 컨테이너 전환은 무거운 트리를 미는 slide를 버리고 빠른 페이드만 남긴다(`oceScreenEnter` 재작성). 세 탭(Home/Records/Settings) LazyColumn 섹션과 온보딩(Level/Topic) Column 자식에 스태거를 적용한다.

**Tech Stack:** Kotlin, Jetpack Compose, `androidx.compose.animation.core.Animatable`, `graphicsLayer`, JUnit4, Roborazzi(스크린샷).

## Global Constraints

- **oc-rise 정본(프로토타입):** translateY `14dp`→0 + opacity 0→1, duration `620ms`, easing `easingOut`(=`cubic-bezier(0.22,1,0.36,1)`, `OceMotion.easingOut`). 지연 = `40ms + 110ms*index`, index는 `11`에서 캡(=1250ms). reduce-motion → 애니메이션 없음(즉시 최종 상태). 근거: `prototype/Prototype Flow (standalone).html` `.oc-home-stagger`/`@keyframes oc-rise`.
- 모션 값은 `OceTheme.motion`(OceMotion 토큰)에서만 — duration/easing 하드코딩 금지(스태거 전용 상수 `STAGGER_*`는 프로토 실측값이라 예외적으로 `ScreenEntrance.kt`에 명명 상수로 둔다).
- **컨테이너 전환:** 화면↔화면(NavHost) 전환에서 `slideInVertically` 완전 제거 — 빠른 fadeIn만(`durationFastMs=100ms`). exit = `ExitTransition.None`(하드컷) 유지. reduce-motion → `EnterTransition.None`.
- **스태거 발동:** 진입 시 1회, 진입 창 동안 첫 컴포즈된(=초기 보이는) 섹션만. 이후 스크롤로 들어온 섹션은 즉시 표시(재발동 없음).
- **골든 불변 계약:** `reduceMotion=true`면 `staggerReveal`은 no-op(최종 상태 그대로)이어야 한다 → 스크린샷 테스트는 `reduceMotion=true`로 렌더해 기존 Roborazzi 골든이 **재생성 없이** 통과해야 한다.
- 검증은 반드시 `scripts/verify-android.sh`(워크트리 gradle 격리·google-services 복사). detekt 통과(미사용 import 제거).

---

### Task 1: 스태거 프리미티브 `ScreenEntrance` + 지연 계산 단위테스트

프로토 `oc-rise` 스태거를 이식하는 재사용 프리미티브. 순수 지연 계산(`staggerDelayMs`)은 JVM 단위테스트로 프로토 시퀀스(40/150/260…1250, 캡)를 반증가능하게 고정한다. Modifier 자체는 `graphicsLayer` 합성 전용이라 relayout 이 없어 렉을 유발하지 않는다.

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/ScreenEntrance.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/ScreenEntranceTest.kt`

**Interfaces:**
- Consumes: `OceTheme.motion.easingOut`(`com.jjundev.oneclickeng.ui.theme`).
- Produces (Task 3·4·5가 소비):
  - `fun staggerDelayMs(index: Int): Int`
  - `class ScreenEntranceState`(필드 `active: Boolean`)
  - `@Composable fun rememberScreenEntrance(reduceMotion: Boolean, windowMs: Int = 400): ScreenEntranceState`
  - `fun Modifier.staggerReveal(index: Int, entrance: ScreenEntranceState): Modifier`
  - 상수 `STAGGER_RISE_DP=14`, `STAGGER_DURATION_MS=620`, `STAGGER_BASE_DELAY_MS=40`, `STAGGER_STEP_MS=110`, `STAGGER_MAX_INDEX=11`

- [ ] **Step 1: 실패 테스트 작성**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/ScreenEntranceTest.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.foundation

import org.junit.Assert.assertEquals
import org.junit.Test

/** 스태거 지연 순수 검증 — 프로토 .oc-home-stagger nth-child 시퀀스(40/150/260…1250)와 캡. */
class ScreenEntranceTest {
    @Test
    fun delays_match_prototype_sequence() {
        assertEquals(40, staggerDelayMs(0))
        assertEquals(150, staggerDelayMs(1))
        assertEquals(260, staggerDelayMs(2))
        assertEquals(370, staggerDelayMs(3))
        assertEquals(1250, staggerDelayMs(11))
    }

    @Test
    fun delay_is_capped_at_max_index() {
        assertEquals(staggerDelayMs(11), staggerDelayMs(50))
    }

    @Test
    fun negative_index_clamps_to_base() {
        assertEquals(40, staggerDelayMs(-3))
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ScreenEntranceTest*'`
Expected: FAIL — `unresolved reference: staggerDelayMs`.

- [ ] **Step 3: 프리미티브 구현**

Create `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/ScreenEntrance.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.foundation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlinx.coroutines.delay

// oc-rise 정본(prototype .oc-home-stagger): translateY 14px→0 + opacity 0→1, 0.62s easeOut,
// 지연 40 + 110*index (nth-child 1..12 → 40..1250ms).
const val STAGGER_RISE_DP = 14
const val STAGGER_DURATION_MS = 620
const val STAGGER_BASE_DELAY_MS = 40
const val STAGGER_STEP_MS = 110
const val STAGGER_MAX_INDEX = 11

/** nth-child 스태거 지연(ms): 40 + 110*clamp(index,0,11). 순수 함수(테스트 대상). */
fun staggerDelayMs(index: Int): Int =
    STAGGER_BASE_DELAY_MS + STAGGER_STEP_MS * index.coerceIn(0, STAGGER_MAX_INDEX)

/**
 * 화면 진입 스태거 게이트. [active] 가 true 인 동안(진입 창) 첫 컴포즈된 섹션만 애니메이션한다.
 * 창은 [rememberScreenEntrance] 의 windowMs 후 닫혀, 이후 스크롤로 들어온 섹션은 즉시 표시(재발동 없음).
 */
class ScreenEntranceState internal constructor(active: Boolean) {
    var active: Boolean by mutableStateOf(active)
        internal set
}

/**
 * 진입 스태거 상태 생성. [reduceMotion] 이면 상시 비활성(즉시 최종 상태). 아니면 [windowMs] 동안 창을 열어
 * 그 사이 첫 컴포즈된 섹션이 스태거로 등장하고, 이후 창을 닫는다.
 */
@Composable
fun rememberScreenEntrance(
    reduceMotion: Boolean,
    windowMs: Int = 400,
): ScreenEntranceState {
    val state = remember { ScreenEntranceState(active = !reduceMotion) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            state.active = false
            return@LaunchedEffect
        }
        delay(windowMs.toLong())
        state.active = false
    }
    return state
}

/**
 * 프로토 oc-rise 스태거를 적용한다. 첫 컴포지션에 [entrance].active 를 스냅샷해, 진입 창 안이면 [index] 지연 후
 * 상승([STAGGER_RISE_DP]dp)+페이드([STAGGER_DURATION_MS]ms, easeOut), 아니면 즉시 최종 상태(no-op).
 * graphicsLayer 합성만 사용 → relayout 없음(부드러움). reduce-motion 은 [entrance] 가 상시 비활성이라 자동 no-op.
 */
fun Modifier.staggerReveal(
    index: Int,
    entrance: ScreenEntranceState,
): Modifier =
    composed {
        val animateOnEnter = remember { entrance.active }
        if (!animateOnEnter) return@composed this
        val progress = remember { Animatable(0f) }
        val risePx = with(LocalDensity.current) { STAGGER_RISE_DP.dp.toPx() }
        val easing = OceTheme.motion.easingOut
        LaunchedEffect(Unit) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec =
                    tween(
                        durationMillis = STAGGER_DURATION_MS,
                        delayMillis = staggerDelayMs(index),
                        easing = easing,
                    ),
            )
        }
        graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * risePx
        }
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ScreenEntranceTest*'`
Expected: PASS (3 tests).

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/ScreenEntrance.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/ScreenEntranceTest.kt
git commit -m "feat(anim): add oc-rise stagger entrance primitive"
```

---

### Task 2: 컨테이너 전환을 빠른 페이드로 (렉 유발 slide 제거)

현재 `oceScreenEnter`는 화면 트리 전체를 `slideInVertically`로 밀어 첫 컴포지션과 겹쳐 프레임을 떨어뜨린다. slide를 제거하고 빠른 fadeIn 만 남긴다. 스태거(Task 3~5)가 "샤라락" 등장을 담당한다.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavTransitions.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavHost.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavTransitionsTest.kt`

**Interfaces:**
- Produces (변경): `fun oceScreenEnter(motion: OceMotion, reduceMotion: Boolean): EnterTransition` — `offsetY8Px` 파라미터 제거. `oceScreenExit` 불변.
- Consumes: `OceMotion.durationFastMs`, `easingStandard`.

- [ ] **Step 1: 테스트를 새 시그니처로 갱신(먼저 실패시키기)**

Edit `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavTransitionsTest.kt` — 두 호출에서 `offsetY8Px = 24,` 인자를 제거한다.

교체 전:
```kotlin
        assertSame(EnterTransition.None, oceScreenEnter(motion, offsetY8Px = 24, reduceMotion = true))
```
교체 후:
```kotlin
        assertSame(EnterTransition.None, oceScreenEnter(motion, reduceMotion = true))
```

교체 전:
```kotlin
        assertNotSame(EnterTransition.None, oceScreenEnter(motion, offsetY8Px = 24, reduceMotion = false))
```
교체 후:
```kotlin
        assertNotSame(EnterTransition.None, oceScreenEnter(motion, reduceMotion = false))
```

- [ ] **Step 2: 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*OceNavTransitionsTest*'`
Expected: FAIL — 컴파일 에러(`too many arguments` / 시그니처 불일치).

- [ ] **Step 3: `oceScreenEnter` 를 fade-only 로 재작성**

Edit `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavTransitions.kt`.

import 정리 — 제거:
```kotlin
import androidx.compose.animation.slideInVertically
```
추가(없으면):
```kotlin
import androidx.compose.animation.fadeIn
```

함수 교체 전:
```kotlin
fun oceScreenEnter(
    motion: OceMotion,
    offsetY8Px: Int,
    reduceMotion: Boolean,
): EnterTransition =
    if (reduceMotion) {
        EnterTransition.None
    } else {
        fadeIn(tween(motion.durationBaseMs, easing = motion.easingOut)) +
            slideInVertically(tween(motion.durationBaseMs, easing = motion.easingOut)) { offsetY8Px }
    }
```

함수 교체 후:
```kotlin
fun oceScreenEnter(
    motion: OceMotion,
    reduceMotion: Boolean,
): EnterTransition =
    if (reduceMotion) {
        EnterTransition.None
    } else {
        // 컨테이너는 빠른 페이드만 — slide 제거(렉 원인). 화면 등장감은 섹션 스태거(oc-rise)가 담당.
        fadeIn(tween(motion.durationFastMs, easing = motion.easingStandard))
    }
```

`oceScreenExit`(= `ExitTransition.None`)와 그 KDoc은 그대로 둔다. 파일 KDoc 의 "fade + 8dp 상승" 문구가 있으면 "fade(slide 없음)"로 정정한다.

**스테일 KDoc 정리(필수):** `offsetY8Px` 파라미터를 지웠으므로 `oceScreenEnter` 위 KDoc 의 `@param offsetY8Px …` 줄을 삭제한다(존재하지 않는 파라미터 문서화 = detekt/리뷰 지적). 또한 Step 1 에서 편집하는 `OceNavTransitionsTest.kt` 상단 KDoc 에 "offsetY8Px 는 density=3 기준 8dp=24px 예시값" 류 문구가 있으면 함께 삭제한다.

- [ ] **Step 4: `OceNavHost` 호출부 갱신(density/offset 제거)**

Edit `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavHost.kt`.

import 제거(더는 미사용):
```kotlin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
```

교체 전:
```kotlin
    val motion = OceTheme.motion
    val offsetY8Px = with(LocalDensity.current) { 8.dp.roundToPx() }
    NavHost(
        navController = navController,
        startDestination = OceTab.Start.route,
        modifier = modifier,
        enterTransition = { oceScreenEnter(motion, offsetY8Px, reduceMotion) },
        exitTransition = { oceScreenExit },
        popEnterTransition = { oceScreenEnter(motion, offsetY8Px, reduceMotion) },
        popExitTransition = { oceScreenExit },
    ) {
```

교체 후:
```kotlin
    val motion = OceTheme.motion
    NavHost(
        navController = navController,
        startDestination = OceTab.Start.route,
        modifier = modifier,
        enterTransition = { oceScreenEnter(motion, reduceMotion) },
        exitTransition = { oceScreenExit },
        popEnterTransition = { oceScreenEnter(motion, reduceMotion) },
        popExitTransition = { oceScreenExit },
    ) {
```

- [ ] **Step 5: `AppRoot` outer NavHost 호출부 갱신**

Edit `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt`.

import 제거(더는 미사용):
```kotlin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
```

교체 전:
```kotlin
    val reduceMotion = rememberReduceMotion()
    val motion = OceTheme.motion
    val offsetY8Px = with(LocalDensity.current) { 8.dp.roundToPx() }
    NavHost(
        navController = outerNavController,
        startDestination = resolvedStart,
        enterTransition = { oceScreenEnter(motion, offsetY8Px, reduceMotion) },
        exitTransition = { oceScreenExit },
        popEnterTransition = { oceScreenEnter(motion, offsetY8Px, reduceMotion) },
        popExitTransition = { oceScreenExit },
    ) {
```

교체 후:
```kotlin
    val reduceMotion = rememberReduceMotion()
    val motion = OceTheme.motion
    NavHost(
        navController = outerNavController,
        startDestination = resolvedStart,
        enterTransition = { oceScreenEnter(motion, reduceMotion) },
        exitTransition = { oceScreenExit },
        popEnterTransition = { oceScreenEnter(motion, reduceMotion) },
        popExitTransition = { oceScreenExit },
    ) {
```

- [ ] **Step 6: 전체 검증**

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL — detekt clean(미사용 import 0), `OceNavTransitionsTest` 통과, `AppNavigationTest` 컴파일 유지, 전 단위테스트 통과.

- [ ] **Step 7: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavTransitions.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavHost.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavTransitionsTest.kt
git commit -m "fix(nav): drop full-screen slide (jank); container fade-only"
```

---

### Task 3: 홈 화면 섹션 스태거 적용

`HomeContent`(LazyColumn)의 상단 섹션들에 `staggerReveal` 을 적용한다. `HomeContent` 는 이미 `reduceMotion: Boolean = false` 파라미터를 가진다([HomeScreen.kt:222]).

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreenScreenshotTest.kt`

**Interfaces:**
- Consumes: `rememberScreenEntrance(reduceMotion)`, `Modifier.staggerReveal(index, entrance)`(Task 1).

**섹션 → index 매핑(HomeContent):**

| item key | index |
|---|---|
| `reminder_banner` | 0 |
| `header` | 1 |
| `stats` | 2 |
| `hero` | 3 |
| `new_chat` / `settings_inline` (택1) | 4 |
| `situations_header` | 5 |
| grid rows / situation rows (`itemsIndexed`) | `6 + index`(내부 index) |
| `more_situations` | 11 |
| `atLimit` | 11 |

- [ ] **Step 1: 진입 상태 생성 + import 추가**

`HomeContent` 본문 최상단(LazyColumn 호출 직전)에 추가:
```kotlin
    val entrance = rememberScreenEntrance(reduceMotion)
```

import 추가:
```kotlin
import com.jjundev.oneclickeng.ui.foundation.rememberScreenEntrance
import com.jjundev.oneclickeng.ui.foundation.staggerReveal
```

- [ ] **Step 2: 각 섹션 컴포저블의 `modifier` 앞에 `.staggerReveal(index, entrance)` 삽입**

각 `item {}` 안에서 렌더되는 섹션 컴포저블의 `modifier = Modifier.…` 를 `modifier = Modifier.staggerReveal(<index>, entrance).…` 로 바꾼다. 매핑 표의 index 를 사용한다.

예시(실제 코드 — `stats` 섹션, index=2):

교체 전:
```kotlin
        item(key = "stats") {
            StatsStrip(
                studyTimeLabel = state.studyTimeLabel,
                streak = state.streak,
                modifier = Modifier.padding(top = OceTheme.spacing.md),
            )
        }
```
교체 후:
```kotlin
        item(key = "stats") {
            StatsStrip(
                studyTimeLabel = state.studyTimeLabel,
                streak = state.streak,
                modifier = Modifier.staggerReveal(2, entrance).padding(top = OceTheme.spacing.md),
            )
        }
```

동일 패턴을 나머지 섹션에 적용한다(매핑 표 index):
- `reminder_banner` → `OneClickReminderEnabledBanner(... modifier = Modifier.staggerReveal(0, entrance).padding(top = OceTheme.spacing.lg))`
- `header` → `Column(modifier = Modifier.staggerReveal(1, entrance).padding(top = OceTheme.spacing.xxl), …)`
- `hero` → `HeroCta(... modifier = Modifier.staggerReveal(3, entrance).padding(top = OceTheme.spacing.xl))`
- `new_chat` → `NewChatLink(... modifier = Modifier.staggerReveal(4, entrance).padding(top = OceTheme.spacing.md))`
- `settings_inline` → `SettingsInline(... modifier = Modifier.staggerReveal(4, entrance).padding(top = OceTheme.spacing.md))`
- `situations_header` → `SituationsHeader(... modifier = Modifier.staggerReveal(5, entrance).padding(top = OceTheme.spacing.xxl))`
- grid `Row(modifier = Modifier.staggerReveal(6 + index, entrance).fillMaxWidth().padding(...).height(IntrinsicSize.Min), …)` (기존 `Modifier.fillMaxWidth()...` 앞에 삽입)
- situation `SituationRow(... modifier = Modifier.staggerReveal(6 + index, entrance).padding(top = …))`
- `more_situations` → `MoreSituationsButton(... modifier = Modifier.staggerReveal(11, entrance).padding(top = OceTheme.spacing.xl))`
- `atLimit` → `OneClickAtLimitNotice(... modifier = Modifier.staggerReveal(11, entrance).padding(top = OceTheme.spacing.md))`

- [ ] **Step 3: 스크린샷 테스트를 `reduceMotion = true` 로 렌더(골든 불변 보장)**

`HomeScreenScreenshotTest.kt` 에서 `HomeContent(...)`(또는 `HomeScreen(...)`) 호출에 `reduceMotion = true` 를 명시한다(이미 있으면 확인만). 이유: `reduceMotion=true` → `staggerReveal` no-op → 최종 상태 렌더 → 기존 골든과 동일.

확인 방법: `grep -n "reduceMotion" HomeScreenScreenshotTest.kt`. 각 렌더 호출에 인자가 없으면 `reduceMotion = true,` 를 추가한다.

- [ ] **Step 4: 검증 (스크린샷 골든 재생성 없이 통과)**

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL — detekt clean, `HomeScreenScreenshotTest`·`HomeHeroRevealTest` 포함 전 단위테스트 통과. 스크린샷은 골든 **재생성 없이** 일치해야 한다(불일치 시 = reduceMotion 미적용 → Step 3 재확인).

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreenScreenshotTest.kt
git commit -m "feat(home): oc-rise stagger on home sections"
```

---

### Task 4: 기록·설정 탭 섹션 스태거 적용

`RecordsContent`([RecordsScreen.kt:93])·`SettingsContent`([SettingsScreen.kt:276]) LazyColumn 섹션에 스태거를 적용한다. 두 Content 는 현재 `reduceMotion` 파라미터가 **없으므로** 추가하고, 각 Screen 컴포저블에서 `rememberReduceMotion()` 을 읽어 넘긴다.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreen.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreenScreenshotTest.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt`

**Interfaces:**
- Consumes: `rememberReduceMotion()`(`com.jjundev.oneclickeng.ui.foundation`), `rememberScreenEntrance`, `staggerReveal`.

**섹션 → index 매핑:**
- Records: `lifetime`=0, `count`=1, `empty`=1, 이어지는 기록 카드 리스트는 `2 + 카드index`. `load_more`(line 179)는 **표시 내용 없이 `LaunchedEffect` 만** 있으므로 스태거 제외(no-op).
- Settings: `profile_card`=0, `voice_card`=1, `notify_card`=2, `data_card`=3, `account_card`=4, `info_card`=5. **섹션 헤더는 스태거 제외**(공유 `sectionHeader()` 헬퍼 변경 회피 — 헤더는 작고 바로 뒤 카드와 함께 등장해도 자연스럽다). 카드 `item` 만 스태거.

**구조 주의(리뷰 확인):**
- Records 카드/`empty`/`load_more` 는 `RecordsContent` 가 직접 렌더하지 않고 별도 `LazyListScope.cardList(...)` 확장(RecordsScreen.kt:153)에 산다. `entrance` 와 base index(=2)를 `cardList` 파라미터로 **함께 전달**해 카드에 `.staggerReveal(2 + index, entrance)` 를 적용한다. `entrance` 를 확장으로 넘기지 않으면 카드에 스태거를 걸 수 없다.
- Settings 섹션 헤더는 공유 `sectionHeader()`(SettingsScreen.kt:485)를 쓰므로 위 방침대로 **건드리지 않는다**.

- [ ] **Step 1: `reduceMotion` 파라미터 추가 + 배선(Records)**

`RecordsContent` 시그니처에 `reduceMotion: Boolean = false,` 를 추가하고, `RecordsScreen`(공개 컴포저블)에서 `RecordsContent(...)` 호출에 `reduceMotion = rememberReduceMotion()` 를 넘긴다.

`RecordsScreen.kt` import 추가:
```kotlin
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.foundation.rememberScreenEntrance
import com.jjundev.oneclickeng.ui.foundation.staggerReveal
```

`RecordsContent` 본문 LazyColumn 직전:
```kotlin
    val entrance = rememberScreenEntrance(reduceMotion)
```

- [ ] **Step 2: Records 섹션에 `.staggerReveal(index, entrance)` 삽입**

`RecordsContent` 가 직접 렌더하는 `lifetime`(0)·`count`(1)·`empty`(1) 섹션의 `modifier` 앞에 매핑 index 로 `.staggerReveal(...)` 를 삽입한다(Task 3 Step 2 와 동일 패턴). 섹션 컴포저블이 `modifier` 를 받지 않으면 `item {}` 내용을 `Box(modifier = Modifier.staggerReveal(index, entrance)) { … }` 로 감싼다.

카드 리스트는 `LazyListScope.cardList(...)` 확장(RecordsScreen.kt:153)에 있으므로, 그 확장 시그니처에 `entrance: ScreenEntranceState`(+ base index=2)를 추가하고 `RecordsContent` 호출부에서 넘긴 뒤, 각 카드 `item` 에 `.staggerReveal(2 + index, entrance)` 를 적용한다. `load_more` 는 제외(no-op). import 에 `com.jjundev.oneclickeng.ui.foundation.ScreenEntranceState` 추가.

- [ ] **Step 3: `reduceMotion` 추가 + 배선 + 스태거(Settings)**

`SettingsContent` 에 `reduceMotion: Boolean = false,` 추가, `SettingsScreen` 에서 `reduceMotion = rememberReduceMotion()` 배선, import(위와 동일 3종) 추가, LazyColumn 직전 `val entrance = rememberScreenEntrance(reduceMotion)`, 각 **카드** 섹션(`profile_card`~`info_card`) `modifier` 앞에 매핑 index 로 `.staggerReveal(...)` 삽입. 섹션 헤더(공유 `sectionHeader()` 헬퍼, line 485)는 **건드리지 않는다**(방침대로 제외).

- [ ] **Step 4: 스크린샷 테스트 `reduceMotion = true` 렌더**

`RecordsScreenScreenshotTest.kt`·`SettingsScreenScreenshotTest.kt` 의 렌더 호출에 `reduceMotion = true` 를 명시한다(골든 불변 계약). `grep -n "reduceMotion\|RecordsContent\|RecordsScreen" …` 로 각 호출부 확인 후 인자 추가.

- [ ] **Step 5: 검증**

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL — detekt clean, Records·Settings 스크린샷 골든 **재생성 없이** 통과, 전 단위테스트 통과.

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreen.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreenScreenshotTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt
git commit -m "feat(records,settings): oc-rise stagger on tab sections"
```

---

### Task 5: 온보딩 Level·Topic 화면 스태거 적용

`LevelQuestionContent`([LevelQuestionScreen.kt:68])·`TopicQuestionContent`([TopicQuestionScreen.kt:65]) 는 `Column(verticalScroll)` 이라 직계 자식이 모두 컴포즈된다 → 프로토 `.oc-home-stagger > *` 와 가장 충실하게 자식 순서대로 스태거를 건다.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/level/LevelQuestionScreen.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/topic/TopicQuestionScreen.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/level/LevelQuestionScreenshotTest.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/topic/TopicQuestionScreenshotTest.kt`

**Interfaces:**
- Consumes: `rememberReduceMotion()`, `rememberScreenEntrance`, `staggerReveal`.

- [ ] **Step 1: `reduceMotion` 추가 + 배선(Level)**

`LevelQuestionContent` 에 `reduceMotion: Boolean = false,` 추가, `LevelQuestionScreen` 에서 `reduceMotion = rememberReduceMotion()` 배선, import 3종 추가, `Column(verticalScroll)` 직전 `val entrance = rememberScreenEntrance(reduceMotion)`.

- [ ] **Step 2: Level Column 직계 자식에 `.staggerReveal(childIndex, entrance)` 삽입**

`Column { … }` 안 직계 자식 컴포저블에 위→아래 순서로 index 0,1,2,… 를 부여해 각 자식의 `modifier` 앞에 `.staggerReveal(<childIndex>, entrance)` 를 삽입한다. `modifier` 를 받지 않는 자식은 `Box(modifier = Modifier.staggerReveal(<childIndex>, entrance)) { … }` 로 감싼다. (스텝바/헤더/설명/옵션 리스트/CTA 순.)

- [ ] **Step 3: `reduceMotion` 추가 + 배선 + 스태거(Topic)**

Topic 도 동일: `TopicQuestionContent` 에 `reduceMotion` 추가·배선·import·`entrance` 생성 후, `Column` 직계 자식에 순서 index 로 `.staggerReveal(...)` 삽입.

- [ ] **Step 4: 스크린샷 테스트 `reduceMotion = true` 렌더**

`LevelQuestionScreenshotTest.kt`·`TopicQuestionScreenshotTest.kt` 렌더 호출에 `reduceMotion = true` 명시(골든 불변 계약).

- [ ] **Step 5: 검증**

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL — detekt clean, 온보딩 스크린샷 골든 **재생성 없이** 통과, 전 단위테스트 통과.

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/level/LevelQuestionScreen.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/topic/TopicQuestionScreen.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/level/LevelQuestionScreenshotTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/topic/TopicQuestionScreenshotTest.kt
git commit -m "feat(onboarding): oc-rise stagger on level/topic sections"
```

---

## 검증 노트(플랜 밖 · 참고)

- 실제 부드러움(60/120Hz 프레임 유지)의 최종 확인은 실기기(`scripts/verify-android.sh :app:installDebug` 후 육안)로 한다 — 단위/스크린샷 게이트는 정적 최종 상태만 본다.
- 스태거는 `graphicsLayer`(합성)만 건드려 relayout 이 없다 — 이것이 slide(전 트리 이동) 대비 부드러움의 핵심.
- `windowMs=400`: 초기 보이는 섹션이 첫 레이아웃 패스에서 창을 잡도록 넉넉히 두되, 이후 스크롤 진입 섹션은 창이 닫혀 즉시 표시된다. 실기기에서 상단 섹션 일부가 창을 놓치면 소폭 상향 조정.
- 요약 화면(`SummaryScreen`, verticalScroll)은 이번 스코프에서 제외(사용자 선택: 세 탭 + 온보딩). 후속 확장 시 Task 5 패턴 재사용.
- `docs/ui/01-foundations.md:61` F4 결정 라인을 "전환=컨테이너 빠른 페이드 + 섹션 oc-rise 스태거, exit 하드컷, reduce-motion=정적"으로 back-prop(비-코드 후속).

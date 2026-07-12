# 탭·화면 전환 fade-up 정합 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 탭 전환·화면 전환 시 등장하는 화면 애니메이션을 `prototype/Prototype Flow (standalone).html` 의 `oc-fade-up`(불투명도 0→1 + Y축 8px 상승, 200ms easeOut)과 동일하게 구현한다.

**Architecture:** 전환 스펙을 단일 공유 팩토리(`OceNavTransitions.kt`)로 추출하고, 내부 3탭 `OceNavHost` 의 현행 임시 크로스페이드를 이 팩토리로 교체한 뒤 동일 스펙을 바깥 `AppRoot` NavHost(온보딩·세션 풀스크린 그래프)에도 적용해 전 전환을 균일하게 맞춘다. 진입=fade+8dp 상승, 퇴장=하드 컷(`ExitTransition.None` — 프로토처럼 구 화면 즉시 제거), reduce-motion 시 진입도 정적(`EnterTransition.None`).

**Tech Stack:** Kotlin, Jetpack Compose, Navigation-Compose (`androidx.navigation.compose.NavHost`), `androidx.compose.animation`(fadeIn/slideInVertically), JUnit4.

## Global Constraints

- 모션 값 정본은 `OceMotion` 토큰 — 새 duration/easing 값 하드코딩 금지, `OceTheme.motion` 을 통해서만 소비(`durationBaseMs=200`, `easingOut=cubic-bezier(0.22,1,0.36,1)`). 근거: `android/.../ui/theme/OceMotion.kt:18`.
- reduce-motion 대체는 A7 스펙 "전환→크로스페이드/즉시" 중 **즉시(None)** 채택 — 결정성 테스트 경로(`reduceMotion=true`) 보존. 근거: `docs/ui/06-accessibility-impl.md:106`.
- 이동 거리 `8dp`(= 프로토 `translateY(8px)`), 방향 = 세로 상승(탭 전환도 가로 슬라이드가 아니라 동일 세로 fade-up).
- 퇴장 = `ExitTransition.None` 하드 컷(사용자 확정). exit 에 fadeOut 추가 금지.
- 검증은 반드시 `scripts/verify-android.sh` 로 실행(워크트리 gradle 오염·`google-services.json` 부재 회피). 근거: `CLAUDE.md`, `scripts/verify-android.sh`.
- detekt 통과 필수(미사용 import 제거 포함) — 기본 검증 세트에 `:app:detekt` 포함.

---

### Task 1: 공유 전환 팩토리 `OceNavTransitions`

프로토 `oc-fade-up` 를 Compose EnterTransition 으로 이식하는 순수 팩토리 함수 + 하드컷 퇴장 상수. Compose 무관 순수 로직이라 JVM 단위테스트로 reduce-motion 게이트·하드컷을 반증가능하게 고정한다.

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavTransitions.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavTransitionsTest.kt`

**Interfaces:**
- Consumes: `OceMotion`(`com.jjundev.oneclickeng.ui.theme.OceMotion`) — 필드 `durationBaseMs: Int`, `easingOut: Easing`.
- Produces:
  - `fun oceScreenEnter(motion: OceMotion, offsetY8Px: Int, reduceMotion: Boolean): EnterTransition`
  - `val oceScreenExit: ExitTransition`
  - Task 2·3 이 이 두 심볼을 NavHost 전환 람다에서 호출한다.

- [ ] **Step 1: 실패 테스트 작성**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavTransitionsTest.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import com.jjundev.oneclickeng.ui.theme.OceMotion
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 전환 팩토리 순수 검증(Compose 무관). 실제 프레임(비트맵)은 대상 아니고, reduce-motion 게이트와
 * 퇴장 하드컷 계약만 반증가능하게 고정한다. offsetY8Px 는 density=3 기준 8dp=24px 예시값.
 */
class OceNavTransitionsTest {
    private val motion = OceMotion()

    @Test
    fun reduceMotion_enter_isNone() {
        assertSame(EnterTransition.None, oceScreenEnter(motion, offsetY8Px = 24, reduceMotion = true))
    }

    @Test
    fun normalMotion_enter_isNotNone() {
        assertNotSame(EnterTransition.None, oceScreenEnter(motion, offsetY8Px = 24, reduceMotion = false))
    }

    @Test
    fun exit_isAlwaysHardCut() {
        assertSame(ExitTransition.None, oceScreenExit)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*OceNavTransitionsTest*'`
Expected: FAIL — 컴파일 에러(`unresolved reference: oceScreenEnter` / `oceScreenExit`).

- [ ] **Step 3: 최소 구현 작성**

Create `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavTransitions.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import com.jjundev.oneclickeng.ui.theme.OceMotion

/**
 * 전역 화면/탭 전환 정본(F4 확정). 프로토타입 `oc-fade-up`(opacity 0→1 + translateY 8px→0) 이식:
 * 진입 = fade + 8dp 상승([motion].durationBaseMs / easingOut), 퇴장 = 하드 컷(구 화면 즉시 제거 — 프로토
 * 정합, [oceScreenExit]). [reduceMotion] 시 진입도 정적([EnterTransition.None], A7 "전환→즉시").
 *
 * 내부 3탭([OceNavHost])·바깥 그래프([com.jjundev.oneclickeng.ui.root.AppRoot] NavHost)가 이 동일 스펙을
 * 공유해 전 전환을 균일화한다. 탭 전환도 가로 슬라이드가 아니라 동일 세로 fade-up(프로토 parity 우선).
 *
 * @param offsetY8Px 8dp 를 px 로 환산한 상승 시작 오프셋(호출부에서 `LocalDensity` 로 계산해 주입 — 팩토리는
 *   밀도 비의존). 프로토 `translateY(8px)` 대응.
 */
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

/**
 * 퇴장 전환 = 하드 컷([ExitTransition.None]). 프로토는 명시 exit 없이 구 화면을 즉시 제거하고 새 콘텐츠만
 * fade-up 시킨다 — 이를 정합(reduce-motion 무관 동일). exit 에 fadeOut 추가 금지(Global Constraints).
 */
val oceScreenExit: ExitTransition = ExitTransition.None
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*OceNavTransitionsTest*'`
Expected: PASS (3 tests).

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavTransitions.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavTransitionsTest.kt
git commit -m "feat(nav): add oc-fade-up screen transition factory (F4)"
```

---

### Task 2: 내부 3탭 `OceNavHost` 배선(임시 크로스페이드 교체)

현행 F4 잠정 크로스페이드(`fadeIn`/`fadeOut`)를 Task 1 팩토리로 교체하고 pop 방향까지 대칭 지정한다. 탭 전환이 프로토 fade-up 으로 등장하게 된다.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavHost.kt`

**Interfaces:**
- Consumes: `oceScreenEnter(...)`, `oceScreenExit`(Task 1, 동일 패키지 — import 불필요).
- Produces: 없음(내부 배선). `OceNavHost` 시그니처(`reduceMotion` seam 포함)는 유지.

- [ ] **Step 1: import 정리 — 신규 추가 / 미사용 제거**

`OceNavHost.kt` 상단 import 블록을 아래로 맞춘다. 제거: `EnterTransition`, `ExitTransition`, `tween`, `fadeIn`, `fadeOut`(팩토리로 이동). 추가: `LocalDensity`, `dp`. 유지: `rememberReduceMotion`, `OceTheme`.

제거할 import (있으면 삭제):
```kotlin
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
```

추가할 import:
```kotlin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
```

- [ ] **Step 2: 전환 로컬값·NavHost 파라미터 교체**

함수 본문에서 기존 두 로컬(`val durationMs = OceTheme.motion.durationBaseMs` / `val easing = OceTheme.motion.easingStandard`)과 `NavHost(...)` 의 `enterTransition`/`exitTransition` 람다를 아래로 교체한다.

교체 전(현행):
```kotlin
    val durationMs = OceTheme.motion.durationBaseMs
    val easing = OceTheme.motion.easingStandard
    NavHost(
        navController = navController,
        startDestination = OceTab.Start.route,
        modifier = modifier,
        enterTransition = {
            if (reduceMotion) EnterTransition.None else fadeIn(tween(durationMs, easing = easing))
        },
        exitTransition = {
            if (reduceMotion) ExitTransition.None else fadeOut(tween(durationMs, easing = easing))
        },
    ) {
```

교체 후:
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

`composable(OceTab.Home.route) { ... }` 등 목적지 블록과 `startDestination = OceTab.Start.route` 는 그대로 둔다.

- [ ] **Step 3: KDoc 의 "F4 미결" 문구를 확정 문구로 갱신**

함수 위 KDoc 의 전환 훅 단락을 아래로 교체한다.

교체 전:
```kotlin
 * **전환 훅(F4 미결 · 잠정 기본값):** 교체 가능한 전환 seam 에 잠정 기본값(크로스페이드 200ms)을 주입한다.
 * [reduceMotion] 이 true 면 정적 스냅으로 대체한다(수용기준).
```

교체 후:
```kotlin
 * **전환(F4 확정):** 프로토 `oc-fade-up`(fade + 8dp 상승, 진입만) 정합 — 공유 [oceScreenEnter]/[oceScreenExit]
 * 를 소비한다. 진입=fade+8dp↑, 퇴장=하드 컷([oceScreenExit]). [reduceMotion] 이 true 면 진입도 정적(None).
```

- [ ] **Step 4: 컴파일·detekt·단위테스트 검증**

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL — detekt 통과(미사용 import 0), `:app:compileDebugAndroidTestKotlin` 성공(기존 `AppNavigationTest` 컴파일 유지), 단위테스트 전건 통과(Task 1 포함).

주의: 실제 탭 전환 모션의 행위 게이트는 계측 테스트 `AppNavigationTest`(androidTest, `createComposeRule`)로, 에뮬레이터에서 `connectedDebugAndroidTest` 로만 실행된다 — `verify-android.sh` 는 이를 **컴파일까지만** 확인한다. 기존 테스트는 콘텐츠 assertion + `reduceMotion=true` 주입 경로라 이 변경으로 깨지지 않는다.

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavHost.kt
git commit -m "feat(nav): wire tab NavHost to oc-fade-up transitions (F4)"
```

---

### Task 3: 바깥 `AppRoot` NavHost 배선(온보딩·세션 풀스크린 정합)

바깥 그래프는 현재 전환 미지정(Compose 기본)이다. 동일 공유 팩토리를 적용해 온보딩 단계·홈→세션 풀스크린 진입까지 균일 fade-up 으로 맞춘다(사용자 확정: 세션 진입도 균일 fade-up).

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt`

**Interfaces:**
- Consumes: `oceScreenEnter(...)`, `oceScreenExit`(Task 1 — `com.jjundev.oneclickeng.ui.navigation` 패키지 import), `rememberReduceMotion()`.
- Produces: 없음(내부 배선).

- [ ] **Step 1: import 추가**

`AppRoot.kt` 상단 import 블록에 아래를 추가한다.

```kotlin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.navigation.oceScreenEnter
import com.jjundev.oneclickeng.ui.navigation.oceScreenExit
import com.jjundev.oneclickeng.ui.theme.OceTheme
```

- [ ] **Step 2: 전환값 계산 + outer NavHost 파라미터 지정**

`AppRoot` 컴포저블에서 `val outerNavController = rememberNavController()` 바로 다음에 전환값을 계산하고, `NavHost(...)` 호출에 네 전환 람다를 추가한다.

교체 전:
```kotlin
    val outerNavController = rememberNavController()
    NavHost(navController = outerNavController, startDestination = resolvedStart) {
```

교체 후:
```kotlin
    val outerNavController = rememberNavController()
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

블록 내부(`composable(MAIN_TABS_ROUTE) { ... }`, `onboardingGraph(outerNavController)`, `homeSessionGraph(outerNavController)`)는 그대로 둔다. NavHost 레벨 전환은 중첩 `navigation(){}` 그래프(온보딩·세션)까지 상속되어 균일 fade-up 이 된다.

- [ ] **Step 3: 컴파일·detekt·단위테스트 검증**

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL — detekt 통과, 컴파일 성공, 단위테스트 전건 통과.

- [ ] **Step 4: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt
git commit -m "feat(nav): apply oc-fade-up to outer graph (onboarding/session) — F4"
```

---

## 검증 노트(플랜 밖 · 참고)

- 전체 행위 게이트(실기기 fade-up 육안 + 계측 테스트)는 에뮬레이터 `scripts/verify-android.sh :app:connectedDebugAndroidTest` 로 별도 실행(에뮬레이터 필요). 본 플랜의 자동 게이트는 `verify-android.sh` 기본 세트(detekt + androidTest 컴파일 + 단위테스트)까지다.
- 문서 부채(비-코드, 후속): `docs/ui/01-foundations.md:61` F4 상태를 🟠→✅ 로 back-prop 하고 결정 라인("전역 전환 = oc-fade-up, exit 하드컷, reduce-motion None")을 기록. 본 플랜 범위 밖.
- `ExitTransition.None` 은 전환 200ms 동안 구 화면을 그 아래 유지한다(t=0 즉시 제거 아님). 실기기에서 진입 초반 고스팅이 거슬리면 후속으로 `fadeOut(tween(0))` 0ms 스냅 변형 검토(사용자 확정: 일단 None 유지).

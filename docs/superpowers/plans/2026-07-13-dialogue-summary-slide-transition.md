# 대화학습 → 세션 요약 슬라이드 전환 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 대화학습 세션이 완료되면 즉시 요약으로 넘어가지 않고, 완료 상태를 1초 노출한 뒤 요약 화면이 오른쪽에서 슬라이드로 들어오며(대화 화면은 왼쪽으로 퇴장) 전환한다.

**Architecture:** 두 개의 독립 seam으로 구현한다. (1) **1초 대기** — 자동 요약 이동을 발화하는 공용 컴포저블 `DialogueTurnContent`의 `LaunchedEffect(sessionPhase)` 안에 `delay(1000)`를 넣는다(홈·온보딩 세션 루프가 공유). (2) **슬라이드 전환** — 전역 전환 정본 파일 `OceNavTransitions.kt`에 순수 함수(reduce-motion 게이트 + 목적지 route 게이트)로 슬라이드 토큰을 추가하고, 두 세션 그래프(`HomeSessionGraph`·`OnboardingGraph`)의 `session→summary` 엣지에만 per-`composable` `enterTransition`/`exitTransition`으로 배선한다. 컨테이너 기본 전환(`oceScreenEnter`/`oceScreenExit` = `None`)은 그대로 두고, 이 한 엣지만 예외로 슬라이드를 얹는다.

**Tech Stack:** Kotlin, Jetpack Compose, androidx.navigation.compose(단일 outer NavHost + nested `navigation` 그래프), kotlinx.coroutines(`delay`), Robolectric + Compose UI test(`createComposeRule`, `mainClock`), JUnit4.

## Global Constraints

- **모듈:** 모든 코드는 `android/` Gradle 프로젝트, 패키지 루트 `com.jjundev.oneclickeng`. 소스 = `android/app/src/main/kotlin/...`, 테스트 = `android/app/src/test/kotlin/...`.
- **검증 명령(정본):** 워크트리에서 gradle은 반드시 `scripts/verify-android.sh`로 돌린다(공유 `~/.gradle` 오염·`google-services.json` 부재 회피). 기본 세트 = `:app:detekt :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:testReleaseUnitTest`. 단일 테스트 = `scripts/verify-android.sh :app:testDebugUnitTest --tests '*Xxx*'`.
- **ktlint 제외:** 기본 검증 세트는 `ktlintMainSourceSetCheck`를 **제외**한다(master 선존재 import 정렬 위반으로 항상 실패). 따라서 import 정렬은 빌드를 깨지 않지만, 그래도 관례(android → androidx → com.jjundev → kotlin → kotlinx)에 맞춰 그룹핑한다. detekt는 import 정렬을 게이트하지 않는다.
- **reduce-motion 게이트(정본):** 시스템 reduce-motion은 `rememberReduceMotion(): Boolean`(`ui/foundation/ReduceMotion.kt`, `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`)으로 읽는다. **스냅샷 시맨틱**(최초 컴포지션 1회 읽기) — 소비처는 이 값을 **파라미터 seam**으로 받는다. reduce-motion이면 전환은 `EnterTransition.None`/`ExitTransition.None`(무전환).
- **컨테이너 기본 전환 불변:** `oceScreenEnter`/`oceScreenExit`(둘 다 `None`)는 **변경 금지** — 프로토 정합상 컨테이너는 즉시 교체(무전환)라 잔상이 없어야 한다. 슬라이드는 `session→summary` 목적지 엣지에만 per-`composable` 오버라이드로 얹는다. `exit`에 `fadeOut` 추가 금지.
- **슬라이드 방향(확정):** 요약이 **오른쪽에서** 진입(`+fullWidth → 0`), 대화 화면은 **왼쪽으로** 퇴장(`0 → -fullWidth`). 표준 "다음 화면" 푸시(화면 전체가 왼쪽으로 이동).
- **모션 값:** 슬라이드 길이 `340ms`, easing `OceMotionTokens.easingOut`(= `CubicBezierEasing(0.22f, 1f, 0.36f, 1f)`, `ui/theme/OceMotion.kt`의 `internal val OceMotionTokens`; 같은 모듈이라 `ui.navigation`에서 접근 가능). 대기 `1000ms`.

---

## File Structure

- **`feature/session/turn/DialogueTurnScreen.kt`** (수정) — 공용 대화 턴 콘텐츠. 세션 완료 자동 요약 이동 트리거(`LaunchedEffect`)에 1초 대기를 넣고, 대기 상수를 파일 스코프에 선언한다. 홈·온보딩 두 세션 루프가 이 컴포저블을 공유하므로 1초 대기는 두 흐름에 동시에 적용된다(의도).
- **`ui/navigation/OceNavTransitions.kt`** (수정) — 전역 전환 정본. 슬라이드 진입/퇴장 기본 팩토리(reduce-motion 게이트)와 목적지 route 게이트를 씌운 엣지 함수(순수 함수 → 단위 테스트 가능)를 추가한다. 컨테이너 기본 토큰(`oceScreenEnter`/`oceScreenExit`)은 손대지 않는다.
- **`feature/home/HomeSessionGraph.kt`** (수정) — 홈 세션 그래프. `homeSessionGraph`/`sessionDestination`/`summaryDestination`에 `reduceMotion` 파라미터를 통과시키고, `session→summary` 엣지에 `enterTransition`/`exitTransition`을 배선한다.
- **`feature/onboarding/OnboardingGraph.kt`** (수정) — 온보딩 세션 그래프. 홈과 동일 패턴으로 `reduceMotion`을 통과시키고 `session→summary` 엣지에 배선한다. **주의:** 온보딩 session의 `onExit`은 `navigate(MAIN_TABS)`(전진)라 무가드 `exitTransition`은 홈-이동에도 발화 → **반드시 targetState route 게이트**로 요약 엣지에만 슬라이드가 걸리게 한다.
- **`ui/root/AppRoot.kt`** (수정) — outer NavHost. `rememberReduceMotion()`을 1회 읽어 `homeSessionGraph`·`onboardingGraph` 두 빌더에 넘긴다.
- **`feature/session/turn/SummaryHandoffDelayTest.kt`** (신규 테스트) — 1초 대기 검증(Robolectric + `mainClock`).
- **`ui/navigation/OceNavTransitionsTest.kt`** (수정 테스트) — 슬라이드 토큰의 reduce-motion 게이트 + route 게이트 검증.

**결정 체크포인트:** 슬라이드 방향은 사용자가 "요약이 오른쪽에서 진입(화면이 왼쪽으로 이동)"으로 확정함 → Global Constraints에 고정. 다른 실행 포크 없음.

---

### Task 1: 대화학습 완료 → 요약 이동 전 1초 대기

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt` (import 추가 + 파일 스코프 상수 추가 + `LaunchedEffect` 본문 수정, 현재 156–158)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SummaryHandoffDelayTest.kt` (신규)

**Interfaces:**
- Consumes: `SessionPhase.Completed`(enum, `DialogueUiState.kt`), `DialogueTurnContent(...)` 시그니처(기존), `onViewSummary: () -> Unit`(기존 파라미터).
- Produces: `internal const val SUMMARY_HANDOFF_DELAY_MS = 1_000L` (파일 스코프, `feature.session.turn` 패키지). Task 3의 코드는 이 상수에 의존하지 않으나 테스트가 참조한다.

- [ ] **Step 1: 실패하는 테스트 작성**

신규 파일 `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SummaryHandoffDelayTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.turn

import android.app.Application
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 세션 완료 → 요약 자동 이동은 완료 상태를 [SUMMARY_HANDOFF_DELAY_MS] 노출한 뒤에만 발화한다(1초 대기).
 * 반증가능: 대기 경과 직전엔 콜백 0회, 경과 시점에 정확히 1회.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class SummaryHandoffDelayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `completed handoff fires only after the delay`() {
        composeRule.mainClock.autoAdvance = false
        var summaryCount = 0
        composeRule.setContent {
            OceTheme {
                Surface {
                    DialogueTurnContent(
                        messages = emptyList(),
                        turnPhase = TurnPhase.OpponentTurn,
                        sessionPhase = SessionPhase.Completed,
                        currentTask = null,
                        listState = rememberLazyListState(),
                        onSubmitStub = {},
                        onViewSummary = { summaryCount += 1 },
                    )
                }
            }
        }

        val base = composeRule.mainClock.currentTime
        advanceTo(base + SUMMARY_HANDOFF_DELAY_MS - 1)
        composeRule.runOnIdle { assertEquals(0, summaryCount) }

        advanceTo(base + SUMMARY_HANDOFF_DELAY_MS)
        composeRule.runOnIdle { assertEquals(1, summaryCount) }
    }

    private fun advanceTo(targetTimeMs: Long) {
        composeRule.mainClock.advanceTimeBy(
            targetTimeMs - composeRule.mainClock.currentTime,
            ignoreFrameDuration = true,
        )
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SummaryHandoffDelayTest*'`
Expected: FAIL — `SUMMARY_HANDOFF_DELAY_MS` 미해결 컴파일 에러(unresolved reference). (대기가 아직 없어 즉시 발화하므로, 컴파일이 됐다면 `assertEquals(0, summaryCount)`에서 실패했을 것.)

- [ ] **Step 3: 상수 + import 추가**

`DialogueTurnScreen.kt` import 블록에 다음 한 줄을 추가한다(kotlinx 그룹):

```kotlin
import kotlinx.coroutines.delay
```

이어서 `DialogueTurnContent` 컴포저블 선언 바로 위에 파일 스코프 상수를 추가한다. 앵커(현재 파일에 존재):

```kotlin
@Composable
internal fun DialogueTurnContent(
```

를 다음으로 교체:

```kotlin
/** 대화학습 완료 → 세션 요약 자동 이동 전, 완료 상태를 노출하는 대기(ms). 이후 nav 그래프가 오른쪽 슬라이드로 넘긴다. */
internal const val SUMMARY_HANDOFF_DELAY_MS = 1_000L

@Composable
internal fun DialogueTurnContent(
```

- [ ] **Step 4: `LaunchedEffect`에 대기 삽입**

앵커(현재 156–158):

```kotlin
    LaunchedEffect(sessionPhase) {
        if (sessionPhase == SessionPhase.Completed) onViewSummary()
    }
```

를 다음으로 교체:

```kotlin
    LaunchedEffect(sessionPhase) {
        if (sessionPhase == SessionPhase.Completed) {
            // 완료 화면 없이 곧장 요약으로 가되, 완료 상태를 1초 노출한 뒤 넘어간다(요구). 컨테이너 전환(요약이
            // 오른쪽에서 슬라이드)은 nav 그래프가 담당한다. sessionPhase 전이는 단방향(Completed 유지)이라
            // 이 delay 는 정확히 한 번만 걸리고, 대기 중 화면 이탈 시 코루틴이 취소된다.
            delay(SUMMARY_HANDOFF_DELAY_MS)
            onViewSummary()
        }
    }
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SummaryHandoffDelayTest*'`
Expected: PASS (1 test).

- [ ] **Step 6: 회귀 확인 — 기존 대화 턴 테스트**

기존 스크린샷/상태 테스트는 `sessionPhase = InTurn`만 쓰므로 `LaunchedEffect`가 발화하지 않아 영향 없음을 확인한다.

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*DialogueTurnScreenshotTest*' --tests '*GeneratedDialogueStateTest*'`
Expected: PASS (변경 없음).

- [ ] **Step 7: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SummaryHandoffDelayTest.kt
git commit -m "feat(session): wait 1s before auto-navigating dialogue completion to summary"
```

---

### Task 2: 슬라이드 전환 토큰(reduce-motion + route 게이트)

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavTransitions.kt` (import + 함수 4개 추가; 기존 `oceScreenEnter`/`oceScreenExit`는 불변)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavTransitionsTest.kt` (기존 파일에 테스트 추가)

**Interfaces:**
- Consumes: `OceMotionTokens.easingOut`(`ui/theme/OceMotion.kt`, `internal val`), `EnterTransition`/`ExitTransition`, `slideInHorizontally`/`slideOutHorizontally`, `tween`.
- Produces (Task 3이 소비):
  - `fun summaryHandoffEnter(reduceMotion: Boolean): EnterTransition` — 요약 진입 슬라이드(오른쪽에서). reduce-motion → `EnterTransition.None`.
  - `fun sessionHandoffExit(reduceMotion: Boolean): ExitTransition` — 대화 퇴장 슬라이드(왼쪽으로). reduce-motion → `ExitTransition.None`.
  - `fun summaryEnterFor(sourceRoute: String?, sessionRoute: String, reduceMotion: Boolean): EnterTransition` — 진입 소스가 `sessionRoute`일 때만 `summaryHandoffEnter`, 아니면 `EnterTransition.None`.
  - `fun sessionExitFor(targetRoute: String?, summaryRoute: String, reduceMotion: Boolean): ExitTransition` — 퇴장 타깃이 `summaryRoute`일 때만 `sessionHandoffExit`, 아니면 `ExitTransition.None`.

- [ ] **Step 1: 실패하는 테스트 작성**

`OceNavTransitionsTest.kt`에 아래 내용을 추가한다. 먼저 import를 보강한다(파일 상단 기존 import 옆):

```kotlin
import org.junit.Assert.assertNotSame
```

그리고 클래스(`class OceNavTransitionsTest {`) 안, 기존 두 테스트 아래에 다음 테스트들을 추가한다:

```kotlin
    // --- 세션 요약 핸드오프 슬라이드 토큰 ---

    private val sessionRoute = "home/session?level={level}"
    private val summaryRoute = "home/summary?sessionId={sessionId}&level={level}"

    @Test
    fun summaryHandoffEnter_reduceMotion_isNoTransition() {
        assertSame(EnterTransition.None, summaryHandoffEnter(reduceMotion = true))
    }

    @Test
    fun summaryHandoffEnter_motionOn_slides() {
        assertNotSame(EnterTransition.None, summaryHandoffEnter(reduceMotion = false))
    }

    @Test
    fun sessionHandoffExit_reduceMotion_isHardCut() {
        assertSame(ExitTransition.None, sessionHandoffExit(reduceMotion = true))
    }

    @Test
    fun sessionHandoffExit_motionOn_slides() {
        assertNotSame(ExitTransition.None, sessionHandoffExit(reduceMotion = false))
    }

    @Test
    fun summaryEnterFor_fromSession_motionOn_slides() {
        assertNotSame(
            EnterTransition.None,
            summaryEnterFor(sourceRoute = sessionRoute, sessionRoute = sessionRoute, reduceMotion = false),
        )
    }

    @Test
    fun summaryEnterFor_fromOtherRoute_isNoTransition() {
        assertSame(
            EnterTransition.None,
            summaryEnterFor(sourceRoute = "other", sessionRoute = sessionRoute, reduceMotion = false),
        )
    }

    @Test
    fun summaryEnterFor_nullSource_isNoTransition() {
        assertSame(
            EnterTransition.None,
            summaryEnterFor(sourceRoute = null, sessionRoute = sessionRoute, reduceMotion = false),
        )
    }

    @Test
    fun sessionExitFor_toSummary_motionOn_slides() {
        assertNotSame(
            ExitTransition.None,
            sessionExitFor(targetRoute = summaryRoute, summaryRoute = summaryRoute, reduceMotion = false),
        )
    }

    @Test
    fun sessionExitFor_toOtherRoute_isHardCut() {
        assertSame(
            ExitTransition.None,
            sessionExitFor(targetRoute = "main_tabs", summaryRoute = summaryRoute, reduceMotion = false),
        )
    }

    @Test
    fun sessionExitFor_toSummary_reduceMotion_isHardCut() {
        assertSame(
            ExitTransition.None,
            sessionExitFor(targetRoute = summaryRoute, summaryRoute = summaryRoute, reduceMotion = true),
        )
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*OceNavTransitionsTest*'`
Expected: FAIL — `summaryHandoffEnter`/`sessionHandoffExit`/`summaryEnterFor`/`sessionExitFor` unresolved reference(컴파일 실패).

- [ ] **Step 3: 슬라이드 토큰 함수 구현**

`OceNavTransitions.kt` import 블록에 추가:

```kotlin
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.jjundev.oneclickeng.ui.theme.OceMotionTokens
```

파일 맨 끝(`oceScreenExit` 정의 아래)에 다음을 추가:

```kotlin

/** 세션 요약 핸드오프 슬라이드 길이(ms). 전체 화면 전환이라 입력 독 슬라이드업(340ms)과 동일 박자. */
private const val SUMMARY_HANDOFF_SLIDE_MS = 340

/**
 * 대화학습 종료 → 세션 요약 진입 전환: 요약이 **오른쪽에서** 밀려 들어온다(`+fullWidth → 0`). reduce-motion 이면
 * 무전환([EnterTransition.None]) — 컨테이너 기본([oceScreenEnter])과 동일. 컨테이너 기본은 건드리지 않고 이 목적지
 * 엣지에만 얹는 예외다(Global Constraints).
 */
fun summaryHandoffEnter(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) {
        EnterTransition.None
    } else {
        slideInHorizontally(
            animationSpec = tween(SUMMARY_HANDOFF_SLIDE_MS, easing = OceMotionTokens.easingOut),
        ) { fullWidth -> fullWidth }
    }

/**
 * 대화학습 화면의 요약 핸드오프 퇴장: **왼쪽으로** 밀려 나간다(`0 → -fullWidth`, 요약이 오른쪽에서 들어오는 것과 짝).
 * reduce-motion 이면 하드 컷([ExitTransition.None]).
 */
fun sessionHandoffExit(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) {
        ExitTransition.None
    } else {
        slideOutHorizontally(
            animationSpec = tween(SUMMARY_HANDOFF_SLIDE_MS, easing = OceMotionTokens.easingOut),
        ) { fullWidth -> -fullWidth }
    }

/**
 * 요약 목적지의 진입 전환을 **session→summary 엣지로 한정**한다. 진입 소스([sourceRoute])가 [sessionRoute]일 때만
 * 슬라이드([summaryHandoffEnter]), 그 외/`null` 진입은 무전환. (현재 요약 진입 엣지는 하나뿐이나, 미래에 다른 진입
 * 경로가 생겨도 슬라이드가 새지 않도록 명시 게이트.)
 */
fun summaryEnterFor(sourceRoute: String?, sessionRoute: String, reduceMotion: Boolean): EnterTransition =
    if (sourceRoute == sessionRoute) summaryHandoffEnter(reduceMotion) else EnterTransition.None

/**
 * 대화 목적지의 퇴장 전환을 **session→summary 엣지로 한정**한다. 퇴장 타깃([targetRoute])이 [summaryRoute]일 때만
 * 슬라이드([sessionHandoffExit]), 그 외(예: 온보딩의 홈-이동 `navigate(MAIN_TABS)`)는 무전환. 온보딩 session 은
 * 요약 외에도 전진 내비게이션이 있어 이 게이트가 필수다.
 */
fun sessionExitFor(targetRoute: String?, summaryRoute: String, reduceMotion: Boolean): ExitTransition =
    if (targetRoute == summaryRoute) sessionHandoffExit(reduceMotion) else ExitTransition.None
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*OceNavTransitionsTest*'`
Expected: PASS (기존 2 + 신규 10 = 12 tests).

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavTransitions.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavTransitionsTest.kt
git commit -m "feat(nav): add session->summary slide transition tokens with reduce-motion and edge gates"
```

---

### Task 3: 두 세션 그래프에 슬라이드 배선 + reduce-motion 통과

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt` (`rememberReduceMotion()` 1회 읽기 + 두 그래프 빌더에 전달)
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeSessionGraph.kt` (`reduceMotion` 통과 + `session`/`summary` 목적지에 전환 배선)
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/OnboardingGraph.kt` (동일 패턴)

**Interfaces:**
- Consumes: Task 2의 `summaryEnterFor(sourceRoute, sessionRoute, reduceMotion)`, `sessionExitFor(targetRoute, summaryRoute, reduceMotion)`; `rememberReduceMotion(): Boolean`(`ui/foundation/ReduceMotion.kt`); route 상수 `HOME_SESSION_ROUTE`/`HOME_SUMMARY_ROUTE`(HomeSessionGraph.kt `private const`), `ONBOARDING_SESSION_ROUTE`/`ONBOARDING_SUMMARY_ROUTE`(OnboardingRoutes.kt `internal const`, 동일 패키지).
- Produces: 시그니처 변경 — `fun NavGraphBuilder.homeSessionGraph(navController: NavHostController, reduceMotion: Boolean)`, `fun NavGraphBuilder.onboardingGraph(navController: NavHostController, reduceMotion: Boolean)`. (호출부는 AppRoot 하나뿐 — 이 Task에서 함께 수정.)
- **테스트 참고:** 슬라이드 전환의 애니메이션 자체는 Hilt VM 의존(`GeneratedDialogueSessionRoute`/`SummaryRoute`가 `hiltViewModel`)으로 값싼 단위/스크린샷 테스트가 불가능하다. 전환 결정 로직은 Task 2에서 순수 함수로 완전히 단위 테스트했으므로, 이 Task의 자동 검증은 **컴파일·detekt·전체 단위 테스트 회귀 통과**(`scripts/verify-android.sh` 기본 세트)로 한정하고, 실제 슬라이드는 에뮬레이터 수동 검증(Step 6)으로 확인한다.

- [ ] **Step 1: 홈 그래프 배선**

`HomeSessionGraph.kt` import 블록에 추가:

```kotlin
import com.jjundev.oneclickeng.ui.navigation.sessionExitFor
import com.jjundev.oneclickeng.ui.navigation.summaryEnterFor
```

`homeSessionGraph` 시그니처와 목적지 호출을 `reduceMotion`을 받도록 교체. 앵커(현재 41–47):

```kotlin
fun NavGraphBuilder.homeSessionGraph(navController: NavHostController) {
    navigation(startDestination = HOME_GENERATING_ROUTE, route = HOME_SESSION_GRAPH_ROUTE) {
        generatingDestination(navController)
        sessionDestination(navController)
        summaryDestination(navController)
    }
}
```

를 다음으로 교체:

```kotlin
fun NavGraphBuilder.homeSessionGraph(navController: NavHostController, reduceMotion: Boolean) {
    navigation(startDestination = HOME_GENERATING_ROUTE, route = HOME_SESSION_GRAPH_ROUTE) {
        generatingDestination(navController)
        sessionDestination(navController, reduceMotion)
        summaryDestination(navController, reduceMotion)
    }
}
```

- [ ] **Step 2: 홈 session/summary 목적지에 전환 배선**

`sessionDestination` 선언 + `composable(...)` 헤더를 교체. 앵커(현재 113–123):

```kotlin
private fun NavGraphBuilder.sessionDestination(navController: NavHostController) {
    composable(
        route = HOME_SESSION_ROUTE,
        arguments =
            listOf(
                navArgument(H_ARG_LEVEL) { type = NavType.StringType; defaultValue = "" },
                navArgument(H_ARG_LENGTH) { type = NavType.IntType; defaultValue = DEFAULT_LENGTH },
                navArgument(H_ARG_TOPIC_LABEL) { type = NavType.StringType; defaultValue = "" },
                navArgument(H_ARG_TOPIC_EMOJI) { type = NavType.StringType; defaultValue = "" },
            ),
    ) { entry ->
```

를 다음으로 교체(파라미터 추가 + `exitTransition` 추가):

```kotlin
private fun NavGraphBuilder.sessionDestination(navController: NavHostController, reduceMotion: Boolean) {
    composable(
        route = HOME_SESSION_ROUTE,
        arguments =
            listOf(
                navArgument(H_ARG_LEVEL) { type = NavType.StringType; defaultValue = "" },
                navArgument(H_ARG_LENGTH) { type = NavType.IntType; defaultValue = DEFAULT_LENGTH },
                navArgument(H_ARG_TOPIC_LABEL) { type = NavType.StringType; defaultValue = "" },
                navArgument(H_ARG_TOPIC_EMOJI) { type = NavType.StringType; defaultValue = "" },
            ),
        // 대화 → 요약 핸드오프에서만 왼쪽으로 슬라이드 퇴장(요약 나가기 pop 은 popExitTransition=기본 유지).
        exitTransition = { sessionExitFor(targetState.destination.route, HOME_SUMMARY_ROUTE, reduceMotion) },
    ) { entry ->
```

이어서 `summaryDestination` 선언 + `composable(...)` 헤더를 교체. 앵커(현재 147–155):

```kotlin
private fun NavGraphBuilder.summaryDestination(navController: NavHostController) {
    composable(
        route = HOME_SUMMARY_ROUTE,
        arguments =
            listOf(
                navArgument(H_ARG_SESSION_ID) { type = NavType.StringType; defaultValue = "" },
                navArgument(H_ARG_LEVEL) { type = NavType.StringType; defaultValue = DISPLAY_DIFFICULTY_DEFAULT },
            ),
    ) { entry ->
```

를 다음으로 교체(파라미터 추가 + `enterTransition` 추가):

```kotlin
private fun NavGraphBuilder.summaryDestination(navController: NavHostController, reduceMotion: Boolean) {
    composable(
        route = HOME_SUMMARY_ROUTE,
        arguments =
            listOf(
                navArgument(H_ARG_SESSION_ID) { type = NavType.StringType; defaultValue = "" },
                navArgument(H_ARG_LEVEL) { type = NavType.StringType; defaultValue = DISPLAY_DIFFICULTY_DEFAULT },
            ),
        // 대화(HOME_SESSION_ROUTE)에서 진입할 때만 오른쪽에서 슬라이드 진입.
        enterTransition = { summaryEnterFor(initialState.destination.route, HOME_SESSION_ROUTE, reduceMotion) },
    ) { entry ->
```

- [ ] **Step 3: 온보딩 그래프 배선**

`OnboardingGraph.kt` import 블록에 추가:

```kotlin
import com.jjundev.oneclickeng.ui.navigation.sessionExitFor
import com.jjundev.oneclickeng.ui.navigation.summaryEnterFor
```

`onboardingGraph` 시그니처와 목적지 호출을 교체. 앵커(현재 44–52):

```kotlin
fun NavGraphBuilder.onboardingGraph(navController: NavHostController) {
    navigation(startDestination = ONBOARDING_LEVEL_ROUTE, route = ONBOARDING_ROUTE) {
        levelDestination(navController)
        topicDestination(navController)
        generatingDestination(navController)
        sessionDestination(navController)
        summaryDestination(navController)
    }
}
```

를 다음으로 교체:

```kotlin
fun NavGraphBuilder.onboardingGraph(navController: NavHostController, reduceMotion: Boolean) {
    navigation(startDestination = ONBOARDING_LEVEL_ROUTE, route = ONBOARDING_ROUTE) {
        levelDestination(navController)
        topicDestination(navController)
        generatingDestination(navController)
        sessionDestination(navController, reduceMotion)
        summaryDestination(navController, reduceMotion)
    }
}
```

- [ ] **Step 4: 온보딩 session/summary 목적지에 전환 배선**

`sessionDestination` 선언 + `composable(...)` 헤더를 교체. 앵커(현재 149–160):

```kotlin
private fun NavGraphBuilder.sessionDestination(navController: NavHostController) {
    composable(
        route = ONBOARDING_SESSION_ROUTE,
        arguments =
            listOf(
                navArgument(ARG_LEVEL) { type = NavType.StringType; defaultValue = FIRST_SESSION_LEVEL },
                navArgument(ARG_FIRST) { type = NavType.BoolType; defaultValue = true },
                navArgument(ARG_LENGTH) { type = NavType.IntType; defaultValue = FIRST_SESSION_LENGTH },
                navArgument(ARG_TOPIC_LABEL) { type = NavType.StringType; defaultValue = "" },
                navArgument(ARG_TOPIC_EMOJI) { type = NavType.StringType; defaultValue = "" },
            ),
    ) { entry ->
```

를 다음으로 교체(파라미터 추가 + `exitTransition` 추가 — 온보딩 session 은 요약 외 홈-이동 `navigate` 도 있으므로 route 게이트가 필수):

```kotlin
private fun NavGraphBuilder.sessionDestination(navController: NavHostController, reduceMotion: Boolean) {
    composable(
        route = ONBOARDING_SESSION_ROUTE,
        arguments =
            listOf(
                navArgument(ARG_LEVEL) { type = NavType.StringType; defaultValue = FIRST_SESSION_LEVEL },
                navArgument(ARG_FIRST) { type = NavType.BoolType; defaultValue = true },
                navArgument(ARG_LENGTH) { type = NavType.IntType; defaultValue = FIRST_SESSION_LENGTH },
                navArgument(ARG_TOPIC_LABEL) { type = NavType.StringType; defaultValue = "" },
                navArgument(ARG_TOPIC_EMOJI) { type = NavType.StringType; defaultValue = "" },
            ),
        // 요약 핸드오프에서만 왼쪽 슬라이드 퇴장. 대화 나가기(홈-이동 navigate(MAIN_TABS))는 무전환으로 남긴다.
        exitTransition = { sessionExitFor(targetState.destination.route, ONBOARDING_SUMMARY_ROUTE, reduceMotion) },
    ) { entry ->
```

이어서 `summaryDestination` 선언 + `composable(...)` 전체 헤더를 교체. 앵커(현재 188–197):

```kotlin
private fun NavGraphBuilder.summaryDestination(navController: NavHostController) {
    composable(
        route = ONBOARDING_SUMMARY_ROUTE,
        arguments =
            listOf(
                navArgument(ARG_SESSION_ID) { type = NavType.StringType; defaultValue = "" },
                navArgument(ARG_LEVEL) { type = NavType.StringType; defaultValue = FIRST_SESSION_LEVEL },
                navArgument(ARG_FIRST) { type = NavType.BoolType; defaultValue = true },
            ),
    ) { entry ->
```

를 다음으로 교체(파라미터 추가 + `enterTransition` 추가):

```kotlin
private fun NavGraphBuilder.summaryDestination(navController: NavHostController, reduceMotion: Boolean) {
    composable(
        route = ONBOARDING_SUMMARY_ROUTE,
        arguments =
            listOf(
                navArgument(ARG_SESSION_ID) { type = NavType.StringType; defaultValue = "" },
                navArgument(ARG_LEVEL) { type = NavType.StringType; defaultValue = FIRST_SESSION_LEVEL },
                navArgument(ARG_FIRST) { type = NavType.BoolType; defaultValue = true },
            ),
        // 대화(ONBOARDING_SESSION_ROUTE)에서 진입할 때만 오른쪽에서 슬라이드 진입.
        enterTransition = { summaryEnterFor(initialState.destination.route, ONBOARDING_SESSION_ROUTE, reduceMotion) },
    ) { entry ->
```

- [ ] **Step 5: AppRoot에서 reduce-motion 읽어 두 그래프에 전달**

`AppRoot.kt` import 블록에 추가:

```kotlin
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
```

`AppRoot` 컴포저블에서 `outerNavController` 생성 직후 reduce-motion을 1회 읽는다. 앵커(현재 87):

```kotlin
    val outerNavController = rememberNavController()
    NavHost(
```

를 다음으로 교체:

```kotlin
    val outerNavController = rememberNavController()
    // reduce-motion 스냅샷(1회) — 세션→요약 슬라이드 전환을 그래프 빌더에 파라미터 seam 으로 전달한다.
    val reduceMotion = rememberReduceMotion()
    NavHost(
```

이어서 두 그래프 호출을 교체. 앵커(현재 117–120):

```kotlin
        // 온보딩 그래프(M3-02): 3탭 밖 풀스크린 형제.
        onboardingGraph(outerNavController)
        // 홈 주도 세션 그래프(M3-08): 3탭 밖 풀스크린 형제(주제→생성→대화→요약).
        homeSessionGraph(outerNavController)
```

를 다음으로 교체:

```kotlin
        // 온보딩 그래프(M3-02): 3탭 밖 풀스크린 형제.
        onboardingGraph(outerNavController, reduceMotion)
        // 홈 주도 세션 그래프(M3-08): 3탭 밖 풀스크린 형제(주제→생성→대화→요약).
        homeSessionGraph(outerNavController, reduceMotion)
```

- [ ] **Step 6: 전체 검증(회귀 + 컴파일 + detekt)**

Run: `scripts/verify-android.sh`
Expected: PASS — `:app:detekt`, `:app:compileDebugAndroidTestKotlin`, `:app:testDebugUnitTest`, `:app:testReleaseUnitTest` 모두 성공(Task 1·2 신규 테스트 포함, 그래프 시그니처 변경으로 인한 컴파일 회귀 없음).

수동 확인(에뮬레이터, 애니메이션은 자동 검증 대상 아님): 홈에서 세션을 시작해 대화를 끝까지 진행 → 완료 시 (a) 약 1초간 완료 대화 화면이 유지되고, (b) 요약 화면이 **오른쪽에서** 슬라이드로 들어오며 대화 화면이 왼쪽으로 빠지는지 확인한다. 시스템 "애니메이션 제거"(개발자 옵션 애니메이션 배율 0 또는 접근성)에서는 1초 대기 후 **무전환**으로 즉시 교체되는지 확인한다.

- [ ] **Step 7: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeSessionGraph.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/OnboardingGraph.kt
git commit -m "feat(nav): slide summary in from the right on dialogue->summary handoff (home + onboarding)"
```

---

## 실행 순서 & 의존성

1. **Task 1**(1초 대기) — 독립. 먼저 진행.
2. **Task 2**(슬라이드 토큰) — 독립. Task 1과 병렬 가능.
3. **Task 3**(그래프 배선) — Task 2의 `summaryEnterFor`/`sessionExitFor` 시그니처에 의존. 반드시 Task 2 이후.

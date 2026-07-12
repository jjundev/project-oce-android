# 애니메이션 핫픽스(스태거 속도·탭 잔상·기록 카운트업) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 세 가지 핫픽스 — (1) 순차 스태거를 더 스낵하게(빠르게), (2) 탭 전환 시 구 화면이 비쳐 보이는 잔상 제거, (3) 기록 탭 슬롯머신 카운트업을 debug 빌드에서 눈으로 검증 가능하게 시드.

**Architecture:** (1) `ScreenEntrance.kt` 스태거 상수만 낮춘다. (2) 컨테이너 전환의 fadeIn(구 화면 위에 겹쳐 페이드 = 잔상)을 제거하고 즉시 교체(`EnterTransition.None`)로 바꾼다 — 등장감은 섹션 스태거가 전담. (3) `StubLifetimeStatsSource` 를 `BuildConfig.DEBUG` 게이트로 샘플 통계를 반환하게 해 debug 앱에서 카운트업이 실제로 굴러가게 한다(release 는 스텁 0 유지).

**Tech Stack:** Kotlin, Jetpack Compose, Navigation-Compose, Hilt, JUnit4.

## Global Constraints

- **스태거 속도(확정값):** `STAGGER_DURATION_MS 620→380`, `STAGGER_STEP_MS 110→60`, `STAGGER_BASE_DELAY_MS 40→20`, `rememberScreenEntrance windowMs 기본 400→300`. `STAGGER_MAX_INDEX`(11) 유지 → 지연 캡 = 20+60×11 = 680ms.
- **탭 잔상 제거:** 컨테이너 전환은 **enter=`EnterTransition.None`·exit=`ExitTransition.None`(즉시 교체)**. fadeIn/slide/crossfade 금지. 화면 등장감은 섹션 stagger(oc-rise)가 전담.
- **기록 카운트업 시드:** debug 빌드에서만 `StubLifetimeStatsSource.lifetime()` 이 샘플(`xp=1240, studyMinutes=135, studyDays=12`) 반환, release 는 `null`(스텁 0) 유지. 게이트 = `BuildConfig.DEBUG` (buildConfig 빌드피처 활성화 필요). 실데이터 배선(M3-05)과 무관한 개발 확인용.
- **골든 불변:** 스태거는 `reduceMotion=true`에서 no-op이므로 스크린샷 테스트 골든은 이 핫픽스로도 재생성 없이 통과해야 한다(속도 상수만 바뀜, 정적 최종 상태 동일).
- 검증은 반드시 `scripts/verify-android.sh`(워크트리 gradle 격리·google-services 복사). detekt 통과(미사용 import/param 제거).

---

### Task 1: 스태거 속도 스낵하게 (상수 하향)

프로토 실측값(620/110/40)이 실기기에서 너무 느려, 확정 속도값으로 낮춘다. 순수 지연 계산 테스트를 새 시퀀스로 갱신한다.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/ScreenEntrance.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/ScreenEntranceTest.kt`

**Interfaces:**
- Produces (값 변경만, 시그니처 불변): `staggerDelayMs(index)` = `20 + 60*clamp(index,0,11)`; `rememberScreenEntrance(reduceMotion, windowMs=300)`.

- [ ] **Step 1: 테스트를 새 시퀀스로 갱신(먼저 실패시키기)**

Edit `ScreenEntranceTest.kt` — 기대 지연을 새 값(base 20, step 60)으로 바꾼다.

교체 전:
```kotlin
    @Test
    fun delays_match_prototype_sequence() {
        assertEquals(40, staggerDelayMs(0))
        assertEquals(150, staggerDelayMs(1))
        assertEquals(260, staggerDelayMs(2))
        assertEquals(370, staggerDelayMs(3))
        assertEquals(1250, staggerDelayMs(11))
    }
```
교체 후:
```kotlin
    @Test
    fun delays_match_snappy_sequence() {
        assertEquals(20, staggerDelayMs(0))
        assertEquals(80, staggerDelayMs(1))
        assertEquals(140, staggerDelayMs(2))
        assertEquals(200, staggerDelayMs(3))
        assertEquals(680, staggerDelayMs(11))
    }
```

교체 전:
```kotlin
    @Test
    fun negative_index_clamps_to_base() {
        assertEquals(40, staggerDelayMs(-3))
    }
```
교체 후:
```kotlin
    @Test
    fun negative_index_clamps_to_base() {
        assertEquals(20, staggerDelayMs(-3))
    }
```

(`delay_is_capped_at_max_index` 테스트는 `staggerDelayMs(11) == staggerDelayMs(50)` 비교라 값 변경과 무관 — 그대로 둔다.)

- [ ] **Step 2: 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ScreenEntranceTest*'`
Expected: FAIL — `delays_match_snappy_sequence` 가 기존 상수(40/150/…)로 계산돼 어긋남.

- [ ] **Step 3: 상수 하향**

Edit `ScreenEntrance.kt`.

교체 전:
```kotlin
const val STAGGER_RISE_DP = 14
const val STAGGER_DURATION_MS = 620
const val STAGGER_BASE_DELAY_MS = 40
const val STAGGER_STEP_MS = 110
const val STAGGER_MAX_INDEX = 11
```
교체 후:
```kotlin
const val STAGGER_RISE_DP = 14
const val STAGGER_DURATION_MS = 380
const val STAGGER_BASE_DELAY_MS = 20
const val STAGGER_STEP_MS = 60
const val STAGGER_MAX_INDEX = 11
```

`rememberScreenEntrance` 의 windowMs 기본값도 낮춘다.

교체 전:
```kotlin
fun rememberScreenEntrance(
    reduceMotion: Boolean,
    windowMs: Int = 400,
): ScreenEntranceState {
```
교체 후:
```kotlin
fun rememberScreenEntrance(
    reduceMotion: Boolean,
    windowMs: Int = 300,
): ScreenEntranceState {
```

같은 파일의 스테일 주석도 새 값으로 정정한다(빌드는 안 깨지나 코드와 어긋남):
- 상단 블록 주석 `// oc-rise 정본 … 지연 40 + 110*index (nth-child 1..12 → 40..1250ms).` → `지연 20 + 60*index (index 0..11 → 20..680ms).`
- `staggerDelayMs` KDoc `nth-child 스태거 지연(ms): 40 + 110*clamp(index,0,11).` → `스태거 지연(ms): 20 + 60*clamp(index,0,11).`
- 테스트 파일 `ScreenEntranceTest.kt` 상단 KDoc 의 `프로토 … 시퀀스(40/150/260…1250)` → `스낵 시퀀스(20/80/140…680)`.

- [ ] **Step 4: 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ScreenEntranceTest*'`
Expected: PASS (3 tests).

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/ScreenEntrance.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/foundation/ScreenEntranceTest.kt
git commit -m "fix(anim): snappier stagger timing (380/60/20, window 300)"
```

---

### Task 2: 탭 전환 잔상 제거 (컨테이너 즉시 교체)

현재 `oceScreenEnter`는 `fadeIn`으로 새 화면을 **구 화면 위에 겹쳐** 100ms 페이드인한다(exit=None이라 구 화면이 그대로 남음) → 구 화면이 비쳐 보이는 "잔상". 컨테이너 전환을 즉시 교체(enter=None·exit=None)로 바꿔 겹침을 없앤다. 등장감은 섹션 스태거가 전담(프로토처럼 컨테이너는 전환 없음).

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavTransitions.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavHost.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/ReduceMotion.kt` (스테일 KDoc 정리)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavTransitionsTest.kt`
- Test: `android/app/src/androidTest/kotlin/com/jjundev/oneclickeng/ui/navigation/AppNavigationTest.kt`

**Interfaces:**
- Produces (변경): `val oceScreenEnter: EnterTransition = EnterTransition.None` (함수 → 상수), `val oceScreenExit: ExitTransition = ExitTransition.None`(불변). `OceNavHost` 에서 `reduceMotion` 파라미터 제거.

- [ ] **Step 1: 테스트를 즉시-교체 계약으로 갱신(먼저 실패시키기)**

Replace the whole body of `OceNavTransitionsTest.kt` with:

```kotlin
package com.jjundev.oneclickeng.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 전환 계약 검증 — 컨테이너는 즉시 교체(무전환)라 잔상이 없어야 한다. enter/exit 모두 None 상수임을 고정한다.
 */
class OceNavTransitionsTest {
    @Test
    fun enter_isInstantNoTransition() {
        assertSame(EnterTransition.None, oceScreenEnter)
    }

    @Test
    fun exit_isHardCut() {
        assertSame(ExitTransition.None, oceScreenExit)
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*OceNavTransitionsTest*'`
Expected: FAIL — `oceScreenEnter` 가 아직 함수라 `assertSame(..., oceScreenEnter)` 컴파일 불가.

- [ ] **Step 3: `oceScreenEnter` 를 None 상수로**

Edit `OceNavTransitions.kt`.

import 제거(더는 미사용):
```kotlin
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import com.jjundev.oneclickeng.ui.theme.OceMotion
```

함수+KDoc 교체 전:
```kotlin
/**
 * 전역 화면/탭 전환 정본(F4 확정). 컨테이너 = 빠른 fade(slide 없음 — jank 원인 제거),
 * 화면 등장감 = 섹션별 stagger(oc-rise, Task 3~5).
 * 퇴장 = 하드 컷(구 화면 즉시 제거 — 프로토 정합, [oceScreenExit]).
 * [reduceMotion] 시 진입도 정적([EnterTransition.None], A7 "전환→즉시").
 *
 * 내부 3탭([OceNavHost])·바깥 그래프([com.jjundev.oneclickeng.ui.root.AppRoot] NavHost)가 이 동일 스펙을
 * 공유해 전 전환을 균일화한다.
 */
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
교체 후:
```kotlin
/**
 * 전역 화면/탭 전환 정본(F4 확정). 컨테이너는 **즉시 교체(무전환)** — 이전 fade 는 구 화면 위에 겹쳐
 * 페이드인돼 잔상을 남겨 제거했다. 화면 등장감은 섹션별 stagger(oc-rise)가 전담한다(프로토처럼 컨테이너는
 * 전환 없음). 내부 3탭([OceNavHost])·바깥 그래프([com.jjundev.oneclickeng.ui.root.AppRoot] NavHost)가 공유한다.
 */
val oceScreenEnter: EnterTransition = EnterTransition.None
```

(`oceScreenExit` 상수와 그 KDoc은 그대로 둔다.)

- [ ] **Step 4: `OceNavHost` 갱신(reduceMotion 제거 + 즉시 교체 배선)**

Edit `OceNavHost.kt`.

import 제거(더는 미사용):
```kotlin
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.theme.OceTheme
```

KDoc 교체 전:
```kotlin
 * **전환:** 컨테이너 = 빠른 fade(slide 없음), 화면 등장감 = 섹션 stagger(Task 3~5).
 * 퇴장 = 하드 컷([oceScreenExit]). [reduceMotion] 이 true 면 진입도 정적(None).
```
KDoc 교체 후:
```kotlin
 * **전환:** 컨테이너 = 즉시 교체(무전환 — 잔상 제거), 화면 등장감 = 섹션 stagger.
```

시그니처+본문 교체 전:
```kotlin
fun OceNavHost(
    navController: NavHostController,
    onStartSession: (promptSeed: String, topicLabel: String, topicEmoji: String, level: String, length: Int) -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotion(),
) {
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
교체 후:
```kotlin
fun OceNavHost(
    navController: NavHostController,
    onStartSession: (promptSeed: String, topicLabel: String, topicEmoji: String, level: String, length: Int) -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = OceTab.Start.route,
        modifier = modifier,
        enterTransition = { oceScreenEnter },
        exitTransition = { oceScreenExit },
        popEnterTransition = { oceScreenEnter },
        popExitTransition = { oceScreenExit },
    ) {
```

- [ ] **Step 5: `AppRoot` outer NavHost 갱신**

Edit `AppRoot.kt`.

import 제거(더는 미사용 — detekt 로 재확인):
```kotlin
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.theme.OceTheme
```

교체 전:
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
교체 후:
```kotlin
    NavHost(
        navController = outerNavController,
        startDestination = resolvedStart,
        enterTransition = { oceScreenEnter },
        exitTransition = { oceScreenExit },
        popEnterTransition = { oceScreenEnter },
        popExitTransition = { oceScreenExit },
    ) {
```

- [ ] **Step 6: `AppNavigationTest` 의 reduceMotion 주입 제거**

Edit `AppNavigationTest.kt` — `OceNavHost` 의 `reduceMotion` 파라미터가 사라졌으므로 주입을 제거하고, 테스트 이름/KDoc의 reduce-motion 전환 프레이밍을 정정한다.

KDoc 교체 전:
```kotlin
     * reduce-motion 정적 대체 seam 검증(수용기준 #4). 시스템 설정 토글(WRITE_SECURE_SETTINGS 필요)
     * 대신 [OceNavHost] 의 reduceMotion 인자를 직접 주입해, 정적 전환 경로에서도 시작 화면이 정상
     * 렌더됨을 반증가능하게 확인한다.
     */
    @Test
    fun navHostRendersStartDestinationWithReduceMotion() {
```
KDoc 교체 후:
```kotlin
     * 컨테이너 전환이 즉시 교체(무전환)로 바뀐 뒤에도 시작 목적지가 정상 렌더됨을 확인한다.
     */
    @Test
    fun navHostRendersStartDestination() {
```

호출 교체 전:
```kotlin
                    OceNavHost(
                        navController = navController,
                        onStartSession = { _, _, _, _, _ -> },
                        onResume = {},
                        reduceMotion = true,
                    )
```
호출 교체 후:
```kotlin
                    OceNavHost(
                        navController = navController,
                        onStartSession = { _, _, _, _, _ -> },
                        onResume = {},
                    )
```

- [ ] **Step 7: `ReduceMotion.kt` 스테일 KDoc 정정**

`rememberReduceMotion` 의 KDoc 이 `OceNavHost(reduceMotion = …)` 를 사용 예시로 든다(라인 13·19 부근). Task 2 로 그 파라미터가 사라졌으므로 예시를 갱신한다: `OceNavHost(reduceMotion = …)` 언급을 `OneClickSkeleton(reduceMotion = …)` 등 현존 소비처로 교체하거나 해당 예시 구절을 삭제한다(프로세스 산문일 뿐 컴파일엔 무관하나, 존재하지 않는 seam 을 가리키지 않도록).

- [ ] **Step 8: 전체 검증**

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL — detekt clean(미사용 import/param 0), `OceNavTransitionsTest` 통과, `AppNavigationTest` 컴파일·통과, 전 단위·스크린샷 테스트 통과(스태거 no-op이라 골든 불변).

- [ ] **Step 9: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavTransitions.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavHost.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/ReduceMotion.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavTransitionsTest.kt \
        android/app/src/androidTest/kotlin/com/jjundev/oneclickeng/ui/navigation/AppNavigationTest.kt
git commit -m "fix(nav): instant container swap — remove tab-switch afterimage"
```

---

### Task 3: 기록 카운트업 debug 시드 (기기서 검증 가능)

`StubLifetimeStatsSource` 가 항상 `null` 을 반환해 카운트업이 정적 0으로 강등된다(실데이터 = M3-05 미배선). debug 빌드에서만 샘플 통계를 반환하게 해, 실기기에서 슬롯머신 롤업+scaleY 바운스를 눈으로 검증한다. release 는 스텁 0 유지.

**Files:**
- Modify: `android/app/build.gradle.kts` (buildConfig 빌드피처 활성화)
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/LifetimeStats.kt`

**Interfaces:**
- Produces: `StubLifetimeStatsSource.lifetime()` = debug 시 `LifetimeStats(1240,135,12)`, release 시 `null`(불변 계약).
- Consumes: `com.jjundev.oneclickeng.BuildConfig.DEBUG`(buildConfig 활성화로 생성).

**참고(검증 안전성):** `RecordsViewModelTest` 는 실제 Stub 이 아니라 `FakeLifetimeStatsSource` 를 주입하므로 이 변경에 영향받지 않는다. `RecordsScreenScreenshotTest` 는 `lifetime` 을 컴포저블에 직접 전달(animateCountUp=false)하므로 골든 불변. `BuildConfig.DEBUG` 는 variant별 컴파일 상수라 어느 단위테스트도 Stub의 분기를 단정하지 않는다(새 단위테스트 없음 — 아래 사유).

- [ ] **Step 1: buildConfig 빌드피처 활성화**

Edit `android/app/build.gradle.kts`.

교체 전:
```kotlin
    buildFeatures {
        compose = true
```
교체 후:
```kotlin
    buildFeatures {
        compose = true
        buildConfig = true
```

- [ ] **Step 2: Stub 을 debug 시드로**

Edit `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/LifetimeStats.kt`.

import 추가(파일 상단, 기존 import 블록):
```kotlin
import com.jjundev.oneclickeng.BuildConfig
```

교체 전:
```kotlin
/** M3-05 배선 전 스텁 — 항상 `null`. 헤더는 정적 0 지표로 렌더된다. */
@Singleton
class StubLifetimeStatsSource
    @Inject
    constructor() : LifetimeStatsSource {
        override suspend fun lifetime(): LifetimeStats? = null
    }
```
교체 후:
```kotlin
/**
 * M3-05 배선 전 스텁. **release = 항상 `null`**(헤더 정적 0). **debug 빌드에서는** 슬롯머신 카운트업을 눈으로
 * 검증할 수 있도록 샘플 통계([DEBUG_SAMPLE])를 시드한다(`BuildConfig.DEBUG` 게이트) — 실데이터 배선(M3-05)과
 * 무관한 개발 확인용이며, M3-05 가 이 seam 을 교체하면 제거 대상이다.
 */
@Singleton
class StubLifetimeStatsSource
    @Inject
    constructor() : LifetimeStatsSource {
        override suspend fun lifetime(): LifetimeStats? =
            if (BuildConfig.DEBUG) DEBUG_SAMPLE else null

        private companion object {
            val DEBUG_SAMPLE = LifetimeStats(xp = 1240, studyMinutes = 135, studyDays = 12)
        }
    }
```

- [ ] **Step 3: 빌드·정적검증 통과 확인**

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL — buildConfig 생성 후 debug/release 모두 컴파일, detekt clean, 전 단위·스크린샷 테스트 통과(Fake 주입이라 무영향, 골든 불변).

(새 단위테스트 없음 — 사유: `BuildConfig.DEBUG` 는 variant 컴파일 상수라 `testDebugUnitTest`(true)/`testReleaseUnitTest`(false)에서 분기가 달라져, Stub 분기를 단정하는 테스트는 release 게이트를 깨뜨린다. 검증은 아래 실기기 육안으로 한다.)

- [ ] **Step 4: 실기기 육안 검증**

```bash
scripts/verify-android.sh :app:installDebug
```
그다음 기기에서 **앱을 새로 실행**(프로세스 첫 기록 탭 진입에서만 롤업 — `HistoryCountUpGate` @Singleton 첫 진입 게이트) → 기록 탭 진입 → 평생 통계 헤더의 `누적 1240 XP`·`12일 학습` 숫자가 0에서 굴러오르며(슬롯머신 롤업 1260ms) scaleY 바운스가 보이는지 확인. (섹션 자체는 스태거로 떠오르며, 그 안에서 숫자가 롤업된다.)

- [ ] **Step 5: 커밋**

```bash
git add android/app/build.gradle.kts \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/LifetimeStats.kt
git commit -m "feat(records): debug-only lifetime seed to verify count-up"
```

---

## 검증 노트(플랜 밖 · 참고)

- 스태거 속도·잔상 제거의 최종 체감은 실기기(`installDebug` 후 육안)로 확정한다 — 단위/스크린샷 게이트는 정적 최종 상태·지연 수치만 본다.
- 카운트업 debug 시드는 M3-05 실데이터 배선 시 제거 대상(그때 `StubLifetimeStatsSource` 자체가 실구현으로 교체됨).
- 잔상 제거로 `reduceMotion` 이 컨테이너 전환에서 완전히 빠졌다(스태거 쪽 `rememberScreenEntrance(reduceMotion)` 게이트는 각 화면에 그대로 유지 — 무관).

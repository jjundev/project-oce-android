# Play Store 강제 업데이트 게이트 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 앱 실행 시 Play Store에 IMMEDIATE 타입 업데이트가 있으면 Google Play Core의 인앱 업데이트(In-App Update) 플로우를 자동으로 띄워 사용자가 업데이트를 완료하기 전까지 앱을 쓸 수 없게 막는다(`archive/android`의 `MainActivity.checkForAppUpdate`와 동일한 사용자 경험 재구현).

**Architecture:** `core/update/` 레이어에 Play Core 타입을 완전히 감추는 `AppUpdateChecker` seam(인터페이스)을 하나 두고, 실제 Play Core 구현(`PlayAppUpdateChecker`)을 Hilt로 바인딩한다. `ui/root/`에 이 seam만 의존하는 별도의 `UpdateGateViewModel`을 새로 만들어 `AppViewModel`의 기존 부트 게이트(로그인/온보딩)를 건드리지 않고, `AppRoot`의 최상단에서 독립적으로 앱 전체를 가로막는다. `AppRoot`는 `ActivityResultLauncher<IntentSenderRequest>`를 등록해 실제 업데이트 플로우를 시작하고, 사용자가 취소하면 archive와 동일하게 `Activity.finish()`로 앱을 종료한다. 재개(onResume) 시 진행 중인 업데이트를 다시 띄우는 것도 archive의 `onResume` 로직을 그대로 이식한다.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, `com.google.android.play:app-update:2.1.0`(archive와 동일 버전 고정). Split(2.x) `app-update` 라이브러리의 `AppUpdateManager.getAppUpdateInfo()`는 `com.google.android.gms.tasks.Task<AppUpdateInfo>`를 반환한다(레거시 모놀리식 `com.google.android.play:core`의 자체 `Task` 타입이 아니다 — [공식 API 레퍼런스](https://developer.android.com/reference/com/google/android/play/core/appupdate/AppUpdateManager) 확인) — 이미 이 모듈에 있는 `kotlinx-coroutines-play-services`의 `.await()`(`android/app/build.gradle.kts`, 게스트 익명 로그인 브릿지와 동일 의존성)를 그대로 재사용한다. 별도 coroutine 브릿지 코드나 신규 의존성이 필요 없다.

## Global Constraints

- 신규 외부 의존성은 `com.google.android.play:app-update:2.1.0` 하나만 추가한다(archive와 동일 버전 — `libs.versions.toml`의 AGP/Kotlin 라인처럼 "이미 검증된 버전 재사용" 원칙을 따른다). `app-update-ktx`는 추가하지 않는다 — 불필요하다: split(2.x) `app-update` 라이브러리의 `getAppUpdateInfo()`는 이미 `com.google.android.gms.tasks.Task`를 반환하므로([공식 API 레퍼런스](https://developer.android.com/reference/com/google/android/play/core/appupdate/AppUpdateManager)), 이 모듈에 이미 있는 `kotlinx-coroutines-play-services`의 `.await()`로 바로 suspend 브릿지가 된다(신규 coroutine 브릿지 코드·의존성 불필요).
- Play Core 관련 타입(`AppUpdateManager`, `AppUpdateInfo`, `UpdateAvailability` 등)은 `core/update/` 밖으로 새지 않는다 — `ui/root/UpdateGateViewModel`은 자체 `AppUpdateChecker` 인터페이스만 의존해 Play Core 없이 단위테스트한다(`AuthRepository`/`ProfileRepository`/`ConnectivityObserver`와 동일 관례).
- 판정 로직(업데이트 필요/진행중 여부)은 프레임워크 비의존 순수 함수로 분리해 단위테스트한다(`core/connectivity`의 `connectivityOf` 관례와 동일).
- 조회 실패(Play 서비스 미탑재 기기, 에뮬레이터, 네트워크 오류 등)는 항상 **fail-open**(업데이트 불필요로 간주, 부팅 진행)한다 — 거짓 차단으로 앱을 영구히 못 열게 만들면 안 된다.
- `hiltViewModel()`로 연결되는 스테이트풀 배선(`AppRoot`의 실제 Play Core 트리거 경로)은 이 코드베이스의 기존 관례상 Robolectric 단위테스트 범위 밖이다(`HomeSituationTapTest.kt:21` 주석 참조) — Task 3은 콜백만 받는 stateless 컴포저블만 테스트하고, 실제 Play Console 연동 검증은 Task 5의 수동 QA로 커버한다.
- Play Core의 IMMEDIATE 업데이트 플로우는 **실제 Play Console 릴리스 트랙에 더 높은 versionCode가 올라가 있어야만** 트리거된다 — 로컬 빌드나 CI로는 종단 검증이 불가능하다. 이 리포에는 아직 release 서명 설정(`signingConfig`)이 없다(`android/app/build.gradle.kts`의 `release` 블록에 `signingConfig` 없음) — Play Console 업로드가 필요한 Task 5는 서명 설정이 먼저 갖춰져야 실행 가능하며, 이는 이 플랜의 범위 밖이다(별도 결정 필요).

---

## File Structure

**Create:**
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/update/AppUpdateChecker.kt` — Play Core를 감추는 seam 인터페이스.
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/update/PlayAppUpdateChecker.kt` — 실제 Play Core 구현(`.await()` 로 suspend 변환) + 순수 판정 함수(`isImmediateUpdateAvailable`/`isImmediateUpdateInProgress`).
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/update/UpdateModule.kt` — Hilt `@Binds` 모듈.
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/update/PlayUpdateAvailabilityTest.kt` — 순수 판정 함수 진리표 테스트.
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/UpdateGateViewModel.kt` — 강제 업데이트 게이트 상태 머신.
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/root/UpdateGateViewModelTest.kt` — `AppUpdateChecker` 페이크로 상태 전이 테스트.
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickUpdateGate.kt` — 강제 업데이트 전체화면 stateless 컴포저블(기존 `BlockingGateScaffold` 재사용).
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/OneClickUpdateGateTest.kt` — Robolectric 컴포즈 테스트.

**Modify:**
- `android/gradle/libs.versions.toml` — `playAppUpdate` 버전 + `play-app-update` 라이브러리 좌표 추가.
- `android/app/build.gradle.kts` — 의존성 추가 + release 변이 단위테스트 제외 목록에 새 컴포즈 테스트 등록.
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt` — 최상단에 업데이트 게이트 마운트, `ActivityResultLauncher` 등록, onResume 재개 훅, 취소 시 `finish()`.

---

## Task 1: `core/update` — Play Core seam + 판정 로직 + DI 바인딩

**Files:**
- Modify: `android/gradle/libs.versions.toml`
- Modify: `android/app/build.gradle.kts`
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/update/AppUpdateChecker.kt`
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/update/PlayAppUpdateChecker.kt`
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/update/UpdateModule.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/update/PlayUpdateAvailabilityTest.kt`

**Interfaces:**
- Produces: `interface AppUpdateChecker { suspend fun isImmediateUpdateRequired(): Boolean; suspend fun isUpdateInProgress(): Boolean; suspend fun launchImmediateUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) }` — Task 2가 이걸로 `UpdateGateViewModel`을 만든다.
- Produces: `internal fun isImmediateUpdateAvailable(availability: Int, isImmediateAllowed: Boolean): Boolean`, `internal fun isImmediateUpdateInProgress(availability: Int): Boolean` — 순수 판정 함수.
- Produces: Hilt에서 `AppUpdateChecker`를 주입하면 `PlayAppUpdateChecker` 싱글톤이 온다(`UpdateModule` 바인딩).

- [ ] **Step 1: 버전 카탈로그에 의존성 추가**

`android/gradle/libs.versions.toml`의 `[versions]` 블록, `androidxHilt` 줄 다음에 추가:

```toml
# Play Core 인앱 업데이트(강제 업데이트 게이트, archive MainActivity.checkForAppUpdate 이식). archive 와 동일 버전 고정.
playAppUpdate = "2.1.0"
```

`[libraries]` 블록, `androidx-hilt-compiler` 줄 다음에 추가:

```toml
# 강제 업데이트 게이트(core/update) — Play Core Task<AppUpdateInfo> API. split(2.x) 라이브러리라
# com.google.android.gms.tasks.Task 를 반환 — 이미 있는 kotlinx-coroutines-play-services 의 .await() 재사용.
play-app-update = { group = "com.google.android.play", name = "app-update", version.ref = "playAppUpdate" }
```

- [ ] **Step 2: 앱 모듈에 의존성 추가**

`android/app/build.gradle.kts`의 `dependencies` 블록, `implementation(libs.androidx.hilt.work)` / `ksp(libs.androidx.hilt.compiler)` 아래(WorkManager 블록 다음)에 추가:

```kotlin
    // 강제 업데이트 게이트(core/update) — archive MainActivity.checkForAppUpdate 이식.
    implementation(libs.play.app.update)
```

- [ ] **Step 3: 실패하는 테스트 작성**

`android/app/src/test/kotlin/com/jjundev/oneclickeng/core/update/PlayUpdateAvailabilityTest.kt`:

```kotlin
package com.jjundev.oneclickeng.core.update

import com.google.android.play.core.install.model.UpdateAvailability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayUpdateAvailabilityTest {
    @Test
    fun `update available plus immediate allowed is required`() {
        assertTrue(
            isImmediateUpdateAvailable(
                availability = UpdateAvailability.UPDATE_AVAILABLE,
                isImmediateAllowed = true,
            ),
        )
    }

    @Test
    fun `update available but immediate not allowed is not required`() {
        assertFalse(
            isImmediateUpdateAvailable(
                availability = UpdateAvailability.UPDATE_AVAILABLE,
                isImmediateAllowed = false,
            ),
        )
    }

    @Test
    fun `no update available is not required regardless of allowed flag`() {
        assertFalse(
            isImmediateUpdateAvailable(
                availability = UpdateAvailability.UPDATE_NOT_AVAILABLE,
                isImmediateAllowed = true,
            ),
        )
    }

    @Test
    fun `developer triggered update in progress is reported as in-progress`() {
        assertTrue(isImmediateUpdateInProgress(UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS))
    }

    @Test
    fun `update available is not reported as in-progress`() {
        assertFalse(isImmediateUpdateInProgress(UpdateAvailability.UPDATE_AVAILABLE))
    }
}
```

Note: `isImmediateUpdateInProgress`라는 이름을 쓴다(단순히 `isUpdateInProgress`가 아니라) — `AppUpdateChecker.isUpdateInProgress()` 멤버 함수와 이름이 겹치면 컴파일은 되지만(파라미터 개수가 달라 오버로드 해석은 되지만) 리뷰어를 헷갈리게 한다.

- [ ] **Step 4: 테스트 실행해 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.core.update.PlayUpdateAvailabilityTest'`
Expected: FAIL — `isImmediateUpdateAvailable`/`isImmediateUpdateInProgress` unresolved reference (아직 정의 안 됨).

- [ ] **Step 5: `AppUpdateChecker` 인터페이스 작성**

`android/app/src/main/kotlin/com/jjundev/oneclickeng/core/update/AppUpdateChecker.kt`:

```kotlin
package com.jjundev.oneclickeng.core.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest

/**
 * Play 스토어 강제 업데이트 게이트 seam(archive `MainActivity.checkForAppUpdate` 재구현). Play Core
 * 타입을 이 인터페이스 뒤에 완전히 숨겨 [com.jjundev.oneclickeng.ui.root.UpdateGateViewModel] 을 Play
 * Core 없이 단위테스트할 수 있게 한다(`AuthRepository`/`ProfileRepository` 와 동일 관례).
 */
interface AppUpdateChecker {
    /** IMMEDIATE 타입 업데이트가 스토어에 있으면 true. 조회 실패(Play 서비스 미탑재 등)는 false(fail-open). */
    suspend fun isImmediateUpdateRequired(): Boolean

    /** 직전에 시작된 IMMEDIATE 업데이트가 아직 진행 중이면 true(onResume 재개 판정용). */
    suspend fun isUpdateInProgress(): Boolean

    /** IMMEDIATE 업데이트 플로우를 시작한다. [launcher] 결과 처리는 호출부 책임이다. */
    suspend fun launchImmediateUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>)
}
```

- [ ] **Step 6: `PlayAppUpdateChecker` 구현 작성**

`android/app/src/main/kotlin/com/jjundev/oneclickeng/core/update/PlayAppUpdateChecker.kt`:

```kotlin
package com.jjundev.oneclickeng.core.update

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AppUpdateChecker] 의 Play Core 구현(archive/android `MainActivity.checkForAppUpdate` 이식). Play
 * 스토어로 설치된 실기기에서만 `UPDATE_AVAILABLE` 신호를 준다 — 에뮬레이터/사이드로드/조회 실패는
 * [isImmediateUpdateAvailable] 이 false 라 fail-open(강제 업데이트 없이 부팅 진행).
 *
 * `AppUpdateManager.getAppUpdateInfo()` 는 split(2.x) 라이브러리에서 `com.google.android.gms.tasks.Task`
 * 를 반환한다(레거시 모놀리식 `com.google.android.play:core`의 자체 Task 타입이 아니다) — 이미 이 모듈에
 * 있는 `kotlinx-coroutines-play-services` 의 `.await()`(AppViewModel 의 게스트 익명 로그인 브릿지와
 * 동일 의존성)로 바로 suspend 화한다. 별도 coroutine 브릿지 코드가 필요 없다.
 */
@Singleton
class PlayAppUpdateChecker
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : AppUpdateChecker {
        private val manager: AppUpdateManager = AppUpdateManagerFactory.create(context)

        override suspend fun isImmediateUpdateRequired(): Boolean =
            runCatching {
                val info = manager.appUpdateInfo.await()
                isImmediateUpdateAvailable(
                    availability = info.updateAvailability(),
                    isImmediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
                )
            }.getOrDefault(false)

        override suspend fun isUpdateInProgress(): Boolean =
            runCatching {
                isImmediateUpdateInProgress(manager.appUpdateInfo.await().updateAvailability())
            }.getOrDefault(false)

        override suspend fun launchImmediateUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
            val info = manager.appUpdateInfo.await()
            manager.startUpdateFlowForResult(
                info,
                launcher,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
            )
        }
    }

/**
 * IMMEDIATE 업데이트 필요 판정(archive `checkForAppUpdate` 조건 이식). 프레임워크 비의존 순수 함수라
 * Play Core 상수만으로 진리표를 고정한다: `UPDATE_AVAILABLE` && IMMEDIATE 허용 둘 다여야 true.
 */
internal fun isImmediateUpdateAvailable(
    availability: Int,
    isImmediateAllowed: Boolean,
): Boolean = availability == UpdateAvailability.UPDATE_AVAILABLE && isImmediateAllowed

/**
 * 직전 IMMEDIATE 업데이트가 아직 진행 중인지(onResume 재개 판정, archive `onResume` 이식). 이름을
 * `isImmediateUpdateInProgress`로 둔다 — [AppUpdateChecker.isUpdateInProgress] 멤버 함수와 이름이
 * 겹치면(오버로드로 컴파일은 되지만) 호출부에서 어느 쪽이 불리는지 헷갈린다.
 */
internal fun isImmediateUpdateInProgress(availability: Int): Boolean =
    availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
```

- [ ] **Step 7: Hilt 바인딩 모듈 작성**

`android/app/src/main/kotlin/com/jjundev/oneclickeng/core/update/UpdateModule.kt`:

```kotlin
package com.jjundev.oneclickeng.core.update

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 강제 업데이트 게이트 seam 바인딩. [AppUpdateChecker] → [PlayAppUpdateChecker](Play Core 기반). */
@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {
    @Binds
    @Singleton
    abstract fun bindAppUpdateChecker(impl: PlayAppUpdateChecker): AppUpdateChecker
}
```

- [ ] **Step 8: 테스트 실행해 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.core.update.PlayUpdateAvailabilityTest'`
Expected: PASS (5 tests)

- [ ] **Step 9: 전체 빌드로 컴파일/detekt 확인**

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL (detekt + androidTest 컴파일 + 양 변이 단위테스트 통과, 새 파일들이 컴파일 에러 없이 포함됨)

- [ ] **Step 10: 커밋**

```bash
git add android/gradle/libs.versions.toml android/app/build.gradle.kts \
  android/app/src/main/kotlin/com/jjundev/oneclickeng/core/update/ \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/core/update/
git commit -m "feat(update): add Play Core seam and immediate-update availability logic"
```

---

## Task 2: `UpdateGateViewModel` — 게이트 상태 머신

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/UpdateGateViewModel.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/root/UpdateGateViewModelTest.kt`

**Interfaces:**
- Consumes: `AppUpdateChecker`(Task 1) — 생성자 주입.
- Produces: `sealed interface UpdateGateState { data object Checking; data object NotRequired; data object Required }`, `class UpdateGateViewModel(updateChecker: AppUpdateChecker) : ViewModel()` — `val state: StateFlow<UpdateGateState>`, `fun onResumeCheck()`, `fun launchUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>)`. Task 4가 `AppRoot`에서 이 셋을 그대로 쓴다.

- [ ] **Step 1: 실패하는 테스트 작성**

`android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/root/UpdateGateViewModelTest.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.root

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import com.jjundev.oneclickeng.core.update.AppUpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateGateViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `no update available resolves NotRequired`() {
        val model = UpdateGateViewModel(FakeAppUpdateChecker(immediateRequired = false))
        assertEquals(UpdateGateState.NotRequired, model.state.value)
    }

    @Test
    fun `immediate update available resolves Required`() {
        val model = UpdateGateViewModel(FakeAppUpdateChecker(immediateRequired = true))
        assertEquals(UpdateGateState.Required, model.state.value)
    }

    @Test
    fun `check failure fails open to NotRequired`() {
        val model = UpdateGateViewModel(FakeAppUpdateChecker(throwOnCheck = true))
        assertEquals(UpdateGateState.NotRequired, model.state.value)
    }

    @Test
    fun `resume check re-flags Required when an immediate update is still in progress`() {
        val checker = FakeAppUpdateChecker(immediateRequired = false, inProgress = true)
        val model = UpdateGateViewModel(checker)
        assertEquals(UpdateGateState.NotRequired, model.state.value)

        model.onResumeCheck()

        assertEquals(UpdateGateState.Required, model.state.value)
    }

    @Test
    fun `resume check does nothing while still checking`() {
        val checker = FakeAppUpdateChecker(neverResolves = true)
        val model = UpdateGateViewModel(checker)
        assertEquals(UpdateGateState.Checking, model.state.value)

        model.onResumeCheck()

        assertEquals(0, checker.inProgressCalls)
    }

    @Test
    fun `launchUpdate delegates to the checker with the given launcher`() {
        val checker = FakeAppUpdateChecker(immediateRequired = true)
        val model = UpdateGateViewModel(checker)

        model.launchUpdate(NoOpLauncher)

        assertEquals(1, checker.launchCalls)
    }
}

private class FakeAppUpdateChecker(
    private val immediateRequired: Boolean = false,
    private val inProgress: Boolean = false,
    private val throwOnCheck: Boolean = false,
    private val neverResolves: Boolean = false,
) : AppUpdateChecker {
    var launchCalls = 0
        private set
    var inProgressCalls = 0
        private set

    override suspend fun isImmediateUpdateRequired(): Boolean {
        if (neverResolves) kotlinx.coroutines.awaitCancellation()
        if (throwOnCheck) error("Play services unavailable")
        return immediateRequired
    }

    override suspend fun isUpdateInProgress(): Boolean {
        inProgressCalls++
        return inProgress
    }

    override suspend fun launchImmediateUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        launchCalls++
    }
}

private val NoOpLauncher =
    object : ActivityResultLauncher<IntentSenderRequest>() {
        override fun launch(
            input: IntentSenderRequest,
            options: ActivityOptionsCompat?,
        ) = Unit

        override fun unregister() = Unit

        override fun getContract(): ActivityResultContract<IntentSenderRequest, *> =
            throw UnsupportedOperationException("not needed for this test")
    }
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.ui.root.UpdateGateViewModelTest'`
Expected: FAIL — `UpdateGateViewModel`/`UpdateGateState` unresolved reference (아직 정의 안 됨).

- [ ] **Step 3: `UpdateGateViewModel` 구현 작성**

`android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/UpdateGateViewModel.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.root

import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjundev.oneclickeng.core.update.AppUpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 강제 업데이트 게이트(archive `MainActivity` 재구현). [AppRoot] 최상단에서 마운트돼 [AppViewModel]
 * 부트 게이트보다 먼저 확정된다 — 업데이트가 필요하면 로그인/온보딩/메인 어느 것도 컴포즈되지 않는다.
 * [AppViewModel] 은 건드리지 않는다(관심사 분리, 기존 부트 로직 위험 최소화).
 */
@HiltViewModel
class UpdateGateViewModel
    @Inject
    constructor(
        private val updateChecker: AppUpdateChecker,
    ) : ViewModel() {
        private val _state = MutableStateFlow<UpdateGateState>(UpdateGateState.Checking)
        val state: StateFlow<UpdateGateState> = _state.asStateFlow()
        private var launchJob: Job? = null

        init {
            viewModelScope.launch { check() }
        }

        private suspend fun check() {
            _state.value =
                if (runCatching { updateChecker.isImmediateUpdateRequired() }.getOrDefault(false)) {
                    UpdateGateState.Required
                } else {
                    UpdateGateState.NotRequired
                }
        }

        /**
         * [AppRoot] 의 ON_RESUME 훅(archive `onResume` 이식). 최초 판정이 끝나기 전(=[UpdateGateState.Checking])
         * 이면 아직 결정할 게 없으니 no-op. 이미 진행 중인 업데이트가 있으면 다시 [UpdateGateState.Required]
         * 로 세팅해 [AppRoot] 의 재개 트리거를 유도한다.
         */
        fun onResumeCheck() {
            if (_state.value == UpdateGateState.Checking) return
            viewModelScope.launch {
                if (runCatching { updateChecker.isUpdateInProgress() }.getOrDefault(false)) {
                    _state.value = UpdateGateState.Required
                }
            }
        }

        /**
         * [AppRoot] 는 이 함수를 두 곳에서 부를 수 있다: [UpdateGateState.Required] 진입 시 자동 트리거
         * (`LaunchedEffect`)와 [com.jjundev.oneclickeng.ui.component.OneClickUpdateGate] 의 수동 버튼.
         * 이미 진행 중인 [launchJob] 이 있으면 무시해 Play Core 플로우가 중복 시작되지 않게 한다.
         */
        fun launchUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
            if (launchJob?.isActive == true) return
            launchJob =
                viewModelScope.launch {
                    runCatching { updateChecker.launchImmediateUpdate(launcher) }
                        .onFailure { Log.w(TAG, "Failed to start immediate update flow", it) }
                }
        }

        private companion object {
            const val TAG = "UpdateGateViewModel"
        }
    }

/**
 * 강제 업데이트 게이트 상태(archive `MainActivity` 재구현). [Checking] 이 확정되기 전까지 [AppRoot] 는
 * splash 만 보여준다(기존 [BootState.Loading] 과 동일 자리, 별도 게이트라 합치지 않는다).
 */
sealed interface UpdateGateState {
    /** 최초 조회 중 — [AppRoot] 는 splash 만 보여준다. */
    data object Checking : UpdateGateState

    /** 업데이트 불필요 — [AppRoot] 는 기존 [AppViewModel] 부트 게이트로 진행한다. */
    data object NotRequired : UpdateGateState

    /** IMMEDIATE 업데이트 필요 — [AppRoot] 는 [com.jjundev.oneclickeng.ui.component.OneClickUpdateGate] 로 앱을 가로막는다. */
    data object Required : UpdateGateState
}
```

- [ ] **Step 4: 테스트 실행해 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.ui.root.UpdateGateViewModelTest'`
Expected: PASS (6 tests)

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/UpdateGateViewModel.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/root/UpdateGateViewModelTest.kt
git commit -m "feat(update): add UpdateGateViewModel state machine"
```

---

## Task 3: `OneClickUpdateGate` — 전체화면 강제 업데이트 컴포저블

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickUpdateGate.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/OneClickUpdateGateTest.kt`
- Modify: `android/app/build.gradle.kts`

**Interfaces:**
- Consumes: 기존 `internal fun BlockingGateScaffold(...)`/`internal data class GateAction(...)`(`ui/component/OneClickBlockingGate.kt`, 같은 패키지라 접근 가능), `enum class OceIcon`(`ui/foundation/OneClickIcon.kt`) — `OceIcon.CloudSync` 사용(전용 "업데이트" 아이콘이 카탈로그에 없어 의미상 가장 가까운 기존 아이콘 재사용, 신규 drawable 추가 안 함).
- Produces: `@Composable fun OneClickUpdateGate(onUpdateNow: () -> Unit, modifier: Modifier = Modifier)` — Task 4가 `AppRoot`에서 그대로 쓴다.

- [ ] **Step 1: 실패하는 테스트 작성**

`android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/OneClickUpdateGateTest.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.component

import android.app.Application
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OneClickUpdateGateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tapping_the_primary_action_invokes_onUpdateNow() {
        var tapped = false
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                OneClickUpdateGate(onUpdateNow = { tapped = true })
            }
        }

        composeRule.onNodeWithText("지금 업데이트").performClick()

        assertTrue(tapped)
    }

    @Test
    fun renders_the_forced_update_title() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                OneClickUpdateGate(onUpdateNow = {})
            }
        }

        composeRule.onNodeWithText("새 버전이 나왔어요").assertExists()
    }
}
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.ui.component.OneClickUpdateGateTest'`
Expected: FAIL — `OneClickUpdateGate` unresolved reference (아직 정의 안 됨).

- [ ] **Step 3: `OneClickUpdateGate` 구현 작성**

`android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickUpdateGate.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 강제 업데이트 전체화면 게이트(archive `MainActivity` 취소 시 Toast+finish 대체 화면, C12 anatomy 재사용).
 * [OneClickBlockingGate] 와 레이아웃은 같지만 액션이 "재시도"가 아니라 "지금 업데이트" 하나뿐이라
 * [BlockingGateSurface] 를 늘리지 않고 별도 공개 컴포넌트로 둔다.
 */
@Composable
fun OneClickUpdateGate(
    onUpdateNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BlockingGateScaffold(
        icon = OceIcon.CloudSync,
        title = "새 버전이 나왔어요",
        body = "계속 사용하려면 최신 버전으로 업데이트해 주세요.",
        actions = listOf(GateAction(label = "지금 업데이트", onClick = onUpdateNow, primary = true)),
        modifier = modifier,
    )
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun OneClickUpdateGatePreview() {
    OceTheme {
        OneClickUpdateGate(onUpdateNow = {})
    }
}
```

- [ ] **Step 4: release 변이 단위테스트 제외 목록에 등록**

`android/app/build.gradle.kts`의 `tasks.withType<Test>().configureEach { ... exclude(...) }` 목록(release 전용 제외, `createComposeRule` 테스트는 debug 전용)에 한 줄 추가. `"**/RecordsSkeletonMinHoldTest*",` 다음 줄에:

```kotlin
            "**/OneClickUpdateGateTest*",
```

- [ ] **Step 5: 테스트 실행해 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.ui.component.OneClickUpdateGateTest'`
Expected: PASS (2 tests)

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickUpdateGate.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/OneClickUpdateGateTest.kt \
  android/app/build.gradle.kts
git commit -m "feat(update): add OneClickUpdateGate forced-update screen"
```

---

## Task 4: `AppRoot`에 게이트 배선

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt`

**Interfaces:**
- Consumes: `UpdateGateViewModel`/`UpdateGateState`(Task 2), `OneClickUpdateGate`(Task 3).
- Produces: 없음(터미널 통합 지점 — 이 파일을 더 소비하는 후속 태스크 없음).

- [ ] **Step 1: import 추가**

`android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt` 상단 import 블록에 추가(알파벳 순서 유지):

```kotlin
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jjundev.oneclickeng.ui.component.OneClickUpdateGate
```

(`android.app.Activity`와 `androidx.compose.ui.platform.LocalContext`는 이미 import돼 있다 — `AppRoot.kt:3`, `AppRoot.kt:17`.)

- [ ] **Step 2: `AppRoot` 함수 본문 최상단에 업데이트 게이트 삽입**

기존:

```kotlin
@Composable
fun AppRoot(
    startRoute: String? = null,
    pendingNav: String? = null,
    onNavConsumed: () -> Unit = {},
) {
    val appViewModel = hiltViewModel<AppViewModel>()
    val bootState by appViewModel.uiState.collectAsStateWithLifecycle()
    val isOnline by appViewModel.isOnline.collectAsStateWithLifecycle()
```

교체 후:

```kotlin
@Composable
fun AppRoot(
    startRoute: String? = null,
    pendingNav: String? = null,
    onNavConsumed: () -> Unit = {},
) {
    val updateGateViewModel = hiltViewModel<UpdateGateViewModel>()
    val updateGateState by updateGateViewModel.state.collectAsStateWithLifecycle()
    val updateContext = LocalContext.current
    val updateLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            // 사용자가 강제 업데이트를 취소/실패시키면 archive MainActivity 와 동일하게 앱을 종료한다.
            if (result.resultCode != Activity.RESULT_OK) {
                (updateContext as? Activity)?.finish()
            }
        }
    LaunchedEffect(updateGateState) {
        if (updateGateState == UpdateGateState.Required) {
            updateGateViewModel.launchUpdate(updateLauncher)
        }
    }
    val updateLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(updateLifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    updateGateViewModel.onResumeCheck()
                }
            }
        updateLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { updateLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    when (updateGateState) {
        UpdateGateState.Checking -> {
            BootSplash()
            return
        }
        UpdateGateState.Required -> {
            OneClickUpdateGate(onUpdateNow = { updateGateViewModel.launchUpdate(updateLauncher) })
            return
        }
        UpdateGateState.NotRequired -> Unit
    }

    val appViewModel = hiltViewModel<AppViewModel>()
    val bootState by appViewModel.uiState.collectAsStateWithLifecycle()
    val isOnline by appViewModel.isOnline.collectAsStateWithLifecycle()
```

(변수명을 `updateContext`/`updateLifecycleOwner`로 접두사를 붙인 이유: 기존 `MainTabsScaffold` 내부의 `val activity = LocalContext.current as? Activity` 등 아래쪽 지역 변수와 이름이 겹치지 않게 하기 위함 — 실제로는 서로 다른 컴포저블 스코프라 충돌하지 않지만, 가독성을 위해 구분한다.)

- [ ] **Step 3: 검증**

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL. 기존 `AppViewModelTest`/`HomeSituationTapTest` 등 회귀 없음 — `AppRoot.kt` 자체를 구동하는 자동 테스트는 이 코드베이스에 없다(Global Constraints 참조, 스테이트풀 `hiltViewModel()` 배선은 관례상 수동 검증 범위). Task 5가 실기기 검증을 커버한다.

- [ ] **Step 4: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt
git commit -m "feat(update): wire the forced-update gate into AppRoot"
```

---

## Task 5: Play Console 수동 QA (코드 변경 없음)

Play Core의 IMMEDIATE 업데이트 플로우는 실제 Play Console 릴리스 트랙과 실기기에서만 트리거된다 — 자동화 테스트로 종단 검증이 불가능하다(Global Constraints 참조). 아래는 검증을 위한 구체적 수동 절차다. **전제조건: `android/app/build.gradle.kts`의 `release` 빌드 타입에 서명 설정(`signingConfig`)이 아직 없다 — 이 태스크를 실행하려면 먼저 릴리스 서명 키/설정을 준비해야 하며, 이는 이 플랜 범위 밖의 별도 결정이 필요하다.**

- [ ] **Step 1:** `android/app/build.gradle.kts`의 `defaultConfig.versionCode`를 현재 값(1)에서 `2`로 올린 커밋을 하나 만든다(QA 전용 임시 커밋, 이 플랜의 정식 태스크가 아니므로 QA 종료 후 되돌린다).

- [ ] **Step 2:** 서명된 릴리스 AAB를 빌드해 Play Console → 내부 테스트(Internal testing) 트랙에 업로드하고 배포한다.

- [ ] **Step 3:** 테스트 기기에 **이전(더 낮은) versionCode** 빌드를 설치한다(내부 테스트 옵트인 링크로 이전 배포본을 받거나, `versionCode=1` APK를 직접 설치).

- [ ] **Step 4:** `versionCode`를 `3`으로 올려 새 빌드를 같은 내부 테스트 트랙에 업로드·배포한다.

- [ ] **Step 5:** Step 3 기기에서 Play 스토어 앱을 한 번 열어 스토어 메타데이터를 동기화한 뒤, OCE 앱을 실행한다. `UpdateGateViewModel.isImmediateUpdateRequired()`가 true를 반환해 Play의 네이티브 업데이트 시트가 즉시 뜨는지 확인한다.

- [ ] **Step 6:** Play 시트에서 "취소"를 탭한다 → 앱이 종료되는지 확인한다(archive의 강제 종료 동작과 동일).

- [ ] **Step 7:** 앱을 다시 열고 이번엔 "업데이트"를 탭한다 → 업데이트가 설치되고 앱이 자동으로 재개되는지 확인한다.

- [ ] **Step 8:** 별도로, 다운로드 진행 중(설치 완료 전)에 앱을 백그라운드로 보냈다가 다시 포그라운드로 가져온다 → `onResumeCheck()`가 `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS` 경로로 업데이트 플로우를 재개시키는지 확인한다.

- [ ] **Step 9:** QA가 끝나면 Step 1의 `versionCode` 임시 변경을 되돌린다(원래 값으로 복원하는 별도 커밋, 또는 QA 커밋을 되돌리는 revert).

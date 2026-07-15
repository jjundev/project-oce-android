# 기록 탭 재진입 시 저장 카드 갱신 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 저장한 카드가 앱 재시작 없이 기록 탭으로 돌아오는 즉시 현재 선택된 카드 목록에 반영되도록 한다.

**Architecture:** 기존의 `SavedCardQuerySource.page()` 1회성 커서 조회와 탭별 누적 상태는 유지한다. `RecordsViewModel.refresh()`가 현재 선택된 카드 타입의 목록과 커서를 초기화한 뒤 첫 페이지를 다시 읽고, retained ViewModel의 `refreshOnResume()`가 최초 resume를 소비한 뒤 재진입마다 이를 호출한다. `RecordsScreen`은 기록 destination의 lifecycle이 `ON_RESUME`에 도달할 때마다 `refreshOnResume()`을 호출하며, 초기 로드가 진행 중일 때는 새 조회를 시작하지 않는다.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Lifecycle, Navigation Compose, Kotlin Coroutines, JUnit 4, Robolectric.

## Global Constraints

- 기록 탭으로 전환하는 경우에 실시간 데이터를 불러온다.
- 기존 Firestore 저장 카드 스키마, `SavedCardQuerySource` 커서 페이지네이션, 오프라인 캐시 동작, 삭제의 낙관적 UI 동작은 변경하지 않는다.
- 새 의존성, Firestore 리스너, 데이터 마이그레이션, 분석 이벤트는 추가하지 않는다.
- Android 검증은 저장소 규약에 따라 `scripts/verify-android.sh`를 통해 실행한다.

---

## 조사 결과와 파일 구조

현재 `RecordsViewModel`은 `init`에서 기본 `EXPRESSION` 탭의 `loadFirstPage()`를 한 번 호출하고, 각 카드 타입의 `TypeState`를 ViewModel 생명주기 동안 보존한다. `OceBottomNav.navigateToTab()`은 `saveState=true`, `restoreState=true`와 `launchSingleTop=true`를 사용하므로 기록 탭을 떠나도 이 ViewModel의 목록이 유지된다. 이 때문에 저장 직후 기록 탭으로 돌아와도 기존 목록이 그대로 보이는 것이 현재 증상의 원인이다.

현재 코드와 책임을 기준으로 다음 파일만 수정한다.

- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModel.kt` — 현재 선택된 카드 타입의 첫 페이지를 버리고 다시 읽는 `refresh()` 계약과 동시 로드 가드.
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreen.kt` — 기록 destination이 재개될 때 ViewModel을 갱신하는 lifecycle effect. 기존 Settings 탭의 `LifecycleEventObserver` 패턴을 따른다.
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModelTest.kt` — refresh가 첫 페이지 커서를 초기화하고 최신 fake 데이터를 표시하는 단위 테스트 및 fake query 관찰 지점.
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreenRefreshTest.kt` — 실제 `RecordsScreen(viewModel = ...)`을 렌더링해 최초 resume 중복 조회 방지와 lifecycle 재진입 refresh를 검증하는 Compose/Robolectric 테스트.

실행 수준의 설계 분기는 없다. 이 저장소가 이미 `SettingsScreen`에서 destination lifecycle의 `ON_RESUME`를 관찰하고 있고, 기록 쿼리도 명시적으로 1회성 `.get()` + cursor 구조이므로, NavHost에 별도 route 전달값을 추가하거나 연속 Firestore snapshot listener로 바꾸지 않는다.

### Task 1: ViewModel에 현재 탭 refresh 계약 추가

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModel.kt:20-132`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModelTest.kt:1-203`

**Interfaces:**
- Consumes: existing `SavedCardQuerySource.page(cardType: CardType, after: DocumentSnapshot?, limit: Int): SavedCardPage`.
- Produces: `RecordsViewModel.refresh(): Unit`와 `RecordsViewModel.refreshOnResume(): Unit`. `refresh()`는 현재 선택된 `CardType`의 카드 목록을 비우고 cursor/end 상태를 초기화한 뒤 `page(cardType, after = null)`을 한 번 요청한다. `refreshOnResume()`는 retained ViewModel 수명 동안 최초 resume 콜백은 소비하고, 이후 resume부터 `refresh()`를 호출한다. 두 메서드 모두 해당 타입의 요청이 이미 `loading=true`이면 새 요청을 만들지 않는다.

- [ ] **Step 1: 새 refresh 동작을 고정하는 실패 테스트를 먼저 작성한다**

`RecordsViewModelTest.kt`에 다음 테스트를 추가한다. 기존 `expr`, `vm`, `advanceUntilIdle` 헬퍼를 재사용하고, 첫 조회 뒤 fake query의 데이터를 바꾼 다음 refresh가 최신 첫 페이지를 읽는지 확인한다.

```kotlin
@Test
fun `refresh reloads the first page for the currently selected tab`() =
    runTest(dispatcher) {
        val query = FakeQuerySource(mapOf(CardType.EXPRESSION to listOf(expr("old"))))
        val viewModel = vm(query = query)
        advanceUntilIdle()

        query.replace(CardType.EXPRESSION, listOf(expr("new"), expr("old")))
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf("new", "old"), viewModel.uiState.value.cards.map { it.cardId })
        assertEquals(
            2,
            query.requests.count { (cardType, after) ->
                cardType == CardType.EXPRESSION && after == null
            },
        )
    }
```

같은 테스트 파일에 진행 중인 최초 조회와 refresh가 겹치지 않는다는 회귀 테스트도 추가한다. `init`의 `loadPage()`가 `loading=true`를 동기적으로 먼저 기록하고 실제 fake 호출은 테스트 dispatcher에 예약하므로, `refresh()`가 즉시 반환하는지 검증할 수 있다.

```kotlin
@Test
fun `refresh is a no-op while the current tab is loading`() =
    runTest(dispatcher) {
        val query = FakeQuerySource(mapOf(CardType.EXPRESSION to listOf(expr("only"))))
        val viewModel = vm(query = query)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(1, query.requests.count { it.first == CardType.EXPRESSION && it.second == null })
        assertEquals(listOf("only"), viewModel.uiState.value.cards.map { it.cardId })
    }
```

retained ViewModel 게이트가 최초 resume와 재진입을 구분하는지도 같은 파일에 추가한다.

```kotlin
@Test
fun `first resume uses the init result and second resume refreshes`() =
    runTest(dispatcher) {
        val query = FakeQuerySource(mapOf(CardType.EXPRESSION to listOf(expr("old"))))
        val viewModel = vm(query = query)

        viewModel.refreshOnResume()
        advanceUntilIdle()
        assertEquals(1, query.requests.count { it.first == CardType.EXPRESSION && it.second == null })

        query.replace(CardType.EXPRESSION, listOf(expr("new")))
        viewModel.refreshOnResume()
        advanceUntilIdle()

        assertEquals(2, query.requests.count { it.first == CardType.EXPRESSION && it.second == null })
        assertEquals(listOf("new"), viewModel.uiState.value.cards.map { it.cardId })
    }
```

`FakeQuerySource`를 다음처럼 바꿔 첫 페이지 요청 횟수와 mutable fixture를 관찰할 수 있게 한다. 기존 호출부의 생성자 형태는 유지한다. `refresh()` 구현은 코드 블록에 명시된 대로 `cursor = null`을 저장한 뒤 `loadFirstPage()`를 호출해야 하며, 두 refresh 테스트는 새 요청의 `after == null`을 단언한다.

```kotlin
private class FakeQuerySource(
    initialByType: Map<CardType, List<SavedCardEntry>> = emptyMap(),
) : SavedCardQuerySource {
    private val byType = initialByType.toMutableMap()
    val requests = mutableListOf<Pair<CardType, DocumentSnapshot?>>()

    fun replace(
        cardType: CardType,
        entries: List<SavedCardEntry>,
    ) {
        byType[cardType] = entries
    }

    override suspend fun page(
        cardType: CardType,
        after: DocumentSnapshot?,
        limit: Int,
    ): SavedCardPage {
        requests += cardType to after
        return SavedCardPage(
            entries = byType[cardType].orEmpty(),
            cursor = null,
            endReached = true,
        )
    }
}
```

- [ ] **Step 2: 테스트가 현재 코드에서 실패하는지 확인한다**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*RecordsViewModelTest*'
```

Expected: FAIL during test compilation with `Unresolved reference: refresh` at `viewModel.refresh()`; the existing ViewModel has no public refresh contract.

- [ ] **Step 3: 현재 선택 타입만 초기화하고 첫 페이지를 다시 읽는 최소 구현을 추가한다**

`RecordsViewModel`의 `loadMore()`와 `deleteCard()` 사이에 다음 메서드를 추가한다. `loading` 가드는 초기 `init` 조회 또는 이미 진행 중인 페이지 조회와 재진입 refresh가 겹치는 것을 막는다. `loadFirstPage()`가 `loaded=true`를 설정한 뒤 `loadPage(after = null)`을 호출하므로, refresh에서도 동일한 로딩/상태 발행 경로를 재사용한다.

```kotlin
/** 기록 탭 재진입 시 현재 세그먼트를 첫 페이지부터 다시 읽는다. 진행 중인 조회와는 중복 실행하지 않는다. */
fun refresh() {
    val cardType = selected
    val state = typeStates.getValue(cardType)
    if (state.loading) return

    typeStates[cardType] =
        state.copy(
            cards = emptyList(),
            cursor = null,
            endReached = false,
        )
    loadFirstPage(cardType)
}
```

`selected`와 함께 retained ViewModel 필드에 최초 resume 게이트를 두고, 화면 lifecycle effect가 매번 호출할 진입 메서드를 추가한다. 이 게이트를 Compose `remember`에 두지 않아 기록 destination이 dispose/recreate되거나 Navigation state를 복원해도 최초 진입과 재진입을 구분한다.

```kotlin
private var hasResumed = false

/** 기록 destination의 최초 resume는 init 조회가 담당하므로 소비하고, 이후 resume마다 최신 목록을 읽는다. */
fun refreshOnResume() {
    if (!hasResumed) {
        hasResumed = true
        return
    }
    refresh()
}
```

`RecordsViewModel`의 클래스 KDoc도 기존의 “탭별 누적 유지” 설명에 다음 문장을 덧붙여 refresh가 현재 탭만 재설정하고 다른 타입의 누적 상태는 유지한다는 계약을 기록한다.

```kotlin
 * 기록 destination이 다시 재개되면 [refresh]가 현재 선택 타입만 첫 페이지부터 재조회하고, 다른 타입의 누적 상태는 보존한다.
```

- [ ] **Step 4: ViewModel 테스트와 기존 기록 테스트를 통과시킨다**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*RecordsViewModelTest*' --tests '*RecordsDeleteDialogTest*'
```

Expected: BUILD SUCCESSFUL; refresh 테스트와 기존 초기 로드·삭제·탭 전환·통계·복습 배너 테스트가 모두 PASS한다. refresh 중에는 `cards`가 일시적으로 비워지고 `loading=true`가 되며, 조회 완료 후 최신 첫 페이지가 노출된다.

- [ ] **Step 5: 변경을 독립적으로 커밋한다**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModel.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModelTest.kt
git commit -m "feat(records): add current-tab refresh"
```

### Task 2: 기록 destination 재개 시 refresh 연결

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreen.kt:1-80`
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreenRefreshTest.kt`

**Interfaces:**
- Consumes: `RecordsViewModel.refresh(): Unit` from Task 1 and the destination-scoped `LocalLifecycleOwner`.
- Produces: internal composable `RecordsResumeEffect(onResume: () -> Unit)` that forwards every `Lifecycle.Event.ON_RESUME` from the current records destination and unregisters its observer on disposal. The retained ViewModel, not Compose state, decides whether the callback is the initial resume or a refresh-triggering re-entry.

- [ ] **Step 1: 실제 RecordsScreen wiring을 고정하는 실패 테스트를 먼저 작성한다**

`RecordsScreenRefreshTest.kt`를 다음 내용으로 생성한다. 테스트는 독립 effect만 호출하지 않고 실제 `RecordsScreen(viewModel = viewModel)`을 렌더링한다. 따라서 `RecordsScreen`에서 `RecordsResumeEffect(onResume = viewModel::refreshOnResume)` 연결을 빠뜨리면 두 번째 query와 최신 카드 assertion이 실패한다. 테스트는 최초 resume에서 `RecordsViewModel.init`의 한 번 조회만 남는지와 화면 dispose/recreate 이후에도 retained ViewModel이 재진입을 refresh로 처리하는지 확인한다.

```kotlin
package com.jjundev.oneclickeng.feature.records

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.firestore.DocumentSnapshot
import com.jjundev.oneclickeng.feature.review.FakeReviewSource
import com.jjundev.oneclickeng.feature.review.data.ReviewClock
import com.jjundev.oneclickeng.feature.session.saved.CardType
import com.jjundev.oneclickeng.feature.session.saved.FakeSavedCardRepository
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class RecordsScreenRefreshTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `screen refreshes cards when records destination reenters`() {
        val query = RecordingQuerySource()
        val viewModel = recordsViewModel(query)
        val owner = TestLifecycleOwner()
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                OceTheme { RecordsScreen(viewModel = viewModel) }
            }
        }

        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        composeRule.waitForIdle()
        assertEquals(1, query.requests.size)
        assertEquals(listOf("old"), viewModel.uiState.value.cards.map { it.cardId })

        query.entries = listOf(expression("new"), expression("old"))
        composeRule.setContent { }
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                OceTheme { RecordsScreen(viewModel = viewModel) }
            }
        }
        composeRule.waitForIdle()

        assertEquals(2, query.requests.size)
        assertEquals(listOf("new", "old"), viewModel.uiState.value.cards.map { it.cardId })
    }

    private fun recordsViewModel(query: SavedCardQuerySource) =
        RecordsViewModel(
            querySource = query,
            savedCardRepository = FakeSavedCardRepository(),
            lifetimeStatsSource = object : LifetimeStatsSource {
                override suspend fun lifetime(): LifetimeStats? = null
            },
            analytics = object : HistoryAnalytics {
                override fun tabView(cardType: CardType) = Unit
                override fun tabSwitch(cardType: CardType) = Unit
                override fun deleteCard(cardType: CardType, undone: Boolean) = Unit
            },
            countUpGate = HistoryCountUpGate(),
            reviewSource = FakeReviewSource(),
            reviewClock = object : ReviewClock {
                override fun nowMs(): Long = 0L
            },
        )

    private fun expression(id: String) =
        SavedCardEntry(
            cardId = id,
            card = SavedCard.Expression(
                type = "natural",
                koreanPrompt = "",
                before = "",
                after = "after-$id",
                explanation = "",
            ),
        )

    private inner class RecordingQuerySource : SavedCardQuerySource {
        var entries = listOf(expression("old"))
        val requests = mutableListOf<DocumentSnapshot?>()

        override suspend fun page(
            cardType: CardType,
            after: DocumentSnapshot?,
            limit: Int,
        ): SavedCardPage {
            requests += after
            return SavedCardPage(entries = entries, cursor = null, endReached = true)
        }
    }

    private class TestLifecycleOwner : LifecycleOwner {
        override val lifecycle: LifecycleRegistry = LifecycleRegistry(this)
    }
}
```

- [ ] **Step 2: 테스트가 현재 코드에서 실패하는지 확인한다**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*RecordsScreenRefreshTest*'
```

Expected: test compilation succeeds because `RecordsScreen` already exists, then the test FAILs at runtime because `query.requests.size` remains `1` after screen recreation and the old card list remains visible; this is the missing screen-to-ViewModel lifecycle wiring.

- [ ] **Step 3: 기존 Settings lifecycle 패턴으로 effect를 구현하고 RecordsScreen에 설치한다**

`RecordsScreen.kt` import에 다음을 추가한다. `DisposableEffect`, `getValue`, `rememberUpdatedState`, `Lifecycle`, `LifecycleEventObserver`, `LocalLifecycleOwner`는 기존 import와 중복되지 않게 정리한다.

```kotlin
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
```

`RecordsScreen`에서 state를 수집한 직후 다음 호출을 추가한다.

```kotlin
RecordsResumeEffect(onResume = viewModel::refreshOnResume)
```

같은 파일의 `RecordsScreen` 아래에 다음 internal composable을 추가한다. observer는 첫 resume를 임의로 건너뛰지 않고 매 resume를 최신 callback으로 전달한다. 최초 resume 소비 여부는 retained ViewModel의 `refreshOnResume()`가 관리하므로 화면 재생성에도 유지된다. `rememberUpdatedState`로 화면 recomposition이 observer를 불필요하게 다시 등록하지 않으면서 최신 callback을 사용하고, `DisposableEffect` 종료 시 observer를 제거한다.

```kotlin
@Composable
internal fun RecordsResumeEffect(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResume by rememberUpdatedState(onResume)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
```

이 effect는 기록 화면의 모든 `ON_RESUME`를 ViewModel로 전달한다. retained ViewModel의 최초 `refreshOnResume()` 호출은 `init`의 최초 조회를 그대로 사용하고, 이후 탭 재진입·화면 재생성·앱 복귀에서는 현재 선택 타입의 첫 페이지를 새로 로드한다. 이후 resume가 진행 중인 조회와 우연히 겹치는 경우에는 Task 1의 `loading=true` 가드가 두 번째 안전망이 된다.

- [ ] **Step 4: lifecycle effect와 전체 기록 회귀 테스트를 통과시킨다**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*RecordsScreenRefreshTest*' --tests '*RecordsViewModelTest*' --tests '*RecordsScreenScreenshotTest*' --tests '*RecordsTitleBarTest*' --tests '*RecordsDeleteDialogTest*'
```

Expected: BUILD SUCCESSFUL; 실제 `RecordsScreen` lifecycle test는 최초 resume에서 기존 init 조회 1회만 유지하고, 화면 dispose/recreate 시 query가 한 번 더 호출되며 최신 카드가 표시된다. 기존 화면/삭제/제목/스크린샷 테스트도 PASS한다. 화면 외형 변경은 없으므로 새 golden 이미지나 문자열은 추가하지 않는다.

- [ ] **Step 5: 변경을 독립적으로 커밋한다**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreen.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreenRefreshTest.kt
git commit -m "feat(records): refresh cards when tab resumes"
```

## 최종 검증

- [ ] **Spec coverage:** 저장 카드가 기록 탭 재진입 시 반영되어야 한다는 요구는 Task 1의 first-page refresh와 Task 2의 destination `ON_RESUME` 연결로 완전히 커버된다.
- [ ] **No placeholder scan:** plan 전체에서 TODO/TBD/“나중에 구현” 같은 실행 placeholder를 사용하지 않았고, 각 변경 단계에 파일·코드·명령·예상 결과를 기재했다.
- [ ] **Type consistency:** `RecordsViewModel.refresh(): Unit`과 `refreshOnResume(): Unit`이 Task 1에서 정의되고 Task 2의 `viewModel::refreshOnResume`에서 동일하게 소비된다. `RecordsResumeEffect(onResume: () -> Unit)`은 Task 2의 테스트와 화면 연결에서 같은 시그니처를 사용한다.
- [ ] **Full Android verification:** 두 커밋 이후 다음 명령으로 저장소 기본 검증을 실행한다.

```bash
scripts/verify-android.sh
```

Expected: script가 보고하는 기존 환경/정적 분석 제약을 제외하고 Android unit test 및 androidTest compile을 포함한 기본 검증이 성공한다. 실패 시 새 변경으로 인한 실패인지 출력의 task와 테스트 이름을 기준으로 확인한다.

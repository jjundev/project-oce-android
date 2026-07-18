# 요약 화면 저장 기본값 설정 토글 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 세션 요약 화면의 "자연스러운 표현"·"새 단어" 카드는 지금 항상 "저장하지 않음"으로 시작해 사용자가 직접 눌러야 저장된다. 설정 화면에 이 기본값을 "저장"/"저장하지 않음"으로 전환할 수 있는 토글을 추가한다. 켜면 요약 SSE로 카드가 도착하는 즉시 전부 저장 처리되고, 꺼두면(기본값) 지금과 동일하게 동작한다.

**Architecture:** 새 불리언 설정(`SummarySaveSettings.saveByDefault`, 기본 false)을 전용 Jetpack DataStore(`SummarySaveSettingsRepository`)에 저장한다. `SettingsViewModel`이 이 저장소를 읽고/써서 설정 화면의 "데이터 관리" 섹션에 스위치 행을 추가한다. `SummaryCoordinator`(요약 화면의 상태 머신)는 세션 시작 시 이 값을 1회 읽어 캐시해두고, 표현/단어 카드가 SSE로 도착하는 시점(`onEvent`)에 값이 true면 도착한 카드 전부를 기존 수동 저장과 **완전히 동일한 경로**(`SavedCardRepository.save()` + 결정적 `cardId`)로 저장한다. 두 저장 경로(자동/수동)가 같은 `cardId`를 쓰므로, 자동 저장된 카드도 이후 수동으로 토글해서 해제할 수 있다. 북마크 문장(SENTENCE)은 이미 기본 저장 상태라 이 기능의 범위 밖이다(사용자가 명시한 대상은 "자연스러운 표현과 단어들"뿐).

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt, Jetpack DataStore (Preferences), JUnit4, kotlinx-coroutines-test.

## Global Constraints

- 검증은 반드시 `scripts/verify-android.sh` 로 돌린다 — 워크트리 전용 `GRADLE_USER_HOME` 격리 없이는 캐시 오염으로 컴파일 에러도 통과된 것처럼 보일 수 있다(`docs/agents/android-verification.md`).
- 테스트 더블은 이 레포 관례대로 **mockk 미사용, 손수 만든 fake**만 쓴다(`SettingsViewModelTest.kt`, `SummaryCoordinatorTest.kt` 기존 관례).
- 자동 저장은 **표현(EXPRESSION)과 단어(WORD) 카드에만** 적용한다. 북마크 문장(SENTENCE)은 이미 기본 저장 + opt-out 방식이라 건드리지 않는다(사용자가 "자연스러운 표현과 단어들"만 언급).
- 자동 저장은 `SavedCardRepository.save()`를 그대로 재사용하는 **실제 영속화**다(단순히 토글 UI만 채워진 것처럼 보이는 게 아니라, 기록 탭에도 즉시 나타난다) — 이 저장소는 `ADR-0001`(결정적 cardId)과 `ADR-0002`(Firestore 네이티브 오프라인)를 이미 만족하므로 새 ADR은 필요 없다.
- Korean UX 카피는 해요체·비난 없는 톤을 따른다(`strings.xml`의 `<!-- 설정 (M3-09) · 해요체·비난 없는 톤 -->` 컨벤션).
- ktlintMainSourceSetCheck는 기본 검증 세트에서 이미 제외되어 있다(master 선존재 위반) — 이 플랜의 태스크들도 이를 별도로 걷어낼 필요 없다.

---

### Task 1: `SummarySaveSettingsRepository` — 저장 기본값 DataStore

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/settings/SummarySaveSettings.kt`
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/settings/SummarySaveSettingsRepository.kt`
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/settings/SummarySaveSettingsModule.kt`
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/settings/SummarySaveSettingsRepositoryTest.kt`
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/settings/FakeSummarySaveSettingsRepository.kt`

**Interfaces:**
- Produces: `data class SummarySaveSettings(val saveByDefault: Boolean = false)`.
- Produces: `interface SummarySaveSettingsRepository { val settings: Flow<SummarySaveSettings>; suspend fun current(): SummarySaveSettings; suspend fun setSaveByDefault(saveByDefault: Boolean) }`.
- Produces: `class DataStoreSummarySaveSettingsRepository @Inject constructor(@SummarySavePrefs dataStore: DataStore<Preferences>) : SummarySaveSettingsRepository` — bound to the interface via Hilt.
- Produces (test-only): `class FakeSummarySaveSettingsRepository(initial: Boolean = false) : SummarySaveSettingsRepository` with `fun currentValue(): Boolean` helper — used by Task 2 and Task 3.

- [ ] **Step 1: Write the failing repository test**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/settings/SummarySaveSettingsRepositoryTest.kt`:

```kotlin
package com.jjundev.oneclickeng.core.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 실제 파일 백드 DataStore 를 JVM 에서 구동(Robolectric 불필요) — `ReminderRepositoryTest` 와 동일 패턴.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SummarySaveSettingsRepositoryTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun newRepo(scope: CoroutineScope): DataStoreSummarySaveSettingsRepository {
        val dataStore =
            PreferenceDataStoreFactory.create(scope = scope) {
                File(tmpFolder.newFolder(), "summary_save.preferences_pb")
            }
        return DataStoreSummarySaveSettingsRepository(dataStore)
    }

    @Test
    fun `defaults to save-by-default false`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val repo = newRepo(scope)

            assertFalse(repo.current().saveByDefault)

            scope.cancel()
        }

    @Test
    fun `setSaveByDefault persists and is reflected in both current() and the settings flow`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val repo = newRepo(scope)

            repo.setSaveByDefault(true)

            assertEquals(true, repo.current().saveByDefault)
            assertEquals(true, repo.settings.first().saveByDefault)

            scope.cancel()
        }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SummarySaveSettingsRepositoryTest*'`
Expected: FAIL — compilation error, `SummarySaveSettings`/`DataStoreSummarySaveSettingsRepository` unresolved.

- [ ] **Step 3: Implement the domain model**

Create `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/settings/SummarySaveSettings.kt`:

```kotlin
package com.jjundev.oneclickeng.core.settings

/**
 * 세션 요약 화면의 새 표현/단어 카드 저장 기본값. 기본 false — 켜기 전까지는 현재 동작(사용자가 직접
 * 눌러야 저장)을 그대로 유지한다.
 */
data class SummarySaveSettings(
    val saveByDefault: Boolean = false,
)
```

- [ ] **Step 4: Implement the repository interface + DataStore impl**

Create `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/settings/SummarySaveSettingsRepository.kt`:

```kotlin
package com.jjundev.oneclickeng.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 세션 요약 화면의 새 표현/단어 카드 저장 기본값을 읽고 쓴다. 설정 화면(쓰기)과
 * [com.jjundev.oneclickeng.feature.session.summary.SummaryCoordinator](세션 시작 시 1회 읽기) 양쪽이 이
 * 인터페이스를 직접 주입한다(`TtsSettingsRepository` 와 동일한 cross-feature 공유 패턴 — 별도
 * orchestrator 레이어를 두지 않는다).
 */
interface SummarySaveSettingsRepository {
    /** 라이브 설정 스트림(설정 화면 구독용). */
    val settings: Flow<SummarySaveSettings>

    /** 요약 코디네이터가 세션 시작 시 1회 읽는 스냅샷. */
    suspend fun current(): SummarySaveSettings

    suspend fun setSaveByDefault(saveByDefault: Boolean)
}

/** DataStore 구현. 누락 키는 [SummarySaveSettings] 기본값(false)으로 폴백. */
@Singleton
class DataStoreSummarySaveSettingsRepository
    @Inject
    constructor(
        @SummarySavePrefs private val dataStore: DataStore<Preferences>,
    ) : SummarySaveSettingsRepository {
        override val settings: Flow<SummarySaveSettings> = dataStore.data.map(::toSettings)

        override suspend fun current(): SummarySaveSettings = toSettings(dataStore.data.first())

        override suspend fun setSaveByDefault(saveByDefault: Boolean) {
            dataStore.edit { it[KEY_SAVE_BY_DEFAULT] = saveByDefault }
        }

        private fun toSettings(prefs: Preferences): SummarySaveSettings =
            SummarySaveSettings(saveByDefault = prefs[KEY_SAVE_BY_DEFAULT] ?: false)

        companion object {
            val KEY_SAVE_BY_DEFAULT = booleanPreferencesKey("summary_save_by_default")
        }
    }
```

- [ ] **Step 5: Implement the Hilt DI module**

Create `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/settings/SummarySaveSettingsModule.kt`:

```kotlin
package com.jjundev.oneclickeng.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * 요약 저장 기본값 전용 DataStore 를 구분하는 한정자. `TtsProvideModule` 이 이미 무한정자
 * `DataStore<Preferences>`(tts_settings)를 제공하므로, 중복 바인딩을 피하려면 이 저장소도 한정된 인스턴스를
 * 받아야 한다(기능별 1 DataStore 관례 — `ReminderModule` 미러).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SummarySavePrefs

private val Context.summarySaveDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "summary_save_settings")

@Module
@InstallIn(SingletonComponent::class)
abstract class SummarySaveSettingsBindModule {
    @Binds
    @Singleton
    abstract fun bindSummarySaveSettingsRepository(
        impl: DataStoreSummarySaveSettingsRepository,
    ): SummarySaveSettingsRepository
}

@Module
@InstallIn(SingletonComponent::class)
object SummarySaveSettingsProvideModule {
    @Provides
    @Singleton
    @SummarySavePrefs
    fun provideSummarySaveDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.summarySaveDataStore
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SummarySaveSettingsRepositoryTest*'`
Expected: PASS (2 tests).

- [ ] **Step 7: Add the shared test fake**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/settings/FakeSummarySaveSettingsRepository.kt`:

```kotlin
package com.jjundev.oneclickeng.core.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [SummaryCoordinatorTest]와 [SettingsViewModelTest] 양쪽에서 공유하는 페이크. Firestore/DataStore 없이
 * 값을 메모리에 들고 있으며, [settings] Flow 는 [setSaveByDefault] 쓰기를 즉시 반영한다(리액티브 조합
 * 검증용).
 */
class FakeSummarySaveSettingsRepository(
    initial: Boolean = false,
) : SummarySaveSettingsRepository {
    private val state = MutableStateFlow(SummarySaveSettings(initial))

    override val settings: Flow<SummarySaveSettings> = state

    override suspend fun current(): SummarySaveSettings = state.value

    override suspend fun setSaveByDefault(saveByDefault: Boolean) {
        state.value = SummarySaveSettings(saveByDefault)
    }

    fun currentValue(): Boolean = state.value.saveByDefault
}
```

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/core/settings/SummarySaveSettings.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/core/settings/SummarySaveSettingsRepository.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/core/settings/SummarySaveSettingsModule.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/core/settings/SummarySaveSettingsRepositoryTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/core/settings/FakeSummarySaveSettingsRepository.kt
git commit -m "feat(settings): add summary save-by-default DataStore repository"
```

---

### Task 2: `SummaryCoordinator` — 도착한 표현/단어 카드를 기본값에 따라 자동 저장

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinator.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinatorTest.kt`

**Interfaces:**
- Consumes: `SummarySaveSettingsRepository` (Task 1) — `suspend fun current(): SummarySaveSettings`.
- Consumes: `FakeSummarySaveSettingsRepository(initial: Boolean = false)` (Task 1, test-only) — `fun currentValue(): Boolean`.
- Consumes (existing): `SavedCardRepository.save(cardId: String, card: SavedCard)`, `SavedCardId.forSummary(sessionId, cardType, sourceIndex): String`, `ExpressionCard.toSavedCard(): SavedCard.Expression`, `WordCard.toSavedCard(): SavedCard.Word`.
- Produces: `SummaryCoordinator` constructor gains a new positional parameter `saveSettings: SummarySaveSettingsRepository` (inserted right after `savedCardRepository`, before `studytime`) — every caller must update.

- [ ] **Step 1: Write the failing tests**

In `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinatorTest.kt`, add the import and update the test factory to accept the new dependency:

```kotlin
import com.jjundev.oneclickeng.core.settings.FakeSummarySaveSettingsRepository
```

Replace the `coordinator(...)` factory function:

```kotlin
    @Suppress("LongParameterList") // 코디네이터 seam 을 그대로 반영하는 테스트 팩토리 — 기본값 오버라이드용.
    private fun coordinator(
        scope: CoroutineScope,
        stream: FakeSummaryStream,
        bookmarks: FakeBookmarkSource = FakeBookmarkSource(),
        ledger: FakeLedger = FakeLedger(),
        savedCards: FakeSavedCardRepository = FakeSavedCardRepository(),
        saveSettings: FakeSummarySaveSettingsRepository = FakeSummarySaveSettingsRepository(),
        studytime: FakeStudytimeRepository = FakeStudytimeRepository(),
    ) = SummaryCoordinator(stream, store(), bookmarks, ledger, savedCards, saveSettings, studytime, scope)
```

Then add three new test functions at the end of the class, right before the final closing `}`:

```kotlin
    @Test
    fun `expression and word cards are auto-saved for every index when save-by-default is enabled`() =
        runTest {
            val stream = FakeSummaryStream()
            val repo = FakeSavedCardRepository()
            val saveSettings = FakeSummarySaveSettingsRepository(initial = true)
            val coordinator = coordinator(coordScope(), stream, savedCards = repo, saveSettings = saveSettings)

            coordinator.begin() // sessionId=s1
            runCurrent()
            stream.push(SummaryEvent.Card.Expression(listOf(expressionItem(), expressionItem())))
            stream.push(SummaryEvent.Card.Word(listOf(wordItem(), wordItem())))
            stream.push(done())
            runCurrent()

            assertEquals(setOf(0, 1), coordinator.state.value.savedExprIndices)
            assertEquals(setOf(0, 1), coordinator.state.value.savedWordIndices)
            assertEquals(4, repo.saves.size)
            assertEquals(
                setOf("s1__EXPRESSION__0", "s1__EXPRESSION__1", "s1__WORD__0", "s1__WORD__1"),
                repo.saves.map { it.cardId }.toSet(),
            )
        }

    @Test
    fun `cards stay unsaved by default when save-by-default is disabled`() =
        runTest {
            val stream = FakeSummaryStream()
            val repo = FakeSavedCardRepository()
            val coordinator = coordinator(coordScope(), stream, savedCards = repo) // saveSettings defaults false

            coordinator.begin()
            runCurrent()
            stream.push(wordCard())
            stream.push(expressionCard())
            stream.push(done())
            runCurrent()

            assertTrue(coordinator.state.value.savedWordIndices.isEmpty())
            assertTrue(coordinator.state.value.savedExprIndices.isEmpty())
            assertTrue(repo.saves.isEmpty())
        }

    @Test
    fun `a card auto-saved by the default-on setting can still be manually un-saved`() =
        runTest {
            val stream = FakeSummaryStream()
            val repo = FakeSavedCardRepository()
            val saveSettings = FakeSummarySaveSettingsRepository(initial = true)
            val coordinator = coordinator(coordScope(), stream, savedCards = repo, saveSettings = saveSettings)

            coordinator.begin()
            runCurrent()
            stream.push(wordCard())
            stream.push(done())
            runCurrent()
            assertEquals(setOf(0), coordinator.state.value.savedWordIndices)

            coordinator.toggleSaveWord(0)
            runCurrent()

            assertTrue(coordinator.state.value.savedWordIndices.isEmpty())
            assertEquals(1, repo.deletes.size)
            assertEquals("s1__WORD__0", repo.deletes.first().cardId)
        }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SummaryCoordinatorTest*'`
Expected: FAIL — compilation error (`SummaryCoordinator` has no 8-arg constructor yet; `saveSettings` param unresolved).

- [ ] **Step 3: Wire `SummaryCoordinator` to read the setting and auto-save arriving cards**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinator.kt`, add the import:

```kotlin
import com.jjundev.oneclickeng.core.settings.SummarySaveSettingsRepository
```

Add the new constructor parameter right after `savedCardRepository` (before `studytime`):

```kotlin
    class SummaryCoordinator
        @Inject
        constructor(
            private val stream: SummaryStream,
            private val turnBuffer: SessionTurnBufferStore,
            private val bookmarkSource: BookmarkSource,
            private val ledger: CompletionLedger,
            private val savedCardRepository: SavedCardRepository,
            private val saveSettings: SummarySaveSettingsRepository,
            private val studytime: StudytimeRepository,
            private val scope: CoroutineScope,
        ) {
```

Add a new field next to the other 저장 카드 낙관적 UI 축 fields (after `private var unsavedBookmarkIds = emptySet<String>()`):

```kotlin
        // 저장 기본값(설정 화면). start() 가 [beginAttempt] 로 SSE 오픈을 이 값 확정 뒤로 미뤄, 카드 도착
        // 시점과의 레이스를 없앤다.
        private var saveByDefault = false
```

Replace the tail of `start()` — the block from `emit()` and `launchAttempt(sections = null)` down — with a call to a new `beginAttempt`:

```kotlin
            emit()
            beginAttempt(sessionId)
        }

        /**
         * 저장 기본값 설정을 1회 읽은 뒤 SSE 를 연다. 표현/단어 카드가 [onEvent] 로 도착하는 시점에
         * [saveByDefault] 가 이미 확정돼 있어야 자동 저장 여부를 놓치지 않는다 — 네트워크(SSE)보다 로컬
         * DataStore 읽기가 사실상 항상 먼저 끝나지만, 순서를 코드로 보장해 레이스를 없앤다. 대기 중 다시
         * [start]/[reset] 이 호출되면 sessionId 불일치로 무시한다([loadBookmarks]/[recordAccrual] 과 동일한
         * supersede 가드).
         */
        private fun beginAttempt(sessionId: String) {
            scope.launch {
                saveByDefault = saveSettings.current().saveByDefault
                if (sessionId == this@SummaryCoordinator.sessionId) launchAttempt(sections = null)
            }
        }
```

Update `reset()` to also clear the cached flag (add this line next to the other resets):

```kotlin
            savedWordIndices = emptySet()
            savedExprIndices = emptySet()
            unsavedBookmarkIds = emptySet()
            saveByDefault = false
            _state.value = EMPTY
        }
```

Update the two card branches in `onEvent` to auto-save when `saveByDefault` is true:

```kotlin
                is SummaryEvent.Card.Expression -> {
                    val items = event.items.take(MAX_EXPRESSIONS).map { it.toDomain() }
                    expression = SummarySectionState.Ready(items)
                    if (saveByDefault) autoSaveExpressions(items)
                    afterCard(token)
                }
                is SummaryEvent.Card.Word -> {
                    val items = event.items.take(MAX_WORDS).map { it.toDomain() }
                    word = SummarySectionState.Ready(items)
                    if (saveByDefault) autoSaveWords(items)
                    afterCard(token)
                }
```

Add the two new private helpers next to `toggleSaveBookmark` (after it, before `loadBookmarks`):

```kotlin
        /**
         * 저장 기본값 ON 일 때 표현 카드 도착 즉시 전 항목을 저장한다. [toggleSaveExpression] 과 같은
         * cardId 규칙([SavedCardId.forSummary])을 써서 이후 수동 토글(해제)이 정확히 같은 문서를 가리킨다.
         */
        private fun autoSaveExpressions(items: List<ExpressionCard>) {
            val id = sessionId ?: return
            savedExprIndices = items.indices.toSet()
            items.forEachIndexed { index, card ->
                savedCardRepository.save(SavedCardId.forSummary(id, CardType.EXPRESSION, index), card.toSavedCard())
            }
        }

        /** [autoSaveExpressions] 와 동형(WORD). */
        private fun autoSaveWords(items: List<WordCard>) {
            val id = sessionId ?: return
            savedWordIndices = items.indices.toSet()
            items.forEachIndexed { index, card ->
                savedCardRepository.save(SavedCardId.forSummary(id, CardType.WORD, index), card.toSavedCard())
            }
        }
```

- [ ] **Step 4: Run the full coordinator test suite to verify everything passes**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SummaryCoordinatorTest*'`
Expected: PASS — all tests in the file (the 3 new ones plus every pre-existing test, confirming no regression).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinator.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinatorTest.kt
git commit -m "feat(summary): auto-save arriving expression/word cards when save-by-default is on"
```

---

### Task 3: 설정 화면 — "데이터 관리" 섹션에 저장 기본값 스위치 추가

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsUiState.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsAnalytics.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsViewModel.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsViewModelTest.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt`

**Interfaces:**
- Consumes: `SummarySaveSettingsRepository` / `FakeSummarySaveSettingsRepository` (Task 1).
- Produces: `SettingsUiState.summarySaveByDefault: Boolean` (default `false`).
- Produces: `SettingsViewModel.onSummarySaveDefaultChange(saveByDefault: Boolean)`.
- Produces: `SettingsAnalytics.summarySaveDefaultToggled(enabled: Boolean)`.
- Produces: `SettingsContent(...)` gains a new required param `onSummarySaveDefaultChange: (Boolean) -> Unit` (inserted right after `onResetClick`).

- [ ] **Step 1: Write the failing ViewModel test**

In `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsViewModelTest.kt`, add the import:

```kotlin
import com.jjundev.oneclickeng.core.settings.FakeSummarySaveSettingsRepository
import com.jjundev.oneclickeng.core.settings.SummarySaveSettingsRepository
```

Add a new test function inside `SettingsViewModelTest`, after `resetMetrics failure ...`:

```kotlin
    @Test
    fun `onSummarySaveDefaultChange persists the toggle, updates state and logs once`() =
        runTest {
            val saveSettings = FakeSummarySaveSettingsRepository()
            val analytics = RecordingSettingsAnalytics()
            val model = settingsViewModel(FakeStudytimeRepository(), analytics, summarySaveSettings = saveSettings)
            advanceUntilIdle()
            assertEquals(false, model.uiState.value.summarySaveByDefault)

            model.onSummarySaveDefaultChange(true)
            advanceUntilIdle()

            assertEquals(true, model.uiState.value.summarySaveByDefault)
            assertEquals(true, saveSettings.currentValue())
            assertEquals(1, analytics.summarySaveDefaultToggledCount)
        }
```

Update the `settingsViewModel(...)` factory to accept and wire the new dependency:

```kotlin
    private fun settingsViewModel(
        studytimeRepository: StudytimeRepository,
        analytics: SettingsAnalytics,
        summarySaveSettings: SummarySaveSettingsRepository = FakeSummarySaveSettingsRepository(),
    ) = SettingsViewModel(
        authRepository = FakeAuth,
        profileRepository = FakeProfile,
        ttsSettings = FakeTtsSettingsRepository(),
        reminderOrchestrator = FakeReminderOrchestrator(),
        studytimeRepository = studytimeRepository,
        cardPurgeRepository = object : CardPurgeRepository {
            override suspend fun count(scope: PurgeScope): Int = 0
            override suspend fun purge(scope: PurgeScope): Int = 0
        },
        accountRepository = FakeAccount,
        pendingMergeStore = FakePendingMergeStore(),
        summarySaveSettings = summarySaveSettings,
        analytics = analytics,
    )
```

Update the two existing calls to `settingsViewModel(...)` (in `resetMetrics success ...` and `resetMetrics failure ...`) — no changes needed there since the new param has a default, but confirm they still compile as-is (they call `settingsViewModel(studytime, analytics)` positionally, which still resolves).

Update `RecordingSettingsAnalytics` to track the new event:

```kotlin
private class RecordingSettingsAnalytics : SettingsAnalytics {
    var metricsResetCount = 0
        private set
    var summarySaveDefaultToggledCount = 0
        private set

    override fun ttsQualityChanged(provider: String) = Unit

    override fun ttsSpeedChanged(speed: Float) = Unit

    override fun muteToggled(muted: Boolean) = Unit

    override fun metricsReset() {
        metricsResetCount++
    }

    override fun cardsPurged(
        scope: String,
        count: Int,
    ) = Unit

    override fun accountDeleted() = Unit

    override fun logout() = Unit

    override fun summarySaveDefaultToggled(enabled: Boolean) {
        summarySaveDefaultToggledCount++
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SettingsViewModelTest*'`
Expected: FAIL — compilation error (`SettingsUiState.summarySaveByDefault`, `SettingsViewModel` constructor param `summarySaveSettings`, `SettingsAnalytics.summarySaveDefaultToggled`, `onSummarySaveDefaultChange` all unresolved).

- [ ] **Step 3: Add the state field**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsUiState.kt`, add the new field to `SettingsUiState` (after `reminderMinute`):

```kotlin
data class SettingsUiState(
    val loading: Boolean = true,
    val nickname: String = "",
    val ttsQuality: TtsQuality = TtsQuality.DEVICE,
    val speechRate: Float = TtsSettings.DEFAULT_SPEECH_RATE,
    val ttsMuted: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = ReminderConfig.DEFAULT_HOUR,
    val reminderMinute: Int = ReminderConfig.DEFAULT_MINUTE,
    /** 요약 화면의 새 표현/단어 카드를 도착 즉시 자동 저장할지(데이터 관리 섹션 스위치). 기본 false. */
    val summarySaveByDefault: Boolean = false,
    val isGuest: Boolean = true,
```

- [ ] **Step 4: Add the analytics method**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsAnalytics.kt`, add to the interface (after `logout()`):

```kotlin
interface SettingsAnalytics {
    fun ttsQualityChanged(provider: String)

    fun ttsSpeedChanged(speed: Float)

    fun muteToggled(muted: Boolean)

    fun metricsReset()

    fun cardsPurged(
        scope: String,
        count: Int,
    )

    fun accountDeleted()

    fun logout()

    fun summarySaveDefaultToggled(enabled: Boolean)
}
```

Add the impl (after `logout()` in `FirebaseSettingsAnalytics`):

```kotlin
        override fun logout() {
            analytics.logEvent("logout", Bundle())
        }

        override fun summarySaveDefaultToggled(enabled: Boolean) {
            analytics.logEvent(
                "summary_save_default_toggled",
                Bundle().apply { putBoolean("enabled", enabled) },
            )
        }
    }
```

- [ ] **Step 5: Wire the ViewModel**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsViewModel.kt`, add the import:

```kotlin
import com.jjundev.oneclickeng.core.settings.SummarySaveSettingsRepository
```

Add the constructor param (right after `pendingMergeStore`, before `analytics`):

```kotlin
    class SettingsViewModel
        @Inject
        constructor(
            private val authRepository: AuthRepository,
            private val profileRepository: ProfileRepository,
            private val ttsSettings: TtsSettingsRepository,
            private val reminderOrchestrator: ReminderOrchestrator,
            private val studytimeRepository: StudytimeRepository,
            private val cardPurgeRepository: CardPurgeRepository,
            private val accountRepository: AccountRepository,
            private val pendingMergeStore: PendingMergeStore,
            private val summarySaveSettings: SummarySaveSettingsRepository,
            private val analytics: SettingsAnalytics,
        ) : ViewModel() {
```

Replace the `init { ... }` combine block to fold in the third flow:

```kotlin
        init {
            // 음성·알림·요약저장 은 라이브 Flow. 첫 방출 시 loading=false.
            viewModelScope.launch {
                combine(
                    ttsSettings.settings,
                    reminderOrchestrator.config,
                    summarySaveSettings.settings,
                ) { tts, reminder, summarySave -> Triple(tts, reminder, summarySave) }
                    .collect { (tts, reminder, summarySave) ->
                        _uiState.update {
                            it.copy(
                                ttsQuality = tts.quality,
                                speechRate = tts.speechRate,
                                ttsMuted = tts.muted,
                                reminderEnabled = reminder.enabled,
                                reminderHour = reminder.hour,
                                reminderMinute = reminder.minute,
                                summarySaveByDefault = summarySave.saveByDefault,
                                loading = false,
                            )
                        }
                    }
            }
            loadNickname()
            refreshAccount()
        }
```

Add the new method next to `onMuteChange` (data-management concern, but same "single-write + analytics" shape — place it right after the `// ----- 음성 -----` block, before `// ----- 알림 -----`):

```kotlin
        // ----- 요약 저장 기본값 -----

        fun onSummarySaveDefaultChange(saveByDefault: Boolean) {
            viewModelScope.launch { summarySaveSettings.setSaveByDefault(saveByDefault) }
            analytics.summarySaveDefaultToggled(saveByDefault)
        }

        // ----- 알림 -----
```

- [ ] **Step 6: Run the ViewModel test to verify it passes**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SettingsViewModelTest*'`
Expected: PASS (3 tests: the 2 pre-existing `resetMetrics` tests + the new one).

- [ ] **Step 7: Add the UI strings**

In `android/app/src/main/res/values/strings.xml`, add two new strings after `settings_data_reset` (inside the `<!-- 데이터 관리 -->` block):

```xml
    <!-- 데이터 관리 -->
    <string name="settings_data_purge">저장 카드 정리</string>
    <string name="settings_data_reset">누적 기록 초기화</string>
    <string name="settings_data_save_default">표현·단어 자동 저장</string>
```

And add the matching description string next to the other `_desc` entries (after `settings_data_reset_desc`):

```xml
    <string name="settings_data_purge_desc">보관 기간이 지난 카드를 삭제해요.</string>
    <string name="settings_data_reset_desc">XP · 연속 학습일 · 학습시간을 0으로.</string>
    <string name="settings_data_save_default_desc">켜면 요약 화면의 새 표현·단어가 자동으로 저장돼요.</string>
```

- [ ] **Step 8: Add the row to `SettingsContent` and thread the callback**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt`, add the new required param to `SettingsContent` (insert right after `onResetClick: () -> Unit,`):

```kotlin
@Suppress("LongMethod", "LongParameterList")
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    versionLabel: String,
    notificationsBlocked: Boolean,
    onNicknameChange: (String) -> Unit,
    onQualityChange: (TtsQuality) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onMuteChange: (Boolean) -> Unit,
    onReminderToggle: (Boolean) -> Unit,
    onReminderTimeClick: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onPurgeClick: () -> Unit,
    onResetClick: () -> Unit,
    onSummarySaveDefaultChange: (Boolean) -> Unit,
    onGoogleSave: () -> Unit,
    onLogoutClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRetryMerge: () -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    isGoogleSaveLoading: Boolean = false,
) {
```

Add the new row inside the "데이터 관리" section body (append after the existing `settings_data_reset` `SettingsNavRow`):

```kotlin
            // ----- 데이터 관리 -----
            item(key = "data") {
                SettingsSection(
                    titleRes = R.string.settings_section_data,
                    modifier = Modifier.staggerReveal(4, entrance),
                ) {
                    SettingsNavRow(
                        icon = OceIcon.CleaningServices,
                        title = stringResource(R.string.settings_data_purge),
                        desc = stringResource(R.string.settings_data_purge_desc),
                        onClick = onPurgeClick,
                    )
                    SettingsCardDivider()
                    SettingsNavRow(
                        icon = OceIcon.RestartAlt,
                        title = stringResource(R.string.settings_data_reset),
                        desc = stringResource(R.string.settings_data_reset_desc),
                        onClick = onResetClick,
                    )
                    SettingsCardDivider()
                    SettingsNavRow(
                        icon = OceIcon.Bookmark,
                        title = stringResource(R.string.settings_data_save_default),
                        desc = stringResource(R.string.settings_data_save_default_desc),
                        trailing = {
                            OneClickSwitch(
                                checked = state.summarySaveByDefault,
                                onCheckedChange = onSummarySaveDefaultChange,
                            )
                        },
                    )
                }
            }
```

Wire the callback in the `SettingsScreen` composable's call to `SettingsContent(...)` (add right after `onResetClick = { showResetDialog = true },`):

```kotlin
        SettingsContent(
            state = state,
            versionLabel = appVersionLabel(context),
            notificationsBlocked = notificationsBlocked,
            onNicknameChange = viewModel::onNicknameChange,
            onQualityChange = viewModel::onQualityChange,
            onSpeedChange = viewModel::onSpeedChange,
            onMuteChange = viewModel::onMuteChange,
            onReminderToggle = onReminderToggle,
            onReminderTimeClick = { showTimeSheet = true },
            onOpenNotificationSettings = { openAppNotificationSettings(context) },
            onPurgeClick = {
                viewModel.loadPurgeCounts()
                showPurgeSheet = true
            },
            onResetClick = { showResetDialog = true },
            onSummarySaveDefaultChange = viewModel::onSummarySaveDefaultChange,
            onGoogleSave = { onGoogleSave() },
            isGoogleSaveLoading = googleSaveLoading,
            onLogoutClick = { showLogoutDialog = true },
            onDeleteClick = { showDeleteDialog = true },
            onRetryMerge = { linkViewModel.retryMerge(LINK_SESSION_ID) },
            onPrivacy = { openUrl(context, SettingsUrls.PRIVACY) },
            onTerms = { openUrl(context, SettingsUrls.TERMS) },
            reduceMotion = rememberReduceMotion(),
        )
```

- [ ] **Step 9: Update the screenshot test call sites**

In `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt`, add `onSummarySaveDefaultChange = {},` (right after `onResetClick = {},`) to **all three** `SettingsContent(...)` call sites: the one inside `renderSettings(...)` (around line 92), and the two inside `accountSection_showsLoadingSpinner_...` / `accountSection_showsChevron_...` (around lines 213 and 250).

Example for `renderSettings(...)`:

```kotlin
                    SettingsContent(
                        state = state,
                        versionLabel = "1.0.0 (1)",
                        notificationsBlocked = blocked,
                        onNicknameChange = {},
                        onQualityChange = {},
                        onSpeedChange = {},
                        onMuteChange = {},
                        onReminderToggle = {},
                        onReminderTimeClick = {},
                        onOpenNotificationSettings = {},
                        onPurgeClick = {},
                        onResetClick = {},
                        onSummarySaveDefaultChange = {},
                        onGoogleSave = {},
                        onLogoutClick = {},
                        onDeleteClick = {},
                        onRetryMerge = {},
                        onPrivacy = {},
                        onTerms = {},
                        reduceMotion = true,
                        isGoogleSaveLoading = isGoogleSaveLoading,
                    )
```

Apply the same single-line insertion (`onSummarySaveDefaultChange = {},` right after `onResetClick = {},`) to the other two call sites.

- [ ] **Step 10: Run the full settings test suite to verify everything passes**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*Settings*'`
Expected: PASS — `SettingsViewModelTest` (3 tests) and `SettingsScreenScreenshotTest` (all tests, including the new row rendering without crashing) all green.

- [ ] **Step 11: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsUiState.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsAnalytics.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsViewModel.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt \
        android/app/src/main/res/values/strings.xml \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsViewModelTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt
git commit -m "feat(settings): add summary save-by-default switch to the data management section"
```

---

### Task 4: 수동 검증 (에뮬레이터/실기기)

**Files:** 없음(코드 변경 없음, 동작 검증 전용).

- [ ] **Step 1: 디버그 빌드 설치**

Run: `scripts/verify-android.sh :app:installDebug`
Expected: BUILD SUCCESSFUL, 에뮬레이터/실기기에 앱 설치됨.

- [ ] **Step 2: 기본값(꺼짐) 동작 확인**

앱을 열고 설정 탭 → "데이터 관리" 섹션에서 "표현·단어 자동 저장" 스위치가 **꺼진 상태**로 보이는지 확인한다. 세션 하나를 완주해 요약 화면에 진입하고, "자연스러운 표현"/"새 단어" 카드가 **북마크 아이콘이 빈 상태**(미저장)로 뜨는지 확인한다(기존 동작 유지).

- [ ] **Step 3: 켜짐 동작 확인**

설정 화면으로 돌아가 스위치를 켠다. 새 세션을 하나 더 완주해 요약 화면에 진입하고, "자연스러운 표현"/"새 단어" 카드가 **도착하자마자 채워진 북마크 아이콘**(저장됨)으로 뜨는지 확인한다. 기록 탭 → 표현/단어 탭에서 방금 세션의 카드가 즉시 보이는지 확인한다(자동 저장이 실제 영속화임을 확인).

- [ ] **Step 4: 수동 해제 확인**

켜짐 상태로 자동 저장된 카드 하나를 요약 화면에서 직접 탭해 해제한다. 북마크 아이콘이 빈 상태로 바뀌는지, 기록 탭에서 해당 카드가 사라지는지 확인한다(자동 저장 카드도 수동 토글이 정상 동작).

- [ ] **Step 5: 앱 재시작 후 설정 유지 확인**

앱을 완전히 종료 후 재실행 → 설정 화면의 스위치가 마지막으로 설정한 상태(켜짐)를 유지하는지 확인한다(DataStore 영속 확인).

---

## Self-Review Notes

- **스펙 커버리지:** "설정 화면에 저장 기본값 토글 추가"(Task 3) · "기본값이 저장/저장안함을 실제로 바꾼다"(Task 2, `SavedCardRepository.save()` 재사용) · "지금은 항상 저장하지않음"(Task 2 회귀 테스트로 고정) 모두 태스크로 커버됨.
- **placeholder 스캔:** 모든 스텝에 완전한 코드/명령이 포함되어 있고 "TODO"/"적절히 처리" 류 표현 없음.
- **타입 일관성:** `SummarySaveSettingsRepository.settings/current/setSaveByDefault`, `SummaryCoordinator` 생성자 8번째 파라미터, `SettingsUiState.summarySaveByDefault`, `SettingsViewModel.onSummarySaveDefaultChange`, `SettingsContent.onSummarySaveDefaultChange` 가 Task 1→2→3 전체에서 동일한 이름/타입으로 참조됨을 확인함.

# 홈/대본 선택 화면 (PR C) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 홈 추천 상황 섹션에 리스트↔2열 그리드 토글을 추가하고, 대본 주제 변경 시 히어로 카드에 NameDrop식 리빌 애니메이션을 재생하며, "미완 대화가 없는데 이어하기가 뜨는" 팬텀 버그를 제거한다.

**Architecture:** 이어하기 판정 로직을 `SessionSnapshotStore`의 검증된 `resumeInfo: Flow<ResumeInfo?>` 하나로 모아(디코드+스키마검증+미완게이트+단위정합) 근본 수정하고, 홈 UI(그리드 토글·히어로 리빌)는 기존 stateless `HomeContent`/`HeroCta` seam에 파라미터로 얹는다. 모든 애니메이션은 기존 `rememberReduceMotion()` 게이트를 통과한다.

**Tech Stack:** Kotlin · Jetpack Compose (BOM 2025.01.00) · Hilt · DataStore(Preferences) · kotlinx.serialization · Roborazzi/Robolectric(스크린샷) · JUnit4

## Global Constraints

- 검증은 **반드시** `scripts/verify-android.sh`로 실행한다(워크트리 gradle 캐시/데몬 오염·`google-services.json` 부재 우회). 예: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SessionSnapshotStoreTest*'`.
- 아이콘은 `OceIcon` 시맨틱 상수 → `@DrawableRes` 벡터 1:1 매핑으로만 추가한다(`OneClickIcon.kt`). enum 항목과 `res/drawable/*.xml` 벡터는 항상 짝으로 만든다.
- `OneClickIcon(contentDescription=…)`은 기본값이 없다. 장식이면 `null`, 인터랙티브 컨트롤이면 문자열 라벨을 **명시**한다(M0 lint gate).
- 버튼/카드 radius 등 shape 는 `OceTheme.shapes.*` 토큰만 사용한다(하드코딩 금지).
- 이어하기 진행 표시 단위는 **완료한 학습자 턴 수**(교환 단위, `messages.count { isLearner }`)이며 `totalTurns`(=세션 길이 5/10)와 같은 축이다. `consumedTurnCount`(스트림 라인 수, ~2배)를 표시/게이트에 쓰지 않는다.
- 커밋은 태스크 단위로 자주 한다. 커밋 메시지는 한국어 관례(`feat(...)`/`fix(...)`)를 따른다.

---

## File Structure

**수정:**
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStore.kt` — `recoverable: Flow<Boolean>` 폐기, 검증된 `resumeInfo: Flow<ResumeInfo?>` + `ResumeInfo` 신설.
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeViewModel.kt` — combine 소스 `recoverable`→`resumeInfo`, `hasResume`/`resumeTopic`/`resumeTurn`/`resumeTotalTurns` 4필드 배선.
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/OneClickIcon.kt` — `OceIcon.ViewAgenda` enum 항목 추가.
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt` — `HomeContent`에 `gridMode`/`onToggleLayout`/`reduceMotion` 파라미터, `SituationsHeader` 토글, 그리드 분기 + `SituationCell`, `HeroCta` NameDrop 리빌.

**생성:**
- `android/app/src/main/res/drawable/ic_view_agenda.xml` — 그리드→리스트 토글 글리프(Material Symbols view_agenda).
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStoreTest.kt` — `resumeInfo` 팬텀 배제·단위·null 병합 검증.
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeHeroRevealTest.kt` — 히어로 메타 스왑(리빌 트리거) 헤드리스 스모크.

**수정(테스트):**
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreenScreenshotTest.kt` — `home_light_grid` 그리드 변형 캡처 추가.

**수정(빌드):**
- `android/app/build.gradle.kts` — Release 단위테스트 제외 글롭에 신규 `createComposeRule` 테스트(`HomeHeroRevealTest`) 등록(`build.gradle.kts:69-71`, 기존 `SlimFeedbackSheetTest` 확대 선례). 미등록 시 `testReleaseUnitTest` 가 `compose-ui-test-manifest` 부재로 `ComponentActivity not found` 실패.

> **테스트 범위 결정(의도적):** 이어하기 판정의 실질 로직은 전부 `SessionSnapshotStore.resumeInfo`에 있고 `HomeViewModel`은 `info -> hasResume/…` 순수 투영(one-liner)이다. VM 은 7개 의존성 fake 조립이 필요해 비용 대비 이득이 없으므로 별도 `HomeViewModelTest`를 만들지 않는다 — 투영 결과의 렌더는 기존 `HomeScreenScreenshotTest.home_light_resume`(resumeTopic/Turn/TotalTurns 전달)이 이미 커버한다.

---

## Task 1: `resumeInfo` seam — 이어하기 팬텀 근본 수정

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStore.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStoreTest.kt`

**Interfaces:**
- Consumes: `SessionTurnSnapshot`(같은 `feature.session.turn` 패키지, `messages: List<MessageData>` 의 `MessageData.isLearner: Boolean`, `sessionPhase: String`, `topicTitle: String?`, `totalTurns: Int?`, `schemaVersion: Int`, `SCHEMA_VERSION`), `SessionPhase`(enum, `feature.session.turn`).
- Produces: `data class ResumeInfo(val topicTitle: String, val doneTurns: Int, val totalTurns: Int)` 및 `val resumeInfo: Flow<ResumeInfo?>` (Task 2 가 소비). `recoverable` 는 이 태스크에서는 **유지**(Task 2 에서 제거)해 컴파일 그린 유지.

- [ ] **Step 1: 실패 테스트 작성**

`android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStoreTest.kt` 생성:

```kotlin
package com.jjundev.oneclickeng.feature.session.resume

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.jjundev.oneclickeng.feature.session.turn.MessageData
import com.jjundev.oneclickeng.feature.session.turn.PendingData
import com.jjundev.oneclickeng.feature.session.turn.SessionPhase
import com.jjundev.oneclickeng.feature.session.turn.SessionTurnSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * SessionSnapshotStore.resumeInfo 검증 — 팬텀 이어하기 배제(스키마 불일치·완주·턴0·빈 제목)와
 * 표시 단위(완료 학습자 턴). 실제 파일 백드 DataStore 를 JVM 에서 구동한다(StudytimeStoreTest 패턴).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionSnapshotStoreTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun newStore(scope: CoroutineScope): SessionSnapshotStore {
        val dataStore =
            PreferenceDataStoreFactory.create(scope = scope) {
                File(tmpFolder.newFolder(), "resume.preferences_pb")
            }
        return SessionSnapshotStore(dataStore)
    }

    private fun learner(text: String) = MessageData(isLearner = true, english = text)

    private fun snapshot(
        messages: List<MessageData>,
        sessionPhase: String = SessionPhase.InTurn.name,
        topicTitle: String? = "카페에서 주문하기",
        totalTurns: Int? = 5,
        schemaVersion: Int = SessionTurnSnapshot.SCHEMA_VERSION,
    ) = SessionTurnSnapshot(
        schemaVersion = schemaVersion,
        topicTitle = topicTitle,
        totalTurns = totalTurns,
        messages = messages,
        turnPhase = "LearnerTurn",
        sessionPhase = sessionPhase,
        currentTaskKo = null,
        consumedTurnCount = 0,
        opponentTurnSerial = 0,
        pending = PendingData(),
        bufferedPending = emptyList(),
        streamStatus = "Idle",
        diagnostic = null,
        micState = "Ready",
        turns = emptyList(),
    )

    @Test
    fun `valid incomplete snapshot yields ResumeInfo with learner-turn count`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            store.write(snapshot(messages = listOf(learner("Hi"), learner("Thanks"))))
            val info = store.resumeInfo.first()

            assertEquals("카페에서 주문하기", info?.topicTitle)
            assertEquals(2, info?.doneTurns)
            assertEquals(5, info?.totalTurns)

            scope.cancel()
        }

    @Test
    fun `turn0 snapshot (no learner message) is not resumable`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            store.write(snapshot(messages = emptyList()))

            assertNull(store.resumeInfo.first())
            scope.cancel()
        }

    @Test
    fun `completed snapshot is not resumable`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            store.write(
                snapshot(messages = listOf(learner("Hi")), sessionPhase = SessionPhase.Completed.name),
            )

            assertNull(store.resumeInfo.first())
            scope.cancel()
        }

    @Test
    fun `stale-schema snapshot is not resumable`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            store.write(snapshot(messages = listOf(learner("Hi")), schemaVersion = 999))

            assertNull(store.resumeInfo.first())
            scope.cancel()
        }

    @Test
    fun `blank-title snapshot is not resumable`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            store.write(snapshot(messages = listOf(learner("Hi")), topicTitle = null))

            assertNull(store.resumeInfo.first())
            scope.cancel()
        }

    @Test
    fun `empty store yields null`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            assertNull(store.resumeInfo.first())
            scope.cancel()
        }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SessionSnapshotStoreTest*'`
Expected: FAIL — 컴파일 에러 `unresolved reference: resumeInfo` / `ResumeInfo`.

- [ ] **Step 3: `resumeInfo` 구현**

`SessionSnapshotStore.kt` 상단 import 에 추가:

```kotlin
import com.jjundev.oneclickeng.feature.session.turn.SessionPhase
```

클래스 본문에서 `recoverable` 선언 **바로 아래**에 추가(`recoverable` 는 아직 삭제하지 않는다):

```kotlin
        /**
         * 이어하기 프롬프트용 검증·해석 스냅샷. `recoverable`(key 존재만 검사)의 팬텀을 근절한다:
         * 디코드 성공 + 스키마 일치 + 미완(sessionPhase != Completed) + 실제 진행(학습자 턴 ≥ 1) +
         * 표시 가능한 제목이 모두 성립할 때만 [ResumeInfo]. 진행 단위(doneTurns)는 완료한 학습자 턴 수로,
         * 세션 헤더 `completedTurns`(GeneratedDialogueSession) 및 `totalTurns` 와 같은 축이다.
         */
        val resumeInfo: Flow<ResumeInfo?> =
            dataStore.data.map { prefs ->
                val snap =
                    prefs[KEY_SNAPSHOT]
                        ?.let { runCatching { json.decodeFromString<SessionTurnSnapshot>(it) }.getOrNull() }
                        ?.takeIf { it.schemaVersion == SessionTurnSnapshot.SCHEMA_VERSION }
                        ?: return@map null
                val title = snap.topicTitle
                val done = snap.messages.count { it.isLearner }
                if (title.isNullOrBlank() || done == 0 || snap.sessionPhase == SessionPhase.Completed.name) {
                    return@map null
                }
                ResumeInfo(
                    topicTitle = title,
                    doneTurns = done,
                    totalTurns = snap.totalTurns ?: DEFAULT_TOTAL_TURNS,
                )
            }
```

`companion object` 를 다음으로 교체(기존은 `KEY_SNAPSHOT` 하나만 보유):

```kotlin
        private companion object {
            val KEY_SNAPSHOT = stringPreferencesKey("session_snapshot_json")

            /** totalTurns 미기재 스냅샷의 폴백(세션 기본 길이, GeneratedDialogueSession.DEFAULT_TOTAL_TURNS 정합). */
            const val DEFAULT_TOTAL_TURNS = 5
        }
```

파일 맨 아래(클래스 밖)에 데이터 클래스 추가:

```kotlin
/**
 * 홈 이어하기 프롬프트가 필요로 하는 스냅샷 요약 — 표시 제목과 진행(완료 학습자 턴 / 전체 턴).
 * 이 타입이 존재한다는 것 자체가 "이어갈 미완 세션이 실재한다"는 계약이다(null = 없음).
 */
data class ResumeInfo(
    val topicTitle: String,
    val doneTurns: Int,
    val totalTurns: Int,
)
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SessionSnapshotStoreTest*'`
Expected: PASS (6개 테스트).

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStore.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStoreTest.kt
git commit -m "feat(resume): 검증된 resumeInfo seam 추가 — 이어하기 팬텀(스키마·완주·턴0) 배제 + 진행 단위 정합"
```

---

## Task 2: HomeViewModel 이 `resumeInfo` 소비 + `recoverable` 제거

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeViewModel.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStore.kt`

**Interfaces:**
- Consumes: `SessionSnapshotStore.resumeInfo: Flow<ResumeInfo?>`(Task 1).
- Produces: `HomeUiState`의 `hasResume`/`resumeTopic`/`resumeTurn`/`resumeTotalTurns` 가 실제로 채워짐(기존 필드, `HomeUiState.kt:25-28`). 다운스트림 UI(HeroCta·header·NewChatLink) 계약 불변.

- [ ] **Step 1: HomeViewModel combine 소스 교체**

`HomeViewModel.kt` 의 `uiState` 정의에서 두 곳을 바꾼다. combine 인자 `snapshotStore.recoverable` → `snapshotStore.resumeInfo`, 람다 파라미터명 `resume` 유지(이제 `ResumeInfo?` 타입):

```kotlin
        val uiState: StateFlow<HomeUiState> =
            combine(
                connectivity.state.map { it == Connectivity.Online },
                snapshotStore.resumeInfo,
                limitHolder.freshRemaining,
                gamification,
                sessionSetup,
            ) { online, resume, remaining, gami, setup ->
                HomeUiState(
                    studyTimeLabel = gami?.studyTimeLabel,
                    streak = gami?.streak ?: 0,
                    isOnline = online,
                    hasResume = resume != null,
                    resumeTopic = resume?.topicTitle,
                    resumeTurn = resume?.doneTurns ?: 0,
                    resumeTotalTurns = resume?.totalTurns ?: 0,
                    // fresh remaining 이 관측됐고(non-null) 그 값이 0 일 때만 at-limit(H6, unknown→억제).
                    atLimit = remaining == 0,
                    level = setup.level,
                    length = setup.length,
                    selectedSituation = setup.selected,
                    situations = setup.situations,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())
```

- [ ] **Step 2: `recoverable` 삭제(사용처 없음)**

`SessionSnapshotStore.kt` 에서 다음 선언과 그 KDoc 한 줄을 제거한다:

```kotlin
        /** `true` while a persisted, schema-current snapshot exists. Home observes this reactively. */
        val recoverable: Flow<Boolean> = dataStore.data.map { it[KEY_SNAPSHOT] != null }
```

(`resumeInfo`가 이를 대체하며, 저장소의 클래스 KDoc 에서 "Home reads [recoverable]" 문구가 있으면 "Home reads [resumeInfo]" 로 갱신한다.)

- [ ] **Step 3: 전체 컴파일·기존 테스트 그린 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*HomeScreenScreenshotTest*' --tests '*SessionSnapshotStoreTest*'`
Expected: PASS — `recoverable` 미해결 참조 없음(사용처는 HomeViewModel 하나였고 방금 교체). 홈 스크린샷 4종 그린.

- [ ] **Step 4: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeViewModel.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStore.kt
git commit -m "fix(home): 이어하기 판정을 resumeInfo 로 배선 + recoverable 폐기 — 히어로 주제·N/M턴 메타 채움"
```

---

## Task 3: `OceIcon.ViewAgenda` 아이콘(enum + 벡터)

**Files:**
- Create: `android/app/src/main/res/drawable/ic_view_agenda.xml`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/OneClickIcon.kt`

**Interfaces:**
- Produces: `OceIcon.ViewAgenda`(그리드 모드일 때 "목록으로 보기" 토글 글리프, Task 4 가 소비).

- [ ] **Step 1: 벡터 드로어블 생성**

`android/app/src/main/res/drawable/ic_view_agenda.xml` (기존 `ic_grid_view.xml` 스타일 정합 — 24dp, 960 뷰포트, `translateY(960)` group, 두 개의 가로 라운드 바):

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- view_agenda (Rounded · opsz24 · FILL 1) · source: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/view_agenda/materialsymbolsrounded/view_agenda_fill1_24px.svg · Apache-2.0, see ICONS-LICENSE.txt -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
    <group android:translateY="960">
        <path
            android:fillColor="#000000"
            android:pathData="M200-160q-33 0-56.5-23.5T120-240v-160q0-33 23.5-56.5T200-480h560q33 0 56.5 23.5T840-400v160q0 33-23.5 56.5T760-160H200Zm0-320q-33 0-56.5-23.5T120-560v-160q0-33 23.5-56.5T200-800h560q33 0 56.5 23.5T840-720v160q0 33-23.5 56.5T760-480H200Z" />
    </group>
</vector>
```

- [ ] **Step 2: enum 항목 추가**

`OneClickIcon.kt` 의 `enum class OceIcon` 에서 `GridView(R.drawable.ic_grid_view),` 바로 아래에 추가:

```kotlin
    ViewAgenda(R.drawable.ic_view_agenda),
```

- [ ] **Step 3: 컴파일 확인**

Run: `scripts/verify-android.sh :app:compileDebugKotlin`
Expected: PASS — `R.drawable.ic_view_agenda` 해결됨.

- [ ] **Step 4: 커밋**

```bash
git add android/app/src/main/res/drawable/ic_view_agenda.xml \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/OneClickIcon.kt
git commit -m "feat(icon): OceIcon.ViewAgenda 추가(그리드→리스트 토글 글리프)"
```

---

## Task 4: 추천 상황 리스트↔그리드 토글

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreenScreenshotTest.kt`

**Interfaces:**
- Consumes: `OceIcon.ViewAgenda`(Task 3), 기존 `HomeSituation`·`OneClickCard`·`SITUATION_ICON_BG_ALPHA`.
- Produces: `HomeContent(gridMode: Boolean = false, onToggleLayout: () -> Unit = {}, …)`, `private fun SituationCell(...)`. 토글 상태는 `HomeScreen`이 소유(`remember`).

- [ ] **Step 1: 그리드 스크린샷 변형 테스트 작성**

`HomeScreenScreenshotTest.kt` 에 테스트 메서드 추가(`home_light_newsession` 아래):

```kotlin
    /** 그리드 레이아웃 — 추천 4개가 2×2 컴팩트 셀(chevron 없음)로 배치되는지 프로토와 대조. */
    @Test
    fun home_light_grid() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        HomeContent(
                            state =
                                HomeUiState(
                                    studyTimeLabel = "오늘 8분",
                                    streak = 7,
                                    isOnline = true,
                                    hasResume = false,
                                    level = "easy",
                                    length = 5,
                                    selectedSituation = sampleSelected,
                                    situations = sampleSituations,
                                ),
                            onStartLearning = {},
                            onResumeContinue = {},
                            onResumeStartNew = {},
                            onViewRecords = {},
                            onOfflineBlocked = {},
                            gridMode = true,
                            modifier = Modifier.weight(1f),
                        )
                        OceBottomNav(rememberNavController())
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/home_light_grid.png")
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*HomeScreenScreenshotTest*'`
Expected: FAIL — 컴파일 에러 `no value passed for parameter` 또는 `unresolved reference: gridMode`(HomeContent 미확장).

- [ ] **Step 3: HomeContent 시그니처 확장 + 헤더/렌더 분기**

`HomeScreen.kt` 상단 import 에 추가:

```kotlin
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow
```

(`heightIn` 이 이미 import 되어 있으면 중복 추가하지 않는다.)

`HomeContent` 파라미터에 두 개 추가(`onSetLength` 다음 줄):

```kotlin
    gridMode: Boolean = false,
    onToggleLayout: () -> Unit = {},
```

`situations_header` item 의 `SituationsHeader(...)` 호출을 다음으로 교체(gridMode/onToggleLayout 전달):

```kotlin
            item(key = "situations_header") {
                SituationsHeader(
                    gridMode = gridMode,
                    onToggleLayout = onToggleLayout,
                    onRefresh = onRefreshSituations,
                    modifier = Modifier.padding(top = OceTheme.spacing.xxl),
                )
            }
```

그 아래 기존 `itemsIndexed(state.situations, …) { … SituationRow … }` 블록 전체를 다음 분기로 교체:

```kotlin
            if (gridMode) {
                val rows = state.situations.chunked(2)
                itemsIndexed(rows, key = { _, pair -> "grid_" + pair.joinToString("_") { it.id } }) { index, pair ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = if (index == 0) OceTheme.spacing.lg else OceTheme.spacing.sm)
                                .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
                    ) {
                        pair.forEach { situation ->
                            SituationCell(
                                situation = situation,
                                onClick = { onSituationSelected(situation) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            } else {
                itemsIndexed(state.situations, key = { _, item -> item.id }) { index, situation ->
                    SituationRow(
                        situation = situation,
                        onClick = { onSituationSelected(situation) },
                        modifier =
                            Modifier.padding(
                                top = if (index == 0) OceTheme.spacing.lg else OceTheme.spacing.sm,
                            ),
                    )
                }
            }
```

- [ ] **Step 4: SituationsHeader 토글화**

`SituationsHeader` 시그니처와 본문을 교체(장식 GridView → 클릭 토글, contentDescription 명시):

```kotlin
/** "추천 상황" 섹션 헤더 — 라벨 + 레이아웃 토글(리스트↔그리드) + 새로고침. */
@Composable
private fun SituationsHeader(
    gridMode: Boolean,
    onToggleLayout: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
    ) {
        Text(
            text = "추천 상황",
            style = OceTheme.typography.sectionLabel.copy(fontWeight = FontWeight.ExtraBold, fontSize = 17.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier =
                Modifier
                    .clip(OceTheme.shapes.radius12)
                    .clickable(onClick = onToggleLayout)
                    .padding(OceTheme.spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            OneClickIcon(
                icon = if (gridMode) OceIcon.ViewAgenda else OceIcon.GridView,
                contentDescription = if (gridMode) "목록으로 보기" else "그리드로 보기",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = OceIconSize.ListDisclosure,
            )
        }
        Row(
            modifier = Modifier.clickable(onClick = onRefresh),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
        ) {
            OneClickIcon(
                icon = OceIcon.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                size = OceIconSize.ListDisclosure,
            )
            Text(
                text = "새로고침",
                style = OceTheme.typography.helper,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
```

> 주의: `OneClickIcon` 의 `modifier` 는 마지막 파라미터가 아니므로 named argument(`modifier = …`)로 전달한다.

- [ ] **Step 5: SituationCell 신설**

`SituationRow` 정의 **바로 아래**에 추가:

```kotlin
/** 그리드 셀 — 컴팩트 카드(상단 아이콘 박스 + 라벨 최대 2줄, chevron 없음). 탭 = 선택 갱신 + 즉시 시작. */
@Composable
private fun SituationCell(
    situation: HomeSituation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OneClickCard(modifier = modifier.clickable(onClick = onClick)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .padding(horizontal = OceTheme.spacing.lg, vertical = OceTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(OceTheme.shapes.radius12)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = SITUATION_ICON_BG_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                OneClickIcon(
                    icon = situation.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    size = OceIconSize.ListDisclosure,
                )
            }
            Text(
                text = situation.labelKo,
                style = OceTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
```

- [ ] **Step 6: HomeScreen 이 토글 상태 소유·주입**

`HomeScreen` 컴포저블에서 `topicSheetVisible`/`timePickerVisible` `remember` 옆에 추가:

```kotlin
    var gridMode by remember { mutableStateOf(false) }
```

`HomeContent(...)` 호출에 두 인자 추가(`onSetLength = viewModel::setLength` 다음):

```kotlin
        gridMode = gridMode,
        onToggleLayout = { gridMode = !gridMode },
```

- [ ] **Step 7: 그리드 캡처 기록 + 전체 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*HomeScreenScreenshotTest*' -Proborazzi.record`
Expected: PASS — `home_light_grid.png` 등 캡처 산출. `android/app/build/outputs/roborazzi/home_light_grid.png` 를 육안으로 열어 추천 4개가 2×2 컴팩트 셀(chevron 없음, 높이 균등)로 배치됐는지 프로토와 대조한다.

- [ ] **Step 8: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreenScreenshotTest.kt
git commit -m "feat(home): 추천 상황 리스트↔2열 그리드 토글(SituationCell·item-emission 분기)"
```

> Roborazzi 는 캡처를 `android/app/build/outputs/roborazzi/`(빌드 산출물, 미커밋)에 쓴다 — 커밋 대상은 소스뿐이다.

---

## Task 5: 히어로 NameDrop 리빌 애니메이션

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt`
- Modify: `android/app/build.gradle.kts` — Release 단위테스트 제외 글롭에 `HomeHeroRevealTest` 등록.
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeHeroRevealTest.kt`

**Interfaces:**
- Consumes: `rememberReduceMotion()`(`ui.foundation.ReduceMotion`), `OceIcon.AutoAwesome`, `HeroBadge`.
- Produces: `HomeContent(reduceMotion: Boolean = false, …)`, `HeroCta(reduceMotion: Boolean, …)`. 주제(라벨) 변경 시 헤이즈+스파클+메타 드롭인+햅틱; 첫 컴포지션·이어하기 모드·reduce-motion 에서는 애니메이션 skip(메타는 항상 스왑).

- [ ] **Step 1: 리빌 스모크 테스트 작성**

`android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeHeroRevealTest.kt` 생성:

```kotlin
package com.jjundev.oneclickeng.feature.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 히어로 리빌 트리거의 헤드리스 스모크 — 애니메이션 프레임 자체(비트맵)는 검증 대상이 아니고,
 * 주제 변경 시 히어로 메타 텍스트가 새 라벨로 스왑되며(트리거·AnimatedContent 배선) 크래시가 없는지만 본다.
 * reduce-motion on/off 두 경로 모두 새 라벨을 표시해야 한다(reduce-motion 은 애니메이션만 끄고 메타는 갱신).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HomeHeroRevealTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun stateWith(label: String) =
        HomeUiState(
            isOnline = true,
            hasResume = false,
            level = "easy",
            length = 5,
            selectedSituation = SelectedSituation("id-$label", label, "seed"),
            situations = emptyList(),
        )

    private fun assertMetaSwaps(reduceMotion: Boolean) {
        var state by mutableStateOf(stateWith("카페에서 주문하기"))
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                HomeContent(
                    state = state,
                    onStartLearning = {},
                    onResumeContinue = {},
                    onResumeStartNew = {},
                    onViewRecords = {},
                    onOfflineBlocked = {},
                    reduceMotion = reduceMotion,
                )
            }
        }
        composeRule.onNodeWithText("카페에서 주문하기", substring = true).assertExists()

        state = stateWith("날씨로 스몰토크")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("날씨로 스몰토크", substring = true).assertExists()
    }

    @Test
    fun meta_swaps_with_motion() = assertMetaSwaps(reduceMotion = false)

    @Test
    fun meta_swaps_reduced_motion() = assertMetaSwaps(reduceMotion = true)
}
```

- [ ] **Step 2: Release 변이 제외 등록(createComposeRule)**

`HomeHeroRevealTest` 는 `createComposeRule()` 을 쓰므로 Release 단위테스트에서 `ComponentActivity` 를 못 찾는다(디버그 전용 `compose-ui-test-manifest`). `android/app/build.gradle.kts` 의 Release 제외 글롭(현재 `build.gradle.kts:70`)을 다음으로 교체해 등록한다:

```kotlin
    if (name.contains("Release", ignoreCase = true)) {
        exclude("**/*ScreenshotTest*", "**/SlimFeedbackSheetTest*", "**/HomeHeroRevealTest*")
    }
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*HomeHeroRevealTest*'`
Expected: FAIL — 컴파일 에러 `no value passed for parameter 'reduceMotion'`(HomeContent 미확장).

- [ ] **Step 4: import 추가**

`HomeScreen.kt` 상단 import 에 추가:

```kotlin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.util.lerp
import androidx.compose.runtime.LaunchedEffect
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
```

(`fadeIn`/`fadeOut`, `Alignment`, `getValue`/`setValue`/`mutableStateOf`/`remember` 는 이미 import 되어 있다. `LaunchedEffect` 는 **현재 `HomeScreen.kt` 에 import 되어 있지 않으므로** 위 목록에 포함해 반드시 추가한다.)

파일 상단 private 상수 근처에 추가:

```kotlin
/** NameDrop 리빌 지속(ms) — 헤이즈 확장·메타 드롭인 공용. */
private const val REVEAL_MS = 700
/** 스파클이 살아있는 리빌 진행 구간(0..이 값). */
private const val SPARKLE_FRACTION = 0.4f
```

- [ ] **Step 5: HomeContent → HeroCta 에 reduceMotion 배선**

`HomeContent` 파라미터에 추가(`onToggleLayout` 다음):

```kotlin
    reduceMotion: Boolean = false,
```

`item(key = "hero")` 의 `HeroCta(...)` 호출에 인자 추가(`onDisabledClick = onOfflineBlocked,` 다음):

```kotlin
                reduceMotion = reduceMotion,
```

`HomeScreen` 컴포저블의 `HomeContent(...)` 호출에 추가(`onToggleLayout = { gridMode = !gridMode },` 다음):

```kotlin
        reduceMotion = rememberReduceMotion(),
```

- [ ] **Step 6: HeroCta 리빌 구현**

`HeroCta` 시그니처에 `reduceMotion: Boolean` 추가(`onDisabledClick: () -> Unit,` 다음 줄):

```kotlin
    reduceMotion: Boolean,
```

`HeroCta` 본문을 다음으로 교체(리빌 상태·트리거 추가, Row 를 Box 로 감싸 스파클 오버레이, 헤이즈 drawWithContent, 메타 AnimatedContent):

```kotlin
@Composable
private fun HeroCta(
    online: Boolean,
    resumeTopic: String?,
    resumeTurn: Int,
    resumeTotalTurns: Int,
    situationLabel: String?,
    level: String?,
    length: Int,
    onClick: () -> Unit,
    onDisabledClick: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val reveal = remember { Animatable(0f) }
    var primed by remember { mutableStateOf(false) }

    // 주제(라벨) 변경 시 1회 리빌. 최초 컴포지션(기본 선택)·이어하기 히어로·reduce-motion 에서는 재생하지 않는다.
    LaunchedEffect(situationLabel, resumeTopic) {
        if (resumeTopic != null) return@LaunchedEffect
        if (!primed) {
            primed = true
            return@LaunchedEffect
        }
        if (reduceMotion) return@LaunchedEffect
        reveal.snapTo(0f)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        reveal.animateTo(1f, tween(REVEAL_MS, easing = FastOutSlowInEasing))
    }

    val p = reveal.value
    val revealActive = p > 0f && p < 1f
    val sparkleAlpha = if (revealActive) (1f - p / SPARKLE_FRACTION).coerceIn(0f, 1f) else 0f
    val subtitle =
        if (resumeTopic != null) {
            "$resumeTopic · $resumeTurn / ${resumeTotalTurns}턴"
        } else {
            listOfNotNull(situationLabel, "${length}턴", level?.let(::levelLabel)).joinToString(" · ")
        }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = HeroMinHeight)
                        .clip(OceTheme.shapes.radius24)
                        .alpha(if (online) 1f else DISABLED_ALPHA)
                        .background(OceTheme.colors.brandGradient())
                        .drawWithContent {
                            drawContent()
                            if (revealActive) {
                                val center = Offset(size.width - 44.dp.toPx(), size.height / 2f)
                                val radius = lerp(0f, size.width * 1.1f, p).coerceAtLeast(1f)
                                drawCircle(
                                    brush =
                                        Brush.radialGradient(
                                            colors = listOf(onPrimary.copy(alpha = 0.30f * (1f - p)), Color.Transparent),
                                            center = center,
                                            radius = radius,
                                        ),
                                    radius = radius,
                                    center = center,
                                    blendMode = BlendMode.Plus,
                                )
                            }
                        }
                        .then(
                            if (online) {
                                Modifier.clickable(onClick = onClick)
                            } else {
                                Modifier
                                    .clickable(onClick = onDisabledClick)
                                    .semantics { disabled() }
                            },
                        )
                        .padding(OceTheme.spacing.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
                ) {
                    Text(
                        text = if (resumeTopic != null) "이어서 대화하기" else "바로 대화 시작하기",
                        style = OceTheme.typography.homeTitle.copy(fontSize = 23.sp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    if (reduceMotion || resumeTopic != null) {
                        Text(
                            text = subtitle,
                            style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        AnimatedContent(
                            targetState = subtitle,
                            transitionSpec = {
                                (
                                    fadeIn(tween(REVEAL_MS)) +
                                        slideInVertically(tween(REVEAL_MS)) { it / 3 }
                                ) togetherWith fadeOut(tween(REVEAL_MS / 2))
                            },
                            label = "heroMetaDrop",
                        ) { text ->
                            Text(
                                text = text,
                                style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
                HeroBadge(icon = if (resumeTopic != null) OceIcon.PlayArrow else OceIcon.Mic)
            }
            if (sparkleAlpha > 0f) {
                OneClickIcon(
                    icon = OceIcon.AutoAwesome,
                    contentDescription = null,
                    tint = onPrimary,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = OceTheme.spacing.xl + 12.dp)
                            .graphicsLayer {
                                alpha = sparkleAlpha
                                val s = 0.6f + 0.6f * (1f - sparkleAlpha)
                                scaleX = s
                                scaleY = s
                            },
                )
            }
        }
        if (!online) {
            Text(
                text = "새 대화는 인터넷 연결이 필요해요.",
                style = OceTheme.typography.helper,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 7: 스모크 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*HomeHeroRevealTest*'`
Expected: PASS (2개 — with/without motion, 둘 다 새 라벨 표시).

- [ ] **Step 8: 홈 스크린샷 회귀 확인(정적 프레임 안정성)**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*HomeScreenScreenshotTest*'`
Expected: PASS — 첫 컴포지션 `primed` 가드로 정적 캡처에서 리빌이 발화하지 않아 기존 4종 + `home_light_grid` 캡처가 변하지 않는다(byte-diff 없음).

- [ ] **Step 9: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt \
        android/app/build.gradle.kts \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeHeroRevealTest.kt
git commit -m "feat(home): 히어로 NameDrop 리빌 — 주제 변경 시 헤이즈·스파클·메타 드롭인·햅틱(reduce-motion 게이트)"
```

---

## Task 6: 전체 검증 세트

**Files:** (없음 — 통합 검증만)

- [ ] **Step 1: 기본 검증 세트 실행**

Run: `scripts/verify-android.sh`
Expected: PASS — 컴파일·detekt·단위테스트 그린. 신규 테스트(`SessionSnapshotStoreTest`, `HomeHeroRevealTest`)와 확장된 `HomeScreenScreenshotTest` 포함.

- [ ] **Step 2: 실패 시 대응**

`HomeContent`/`HeroCta`/`SituationsHeader` 는 모두 `@Composable` 이고 `config/detekt/detekt.yml` 의 `LongParameterList: ignoreAnnotated: ['Composable']` 로 이미 면제되므로 파라미터 증가로는 detekt 가 걸리지 않는다(추가 억제 불필요). 그 외 실패는 로그의 `file:line` 을 그대로 수정한다.

- [ ] **Step 3: 최종 커밋(있으면)**

```bash
git add -A
git commit -m "chore(home): PR C 통합 검증 대응(detekt 억제/후속 정리)" || echo "no changes"
```

---

## Self-Review

**1. Spec coverage**
- 대본 선택 그리드(TopicSelectScreen.kt, HomeScreen.kt) → Task 3(아이콘) + Task 4(토글·SituationCell). 주: 그리드는 **홈 추천 리스트**에 구현(설계 결정 #14 — `TopicSelectSheet`는 그룹 리스트 유지, 불변).
- 주제 변경 시 히어로 NameDrop 애니메이션(HeroCta) → Task 5.
- 미완 대화 없는데 이어하기 뜨는 버그(resume/ + HomeViewModel/HomeUiState) → Task 1(store) + Task 2(VM). 팬텀 3종(스키마·완주·턴0) + 빈 제목 모두 배제.
- 갭 없음.

**2. Placeholder scan** — TBD/TODO/"적절히 처리" 없음. 모든 코드 스텝에 실제 코드 블록 존재. 각 스텝은 단일 액션(테스트 작성 / 실패 확인 / 구현 / 통과 확인 / 커밋).

**3. Type consistency**
- `ResumeInfo(topicTitle: String, doneTurns: Int, totalTurns: Int)` — Task 1 정의, Task 2 에서 `resume?.topicTitle`/`resume?.doneTurns`/`resume?.totalTurns` 로만 소비(일치).
- `resumeInfo: Flow<ResumeInfo?>` — Task 1 produces, Task 2 combine 소스로 consume(일치).
- `HomeContent(gridMode, onToggleLayout, reduceMotion)` — Task 4/5 에서 파라미터 추가, Task 4 screenshot(`gridMode=true`)·Task 5 test(`reduceMotion=…`)·HomeScreen 호출부 모두 named arg 로 일치.
- `SituationsHeader(gridMode, onToggleLayout, onRefresh, modifier)` — Task 4 에서 시그니처·호출부 동시 교체(일치).
- `OceIcon.ViewAgenda` — Task 3 정의, Task 4 소비(일치). `OceIcon.AutoAwesome` 는 기존 상수(재사용).
- `MessageData.isLearner` — 실제 필드(`SessionTurnSnapshot.kt`), Task 1 코드·테스트 동일 사용.

---

## Termination

집중 커밋 6태스크(스토어 seam → VM 배선 → 아이콘 → 그리드 → 히어로 리빌 → 통합검증). 각 태스크는 독립적으로 컴파일·테스트·커밋 가능하며, 그릴 리뷰(2회, 0 blocker)에서 확정된 설계를 그대로 구현한다.

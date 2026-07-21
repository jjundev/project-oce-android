# 설정 탭 재진입 시 스크롤 초기화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 설정 탭을 다른 탭으로 전환했다가 다시 열 때 설정 목록을 항상 맨 위에서 시작하게 한다.

**Architecture:** 기존 하단 내비게이션의 `saveState/restoreState` 동작은 유지한다. `OceNavHost`가 현재 목적지 route가 설정으로 진입하는 순간을 감지해 방문 카운터를 만들고, 이를 `SettingsScreen`에 전달한다. `SettingsScreen`은 `LazyListState`를 소유하고 `SettingsContent`에 reset key를 전달하며, `SettingsContent`는 key가 바뀔 때 `scrollToItem(0)`을 실행한다. 따라서 앱 resume이나 외부 권한/시스템 설정 복귀가 아닌 실제 탭 재진입에서만 설정 목록이 초기화된다.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose, Compose `LazyListState`, Robolectric Compose UI test.

## Global Constraints

- 설정 탭으로 돌아왔을 때 설정 탭은 맨 위에 있어야 한다.
- 학습 탭과 기록 탭의 기존 스크롤 복원 동작은 변경하지 않는다.
- 새 라이브러리나 의존성을 추가하지 않는다.
- 기존 설정 화면의 ViewModel 상태, 다이얼로그, 스낵바, 시스템 설정 복귀 동작은 유지한다.

---

## File Structure

- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt`
  - `SettingsScreen`이 설정 화면의 `LazyListState`를 소유하고 네비게이션에서 받은 reset key를 콘텐츠에 전달한다.
  - 기존 생명주기 observer의 알림 상태 갱신과 스낵바 dismiss 동작은 유지하고, 탭 reset은 담당하지 않는다.
  - stateless 테스트 seam인 `SettingsContent`가 외부 `LazyListState`와 reset key를 받을 수 있게 한다.
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavHost.kt`
  - 현재 route 전환을 관찰해 `Home/Records → Settings` 진입마다 설정 방문 카운터를 증가시킨다.
  - 방문 카운터를 `SettingsScreen(scrollResetKey = ...)`으로 전달한다.
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt`
  - 설정 콘텐츠가 재진입 key 변경 시 목록을 처음 위치로 이동하는지 Compose UI 테스트를 추가한다.
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavHostTest.kt`
  - 설정 route 진입 여부에 따른 방문 카운터 decision table을 검증한다.

## Decision Checkpoint

코드 구조가 결정하는 범위를 벗어난 실행-level fork는 없다. 내비게이션 공통의 상태 저장/복원 옵션은 유지하면서, 이미 현재 route를 소유한 `OceNavHost`에서 설정 진입 신호만 전달하는 것이 외부 화면 복귀까지 잘못 초기화하지 않는 최소 변경이다.

### Task 1: 설정 탭 재진입 시 `LazyListState` 초기화

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt` (`SettingsScreen`, `SettingsContent`)
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt` (재진입 스크롤 동작 테스트)

**Interfaces:**
- Consumes: `OceNavHost`의 현재 destination route 전환, 설정 화면이 이미 사용하는 `SettingsUiState`와 기존 callback 집합.
- Produces: `SettingsContent(listState: LazyListState, scrollResetKey: Any, ...)`; `scrollResetKey`가 바뀌면 `listState`의 `firstVisibleItemIndex`와 `firstVisibleItemScrollOffset`가 0이 되는 동작 계약. `SettingsScreen`의 `ON_RESUME`는 알림 상태 갱신만 수행한다.

- [ ] **Step 1: 재진입 reset 동작을 검증하는 실패 테스트를 작성한다**

  `SettingsScreenScreenshotTest.kt`에 다음 테스트를 추가한다. 테스트는 실제 탭 전환을 흉내 내는 `scrollResetKey` 변경을 사용해 stateless `SettingsContent` seam에서 동작을 고정한다. 먼저 설정 목록을 위로 스와이프해 위치를 변경하고, key를 증가시킨 뒤 목록이 맨 위로 돌아오는지 검증한다.

  ```kotlin
  @Test
  fun `settings list returns to top when reentry key changes`() {
      var scrollResetKey by mutableIntStateOf(0)
      lateinit var listState: LazyListState

      composeRule.setContent {
          listState = rememberLazyListState()
          OceTheme {
              Surface(color = MaterialTheme.colorScheme.background) {
                  SettingsContent(
                      state = SettingsUiState(loading = false, nickname = "준영", isGuest = true),
                      versionLabel = "1.0.0 (1)",
                      notificationsBlocked = false,
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
                      listState = listState,
                      scrollResetKey = scrollResetKey,
                      reduceMotion = true,
                  )
              }
          }
      }
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(SETTINGS_SCROLL_CONTENT_TAG).performTouchInput {
          swipeUp(startY = 900f, endY = 100f, durationMillis = 300)
      }
      composeRule.waitForIdle()
      composeRule.runOnIdle {
          assertTrue(
              listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0,
          )
          scrollResetKey += 1
      }
      composeRule.waitForIdle()

      composeRule.runOnIdle {
          assertEquals(0, listState.firstVisibleItemIndex)
          assertEquals(0, listState.firstVisibleItemScrollOffset)
      }
  }
  ```

  필요한 import는 기존 import 목록에 다음을 추가한다.

  ```kotlin
  import androidx.compose.foundation.lazy.LazyListState
  import androidx.compose.foundation.lazy.rememberLazyListState
  import androidx.compose.runtime.mutableIntStateOf
  import androidx.compose.ui.test.performTouchInput
  import androidx.compose.ui.test.swipeUp
  import org.junit.Assert.assertTrue
  ```

- [ ] **Step 2: 테스트가 reset 구현 부재로 실패하는지 확인한다**

  Run:

  ```bash
  ./gradlew :app:testDebugUnitTest --tests com.jjundev.oneclickeng.feature.settings.SettingsScreenScreenshotTest
  ```

  Expected: 새 테스트가 `SettingsContent`에 `listState` 또는 `scrollResetKey` 파라미터가 없어서 컴파일되지 않거나, 파라미터를 먼저 추가한 경우 `firstVisibleItemIndex`가 0으로 돌아오지 않아 FAIL한다. 기존 설정 스크린샷 테스트의 실패가 있으면 새 동작과 분리해 원인을 확인한다.

- [x] **Step 3: `OceNavHost`가 설정 탭 진입 신호를 생성하고 `SettingsScreen`이 전달받도록 구현한다**

  `OceNavHost.kt`에서 `currentBackStackEntryAsState()`를 관찰하고, route가 설정으로 바뀌었을 때만 방문 카운터를 증가시킨다. 같은 route의 재선택이나 앱 resume은 카운터를 변경하지 않는다.

  ```kotlin
  val currentBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = currentBackStackEntry?.destination?.route
  var previousRoute by remember { mutableStateOf<String?>(null) }
  var settingsVisitCounter by remember { mutableIntStateOf(0) }
  LaunchedEffect(currentRoute) {
      settingsVisitCounter = nextSettingsVisitCounter(previousRoute, currentRoute, settingsVisitCounter)
      previousRoute = currentRoute
  }
  ```

  순수 counter helper는 다음 계약으로 두어 route decision table을 JVM 단위 테스트로 고정한다.

  ```kotlin
  internal fun nextSettingsVisitCounter(
      previousRoute: String?,
      currentRoute: String?,
      currentCounter: Int,
  ): Int =
      if (currentRoute == OceTab.Settings.route && previousRoute != currentRoute) {
          currentCounter + 1
      } else {
          currentCounter
      }
  ```

  `SettingsScreen`에는 다음 optional parameter를 추가하고, `SettingsContent(...)` 호출에 전달한다. 기존 `SettingsScreen()` 호출부의 호환성은 유지한다.

  ```kotlin
  fun SettingsScreen(
      modifier: Modifier = Modifier,
      viewModel: SettingsViewModel = hiltViewModel(),
      linkViewModel: GoogleLinkViewModel = hiltViewModel(),
      scrollResetKey: Any = Unit,
  )
  ```

  `SettingsScreen` 내부의 기존 `DisposableEffect(lifecycleOwner)`는 알림 상태 재조회와 스낵바 dismiss만 유지한다. 탭 전환과 무관한 앱 resume/외부 화면 복귀에서는 reset key를 변경하지 않는다.

  `SettingsContent` 시그니처에는 `LazyListState`와 reset key를 추가한다. 기본값을 두어 기존 스크린샷/프리뷰 호출부가 컴파일되도록 한다.

  ```kotlin
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
      listState: LazyListState = rememberLazyListState(),
      scrollResetKey: Any = Unit,
  ) {
  ```

- [x] **Step 4: reset key가 바뀔 때 목록을 맨 위로 이동하도록 구현한다**

  `SettingsContent`에서 `LazyColumn`을 만들기 전에 다음 effect를 추가하고, `LazyColumn`에 `state = listState`를 지정한다. `scrollToItem(0)`은 애니메이션 없이 실행되어 사용자가 설정 탭을 처음 렌더링한 것처럼 즉시 맨 위를 보게 한다.

  ```kotlin
  LaunchedEffect(scrollResetKey) {
      listState.scrollToItem(0)
  }
  ```

  `LazyColumn` modifier에는 테스트가 스크롤 surface를 안정적으로 찾을 수 있도록 기존 modifier 체인 끝에 다음 tag를 추가한다.

  ```kotlin
  modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp)
      .testTag(SETTINGS_SCROLL_CONTENT_TAG),
  state = listState,
  ```

  파일 하단의 기존 test tag 상수 영역에 다음 상수를 추가한다.

  ```kotlin
  internal const val SETTINGS_SCROLL_CONTENT_TAG = "settings_scroll_content"
  ```

- [ ] **Step 5: 동작 테스트와 전체 관련 테스트를 실행한다**

  Run:

  ```bash
  ./gradlew :app:testDebugUnitTest --tests com.jjundev.oneclickeng.feature.settings.SettingsScreenScreenshotTest
  ./gradlew :app:testDebugUnitTest --tests com.jjundev.oneclickeng.feature.settings.SettingsViewModelTest
  ./gradlew :app:testDebugUnitTest
  ```

  Expected: 설정 재진입 테스트와 기존 설정 테스트가 PASS하고, 전체 debug unit test도 PASS한다. 수동 확인 시 설정 탭을 아래로 스크롤한 뒤 학습 또는 기록 탭으로 이동하고 설정 탭으로 돌아왔을 때 프로필/최상단 섹션이 보이며, 학습·기록 탭은 기존 상태 복원 동작을 유지해야 한다.

- [ ] **Step 6: 변경을 하나의 작업으로 커밋한다**

  ```bash
  git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt
  git commit -m "fix: reset settings scroll on tab reentry"
  ```

## Self-Review

- Spec coverage: 설정 탭 재진입 시 맨 위로 이동하는 요구사항은 `nextSettingsVisitCounter`, `scrollResetKey`, `LaunchedEffect(scrollResetKey)`로 구현한다.
- Scope coverage: 학습·기록 탭의 공통 내비게이션 `saveState/restoreState`는 수정하지 않아 기존 동작을 보존한다.
- Placeholder scan: `TBD`, `TODO`, 추후 구현 지시, 미정 타입/함수 참조가 없다.
- Type consistency: `OceNavHost`가 route 기반 `Int` key를 만들고 `SettingsScreen`이 `Any` key로 전달하며, `SettingsContent`가 이를 `LaunchedEffect` key로 사용한다. 테스트 helper와 UI test가 동일한 reset 계약을 검증한다.

자동 계획 리뷰는 단일 Task 계획이므로 writing-plans 규칙에 따라 생략한다.

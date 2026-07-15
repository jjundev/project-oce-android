# 설정 탭 스낵바 재표시 방지 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 설정 탭에서 저장카드 정리 후 표시된 스낵바 메시지가 다른 탭을 다녀온 뒤 다시 표시되지 않도록 일회성 메시지를 화면 이탈에 안전하게 소비한다.

**Architecture:** 현재 `SettingsViewModel`이 `SettingsUiState.message`에 결과 메시지를 보관하고, `SettingsScreen`이 이를 스낵바로 표시한 뒤 소비한다. 메시지 전달 effect를 작은 내부 Composable로 분리하고, 스낵바 표시를 `try/finally`로 감싸 표시 코루틴이 정상 종료되거나 탭 전환으로 취소되는 모든 경우에 `consumeMessage()`가 실행되게 한다. 따라서 스낵바는 먼저 표시되고, 화면 이탈 시에도 ViewModel에는 재생할 메시지가 남지 않는다.

**Tech Stack:** Kotlin, Jetpack Compose runtime, Material 3 `SnackbarHostState`, Robolectric Compose UI tests, JUnit.

## Global Constraints

- 저장카드 삭제 성공 시 현재 스낵바 메시지와 표시 타이밍은 유지한다.
- 삭제 실패, 기록 초기화 실패, 로그아웃 실패 등 `SettingsMessage`의 다른 메시지에도 동일한 일회성 소비 semantics를 적용한다.
- 스낵바 컴포넌트, 문자열 리소스, 카드 정리 저장소 동작은 변경하지 않는다.
- 테스트는 기존 JVM Compose/Robolectric 테스트 구성을 사용하며 새로운 의존성을 추가하지 않는다.

---

## File Structure

- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt` — 설정 메시지 effect를 추출하고, 메시지를 스낵바 큐에 넣기 전에 소비하도록 화면 연결을 변경한다.
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt` — effect가 화면 이탈/재진입 시 메시지를 재표시하지 않는 회귀 테스트를 추가한다.

두 파일은 각각 메시지 전달 구현과 그 UI 생명주기 회귀 검증만 담당한다. `SettingsViewModel.kt`의 메시지 생성·소비 API와 `SettingsUiState.kt`의 모델은 이미 요구 동작을 표현하고 있으므로 수정하지 않는다.

## Decision Checkpoint

코드베이스가 `SnackbarHostState.showSnackbar`를 `SettingsScreen`에서 직접 호출하고 있고, 문제는 그 호출 뒤의 `consumeMessage()` 순서에서 발생한다. 별도 상태 저장소, 내비게이션 변경, 스낵바 API 변경이 필요한 실행 수준의 선택지는 없으므로 추가 질문 없이 진행한다.

### Task 1: Consume settings messages before snackbar suspension

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt:136-142` — 현재 인라인 `LaunchedEffect(messageText)`를 내부 effect Composable 호출로 교체하고 effect를 추가한다.
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt` — 스낵바 표시가 suspend된 상태에서 화면을 제거·재생성해도 한 번만 전달되는지 검증한다.

**Interfaces:**
- Consumes: `SettingsScreen`의 `messageText: String?`, `SnackbarHostState.showSnackbar`, `SettingsViewModel.consumeMessage`.
- Produces: `internal fun SettingsMessageEffect(messageText: String?, showSnackbar: suspend (String) -> SnackbarResult, consumeMessage: () -> Unit)`; 메시지가 non-null이면 `consumeMessage()`를 먼저 한 번 호출하고 이후 `showSnackbar(messageText)`를 호출한다.

- [ ] **Step 1: Write the failing regression test**

  `SettingsScreenScreenshotTest.kt`에 아래 테스트와 필요한 imports/state를 추가한다. 첫 스낵바 호출은 `CompletableDeferred`에서 대기하게 만들어, 기존 구현처럼 `showSnackbar`가 끝나기 전에는 메시지가 소비되지 않는 상황을 고정한다. `consumeMessage`가 메시지를 null로 만들면 재진입 시 effect가 다시 실행되지 않아야 한다.

  ```kotlin
  @Test
  fun settingsMessageIsConsumedBeforeSnackbarSuspensionAndDoesNotReplayAfterReentry() {
      val snackbarStarted = CompletableDeferred<Unit>()
      val releaseSnackbar = CompletableDeferred<Unit>()
      var visible by mutableStateOf(true)
      var message by mutableStateOf<String?>("카드를 삭제했어요.")
      var snackbarCalls = 0

      composeRule.setContent {
          if (visible) {
              SettingsMessageEffect(
                  messageText = message,
                  showSnackbar = {
                      snackbarCalls += 1
                      snackbarStarted.complete(Unit)
                      releaseSnackbar.await()
                      SnackbarResult.Dismissed
                  },
                  consumeMessage = { message = null },
              )
          }
      }

      composeRule.waitUntil { snackbarStarted.isCompleted }
      composeRule.runOnIdle {
          assertNull(message)
          assertEquals(1, snackbarCalls)
      }

      visible = false
      composeRule.waitForIdle()
      visible = true
      composeRule.waitForIdle()
      assertEquals(1, snackbarCalls)

      releaseSnackbar.complete(Unit)
  }
  ```

  Add these imports to the test file:

  ```kotlin
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.setValue
  import androidx.compose.material3.SnackbarResult
  import kotlinx.coroutines.CompletableDeferred
  import org.junit.Assert.assertEquals
  import org.junit.Assert.assertNull
  ```

- [ ] **Step 2: Run the focused test and verify it fails**

  Run:

  ```bash
  cd android
  ./gradlew :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.settings.SettingsScreenScreenshotTest.settingsMessageIsConsumedBeforeSnackbarSuspensionAndDoesNotReplayAfterReentry'
  ```

  Expected before the fix: FAIL because the existing effect calls `showSnackbar(messageText)` before `viewModel.consumeMessage()`, so `message` remains non-null while the snackbar is suspended.

- [ ] **Step 3: Extract the effect and consume cancelled snackbar messages**

  In `SettingsScreen.kt`, import `SnackbarResult`, replace the existing result-message block:

  ```kotlin
  val messageText = state.message?.let { settingsMessageText(it) }
  LaunchedEffect(messageText) {
      if (messageText != null) {
          snackbarHostState.showSnackbar(messageText)
          viewModel.consumeMessage()
      }
  }
  ```

  with:

  ```kotlin
  val messageText = state.message?.let { settingsMessageText(it) }
  SettingsMessageEffect(
      messageText = messageText,
      showSnackbar = snackbarHostState::showSnackbar,
      consumeMessage = viewModel::consumeMessage,
  )
  ```

  Add this internal, testable effect near `settingsMessageText`. The snackbar call must happen before consumption so the first popup is not cancelled by the state update; `finally` consumes the message both after normal dismissal and when the effect is cancelled by tab navigation:

  ```kotlin
  @Composable
  internal fun SettingsMessageEffect(
      messageText: String?,
      showSnackbar: suspend (String) -> SnackbarResult,
      consumeMessage: () -> Unit,
  ) {
      LaunchedEffect(messageText) {
          if (messageText != null) {
              try {
                  showSnackbar(messageText)
              } finally {
                  consumeMessage()
              }
          }
      }
  }
  ```

  Keep the existing link-failure effect unchanged; this change only establishes consume-before-suspend ordering for messages owned by `SettingsViewModel`.

- [ ] **Step 4: Run the focused test and the settings test suite**

  Run the regression test again:

  ```bash
  cd android
  ./gradlew :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.settings.SettingsScreenScreenshotTest.settingsMessageIsConsumedBeforeSnackbarSuspensionAndDoesNotReplayAfterReentry'
  ```

  Expected: PASS.

  Then run all settings JVM tests:

  ```bash
  ./gradlew :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.settings.*'
  ```

  Expected: PASS, with existing screenshot baselines unchanged because no visual layout or string changes were made.

- [ ] **Step 5: Commit the focused fix**

  ```bash
  git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt
  git commit -m "fix: prevent settings snackbar replay after tab switch"
  ```

## Self-Review

- Spec coverage: the successful saved-card purge snackbar still appears once; leaving and returning to the settings tab cannot replay it; the same lifecycle safety applies to all `SettingsMessage` variants.
- Placeholder scan: no `TBD`, `TODO`, or unspecified implementation step is used; all changed files, commands, expected results, and code interfaces are explicit.
- Type consistency: `SettingsMessageEffect.showSnackbar` uses `suspend (String) -> SnackbarResult`, matching `SnackbarHostState.showSnackbar`; `consumeMessage` is a zero-argument callback matching `SettingsViewModel.consumeMessage`.

## Execution Handoff

Plan complete and saved to `docs/plans/2026-07-15-settings-snackbar-replay.md`. This is a one-task plan, so the automatic subagent plan review is skipped as permitted by the writing-plans skill.

Two execution options:

1. **Subagent-Driven (recommended)** — dispatch a fresh subagent for the task and review the result.
2. **Inline Execution** — execute the task in this session using executing-plans with a checkpoint.

Which approach?

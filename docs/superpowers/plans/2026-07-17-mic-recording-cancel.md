# Mic Recording Cancel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** While a learner is mid-recording on the dialogue learning screen, give them a way to cancel/discard the recording and start over, instead of being forced to stop-and-submit or wait it out.

**Architecture:** The dock's bottom toggle label already swaps text depending on mode (`MicDock.kt`'s `InputModeToggle`). We add a third branch: when `micState == MicState.Recording`, the toggle shows "다시 말하기" instead of "채팅으로 입력하기" and calls a new `onCancelRecording` callback instead of `onToggleTextMode(true)`. That callback is wired to a new `GeneratedDialogueSessionViewModel.onCancelRecording()` method that stops the in-flight `RecordingController` capture, discards its result (never calls `speaking.analyze(...)`), and resets `micState` back to `Ready` so the mic button is tappable again to start a fresh recording.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt ViewModel, Robolectric + Compose UI testing (`createComposeRule`), Roborazzi (screenshot capture only, no checked-in goldens to diff against).

## Global Constraints

- All UI copy is hardcoded Korean string literals in this screen today (no `strings.xml` resources exist for the dialogue/mic screen) — follow that existing convention, do not introduce a new `strings.xml` entry for "다시 말하기".
- `MicDock`/`MicColumn`/`MicSessionDock` are all `internal` — keep the new `onCancelRecording` parameter `internal`-visible only (no new public API surface).
- Any new test file using `createComposeRule()` MUST be added to the Release-variant test exclusion list in `android/app/build.gradle.kts` (compose-ui-test-manifest only merges into the debug manifest — Release unit tests using `createComposeRule()` fail with a missing `ComponentActivity` otherwise). This is a documented, recurring gotcha in that file's comments.
- Gradle verification in this worktree MUST go through `scripts/verify-android.sh` (never bare `./gradlew`) — it isolates `GRADLE_USER_HOME` per worktree and copies `google-services.json` from the main worktree, both of which are required for tests to compile/run correctly here. See `docs/agents/android-verification.md`.
- No `RecordingController` fake and no `GeneratedDialogueSessionViewModel` unit-test harness exist anywhere in this codebase today (confirmed by search), and no mocking library (MockK/Mockito) is a project dependency. Building a full 9-dependency fake harness solely to unit-test one 4-line cancel method is disproportionate and would set an unrequested new testing precedent. This plan does NOT add one — `onCancelRecording()` is verified via the Compose-level callback-wiring test (Task 2) and manual on-device verification (Task 3), matching this class's existing (zero) unit-test coverage baseline.

---

### Task 1: Add `onCancelRecording()` to `GeneratedDialogueSessionViewModel`

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt:559` (insert after `stopRecording()`, before `onAnalysisState()`)

**Interfaces:**
- Consumes: `recording: RecordingController` (existing constructor property, `RecordingController.stop(): RecordingResult` at `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/audio/RecordingController.kt:26`); `micState` (existing `MicState`-typed property, settable via private setter within the class); `MicState.Recording` / `MicState.Ready` (existing enum values, `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/audio/MicState.kt:17-18`).
- Produces: `fun onCancelRecording()` — no-arg, callable from Compose as a method reference (`viewModel::onCancelRecording`), matching the existing `onAdvance`/`onMicTap` pattern. Guards on `micState == MicState.Recording` (no-op otherwise, mirroring the defensive no-op in `onMicTap()` at line 512).

- [ ] **Step 1: Add the `onCancelRecording()` method**

Open `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt`. Find `stopRecording()`, which currently ends like this:

```kotlin
        private fun stopRecording() {
            viewModelScope.launch {
                when (val result = recording.stop()) {
                    is RecordingResult.Captured -> {
                        val sid = currentSessionId()
                        if (sid != null) {
                            pendingClip = result // 전사 성공 시 append 되는 말풍선에 붙일 자기 녹음
                            micState = MicState.Analyzing
                            speaking.analyze(result, sid)
                        } else {
                            micState = MicState.Ready
                            retryHint = HINT_ERROR
                        }
                    }
                    RecordingResult.TooQuiet -> {
                        micState = MicState.Ready
                        retryHint = HINT_RETRY
                    }
                    is RecordingResult.Failed -> {
                        micState = MicState.Ready
                        retryHint = HINT_ERROR
                    }
                }
            }
        }

        // 우리 분석(micState=Analyzing)에만 반응 — Singleton 의 이전 세션 잔여 상태 오반응 차단.
        private fun onAnalysisState(state: SpeakingAnalysisState) {
```

Insert a new `onCancelRecording()` function between them:

```kotlin
        private fun stopRecording() {
            viewModelScope.launch {
                when (val result = recording.stop()) {
                    is RecordingResult.Captured -> {
                        val sid = currentSessionId()
                        if (sid != null) {
                            pendingClip = result // 전사 성공 시 append 되는 말풍선에 붙일 자기 녹음
                            micState = MicState.Analyzing
                            speaking.analyze(result, sid)
                        } else {
                            micState = MicState.Ready
                            retryHint = HINT_ERROR
                        }
                    }
                    RecordingResult.TooQuiet -> {
                        micState = MicState.Ready
                        retryHint = HINT_RETRY
                    }
                    is RecordingResult.Failed -> {
                        micState = MicState.Ready
                        retryHint = HINT_ERROR
                    }
                }
            }
        }

        /**
         * 도크 "다시 말하기" 탭 — 녹음 취소. [stopRecording] 과 달리 결과를 버리고 Analyzing/분석에 진입하지
         * 않는다. Ready 로 되돌리면 [MicButton] 이 이미 tappable(enabled=Ready||Recording)이라 재녹음은
         * 추가 배선 없이 바로 가능하다.
         */
        fun onCancelRecording() {
            if (micState != MicState.Recording) return
            viewModelScope.launch {
                recording.stop()
                micState = MicState.Ready
            }
        }

        // 우리 분석(micState=Analyzing)에만 반응 — Singleton 의 이전 세션 잔여 상태 오반응 차단.
        private fun onAnalysisState(state: SpeakingAnalysisState) {
```

- [ ] **Step 2: Compile-check**

Run:
```bash
scripts/verify-android.sh :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`. This confirms the new method compiles against the existing `RecordingController`/`MicState` types before any UI wires up to it (Task 2 will fail to compile until this method exists, since `MicSessionDock` will reference `viewModel::onCancelRecording`).

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt
git commit -m "feat(dialogue): add onCancelRecording to discard an in-flight recording"
```

---

### Task 2: Add "다시 말하기" cancel toggle to `MicDock`/`MicColumn`, wire the ViewModel, update call sites

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDock.kt` (add `onCancelRecording` param to `MicDock`/`MicColumn`, branch the toggle label, wire `MicSessionDock`)
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDockTogglePositionTest.kt:50-64` (add `onCancelRecording = {}` to the existing `MicDock(...)` call)
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreenshotTest.kt:153-167,185-199` (add `onCancelRecording = {}` to both existing `MicDock(...)` calls)
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionFlowScreenshotTest.kt:95-109,143-157` (add `onCancelRecording = {}` to both existing `MicDock(...)` calls)
- Modify: `android/app/build.gradle.kts:91` (register the new test file in the Release-variant `createComposeRule()` exclusion list)
- Test: Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDockCancelRecordingTest.kt`

**Interfaces:**
- Consumes: `MicState.Recording` (`android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/audio/MicState.kt:18`); `OceIcon.Refresh` (`android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/OneClickIcon.kt:109`); `GeneratedDialogueSessionViewModel.onCancelRecording()` from Task 1; the existing `private fun InputModeToggle(icon: OceIcon, label: String, onClick: () -> Unit, topGap: Dp = OceTheme.spacing.md)` composable already defined in `MicDock.kt:317-346`.
- Produces: `MicDock(..., onCancelRecording: () -> Unit, ...)` — new required parameter on the existing `internal fun MicDock(...)` composable signature. Every call site across production and test code must pass it.

- [ ] **Step 1: Write the failing test**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDockCancelRecordingTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.turn

import android.app.Application
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.jjundev.oneclickeng.ui.audio.MicState
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 녹음 중("다시 말하기") 취소 어피던스 검증. Recording 상태에서는 하단 토글이 "채팅으로 입력하기" 대신
 * "다시 말하기"로 바뀌고, 탭하면 [onCancelRecording] 콜백이 호출돼야 한다(다른 상태는 기존 문구 유지).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class MicDockCancelRecordingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val task = ScaffoldTask("라떼 한 잔을 주문해보세요")
    private val waveform = MutableStateFlow(FloatArray(0))

    private fun setDock(micState: MicState, onCancelRecording: () -> Unit) {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                MicDock(
                    task = task,
                    micState = micState,
                    waveform = waveform,
                    textMode = false,
                    textValue = "",
                    retryHint = null,
                    permanentlyDenied = false,
                    reduceMotion = true,
                    onMicTap = {},
                    onAdvance = {},
                    onCancelRecording = onCancelRecording,
                    onToggleTextMode = {},
                    onTextChange = {},
                    onSubmitText = {},
                )
            }
        }
    }

    @Test
    fun recording_state_shows_cancel_label_instead_of_chat_toggle() {
        setDock(MicState.Recording, onCancelRecording = {})

        composeRule.onNodeWithText("다시 말하기", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("채팅으로 입력하기", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun tapping_cancel_label_invokes_callback() {
        var cancelCount = 0
        setDock(MicState.Recording, onCancelRecording = { cancelCount++ })

        composeRule.onNodeWithText("다시 말하기", useUnmergedTree = true).performClick()

        assertEquals(1, cancelCount)
    }

    @Test
    fun ready_state_keeps_chat_toggle() {
        setDock(MicState.Ready, onCancelRecording = {})

        composeRule.onNodeWithText("채팅으로 입력하기", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("다시 말하기", useUnmergedTree = true).assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*MicDockCancelRecordingTest*'
```
Expected: **compile failure** (not a runtime assertion failure) — `MicDock(...)` does not yet have an `onCancelRecording` parameter, so the call in the new test does not type-check. The error looks like:
```
e: ... MicDockCancelRecordingTest.kt:29:17 no value passed for parameter 'onCancelRecording'
```
or, once `onCancelRecording` exists on `MicDock` but before the branch logic is added, the assertions will fail at runtime instead (`다시 말하기` node not found) — either failure mode confirms Step 3 below is not yet done.

- [ ] **Step 3: Add `onCancelRecording` param + branch logic to `MicDock`/`MicColumn`**

Open `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDock.kt`.

Change the `MicDock` signature (currently lines 144-158):

```kotlin
@Composable
internal fun MicDock(
    task: ScaffoldTask,
    micState: MicState,
    waveform: StateFlow<FloatArray>,
    textMode: Boolean,
    textValue: String,
    retryHint: String?,
    permanentlyDenied: Boolean,
    reduceMotion: Boolean,
    onMicTap: () -> Unit,
    onAdvance: () -> Unit,
    onToggleTextMode: (Boolean) -> Unit,
    onTextChange: (String) -> Unit,
    onSubmitText: () -> Unit,
    modifier: Modifier = Modifier,
) {
```

to:

```kotlin
@Composable
internal fun MicDock(
    task: ScaffoldTask,
    micState: MicState,
    waveform: StateFlow<FloatArray>,
    textMode: Boolean,
    textValue: String,
    retryHint: String?,
    permanentlyDenied: Boolean,
    reduceMotion: Boolean,
    onMicTap: () -> Unit,
    onAdvance: () -> Unit,
    onCancelRecording: () -> Unit,
    onToggleTextMode: (Boolean) -> Unit,
    onTextChange: (String) -> Unit,
    onSubmitText: () -> Unit,
    modifier: Modifier = Modifier,
) {
```

Update the `MicColumn(...)` call inside `MicDock`'s body (currently lines 174-183):

```kotlin
            MicColumn(
                micState = micState,
                waveform = waveform,
                retryHint = retryHint,
                permanentlyDenied = permanentlyDenied,
                reduceMotion = reduceMotion,
                onMicTap = onMicTap,
                onAdvance = onAdvance,
                onToggleTextMode = onToggleTextMode,
            )
```

to:

```kotlin
            MicColumn(
                micState = micState,
                waveform = waveform,
                retryHint = retryHint,
                permanentlyDenied = permanentlyDenied,
                reduceMotion = reduceMotion,
                onMicTap = onMicTap,
                onAdvance = onAdvance,
                onCancelRecording = onCancelRecording,
                onToggleTextMode = onToggleTextMode,
            )
```

Change the `MicColumn` signature (currently lines 189-197):

```kotlin
@Composable
private fun MicColumn(
    micState: MicState,
    waveform: StateFlow<FloatArray>,
    retryHint: String?,
    permanentlyDenied: Boolean,
    reduceMotion: Boolean,
    onMicTap: () -> Unit,
    onAdvance: () -> Unit,
    onToggleTextMode: (Boolean) -> Unit,
) {
```

to:

```kotlin
@Composable
private fun MicColumn(
    micState: MicState,
    waveform: StateFlow<FloatArray>,
    retryHint: String?,
    permanentlyDenied: Boolean,
    reduceMotion: Boolean,
    onMicTap: () -> Unit,
    onAdvance: () -> Unit,
    onCancelRecording: () -> Unit,
    onToggleTextMode: (Boolean) -> Unit,
) {
```

Change the toggle branch inside `MicColumn`'s body (currently lines 239-255):

```kotlin
            if (micState == MicState.Complete) {
                Button(
                    onClick = onAdvance,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
                    shape = OceTheme.shapes.radius12,
                ) {
                    Text(text = "다음", style = OceTheme.typography.sectionLabel)
                }
            } else {
                InputModeToggle(
                    icon = OceIcon.Keyboard,
                    label = "채팅으로 입력하기",
                    onClick = { onToggleTextMode(true) },
                    // 마이크 모드: 상태 문구와 밀착(중앙정렬로 생긴 텍스트 위 여백 상쇄) — 토글은 도크 하단 고정.
                    topGap = 0.dp,
                )
            }
```

to:

```kotlin
            if (micState == MicState.Complete) {
                Button(
                    onClick = onAdvance,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
                    shape = OceTheme.shapes.radius12,
                ) {
                    Text(text = "다음", style = OceTheme.typography.sectionLabel)
                }
            } else if (micState == MicState.Recording) {
                InputModeToggle(
                    icon = OceIcon.Refresh,
                    label = "다시 말하기",
                    onClick = onCancelRecording,
                    // 마이크 모드: 상태 문구와 밀착(중앙정렬로 생긴 텍스트 위 여백 상쇄) — 토글은 도크 하단 고정.
                    topGap = 0.dp,
                )
            } else {
                InputModeToggle(
                    icon = OceIcon.Keyboard,
                    label = "채팅으로 입력하기",
                    onClick = { onToggleTextMode(true) },
                    // 마이크 모드: 상태 문구와 밀착(중앙정렬로 생긴 텍스트 위 여백 상쇄) — 토글은 도크 하단 고정.
                    topGap = 0.dp,
                )
            }
```

Finally, wire `MicSessionDock`'s `MicDock(...)` call (currently lines 108-123):

```kotlin
    MicDock(
        task = task,
        micState = viewModel.micState,
        waveform = viewModel.waveform,
        textMode = viewModel.textMode,
        textValue = viewModel.textValue,
        retryHint = viewModel.retryHint,
        permanentlyDenied = permanentlyDenied,
        reduceMotion = reduceMotion,
        onMicTap = ::handleMicTap,
        onAdvance = viewModel::onAdvance,
        onToggleTextMode = viewModel::onToggleTextMode,
        onTextChange = viewModel::onTextChange,
        onSubmitText = viewModel::onSubmitText,
        modifier = modifier,
    )
```

to:

```kotlin
    MicDock(
        task = task,
        micState = viewModel.micState,
        waveform = viewModel.waveform,
        textMode = viewModel.textMode,
        textValue = viewModel.textValue,
        retryHint = viewModel.retryHint,
        permanentlyDenied = permanentlyDenied,
        reduceMotion = reduceMotion,
        onMicTap = ::handleMicTap,
        onAdvance = viewModel::onAdvance,
        onCancelRecording = viewModel::onCancelRecording,
        onToggleTextMode = viewModel::onToggleTextMode,
        onTextChange = viewModel::onTextChange,
        onSubmitText = viewModel::onSubmitText,
        modifier = modifier,
    )
```

- [ ] **Step 4: Update the five other existing `MicDock(...)` call sites so the module compiles**

In `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDockTogglePositionTest.kt`, the `MicDock(...)` call (lines 50-64) currently ends:

```kotlin
                            MicDock(
                                task = task,
                                micState = MicState.Ready,
                                waveform = waveform,
                                textMode = textMode,
                                textValue = "",
                                retryHint = null,
                                permanentlyDenied = false,
                                reduceMotion = true,
                                onMicTap = {},
                                onAdvance = {},
                                onToggleTextMode = {},
                                onTextChange = {},
                                onSubmitText = {},
                            )
```

Add `onCancelRecording = {},` after `onAdvance = {},`:

```kotlin
                            MicDock(
                                task = task,
                                micState = MicState.Ready,
                                waveform = waveform,
                                textMode = textMode,
                                textValue = "",
                                retryHint = null,
                                permanentlyDenied = false,
                                reduceMotion = true,
                                onMicTap = {},
                                onAdvance = {},
                                onCancelRecording = {},
                                onToggleTextMode = {},
                                onTextChange = {},
                                onSubmitText = {},
                            )
```

In `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreenshotTest.kt`, apply the same `onCancelRecording = {},` addition (after `onAdvance = {},`) to **both** `MicDock(...)` calls: the one inside `captureLearner(...)` (lines 153-167) and the one inside `captureRecording(...)` (lines 185-199).

In `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionFlowScreenshotTest.kt`, apply the same addition to **both** `MicDock(...)` calls: the one inside `captureDock(...)` (lines 95-109) and the one inside `flow_text_input_light()` (lines 143-157).

- [ ] **Step 5: Register the new test in the Release-variant exclusion list**

Open `android/app/build.gradle.kts`. Find the `exclude(...)` block (starts at line 77) and add the new test class next to its sibling `MicDockTogglePositionTest`:

```kotlin
            "**/MicDockTogglePositionTest*",
            "**/DeepFeedbackRegionTest*",
```

to:

```kotlin
            "**/MicDockTogglePositionTest*",
            "**/MicDockCancelRecordingTest*",
            "**/DeepFeedbackRegionTest*",
```

- [ ] **Step 6: Run the new test and the existing mic-dock tests to verify they pass**

Run:
```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*MicDockCancelRecordingTest*' --tests '*MicDockTogglePositionTest*' --tests '*DialogueTurnScreenshotTest*' --tests '*SessionFlowScreenshotTest*'
```
Expected: `BUILD SUCCESSFUL`, all tests green (`MicDockCancelRecordingTest`'s 3 tests plus the pre-existing tests in the other 3 files, now compiling with the new required parameter).

- [ ] **Step 7: Full worktree verification**

Run:
```bash
scripts/verify-android.sh
```
Expected: `BUILD SUCCESSFUL` (detekt + androidTest compile + both unit test variants, including the Release-variant exclusion added in Step 5).

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDock.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDockCancelRecordingTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDockTogglePositionTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreenshotTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionFlowScreenshotTest.kt \
        android/app/build.gradle.kts
git commit -m "feat(dialogue): show \"다시 말하기\" cancel toggle while recording"
```

---

### Task 3: Manual on-device verification

**Files:** none (no code changes — this task drives the real app to confirm the audio-layer behavior that Task 1/2's tests cannot exercise: real `AudioRecord` capture/teardown, and the actual mic button re-enabling after cancel).

**Interfaces:**
- Consumes: the fully wired feature from Tasks 1-2 (installed debug build).
- Produces: nothing — verification only.

- [ ] **Step 1: Build and install the debug APK**

Run:
```bash
scripts/verify-android.sh :app:installDebug
```
Expected: `BUILD SUCCESSFUL`, app installed on the connected device/emulator (a device/emulator must already be connected — if none is available, run `:app:assembleDebug` instead and install manually).

- [ ] **Step 2: Start a dialogue session and begin recording**

Launch the app, start (or resume) a 대화 학습 session, reach a learner turn, and tap the mic button once.

Expected: mic button turns to the Recording (red) visual, waveform animates, and the bottom label reads **"다시 말하기"** (not "채팅으로 입력하기").

- [ ] **Step 3: Cancel the recording**

Tap "다시 말하기".

Expected: recording stops immediately (waveform disappears), the mic button reverts to the Ready (grey) visual, the bottom label reverts to "채팅으로 입력하기", and — critically — **no** "분석 중" (Analyzing) state or turn feedback sheet appears (confirms the recording was discarded, not submitted).

- [ ] **Step 4: Confirm a fresh recording can start immediately**

Tap the mic button again.

Expected: recording starts normally (Recording visual + waveform resume, label switches back to "다시 말하기"), i.e. the button was never left disabled after cancel.

- [ ] **Step 5: Confirm the normal stop-and-submit path is unaffected**

Start a new recording, speak briefly, then tap the **mic button itself** (not "다시 말하기") to stop it normally.

Expected: existing behavior unchanged — mic transitions to "분석 중" (Analyzing) and then to the turn feedback flow, exactly as before this change.

---

## Self-Review

**Spec coverage:**
- "녹음 중인 경우, 취소할 수 있는 방법이 존재하지 않는다" → addressed by the new `onCancelRecording()` cancel path (Tasks 1-2).
- "녹음 버튼 하단의 텍스트를 '채팅으로 입력하기' -> '다시 말하기'로 바꾸자" → addressed by the `MicState.Recording` branch in `MicColumn` (Task 2, Step 3), verified by `MicDockCancelRecordingTest.recording_state_shows_cancel_label_instead_of_chat_toggle` and `ready_state_keeps_chat_toggle`.
- "다시 말하기를 클릭하면, 녹음이 중지되고" → `onCancelRecording()` calls `recording.stop()` (Task 1), discarding the result instead of routing to `speaking.analyze(...)`.
- "다시 녹음을 시작할 수 있도록 녹음 시작 버튼이 다시 비활성화된다(다시 누르면 녹음 시작)" → `micState` resets to `Ready`, and `MicButton`'s existing `enabled = micState == MicState.Ready || micState == MicState.Recording` (unchanged) already makes the Ready-state button tappable to start a new recording — verified manually in Task 3, Steps 3-4 (no code change needed to `MicButton` itself).

**Placeholder scan:** no TBD/TODO, no "add error handling" hand-waving, no "similar to Task N" — every step shows complete before/after code.

**Type consistency:** `onCancelRecording: () -> Unit` is named identically across `MicDock`, `MicColumn`, `MicSessionDock`, `GeneratedDialogueSessionViewModel.onCancelRecording()`, and all six call sites (production + 5 test call sites + the new test file). `MicState.Recording`/`MicState.Ready` usage matches the existing enum in `MicState.kt`.

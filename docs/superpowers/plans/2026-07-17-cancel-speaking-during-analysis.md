# Cancel Speaking During Analysis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing "처음부터 말하기" cancel affordance to the Analyzing phase ("말한 내용을 다듬는 중이에요…"), so a learner can abandon a speaking attempt while the LLM transcription/analysis is still in flight — and guarantee the turn feedback sheet never rises afterwards from that abandoned request.

**Architecture:** The cancel affordance already exists for `MicState.Recording` (`MicDock.kt`'s toggle ladder → `GeneratedDialogueSessionViewModel.onCancelRecording()`). This plan widens both ends. On the UI side, the toggle branch fires for `Recording || Analyzing`. On the ViewModel side, the method is renamed `onCancelSpeaking()` (it now spans the whole record→analyze pipeline, not just recording) and gains an Analyzing branch that calls `speaking.reset()` — cancelling the in-flight job so the LLM `Result` is never emitted, which is what keeps `triggerFeedback(...)` (the sole entry point that raises the feedback sheet) from ever running. The pre-existing `micState != MicState.Analyzing` guard at the top of `onAnalysisState` remains as the second layer, absorbing the narrow race where a cancelled coroutine still writes its `_state` value before dying.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt ViewModel, kotlinx-coroutines (+ `kotlinx-coroutines-test` virtual time), Robolectric + Compose UI testing (`createComposeRule`), Roborazzi (screenshot capture only; no checked-in goldens).

## Global Constraints

- All UI copy on this screen is hardcoded Korean string literals (no `strings.xml` entries exist for the dialogue/mic screen) — follow that convention; do not introduce a resource for "처음부터 말하기".
- `MicDock`/`MicColumn`/`MicSessionDock` are `internal`; `InputModeToggle` is `private`. Keep the renamed `onCancelSpeaking` parameter `internal`-visible only — no new public API surface.
- Any new test file using `createComposeRule()` MUST be registered in the Release-variant exclusion list in `android/app/build.gradle.kts` (compose-ui-test-manifest merges only into the debug manifest; Release unit tests otherwise fail on a missing `ComponentActivity`). Renaming an already-registered test class means renaming its exclusion entry too.
- Gradle verification in this worktree MUST go through `scripts/verify-android.sh` (never bare `./gradlew`) — it isolates `GRADLE_USER_HOME` per worktree and copies `google-services.json` from the main worktree. See `docs/agents/android-verification.md`.
- `scripts/verify-android.sh` with no args does **not** currently return a clean `BUILD SUCCESSFUL` in this worktree, for two pre-existing reasons unrelated to this work: a detekt `LoopWithTooManyJumpStatements` violation at `SettingsScreen.kt:224`, and `RecordsSkeletonTest`/`RecordsSkeletonMinHoldTest` missing from the Release-variant exclusion list. Do **not** fix these here. Verify with the targeted task commands given in each task; treat only *new* failures as yours.
- No `GeneratedDialogueSessionViewModel` unit-test harness and no mocking library (MockK/Mockito) exist in this codebase; the VM has 9 constructor dependencies. Per the precedent set in `docs/superpowers/plans/2026-07-17-mic-recording-cancel.md`, this plan does **not** build one. The "late LLM response must be ignored" requirement is instead pinned by a test at the `SpeakingAnalysisCoordinator` seam (which has an existing test file and `FakeLlmApi`), plus manual on-device verification.

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt` | Owns settled `MicState` + the record→analyze→complete loop | Rename `onCancelRecording()` → `onCancelSpeaking()`; add the Analyzing branch (`speaking.reset()`), discard `pendingClip` |
| `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDock.kt` | Stateless mic dock + VM wiring | Rename the `onCancelRecording` parameter (both composables + the `MicSessionDock` wiring); widen the toggle branch to `Recording \|\| Analyzing` |
| `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/speaking/SpeakingAnalysisCoordinatorTest.kt` | Coordinator state-machine contract | Add: reset-during-in-flight discards the late response |
| `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDockCancelSpeakingTest.kt` | Cancel affordance UI contract | Renamed from `MicDockCancelRecordingTest.kt`; add Analyzing + Complete cases |
| `android/app/src/test/kotlin/.../{MicDockTogglePositionTest,DialogueTurnScreenshotTest,SessionFlowScreenshotTest}.kt` | Pre-existing `MicDock(...)` call sites | Mechanical parameter rename only (5 call sites) |
| `android/app/build.gradle.kts` | Build config | Rename the Release-exclusion entry for the renamed test class |

---

### Task 1: Rename to `onCancelSpeaking()` and cancel the in-flight analysis

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt:561-570`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDock.kt` (lines 119, 156, 184, 200, 255 — parameter rename only, no branch change yet)
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDockTogglePositionTest.kt:61`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreenshotTest.kt:164,197`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionFlowScreenshotTest.kt:106,155`
- Rename: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDockCancelRecordingTest.kt` → `MicDockCancelSpeakingTest.kt` (class + `onCancelRecording` param references inside)
- Modify: `android/app/build.gradle.kts:92` (Release-exclusion entry rename)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/speaking/SpeakingAnalysisCoordinatorTest.kt` (add one test)

**Interfaces:**
- Consumes: `speaking: SpeakingAnalysisCoordinator` (existing constructor property) and its `fun reset()` (`SpeakingAnalysisCoordinator.kt:115` — cancels the in-flight job, bumps the monotonic `sessionToken`, clears `lastTranscript`, sets state to `Idle`); `recording: RecordingController.stop(): RecordingResult`; `pendingClip: RecordingResult.Captured?` (private VM field, `GeneratedDialogueSession.kt:272`); `MicState.Ready`/`Recording`/`Analyzing`/`Complete`.
- Produces: `fun onCancelSpeaking()` — no-arg, public on the VM, callable as `viewModel::onCancelSpeaking`. **Replaces** `onCancelRecording()`, which ceases to exist. `MicDock`/`MicColumn` expose it as the `onCancelSpeaking: () -> Unit` parameter (Task 2 consumes this name in its branch change).

- [ ] **Step 1: Write the failing test — the coordinator must discard a late response after reset**

Open `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/speaking/SpeakingAnalysisCoordinatorTest.kt`. Find the existing last test, which currently reads:

```kotlin
    @Test
    fun `reset returns to Idle and clears transcript`() =
        runTest {
            val api = FakeLlmApi(response = result("hi"))
            val coordinator = SpeakingAnalysisCoordinator(api, coordScope())

            coordinator.analyze(captured(), "s1")
            advanceUntilIdle()
            coordinator.reset()
            advanceUntilIdle()

            assertEquals(SpeakingAnalysisState.Idle, coordinator.state.value)
            assertNull(coordinator.transcript())
        }
```

Insert a new test directly after it (before the `coordScope()` helper):

```kotlin
    @Test
    fun `reset during an in-flight analysis discards the late response`() =
        runTest {
            // 분석 중 "처음부터 말하기" 취소의 계약. 위 테스트는 분석이 **끝난 뒤** 리셋하지만, 취소는 왕복
            // 중에 일어난다 — 뒤늦게 도착한 응답이 Result 로 새어나오면 GeneratedDialogueSessionViewModel 의
            // triggerFeedback 이 돌아 취소한 턴의 피드백 시트가 떠오른다. 취소 후에는 Idle 로 남아야 한다.
            val api = FakeLlmApi(response = result("late"), delayMs = 5_000)
            val coordinator = SpeakingAnalysisCoordinator(api, coordScope())

            coordinator.analyze(captured(), "s1")
            runCurrent() // 요청 in-flight(응답 전)
            coordinator.reset()
            advanceUntilIdle() // in-flight 요청의 지연이 다 흐르도록 — 응답이 와도 무시돼야 한다

            assertEquals(SpeakingAnalysisState.Idle, coordinator.state.value)
            assertNull(coordinator.transcript())
        }
```

**Honest note for the implementer and reviewer:** this test is expected to **pass on the current code** — `reset()` already cancels `currentJob` and bumps `sessionToken`. It is not a RED-first test. It is a contract/regression test that pins the exact guarantee the new Analyzing-cancel path depends on, at the only seam in this codebase where that guarantee is testable. Do not "fix" the coordinator to make it fail first. If it *fails*, stop and report — that means the cancel design in Step 3 is unsound and the plan needs revisiting.

- [ ] **Step 2: Run the new coordinator test**

Run:
```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*SpeakingAnalysisCoordinatorTest*'
```
Expected: `BUILD SUCCESSFUL`, all tests pass including the new one (see the note in Step 1 — passing here is the expected, correct outcome).

- [ ] **Step 3: Rename and extend the ViewModel method**

Open `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt`. The method currently reads:

```kotlin
        /**
         * 도크 "처음부터 말하기" 탭 — 녹음 취소. [stopRecording] 과 달리 결과를 버리고 Analyzing/분석에 진입하지
         * 않는다. Ready 로 되돌리면 [MicButton] 이 이미 tappable(enabled=Ready||Recording)이라 재녹음은
         * 추가 배선 없이 바로 가능하다.
         */
        fun onCancelRecording() {
            if (micState != MicState.Recording) return
            micState = MicState.Ready
            viewModelScope.launch { recording.stop() }
        }
```

Replace it with:

```kotlin
        /**
         * 도크 "처음부터 말하기" 탭 — 이번 발화 시도를 통째로 버린다(녹음 중·분석 중 공용).
         *
         * - Recording: 캡처를 멈추고 결과를 버린다([stopRecording] 과 달리 Analyzing 으로 넘어가지 않는다).
         * - Analyzing: 진행 중인 전사/LLM 왕복을 [SpeakingAnalysisCoordinator.reset] 으로 취소한다. 취소된
         *   요청은 Result 를 내지 않으므로 [onAnalysisState] → [triggerFeedback] 이 돌지 않고, 따라서 취소한
         *   턴의 피드백 시트가 뒤늦게 떠오르지 않는다(시트는 [triggerFeedback] → feedback.start 로만 뜬다).
         *   reset 과 응답 기록이 겹치는 좁은 창은 [onAnalysisState] 의 `micState != Analyzing` 가드가 흡수한다.
         *
         * micState 를 launch 밖에서 **먼저** 뒤집는 건 재탭 창을 즉시 닫기 위함이다(안에서 뒤집으면 stop() 이
         * 끝나기 전 마이크 재탭이 [stopRecording] 을 타 취소한 녹음을 도로 제출한다). Ready 로 되돌리면
         * [MicButton] 이 이미 tappable(enabled=Ready||Recording)이라 재녹음은 추가 배선 없이 바로 가능하다.
         */
        fun onCancelSpeaking() {
            val phase = micState
            if (phase != MicState.Recording && phase != MicState.Analyzing) return
            micState = MicState.Ready
            pendingClip = null // 취소한 녹음은 다음 답변 말풍선에 붙지 않는다
            if (phase == MicState.Recording) {
                viewModelScope.launch { recording.stop() }
            } else {
                speaking.reset()
            }
        }
```

- [ ] **Step 4: Rename the parameter across `MicDock.kt` (no branch change yet)**

Open `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDock.kt` and rename all five references. The exact edits:

Line 119, inside `MicSessionDock`'s `MicDock(...)` call:
```kotlin
        onCancelRecording = viewModel::onCancelRecording,
```
becomes:
```kotlin
        onCancelSpeaking = viewModel::onCancelSpeaking,
```

Line 156, in the `MicDock` signature:
```kotlin
    onCancelRecording: () -> Unit,
```
becomes:
```kotlin
    onCancelSpeaking: () -> Unit,
```

Line 184, in `MicDock`'s body where it calls `MicColumn`:
```kotlin
                onCancelRecording = onCancelRecording,
```
becomes:
```kotlin
                onCancelSpeaking = onCancelSpeaking,
```

Line 200, in the `MicColumn` signature:
```kotlin
    onCancelRecording: () -> Unit,
```
becomes:
```kotlin
    onCancelSpeaking: () -> Unit,
```

Line 255, in the `InputModeToggle` call inside `MicColumn`'s Recording branch:
```kotlin
                    onClick = onCancelRecording,
```
becomes:
```kotlin
                    onClick = onCancelSpeaking,
```

- [ ] **Step 5: Rename the cancel test file, class, and its parameter references**

Rename the file (use `git mv` so history follows):
```bash
git mv android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDockCancelRecordingTest.kt \
       android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDockCancelSpeakingTest.kt
```

Then in `MicDockCancelSpeakingTest.kt`, apply these edits.

The KDoc (lines 18-21) currently reads:
```kotlin
/**
 * 녹음 중("처음부터 말하기") 취소 어피던스 검증. Recording 상태에서는 하단 토글이 "채팅으로 입력하기" 대신
 * "처음부터 말하기"로 바뀌고, 탭하면 [onCancelRecording] 콜백이 호출돼야 한다(다른 상태는 기존 문구 유지).
 */
```
becomes:
```kotlin
/**
 * 발화 취소("처음부터 말하기") 어피던스 검증. Recording 상태에서는 하단 토글이 "채팅으로 입력하기" 대신
 * "처음부터 말하기"로 바뀌고, 탭하면 [onCancelSpeaking] 콜백이 호출돼야 한다(다른 상태는 기존 문구 유지).
 */
```

The class declaration (line 24):
```kotlin
class MicDockCancelRecordingTest {
```
becomes:
```kotlin
class MicDockCancelSpeakingTest {
```

The helper (lines 31, 45):
```kotlin
    private fun setDock(micState: MicState, onCancelRecording: () -> Unit) {
```
becomes:
```kotlin
    private fun setDock(micState: MicState, onCancelSpeaking: () -> Unit) {
```
and inside its `MicDock(...)` call:
```kotlin
                    onCancelRecording = onCancelRecording,
```
becomes:
```kotlin
                    onCancelSpeaking = onCancelSpeaking,
```

The three call sites in the test bodies (lines 56, 65, 74):
```kotlin
        setDock(MicState.Recording, onCancelRecording = {})
```
becomes:
```kotlin
        setDock(MicState.Recording, onCancelSpeaking = {})
```
```kotlin
        setDock(MicState.Recording, onCancelRecording = { cancelCount++ })
```
becomes:
```kotlin
        setDock(MicState.Recording, onCancelSpeaking = { cancelCount++ })
```
```kotlin
        setDock(MicState.Ready, onCancelRecording = {})
```
becomes:
```kotlin
        setDock(MicState.Ready, onCancelSpeaking = {})
```

- [ ] **Step 6: Rename the parameter at the four remaining `MicDock(...)` call sites**

In `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDockTogglePositionTest.kt` (line 61), `DialogueTurnScreenshotTest.kt` (lines 164 and 197), and `SessionFlowScreenshotTest.kt` (lines 106 and 155), each `MicDock(...)` call contains the line:

```kotlin
                                onCancelRecording = {},
```

Change every one to:

```kotlin
                                onCancelSpeaking = {},
```

(Indentation differs per call site — keep each line's existing leading whitespace; only the identifier changes.)

- [ ] **Step 7: Rename the Release-variant exclusion entry**

Open `android/app/build.gradle.kts`. Inside the `exclude(...)` block, the line:

```kotlin
            "**/MicDockCancelRecordingTest*",
```

becomes:

```kotlin
            "**/MicDockCancelSpeakingTest*",
```

- [ ] **Step 8: Verify the rename compiles and every affected test still passes**

Run:
```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*MicDockCancelSpeakingTest*' --tests '*MicDockTogglePositionTest*' --tests '*DialogueTurnScreenshotTest*' --tests '*SessionFlowScreenshotTest*' --tests '*SpeakingAnalysisCoordinatorTest*'
```
Expected: `BUILD SUCCESSFUL`, all green. The rename is behavior-preserving, so the three pre-existing `MicDockCancelSpeakingTest` tests must still pass unchanged.

Then confirm no stale references survive:
```bash
grep -rn "onCancelRecording\|MicDockCancelRecordingTest" android/app/src android/app/build.gradle.kts
```
Expected: **no output**. Any hit is a missed rename.

- [ ] **Step 9: Verify the Release variant too (the exclusion rename is load-bearing)**

Run:
```bash
scripts/verify-android.sh :app:testReleaseUnitTest --tests '*SpeakingAnalysisCoordinatorTest*'
```
Expected: `BUILD SUCCESSFUL`. This proves the renamed exclusion glob still parses and that `MicDockCancelSpeakingTest` is correctly kept out of the Release variant (a missed exclusion rename surfaces here as a `ComponentActivity` failure, not at debug).

- [ ] **Step 10: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDock.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/speaking/SpeakingAnalysisCoordinatorTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDockCancelSpeakingTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDockCancelRecordingTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDockTogglePositionTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreenshotTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionFlowScreenshotTest.kt \
        android/app/build.gradle.kts
git commit -m "feat(dialogue): cancel the in-flight analysis from onCancelSpeaking"
```

(The `git add` of both the old and new test filename records the rename; `git mv` in Step 5 already staged it, and re-adding both paths is harmless.)

---

### Task 2: Show "처음부터 말하기" during Analyzing

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDock.kt:251` (the toggle branch condition)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDockCancelSpeakingTest.kt` (add three tests)

**Interfaces:**
- Consumes: `MicState.Analyzing` / `MicState.Complete` (`ui/audio/MicState.kt:19`, `:20`); the `onCancelSpeaking: () -> Unit` parameter on `MicDock`/`MicColumn` produced by Task 1; the existing `private fun InputModeToggle(icon: OceIcon?, label: String, onClick: () -> Unit, topGap: Dp = OceTheme.spacing.md)` (`MicDock.kt:319`, whose `icon` is already nullable and rendered only when non-null).
- Produces: nothing new — this task only widens an existing branch condition. No signature changes.

- [ ] **Step 1: Write the failing tests**

Open `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDockCancelSpeakingTest.kt`. After the existing `ready_state_keeps_chat_toggle()` test and before the closing `}` of the class, add three tests:

```kotlin
    @Test
    fun analyzing_state_shows_cancel_label_instead_of_chat_toggle() {
        setDock(MicState.Analyzing, onCancelSpeaking = {})

        composeRule.onNodeWithText("처음부터 말하기", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("채팅으로 입력하기", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun tapping_cancel_label_while_analyzing_invokes_callback() {
        var cancelCount = 0
        setDock(MicState.Analyzing, onCancelSpeaking = { cancelCount++ })

        composeRule.onNodeWithText("처음부터 말하기", useUnmergedTree = true).performClick()

        assertEquals(1, cancelCount)
    }

    @Test
    fun complete_state_keeps_next_button() {
        setDock(MicState.Complete, onCancelSpeaking = {})

        composeRule.onNodeWithText("다음").assertExists()
        composeRule.onNodeWithText("처음부터 말하기", useUnmergedTree = true).assertDoesNotExist()
    }
```

(`complete_state_keeps_next_button` guards the branch ladder's ordering: `Complete` must keep winning over the widened cancel branch.)

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*MicDockCancelSpeakingTest*'
```
Expected: **FAIL** — `analyzing_state_shows_cancel_label_instead_of_chat_toggle` and `tapping_cancel_label_while_analyzing_invokes_callback` fail, because `MicState.Analyzing` currently falls through to the `else` branch and renders "채팅으로 입력하기". The failures look like:
```
java.lang.AssertionError: Failed: assertExists.
Reason: Expected exactly '1' node but could not find any node that satisfies: (Text + EditableText contains '처음부터 말하기' (ignoreCase: false))
```
`complete_state_keeps_next_button` should already **pass** (the `Complete` branch is unchanged) — it is a guard, not a RED test.

- [ ] **Step 3: Widen the toggle branch to cover Analyzing**

Open `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDock.kt`. The branch ladder inside `MicColumn` currently reads:

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
                    icon = null,
                    label = "처음부터 말하기",
                    onClick = onCancelSpeaking,
                    // 마이크 모드: 상태 문구와 밀착(중앙정렬로 생긴 텍스트 위 여백 상쇄) — 토글은 도크 하단 고정.
                    topGap = 0.dp,
                )
            } else {
```

Change only the `else if` condition, and update its comment to say why Analyzing is included:

```kotlin
            if (micState == MicState.Complete) {
                Button(
                    onClick = onAdvance,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
                    shape = OceTheme.shapes.radius12,
                ) {
                    Text(text = "다음", style = OceTheme.typography.sectionLabel)
                }
            } else if (micState == MicState.Recording || micState == MicState.Analyzing) {
                // 녹음 중·분석 중 모두 취소 가능 — 분석 중엔 채팅 전환이 어차피 막혀 있고(onSubmitText 가
                // Analyzing 을 거른다), 대신 진행 중인 LLM 왕복을 버리고 처음부터 다시 말할 수 있어야 한다.
                InputModeToggle(
                    icon = null,
                    label = "처음부터 말하기",
                    onClick = onCancelSpeaking,
                    // 마이크 모드: 상태 문구와 밀착(중앙정렬로 생긴 텍스트 위 여백 상쇄) — 토글은 도크 하단 고정.
                    topGap = 0.dp,
                )
            } else {
```

- [ ] **Step 4: Run the tests to verify they pass**

Run:
```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*MicDockCancelSpeakingTest*' --tests '*MicDockTogglePositionTest*' --tests '*DialogueTurnScreenshotTest*' --tests '*SessionFlowScreenshotTest*'
```
Expected: `BUILD SUCCESSFUL`, all green — the six `MicDockCancelSpeakingTest` tests plus the pre-existing dock/screenshot tests.

- [ ] **Step 5: Regenerate the Analyzing screenshot and eyeball it**

Run:
```bash
scripts/verify-android.sh :app:testDebugUnitTest -Proborazzi.record --tests '*SessionFlowScreenshotTest.flow_analyzing_light*'
```
Expected: `BUILD SUCCESSFUL`. Then open `android/app/build/outputs/roborazzi/flow_analyzing_light.png` and confirm the dock shows the "말한 내용을 다듬는 중이에요…" status with **"처음부터 말하기"** beneath it (no icon), where it previously showed "채팅으로 입력하기". These PNGs are build output and are not committed.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDock.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDockCancelSpeakingTest.kt
git commit -m "feat(dialogue): offer \"처음부터 말하기\" while analyzing, not just recording"
```

---

### Task 3: Manual on-device verification

**Files:** none — this exercises what neither Robolectric nor the coordinator test can reach: the real `AudioRecord` teardown, a real LLM round-trip, and the feedback sheet actually staying down after a mid-analysis cancel.

**Interfaces:**
- Consumes: the wired feature from Tasks 1-2 on a connected device (a Galaxy S23 / `SM-S911N` was connected and receiving `installDebug` during this branch's earlier work).
- Produces: nothing — verification only.

- [ ] **Step 1: Build and install**

Run:
```bash
scripts/verify-android.sh :app:installDebug
```
Expected: `BUILD SUCCESSFUL` and `Installed on 1 device.` If it fails with `No connected devices!`, reconnect the phone (confirm with `adb devices`) and re-run — do not skip this task.

- [ ] **Step 2: Cancel during Analyzing — the core case**

Launch the app, start a 대화 학습 session, reach a learner turn. Tap the mic, say a full sentence, then tap the **mic button** to stop normally. The dock enters "말한 내용을 다듬는 중이에요…". While that status is showing, tap **"처음부터 말하기"**.

Expected: the Analyzing status disappears immediately and the dock returns to Ready ("탭하고 편하게 말해보세요" + "채팅으로 입력하기").

- [ ] **Step 3: Confirm the feedback sheet never rises afterwards**

Stay on that turn and wait at least 20 seconds (comfortably past both the LLM round-trip and the 15s `ANALYZE_WATCHDOG_MS`), without tapping anything.

Expected — this is the requirement this whole plan exists for: **no** feedback sheet slides up, **no** answer bubble is appended to the chat, and the dock stays in Ready. If a sheet appears at any point, the cancel is leaking; stop and report.

- [ ] **Step 4: Confirm a fresh attempt works after an Analyzing-cancel**

Tap the mic, speak, and tap the mic again to stop.

Expected: normal behavior — Analyzing appears, then the answer bubble and the feedback sheet for **this** attempt only. The transcript must match what you just said, not the earlier cancelled utterance.

- [ ] **Step 5: Confirm the Recording-phase cancel still works (regression)**

Tap the mic to start recording and, while the waveform is animating, tap **"처음부터 말하기"**.

Expected: recording stops, dock returns to Ready, no Analyzing, no sheet. Tapping the mic again starts a fresh recording.

---

## Self-Review

**Spec coverage:**
- "'말한 문장을 다듬는 중이에요...' 에서도 '처음부터 말하기'를 보여줘" → Task 2, Step 3 widens the branch to `Recording || Analyzing`; verified by `analyzing_state_shows_cancel_label_instead_of_chat_toggle` (Task 2, Step 1), the regenerated `flow_analyzing_light.png` (Step 5), and on-device Step 2. (The status string in the code is "말한 내용을 다듬는 중이에요…" — `MicDock.kt:277` — which is the state the request describes; it is not itself changed.)
- "이 경우에는 llm의 답변으로 인해서 피드백 시트가 후에 올라올 수 있는데, 이것도 무시하는 로직을 추가해야 해" → Task 1, Step 3: the Analyzing branch calls `speaking.reset()`, cancelling the in-flight job so no `Result` is emitted, so `onAnalysisState` never reaches `triggerFeedback(...)` — the only caller of `feedback.start(...)`, and therefore the only thing that can raise `SlimFeedbackSheet` (which early-returns on `Idle`). The pre-existing `micState != MicState.Analyzing` guard (`GeneratedDialogueSession.kt:574`) is the second layer for the narrow race where a cancelled coroutine writes `_state` before dying. Pinned by the coordinator contract test (Task 1, Step 1) and on-device Step 3.
- Tapping the cancel affordance during Analyzing must actually invoke the VM → `tapping_cancel_label_while_analyzing_invokes_callback` (Task 2) covers the wiring; `onCancelSpeaking`'s `phase != Recording && phase != Analyzing` guard (Task 1) makes it live for Analyzing.

**Placeholder scan:** no TBD/TODO/"handle edge cases"; every step carries complete before/after code and exact commands with expected output.

**Type consistency:** `onCancelSpeaking` is spelled identically in the VM method (Task 1 Step 3), the `MicDock`/`MicColumn` parameters and their two internal call sites (Task 1 Step 4), all six test call sites (Task 1 Steps 5-6), and Task 2's branch (`onClick = onCancelSpeaking`). `MicDockCancelSpeakingTest` matches between the `git mv`, the class declaration, the build.gradle exclusion glob, and every `--tests` filter. `InputModeToggle(icon = null, ...)` type-checks against the already-nullable `icon: OceIcon?` parameter at `MicDock.kt:320`. Task 1's Step 8 `grep` mechanically proves no old-name references survive.

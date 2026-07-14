# Loading Quiz Everywhere + Reveal-State Prototype Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the wait-quiz appear on **every** dialogue-generation wait (not just onboarding) by fixing the stale-state bug that silently skips it on 2nd+ generations, and bring the quiz *answered/reveal* UI into parity with the prototype.

**Architecture:** Part 1 is a bug fix — the process-`@Singleton` `DialogueGenerationCoordinator` retains a prior session's sticky `Ready`; a newly-mounted generating screen reads it before its own `start()` runs and auto-advances past the quiz. We add a `reset()` seam on the coordinator and call it from the generation ViewModel's `init` so each new generation surface starts from `Idle`. Part 2 is a Compose parity change to the reveal row in `OneClickWaitQuiz` (layout + color + typography) verified with a Roborazzi capture against the decoded prototype markup.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Kotlin coroutines/StateFlow, JUnit4, kotlinx-coroutines-test, Robolectric + Roborazzi (screenshot capture).

## Global Constraints

- **Verification command:** always run gradle via `scripts/verify-android.sh` from the worktree root — never bare `./gradlew` (shared `~/.gradle` cache pollution, missing `google-services.json`, KGP variant test source-sets). Targeted run form: `scripts/verify-android.sh :app:testDebugUnitTest --tests '<pattern>'`. (`docs/agents/android-verification.md`.)
- **Screenshot tests have NO committed golden PNGs.** `captureRoboImage(...)` writes to the gitignored `build/outputs/roborazzi/`. Recording is gated on `-Proborazzi.record`. Prototype parity is confirmed by **recording the PNG and visually comparing to the prototype** (`docs/adr/0006-prototype-as-realization-sot.md` — the prototype is the realization SoT). There is no automated pixel-compare gate.
- **Do not regress the genuine <1s fast-generation auto-skip.** When a real generation reaches `Ready` within the 1000ms gate, the screen must still skip the quiz and go straight to the conversation (`DialogueGeneratingScreen.kt:100-103`). The fix must only drop a *stale prior* `Ready`, never a fresh one.
- **Quiz remains ungraded/non-punitive** (`docs/ux/loading-quiz-interstitial.md`, ADR-0005): the reveal distinguishes right/wrong by copy tone only, never by red/error color.
- **Out of scope:** the resume ("이어하기") path — it bypasses generation entirely (`HomeSessionGraph.kt:71`), so there is no generation wait to show a quiz during.

---

### Task 1: Coordinator `reset()` — stale-state guard

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationCoordinator.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationCoordinatorTest.kt`

**Interfaces:**
- Consumes: existing coordinator internals (`_state`, `sessionToken`, `currentJob`, `watchdogJob`, accumulators, `DialogueStreamStatus.Streaming`, `DialogueGenState.Idle`).
- Produces: `fun reset()` on `DialogueGenerationCoordinator` — returns the coordinator to `DialogueGenState.Idle`, bumps `sessionToken` (so late events from a superseded stream are dropped), cancels in-flight jobs, and clears accumulators. Consumed by Task 2.

- [ ] **Step 1: Write the failing tests**

Add these two tests inside the existing `class DialogueGenerationCoordinatorTest` in `DialogueGenerationCoordinatorTest.kt` (the file already defines the `turn(...)` helper and a `coordScope()` helper used by the other tests; reuse them):

```kotlin
    @Test
    fun `reset returns to Idle and drops a prior Ready`() =
        runTest {
            val stream = FakeDialogueStream()
            val coordinator = DialogueGenerationCoordinator(stream, coordScope())

            coordinator.start("easy", "coffee", 5, firstSession = true)
            runCurrent()
            stream.push(DialogueEvent.Start("s1", remaining = 2))
            stream.push(DialogueEvent.Turn(turn("Hi")))
            runCurrent()
            assertTrue(coordinator.state.value is DialogueGenState.Ready)

            coordinator.reset()

            assertEquals(DialogueGenState.Idle, coordinator.state.value)
        }

    @Test
    fun `a late turn from a superseded stream is ignored after reset`() =
        runTest {
            val stream = FakeDialogueStream()
            val coordinator = DialogueGenerationCoordinator(stream, coordScope())

            coordinator.start("easy", "coffee", 5, firstSession = true)
            runCurrent()
            stream.push(DialogueEvent.Start("s1", remaining = 2))
            stream.push(DialogueEvent.Turn(turn("Hi")))
            runCurrent()

            coordinator.reset()
            // A slow event from the now-superseded stream must not revive Ready.
            stream.push(DialogueEvent.Turn(turn("late")))
            runCurrent()

            assertEquals(DialogueGenState.Idle, coordinator.state.value)
        }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*DialogueGenerationCoordinatorTest*'`
Expected: FAIL — compilation error `unresolved reference: reset` (method does not exist yet).

- [ ] **Step 3: Implement `reset()`**

In `DialogueGenerationCoordinator.kt`, add this method immediately after `blockOffline()` (around line 126, before `private fun launchAttempt`):

```kotlin
    /**
     * Return to [DialogueGenState.Idle], dropping any prior attempt's sticky Ready/terminal state.
     * Called when a new generation surface mounts ([DialogueGenerationViewModel] init) so a previous
     * session's state — retained because this coordinator is a process [Singleton] — cannot leak into
     * the newly mounted generating screen and auto-skip the wait quiz (loading-quiz-interstitial.md §5).
     * Bumps [sessionToken] so any late event from a superseded stream is dropped, and cancels in-flight
     * jobs defensively. Does not open a stream or consume quota.
     */
    fun reset() {
        ++sessionToken
        currentJob?.cancel()
        watchdogJob?.cancel()
        lastRequest = null
        sessionId = null
        remaining = null
        meta = null
        streamStatus = DialogueStreamStatus.Streaming
        turns.clear()
        _state.value = DialogueGenState.Idle
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*DialogueGenerationCoordinatorTest*'`
Expected: PASS (all coordinator tests, including the two new ones).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationCoordinator.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationCoordinatorTest.kt
git commit -m "feat(dialogue): add coordinator reset() to drop stale generation state"
```

---

### Task 2: Reset leftover coordinator state on generation-VM creation (the bug fix)

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModel.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModelTest.kt`

**Interfaces:**
- Consumes: `DialogueGenerationCoordinator.reset()` (Task 1).
- Produces: no new public API. Behavior change: after a `DialogueGenerationViewModel` is constructed, `state.value == DialogueGenState.Idle` regardless of any prior generation's sticky state — so `DialogueGeneratingScreen` no longer reads a stale `Ready` and no longer auto-skips the quiz on 2nd+ generations.

**Why this fixes the reported symptom:** onboarding is the *first* generation of an app run, so the singleton coordinator is already `Idle` and the quiz shows. Every later generation (home "새 대화 시작") reused the coordinator's sticky `Ready` from the prior session; `DialogueGeneratingScreen.kt:100` computed `readyBeforeGate = true` on the first frame and `DialogueGeneratingScreen.kt:101-103` auto-advanced past the quiz. Resetting at VM `init` (which runs during the route's first composition, before the `LaunchedEffect(Unit){ start() }` in `DialogueGeneratingScreen.kt:241-243`) makes the first observed state `Idle`, so the quiz shows after the 1s gate exactly like onboarding.

- [ ] **Step 1: Write the failing test**

Add this test to `class DialogueGenerationViewModelTest` in `DialogueGenerationViewModelTest.kt`. Also add the import `import com.jjundev.oneclickeng.core.network.DialogueTurn` near the other `core.network` imports at the top of the file.

```kotlin
    @Test
    fun `a prior generation's sticky Ready does not leak into a newly created generation VM`() =
        runTest {
            val stream = FakeStream()
            val scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val coordinator =
                DialogueGenerationCoordinator(stream, scope, FakeConnectivity(offline = false))

            // A prior generation completed → the process-singleton coordinator holds a sticky Ready.
            coordinator.start("easy", "t", 5, firstSession = true)
            runCurrent()
            stream.push(DialogueEvent.Start(sessionId = "s1", remaining = 3))
            stream.push(DialogueEvent.Turn(DialogueTurn(ko = "안녕", en = "Hi", role = "model")))
            runCurrent()
            assertTrue(coordinator.state.value is DialogueGenState.Ready)

            // A new generating screen mounts → a fresh VM is created sharing that singleton coordinator.
            val vm =
                DialogueGenerationViewModel(
                    coordinator,
                    bank,
                    RecordingAnalytics(),
                    RecordingLimitAnalytics(),
                    SessionSnapshotStore(inMemoryPrefsDataStore()),
                    scope,
                    RecordingOfflineAnalytics(),
                    FakeConfig(true),
                )

            // init must reset the leftover Ready so the generating screen sees Idle (→ quiz, not auto-skip).
            assertEquals(DialogueGenState.Idle, vm.state.value)
        }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*DialogueGenerationViewModelTest*'`
Expected: FAIL — assertion error: expected `Idle` but was `Ready(...)` (the VM currently exposes the coordinator's leftover state unchanged).

- [ ] **Step 3: Reset the coordinator in the VM `init`**

In `DialogueGenerationViewModel.kt`, add an `init` block. Place it after the property declarations — right after the `private var preflightBlocked = false` line (around line 63) — and before `fun start(...)`. The constructor parameter is already named `coordinator`. (Placement within the class body is not load-bearing: `state` is a StateFlow *reference* alias to `coordinator.state` (`DialogueGenerationViewModel.kt:43`), not a captured snapshot, so `coordinator.reset()` in `init` updates `vm.state.value` to `Idle` regardless of ordering.)

```kotlin
        init {
            // 이 코디네이터는 process @Singleton 이라 직전 세션의 sticky Ready 가 남는다. 새 생성 VM 이 뜰 때
            // 그 잔여 상태를 Idle 로 되돌려, 생성 화면이 stale Ready 를 읽고 대기 퀴즈를 건너뛰는 걸 막는다
            // (온보딩=첫 생성이라 원래 Idle → 정상, 2번째+ 생성만 문제였음). start() 는 곧이어 Generating 으로
            // 전이하므로 정상 <1s fast-ready 자동 스킵은 그대로 보존된다.
            coordinator.reset()
        }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*DialogueGenerationViewModelTest*'`
Expected: PASS (the new test plus all pre-existing VM tests — the pre-existing `start()`/offline/analytics tests are unaffected because they construct the VM then call `start()`, which still transitions to `Generating`/`OfflineBlocked` as before).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModel.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModelTest.kt
git commit -m "fix(dialogue): show wait-quiz on every generation, not just onboarding

The @Singleton coordinator retained the prior session's sticky Ready, so the
generating screen read it before start() ran and auto-skipped the quiz on 2nd+
generations. Reset the coordinator when the generation VM is created."
```

---

### Task 3: Reveal-state prototype parity in `OneClickWaitQuiz`

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickWaitQuiz.kt` (reveal `Crossfade` block, `OneClickWaitQuiz.kt:131-173`)
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/OneClickWaitQuizScreenshotTest.kt`

**Interfaces:**
- Consumes: existing `OneClickWaitQuiz(items, modifier, onAnswered, loading, reduceMotion)` signature and `previewWaitQuizItems()` (both in `OneClickWaitQuiz.kt`).
- Produces: no signature change. The reveal row is restructured to match the prototype.

**Prototype reference (decoded from `prototype/Prototype Flow (standalone).html`, the realization SoT):**
```html
<!-- reveal row: note (left, flex:1) + 다음 button (right); space-between -->
<div style="display:flex; align-items:center; justify-content:space-between; gap:12px; padding-top:2px;">
  <span style="flex:1; font:600 13.5px 'Pretendard'; color:var(--text-secondary); line-height:1.45;">{{ quizNote }}</span>
  <button style="... font:700 14px 'Pretendard'; color:var(--brand-primary);">다음 <chevron_right 20px></button>
</div>
```
Prototype `quizNote` color is `--text-secondary` (= Android `MaterialTheme.colorScheme.onSurfaceVariant`, `Theme.kt:21,35`) for **both** correct and wrong — right/wrong is signalled by copy only. `--text-secondary` typography is `600 13.5px` line-height 1.45 (Android `OceTheme.typography.helper` is `Normal 13sp / 1.45`, `Type.kt:77` → copy with SemiBold + 13.5sp).

**Current gaps being fixed** (`OneClickWaitQuiz.kt:145-171`): the note+button are stacked in a `Column`; the note is colored `OceTheme.colors.feedbackCorrectAccent` (green) for wrong answers too; the note uses `OceTheme.typography.body` (16sp).

- [ ] **Step 1: Write the failing test (new screenshot + reveal-structure test file)**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/OneClickWaitQuizScreenshotTest.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.component

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * C20 WaitQuiz 리빌(정답/오답) 표면. 프로토 "기다리는 동안 가볍게" 카드 answered 상태 정합 확인용.
 * 커밋 골든 없음 — 프로토 대조는 `-Proborazzi.record` 후 육안(docs/adr/0006). reduceMotion=true·loading=false 라
 * 무한 링 전이가 없어 클릭→캡처가 행(hang) 없이 안전하다(OneClickWaitQuiz KDoc @param loading 주의).
 *
 * previewWaitQuizItems() 1번 문항: optionA "I have a plan." = 정답(correctIndex 0), optionB "I have plan." = 오답.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class OneClickWaitQuizScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reveal_correct_light() = captureReveal("I have a plan.", "quiz_reveal_correct_light", dark = false)

    @Test
    fun reveal_correct_dark() = captureReveal("I have a plan.", "quiz_reveal_correct_dark", dark = true)

    @Test
    fun reveal_wrong_light() = captureReveal("I have plan.", "quiz_reveal_wrong_light", dark = false)

    @Test
    fun reveal_wrong_dark() = captureReveal("I have plan.", "quiz_reveal_wrong_dark", dark = true)

    /** 오답을 눌러도 비처벌 리빌 카피와 "다음" 어포던스가 뜬다(리빌 구조 회귀 가드). */
    @Test
    fun wrong_answer_reveals_non_punitive_copy_and_next() {
        composeRule.setContent {
            OceTheme {
                OneClickWaitQuiz(items = previewWaitQuizItems(), loading = false, reduceMotion = true)
            }
        }
        composeRule.onNodeWithText("I have plan.").performClick()
        composeRule.onNodeWithText("괜찮아요. \"a plan\" 처럼 관사를 붙여요.").assertIsDisplayed()
        composeRule.onNodeWithText("다음").assertIsDisplayed()
    }

    private fun captureReveal(optionText: String, name: String, dark: Boolean) {
        composeRule.setContent {
            OceTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    OneClickWaitQuiz(items = previewWaitQuizItems(), loading = false, reduceMotion = true)
                }
            }
        }
        composeRule.onNodeWithText(optionText).performClick()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }
}
```

- [ ] **Step 2: Run the test to verify it passes structurally, then record the "before" image**

First confirm the behavioral test passes (it exercises the current reveal wiring, which already selects the right copy):

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*OneClickWaitQuizScreenshotTest*'`
Expected: PASS for `wrong_answer_reveals_non_punitive_copy_and_next` (reveal copy logic is already correct). The `reveal_*` capture tests also PASS (they only capture, no golden compare).

Then record the current appearance for a "before" reference:

Run: `scripts/verify-android.sh :app:testDebugUnitTest -Proborazzi.record --tests '*OneClickWaitQuizScreenshotTest*'`
Expected: PNGs written under `android/app/build/outputs/roborazzi/quiz_reveal_*.png`. Inspect `quiz_reveal_wrong_light.png` — the note text is currently **green** (feedbackCorrectAccent) and sits **above** the "다음" button. This is the parity defect to fix.

- [ ] **Step 3: Restructure the reveal row for prototype parity**

In `OneClickWaitQuiz.kt`, replace the reveal `Crossfade` body. Change the inner `if (sel != null) { Column(...) { Text(...); TextButton(...) } }` block (`OneClickWaitQuiz.kt:142-172`) to a `Row` with the note (neutral secondary, SemiBold 13.5sp, `weight(1f)`) on the left and the "다음" button on the right:

```kotlin
                    if (sel != null) {
                        val revealCopy =
                            if (sel == item.correctIndex) item.revealCopyCorrect else item.revealCopyWrong
                        // 프로토 정합: 리빌 노트(좌, text-secondary=onSurfaceVariant 중립)와 "다음"(우)을 한 줄
                        // Row(space-between)로. 정답/오답 구분은 색이 아니라 카피 톤으로만(비처벌) — 오답도
                        // correct-accent 초록으로 칠하지 않는다(loading-quiz-interstitial.md, ADR-0005).
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = REVEAL_ROW_TOP_PADDING),
                            horizontalArrangement = Arrangement.spacedBy(REVEAL_ROW_GAP),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = revealCopy,
                                style =
                                    OceTheme.typography.helper.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                    ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            TextButton(
                                onClick = {
                                    index = if (items.isEmpty()) 0 else (index + 1) % items.size
                                    revealed = null
                                },
                                contentPadding =
                                    PaddingValues(
                                        horizontal = OceTheme.spacing.xs,
                                        vertical = OceTheme.spacing.xs,
                                    ),
                            ) {
                                Text(
                                    text = "다음",
                                    style = OceTheme.typography.sectionLabel,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                OneClickIcon(
                                    icon = OceIcon.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    size = OceIconSize.ListDisclosure,
                                )
                            }
                        }
                    }
```

Add the import near the other layout imports (after `import androidx.compose.foundation.layout.padding`, `OneClickWaitQuiz.kt:20`):

```kotlin
import androidx.compose.foundation.layout.PaddingValues
```

Add these private dimension constants next to the existing ring constants (after `OneClickWaitQuiz.kt:299`, near `RING_PEAK_END`):

```kotlin
/** 리빌 행(프로토): 노트↔"다음" 간격 gap:12px · 상단 여백 padding-top:2px. */
private val REVEAL_ROW_GAP = 12.dp
private val REVEAL_ROW_TOP_PADDING = 2.dp
```

Note: `OceTheme.colors.feedbackCorrectAccent` is no longer referenced anywhere in this file after the change (it was only used at the old `OneClickWaitQuiz.kt:149`); leave the `OceTheme` import — it is still used for `typography`, `spacing`, `shapes`, `motion`, and other `colors.*`.

- [ ] **Step 4: Re-run tests, re-record, and confirm parity**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*OneClickWaitQuizScreenshotTest*'`
Expected: PASS (behavioral guard + captures still render; the Row refactor preserves the reveal copy and "다음" nodes).

Run: `scripts/verify-android.sh :app:testDebugUnitTest -Proborazzi.record --tests '*OneClickWaitQuizScreenshotTest*'`
Expected: PNGs re-written. Inspect and confirm against the prototype markup:
- The reveal note and "다음" are on **one row**, note left / "다음" right (space-between).
- The note text is **neutral secondary** (`onSurfaceVariant`), the **same color for correct and wrong** (`quiz_reveal_correct_*` vs `quiz_reveal_wrong_*` differ only in wording, not color).
- The note is SemiBold ~13.5sp (smaller than the 16sp question above it).

> **On automated coverage of the color fix:** the note-color change (green→neutral) is *not* caught by an automated assertion — the `wrong_answer_reveals_non_punitive_copy_and_next` test guards the reveal *structure* (copy + "다음" present) but not color, and there are no committed Roborazzi goldens to pixel-compare. Visual PNG inspection against the prototype is the parity gate here, consistent with the repo's established prototype-parity loop (ADR-0006). *Optional hardening (not required):* add a `composeRule.onNodeWithText(revealCopy).captureToImage()` pixel-sample assertion that the note pixel is not `feedbackCorrectAccent`, so an accidental revert is caught in CI rather than by eye — skip it if it proves flaky under Robolectric NATIVE graphics.

- [ ] **Step 5: Guard the whole generating surface still renders after the change**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*DialogueGeneratingScreenshotTest*'`
Expected: PASS — `generating_quiz_*` and `generating_ready_*` captures still render without hang (the quiz card is embedded there; this confirms the reveal refactor didn't break the host screen).

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickWaitQuiz.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/OneClickWaitQuizScreenshotTest.kt
git commit -m "fix(quiz): match reveal row to prototype (neutral note color, single row)"
```

---

### Task 4: Full verification sweep

**Files:** none (verification only).

- [ ] **Step 1: Run the default verification set**

Run: `scripts/verify-android.sh`
Expected: PASS — detekt + androidTest compile + both-variant unit tests. This catches detekt/style violations introduced by the edits and confirms nothing else regressed.

- [ ] **Step 2: If detekt flags anything in the touched files, fix inline and re-run**

Run: `scripts/verify-android.sh`
Expected: PASS clean. (Common nits: unused imports, magic numbers — the plan already extracts `REVEAL_ROW_GAP`/`REVEAL_ROW_TOP_PADDING` as named constants to avoid magic-number findings.)

- [ ] **Step 3: Final commit if any lint fixes were needed**

```bash
git add -A
git commit -m "chore: detekt fixes for loading-quiz changes"
```

---

## Notes for the executor

- **Manual end-to-end check of the bug fix** (optional but recommended, since the symptom is behavioral): build/install, complete one dialogue through to summary, return home, tap "바로 대화 시작하기" again, and confirm the wait-quiz card now appears during the second generation's wait (previously it flashed straight to the conversation). Onboarding behavior must be unchanged.
- The reveal reworks only the *answered* state. The unanswered quiz card, the rotating ring, the header pill/counter, and the ungraded disclaimer are already prototype-matched — do not alter them.
- **Why the VM-init `reset()` is safe w.r.t. other coordinator consumers** (confirmed against the nav graph): a fresh `DialogueGenerationViewModel.init` never fires while another coordinator-consuming ViewModel is alive. The generating route is `popUpTo(...){ inclusive = true }`-popped the instant it navigates forward (`HomeSessionGraph.kt:107`, `OnboardingGraph.kt:141`), and the session→summary→home path pops each prior destination (`HomeSessionGraph.kt:142`, `:75`). `SessionLimitHolder` only reacts to `Ready`/`QuotaBlocked` (`SessionLimitHolder.kt:41-45`), so an `Idle` emission is a no-op for it; `SummaryViewModel` does not consume `DialogueGenerationCoordinator` at all (it has its own `SummaryCoordinator`). So the `Idle` reset only ever drops the *previous, finished* generation's sticky state — never a live consumer's data.

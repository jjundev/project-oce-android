# Deep Feedback Error-State Skeleton Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When deep feedback ("깊은 피드백") fails to load, stop the infinite shimmer skeletons and show only a retry message, so users can tell it failed instead of thinking it is still loading.

**Architecture:** The `DeepFeedbackCoordinator` already transitions `Loading → Error` correctly (stream close, `Done`/`Error` events, and a 20s idle watchdog). The bug is purely in the `Error`-branch *rendering* of `DeepFeedbackRegion`: it calls `DeepBlocks(...)`, which draws an infinite-shimmer `BlockSkeleton()` for every block that did not arrive. In the common "nothing loaded" failure (all three blocks `null`) the user sees three skeletons shimmering forever with only a small retry row below — reading as "still loading." Fix: in the `Error` branch only, suppress the skeletons (keep any arrived blocks, "sticky") and show the retry message. No coordinator change is needed — the underlying job is already terminated when the state is `Error`; the "loading" being cancelled here is the skeleton animation.

**Tech Stack:** Kotlin, Jetpack Compose, Robolectric + Compose UI test (`createComposeRule`), Gradle via `scripts/verify-android.sh`.

## Global Constraints

- All source lives under `android/app/src/main/kotlin/com/jjundev/oneclickeng/`; tests under `android/app/src/test/kotlin/com/jjundev/oneclickeng/`.
- Verification MUST run through the worktree wrapper: `scripts/verify-android.sh` (never bare `gradle`/`./gradlew` — see `docs/agents/android-verification.md`). First run provisions a worktree-local `GRADLE_USER_HOME` (network, several minutes).
- Do NOT change `DeepFeedbackCoordinator`, `DeepFeedbackState`, the SSE stream, or the 20s watchdog. This is a UI-rendering-only fix.
- `Loading`-state progressive render (arrived block = real data, missing = shimmer skeleton) MUST stay unchanged. Skeletons are suppressed ONLY in the `Error` state.
- Failure copy (decided): `"깊은 분석을 불러오지 못했어요. 다시 시도해볼까요?"`. The retry affordance stays the existing `OneClickInlineError(mode = Recoverable)` "재시도" button.
- Partial-arrival policy (decided): keep already-arrived blocks visible (sticky); suppress only the skeletons for the missing blocks.

---

### Task 1: Make the deep skeleton test-observable (seam + characterization)

Add a `testTag` to the deep-block skeleton so tests can count skeletons, and lock the *current* behavior (Loading shows skeletons, Ready shows none) with a characterization test. No behavior change in this task.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackSections.kt` (add tag constant ~after imports near line 49; edit `BlockSkeleton()` at lines 148-151)
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackRegionTest.kt`

**Interfaces:**
- Produces: `internal const val DeepBlockSkeletonTag = "deep_block_skeleton"` in package `com.jjundev.oneclickeng.feature.session.feedback` — the Compose test tag applied to every deep block skeleton. Task 2's tests reuse it.
- Consumes: existing `DeepFeedbackRegion(state, onRetry, bookmarkedLevels, onToggleBookmark, modifier)` and `DeepFeedbackState.Loading` / `.Ready` (already defined in `DeepFeedbackState.kt`).

- [ ] **Step 1: Add the tag constant and tag the skeleton**

In `DeepFeedbackSections.kt`, add the import (with the other `androidx.compose.ui.*` imports, e.g. after line 28 `import androidx.compose.ui.platform.LocalDensity`):

```kotlin
import androidx.compose.ui.platform.testTag
```

Add the constant just above `DeepFeedbackRegion` (after the file's imports, before the KDoc at line 50):

```kotlin
/** Compose test tag on every deep-block shimmer skeleton — lets tests assert skeleton count. */
internal const val DeepBlockSkeletonTag = "deep_block_skeleton"
```

Replace `BlockSkeleton()` (currently lines 148-151):

```kotlin
@Composable
private fun BlockSkeleton() {
    OneClickSkeleton(shape = SkeletonShape.Section)
}
```

with:

```kotlin
@Composable
private fun BlockSkeleton() {
    OneClickSkeleton(
        shape = SkeletonShape.Section,
        modifier = Modifier.testTag(DeepBlockSkeletonTag),
    )
}
```

- [ ] **Step 2: Write the characterization test**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackRegionTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.feedback

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [DeepFeedbackRegion] 렌더 계약: 스켈레톤은 [DeepFeedbackState.Loading] 에서만 보이고, 실패([Error]) 시엔
 * 재시도 메시지만 남긴다(무한 시머로 "로딩 중"처럼 보이던 버그 회귀 방지). 스켈레톤은 [DeepBlockSkeletonTag]
 * 로 카운트한다. 시머는 rememberInfiniteTransition 이라 테스트 클럭 자동전진을 끄고(autoAdvance=false)
 * 노드 트리만 검증한다 — 아니면 idle 대기가 무한 애니메이션에 막힌다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class DeepFeedbackRegionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loading_shows_three_skeletons() {
        composeRule.mainClock.autoAdvance = false // 무한 시머 → idle 대기 회피
        composeRule.setContent {
            OceTheme {
                DeepFeedbackRegion(
                    state = DeepFeedbackState.Loading(),
                    onRetry = {},
                    bookmarkedLevels = emptySet(),
                    onToggleBookmark = {},
                )
            }
        }

        composeRule.onAllNodesWithTag(DeepBlockSkeletonTag).assertCountEquals(3)
    }
}
```

- [ ] **Step 3: Run the test to verify it passes (characterization — should already pass)**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*DeepFeedbackRegionTest*'`
Expected: PASS. (If it fails to find 3 tagged nodes, the tag wiring in Step 1 is wrong — fix before continuing.)

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackSections.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackRegionTest.kt
git commit -m "test(feedback): make deep-block skeleton test-observable via testTag"
```

---

### Task 2: Suppress skeletons in the deep Error state + update copy

Add a `showSkeletons` flag to `DeepBlocks`, pass `false` from the `Error` branch (keep arrived blocks, drop missing-block skeletons), and update the failure message. This is the actual fix.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackSections.kt` (Error branch lines 91-110; `DeepBlocks` lines 125-146)
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackRegionTest.kt` (add two tests + one helper)

**Interfaces:**
- Consumes: `DeepBlockSkeletonTag` (Task 1); `DeepFeedbackState.Error(conceptualBridge, toneStyle, paraphrasing)`, `ConceptualBridge`, `VennData`, `VennCircle` (all in `DeepFeedbackState.kt`); `OneClickInlineError(mode, message, onRetry, onSkip)`.
- Produces: `DeepBlocks(..., showSkeletons: Boolean = true)` — when `false`, a `null` block renders nothing instead of a skeleton. Only the `Error` branch passes `false`; `Loading`/`Ready` use the default.

- [ ] **Step 1: Write the two failing tests**

Add these to `DeepFeedbackRegionTest.kt`. First add imports (with the existing test imports):

```kotlin
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
```

Add a sample-block helper inside the class (mirrors the block that renders the "개념 브리지" header):

```kotlin
private fun sampleConceptualBridge() =
    ConceptualBridge(
        literalTranslation = "커피 하나요.",
        explanation = "조금 더 공손하게 표현할 수 있어요.",
        venn =
            VennData(
                guide = "두 단어의 의미 차이를 볼까요?",
                left = VennCircle("get", listOf("얻다")),
                right = VennCircle("order", listOf("주문하다")),
                intersectionItems = listOf("받다"),
            ),
    )
```

Add the two tests:

```kotlin
@Test
fun error_all_missing_shows_message_and_no_skeleton() {
    // Step 2 (pre-fix) still renders infinite-shimmer skeletons; setContent → waitForIdle would hang.
    // Harmless post-fix (Error renders no skeletons). See DialogueGeneratingScreenshotTest for the same guard.
    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
        OceTheme {
            DeepFeedbackRegion(
                state = DeepFeedbackState.Error(),
                onRetry = {},
                bookmarkedLevels = emptySet(),
                onToggleBookmark = {},
            )
        }
    }

    composeRule.onNodeWithText("깊은 분석을 불러오지 못했어요. 다시 시도해볼까요?").assertIsDisplayed()
    composeRule.onNodeWithText("재시도").assertIsDisplayed()
    composeRule.onAllNodesWithTag(DeepBlockSkeletonTag).assertCountEquals(0)
}

@Test
fun error_partial_keeps_arrived_block_and_hides_skeletons() {
    // Same reason as above: pre-fix the missing tone/para blocks render infinite-shimmer skeletons.
    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
        OceTheme {
            DeepFeedbackRegion(
                state = DeepFeedbackState.Error(conceptualBridge = sampleConceptualBridge()),
                onRetry = {},
                bookmarkedLevels = emptySet(),
                onToggleBookmark = {},
            )
        }
    }

    composeRule.onNodeWithText("개념 브리지").assertIsDisplayed() // 도착 블록은 sticky 유지
    composeRule.onNodeWithText("깊은 분석을 불러오지 못했어요. 다시 시도해볼까요?").assertIsDisplayed()
    composeRule.onAllNodesWithTag(DeepBlockSkeletonTag).assertCountEquals(0) // 미도착 블록 스켈레톤 없음
}
```

Add the domain imports needed by the helper (with the other imports at the top of the test file):

```kotlin
// (same package — no import needed for ConceptualBridge/VennData/VennCircle; they live in this package)
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*DeepFeedbackRegionTest*'`
Expected: FAIL. `error_all_missing_...` fails skeleton count (currently 3, not 0) and message text (current copy is `"깊은 분석을 불러오지 못했어요."`); `error_partial_...` fails skeleton count (currently 2, not 0) and message text.

- [ ] **Step 3: Add the `showSkeletons` flag to `DeepBlocks`**

In `DeepFeedbackSections.kt`, replace `DeepBlocks` (currently lines 125-146):

```kotlin
@Composable
private fun DeepBlocks(
    modifier: Modifier,
    conceptualBridge: ConceptualBridge?,
    toneStyle: ToneStyle?,
    paraphrasing: Paraphrasing?,
    bookmarkedLevels: Set<Int>,
    onToggleBookmark: (Paraphrase) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sectionGap),
    ) {
        if (conceptualBridge != null) ConceptualBridgeBlock(conceptualBridge) else BlockSkeleton()
        if (toneStyle != null) ToneStyleBlock(toneStyle) else BlockSkeleton()
        if (paraphrasing != null) {
            ParaphrasingBlock(paraphrasing, bookmarkedLevels, onToggleBookmark)
        } else {
            BlockSkeleton()
        }
    }
}
```

with (the KDoc above `DeepBlocks` at lines 121-124 stays):

```kotlin
@Composable
private fun DeepBlocks(
    modifier: Modifier,
    conceptualBridge: ConceptualBridge?,
    toneStyle: ToneStyle?,
    paraphrasing: Paraphrasing?,
    bookmarkedLevels: Set<Int>,
    onToggleBookmark: (Paraphrase) -> Unit,
    showSkeletons: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sectionGap),
    ) {
        when {
            conceptualBridge != null -> ConceptualBridgeBlock(conceptualBridge)
            showSkeletons -> BlockSkeleton()
        }
        when {
            toneStyle != null -> ToneStyleBlock(toneStyle)
            showSkeletons -> BlockSkeleton()
        }
        when {
            paraphrasing != null -> ParaphrasingBlock(paraphrasing, bookmarkedLevels, onToggleBookmark)
            showSkeletons -> BlockSkeleton()
        }
    }
}
```

(A `when` used as a statement with no matching branch renders nothing — exactly the "missing block, no skeleton" case.)

- [ ] **Step 4: Wire the Error branch to suppress skeletons and update the copy**

In `DeepFeedbackRegion`, replace the `Error` branch (currently lines 91-110):

```kotlin
        is DeepFeedbackState.Error ->
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.lg),
            ) {
                DeepBlocks(
                    Modifier,
                    state.conceptualBridge,
                    state.toneStyle,
                    state.paraphrasing,
                    bookmarkedLevels,
                    onToggleBookmark,
                )
                OneClickInlineError(
                    mode = InlineErrorMode.Recoverable,
                    message = "깊은 분석을 불러오지 못했어요.",
                    onRetry = onRetry,
                    onSkip = {}, // deep 은 섹션별 스킵이 없다(영역 재시도만, §9.2)
                )
            }
```

with:

```kotlin
        is DeepFeedbackState.Error ->
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.lg),
            ) {
                // 실패 시 미도착 블록의 무한 시머 스켈레톤을 없앤다(showSkeletons=false) — 도착 블록은
                // sticky 로 남기고, 하단 재시도 메시지만 노출해 "로딩 중"으로 오인되지 않게 한다.
                DeepBlocks(
                    Modifier,
                    state.conceptualBridge,
                    state.toneStyle,
                    state.paraphrasing,
                    bookmarkedLevels,
                    onToggleBookmark,
                    showSkeletons = false,
                )
                OneClickInlineError(
                    mode = InlineErrorMode.Recoverable,
                    message = "깊은 분석을 불러오지 못했어요. 다시 시도해볼까요?",
                    onRetry = onRetry,
                    onSkip = {}, // deep 은 섹션별 스킵이 없다(영역 재시도만, §9.2)
                )
            }
```

Also update the KDoc line for the `Error` state (line 55) to reflect that skeletons are no longer drawn on failure:

```kotlin
 * - [DeepFeedbackState.Error]: 이미 도착한 블록은 보존(sticky), 미도착 블록은 스켈레톤 없이 생략 + 영역 1개 인라인 재시도(§9.2).
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*DeepFeedbackRegionTest*'`
Expected: PASS (all four tests: `loading_shows_three_skeletons`, `error_all_missing_shows_message_and_no_skeleton`, `error_partial_keeps_arrived_block_and_hides_skeletons`, plus no regressions).

- [ ] **Step 6: Run detekt + the feedback test suite to confirm no regressions**

Run: `scripts/verify-android.sh :app:detekt :app:testDebugUnitTest --tests '*feedback*'`
Expected: PASS (detekt clean; existing `DeepFeedbackCoordinatorTest`, `SlimFeedbackSheetTest`, etc. still green).

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackSections.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackRegionTest.kt
git commit -m "fix(feedback): drop shimmer skeletons on deep-feedback failure, show retry message"
```

---

## Notes on the "cancel loading" requirement

The request "로딩은 취소하자" is satisfied at the UI layer: removing the skeletons stops the visible loading animation. No coordinator change is required because by the time the state is `Error`, the streaming job is already terminated: the stream has closed on its own, or the 20s idle watchdog's timeout branch calls `currentJob?.cancel()` before invoking `settleOnClose()` (`DeepFeedbackCoordinator.kt:243-253`), and `settleOnClose()` itself cancels `watchdogJob` (`:236-241`). There is no path where the sheet stays in `Loading` indefinitely: the watchdog re-arms per block and fires within 20s of the last activity, transitioning to `Error`. The "forever loading" the user sees is entirely the `Error`-state skeletons this plan removes.

# Feedback Sheet Skeleton Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make turn-feedback loading placeholders use the Home recommended-situation card shimmer design while keeping slim section titles visible and making deep-feedback titles shimmer as part of their loading placeholders.

**Architecture:** Keep `OneClickSkeleton` unchanged because it is also used by summary and other screens. Add one feedback-package card skeleton composed from the existing `OneClickCard` and `OneClickShimmerPiece` primitives, whose anatomy mirrors Home's `SituationSkeletonRow`; the caller selects whether to add a title placeholder. `SlimFeedbackSheet` keeps its three real `SlimSectionBlock` headers outside this skeleton, while `DeepFeedbackSections` puts the title placeholder inside each deep block skeleton and preserves its existing outer test tag.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Robolectric Compose tests, existing `OceTheme`, `OneClickCard`, and `OneClickShimmerPiece`.

## Global Constraints

- Keep the immediate recap header, the slim section order, section state model, retry/skip UI, `nextEnabled` gating, and deep request lifecycle unchanged.
- In slim loading, the concrete labels `작문 점수`, `문법`, and `자연스러운 표현` must remain visible; only each section's content is a shimmer placeholder.
- In deep (`더 보기`) loading, do not render concrete deep title text; each block must include a shimmer title placeholder together with its content placeholder.
- Mirror the Home recommended-situation skeleton's flat `OneClickCard` frame, 40dp leading rounded shimmer piece, background-base/hairline-highlight shimmer, and `OceTheme.motion.shimmerLoopMs` behavior by reusing `OneClickCard` and `OneClickShimmerPiece`.
- Respect reduced motion through `OneClickShimmerPiece`'s existing `rememberReduceMotion` default; do not add colors, animation constants, or dependencies.
- Keep `DEEP_BLOCK_SKELETON_TAG = "deep_block_skeleton"` behavior intact so existing deep-loading/error tests continue to count three loading blocks and zero error-state blocks.
- Keep this Compose test in `SlimFeedbackSheetTest`, which is already excluded from release unit-test variants in `android/app/build.gradle.kts`.
- Run every Android verification through `scripts/verify-android.sh`; it supplies the worktree-local Gradle home, Android SDK, and ignored Firebase configuration.

## Post-plan requirement update (2026-07-12)

The user changed the deep-loading rule after the original plan was implemented: `더 보기` deep loading now follows slim loading exactly. `개념 브리지`, `톤 · 스타일`, and `다르게 말해보기` stay as concrete `DeepSectionHeader` text while the card below each header shimmers. This update supersedes every earlier statement in this plan that requires a deep title placeholder or the `deep_feedback_title_shimmer` tag; the Home-card body, reduced-motion path, and `DEEP_BLOCK_SKELETON_TAG` contract remain unchanged.

---

## File Structure

- Create `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/FeedbackLoadingSkeleton.kt` — feedback-only, Home-card-shaped loading composable; exports internal tags for the card and optional deep title shimmer.
- Modify `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackSheet.kt:56-63,396-419` — replace only the slim `Loading` branch's generic section rectangle with the new content-only card skeleton; retain `SlimSectionBlock` and its literal headers untouched.
- Modify `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackSections.kt:39-52,164-170` — replace the generic deep rectangle with the same card skeleton in title-placeholder mode, wrapped in the existing deep-block tag.
- Modify `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackSheetTest.kt:3-55` — add a loading-state Compose regression test for all three visible slim titles, six Home-style skeleton cards (three slim plus three deep), and three deep title shimmer pieces.
- Do not modify `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt` or `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickSkeleton.kt`; the former remains the visual reference and the latter has unrelated consumers.

## Decision Checkpoint

No execution-level decision is unresolved. The existing Home list skeleton is the applicable visual reference, and the feedback package can reuse its already-public primitives without changing shared behavior or the Home implementation.

### Task 1: Lock the slim/deep loading anatomy with a Compose regression test

**Files:**
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackSheetTest.kt:3-55`

**Interfaces:**
- Consumes: `SlimFeedbackContent(state, onRetry, onSkip, onNext, deepState, deepExpanded)`, `SlimFeedbackState.Active`, `SectionState.Loading`, and `DeepFeedbackState.Loading`.
- Produces: a test contract for production tags `"feedback_loading_card"` and `"deep_feedback_title_shimmer"`: three real slim headers stay visible; exactly six card skeletons render when all slim sections and all deep blocks are loading; exactly three title shimmers belong to deep only.

- [ ] **Step 1: Add loading fixtures, test-tag literals, and the failing rendering test**

In `SlimFeedbackSheetTest.kt`, add these imports after the existing Compose test imports:

```kotlin
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
```

Add these constants inside `SlimFeedbackSheetTest`, immediately below `val composeRule`:

```kotlin
    private companion object {
        const val FEEDBACK_LOADING_CARD_TAG = "feedback_loading_card"
        const val DEEP_FEEDBACK_TITLE_SHIMMER_TAG = "deep_feedback_title_shimmer"
    }
```

Add this loading fixture below `active()`:

```kotlin
    private fun loading(): SlimFeedbackState.Active =
        SlimFeedbackState.Active(
            header = RecapHeader(koreanPrompt = "라떼 한 잔을 주문해보세요", userText = "Can I get a latte?"),
            writingScore = SectionState.Loading,
            grammar = SectionState.Loading,
            natural = SectionState.Loading,
        )
```

Add this test below `footer_holds_both_more_toggle_and_next`:

```kotlin
    @Test
    fun loading_keeps_slim_titles_visible_and_shimmers_deep_titles_inside_home_style_cards() {
        // OneClickShimmerPiece uses an infinite transition; keep the test scheduler from waiting for it.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            OceTheme {
                SlimFeedbackContent(
                    state = loading(),
                    onRetry = {},
                    onSkip = {},
                    onNext = {},
                    deepState = DeepFeedbackState.Loading(),
                    deepExpanded = true,
                )
            }
        }

        listOf("작문 점수", "문법", "자연스러운 표현").forEach { title ->
            composeRule.onNodeWithText(title).assertIsDisplayed()
        }
        listOf("개념 브리지", "톤 · 스타일", "다르게 말해보기").forEach { title ->
            composeRule.onNodeWithText(title).assertDoesNotExist()
        }
        composeRule.onAllNodesWithTag(FEEDBACK_LOADING_CARD_TAG).assertCountEquals(6)
        composeRule.onAllNodesWithTag(DEEP_FEEDBACK_TITLE_SHIMMER_TAG).assertCountEquals(3)
    }
```

- [ ] **Step 2: Run the focused test and verify the current implementation fails**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests "com.jjundev.oneclickeng.feature.session.feedback.SlimFeedbackSheetTest"
```

Expected: FAIL in `loading_keeps_slim_titles_visible_and_shimmers_deep_titles_inside_home_style_cards`, because the generic `OneClickSkeleton(SkeletonShape.Section)` has neither `feedback_loading_card` nor `deep_feedback_title_shimmer`; the count assertion reports `0` instead of `6`.

- [ ] **Step 3: Commit the test contract before implementing the visual replacement**

```bash
git add android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackSheetTest.kt
git commit -m "test(feedback): define card skeleton loading contract"
```

Expected: one commit containing only the new loading-state regression test and its imports/fixtures.

### Task 2: Implement and wire the Home-style feedback card skeleton

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/FeedbackLoadingSkeleton.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackSheet.kt:56-63,396-419`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackSections.kt:39-52,164-170`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackSheetTest.kt`
- Regression test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackRegionTest.kt`

**Interfaces:**
- Consumes: `OneClickCard`, `OneClickShimmerPiece(shape, modifier, reduceMotion)`, `OceTheme.shapes.radius12`, `OceTheme.spacing.lg`, `OceTheme.spacing.md`, `OceTheme.motion.shimmerLoopMs` (indirectly through `OneClickShimmerPiece`), and `rememberReduceMotion` (the default argument).
- Produces: `internal fun FeedbackLoadingSkeleton(showTitlePlaceholder: Boolean, modifier: Modifier = Modifier, reduceMotion: Boolean = rememberReduceMotion())`; every invocation emits one node tagged `feedback_loading_card`, and `showTitlePlaceholder = true` emits exactly one node tagged `deep_feedback_title_shimmer`.
- Preserves: `SectionSlot` only calls the new skeleton for `SectionState.Loading`; `BlockSkeleton` keeps its outer `DEEP_BLOCK_SKELETON_TAG` wrapper, so existing deep tests observe the same three-block contract.

- [ ] **Step 1: Create the feedback-scoped Home-card skeleton**

Create `FeedbackLoadingSkeleton.kt` with this complete content:

```kotlin
package com.jjundev.oneclickeng.feature.session.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.component.OneClickShimmerPiece
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** Test tag for each Home-recommended-situation-shaped feedback loading card. */
internal const val FEEDBACK_LOADING_CARD_TAG = "feedback_loading_card"

/** Test tag for the title placeholder included only in deep-feedback loading blocks. */
internal const val DEEP_FEEDBACK_TITLE_SHIMMER_TAG = "deep_feedback_title_shimmer"

private val FeedbackSkeletonLineShape = RoundedCornerShape(6.dp)
private val DeepTitlePlaceholderWidth = 112.dp

/**
 * Feedback loading card with the same card frame and 40dp leading shimmer anatomy as Home's recommended
 * situation skeleton. Slim callers leave [showTitlePlaceholder] false because their real section header stays
 * outside this card; deep callers pass true because the deep title arrives with the block data.
 */
@Composable
internal fun FeedbackLoadingSkeleton(
    showTitlePlaceholder: Boolean,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotion(),
) {
    OneClickCard(modifier = modifier.fillMaxWidth().testTag(FEEDBACK_LOADING_CARD_TAG)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = OceTheme.spacing.lg, vertical = OceTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
        ) {
            if (showTitlePlaceholder) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
                ) {
                    OneClickShimmerPiece(
                        shape = OceTheme.shapes.radius12,
                        modifier = Modifier.size(20.dp),
                        reduceMotion = reduceMotion,
                    )
                    OneClickShimmerPiece(
                        shape = FeedbackSkeletonLineShape,
                        modifier = Modifier.width(DeepTitlePlaceholderWidth).height(16.dp).testTag(DEEP_FEEDBACK_TITLE_SHIMMER_TAG),
                        reduceMotion = reduceMotion,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
            ) {
                OneClickShimmerPiece(
                    shape = OceTheme.shapes.radius12,
                    modifier = Modifier.size(40.dp),
                    reduceMotion = reduceMotion,
                )
                OneClickShimmerPiece(
                    shape = FeedbackSkeletonLineShape,
                    modifier = Modifier.weight(1f).height(14.dp),
                    reduceMotion = reduceMotion,
                )
            }
        }
    }
}
```

The body row intentionally matches `SituationSkeletonRow` in `HomeScreen.kt`: `OneClickCard`, 16dp horizontal / 12dp vertical padding, a 40dp `radius12` leading piece, a 12dp gap, and a 14dp rounded label line. The optional 20dp icon-plus-label row models the deep block's title as loading rather than exposing its final text.

- [ ] **Step 2: Wire slim loading to the content-only card and remove the now-unused generic-skeleton imports**

In `SlimFeedbackSheet.kt`, delete these two imports:

```kotlin
import com.jjundev.oneclickeng.ui.component.OneClickSkeleton
import com.jjundev.oneclickeng.ui.component.SkeletonShape
```

Replace the `Loading` branch at line 404 with:

```kotlin
        is SectionState.Loading -> FeedbackLoadingSkeleton(showTitlePlaceholder = false)
```

Do not change the three `SlimSectionBlock(...)` calls at lines 261-275. Their icon and Korean label remain outside `SectionSlot`, which is the mechanism that keeps every slim title concrete during loading.

- [ ] **Step 3: Wire deep loading to the title-inclusive card while preserving its block-count tag**

In `DeepFeedbackSections.kt`, delete these two imports:

```kotlin
import com.jjundev.oneclickeng.ui.component.OneClickSkeleton
import com.jjundev.oneclickeng.ui.component.SkeletonShape
```

Replace `BlockSkeleton()` with:

```kotlin
@Composable
private fun BlockSkeleton() {
    Box(modifier = Modifier.testTag(DEEP_BLOCK_SKELETON_TAG)) {
        FeedbackLoadingSkeleton(showTitlePlaceholder = true)
    }
}
```

Keep `DEEP_BLOCK_SKELETON_TAG` declared in this file. The outer `Box` is necessary because a second `testTag` on the same semantic node would overwrite the reusable card tag; nesting lets legacy deep tests count blocks while the new test counts Home-style cards independently.

- [ ] **Step 4: Run the targeted feedback tests and verify the new loading contract passes**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests "com.jjundev.oneclickeng.feature.session.feedback.SlimFeedbackSheetTest" --tests "com.jjundev.oneclickeng.feature.session.feedback.DeepFeedbackRegionTest"
```

Expected: PASS. `SlimFeedbackSheetTest` finds all three concrete slim titles, finds none of the three concrete deep titles, finds six `feedback_loading_card` nodes, and finds three `deep_feedback_title_shimmer` nodes. `DeepFeedbackRegionTest.loading_shows_three_skeletons` still finds three `deep_block_skeleton` nodes, and both error cases still find zero.

- [ ] **Step 5: Run static verification, inspect both loading paths, and commit**

Run:

```bash
scripts/verify-android.sh :app:detekt :app:checkNoRawHexColors
```

Expected: PASS. No raw colors, animation constants, or new dependencies are introduced.

Then inspect the two paths manually in a debug build:

1. Trigger a turn and observe the initial slim loading state: `작문 점수`, `문법`, and `자연스러운 표현` remain readable above three card-frame shimmers.
2. Tap `더 보기` after slim feedback settles and observe deep loading: each of the three placeholders has a shimmer icon-and-title row plus the Home-style 40dp-leading card row; concrete `개념 브리지`, `톤 · 스타일`, and `다르게 말해보기` text does not appear until its individual block is ready.
3. Enable Android's reduced-motion setting and repeat either state: placeholders remain static rather than sweeping.

Commit:

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/FeedbackLoadingSkeleton.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackSheet.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackSections.kt
git commit -m "feat(feedback): align loading skeletons with home cards"
```

Expected: one implementation commit that contains the reusable feedback skeleton and only its two callers.

## Self-Review

1. **Spec coverage:** Task 2 preserves all three slim headers because only `SectionSlot` changes; the test simultaneously proves that no deep title text leaks during loading and that deep title shimmer is present through `showTitlePlaceholder = true`; both paths reuse the Home card primitives and therefore the same 1200ms/reduced-motion shimmer behavior. The shared `OneClickSkeleton` and Home screen remain unchanged, preventing unrelated UI changes.
2. **Placeholder scan:** This plan contains concrete files, production tags, Kotlin bodies, test code, Gradle commands, expected results, and commit commands. It contains no deferred implementation markers.
3. **Type consistency:** The test's literal tag values exactly match the internal constants in `FeedbackLoadingSkeleton.kt`; `FeedbackLoadingSkeleton` takes `Boolean`, `Modifier`, and `Boolean` reduced-motion inputs, while the slim/deep callers use the first two through named/default arguments. The deep outer tag remains `DEEP_BLOCK_SKELETON_TAG` so its pre-existing tests remain valid.

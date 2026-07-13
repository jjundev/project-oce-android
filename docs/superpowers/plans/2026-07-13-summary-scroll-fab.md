# Summary Scroll-Assist FAB Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the prototype's floating scroll-assist FAB (`summaryFab`) to the bottom of the session-summary screen — a circular button above the "완료" footer that pages down through the summary and flips to a "back to top" control once the user reaches the bottom.

**Architecture:** Pure view-layer change inside the already-stateless `SummaryScreen` composable. The FAB reuses the screen's existing `rememberScrollState()` — no ViewModel, Coordinator, or state-model changes. The FAB overlays the scroll region (anchored bottom-center, above the fixed footer) and derives its icon/behavior from `scrollState.value` vs `scrollState.maxValue`. Visibility is gated on the footer being present (`onDone != null`) and the content actually being scrollable (`maxValue > 0`), so it never renders as a dead affordance.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Compose BOM `2025.01.00` (foundation 1.7.x → `ScrollState.viewportSize` + `animateScrollBy` available), Robolectric + Roborazzi for tests/screenshots.

## Global Constraints

- **No emoji in UI** (P16) — icons are Material Symbols vectors via `OceIcon`/`OneClickIcon`. The FAB chevron reuses `OceIcon.ExpandMore` (rotated 180° for the "up" state), mirroring the existing `MoreChevron`.
- **Design tokens only** — colors from `MaterialTheme.colorScheme` (`surface`, `outlineVariant`, `primary`); no hardcoded hex. Dimensions as named `private val … .dp` constants, not inline magic numbers (detekt `MagicNumber`).
- **Icon `contentDescription` is a visible choice** — `OneClickIcon` has no default `contentDescription`; the FAB icon carries the accessibility label (parent `clickable` provides the action), matching `MoreChevron` at `SummaryScreen.kt:800`.
- **Android verification runs via `scripts/verify-android.sh`** (worktree-isolated `GRADLE_USER_HOME`, copies `google-services.json`) — never bare `./gradlew`.
- **Roborazzi goldens are gitignored** (`android/app/build/outputs/roborazzi/*.png`) — the screenshot test is a dev-time parity tool, not a committed regression gate. It does not fail the build when the image changes; re-recording is for manual visual parity only.

---

### Task 1: Scroll-assist FAB on the summary screen

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryScreen.kt`
  - Add imports
  - Restructure the scroll region (`SummaryScreen`, currently `SummaryScreen.kt:122-162`) to wrap the scroll `Column` in a `Box` and overlay the FAB
  - Add the `SummaryScrollFab` private composable
  - Add FAB dimension/behavior constants (near the existing `private val` block around `SummaryScreen.kt:1002`)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryScrollFabTest.kt` (create)

**Interfaces:**
- Consumes (already present in `SummaryScreen`):
  - `scrollState: androidx.compose.foundation.ScrollState = rememberScrollState()` — `SummaryScreen.kt:97`
  - `onDone: (() -> Unit)? ` param — FAB renders only when non-null
  - `SUMMARY_SCROLL_CONTENT_TAG: String` — the scroll `Column`'s `testTag` (`SummaryScreen.kt:82`), used by the test to drive scrolling
- Produces (new, file-private — no cross-file consumers):
  - `@Composable private fun SummaryScrollFab(scrollState: ScrollState, modifier: Modifier = Modifier)` — self-gating (returns early when `scrollState.maxValue <= 0`); exposes an accessible control whose `contentDescription` is `"아래로 스크롤"` (not at end) or `"맨 위로"` (at end)

---

- [ ] **Step 1: Write the failing test**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryScrollFabTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.summary

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class SummaryScrollFabTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows down chevron at top and stays down after a single page-down`() {
        setScreen(onDone = {})

        composeRule.onNodeWithContentDescription("아래로 스크롤").assertIsDisplayed()

        // 한 번 page-down 해도 20개 표현 카드라 끝에 못 닿음 → 여전히 "아래로 스크롤".
        composeRule.onNodeWithContentDescription("아래로 스크롤").performClick()
        composeRule.onNodeWithContentDescription("아래로 스크롤").assertIsDisplayed()
    }

    @Test
    fun `flips to up chevron at bottom and returns to top on tap`() {
        setScreen(onDone = {})

        composeRule.onNodeWithTag(SUMMARY_SCROLL_CONTENT_TAG).performTouchInput {
            repeat(8) { swipeUp(durationMillis = 1) }
        }
        composeRule.onNodeWithContentDescription("맨 위로").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("맨 위로").performClick()
        composeRule.onNodeWithContentDescription("아래로 스크롤").assertIsDisplayed()
    }

    @Test
    fun `hides fab when the done footer is absent`() {
        setScreen(onDone = null)

        composeRule.onNodeWithContentDescription("아래로 스크롤").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("맨 위로").assertDoesNotExist()
    }

    private fun setScreen(onDone: (() -> Unit)?) {
        composeRule.setContent {
            OceTheme {
                Surface {
                    SummaryScreen(
                        state = tallState(),
                        onRetry = {},
                        onToggleSaveWord = {},
                        onToggleSaveExpression = {},
                        onDone = onDone,
                    )
                }
            }
        }
    }

    private fun tallState() =
        SummaryState(
            totalScore = 85,
            highlight = HighlightTurn("커피 주세요", "Could I get a latte?", 92),
            bookmarks = List(8) { BookmarkCard("I got lost on the way.", "오는 길에 길을 잃었어요.") },
            accrual = AccrualStrip(streakDays = 1, xp = 20),
            bundle =
                SectionBundle.Sectioned(
                    expression =
                        SummarySectionState.Ready(
                            List(20) {
                                ExpressionCard(
                                    ExpressionType.Natural,
                                    "커피 주세요",
                                    "One coffee",
                                    "Could I grab a coffee?",
                                    "가볍게 주문할 때 자연스러워요.",
                                )
                            },
                        ),
                    word =
                        SummarySectionState.Ready(
                            List(12) {
                                WordCard("grab", "잽싸게 가져오다", "verb", "B1", "Let me grab it.", "제가 가져올게요.")
                            },
                        ),
                    coaching =
                        SummarySectionState.Ready(Coaching("끝까지 대화를 이어갔어요.", "과거형을 한 번 써볼까요?")),
                ),
        )
}
```

Notes for the implementer:
- The `tallState()` fixture mirrors `SummaryScrollEndGateTest.tallState()` (same file/package) — 20 expressions + 12 words guarantee `scrollState.maxValue > 0` at the default Robolectric viewport, so the FAB renders.
- `totalScore = 85` also triggers `OneClickConfettiBurst`, but it is a non-blocking decorative overlay (no `contentDescription`, does not intercept input), so it does not affect these queries — the same fixture/pattern already passes in `SummaryScrollEndGateTest`.

- [ ] **Step 2: Run the test to verify it fails (compile failure — `SummaryScrollFab` not yet added)**

Run:
```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.session.summary.SummaryScrollFabTest'
```
Expected: FAIL. First run fails to **compile** the test because the FAB and its `contentDescription`s (`"아래로 스크롤"` / `"맨 위로"`) don't exist yet, and/or `hides fab when the done footer is absent` is the only assertion that could pass. Confirm the failure references this test class (not an unrelated compile break).

- [ ] **Step 3: Add the required imports to `SummaryScreen.kt`**

Insert these imports in alphabetical position among the existing `androidx.compose.*` / `kotlinx.*` imports (top of the file, `SummaryScreen.kt:5-65`):

```kotlin
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
```

(`background`, `border`, `clickable`, `Box`, `size`, `clip`, `rotate`, `CircleShape`, `Alignment`, `Modifier`, `MaterialTheme`, `remember`, `getValue`, `OneClickIcon`, `OceIcon`, `dp` are already imported.)

- [ ] **Step 4: Restructure the scroll region to overlay the FAB**

In `SummaryScreen` (`SummaryScreen.kt:122-162`), replace the inner scroll `Column` + footer block. The current code is:

```kotlin
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // 스크롤 콘텐츠(위) — weight 로 남은 높이를 채우고, 완료 풋터는 하단 고정(프로토 flex:none 풋터).
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .testTag(SUMMARY_SCROLL_CONTENT_TAG)
                        .padding(OceTheme.spacing.sheetPadding),
                verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sectionGap),
            ) {
                SummaryTitleBar()
                ScoreHero(state.totalScore, state.isFirstSession)
                AccrualCard(state.accrual)
                StreakCaption(state.accrual.streakDays)
                state.highlight?.let { HighlightSection(it) }
                SseBundle(
                    bundle = state.bundle,
                    expanded = expanded,
                    onRetry = onRetry,
                    savedWordIndices = state.savedWordIndices,
                    savedExprIndices = state.savedExprIndices,
                    onToggleSaveWord = onToggleSaveWord,
                    onToggleSaveExpression = onToggleSaveExpression,
                )
                BookmarkSection(state.bookmarks)
                CoachingArea(bundle = state.bundle, onRetry = onRetry)
            }
            // 완료 풋터 — 항상 화면 하단에 고정(스크롤과 무관, 프로토 정합). [onDone] null(온보딩 첫 세션의
            // GoogleSavePromptSheet 오버레이 케이스)이면 미표시.
            if (onDone != null) {
                SummaryDoneFooter(label = doneLabel, onDone = onDone)
            }
        }
        // 진입 폭죽(프로토 fireConfetti) — 점수 있을 때만, 장식 오버레이(입력 미차단·reduce-motion 미발사).
        if (state.totalScore != null) {
            OneClickConfettiBurst(modifier = Modifier.matchParentSize())
        }
    }
```

Replace it with (the scroll `Column` moves inside a new `weight(1f)` `Box`; the FAB is a bottom-center overlay of that `Box`, so it floats just above the footer):

```kotlin
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // 스크롤 영역 — weight 로 남은 높이를 채운다. 스크롤 콘텐츠 위에 스크롤 보조 FAB 를 오버레이하고,
            // 완료 풋터는 이 Box 아래 형제로 두어 FAB 가 풋터 바로 위에 뜨도록 한다(프로토 summaryFab 정합).
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .testTag(SUMMARY_SCROLL_CONTENT_TAG)
                            .padding(OceTheme.spacing.sheetPadding),
                    verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sectionGap),
                ) {
                    SummaryTitleBar()
                    ScoreHero(state.totalScore, state.isFirstSession)
                    AccrualCard(state.accrual)
                    StreakCaption(state.accrual.streakDays)
                    state.highlight?.let { HighlightSection(it) }
                    SseBundle(
                        bundle = state.bundle,
                        expanded = expanded,
                        onRetry = onRetry,
                        savedWordIndices = state.savedWordIndices,
                        savedExprIndices = state.savedExprIndices,
                        onToggleSaveWord = onToggleSaveWord,
                        onToggleSaveExpression = onToggleSaveExpression,
                    )
                    BookmarkSection(state.bookmarks)
                    CoachingArea(bundle = state.bundle, onRetry = onRetry)
                }
                // 스크롤 보조 FAB — 완료 풋터가 있을 때만(온보딩 GoogleSavePromptSheet 오버레이 케이스 제외).
                if (onDone != null) {
                    SummaryScrollFab(
                        scrollState = scrollState,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = SummaryFabBottomGap),
                    )
                }
            }
            // 완료 풋터 — 항상 화면 하단에 고정(스크롤과 무관, 프로토 정합). [onDone] null(온보딩 첫 세션의
            // GoogleSavePromptSheet 오버레이 케이스)이면 미표시.
            if (onDone != null) {
                SummaryDoneFooter(label = doneLabel, onDone = onDone)
            }
        }
        // 진입 폭죽(프로토 fireConfetti) — 점수 있을 때만, 장식 오버레이(입력 미차단·reduce-motion 미발사).
        if (state.totalScore != null) {
            OneClickConfettiBurst(modifier = Modifier.matchParentSize())
        }
    }
```

- [ ] **Step 5: Add the `SummaryScrollFab` composable**

Insert this composable immediately after `SummaryDoneFooter` (after `SummaryScreen.kt:194`, before `SummaryTitleBar`):

```kotlin
/**
 * 스크롤 보조 FAB(프로토 summaryFab) — 완료 풋터 바로 위에 떠 있는 원형 버튼. 끝에 닿기 전엔 아래 chevron
 * (탭 = 뷰포트 [SUMMARY_FAB_PAGE_FRACTION] 만큼 page-down), 끝에 닿으면 위 chevron(탭 = 맨 위로). 시각은
 * [MoreChevron] 원형 버튼(흰 서피스 + hairline)과 동일 규칙에 그림자만 더한다. 스크롤이 불가능하면
 * (내용이 뷰포트에 다 들어와 [ScrollState.maxValue] == 0) 죽은 어포던스가 되므로 렌더하지 않는다.
 */
@Composable
private fun SummaryScrollFab(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    if (scrollState.maxValue <= 0) return
    val scope = rememberCoroutineScope()
    val tolerancePx = with(LocalDensity.current) { SummaryFabAtEndTolerance.roundToPx() }
    val atEnd by remember(tolerancePx) {
        derivedStateOf { scrollState.value >= scrollState.maxValue - tolerancePx }
    }
    Box(
        modifier =
            modifier
                .size(SummaryFabSize)
                .shadow(SummaryFabElevation, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable {
                    scope.launch {
                        if (atEnd) {
                            scrollState.animateScrollTo(0)
                        } else {
                            scrollState.animateScrollBy(scrollState.viewportSize * SUMMARY_FAB_PAGE_FRACTION)
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        OneClickIcon(
            icon = OceIcon.ExpandMore,
            contentDescription = if (atEnd) "맨 위로" else "아래로 스크롤",
            tint = MaterialTheme.colorScheme.primary,
            size = SummaryFabIconSize,
            modifier = Modifier.rotate(if (atEnd) 180f else 0f),
        )
    }
}
```

- [ ] **Step 6: Add the FAB constants**

Insert these next to the existing `private val` block (after `MoreChevronSize` at `SummaryScreen.kt:1020`, before `HIGHLIGHT_BADGE_ALPHA`):

```kotlin
/** 스크롤 보조 FAB 지름(프로토 summaryFab 48px 원형). */
private val SummaryFabSize = 48.dp

/** 스크롤 보조 FAB chevron 아이콘 크기(프로토 26px). */
private val SummaryFabIconSize = 26.dp

/** 스크롤 보조 FAB 그림자 높이(프로토 soft box-shadow 근사). */
private val SummaryFabElevation = 6.dp

/** 스크롤 보조 FAB 와 완료 풋터 사이 간격(프로토 FAB bottom:104 ≈ 풋터 높이 + 16). */
private val SummaryFabBottomGap = 16.dp

/** "끝 도달" 판정 허용 오차(프로토 scrollHeight − 8). */
private val SummaryFabAtEndTolerance = 8.dp

/** FAB 한 번 탭 시 내려가는 뷰포트 비율(프로토 clientHeight * 0.82). */
private const val SUMMARY_FAB_PAGE_FRACTION = 0.82f
```

- [ ] **Step 7: Run the test to verify it passes**

Run:
```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.session.summary.SummaryScrollFabTest'
```
Expected: PASS — all three tests green.

- [ ] **Step 8: Run detekt + the summary regression tests (no regressions)**

Run:
```bash
scripts/verify-android.sh :app:detekt :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.session.summary.*'
```
Expected: PASS — detekt clean (no `MagicNumber`/style violations in the new code) and the existing `SummaryScrollEndGateTest`, `SummaryScreenshotTest`, `SummaryCoordinatorTest` still pass. The layout restructure (scroll `Column` → wrapped in a `Box`) preserves `SUMMARY_SCROLL_CONTENT_TAG` and the `onScrollEndReached` gate, so `SummaryScrollEndGateTest` must stay green.

- [ ] **Step 9: Record the summary screenshot and confirm visual parity (manual, non-gating)**

Run:
```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.session.summary.SummaryScreenshotTest' -Proborazzi.record
```
Then open `android/app/build/outputs/roborazzi/summary_light.png` and confirm the FAB renders as a circular white button with a hairline border, a brand-primary down-chevron, and a soft shadow, floating just above the "완료" footer — matching the prototype `summaryFab`. (This PNG is gitignored; this step is for manual parity only and does not gate the build. `summary_full_light.png` uses a 2600dp viewport where content is not scrollable, so the FAB is intentionally absent there.)

- [ ] **Step 10: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryScreen.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryScrollFabTest.kt
git commit -m "feat(summary): add scroll-assist FAB above the done footer"
```

---

## Self-Review

**1. Spec coverage** — The request ("세션 요약 화면 하단에 fab 핸들바 추가, 프로토타입 참고"):
- Prototype `summaryFab` markup (circular 48px button, `left:50%; bottom:104px`, surface-card bg, hairline border, brand-primary 26px chevron, soft shadow, `scale(.94)` on active) → Step 5 (composable) + Step 6 (constants). ✅ Note: the prototype's `scale(.94)` press feedback is omitted — the standard Compose `clickable` ripple provides press feedback, matching the sibling `MoreChevron` which also has no scale animation (consistency over a pixel-exact press effect). Documented deviation.
- Prototype behavior (`onSummaryScroll` tracks `atEnd = scrollTop + clientHeight >= scrollHeight - 8`; icon `keyboard_arrow_up/down`; aria `맨 위로`/`아래로 스크롤`; tap: at-end → smooth scroll to top, else → `scrollBy(clientHeight * 0.82)`) → Step 5 `atEnd` derivedState (8dp tolerance), icon rotation, `contentDescription`, and the `animateScrollTo(0)` / `animateScrollBy(viewportSize * 0.82)` tap branches. ✅
- Positioned at the **bottom** above the footer → Step 4 bottom-center overlay of the `weight(1f)` `Box`. ✅
- Deviation from prototype: FAB **hidden when content isn't scrollable** (`maxValue <= 0`) and when the footer is absent (`onDone == null`). Rationale documented in Step 5's KDoc and the Architecture note — a scroll-assist control with nothing to scroll (or with a bottom sheet covering the footer area) is a dead/colliding affordance. ✅

**2. Placeholder scan** — No `TBD`/`TODO`/"add error handling"/"similar to". Every code step shows complete code; every run step shows the exact command and expected result. ✅

**3. Type consistency** — `SummaryScrollFab(scrollState: ScrollState, modifier: Modifier)` is defined in Step 5 and called with exactly those arguments in Step 4. Constants (`SummaryFabSize`, `SummaryFabIconSize`, `SummaryFabElevation`, `SummaryFabBottomGap`, `SummaryFabAtEndTolerance`, `SUMMARY_FAB_PAGE_FRACTION`) are declared in Step 6 and each is referenced in Step 4/5. `contentDescription` strings (`"아래로 스크롤"` / `"맨 위로"`) match exactly between the composable (Step 5) and the test queries (Step 1). `SUMMARY_SCROLL_CONTENT_TAG` reused unchanged. ✅

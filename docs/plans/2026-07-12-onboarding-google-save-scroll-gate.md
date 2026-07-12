# Onboarding Google Save Scroll Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 첫 온보딩 세션의 Google 진도 저장 시트를 세션 요약의 최하단에 도달한 뒤 0.5초가 지나서만 보여준다.

**Architecture:** `SummaryScreen`이 기존 `ScrollState`를 소유한 채, 선택적 `onScrollEndReached` 콜백으로 스크롤 최하단 도달을 상위에 알린다. `OnboardingSummaryDestination`은 세션 ID 기준으로 저장 가능한 UI 상태를 보존하고, 첫 세션일 때만 이 콜백을 `GoogleSavePromptSheet` 표시로 변환한다. Google 인증·이관·분석 이벤트 계약은 변경하지 않는다.

**Tech Stack:** Kotlin, Jetpack Compose, Compose runtime `snapshotFlow`, Kotlin coroutines, Robolectric/Compose UI test, Firebase Analytics 기존 어댑터

## Global Constraints

- Google 저장 제안은 첫 세션 완료 후에만 제공하고, primary/secondary/skip 카피와 Google 링크·게스트 이관 동작은 변경하지 않는다.
- 시트는 스크롤 가능한 요약의 최하단에 도달한 뒤 정확히 500ms 동안 그 위치가 유지된 경우에만 표시한다.
- 최하단 대기 중 사용자가 위로 스크롤하거나 콘텐츠 크기가 변해 최하단이 아니게 되면 500ms 대기를 취소한다.
- 일반 세션(`isFirstSession=false`)에는 Google 저장 시트를 표시하거나 스크롤 게이트 코루틴을 시작하지 않는다.
- `google_save_prompt_shown`은 실제 시트가 composition에 들어온 한 번의 시점에만 기존처럼 기록한다. 새 이벤트·새 파라미터를 만들지 않는다.
- 최하단으로 스크롤할 영역이 없는 경우에는 시트를 자동으로 표시하지 않는다(`ScrollState.maxValue > 0` 필요). 사용자는 기존 요약이 아닌 다른 정상 경로로는 이 진입점에 오지 않으며, 요약 콘텐츠는 첫 세션에서 스크롤 가능하게 유지한다.
- Android minSdk 26, targetSdk 36, Compose BOM 2025.01.00 및 기존 디자인 토큰/`OneClickBottomSheet`를 유지한다.
- 인증 토큰·Activity Context를 ViewModel에 전달하지 않으며, `GoogleSavePromptSheet`의 기존 `GoogleCredentialProvider` 경계를 유지한다.

---

## File Structure

| File | Responsibility |
|---|---|
| `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryScreen.kt` | 스크롤 가능한 요약의 하단 도달을 500ms debounce하고, 상위 콜백을 한 번만 호출하는 UI seam을 제공한다. |
| `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryViewModel.kt` | `SummaryRoute`가 새 UI seam을 `SummaryScreen`까지 투명하게 전달한다. |
| `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/OnboardingGraph.kt` | 첫 세션 전용·저장 가능한 시트 표시 상태와 순수 가시성 판정을 소유하고, 최하단 콜백을 Google 시트 표시로 연결한다. |
| `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryScrollEndGateTest.kt` | 500ms 지연과 최하단 이탈 시 취소 동작을 Compose UI 테스트로 고정한다. |
| `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/OnboardingRoutesTest.kt` | 첫 세션/일반 세션 및 하단 도달 여부에 대한 순수 Google 시트 가시성 정책을 고정한다. |
| `docs/ux/01-onboarding-first-session.md` | 온보딩 상태 모델·기본 흐름·복귀 정책에서 Google 저장 제안의 새 노출 조건을 정본으로 기록한다. |
| `docs/ui/04-screen-01-onboarding.md` | O4 Google 저장 제안의 시트 트리거와 500ms·취소 규칙을 화면 설계 문서에 기록한다. |

## Decision Checkpoint

결정이 더 필요하지 않다. 기존 제품 결정(첫 성공 후에만 Google 저장 제안, 첫 세션만 노출, 시트의 세 버튼과 스킵 동작)은 유지한다. “맨 아래까지 스크롤했을 때 0.5초 후”는 스크롤 가능한 콘텐츠의 최하단에서 500ms 유지로 구현하고, 대기 중 하단을 벗어나면 취소하는 것이 의도와 Compose 스크롤 상태 모두에 정합한다.

### Task 1: 요약 화면의 최하단 500ms 게이트

**Files:**
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryScrollEndGateTest.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryScreen.kt:12-125`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryViewModel.kt:62-99`

**Interfaces:**
- Consumes: `SummaryState`, existing `SummaryScreen` callbacks, Compose `ScrollState`.
- Produces: `SummaryRoute(..., onScrollEndReached: (() -> Unit)? = null)` and `SummaryScreen(..., onScrollEndReached: (() -> Unit)? = null)`; the callback is invoked after one continuous 500ms interval at a scrollable lower bound.

- [ ] **Step 1: Write the failing Compose UI tests for the lower-bound gate**

Create `SummaryScrollEndGateTest.kt` with a deliberately tall ready-state fixture and the following tests. Keep this test in the same package as `SummaryScreen` so it can use the production `SUMMARY_SCROLL_CONTENT_TAG` constant.

```kotlin
package com.jjundev.oneclickeng.feature.session.summary

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class SummaryScrollEndGateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `lower bound invokes callback only after 500 milliseconds`() {
        composeRule.mainClock.autoAdvance = false
        var callbackCount = 0
        setScreen { callbackCount += 1 }

        scrollToBottom()
        composeRule.mainClock.advanceTimeBy(GOOGLE_SAVE_PROMPT_DELAY_MS - 1)
        composeRule.runOnIdle { assertEquals(0, callbackCount) }

        composeRule.mainClock.advanceTimeBy(1)
        composeRule.runOnIdle { assertEquals(1, callbackCount) }
    }

    @Test
    fun `leaving the lower bound cancels the pending callback`() {
        composeRule.mainClock.autoAdvance = false
        var callbackCount = 0
        setScreen { callbackCount += 1 }

        scrollToBottom()
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.onNodeWithTag(SUMMARY_SCROLL_CONTENT_TAG).performTouchInput { swipeDown() }
        composeRule.mainClock.advanceTimeBy(GOOGLE_SAVE_PROMPT_DELAY_MS)

        composeRule.runOnIdle { assertEquals(0, callbackCount) }
    }

    @Test
    fun `content shrinking while still at the lower bound preserves the pending delay`() {
        composeRule.mainClock.autoAdvance = false
        var callbackCount = 0
        var expressionCount by mutableIntStateOf(24)
        composeRule.setContent {
            OceTheme {
                Surface {
                    SummaryScreen(
                        state = tallState(expressionCount),
                        onRetry = {},
                        onToggleSaveWord = {},
                        onToggleSaveExpression = {},
                        onScrollEndReached = { callbackCount += 1 },
                    )
                }
            }
        }

        scrollToBottom()
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.runOnIdle { expressionCount = 8 }
        composeRule.mainClock.advanceTimeBy(250)

        composeRule.runOnIdle { assertEquals(1, callbackCount) }
    }

    private fun setScreen(onScrollEndReached: () -> Unit) {
        composeRule.setContent {
            OceTheme {
                Surface {
                    SummaryScreen(
                        state = tallState(),
                        onRetry = {},
                        onToggleSaveWord = {},
                        onToggleSaveExpression = {},
                        onScrollEndReached = onScrollEndReached,
                    )
                }
            }
        }
    }

    private fun scrollToBottom() {
        composeRule.onNodeWithTag(SUMMARY_SCROLL_CONTENT_TAG).performTouchInput {
            repeat(24) { swipeUp() }
        }
    }

    private fun tallState(expressionCount: Int = 20) =
        SummaryState(
            totalScore = 85,
            highlight = HighlightTurn("커피 주세요", "Could I get a latte?", 92),
            bookmarks = List(8) { BookmarkCard("I got lost on the way.", "오는 길에 길을 잃었어요.") },
            accrual = AccrualStrip(streakDays = 1, xp = 20),
            bundle =
                SectionBundle.Sectioned(
                    expression =
                        SummarySectionState.Ready(
                            List(expressionCount) {
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
                    coaching = SummarySectionState.Ready(Coaching("끝까지 대화를 이어갔어요.", "과거형을 한 번 써볼까요?")),
                ),
        )
}
```

- [ ] **Step 2: Run the new test class to verify it fails**

Run:

```bash
./android/gradlew -p android testDebugUnitTest --tests com.jjundev.oneclickeng.feature.session.summary.SummaryScrollEndGateTest
```

Expected: compilation fails because `GOOGLE_SAVE_PROMPT_DELAY_MS`, `SUMMARY_SCROLL_CONTENT_TAG`, and the `onScrollEndReached` parameter do not yet exist.

- [ ] **Step 3: Add the callback seam and cancellable 500ms lower-bound observer**

In `SummaryScreen.kt`, add these imports beside the existing Compose imports:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
```

Declare the shared, internal testable constants immediately above `SummaryScreen`:

```kotlin
internal const val GOOGLE_SAVE_PROMPT_DELAY_MS = 500L
internal const val SUMMARY_SCROLL_CONTENT_TAG = "summary_scroll_content"
```

Replace the `SummaryScreen` signature and the beginning of its body with the following. The pre-existing content blocks remain in the inner `Column` exactly as shown; only the named `scrollState`, callback observer, and tag are new.

```kotlin
@Composable
fun SummaryScreen(
    state: SummaryState,
    onRetry: (SummarySection) -> Unit,
    onToggleSaveWord: (Int) -> Unit,
    onToggleSaveExpression: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onDone: (() -> Unit)? = null,
    doneLabel: String = "완료",
    onScrollEndReached: (() -> Unit)? = null,
) {
    val expanded = remember { mutableStateMapOf<SummarySection, Boolean>() }
    val scrollState = rememberScrollState()
    val currentOnScrollEndReached by rememberUpdatedState(onScrollEndReached)

    if (onScrollEndReached != null) {
        LaunchedEffect(scrollState) {
            snapshotFlow {
                scrollState.maxValue > 0 && scrollState.value == scrollState.maxValue
            }.distinctUntilChanged()
                .collectLatest { isAtBottom ->
                    if (isAtBottom) {
                        delay(GOOGLE_SAVE_PROMPT_DELAY_MS)
                        if (scrollState.maxValue > 0 && scrollState.value == scrollState.maxValue) {
                            currentOnScrollEndReached?.invoke()
                        }
                    }
                }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
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
            if (onDone != null) {
                SummaryDoneFooter(label = doneLabel, onDone = onDone)
            }
        }
        if (state.totalScore != null) {
            OneClickConfettiBurst(modifier = Modifier.matchParentSize())
        }
    }
}
```

In `SummaryViewModel.kt`, add the optional parameter to `SummaryRoute` and forward it unchanged:

```kotlin
@Composable
fun SummaryRoute(
    sessionId: String,
    difficulty: String,
    modeId: String,
    accrual: AccrualStrip,
    modifier: Modifier = Modifier,
    isFirstSession: Boolean = false,
    onDone: (() -> Unit)? = null,
    doneLabel: String = "완료",
    onScrollEndReached: (() -> Unit)? = null,
    viewModel: SummaryViewModel = hiltViewModel(),
) {
    LaunchedEffect(sessionId) {
        viewModel.start(
            sessionId = sessionId,
            difficulty = difficulty,
            modeId = modeId,
            accrual = accrual,
            isFirstSession = isFirstSession,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SummaryScreen(
        state = state,
        onRetry = viewModel::retry,
        onToggleSaveWord = viewModel::toggleSaveWord,
        onToggleSaveExpression = viewModel::toggleSaveExpression,
        modifier = modifier,
        onDone = onDone,
        doneLabel = doneLabel,
        onScrollEndReached = onScrollEndReached,
    )
}
```

- [ ] **Step 4: Run focused tests to verify the callback contract passes**

Run:

```bash
./android/gradlew -p android testDebugUnitTest --tests com.jjundev.oneclickeng.feature.session.summary.SummaryScrollEndGateTest --tests com.jjundev.oneclickeng.feature.session.summary.SummaryScreenshotTest
```

Expected: `BUILD SUCCESSFUL`; the new tests prove no callback at 499ms, one callback at 500ms, cancellation after leaving the lower bound, and that a max-value change which still leaves the user at the lower bound does not restart the timer. Existing summary screenshot tests still compile because the callback is optional.

- [ ] **Step 5: Commit the reusable summary scroll-gate seam**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryScreen.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryViewModel.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryScrollEndGateTest.kt
git commit -m "feat(summary): expose delayed scroll-end callback"
```

### Task 2: 첫 온보딩 Google 저장 시트를 게이트에 연결하고 UX 정본 갱신

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/OnboardingGraph.kt:1-270`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/OnboardingRoutesTest.kt:1-81`
- Modify: `docs/ux/01-onboarding-first-session.md:39-95, 108-114, 190`
- Modify: `docs/ui/04-screen-01-onboarding.md:45-48`

**Interfaces:**
- Consumes: `SummaryRoute(..., onScrollEndReached: (() -> Unit)?)`, `GoogleSavePromptSheet` unchanged, and `isFirstSession` from `ONBOARDING_SUMMARY_ROUTE`.
- Produces: `internal fun shouldShowGoogleSavePrompt(isFirstSession: Boolean, summaryScrollEndReached: Boolean): Boolean`; this is the only policy used to compose `GoogleSavePromptSheet`.

- [ ] **Step 1: Write failing policy tests for first-session-only display**

Append these tests to `OnboardingRoutesTest` before its closing brace:

```kotlin
    @Test
    fun `Google save prompt stays hidden until the first-session summary reaches its lower bound`() {
        assertTrue(!shouldShowGoogleSavePrompt(isFirstSession = true, summaryScrollEndReached = false))
        assertTrue(shouldShowGoogleSavePrompt(isFirstSession = true, summaryScrollEndReached = true))
    }

    @Test
    fun `repeat-session summary never shows the onboarding Google save prompt`() {
        assertTrue(!shouldShowGoogleSavePrompt(isFirstSession = false, summaryScrollEndReached = false))
        assertTrue(!shouldShowGoogleSavePrompt(isFirstSession = false, summaryScrollEndReached = true))
    }
```

- [ ] **Step 2: Run the onboarding policy test to verify it fails**

Run:

```bash
./android/gradlew -p android testDebugUnitTest --tests com.jjundev.oneclickeng.feature.onboarding.OnboardingRoutesTest
```

Expected: compilation fails because `shouldShowGoogleSavePrompt` does not exist.

- [ ] **Step 3: Persist the reveal state in the onboarding destination and leave Google sheet behavior intact**

In `OnboardingGraph.kt`, add these imports:

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
```

Add this policy directly above `OnboardingSummaryDestination`:

```kotlin
internal fun shouldShowGoogleSavePrompt(
    isFirstSession: Boolean,
    summaryScrollEndReached: Boolean,
): Boolean = isFirstSession && summaryScrollEndReached
```

Replace `OnboardingSummaryDestination` with this version. It deliberately does not modify `GoogleSavePromptSheet`; therefore its existing `LaunchedEffect(sessionId)` records `google_save_prompt_shown` only once, when this condition becomes true.

```kotlin
@Composable
private fun OnboardingSummaryDestination(
    sessionId: String,
    userLevel: String,
    isFirstSession: Boolean,
    onLinked: () -> Unit,
    onOneMore: () -> Unit,
    onExitToHome: () -> Unit,
) {
    var summaryScrollEndReached by rememberSaveable(sessionId) { mutableStateOf(false) }
    val onScrollEndReached: (() -> Unit)? =
        if (isFirstSession && !summaryScrollEndReached) {
            { summaryScrollEndReached = true }
        } else {
            null
        }

    Box(modifier = Modifier.fillMaxSize()) {
        SummaryRoute(
            sessionId = sessionId,
            difficulty = if (isFirstSession) FIRST_SESSION_LEVEL else userLevel,
            modeId = "default",
            accrual = AccrualStrip(streakDays = 0, xp = 0),
            isFirstSession = isFirstSession,
            onDone = if (isFirstSession) null else onExitToHome,
            onScrollEndReached = onScrollEndReached,
        )
        if (
            shouldShowGoogleSavePrompt(
                isFirstSession = isFirstSession,
                summaryScrollEndReached = summaryScrollEndReached,
            )
        ) {
            GoogleSavePromptSheet(
                sessionId = sessionId,
                onLinked = onLinked,
                onOneMore = onOneMore,
                onSkip = onExitToHome,
            )
        }
    }
}
```

- [ ] **Step 4: Update the UX source of truth and the screen inventory**

In `docs/ux/01-onboarding-first-session.md`, make all of these exact semantic changes:

```markdown
  → SummaryEntered
  → AwardingCompletion  ┊  SummaryLoading(turnBuffer)   # 독립/병렬 (┊ = 서로 게이팅 안 함)
                        ┊    → SummaryPartialFailure(done.sections) | SummaryReady
                             └→ SummaryScrollEndHeld(500ms)  # 어느 요약 상태에서도 최하단 도달 시
                                 → GoogleSavePrompt
```

Add this state-table row immediately before `GoogleSavePrompt`:

```markdown
| `SummaryScrollEndHeld` | 첫 세션의 스크롤 가능한 요약이 최하단에 도달한 상태. 500ms 동안 유지되면 `GoogleSavePrompt`로 전이하고, 최하단을 벗어나면 요약 상태로 돌아간다. |
```

Replace the `GoogleSavePrompt` state-table description with:

```markdown
| `GoogleSavePrompt` | 첫 세션의 **스크롤 가능한** 요약을 최하단까지 내린 뒤 500ms 동안 그 위치를 유지하면 진도 저장 제안을 표시한다. 대기 중 위로 스크롤하거나 콘텐츠 변화로 최하단이 아니게 되면 대기를 취소한다. |
```

Replace basic-flow items 11–14 with:

```markdown
11. 첫 세션 요약이 스크롤 가능해진 뒤 사용자가 최하단까지 내리고 500ms 동안 그 위치를 유지하면 `GoogleSavePrompt`를 보여준다. 요약 로딩·부분 실패·적립은 이 노출 조건을 게이팅하지 않는다.
12. 최하단 대기 중 사용자가 위로 스크롤하거나 동적 요약 콘텐츠 변화로 현재 위치가 더 이상 최하단이 아니게 되면 500ms 대기를 취소한다. 콘텐츠 높이만 바뀌어도 현재 위치가 계속 최하단이면 대기를 유지한다. 화면이 스크롤 불가능하면 자동으로 시트를 열지 않는다.
13. `Google로 진도 저장`을 primary CTA로, `한 번 더 하기`를 secondary CTA로 둔다.
14. `Google로 진도 저장`은 `linkWithCredential`을 시도한다. 신규 신원이면 `GoogleLinkSucceeded`(데이터 자동 보존, merge 없음)로 홈에 진입하고, `credential-already-in-use` 충돌이면 `GuestMergePending`으로 이관 흐름을 탄다.
15. 사용자가 스킵하면 게스트 상태로 홈에 진입한다.
```

Replace the “요약 진입 후 이탈” recovery row with:

```markdown
| 요약 진입 후 이탈 | 요약으로 복귀한다. 최하단 500ms 조건을 아직 충족하지 못했으면 Google 저장 제안을 다시 열지 않는다. |
```

Keep the event name and parameters unchanged, but change its explanatory line in the event list to:

```markdown
| `google_save_prompt_shown` | `session_id` — 첫 세션 요약 최하단 500ms 조건 충족 후 시트가 실제로 표시될 때 기록 |
```

In `docs/ui/04-screen-01-onboarding.md`, replace O4’s status line with:

```markdown
- **현황:** 첫 세션에서만, 스크롤 가능한 세션 요약을 최하단까지 내린 뒤 500ms 동안 유지하면 노출한다. 대기 중 최하단을 벗어나면 취소한다. primary `Google로 진도 저장` / secondary `한 번 더 하기` / skip `나중에 할게요`. 스킵 시 게스트 홈 진입. 카피: `가입` 대신 `진도 저장`.
```

Keep the existing rev2 bottom-sheet container decision, button order, labels, and existing analytics event names unchanged.

- [ ] **Step 5: Run focused tests and Android static checks**

Run:

```bash
./android/gradlew -p android testDebugUnitTest --tests com.jjundev.oneclickeng.feature.onboarding.OnboardingRoutesTest --tests com.jjundev.oneclickeng.feature.session.summary.SummaryScrollEndGateTest detektDebug ktlintCheck
```

Expected: `BUILD SUCCESSFUL`; policy tests prove only a first session with a received scroll-end signal can compose the sheet, while the Compose test proves that signal has the requested 500ms lower-bound behavior.

- [ ] **Step 6: Perform the targeted manual verification**

Run:

```bash
./scripts/verify-android.sh
```

Expected: the repository’s Android verification script finishes successfully. On an emulator/device, complete a first onboarding session, confirm that the summary is initially interactive without the Google sheet, scroll to the absolute lower bound, wait 500ms, then confirm the sheet uses the existing three actions. Repeat with a normal session and confirm its fixed “완료” footer remains and no Google sheet appears.

- [ ] **Step 7: Commit the onboarding integration and source-of-truth updates**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/OnboardingGraph.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/OnboardingRoutesTest.kt \
        docs/ux/01-onboarding-first-session.md \
        docs/ui/04-screen-01-onboarding.md
git commit -m "feat(onboarding): delay Google save prompt until summary end"
```

## Self-Review

1. **Spec coverage:** Task 1 implements the requested 0.5-second delay, cancellation on departure, and continuous timing across a content-size change that remains at the lower bound; Task 2 limits the behavior to the first onboarding session, retains every existing Google save action, and records the new trigger timing in both UX sources of truth. No Google Auth, merge, server, copy, or normal-session behavior is expanded.
2. **Placeholder scan:** This plan contains no TBD/TODO, vague test instruction, or undefined interface. Every introduced production symbol (`GOOGLE_SAVE_PROMPT_DELAY_MS`, `SUMMARY_SCROLL_CONTENT_TAG`, `onScrollEndReached`, and `shouldShowGoogleSavePrompt`) is defined in a task before use.
3. **Type consistency:** `onScrollEndReached` is nullable `(() -> Unit)?` in both `SummaryRoute` and `SummaryScreen`; `shouldShowGoogleSavePrompt` takes two `Boolean`s; all call sites use those exact names and types.

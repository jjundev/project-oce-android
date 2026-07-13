# Tab UI/UX Tweaks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Four small UI/UX corrections across the 학습(Home)·기록(Records) tabs: simplify the "설정 변경" chip to a single label, make recommended-situation taps reflect onto the hero card instead of starting a session immediately, add the slot-machine count-up (with minute→hour rollover) to the lifetime study-time stat, and change the Records tab title bar to match the Settings tab's pinned centered header.

**Architecture:** Jetpack Compose. Changes are localized edits plus one small primitive extension (`OneClickCountUp` gains a `format` lambda) and one extracted shared composable (`PinnedTabHeader`). No ViewModel/data-layer changes. Screenshot tests here are capture-only (no committed goldens, no `.compare()`), so appearance changes will not fail CI.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Robolectric + `createComposeRule` for interaction tests, JUnit4.

## Global Constraints

- **Verification command (worktree):** all gradle verification MUST go through `scripts/verify-android.sh` (worktree-isolated `GRADLE_USER_HOME`; bare `./gradlew` gives false positives). Single class: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ClassName*'`. Full set (final gate): `scripts/verify-android.sh` (detekt + androidTest compile + both variant unit tests).
- **No new goldens:** the Roborazzi screenshot tests use `captureRoboImage(...)` only (no comparison). Do not add golden files or `.compare()` calls.
- **detekt is in the default set** — unused imports fail the build. When you delete code that used an import, delete the import too.
- **Korean UI copy** is the product language; keep all user-facing strings in Korean, matching surrounding style.
- **Test placement:** new Robolectric/unit tests go under `android/app/src/test/kotlin/com/jjundev/oneclickeng/...` mirroring the source package, matching existing tests (e.g. `HomeHeroRevealTest`, `OneClickBottomSheetExpandTest`).
- All file paths below are relative to the worktree root: `/Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/.claude/worktrees/learning-records-settings-ui-7ce50c/`.

---

## File Structure

**Modified:**
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt` — chip text (Task 1); recommended-situation reflect-only + KDoc/comments (Task 2).
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickCountUp.kt` — add `format` lambda param (Task 3).
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/LifetimeStatsHeader.kt` — study-time slot-machine + `formatStudyTime` helper (Task 4).
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/TabScreenScaffold.kt` — pinned centered header instead of scrolling inline title (Task 5).
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt` — reuse extracted `PinnedTabHeader` (Task 5).

**Created:**
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/PinnedTabHeader.kt` — shared 48dp centered header (Task 5).
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeSettingsChipTest.kt` — Task 1.
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeSituationTapTest.kt` — Task 2.
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/OneClickCountUpFormatTest.kt` — Task 3.
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/LifetimeStatsFormatTest.kt` — Task 4.
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsTitleBarTest.kt` — Task 5.

---

### Task 1: 학습 탭 — "설정 변경" 칩 단일 텍스트

Change the inline settings chip from `"설정 변경 · 쉬움 · 5턴"` (label · difficulty · turns) to a single `"설정 변경"` label. The chip's expand/collapse behavior and the level-null gating are unchanged — only the displayed string changes.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt:738-744`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeSettingsChipTest.kt`

**Interfaces:**
- Consumes: existing `HomeContent(state, onStartLearning, ...)` stateless composable (signature at `HomeScreen.kt:247-270`), `HomeUiState` (`feature/home/HomeUiState.kt:18-34`), `SelectedSituation(topicId, labelKo, promptSeed)`.
- Produces: no new public API. Behavior contract: the settings-inline row renders exactly the text `"설정 변경"` when `state.level != null` and `!state.hasResume`.

- [ ] **Step 1: Write the failing test**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeSettingsChipTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.home

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 회귀: 홈 인라인 설정 칩은 난이도·턴수를 붙이지 않고 "설정 변경" 단일 라벨만 노출한다.
 * level 해소(easy)·이어하기 없음 조건에서 칩이 렌더되고, 정확 텍스트 "설정 변경" 노드가 존재해야 한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HomeSettingsChipTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settings_chip_shows_single_label() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                HomeContent(
                    state =
                        HomeUiState(
                            isOnline = true,
                            hasResume = false,
                            level = "easy",
                            length = 5,
                            selectedSituation = SelectedSituation("id", "카페에서 주문하기", "seed"),
                            situations = emptyList(),
                        ),
                    onStartLearning = {},
                    onResumeContinue = {},
                    onResumeStartNew = {},
                    onViewRecords = {},
                    onOfflineBlocked = {},
                )
            }
        }
        // 인라인 설정 칩까지 스크롤(라지 뷰포트 비의존). 스크롤 대상은 부분일치로 찾는다.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("설정 변경", substring = true))
        // 정확 텍스트 매칭 — 구버전 "설정 변경 · 쉬움 · 5턴" 이면 정확일치 실패(RED).
        composeRule.onNodeWithText("설정 변경").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*HomeSettingsChipTest*'`
Expected: FAIL — the only node containing "설정 변경" has exact text `"설정 변경 · 쉬움 · 5턴"`, so the exact-match `onNodeWithText("설정 변경")` finds no node.

- [ ] **Step 3: Change the chip text**

In `HomeScreen.kt`, replace the `Text(...)` block at lines 738-744:

```kotlin
            Text(
                text =
                    level?.let { "설정 변경 · ${levelLabel(it)} · ${length}턴" }
                        ?: "설정 변경 · 불러오는 중",
                style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
```

with:

```kotlin
            Text(
                text = "설정 변경",
                style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
```

Note: keep the surrounding `Row`'s `clickable(enabled = level != null)` (`HomeScreen.kt:728`) and the `AnimatedVisibility(visible = expanded && level != null, ...)` (`HomeScreen.kt:753-757`) unchanged — the row stays non-expandable until `level` resolves (#6 guard). Only the label string changed. The `level` and `length` params of `SettingsInline` are still used by the segmented controls inside the expanded panel, so do not remove them.

- [ ] **Step 4: Run the test to verify it passes**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*HomeSettingsChipTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeSettingsChipTest.kt
git commit -m "fix(home): collapse settings chip to single '설정 변경' label"
```

---

### Task 2: 학습 탭 — 추천 상황 탭은 히어로 반영만(즉시 시작 제거)

Currently, tapping a recommended-situation row selects the topic AND immediately starts a session (프로토 `startTopic`). Change it to only reflect the choice onto the hero card (like the "다른 상황 고르기" sheet `pickTopic` path), leaving the actual start to the hero CTA. This is a one-line removal in the stateful `HomeScreen` wrapper plus KDoc/comment updates. The stateful wrapper uses `hiltViewModel()`, so it is not unit-testable in isolation — the testable seam is `HomeContent`'s callback contract, backed by a manual app check.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt:187-193` (the `onSituationSelected` lambda), and comments at `HomeScreen.kt:132-133`, `:928`, `:975`.
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeSituationTapTest.kt`

**Interfaces:**
- Consumes: `HomeContent(..., onSituationSelected: (HomeSituation) -> Unit = {}, ...)` (`HomeScreen.kt:256`); `HomeSituation(id, labelKo, icon = OceIcon.Hub, promptSeed = "")` (`feature/home/HomeUiState.kt:47-53`); `viewModel.selectSituationById(id)` (`HomeViewModel.kt:157-159`).
- Produces: behavior contract — the recommended-situation row invokes `onSituationSelected(situation)` and does NOT invoke `onStartLearning`. In the stateful wrapper, selecting a recommended situation updates hero state only (no `onStartSession`).

- [ ] **Step 1: Write the failing test**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeSituationTapTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.home

import android.app.Application
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 계약 가드: 추천 상황 행 탭은 선택 콜백([onSituationSelected])만 호출하고, 학습 시작([onStartLearning])은
 * 호출하지 않는다(시작은 히어로 CTA 소유). 스테이트풀 래퍼(hiltViewModel)는 이 단위테스트 범위 밖이라,
 * 실제 "히어로 반영만" 배선은 앱 수동 검증(Step 5)으로 보강한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HomeSituationTapTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recommended_row_tap_selects_but_does_not_start() {
        var selected: HomeSituation? = null
        var started = false
        val row = HomeSituation(id = "cafe", labelKo = "카페에서 주문하기", promptSeed = "seed")
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                HomeContent(
                    state =
                        HomeUiState(
                            isOnline = true,
                            hasResume = false,
                            level = "easy",
                            length = 5,
                            selectedSituation = SelectedSituation("other", "날씨로 스몰토크", "s2"),
                            situations = listOf(row),
                        ),
                    onStartLearning = { started = true },
                    onResumeContinue = {},
                    onResumeStartNew = {},
                    onViewRecords = {},
                    onOfflineBlocked = {},
                    onSituationSelected = { selected = it },
                )
            }
        }
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("카페에서 주문하기"))
        composeRule.onNodeWithText("카페에서 주문하기").performClick()

        assertEquals(row, selected)
        assertFalse("추천 행 탭이 학습 시작을 트리거하면 안 된다", started)
    }
}
```

- [ ] **Step 2: Run the test to verify it passes as a guard (characterization)**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*HomeSituationTapTest*'`
Expected: PASS. `HomeContent`'s row already routes taps to `onSituationSelected` (not `onStartLearning`), so this documents/locks the contract. (This is a characterization guard, not a red-first test — the behavioral change lives in the Hilt-bound stateful wrapper below, which this codebase does not unit-test.)

- [ ] **Step 3: Make the stateful wrapper reflect-only**

In `HomeScreen.kt`, replace the `onSituationSelected` lambda at lines 187-193:

```kotlin
        onSituationSelected = { situation ->
            // 프로토 startTopic — 선택 갱신 + 즉시 시작.
            viewModel.selectSituationById(situation.id)
            startWithCurrentSetup(
                SelectedSituation(situation.id, situation.labelKo, situation.promptSeed),
            )
        },
```

with:

```kotlin
        onSituationSelected = { situation ->
            // 추천 행 탭 = 선택만 갱신해 히어로에 반영(시트 pickTopic 과 동일). 시작은 히어로 CTA 가 소유한다.
            viewModel.selectSituationById(situation.id)
        },
```

- [ ] **Step 4: Update the now-inaccurate KDoc/comments**

In `HomeScreen.kt`, update three comments so the documented flow matches the new behavior:

1. Lines 132-133 — replace:
```kotlin
 * 프로토 플로우 정합: 히어로 탭 = **바로 대화 생성**([onStartSession] — 세션 설정 화면 없음), 추천 행 탭 =
 * 선택 갱신 + 즉시 시작(startTopic), 시트 = 선택만 하고 닫힘(pickTopic — 홈 히어로 갱신).
```
with:
```kotlin
 * 프로토 플로우 정합: 히어로 탭 = **바로 대화 생성**([onStartSession] — 세션 설정 화면 없음), 추천 행 탭 =
 * 선택만 갱신해 히어로에 반영(시트 pickTopic 과 동일), 시트 = 선택만 하고 닫힘(홈 히어로 갱신).
 * 시작은 두 경로 모두 히어로 CTA 가 소유한다.
```

2. Line 928 — replace:
```kotlin
/** 추천 상황 1행 — 카드(선행 아이콘 + 라벨 + chevron). 탭 = 선택 갱신 + 즉시 시작(프로토 startTopic). */
```
with:
```kotlin
/** 추천 상황 1행 — 카드(선행 아이콘 + 라벨 + chevron). 탭 = 선택만 갱신해 히어로에 반영(시작은 히어로 CTA). */
```

3. Line 975 — replace:
```kotlin
/** 그리드 셀 — 컴팩트 카드(상단 아이콘 박스 + 라벨 최대 2줄, chevron 없음). 탭 = 선택 갱신 + 즉시 시작. */
```
with:
```kotlin
/** 그리드 셀 — 컴팩트 카드(상단 아이콘 박스 + 라벨 최대 2줄, chevron 없음). 탭 = 선택만 갱신해 히어로에 반영. */
```

- [ ] **Step 5: Verify the stateful behavior in the running app (manual)**

The reflect-only wiring in the stateful `HomeScreen` is not unit-tested (hiltViewModel). Verify manually: build/run the app, open the 학습 tab, tap a recommended situation row, and confirm (a) the hero card's situation label updates to the tapped situation, and (b) **no** conversation/session starts. Use the `/run` skill or `scripts/verify-android.sh :app:assembleDebug` + install. Record the observation in the task hand-off.

- [ ] **Step 6: Run the guard test again + commit**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*HomeSituationTapTest*'`
Expected: PASS.

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeSituationTapTest.kt
git commit -m "fix(home): recommended-situation tap reflects onto hero instead of starting"
```

---

### Task 3: `OneClickCountUp` — 커스텀 `format` 람다 지원

Extend the slot-machine count-up primitive so callers can format the animating integer with a lambda (not just a `unit` suffix). This is what lets the study-time stat (Task 4) render `"N시간 N분"` from a single animating minute value — the minute→hour rollover falls out of animating one integer and re-deriving hours/minutes each frame. Fully backward-compatible: existing callers keep using `unit`.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickCountUp.kt:43-101`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/OneClickCountUpFormatTest.kt`

**Interfaces:**
- Consumes: existing `OneClickCountUp` internals (`Animatable` roll-up + scaleY spring, `OceTheme.motion`).
- Produces: `OneClickCountUp(target: Int, modifier, from: Int = 0, unit: String = "", format: (Int) -> String = { "$it$unit" }, static: Boolean = false, reduceMotion, style, color)`. The rendered text and the a11y final label both go through `format(...)`. Task 4 calls it with `format = ::formatStudyTime`.

- [ ] **Step 1: Write the failing test**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/OneClickCountUpFormatTest.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.component

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [OneClickCountUp] 의 [format] 람다는 애니메이션 텍스트와 a11y 최종 라벨 모두에 적용된다.
 * reduceMotion 로 즉시 스냅시켜 최종값(135분 → "2시간 15분")이 contentDescription 으로 노출되는지 본다
 * (프리미티브가 clearAndSetSemantics 로 최종 라벨을 contentDescription 에 싣는다).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OneClickCountUpFormatTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun format_lambda_applies_to_final_label() {
        composeRule.setContent {
            OceTheme {
                OneClickCountUp(
                    target = 135,
                    format = { "${it / 60}시간 ${it % 60}분" },
                    reduceMotion = true,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("2시간 15분").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*OneClickCountUpFormatTest*'`
Expected: FAIL — compilation error, `format` is not a parameter of `OneClickCountUp` (`No value passed for parameter` / `Cannot find a parameter with this name: format`).

- [ ] **Step 3: Add the `format` parameter and route both strings through it**

In `OneClickCountUp.kt`, change the signature (lines 43-52) to add `format` after `unit`:

```kotlin
@Composable
fun OneClickCountUp(
    target: Int,
    modifier: Modifier = Modifier,
    from: Int = 0,
    unit: String = "",
    format: (Int) -> String = { "$it$unit" },
    static: Boolean = false,
    reduceMotion: Boolean = rememberReduceMotion(),
    style: TextStyle = OceTheme.typography.turnScore,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
```

Change the final-label line (currently line 57):

```kotlin
    val finalLabel = "$target$unit"
```

to:

```kotlin
    val finalLabel = format(target)
```

Change the rendered text (currently line 90) from:

```kotlin
        text = "${value.value.roundToInt()}$unit",
```

to:

```kotlin
        text = format(value.value.roundToInt()),
```

Leave everything else (the `LaunchedEffect` animators, `graphicsLayer`, `clearAndSetSemantics`) unchanged. `unit` stays as a param so existing callers and the default `format` keep working.

- [ ] **Step 4: Run the test to verify it passes**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*OneClickCountUpFormatTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickCountUp.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/OneClickCountUpFormatTest.kt
git commit -m "feat(ui): OneClickCountUp supports a custom format lambda"
```

---

### Task 4: 기록 탭 — 학습 시간 슬롯머신 카운트업(분→시간 롤오버)

The lifetime `"N시간 N분"` stat is currently a static `Text` (the other two stats — XP, 학습일 — already count up). Route it through `OneClickCountUp` (Task 3's `format` lambda) so it rolls up like the others. Animating the single total-minutes value and re-deriving hours/minutes each frame produces the minute→hour transition automatically. Static/reduce-motion/parity paths still snap to the exact same `"2시간 15분"` text, so screenshot captures are unchanged.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/LifetimeStatsHeader.kt` (lines 24-79 header/KDoc, 109-130 `TimeMetric`, add `formatStudyTime` near line 141).
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/LifetimeStatsFormatTest.kt`

**Interfaces:**
- Consumes: `OneClickCountUp(target, from, format, static, style, color)` from Task 3; `LifetimeStats(xp, studyMinutes, studyDays)` (`feature/records/LifetimeStats.kt`); `MINUTES_PER_HOUR = 60` (`LifetimeStatsHeader.kt:141`).
- Produces: `internal fun formatStudyTime(totalMinutes: Int): String` returning `"${totalMinutes / 60}시간 ${totalMinutes % 60}분"`; `TimeMetric(iconTint, totalMinutes: Int, static: Boolean)` (signature changes from taking a pre-formatted `text`).

- [ ] **Step 1: Write the failing test for the formatter**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/LifetimeStatsFormatTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.records

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [formatStudyTime] 은 총 분을 "N시간 N분" 복합 표기로 만든다(시간 0이어도 "0시간" 유지 — 기존 렌더 동일).
 * 카운트업이 이 함수를 프레임마다 통과시켜 60분 경계에서 분→시간 롤오버가 자연히 나타난다.
 */
class LifetimeStatsFormatTest {
    @Test
    fun formats_total_minutes_as_hours_and_minutes() {
        assertEquals("2시간 15분", formatStudyTime(135))
        assertEquals("1시간 0분", formatStudyTime(60))
        assertEquals("0시간 59분", formatStudyTime(59))
        assertEquals("0시간 45분", formatStudyTime(45))
        assertEquals("0시간 0분", formatStudyTime(0))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*LifetimeStatsFormatTest*'`
Expected: FAIL — compilation error, `formatStudyTime` is unresolved.

- [ ] **Step 3: Add `formatStudyTime` and rewrite `TimeMetric` to count up**

In `LifetimeStatsHeader.kt`:

(a) Add the formatter next to the existing constant (after line 141 `private const val MINUTES_PER_HOUR = 60`):

```kotlin
/** 총 학습 분 → "N시간 N분" 복합 표기(시간 0이어도 유지 — 기존 정적 렌더와 동일). 카운트업 프레임 포매터. */
internal fun formatStudyTime(totalMinutes: Int): String =
    "${totalMinutes / MINUTES_PER_HOUR}시간 ${totalMinutes % MINUTES_PER_HOUR}분"
```

(b) In `LifetimeStatsHeader` (lines 38-77), remove the now-unused `hours`/`minutes` locals (lines 40-41) and pass raw minutes + `static` to `TimeMetric`. Replace the `TimeMetric(...)` call (lines 65-68):

```kotlin
            TimeMetric(
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                text = "${hours}시간 ${minutes}분",
            )
```

with:

```kotlin
            TimeMetric(
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                totalMinutes = stats.studyMinutes,
                static = static,
            )
```

and delete the two local lines (40-41):

```kotlin
    val hours = stats.studyMinutes / MINUTES_PER_HOUR
    val minutes = stats.studyMinutes % MINUTES_PER_HOUR
```

(c) Replace the `TimeMetric` composable (lines 109-130) so it uses `OneClickCountUp`:

```kotlin
@Composable
private fun TimeMetric(
    iconTint: Color,
    totalMinutes: Int,
    static: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
    ) {
        OneClickIcon(
            icon = OceIcon.Schedule,
            contentDescription = null,
            tint = iconTint,
            size = OceIconSize.FeedbackInline,
        )
        OneClickCountUp(
            target = totalMinutes,
            from = 0,
            format = ::formatStudyTime,
            static = static,
            style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
```

(d) The file already imports `OneClickCountUp` (line 18), `androidx.compose.material3.Text` — `Text` is still used by `Dot()` (line 134), so keep it. No import changes needed. (If detekt flags an unused import after these edits, remove exactly that import.)

- [ ] **Step 4: Update the KDoc that says study-time is static**

In `LifetimeStatsHeader.kt`, replace the KDoc lines 24-26:

```kotlin
/**
 * ① 평생 통계 헤더(카드 아님, R1) — `누적 N XP · 총 N시간 N분 · N일 학습`. XP·학습일은 [OneClickCountUp] 시그니처
 * 카운트업(I3)을 통과시키고, 학습시간은 복합 표기라 정적 텍스트다.
```

with:

```kotlin
/**
 * ① 평생 통계 헤더(카드 아님, R1) — `누적 N XP · 총 N시간 N분 · N일 학습`. XP·학습일·학습시간 모두
 * [OneClickCountUp] 시그니처 카운트업(I3)을 통과한다. 학습시간은 총 분 단일값을 굴리고 프레임마다
 * [formatStudyTime] 로 "N시간 N분" 을 재도출해 60분 경계에서 분→시간 롤오버가 자연히 나타난다.
```

- [ ] **Step 5: Run the formatter test + records screenshot capture to verify no regression**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*LifetimeStatsFormatTest*' --tests '*RecordsScreenScreenshotTest*'`
Expected: PASS. The screenshot test renders with `animate = false` → `static = true` → count-up snaps to `formatStudyTime(135) = "2시간 15분"`, byte-identical to the previous static text (capture-only test, no assertion failure).

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/LifetimeStatsHeader.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/LifetimeStatsFormatTest.kt
git commit -m "feat(records): animate lifetime study-time with minute→hour count-up"
```

---

### Task 5: 기록 탭 — 타이틀바를 설정 탭과 동일하게(중앙 고정 헤더)

Per the prototype, the Settings tab's centered pinned 48dp header (18sp ExtraBold, does not scroll) is the intended treatment; the Records tab should match it. Extract the header into a shared `PinnedTabHeader`, reuse it in `SettingsContent` (no visual change), and repurpose `TabScreenScaffold` to render it above a plain `LazyColumn`. `TabScreenScaffold` is consumed only by `RecordsScreen`, so this changes the Records title bar (from a left-aligned scrolling `screenTitle` to the centered pinned header) without affecting Home (which rolls its own `LazyColumn`).

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/PinnedTabHeader.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/TabScreenScaffold.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt:324-337` (+ remove two now-unused imports)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsTitleBarTest.kt`

**Interfaces:**
- Consumes: `OceTheme.typography.summaryHeadline`, `R.string.tab_settings`, `R.string.tab_records`.
- Produces: `fun PinnedTabHeader(@StringRes titleRes: Int, modifier: Modifier = Modifier)` — full-width 48dp `Box`, centered, `summaryHeadline` 18sp ExtraBold, `heading()` semantics. `TabScreenScaffold` renders it above a `LazyColumn`. `SettingsContent` uses it in place of its inline header `Box`.

- [ ] **Step 1: Write the failing test**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsTitleBarTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.records

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jjundev.oneclickeng.ui.foundation.PinnedTabHeader
import com.jjundev.oneclickeng.R
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 회귀: 기록 탭 타이틀바는 설정 탭과 동일한 [PinnedTabHeader] 를 쓴다. 헤더 컴포저블이 존재하고
 * 제목 문자열을 렌더하는지 스모크로 검증한다(중앙정렬·고정은 시각 캡처로 대조).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RecordsTitleBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pinned_header_renders_title() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PinnedTabHeader(titleRes = R.string.tab_records)
                }
            }
        }
        composeRule.onNodeWithText("기록").assertIsDisplayed()
    }
}
```

Note: verify the exact string for `R.string.tab_records` before asserting — grep `grep -rn "tab_records" android/app/src/main/res/values*/strings.xml`. If it is not literally `기록`, use the actual value in the assertion.

- [ ] **Step 2: Run the test to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*RecordsTitleBarTest*'`
Expected: FAIL — compilation error, `PinnedTabHeader` is unresolved.

- [ ] **Step 3: Create the shared `PinnedTabHeader`**

Create `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/PinnedTabHeader.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.foundation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 상시탭 공통 타이틀바(프로토 정합) — 48dp 고정 높이 중앙 헤더, 스크롤과 무관하게 상단에 고정된다.
 * 설정·기록 탭이 공유해 동일한 헤더를 보장한다([SettingsContent], [TabScreenScaffold]).
 * `TopAppBar` 를 두지 않으므로 `semantics { heading() }` 로 화면 제목 heading 랜드마크를 대체한다.
 */
@Composable
fun PinnedTabHeader(
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(titleRes),
            style = OceTheme.typography.summaryHeadline.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*RecordsTitleBarTest*'`
Expected: PASS.

- [ ] **Step 5: Repurpose `TabScreenScaffold` to pin the header (this changes the Records title bar)**

Replace the entire body of `TabScreenScaffold.kt` with:

```kotlin
package com.jjundev.oneclickeng.ui.foundation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 상시탭 화면 공통 골격(F8). 상단에 [PinnedTabHeader](48dp 고정 중앙 헤더, 프로토 정합)를 두고 그 아래
 * **단일 [LazyColumn] 호스트**로 콘텐츠를 스크롤한다. 타이틀은 스크롤과 무관하게 고정된다(설정 탭과 동일).
 *
 * - 가로 거터 20dp = `OceTheme.spacing.xl`(F8 #1) — 헤더는 전폭 중앙, 콘텐츠 리스트에만 적용한다.
 * - 헤더 heading 시맨틱은 [PinnedTabHeader] 가 소유한다.
 * - 스크롤 상태([rememberLazyListState])는 내장 Saver 로 회전에 생존하고, NavHost restoreState 로
 *   탭 전환 간에도 복원된다.
 */
@Composable
fun TabScreenScaffold(
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        PinnedTabHeader(titleRes = titleRes)
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = OceTheme.spacing.xl),
            state = rememberLazyListState(),
        ) {
            content()
        }
    }
}
```

Note: the Records segments `stickyHeader` (`RecordsScreen.kt:119`) now sticks to the top of the inner `LazyColumn` — i.e. just below the pinned title — which matches the intended pinned-title behavior. No change to `RecordsScreen.kt` is needed.

- [ ] **Step 6: Reuse `PinnedTabHeader` in Settings (no visual change) and drop dead imports**

In `SettingsScreen.kt`, replace the inline header (lines 326-337):

```kotlin
        // 48px 고정 중앙 헤더(프로토 정합).
        Box(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.tab_settings),
                style = OceTheme.typography.summaryHeadline.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        }
```

with:

```kotlin
        PinnedTabHeader(titleRes = R.string.tab_settings)
```

Add the import (with the other `ui.foundation` imports):

```kotlin
import com.jjundev.oneclickeng.ui.foundation.PinnedTabHeader
```

Remove the two imports that are now unused (the header was their only use — confirmed: `.semantics {` and `heading()` appear only in that block):

```kotlin
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
```

Do NOT remove `Box`, `height`, `Alignment`, `FontWeight`, `sp`, `stringResource`, `Text`, or `MaterialTheme` imports — they are still used elsewhere in `SettingsScreen.kt`.

- [ ] **Step 7: Verify full build (detekt catches any stray unused import) + settings capture unchanged**

Run: `scripts/verify-android.sh :app:detekt :app:testDebugUnitTest --tests '*RecordsTitleBarTest*' --tests '*SettingsScreenScreenshotTest*'`
Expected: PASS — detekt clean (no unused imports), settings capture unchanged (`PinnedTabHeader` renders the identical header the inline `Box` did).

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/PinnedTabHeader.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/TabScreenScaffold.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsTitleBarTest.kt
git commit -m "fix(records): match Settings pinned centered title bar via shared PinnedTabHeader"
```

---

## Final Verification

- [ ] **Run the full verification set**

Run: `scripts/verify-android.sh`
Expected: `BUILD SUCCESSFUL` — detekt clean, androidTest compiles, both variant unit tests pass (includes all five new test classes).

- [ ] **Eyeball the captures (optional, recommended)**

The Roborazzi captures are written to `android/app/build/outputs/roborazzi/`. Compare `records_*` and `settings_*` PNGs against `prototype/screenshot/records.png` to confirm: the Records title is now centered/pinned (matching Settings), and the lifetime study-time still reads `"…시간 …분"`.

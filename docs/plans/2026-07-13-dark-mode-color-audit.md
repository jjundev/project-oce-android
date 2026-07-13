# Prototype-Aligned Dark-Mode Color Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every implemented Prototype Flow surface render with the prototype’s dark semantic palette, eliminate non-contract color fallbacks, and leave repeatable dark-mode visual evidence for every prototype screen family.

**Architecture:** Keep the existing two-layer theme design: `MaterialTheme.colorScheme` owns the documented M3 slots while `OceTheme.colors` owns semantic tokens without an M3 slot. First lock the prototype palette into a pure theme contract test, then replace the four production uses that bypass that contract, and finally extend the existing per-screen Roborazzi seams to render the same representative states under `OceTheme(darkTheme = true)`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Robolectric, Roborazzi, Gradle, `scripts/verify-android.sh`.

## Global Constraints

- The visual reference is `prototype/Prototype Flow (standalone).html`; under ADR-0006 it is the realization-SoT for rendered appearance.
- Token values and theme-slot ownership remain governed by `docs/design/design_system_src/design-tokens.md` and `docs/ui/01-foundations.md`; do not invent a new palette.
- Preserve `brand.primary = #39A0ED`, `brand.primaryPressed = #2B7FBB`, and dynamic color OFF. Do not substitute Toss Blue (`#3182F6`).
- Keep raw hexadecimal color literals confined to `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/theme/Color.kt`; `:app:checkNoRawHexColors` must continue to pass.
- Only the documented M3 slots may be consumed from `MaterialTheme.colorScheme`; replace uses of default M3 `surfaceVariant`, `outline`, and `scrim` with the owning semantic token when the prototype specifies one.
- In dark mode, use `surface.background #0E0F12`, `surface.card #1A1B20`, `text.primary #F2F3F5`, `text.secondary #A9ADB6`, `text.tertiary #7C818C`, `border.hairline #2A2C32`, and `border.strong #3A3D45` exactly.
- Preserve the documented dark semantic values: feedback backgrounds `#0F2A22` / `#321B21`, error `#FF8A80`, voice states, streak `#FF7A33`, save gold `#FFD24D`, and scrim `0x99000000`.
- Do not alter navigation, state transitions, copy, analytics, or screen layout as part of the color audit.
- Run Android verification through `scripts/verify-android.sh`; do not invoke Gradle directly from this worktree.

---

## File Structure

| File | Responsibility |
|---|---|
| `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/theme/Theme.kt` | Expose the already-defined light and dark `ColorScheme` instances to same-module contract tests without changing their values or the public `OceTheme` API. |
| `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/theme/OceThemeColorContractTest.kt` | Assert every prototype-owned M3 and custom dark token is bound to the exact color declared by the design-token spec. |
| `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/speaking/SpeakingResultView.kt` | Render the transcript bubble with the semantic card surface, not Material’s unconfigured `surfaceVariant`. |
| `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/topic/TopicSelectScreen.kt` | Render the custom-topic dashed border with `borderStrong`, including its dark value. |
| `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackSheet.kt` | Render the feedback overlay with the prototype-owned semantic scrim, not Material’s default scrim plus a second alpha adjustment. |
| `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsOverlays.kt` | Render confirmation action labels using `onPrimary`, not a hard-coded framework white. |
| `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ui/ReminderScreenshotTest.kt` | Render reminder-sheet fixtures with `OceTheme.colors.scrim` and capture opt-in, permission priming, and banner states in dark mode. |
| Existing `*ScreenshotTest.kt` files in onboarding, home, records, settings, dialogue, turn, and summary packages | Reuse their stateless screen seams and fixtures to capture the same representative states with `darkTheme = true`. |

## Decision Checkpoint

No execution-level fork remains. The prototype embeds the same dark token values already declared in `Color.kt`, so this work is a binding-and-coverage audit rather than a palette redesign. In particular, the existing product decision that `onPrimary` is white remains intact; this plan must not change brand or accessibility policy while reconciling implementation references.

### Task 1: Lock the Prototype Palette into a Theme Contract Test

**Files:**

- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/theme/Theme.kt:12-46`
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/theme/OceThemeColorContractTest.kt`
- Reference: `prototype/Prototype Flow (standalone).html:178` (embedded `[data-theme="dark"]` token block)
- Reference: `docs/design/design_system_src/design-tokens.md:25-48, 196-210`

**Interfaces:**

- Consumes: `LightColorScheme`, `DarkColorScheme`, `LightOceColors`, and `DarkOceColors` from the theme package.
- Produces: internal `LightColorScheme` and `DarkColorScheme` values that same-module unit tests can read; `OceThemeColorContractTest` as the palette regression gate.

- [ ] **Step 1: Write the failing dark-token contract test**

Create `OceThemeColorContractTest.kt` in package `com.jjundev.oneclickeng.ui.theme` with this exact test body. It intentionally names the prototype token and expected dark value at every assertion so a failed binding identifies the mismatched semantic role.

```kotlin
package com.jjundev.oneclickeng.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class OceThemeColorContractTest {
    @Test
    fun `dark ColorScheme matches Prototype Flow semantic tokens`() {
        assertEquals(Color(0xFF39A0ED), DarkColorScheme.primary)
        assertEquals(Color(0xFFFFFFFF), DarkColorScheme.onPrimary)
        assertEquals(Color(0xFF0E0F12), DarkColorScheme.background)
        assertEquals(Color(0xFF1A1B20), DarkColorScheme.surface)
        assertEquals(Color(0xFFF2F3F5), DarkColorScheme.onBackground)
        assertEquals(Color(0xFFF2F3F5), DarkColorScheme.onSurface)
        assertEquals(Color(0xFFA9ADB6), DarkColorScheme.onSurfaceVariant)
        assertEquals(Color(0xFF2A2C32), DarkColorScheme.outlineVariant)
        assertEquals(Color(0xFFFF8A80), DarkColorScheme.error)
        assertEquals(Color(0xFFFFFFFF), DarkColorScheme.onError)
    }

    @Test
    fun `dark custom colors match Prototype Flow semantic tokens`() {
        assertEquals(Color(0xFF2B7FBB), DarkOceColors.primaryPressed)
        assertEquals(Color(0xFF7C818C), DarkOceColors.textTertiary)
        assertEquals(Color(0xFF3A3D45), DarkOceColors.borderStrong)
        assertEquals(Color(0x99000000), DarkOceColors.scrim)
        assertEquals(Color(0xFF009B72), DarkOceColors.feedbackNaturalAccent)
        assertEquals(Color(0xFF0F2A22), DarkOceColors.feedbackNaturalBg)
        assertEquals(Color(0xFFEF767A), DarkOceColors.feedbackCorrectAccent)
        assertEquals(Color(0xFF321B21), DarkOceColors.feedbackCorrectBg)
        assertEquals(Color(0xFF8E96A1), DarkOceColors.voiceReadyCenter)
        assertEquals(Color(0xFF2A2C32), DarkOceColors.voiceReadyOuter)
        assertEquals(Color(0xFFFF6B66), DarkOceColors.voiceRecordingCenter)
        assertEquals(Color(0xFF3A1F22), DarkOceColors.voiceRecordingOuter)
        assertEquals(Color(0xFFB0BEC5), DarkOceColors.voiceAnalyzing)
        assertEquals(Color(0xFF66BB6A), DarkOceColors.voiceComplete)
        assertEquals(Color(0xFFFF7A33), DarkOceColors.gameStreak)
        assertEquals(Color(0xFFFFD24D), DarkOceColors.gameSaveGold)
        assertEquals(Color(0xFF39A0ED), DarkOceColors.gradientStart)
        assertEquals(Color(0xFF2B7FBB), DarkOceColors.gradientEnd)
        assertEquals(Color(0xFF9E9E9E), DarkOceColors.waveformTop)
        assertEquals(Color(0xFF757575), DarkOceColors.waveformBottom)
    }
}
```

- [ ] **Step 2: Run the test to verify the intended seam is unavailable**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*OceThemeColorContractTest*'
```

Expected: compilation fails because `DarkColorScheme` is private to `Theme.kt`; this confirms the test is checking the actual theme binding rather than duplicating a second palette.

- [ ] **Step 3: Narrow only the test seam in `Theme.kt`**

Change the two declarations from private to internal. Do not rename them, alter their constructor arguments, or expose them outside the module.

```kotlin
internal val LightColorScheme =
    lightColorScheme(
        primary = BrandBlue,
        onPrimary = White,
        background = BackgroundLight,
        surface = CardLight,
        onBackground = TextPrimaryLight,
        onSurface = TextPrimaryLight,
        onSurfaceVariant = TextSecondaryLight,
        outlineVariant = HairlineLight,
        error = StateErrorLight,
        onError = White,
    )

internal val DarkColorScheme =
    darkColorScheme(
        primary = BrandBlue,
        onPrimary = White,
        background = BackgroundDark,
        surface = CardDark,
        onBackground = TextPrimaryDark,
        onSurface = TextPrimaryDark,
        onSurfaceVariant = TextSecondaryDark,
        outlineVariant = HairlineDark,
        error = StateErrorDark,
        onError = White,
    )
```

- [ ] **Step 4: Run the contract test and the hex guard**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*OceThemeColorContractTest*'
scripts/verify-android.sh :app:checkNoRawHexColors
```

Expected: both commands finish with `BUILD SUCCESSFUL`; the first runs two passing tests and the second finds no main-source hex offenders.

- [ ] **Step 5: Commit the theme contract**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/theme/Theme.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/theme/OceThemeColorContractTest.kt
git commit -m "test(theme): lock dark prototype color tokens"
```

### Task 2: Remove Production Color-Slot Bypasses

**Files:**

- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/speaking/SpeakingResultView.kt:62-71`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/topic/TopicSelectScreen.kt:245`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackSheet.kt:67,132-142`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsOverlays.kt:16,119-124`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/topic/TopicSelectScreenshotTest.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionFlowScreenshotTest.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ui/ReminderScreenshotTest.kt`

**Interfaces:**

- Consumes: `MaterialTheme.colorScheme.surface`, `MaterialTheme.colorScheme.onPrimary`, and `OceTheme.colors.borderStrong` / `OceTheme.colors.scrim`.
- Produces: no API change; each audited surface reads a documented dark token instead of one of Material 3’s unconfigured defaults or a framework literal.

- [ ] **Step 1: Add failing dark capture names for the three affected interactive surfaces**

Add these tests to the existing screenshot suites before changing production code. Reuse the suites’ current fixed fixtures and only change the theme argument and output name.

```kotlin
@Test
fun topic_select_dark() {
    composeRule.setContent {
        OceTheme(darkTheme = true) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                TopicSelectSheetContent(
                    onTopicChosen = { _, _ -> },
                    onDismiss = {},
                    modifier = Modifier.fillMaxHeight(),
                    selectedTopicId = "cafe",
                )
            }
        }
    }
    composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/topic_select_dark.png")
}
```

```kotlin
@Test
fun settings_dark_member() =
    renderSettings(
        SettingsUiState(loading = false, nickname = "준영", isGuest = false, reminderEnabled = true),
        dark = true,
        blocked = false,
        name = "settings_dark_member",
    )
```

Add an in-window action-row capture in `SettingsScreenScreenshotTest` because `SettingsContent` does not open an overlay by itself and `SettingsConfirmDialog` opens a separate `Dialog` window. This renders the production `DialogButtonRow` directly, so `captureRoboImage` has one deterministic root while still exercising the changed `onPrimary` label.

```kotlin
@Test
fun settings_confirm_delete_dark() {
    composeRule.setContent {
        OceTheme(darkTheme = true) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                DialogButtonRow(
                    modifier = Modifier.padding(24.dp),
                    confirmLabel = "삭제",
                    confirmColor = MaterialTheme.colorScheme.error,
                    confirmEnabled = true,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
    }
    composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/settings_confirm_delete_dark.png")
}
```

Add `import androidx.compose.foundation.layout.padding` and `import androidx.compose.ui.Modifier` to this test file for the in-window `Modifier.padding(24.dp)` call.

In `ReminderScreenshotTest`, replace the test-only raw black scrim with the same theme token used by production overlays. Change the current helper signature and wrapper as follows, then remove both `import androidx.compose.ui.graphics.Color` and `private const val SCRIM = 0x66000000`:

```kotlin
private fun captureSheet(
    name: String,
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    composeRule.setContent {
        OceTheme(darkTheme = dark) {
            Box(modifier = Modifier.fillMaxSize()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    HomeContent(
                        state = sampleHomeState.copy(),
                        onStartLearning = {},
                        onResumeContinue = {},
                        onResumeStartNew = {},
                        onViewRecords = {},
                        onOfflineBlocked = {},
                    )
                }
                Box(modifier = Modifier.fillMaxSize().background(OceTheme.colors.scrim))
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .width(32.dp)
                                        .height(4.dp)
                                        .clip(OceTheme.shapes.pill)
                                        .background(MaterialTheme.colorScheme.outlineVariant),
                            )
                        }
                        Box(modifier = Modifier.padding(OceSheetDefaults.contentPadding)) { content() }
                    }
                }
            }
        }
    }
    composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
}
```

Pass `dark = false` to the existing two sheet tests. Add the following dark variants with their existing fixture values.

```kotlin
@Test
fun reminder_optin_dark() {
    captureSheet("reminder_optin_dark", dark = true) {
        OneClickReminderOptInSheetContent(onOptIn = {}, onLater = {})
    }
}

@Test
fun reminder_priming_dark() {
    captureSheet("reminder_priming_dark", dark = true) {
        OneClickPermissionPrimingSheetContent(
            icon = OceIcon.Notifications,
            rationale = "다음 화면에서 허용을 눌러주세요.\n매일 정한 시각에 학습 리마인더만 보내드려요.\n광고나 다른 알림은 없어요.",
            emphasis = "허용",
            onRequest = {},
            onLater = {},
            title = "알림을 보내도 될까요?",
            requestLabel = "계속",
            laterLabel = "다음에",
            assurance = "거부해도 학습에는 아무 영향이 없어요.",
        )
    }
}
```

In `SessionFlowScreenshotTest`, refactor `flow_feedback_light`’s renderer into a helper with an explicit theme flag, then call it for both names:

```kotlin
@Test
fun flow_feedback_light() = captureFeedback(name = "flow_feedback_light", dark = false)

@Test
fun flow_feedback_dark() = captureFeedback(name = "flow_feedback_dark", dark = true)

private fun captureFeedback(name: String, dark: Boolean) {
    composeRule.setContent {
        OceTheme(darkTheme = dark) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.fillMaxSize()) {
                    DialogueTurnContent(
                        messages = feedbackBehind,
                        turnPhase = TurnPhase.OpponentTurn,
                        sessionPhase = SessionPhase.InTurn,
                        currentTask = null,
                        listState = rememberLazyListState(),
                        onSubmitStub = {},
                        onViewSummary = {},
                        header = header,
                    )
                    Box(modifier = Modifier.fillMaxSize().background(OceTheme.colors.scrim))
                    Surface(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.7f).dp),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(32.dp)
                                            .height(4.dp)
                                            .clip(OceTheme.shapes.pill)
                                            .background(MaterialTheme.colorScheme.outlineVariant),
                                )
                            }
                            SlimFeedbackContent(
                                state = slimActive(),
                                onRetry = {},
                                onSkip = {},
                                onNext = {},
                                modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
    composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
}
```

- [ ] **Step 2: Run the captures before the fixes**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*TopicSelectScreenshotTest*' --tests '*SessionFlowScreenshotTest*' --tests '*SettingsScreenScreenshotTest*' -Proborazzi.record
scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReminderScreenshotTest*' -Proborazzi.record
```

Expected: tests compile and write `topic_select_dark.png`, `flow_feedback_dark.png`, `settings_confirm_delete_dark.png`, `reminder_optin_dark.png`, and `reminder_priming_dark.png` under `android/app/build/outputs/roborazzi/`; visual inspection reveals the pre-fix default M3 `outline`, `scrim`, or `surfaceVariant` where applicable.

- [ ] **Step 3: Bind each surface to its owning semantic token**

Apply exactly these replacements.

```kotlin
// SpeakingResultView.kt — TranscriptBubble
Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier.fillMaxWidth(),
) {
```

```kotlin
// TopicSelectScreen.kt — CustomTopicRow collapsed state
val dashColor = OceTheme.colors.borderStrong
```

```kotlin
// SlimFeedbackSheet.kt — remove `private const val SCRIM_ALPHA = 0.32f`.
// The custom token already contains the prototype’s complete light/dark alpha.
.background(OceTheme.colors.scrim)
```

```kotlin
// SettingsOverlays.kt — DialogButtonRow action label
Text(
    text = confirmLabel,
    style = OceTheme.typography.sectionLabel.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
    color = MaterialTheme.colorScheme.onPrimary,
)
```

Keep `import androidx.compose.ui.graphics.Color` in `SettingsOverlays.kt`: `confirmColor: Color` remains a required parameter of `SettingsConfirmDialog` and `DialogButtonRow`. Only the framework color literal is removed.

- [ ] **Step 4: Verify the bypass scan and affected rendering**

Run:

```bash
rg -n 'colorScheme\.(surfaceVariant|outline\b|scrim)|Color\.White' android/app/src/main/kotlin --glob '*.kt'
scripts/verify-android.sh :app:testDebugUnitTest --tests '*TopicSelectScreenshotTest*' --tests '*SessionFlowScreenshotTest*' --tests '*SettingsScreenScreenshotTest*' -Proborazzi.record
scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReminderScreenshotTest*' -Proborazzi.record
scripts/verify-android.sh :app:checkNoRawHexColors
```

Expected: the `rg` result has no production matches; all affected screenshot tests and the hex guard pass. Compare `topic_select_dark.png`, `flow_feedback_dark.png`, `settings_confirm_delete_dark.png`, `reminder_optin_dark.png`, and `reminder_priming_dark.png` against the dark prototype’s card/border/scrim treatment.

- [ ] **Step 5: Commit token-consumption fixes**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/speaking/SpeakingResultView.kt \
  android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/topic/TopicSelectScreen.kt \
  android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackSheet.kt \
  android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsOverlays.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/topic/TopicSelectScreenshotTest.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionFlowScreenshotTest.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ui/ReminderScreenshotTest.kt
git commit -m "fix(ui): bind dark surfaces to oce color tokens"
```

### Task 3: Capture Dark Variants for Every Prototype Screen Family

**Files:**

- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/level/LevelQuestionScreenshotTest.kt`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/topic/TopicQuestionScreenshotTest.kt`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreenScreenshotTest.kt`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreenScreenshotTest.kt`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ui/ReminderScreenshotTest.kt`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGeneratingScreenshotTest.kt`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreenshotTest.kt`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryScreenshotTest.kt`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt`

**Interfaces:**

- Consumes: each suite’s existing stateless content seam and immutable fixture data.
- Produces: deterministic `*_dark.png` Roborazzi captures matching the prototype states `level`, `topic`, `generating`, `session`, `summary`, `home`, `history`, `settings`, and `limit`.

- [ ] **Step 1: Refactor each simple screen capture to accept `dark: Boolean`**

For `LevelQuestionScreenshotTest`, replace the light-only body with the following parameterized helper and tests.

```kotlin
private fun capture(name: String, dark: Boolean) {
    composeRule.setContent {
        OceTheme(darkTheme = dark) {
            Surface(color = MaterialTheme.colorScheme.background) {
                LevelQuestionContent(onLevelSelected = {}, reduceMotion = true)
            }
        }
    }
    composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
}

@Test
fun onboarding_level_light() = capture(name = "onboarding_level_light", dark = false)

@Test
fun onboarding_level_dark() = capture(name = "onboarding_level_dark", dark = true)
```

Apply the pattern with these exact production content calls and PNG names:

| Test file | Content seam | Add dark test/output |
|---|---|---|
| `LevelQuestionScreenshotTest.kt` | `LevelQuestionContent(onLevelSelected = {}, reduceMotion = true)` | `onboarding_level_dark.png` |
| `TopicQuestionScreenshotTest.kt` | `TopicQuestionContent(onTopicSelected = {}, onBack = {}, reduceMotion = true)` | `onboarding_topic_dark.png` |
| `RecordsScreenScreenshotTest.kt` | existing expression `RecordsContent` fixture | `records_dark_expression.png` |
| `HomeScreenScreenshotTest.kt` | existing new-session `HomeContent` plus `OceBottomNav` fixture | `home_dark_newsession.png` |
| `SettingsScreenScreenshotTest.kt` | existing `renderSettings` fixture | retain `settings_dark_guest.png` and add `settings_dark_member.png` from Task 2 |
| `ReminderScreenshotTest.kt` | existing `HomeContent` banner fixture | `home_dark_reminder_banner.png` |

Add the reminder-banner capture with the test file’s existing `sampleHomeState` fixture:

```kotlin
@Test
fun home_dark_reminder_banner() {
    composeRule.setContent {
        OceTheme(darkTheme = true) {
            Surface(color = MaterialTheme.colorScheme.background) {
                HomeContent(
                    state = sampleHomeState.copy(),
                    onStartLearning = {},
                    onResumeContinue = {},
                    onResumeStartNew = {},
                    onViewRecords = {},
                    onOfflineBlocked = {},
                    showReminderBanner = true,
                )
            }
        }
    }
    composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/home_dark_reminder_banner.png")
}
```

- [ ] **Step 2: Add dark generation and limit captures without changing clock behavior**

Change `captureAfterGate` in `DialogueGeneratingScreenshotTest` to take a `dark` argument and pass it to `OceTheme`. Add these tests while retaining `mainClock.autoAdvance = false` and `advanceTimeBy(GATE_ADVANCE_MS)` exactly as the light helper does.

```kotlin
@Test
fun limit_dark() = captureLimit(name = "limit_dark", dark = true)

@Test
fun generating_quiz_dark() =
    captureAfterGate(DialogueGenState.Generating, name = "generating_quiz_dark", dark = true)

@Test
fun generating_ready_dark() =
    captureAfterGate(
        DialogueGenState.Ready(sessionId = "s", remaining = 2, meta = null, turns = emptyList()),
        name = "generating_ready_dark",
        dark = true,
    )
```

Implement `captureLimit` with the existing `limit_light` `DialogueGeneratingScreen` call, changing only `OceTheme(darkTheme = dark)` and the path interpolation:

```kotlin
private fun captureLimit(name: String, dark: Boolean) {
    composeRule.setContent {
        OceTheme(darkTheme = dark) {
            Surface(color = MaterialTheme.colorScheme.background) {
                DialogueGeneratingScreen(
                    state = DialogueGenState.QuotaBlocked(remaining = 0),
                    quizItems = previewWaitQuizItems(),
                    onStartConversation = {},
                    onRetry = {},
                )
            }
        }
    }
    composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
}
```

- [ ] **Step 3: Add session and summary dark captures at their existing stable seams**

Refactor `DialogueTurnScreenshotTest`’s repeated wrappers into a helper that takes `dark`, then add the three high-signal dark states below. Preserve the existing messages, `header`, `MicDock`, and `reduceMotion = true` fixtures.

```kotlin
@Test
fun session_opponent_dark() = captureOpponent(name = "session_opponent_dark", dark = true)

@Test
fun session_learner_dark() = captureLearner(name = "session_learner_dark", dark = true, textMode = false)

@Test
fun session_recording_dark() = captureRecording(name = "session_recording_dark", dark = true)
```

Make `SummaryScreenshotTest.capture` accept `dark: Boolean`, then retain the existing light tests and add:

```kotlin
@Test
fun summary_dark() = capture("build/outputs/roborazzi/summary_dark.png", dark = true)

@Test
@Config(qualifiers = "+h2600dp")
fun summary_full_dark() = capture("build/outputs/roborazzi/summary_full_dark.png", dark = true)
```

The helper body must continue to render the unchanged `richState()` fixture inside:

```kotlin
OceTheme(darkTheme = dark) {
    Surface(color = MaterialTheme.colorScheme.background) {
        SummaryScreen(
            state = richState(),
            onRetry = {},
            onToggleSaveWord = {},
            onToggleSaveExpression = {},
            onDone = {},
        )
    }
}
```

- [ ] **Step 4: Record every dark screen family and inspect against Prototype Flow**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest \
  --tests '*LevelQuestionScreenshotTest*' \
  --tests '*TopicQuestionScreenshotTest*' \
  --tests '*HomeScreenScreenshotTest*' \
  --tests '*RecordsScreenScreenshotTest*' \
  --tests '*SettingsScreenScreenshotTest*' \
  --tests '*ReminderScreenshotTest*' \
  --tests '*DialogueGeneratingScreenshotTest*' \
  --tests '*DialogueTurnScreenshotTest*' \
  --tests '*SessionFlowScreenshotTest*' \
  --tests '*SummaryScreenshotTest*' \
  -Proborazzi.record
```

Expected: `BUILD SUCCESSFUL` and all of these dark artifacts exist under `android/app/build/outputs/roborazzi/`:

```text
onboarding_level_dark.png
onboarding_topic_dark.png
home_dark_newsession.png
topic_select_dark.png
records_dark_expression.png
settings_dark_guest.png
settings_dark_member.png
settings_confirm_delete_dark.png
home_dark_reminder_banner.png
reminder_optin_dark.png
reminder_priming_dark.png
limit_dark.png
generating_quiz_dark.png
generating_ready_dark.png
session_opponent_dark.png
session_learner_dark.png
session_recording_dark.png
flow_feedback_dark.png
summary_dark.png
summary_full_dark.png
```

Open the prototype, switch it to dark mode, and compare each capture by semantic role rather than pixel-for-pixel layout: background/card separation, primary/secondary/tertiary text, borders, feedback tint surfaces, mic state colors, saved-card gold, streak orange, and modal scrim opacity. Any mismatch must be corrected only by routing the affected component to an existing token from `Color.kt`.

- [ ] **Step 5: Commit dark visual coverage**

```bash
git add android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/level/LevelQuestionScreenshotTest.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/topic/TopicQuestionScreenshotTest.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreenScreenshotTest.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreenScreenshotTest.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ui/ReminderScreenshotTest.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGeneratingScreenshotTest.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreenshotTest.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryScreenshotTest.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt
git commit -m "test(ui): capture prototype screens in dark mode"
```

### Task 4: Run the Full Regression Gate and Deliver the Audit Evidence

**Files:**

- Modify: none
- Verify: files changed in Tasks 1-3
- Reference: `docs/agents/android-verification.md`

**Interfaces:**

- Consumes: the token contract test, all screen screenshot suites, and the project’s `checkNoRawHexColors` Gradle task.
- Produces: a clean Android verification result and a reviewed dark-mode capture set; no generated `build/` output is added to Git.

- [ ] **Step 1: Run the token and static-contract checks**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*OceThemeColorContractTest*'
scripts/verify-android.sh :app:checkNoRawHexColors
rg -n 'colorScheme\.(surfaceVariant|outline\b|scrim)|Color\.White' android/app/src/main/kotlin --glob '*.kt'
```

Expected: both Gradle commands succeed and the final `rg` command produces no matches.

- [ ] **Step 2: Run the repository Android verification set**

Run:

```bash
scripts/verify-android.sh
```

Expected: `BUILD SUCCESSFUL`. This runs the repository’s configured detekt, Android-test compilation, and debug/release unit-test coverage with the worktree-local Gradle home.

- [ ] **Step 3: Inspect the worktree and preserve only source changes**

Run:

```bash
git status --short
```

Expected: only the Kotlin source and test files listed in Tasks 1-3 are staged or unstaged; no `android/app/build/outputs/roborazzi/*.png`, Gradle cache, or copied `google-services.json` appears in Git status.

- [ ] **Step 4: Confirm no implementation residue remains**

Run:

```bash
git status --short
```

Expected: no generated capture, Gradle cache, or copied Firebase configuration is present. The only repository changes are the three commits created in Tasks 1-3.

## Self-Review

1. **Spec coverage:** Task 1 checks the dark token values embedded by Prototype Flow; Task 2 removes every discovered production use of an unowned default M3 color slot or framework white and binds the reminder fixture scrim to the same token; Task 3 covers the nine prototype screen states plus limit, feedback, topic sheet, reminder sheets/banner, confirmation dialog, and representative mic states; Task 4 enforces the repository verification rules.
2. **Placeholder scan:** The plan contains no deferred implementation markers. The feedback capture refactor includes the full current composition tree, and the screen-family table names the exact content seams and output artifacts.
3. **Type consistency:** `LightColorScheme`/`DarkColorScheme` remain `ColorScheme`; `DarkOceColors` remains `OceColors`; screenshot helpers only add `Boolean dark` and `String name` parameters around their existing stateless composable seams.

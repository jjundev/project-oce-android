# Review Flashcard Text Centering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On the review flashcard screen (`ReviewFlashcard`), center-align the answer/prompt text that currently renders flush-left when it wraps to two lines, while leaving the bordered example-sentence card completely untouched.

**Architecture:** One-file, targeted fix. The user confirmed via a screenshot (revealed state of a Word card: `"have something finished"` / `"어떤 일을 끝마치다"`) that the multi-line English answer text sits flush against the left edge instead of reading as a centered headline, even though the parent `Column` already declares `horizontalAlignment = Alignment.CenterHorizontally` (`ReviewFlashcard.kt:57`). The root cause: when a Compose `Text` wraps to multiple lines, it claims the *full* incoming max-width for line-breaking purposes (not just the width of its widest rendered line), so `Column`-level block-centering has no visible effect — each wrapped line still defaults to `TextAlign.Start` *inside that full-width box*. Short single-line texts don't hit this bug because a single line's box collapses to its own content width, which the `Column` genuinely centers as a block — this is exactly why the Korean translation ("어떤 일을 끝마치다") already looks centered in the same screenshot while the English answer does not. The fix adds `textAlign = TextAlign.Center` to every top-level text element in `ReviewFlashcard` that can wrap, and makes the English-answer `Row` (which also holds the speak-icon button) `fillMaxWidth()` with a centered `Arrangement.spacedBy` so the text+icon group centers as a unit regardless of how the icon eats into the available width. The bordered example-sentence box (`ReviewFlashcard.kt:93-110`) is explicitly out of scope — the user pointed at it directly ("복습 카드는 수정하지 않고") and it must not change.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Roborazzi + Robolectric (screenshot verification), `scripts/verify-android.sh`.

## Global Constraints

- Scope is `ReviewFlashcard.kt` only. Do not touch `ReviewExpressionQuiz.kt` or `ReviewFlowScreen.kt` — the user's screenshot is the flashcard (flip-card) screen, not the 2-choice expression quiz, and they explicitly want the bordered example-sentence card left alone.
- Do not modify the example-sentence `Card`-like `Column` at `ReviewFlashcard.kt:93-110` (white/surface background, `radius12`, border) or its two `Text` children — this is the "카드 안에 감싸진 텍스트" the user excluded.
- No new spacing/color literals — reuse existing `OceTheme` tokens already present in the file (`OceTheme.spacing.md`, etc.). `TextAlign` is already imported (`ReviewFlashcard.kt:25`) — no new import needed.
- Verify with `scripts/verify-android.sh` (never bare `./gradlew` — the worktree needs the shared-cache/`google-services.json` workarounds; see `docs/agents/android-verification.md`).
- Screenshot regeneration requires the `-Proborazzi.record` Gradle property (`android/app/build.gradle.kts:67-71`) — without it, `captureRoboImage` is a no-op and no PNG is written.

---

## File Structure

- **Modify** `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewFlashcard.kt`:
  - Lines 60-65 (not-revealed branch, helper text) — add `textAlign = TextAlign.Center`.
  - Lines 74-84 (revealed branch, English-answer `Row` + `Text`) — make the `Row` `fillMaxWidth()` with a center-biased `Arrangement.spacedBy`, add `textAlign = TextAlign.Center` to the `Text`.
  - Lines 86-90 (revealed branch, Korean-translation `Text`) — add `textAlign = TextAlign.Center`.
  - Lines 93-110 (example-sentence card) — **unchanged**, explicitly out of scope.

No new files. No test file changes — verification uses the existing Roborazzi screenshot test (`ReviewFlashcardScreenshotTest.kt`), which already captures all three states needed (`review_flashcard_front_light`, `review_flashcard_back_light`, `review_flashcard_back_dark`) and therefore exercises every changed `Text` for free; no new test needs to be authored.

---

## Task 1: Center the flashcard's wrap-prone text, leave the example card alone

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewFlashcard.kt:60-90`
- Verify via (no code changes): `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/review/ReviewFlashcardScreenshotTest.kt`

**Interfaces:**
- Consumes: `OceTheme.spacing.md` (existing token, already used at `ReviewFlashcard.kt:76`), `Alignment.CenterHorizontally` (already imported, `ReviewFlashcard.kt:20`), `TextAlign.Center` (already imported, `ReviewFlashcard.kt:25`).
- Produces: no new public API — `ReviewFlashcard`'s rendered output changes only (text alignment); function signature `ReviewFlashcard(card, revealed, onReveal, onGrade, onSpeak, modifier)` is unchanged.

- [ ] **Step 1: Capture the "before" screenshots as a baseline**

Run:
```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests "*ReviewFlashcardScreenshotTest*" -Proborazzi.record
```
Expected: BUILD SUCCESSFUL, and `android/app/build/outputs/roborazzi/review_flashcard_back_light.png` (plus `_front_light` and `_back_dark`) are written.

- [ ] **Step 2: Confirm the baseline reproduces the reported bug**

Read `android/app/build/outputs/roborazzi/review_flashcard_back_light.png`. The sample card in this test is `SavedCard.Word("grasp", "완전히 이해하다", "I finally grasped it.", "드디어 이해했다.")` — `"grasp"` is short and won't wrap at Pixel5 width, so this exact baseline may not visibly reproduce the left-flush bug the user saw with the longer `"have something finished"` phrase. That's expected and fine — Step 2 is a sanity check that the capture pipeline works and the example-sentence card (`"I finally grasped it." / "드디어 이해했다."`) renders as today's starting point, since that box must look pixel-identical after Step 4.

- [ ] **Step 3: Add centering to the wrap-prone text elements**

In `ReviewFlashcard.kt`, replace:
```kotlin
            if (!revealed) {
                Text(
                    text = if (card is SavedCard.Word) "이 뜻의 영어 단어는?" else "이 문장을 영어로?",
                    style = OceTheme.typography.helper,
                    color = OceTheme.colors.textTertiary,
                )
                Spacer(Modifier.size(OceTheme.spacing.md))
                Text(
                    text = korean,
                    style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
                ) {
                    Text(
                        text = english,
                        style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    SpeakButton(onClick = { onSpeak(english) })
                }
                Spacer(Modifier.size(OceTheme.spacing.sm))
                Text(
                    text = korean,
                    style = OceTheme.typography.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
```
with:
```kotlin
            if (!revealed) {
                Text(
                    text = if (card is SavedCard.Word) "이 뜻의 영어 단어는?" else "이 문장을 영어로?",
                    style = OceTheme.typography.helper,
                    color = OceTheme.colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(OceTheme.spacing.md))
                Text(
                    text = korean,
                    style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            } else {
                // fillMaxWidth + 중앙 정렬 Arrangement: 텍스트가 2줄로 줄바꿈되면 Text 가 가용 너비 전체를
                // 차지해 Column 의 블록 중앙정렬이 무력화되므로(짧은 한 줄 텍스트만 실제로 중앙에 옴), Row
                // 자체를 전체 너비로 펼치고 (텍스트+스피커 아이콘) 묶음을 가운데로 모은다.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md, Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = english,
                        style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    SpeakButton(onClick = { onSpeak(english) })
                }
                Spacer(Modifier.size(OceTheme.spacing.sm))
                Text(
                    text = korean,
                    style = OceTheme.typography.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
```

Leave everything from `if (card is SavedCard.Word && card.exampleEnglish.isNotBlank()) {` (line 91 in the original file) through its closing `}` (line 111) byte-for-byte unchanged — that's the example-sentence card the user excluded.

- [ ] **Step 4: Re-run the screenshot test and confirm the fix**

Run:
```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests "*ReviewFlashcardScreenshotTest*" -Proborazzi.record
```
Expected: BUILD SUCCESSFUL. Read all three regenerated PNGs and confirm:
- `review_flashcard_front_light.png` — the helper text and the Korean prompt both sit centered above the (unchanged) reveal button.
- `review_flashcard_back_light.png` / `review_flashcard_back_dark.png` — `"grasp"` + speaker icon and `"완전히 이해하다"` are centered as a block; the example-sentence card (`"I finally grasped it."` / `"드디어 이해했다."`, white/surface box with border) is pixel-identical to Step 2's baseline — same position, same left-aligned text inside it.

Because the bundled test fixture (`"grasp"`) is short and won't wrap, also temporarily edit `ReviewFlashcardScreenshotTest.kt:28` to reproduce the user's exact reported case:
```kotlin
    private val word = SavedCard.Word("have something finished", "어떤 일을 끝마치다", "Don't worry, I will have the report finished by tomorrow morning for you.", "걱정 마세요, 내일 아침까지 보고서를 다 끝내 놓을게요.")
```
Re-run:
```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests "*ReviewFlashcardScreenshotTest.back_light*" -Proborazzi.record
```
```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests "*ReviewFlashcardScreenshotTest.back_light*" -Proborazzi.record
```
Read `android/app/build/outputs/roborazzi/review_flashcard_back_light.png` and confirm `"have something finished"` now wraps to two centered lines (not flush-left) and the example-sentence card underneath still reads left-aligned inside its box. Then revert `ReviewFlashcardScreenshotTest.kt` back to the original `"grasp"` fixture (`git checkout -- android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/review/ReviewFlashcardScreenshotTest.kt`) — this file must not be part of the committed diff.

- [ ] **Step 5: Run the full verification suite**

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL — detekt, androidTest compile, and both unit-test variants pass (no regressions in unrelated screens; this change only touches `ReviewFlashcard.kt`).

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewFlashcard.kt
git commit -m "fix(review): center flashcard prompt/answer text, keep example card left-aligned"
```

---

## Self-Review

- **Spec coverage:** The user's requirement — "복습 퀴즈 화면에서, 카드 안에 감싸진 텍스트를 제외하고 나머지는 중앙정렬" — scoped down via their own screenshot + follow-up ("이런 화면에서 맨 위의 'have something finished'와 같은 텍스트를 말하는거야. 복습 카드는 수정하지 않고") to exactly: center the flashcard's headline text (front-side prompt, back-side answer + translation), leave the bordered example-sentence card untouched. Task 1 covers all four wrap-prone `Text` elements in `ReviewFlashcard.kt` and explicitly protects the excluded card.
- **Placeholder scan:** No TBD/TODO, no "add appropriate alignment" — Step 3 shows the complete before/after code including the exact `Arrangement.spacedBy(OceTheme.spacing.md, Alignment.CenterHorizontally)` overload used.
- **Type consistency:** `Arrangement.spacedBy(space: Dp, alignment: Alignment.Horizontal)` is a valid `androidx.compose.foundation.layout.Arrangement` overload; `Row`'s `Modifier.fillMaxWidth()` requires the already-imported `androidx.compose.foundation.layout.fillMaxWidth` (present at `ReviewFlashcard.kt:10`). No other call site references `ReviewFlashcard`'s internal `Row`/`Text` structure, so no signature drift elsewhere in the file.

**Automatic Plan Review skipped** — this plan has exactly one task (per the writing-plans skill's explicit skip condition for single-task plans).

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-16-review-flashcard-text-centering.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**

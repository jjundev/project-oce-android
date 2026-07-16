# Dialogue Chat Bottom Padding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the dialogue-learning chat thread breathing room at the bottom of the screen — as the conversation progresses and the message list scrolls to the latest turn, the last chat bubble currently sits flush (8dp) against the bottom edge instead of the prototype's intended 16dp gap.

**Architecture:** One-line fix. `DialogueTurnContent`'s `LazyColumn` uses a single symmetric `vertical` content-padding value (`OceTheme.spacing.sm` = 8dp) for both top and bottom. The prototype's ground-truth thread container (`prototype/Prototype Flow (standalone).html`, `threadRef` div, `data-screen-label="Dialogue"`) specifies `padding: 8px 18px 16px` — i.e. **top 8px, horizontal 18px, bottom 16px** — asymmetric top/bottom. The fix splits the single `vertical` param into explicit `top`/`bottom` params so bottom matches the prototype's 16dp (`OceTheme.spacing.lg`) while top and horizontal stay as they are today.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Roborazzi + Robolectric (screenshot verification), `scripts/verify-android.sh`.

## Global Constraints

- Ground truth is `prototype/Prototype Flow (standalone).html`. The dialogue thread container's exact style there is `padding:8px 18px 16px; ... gap:10px` — copy the bottom value (16px → 16dp) verbatim; do not invent a new number.
- Colors/dimensions via `OceTheme` tokens — no raw `.dp` literal for a themed spacing value that already has a token (`OceTheme.spacing.lg` = 16dp exists and must be reused, not hardcoded as `16.dp`).
- Verify with `scripts/verify-android.sh` (never bare `./gradlew` — the worktree needs the shared-cache/`google-services.json` workarounds; see `docs/agents/android-verification.md`).
- Do not touch `SessionInputPanel`, `MicDock`, or any other file — the input dock's own spacing is untouched and already correct; only the message thread's `contentPadding` changes.

---

## File Structure

- **Modify** `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt:195` — split the `LazyColumn`'s `contentPadding` `vertical` param into explicit `top`/`bottom`, with `bottom = OceTheme.spacing.lg` (Task 1).

No new files. No test file changes — verification uses the existing Roborazzi screenshot test (`DialogueTurnScreenshotTest.kt`), which already renders every phase (`session_opponent_light/dark`, `session_learner_light/dark`, `session_skeleton_light`, `session_recording_light/dark`) and therefore exercises the changed `contentPadding` for free; no new test needs to be authored.

---

## Task 1: Widen the chat thread's bottom content padding to match the prototype

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt:192-197`
- Verify via (no code changes): `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreenshotTest.kt`

**Interfaces:**
- Consumes: `OceTheme.spacing.sm` (8dp, existing top value, unchanged), `OceTheme.spacing.lg` (16dp, existing token defined in `OceSpacing.kt:16`, already imported/used elsewhere in this same file at line 477).
- Produces: no new public API — `DialogueTurnContent`'s rendered output changes (last message bubble sits 16dp instead of 8dp from the bottom of its scroll container).

- [ ] **Step 1: Capture the "before" screenshot as a baseline**

Run:
```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests "*DialogueTurnScreenshotTest*" -Proborazzi.record
```
Expected: BUILD SUCCESSFUL, and `android/app/build/outputs/roborazzi/session_opponent_light.png` is written/overwritten (this is the OpponentTurn-phase capture — no input dock is visible in this phase, so the LazyColumn's own bottom `contentPadding` is the *only* thing separating the last bubble from the screen edge, making it the clearest before/after signal).

- [ ] **Step 2: Confirm the baseline shows the tight (8dp) gap**

Read `android/app/build/outputs/roborazzi/session_opponent_light.png` and visually confirm the last chat bubble's bottom edge sits close to the image's bottom edge (roughly an 8dp-equivalent gap at the Pixel5 density this test renders at — noticeably tighter than the ~18dp horizontal margins visible on the sides of the same bubble). This is the "red" baseline the fix in Step 3 must change.

- [ ] **Step 3: Split `contentPadding` into explicit top/bottom**

In `DialogueTurnScreen.kt`, replace:
```kotlin
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = OceTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
```
with:
```kotlin
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                // 프로토타입 thread 정합(padding:8px 18px 16px) — 상단 8dp 는 기존 유지, 하단만 16dp 로 넓혀
                // 마지막 말풍선이 화면 하단에 바짝 붙지 않게 한다.
                contentPadding =
                    PaddingValues(
                        horizontal = 18.dp,
                        top = OceTheme.spacing.sm,
                        bottom = OceTheme.spacing.lg,
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
```

- [ ] **Step 4: Re-run the screenshot test and confirm the gap widened**

Run:
```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests "*DialogueTurnScreenshotTest*" -Proborazzi.record
```
Expected: BUILD SUCCESSFUL. Read `android/app/build/outputs/roborazzi/session_opponent_light.png` again and confirm the bottom gap below the last bubble is now visibly larger than in Step 2 (matching the ~16dp bottom vs ~8dp top asymmetry — the bottom gap should now read as roughly double the top gap above the first bubble). Also spot-check `session_learner_light.png` (LearnerTurn phase, dock visible) to confirm the extra 8dp of space now appears as a small additional gap between the last bubble and the top of the input dock panel, with no clipping or overlap.

- [ ] **Step 5: Run the full verification suite**

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL — detekt, androidTest compile, and both unit-test variants pass (no regressions in unrelated screens; this change only touches one `contentPadding` call).

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt
git commit -m "fix(dialogue): widen chat thread bottom padding to match prototype (8px→16px)"
```

---

## Self-Review

- **Spec coverage:** The user's one requirement — "add bottom padding so messages don't run to the very bottom of the screen" — is fully covered by Task 1's single change, grounded in the prototype's own `padding:8px 18px 16px` value (decoded directly from `prototype/Prototype Flow (standalone).html`, `threadRef` div). No other requirement was stated.
- **Placeholder scan:** No TBD/TODO, no "add appropriate padding" — the exact before/after code is shown in Step 3, and the exact token (`OceTheme.spacing.lg`) is named and verified to already exist (`OceSpacing.kt:16`).
- **Type consistency:** `PaddingValues(horizontal =, top =, bottom =)` is a valid Compose Foundation overload (distinct from `PaddingValues(horizontal =, vertical =)`); no other call site references this `contentPadding` value, so no signature drift elsewhere in the file.

**Automatic Plan Review skipped** — this plan has exactly one task (per the writing-plans skill's explicit skip condition for single-task plans).

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-16-dialogue-chat-bottom-padding.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**

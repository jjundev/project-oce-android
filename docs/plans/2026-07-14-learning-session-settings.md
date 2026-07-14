# Learning Session Settings — Level 5-Tier + Length Slider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand the 학습(홈) tab session settings from 3 difficulty levels + 2 length presets to a 5-tier level slider (with per-tier description) and an even 6–20 turn length slider, across client + backend.

**Architecture:** Introduce a single source of truth for the 5 levels on both sides (`SessionLevel` in Kotlin, `LEVELS` in TypeScript). Replace the two `OneClickSegmentedControl`s in the Home inline settings panel with two sliders (a new `SliderMode.Stepped` variant of the existing `OneClickSlider`). Widen the backend validators, difficulty-band prompt, XP tables, Firestore rule, and wait-quiz tier mapping to accept the two new tokens. Deploy backend first, then ship the client.

**Tech Stack:** Kotlin / Jetpack Compose (Android app), TypeScript / Firebase Cloud Functions (`functions/`), Jest (backend tests), JUnit + Robolectric + Roborazzi (Android tests).

## Global Constraints

- **Level tokens (both sides), order easiest→hardest:** `starter`, `easy`, `normal`, `hard`, `expert`. Existing `easy`/`normal`/`hard` tokens are unchanged — no persisted-data migration.
- **Level Korean labels (UI):** starter=`매우 쉬움`, easy=`쉬움`, normal=`중간`, hard=`어려움`, expert=`매우 어려움`. **No CEFR code is ever shown in the UI.**
- **Level CEFR bands (internal, prompt only):** starter=A1, easy=A2, normal=B1, hard=B2, expert=C1.
- **Level XP:** starter=5, easy=10, normal=20, hard=35, expert=55.
- **Level per-tier description (UI, Korean):** starter=`단어와 짧은 문장부터 천천히 시작해요`, easy=`쉬운 일상 표현으로 편하게 대화해요`, normal=`일상 대화를 자연스럽게 이어가요`, hard=`조금 더 길고 깊은 대화까지 해봐요`, expert=`빠르고 풍부한 표현으로 도전해요`.
- **Length:** integer, even only, `6..20`, step `2`, default `10`. (First session is coerced server-side to `easy`/`5` and bypasses length validation — do NOT change that.)
- **Unknown level token → fall back to `normal`** everywhere a token is resolved.
- **Deploy order:** backend (Tasks 3–7) must be deployed before the client (Tasks 8–14) ships to the Play Store. Client-first would 400 on generation.
- **Out of scope (confirmed):** the onboarding level picker (`LevelQuestionScreen.kt`) stays 3 cards — untouched by this plan.
- Android verification runs via `bash scripts/verify-android.sh` (worktree gradle safety per CLAUDE.md). Backend tests run via `cd functions && npx jest <path>`.

---

## File Structure

**New files:**
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/session/SessionLevel.kt` — client SoT enum (token, labelKo, descKo, cefr, xp) + `fromToken`.
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/session/SessionLevelTest.kt` — SoT unit test.
- `functions/src/config/levels.ts` — backend SoT (token list, CEFR band map).
- `functions/test/levels.test.ts` — backend SoT unit test.

**Modified files (client):**
- `ui/component/OneClickSlider.kt` — add `SliderMode.Stepped` + pure `steppedSliderSpec` helper.
- `ui/component/OneClickSliderSpecTest.kt` (new test) — pure spec helper test.
- `feature/home/HomeViewModel.kt` — `DEFAULT_LENGTH=10`, `setLength` clamp to even 6..20, `FALLBACK_LEVEL` via SoT.
- `feature/home/HomeUiState.kt` — `length` default `10`.
- `feature/home/HomeSessionGraph.kt` — `DEFAULT_LENGTH=10`.
- `feature/home/HomeScreen.kt` — remove `LEVEL_OPTIONS`/`LENGTH_OPTIONS`/`levelLabel`; rewrite `SettingsInline` to two sliders + description; hero subtitle via `SessionLevel`.
- `feature/home/HomeAnalytics.kt` — KDoc contract update.
- `feature/session/turn/GeneratedDialogueSession.kt` — `dialogueLevelLabel` via `SessionLevel`.
- `feature/session/turn/DialogueHeader.kt` — progress dots → "n / N" when >8 turns.
- `feature/session/dialogue/DialogueGenerationViewModel.kt` — `FIRST_SESSION_TIER` via `SessionLevel`.
- `feature/gamification/GamificationTime.kt` — client XP map + 2 tokens.
- `feature/session/summary/CompletionLedger.kt` — distinguish `PERMISSION_DENIED`.
- `feature/session/dialogue/quiz/QuizBankRepository.kt` — `starter→easy`, `expert→hard` tier mapping (via pure `mapTierKey`).

**Modified files (backend):**
- `functions/src/llm/dialogue.ts` — `VALID_LEVELS` (5), `VALID_LENGTHS`→range check.
- `functions/src/types/protocol.ts` — `level` union (5).
- `functions/src/llm/feedback.ts` — `VALID_LEVELS` (5).
- `functions/src/providers/gemini.ts` — 5 DIFFICULTY BANDS + `DIALOGUE_PROMPT_VERSION` bump.
- `functions/src/gamification/aggregate.ts` — `XP_BY_DIFFICULTY` + `isDifficulty` (5).
- `firestore.rules` — `point_ledger.difficulty` enum (5).
- `functions/test/dialogue-handler.test.ts` — fixtures `length:5`→`10`.

---

### Task 1: SessionLevel client SoT

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/session/SessionLevel.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/session/SessionLevelTest.kt`

**Interfaces:**
- Produces: `enum class SessionLevel(val token: String, val labelKo: String, val descKo: String, val cefr: String, val xp: Int)` with entries `STARTER, EASY, NORMAL, HARD, EXPERT` (declared easiest→hardest); companion `fun fromToken(token: String?): SessionLevel` (unknown/null → `NORMAL`).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jjundev.oneclickeng.core.session

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionLevelTest {
    @Test
    fun `entries are ordered easiest to hardest`() {
        assertEquals(
            listOf("starter", "easy", "normal", "hard", "expert"),
            SessionLevel.entries.map { it.token },
        )
    }

    @Test
    fun `fromToken resolves known tokens`() {
        assertEquals(SessionLevel.STARTER, SessionLevel.fromToken("starter"))
        assertEquals(SessionLevel.EXPERT, SessionLevel.fromToken("expert"))
        assertEquals(SessionLevel.NORMAL, SessionLevel.fromToken("normal"))
    }

    @Test
    fun `fromToken falls back to NORMAL for unknown or null`() {
        assertEquals(SessionLevel.NORMAL, SessionLevel.fromToken("A2"))
        assertEquals(SessionLevel.NORMAL, SessionLevel.fromToken(null))
        assertEquals(SessionLevel.NORMAL, SessionLevel.fromToken(""))
    }

    @Test
    fun `labels descriptions and xp match the ratified spec`() {
        assertEquals("중간", SessionLevel.NORMAL.labelKo)
        assertEquals("매우 어려움", SessionLevel.EXPERT.labelKo)
        assertEquals("단어와 짧은 문장부터 천천히 시작해요", SessionLevel.STARTER.descKo)
        assertEquals(5, SessionLevel.STARTER.xp)
        assertEquals(55, SessionLevel.EXPERT.xp)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `bash scripts/verify-android.sh` (or, if per-test filtering is wired, the `SessionLevelTest` class)
Expected: FAIL — `SessionLevel` unresolved (does not exist yet).

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.jjundev.oneclickeng.core.session

/**
 * 세션 난이도 단일 소스(SoT). 흩어져 있던 레벨 정의(HomeScreen/HomeSessionGraph/GeneratedDialogueSession
 * 등)를 이 enum 하나로 모은다. [token] 은 백엔드/DB(`users/{uid}.level`, `point_ledger.difficulty`)
 * 저장 값이자 서버 계약(functions/src/config/levels.ts 와 1:1). entries 는 쉬움→어려움 순.
 *
 * [cefr] 은 프롬프트 난이도 밴드용 내부 값으로 UI 에는 절대 노출하지 않는다. [labelKo]/[descKo] 가 화면 표기.
 */
enum class SessionLevel(
    val token: String,
    val labelKo: String,
    val descKo: String,
    val cefr: String,
    val xp: Int,
) {
    STARTER("starter", "매우 쉬움", "단어와 짧은 문장부터 천천히 시작해요", "A1", 5),
    EASY("easy", "쉬움", "쉬운 일상 표현으로 편하게 대화해요", "A2", 10),
    NORMAL("normal", "중간", "일상 대화를 자연스럽게 이어가요", "B1", 20),
    HARD("hard", "어려움", "조금 더 길고 깊은 대화까지 해봐요", "B2", 35),
    EXPERT("expert", "매우 어려움", "빠르고 풍부한 표현으로 도전해요", "C1", 55),
    ;

    companion object {
        /** 저장 토큰 → SessionLevel. 미지/누락 토큰은 NORMAL 로 폴백(구버전 값 안전). */
        fun fromToken(token: String?): SessionLevel =
            entries.firstOrNull { it.token == token } ?: NORMAL
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `bash scripts/verify-android.sh`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/core/session/SessionLevel.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/core/session/SessionLevelTest.kt
git commit -m "feat(session): add SessionLevel single source of truth (5 tiers)"
```

---

### Task 2: OneClickSlider Stepped variant

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickSlider.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/OneClickSliderSpecTest.kt`

**Interfaces:**
- Consumes: existing `OneClickSlider(value, onValueChange, mode, modifier, onValueChangeFinished, showValueLabel)` and `sealed interface SliderMode`.
- Produces:
  - `data class SliderMode.Stepped(val range: IntRange, val step: Int = 1, val labelFormatter: (Int) -> String) : SliderMode`
  - `internal data class SteppedSpec(val valueRange: ClosedFloatingPointRange<Float>, val steps: Int)`
  - `internal fun steppedSliderSpec(range: IntRange, step: Int): SteppedSpec`
  - Callers pass `value` = the actual integer (as Float) within `range`; `onValueChange` emits the snapped integer as Float.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jjundev.oneclickeng.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class OneClickSliderSpecTest {
    @Test
    fun `even 6 to 20 step 2 yields 8 stops so 6 intermediate steps`() {
        val spec = steppedSliderSpec(6..20, 2)
        assertEquals(6f, spec.valueRange.start)
        assertEquals(20f, spec.valueRange.endInclusive)
        assertEquals(6, spec.steps) // stops = steps + 2 = 8
    }

    @Test
    fun `level 0 to 4 step 1 yields 5 stops so 3 intermediate steps`() {
        val spec = steppedSliderSpec(0..4, 1)
        assertEquals(0f, spec.valueRange.start)
        assertEquals(4f, spec.valueRange.endInclusive)
        assertEquals(3, spec.steps)
    }

    @Test
    fun `single stop range never yields negative steps`() {
        assertEquals(0, steppedSliderSpec(10..10, 2).steps)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `bash scripts/verify-android.sh`
Expected: FAIL — `steppedSliderSpec` unresolved.

- [ ] **Step 3: Write minimal implementation**

In `OneClickSlider.kt`, add to the `SliderMode` sealed interface (after the `Discrete` data class, before the closing brace):

```kotlin
    /**
     * 정수 스냅값 — 임의 정수 구간을 [step] 간격으로 스냅한다(레벨 인덱스 0..4, 길이 6..20/step2).
     * [value] 는 stop 인덱스가 아니라 실제 정수(Float)이고, [labelFormatter] 가 그 정수의 표시 문자열을
     * 만든다. 하단 값 라벨은 [showValueLabel] 로 게이팅한다(Discrete 와 달리 무조건 렌더하지 않는다).
     */
    data class Stepped(
        val range: IntRange,
        val step: Int = 1,
        val labelFormatter: (Int) -> String,
    ) : SliderMode
```

Add the pure helper (top-level, near the bottom of the file before the previews):

```kotlin
/** M3 Slider 스펙 파생: 정수 [range]/[step] → (valueRange, steps). stops = steps + 2 규칙. */
internal data class SteppedSpec(
    val valueRange: ClosedFloatingPointRange<Float>,
    val steps: Int,
)

internal fun steppedSliderSpec(
    range: IntRange,
    step: Int,
): SteppedSpec {
    val stops = ((range.last - range.first) / step) + 1
    return SteppedSpec(
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (stops - 2).coerceAtLeast(0),
    )
}
```

Extend the three `when (mode)` blocks in `OneClickSlider`. `valueRange`:

```kotlin
    val valueRange =
        when (mode) {
            is SliderMode.Continuous -> mode.range
            is SliderMode.Discrete -> 0f..(mode.labels.lastIndex.coerceAtLeast(0)).toFloat()
            is SliderMode.Stepped -> steppedSliderSpec(mode.range, mode.step).valueRange
        }
```

`steps`:

```kotlin
    val steps =
        when (mode) {
            is SliderMode.Continuous -> 0
            is SliderMode.Discrete -> (mode.labels.size - 2).coerceAtLeast(0)
            is SliderMode.Stepped -> steppedSliderSpec(mode.range, mode.step).steps
        }
```

`state` (semantics announce):

```kotlin
    val state =
        when (mode) {
            is SliderMode.Continuous -> "${"%.1f".format(value)}x"
            is SliderMode.Discrete -> mode.labels.getOrNull(value.roundToInt())?.let { "${it.en} / ${it.ko}" } ?: ""
            is SliderMode.Stepped -> mode.labelFormatter(value.roundToInt())
        }
```

And the bottom-label `when (mode)` block (after the `Slider(...)` call) — add a `Stepped` branch that respects `showValueLabel`:

```kotlin
            is SliderMode.Stepped ->
                if (showValueLabel) {
                    Text(
                        text = mode.labelFormatter(value.roundToInt()),
                        style = OceTheme.typography.body,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `bash scripts/verify-android.sh`
Expected: PASS (and Kotlin `when` exhaustiveness now compiles across all three `SliderMode` branches).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickSlider.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/OneClickSliderSpecTest.kt
git commit -m "feat(ui): add SliderMode.Stepped integer-snap variant to OneClickSlider"
```

---

### Task 3: Backend level/length validators + SoT

**Files:**
- Create: `functions/src/config/levels.ts`
- Test: `functions/test/levels.test.ts`
- Modify: `functions/src/llm/dialogue.ts:33-34,57-61`
- Modify: `functions/src/types/protocol.ts:31`
- Modify: `functions/test/dialogue-handler.test.ts` (fixtures `length:5`→`10`)

**Interfaces:**
- Produces: `LEVEL_TOKENS: readonly ["starter","easy","normal","hard","expert"]`, `CEFR_BAND: Record<LevelToken,string>`, `type LevelToken`, `isEven6to20(n: number): boolean`.
- Consumes: existing `parseDialoguePayload` and `DialoguePayload`.

- [ ] **Step 1: Write the failing test**

`functions/test/levels.test.ts`:

```ts
import { LEVEL_TOKENS, CEFR_BAND, isEven6to20 } from "../src/config/levels";

describe("levels SoT", () => {
  it("lists 5 tokens easiest→hardest", () => {
    expect([...LEVEL_TOKENS]).toEqual(["starter", "easy", "normal", "hard", "expert"]);
  });
  it("maps every token to a CEFR band", () => {
    expect(CEFR_BAND).toEqual({
      starter: "A1", easy: "A2", normal: "B1", hard: "B2", expert: "C1",
    });
  });
  it("accepts even 6..20 only", () => {
    expect(isEven6to20(6)).toBe(true);
    expect(isEven6to20(20)).toBe(true);
    expect(isEven6to20(10)).toBe(true);
    expect(isEven6to20(5)).toBe(false);   // odd
    expect(isEven6to20(7)).toBe(false);   // odd
    expect(isEven6to20(4)).toBe(false);   // below floor
    expect(isEven6to20(22)).toBe(false);  // above ceiling
    expect(isEven6to20(10.5)).toBe(false);
  });
});
```

Also add to `functions/test/dialogue-handler.test.ts` (new cases alongside existing parse tests — search the file for an existing `parseDialoguePayload` describe block; if none, add one):

```ts
import { parseDialoguePayload, InvalidDialoguePayloadError } from "../src/llm/dialogue";

describe("parseDialoguePayload 5-tier + even length", () => {
  it("accepts the two new level tokens", () => {
    expect(parseDialoguePayload({ level: "starter", topic: "t", length: 6 }).level).toBe("starter");
    expect(parseDialoguePayload({ level: "expert", topic: "t", length: 20 }).level).toBe("expert");
  });
  it("rejects odd or out-of-range length for non-first sessions", () => {
    expect(() => parseDialoguePayload({ level: "normal", topic: "t", length: 5 }))
      .toThrow(InvalidDialoguePayloadError);
    expect(() => parseDialoguePayload({ level: "normal", topic: "t", length: 22 }))
      .toThrow(InvalidDialoguePayloadError);
  });
  it("still coerces first session to easy/5 regardless of input", () => {
    const p = parseDialoguePayload({ level: "expert", topic: "t", length: 20, firstSession: true });
    expect(p).toEqual({ level: "easy", topic: "t", length: 5, firstSession: true });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd functions && npx jest test/levels.test.ts test/dialogue-handler.test.ts`
Expected: FAIL — `../src/config/levels` not found; new dialogue cases fail (odd `length:5` currently accepted, `starter`/`expert` currently rejected).

- [ ] **Step 3: Write minimal implementation**

`functions/src/config/levels.ts`:

```ts
/**
 * Session-level single source of truth (server). Token strings are the wire/DB contract, mirrored
 * 1:1 by the client enum (SessionLevel.kt). [CEFR_BAND] drives the dialogue difficulty-band prompt
 * (gemini.ts) and is NEVER surfaced to the client. Order is easiest→hardest.
 */
export const LEVEL_TOKENS = ["starter", "easy", "normal", "hard", "expert"] as const;
export type LevelToken = (typeof LEVEL_TOKENS)[number];

export const CEFR_BAND: Record<LevelToken, string> = {
  starter: "A1",
  easy: "A2",
  normal: "B1",
  hard: "B2",
  expert: "C1",
};

/** Non-first-session length gate: even integers in [6, 20]. */
export function isEven6to20(n: number): boolean {
  return Number.isInteger(n) && n % 2 === 0 && n >= 6 && n <= 20;
}
```

`functions/src/llm/dialogue.ts` — replace lines 33-34:

```ts
const VALID_LEVELS = new Set<string>(LEVEL_TOKENS);
```

Add the import near the top (with the other imports):

```ts
import { LEVEL_TOKENS, isEven6to20 } from "../config/levels";
```

Replace the length branch (lines 56 and 60-62) so validation uses the range gate:

```ts
  const level = typeof p.level === "string" ? p.level : "";
  const length = typeof p.length === "number" ? p.length : NaN;
  if (!VALID_LEVELS.has(level)) {
    throw new InvalidDialoguePayloadError(`invalid level: ${String(p.level)}`);
  }
  if (!isEven6to20(length)) {
    throw new InvalidDialoguePayloadError(`invalid length: ${String(p.length)}`);
  }
```

(Delete the old `const VALID_LENGTHS = new Set<number>([5, 10]);` line and the old `!VALID_LENGTHS.has(length)` check.)

`functions/src/types/protocol.ts` — replace line 31:

```ts
  level: "starter" | "easy" | "normal" | "hard" | "expert";
```

In `functions/test/dialogue-handler.test.ts`, change every existing fixture that sends `length: 5` with `firstSession: false` (or no `firstSession`) to `length: 10`. These are the four sites at (approximately) lines 133 (400 missing-idempotencyKey), 149 (429 daily-limit), 196 (refund on failure), and 216 (dedup no-refund). The stream-success case (~159-166) already uses `length: 10` — leave it. Leave any `firstSession: true` fixtures alone.

Also in `functions/test/dialogue-parser.test.ts`, the pre-existing "rejects an out-of-range level" case uses `level: "expert"` (now a VALID token) — its throw currently comes from the level check, but after this task `expert` is valid, so change that token to a genuinely-invalid one (e.g. `"legendary"`) so it keeps exercising the level-rejection path:

```ts
// was: { level: "expert", topic: "t", length: 5 }  (would now throw only on odd length)
    parseDialoguePayload({ level: "legendary", topic: "t", length: 6 }); // expect throw on invalid level
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd functions && npx jest test/levels.test.ts test/dialogue-handler.test.ts test/dialogue-parser.test.ts`
Expected: PASS (all three).

- [ ] **Step 5: Commit**

```bash
git add functions/src/config/levels.ts functions/test/levels.test.ts \
        functions/src/llm/dialogue.ts functions/src/types/protocol.ts \
        functions/test/dialogue-handler.test.ts
git commit -m "feat(functions): 5-tier levels + even 6-20 length validation"
```

---

### Task 4: Feedback level validator

**Files:**
- Modify: `functions/src/llm/feedback.ts:33`
- Test: `functions/test/feedback-parser.test.ts`

**Interfaces:**
- Consumes: `LEVEL_TOKENS` (Task 3), existing `parseFeedbackPayload`.

- [ ] **Step 1: Write the failing test**

Add to `functions/test/feedback-parser.test.ts`:

```ts
import { parseFeedbackPayload } from "../src/llm/feedback";

describe("parseFeedbackPayload 5-tier level", () => {
  const base = { koreanPrompt: "안녕", userEnglish: "hi", referenceEnglish: "" };
  it("passes through the two new tokens", () => {
    expect(parseFeedbackPayload({ ...base, level: "starter" }).level).toBe("starter");
    expect(parseFeedbackPayload({ ...base, level: "expert" }).level).toBe("expert");
  });
  it("still defaults unknown level to normal", () => {
    expect(parseFeedbackPayload({ ...base, level: "A2" }).level).toBe("normal");
  });
});
```

Then FIX the pre-existing out-of-range test in the same file. Search `functions/test/feedback-parser.test.ts` for the case that asserts an out-of-range level defaults to `normal` (it currently uses `level: "expert"` — now a VALID token). Change its input token to a genuinely-invalid one so the assertion still holds:

```ts
// was: level: "expert"  →  change to a token that is not one of the 5 valid tiers
    ...base, level: "legendary",   // still expected to default to "normal"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd functions && npx jest test/feedback-parser.test.ts`
Expected: FAIL — the new `starter`/`expert` cases fail (currently fall back to `normal`); the edited out-of-range case now guards `legendary`.

- [ ] **Step 3: Write minimal implementation**

`functions/src/llm/feedback.ts` — replace line 33:

```ts
const VALID_LEVELS = new Set<string>(LEVEL_TOKENS);
```

Add the import near the top:

```ts
import { LEVEL_TOKENS } from "../config/levels";
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd functions && npx jest test/feedback-parser.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add functions/src/llm/feedback.ts functions/test/feedback-parser.test.ts
git commit -m "feat(functions): accept 5-tier level tokens in feedback payload"
```

---

### Task 5: Dialogue difficulty-band prompt (5 bands)

**Files:**
- Modify: `functions/src/providers/gemini.ts:708,723-724`

**Interfaces:** none new (prompt string + version constant).

- [ ] **Step 1: Update the difficulty-band text**

Replace the DIFFICULTY BANDS sentence (lines 723-724) with the 5-band mapping:

```ts
  "DIFFICULTY BANDS: starter = A1 (very short, ~3-5 word lines, most common words only), " +
  "easy = A2 (short, high-frequency), normal = B1, hard = B2, expert = C1 (rich, idiomatic, " +
  "faster register). Obey the requested level's vocabulary/grammar/sentence-length strictly.\n" +
```

- [ ] **Step 2: Bump the prompt version (invalidates the cache key)**

Replace line 708:

```ts
export const DIALOGUE_PROMPT_VERSION = "2026-07-14";
```

- [ ] **Step 3: Build to verify the prompt constants compile**

Run: `cd functions && npm run build`
Expected: PASS (no type errors).

- [ ] **Step 4: Commit**

```bash
git add functions/src/providers/gemini.ts
git commit -m "feat(functions): 5-band difficulty prompt + prompt version bump"
```

---

### Task 6: XP table + difficulty guard (aggregation)

**Files:**
- Modify: `functions/src/gamification/aggregate.ts:18,24`
- Test: `functions/test/aggregate.test.ts`

**Interfaces:**
- Produces: extended `XP_BY_DIFFICULTY` (5 keys) and `isDifficulty` (5 tokens); `Difficulty` type auto-derives from the map.

- [ ] **Step 1: Write the failing test**

Add to `functions/test/aggregate.test.ts`:

```ts
import { XP_BY_DIFFICULTY, isDifficulty } from "../src/gamification/aggregate";

describe("XP table 5 tiers", () => {
  it("awards the ratified XP per tier", () => {
    expect(XP_BY_DIFFICULTY).toEqual({ starter: 5, easy: 10, normal: 20, hard: 35, expert: 55 });
  });
  it("recognizes all 5 tokens as difficulty", () => {
    for (const t of ["starter", "easy", "normal", "hard", "expert"]) {
      expect(isDifficulty(t)).toBe(true);
    }
    expect(isDifficulty("legendary")).toBe(false);
  });
});
```

Then FIX the pre-existing exact-equality test in the same file. Search `functions/test/aggregate.test.ts` for the assertion `expect(XP_BY_DIFFICULTY).toEqual({ easy: 10, normal: 20, hard: 35 })` and update it to the 5-key map:

```ts
    expect(XP_BY_DIFFICULTY).toEqual({ starter: 5, easy: 10, normal: 20, hard: 35, expert: 55 });
```

(This is the same value the new test asserts — leaving the old 3-key `toEqual` would make Step 4 fail.)

- [ ] **Step 2: Run test to verify it fails**

Run: `cd functions && npx jest test/aggregate.test.ts`
Expected: FAIL — map has only 3 keys; `isDifficulty("starter")` returns false.

- [ ] **Step 3: Write minimal implementation**

`functions/src/gamification/aggregate.ts` — replace line 18:

```ts
export const XP_BY_DIFFICULTY = { starter: 5, easy: 10, normal: 20, hard: 35, expert: 55 } as const;
```

Replace the `isDifficulty` body (line 24):

```ts
  return (
    d === "starter" || d === "easy" || d === "normal" || d === "hard" || d === "expert"
  );
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd functions && npx jest test/aggregate.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add functions/src/gamification/aggregate.ts functions/test/aggregate.test.ts
git commit -m "feat(functions): 5-tier XP table + difficulty guard"
```

---

### Task 7: Firestore point_ledger rule (5 tokens)

**Files:**
- Modify: `firestore.rules:25`

**Interfaces:** none.

- [ ] **Step 1: Widen the difficulty enum in the rule**

Replace line 25:

```
                      && request.resource.data.difficulty in ['starter','easy','normal','hard','expert']
```

- [ ] **Step 2: Verify the rules file parses**

Run: `cd functions && npx firebase deploy --only firestore:rules --dry-run` (or, if the CLI/login is unavailable in this environment, visually diff the single-line change and note that deploy validation happens at deploy time).
Expected: no syntax error reported.

- [ ] **Step 3: (If a rules test harness exists) add positive cases**

`firestore-tests/rules.test.ts` exists as a SEPARATE package at the repo root (its own `package.json` + `jest.config.js`, using `@firebase/rules-unit-testing` — a running Firestore emulator is required). Add a `point_ledger` create case there asserting `difficulty: "starter"` and `difficulty: "expert"` are allowed for the owner. Run from that package (NOT `functions/`): `cd firestore-tests && npx jest` (start the Firestore emulator first). If the emulator is unavailable in this environment, note the case was added and defer the run to CI/local emulator.

- [ ] **Step 4: Commit**

```bash
git add firestore.rules
git commit -m "feat(rules): allow 5-tier difficulty in point_ledger create"
```

---

> **Deploy gate:** Tasks 3–7 (backend + rules) deploy to production BEFORE any client task below reaches the Play Store. `cd functions && npm run deploy` and deploy `firestore:rules`.

---

### Task 8: Home level/length model

**Files:**
- Modify: `feature/home/HomeViewModel.kt:60,143-149,183-194`
- Modify: `feature/home/HomeUiState.kt:30`
- Modify: `feature/home/HomeSessionGraph.kt:223`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeViewModelLengthTest.kt` (new)

**Interfaces:**
- Consumes: `SessionLevel` (Task 1).
- Produces: `HomeViewModel.setLength(turns: Int)` now snaps to the nearest even value in `6..20`; `DEFAULT_LENGTH = 10`; constants `MIN_LENGTH=6`, `MAX_LENGTH=20`, `LENGTH_STEP=2`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jjundev.oneclickeng.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelLengthTest {
    @Test
    fun `clampLength snaps to nearest even in 6 to 20`() {
        assertEquals(6, HomeViewModel.clampLength(5))
        assertEquals(6, HomeViewModel.clampLength(4))
        assertEquals(20, HomeViewModel.clampLength(21))
        assertEquals(12, HomeViewModel.clampLength(13)) // 13 → 12 (nearest even, round down on .5 boundary)
        assertEquals(10, HomeViewModel.clampLength(10))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `bash scripts/verify-android.sh`
Expected: FAIL — `clampLength` unresolved.

- [ ] **Step 3: Write minimal implementation**

`HomeUiState.kt` line 30:

```kotlin
    val length: Int = 10,
```

`HomeViewModel.kt` — change `setLength` (lines 147-149) to snap, and add a testable pure helper:

```kotlin
        fun setLength(turns: Int) {
            length.value = clampLength(turns)
        }
```

In the companion (lines 183-194) replace `const val DEFAULT_LENGTH = 5` and add the range constants + helper, and route `FALLBACK_LEVEL` through the SoT:

```kotlin
        companion object {
            const val TAG = "HomeViewModel"
            const val STOP_TIMEOUT_MS = 5_000L
            const val MIN_LENGTH = 6
            const val MAX_LENGTH = 20
            const val LENGTH_STEP = 2
            const val DEFAULT_LENGTH = 10
            val FALLBACK_LEVEL = SessionLevel.NORMAL.token

            const val RECOMMEND_POOL = 5
            const val RECOMMEND_VISIBLE = 4

            /** 임의 정수를 짝수 6..20 로 스냅(슬라이더 밖 입력·구버전 값 방어). */
            fun clampLength(turns: Int): Int {
                val clamped = turns.coerceIn(MIN_LENGTH, MAX_LENGTH)
                return clamped - ((clamped - MIN_LENGTH) % LENGTH_STEP)
            }

            fun Topic.toSelected() = SelectedSituation(topicId = id, labelKo = titleKo, promptSeed = promptSeed)
        }
```

Add the import to `HomeViewModel.kt`:

```kotlin
import com.jjundev.oneclickeng.core.session.SessionLevel
```

(Note: the companion was `private companion object`; make it non-private `companion object` so `clampLength` is testable and `DEFAULT_LENGTH`/`FALLBACK_LEVEL` stay accessible. Update the `readLevel` fallback comment on line 124 to say "defaulting normal".)

`HomeSessionGraph.kt` line 223:

```kotlin
private const val DEFAULT_LENGTH = 10
```

- [ ] **Step 4: Run test to verify it passes**

Run: `bash scripts/verify-android.sh`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeViewModel.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeUiState.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeSessionGraph.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeViewModelLengthTest.kt
git commit -m "feat(home): default length 10, snap to even 6-20, SoT fallback level"
```

---

### Task 9: SettingsInline — level & length sliders

**Files:**
- Modify: `feature/home/HomeScreen.kt:119-129` (remove option lists + `levelLabel`), `:743-810` (`SettingsInline`)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeSettingsSliderTest.kt` (new, Robolectric)

**Interfaces:**
- Consumes: `SessionLevel` (Task 1), `OneClickSlider` + `SliderMode.Stepped` (Task 2), `HomeViewModel` constants (Task 8), existing `onSetLevel: (String) -> Unit` / `onSetLength: (Int) -> Unit`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jjundev.oneclickeng.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class HomeSettingsSliderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `expanded panel shows selected level label and description`() {
        compose.setContent {
            OceTheme {
                SettingsInline(level = "normal", length = 10, onSetLevel = {}, onSetLength = {})
            }
        }
        compose.onNodeWithText("설정 변경").performClick()
        compose.onNodeWithText("중간").assertIsDisplayed()
        compose.onNodeWithText("일상 대화를 자연스럽게 이어가요").assertIsDisplayed()
        compose.onNodeWithText("10턴").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `bash scripts/verify-android.sh`
Expected: FAIL — old panel renders segmented "보통", not "중간"/description.

- [ ] **Step 3: Write minimal implementation**

First, widen the panel's visibility so the same-module test can reference it: change its declaration (near line 743) from `private fun SettingsInline(` to `internal fun SettingsInline(`. (Kotlin top-level `private` is file-scoped, so a test in another file could not otherwise call it; `internal` is module-visible, which the test module can see.)

In `HomeScreen.kt`, delete lines 119-129 (`LEVEL_OPTIONS`, `LENGTH_OPTIONS`, and the `levelLabel` function). Add the import:

```kotlin
import com.jjundev.oneclickeng.core.session.SessionLevel
import com.jjundev.oneclickeng.ui.component.OneClickSlider
import com.jjundev.oneclickeng.ui.component.SliderMode
```

Replace the two `Column` control blocks inside `SettingsInline` (lines 788-805) with slider-based controls:

```kotlin
                    // 레벨: 5-스톱 슬라이더(인덱스 0..4) + 선택 라벨/설명(우측 정렬, CEFR 미노출).
                    val current = SessionLevel.fromToken(level)
                    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            SettingLabel("난이도")
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = current.labelKo,
                                    style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = current.descKo,
                                    style = OceTheme.typography.helper,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                        OneClickSlider(
                            value = current.ordinal.toFloat(),
                            onValueChange = { onSetLevel(SessionLevel.entries[it.roundToInt()].token) },
                            mode =
                                SliderMode.Stepped(
                                    range = 0..SessionLevel.entries.lastIndex,
                                    step = 1,
                                    labelFormatter = { SessionLevel.entries[it].labelKo },
                                ),
                            showValueLabel = false,
                        )
                    }
                    // 길이: 짝수 6..20 슬라이더 + "N턴".
                    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SettingLabel("대화 길이")
                            Text(
                                text = "${length}턴",
                                style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        OneClickSlider(
                            value = length.toFloat(),
                            onValueChange = { onSetLength(it.roundToInt()) },
                            mode =
                                SliderMode.Stepped(
                                    range = HomeViewModel.MIN_LENGTH..HomeViewModel.MAX_LENGTH,
                                    step = HomeViewModel.LENGTH_STEP,
                                    labelFormatter = { "${it}턴" },
                                ),
                            showValueLabel = false,
                        )
                    }
```

Add imports for `TextAlign`, `roundToInt`, and `HomeViewModel` if not present:

```kotlin
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt
```

- [ ] **Step 4: Run test to verify it passes**

Run: `bash scripts/verify-android.sh`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeSettingsSliderTest.kt
git commit -m "feat(home): replace segmented settings with level + length sliders"
```

---

### Task 10: Hero subtitle + session header label via SoT

**Files:**
- Modify: `feature/home/HomeScreen.kt:587-592` (hero subtitle uses `SessionLevel`)
- Modify: `feature/session/turn/GeneratedDialogueSession.kt:799-811`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueLevelLabelTest.kt` (new)

**Interfaces:**
- Consumes: `SessionLevel` (Task 1).
- Produces: `dialogueLevelLabel` now maps via `SessionLevel.fromToken(...).labelKo`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jjundev.oneclickeng.feature.session.turn

import org.junit.Assert.assertEquals
import org.junit.Test

class DialogueLevelLabelTest {
    @Test
    fun `maps 5 tiers to korean label plus turns`() {
        assertEquals("매우 쉬움 · 6턴", dialogueLevelLabelForTest("starter", 6))
        assertEquals("중간 · 10턴", dialogueLevelLabelForTest("normal", 10))
        assertEquals("매우 어려움 · 20턴", dialogueLevelLabelForTest("expert", 20))
    }
}
```

(Expose the private fn for test via an `internal` test shim in the same package — see Step 3.)

- [ ] **Step 2: Run test to verify it fails**

Run: `bash scripts/verify-android.sh`
Expected: FAIL — `dialogueLevelLabelForTest` unresolved; current mapping returns "보통", lacks starter/expert.

- [ ] **Step 3: Write minimal implementation**

`GeneratedDialogueSession.kt` — replace `dialogueLevelLabel` (799-811):

```kotlin
private fun dialogueLevelLabel(
    level: String,
    totalTurns: Int,
): String = "${SessionLevel.fromToken(level).labelKo} · ${totalTurns}턴"

/** 테스트 전용 노출(순수 매핑 검증용). */
internal fun dialogueLevelLabelForTest(
    level: String,
    totalTurns: Int,
): String = dialogueLevelLabel(level, totalTurns)
```

Add import:

```kotlin
import com.jjundev.oneclickeng.core.session.SessionLevel
```

`HomeScreen.kt` — in `HeroCta`, change the subtitle's level mapping (line 591) from `level?.let(::levelLabel)` (now deleted) to:

```kotlin
            listOfNotNull(situationLabel, "${length}턴", level?.let { SessionLevel.fromToken(it).labelKo })
                .joinToString(" · ")
```

- [ ] **Step 4: Run test to verify it passes**

Run: `bash scripts/verify-android.sh`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueLevelLabelTest.kt
git commit -m "feat: hero + session header level label via SessionLevel SoT"
```

---

### Task 11: Dialogue header progress — dots vs "n / N"

**Files:**
- Modify: `feature/session/turn/DialogueHeader.kt:84-100`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueHeaderProgressTest.kt` (new, Robolectric)

**Interfaces:**
- Produces: header renders dot-grid when `totalTurns <= MAX_PROGRESS_DOTS (8)`, else the text `"$completedTurns / $totalTurns"`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jjundev.oneclickeng.feature.session.turn

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DialogueHeaderProgressTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `renders numeric progress when turns exceed dot cap`() {
        compose.setContent {
            OceTheme {
                DialogueHeader(
                    state = DialogueHeaderState(
                        topicEmoji = "☕", title = "카페", levelLabel = "중간 · 10턴",
                        totalTurns = 10, completedTurns = 3,
                    ),
                )
            }
        }
        compose.onNodeWithText("3 / 10").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `bash scripts/verify-android.sh`
Expected: FAIL — 10 dots render, no "3 / 10" text node.

- [ ] **Step 3: Write minimal implementation**

`DialogueHeader.kt` — add a constant near the other dot constants (line 33):

```kotlin
/** 진행 점 최대 개수 — 초과 시 48dp 헤더 폭 보호를 위해 "n / N" 수치 표기로 전환. */
private const val MAX_PROGRESS_DOTS = 8
```

Replace the progress `Row` (lines 84-100):

```kotlin
        if (state.totalTurns <= MAX_PROGRESS_DOTS) {
            Row(horizontalArrangement = Arrangement.spacedBy(ProgressDotGap)) {
                repeat(state.totalTurns) { index ->
                    val color =
                        if (index < state.completedTurns) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    Box(
                        modifier =
                            Modifier
                                .size(ProgressDotSize)
                                .clip(CircleShape)
                                .background(color),
                    )
                }
            }
        } else {
            Text(
                text = "${state.completedTurns} / ${state.totalTurns}",
                style = OceTheme.typography.accrualLabel.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `bash scripts/verify-android.sh`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueHeader.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueHeaderProgressTest.kt
git commit -m "feat(session): numeric progress in header above 8 turns"
```

---

### Task 12: Client XP map (accrual strip)

**Files:**
- Modify: `feature/gamification/GamificationTime.kt:22`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/gamification/GamificationTimeXpTest.kt` (new)

**Interfaces:**
- Produces: `GamificationTime.XP_BY_DIFFICULTY` with 5 keys mirroring `aggregate.ts` (Task 6).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jjundev.oneclickeng.feature.gamification

import org.junit.Assert.assertEquals
import org.junit.Test

class GamificationTimeXpTest {
    @Test
    fun `client XP map mirrors the 5-tier server table`() {
        assertEquals(
            mapOf("starter" to 5, "easy" to 10, "normal" to 20, "hard" to 35, "expert" to 55),
            GamificationTime.XP_BY_DIFFICULTY,
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `bash scripts/verify-android.sh`
Expected: FAIL — map has 3 keys.

- [ ] **Step 3: Write minimal implementation**

`GamificationTime.kt` line 22:

```kotlin
    val XP_BY_DIFFICULTY: Map<String, Int> =
        mapOf("starter" to 5, "easy" to 10, "normal" to 20, "hard" to 35, "expert" to 55)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `bash scripts/verify-android.sh`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/gamification/GamificationTime.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/gamification/GamificationTimeXpTest.kt
git commit -m "feat(gamification): mirror 5-tier XP map on the client"
```

---

### Task 13: CompletionLedger — surface PERMISSION_DENIED

**Files:**
- Modify: `feature/session/summary/CompletionLedger.kt:63-79`

**Interfaces:** none new (logging behavior only).

**Rationale:** during the 5-tier `firestore.rules` migration window, a `point_ledger` write with a new token can be rejected with `PERMISSION_DENIED`; the current broad `catch` logs it identically to a benign idempotent/offline skip, hiding a real XP-loss condition. Distinguish it so it is visible in logs (still non-fatal).

- [ ] **Step 1: Add the typed catch ahead of the generic one**

Add the import:

```kotlin
import com.google.firebase.firestore.FirebaseFirestoreException
```

Replace the single `catch (e: Exception)` block (lines 74-78) with:

```kotlin
                } catch (e: FirebaseFirestoreException) {
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        // 규칙 거부(예: 5티어 이관 창에서 신토큰 미허용) — XP 미적립으로 이어지므로 가시화.
                        Log.w(TAG, "point_ledger create denied — XP not accrued: ${e.message}")
                    } else {
                        Log.d(TAG, "point_ledger create skipped (idempotent/offline): ${e.message}")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "point_ledger create skipped (idempotent/offline): ${e.message}")
                }
```

- [ ] **Step 2: Verify it compiles and existing ledger tests still pass**

Run: `bash scripts/verify-android.sh`
Expected: PASS (no ledger behavior change beyond log level; existing `SavedCard*`/ledger tests unaffected).

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/CompletionLedger.kt
git commit -m "feat(summary): surface PERMISSION_DENIED on point_ledger create"
```

---

### Task 14: Wait-quiz tier mapping (starter→easy, expert→hard)

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/quiz/QuizBankRepository.kt` (package `...feature.session.dialogue.quiz`; `forTier` body near line 48)
- Modify: `feature/session/dialogue/DialogueGenerationViewModel.kt:156` (`FIRST_SESSION_TIER` via SoT)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/quiz/QuizTierKeyTest.kt` (new)

**Interfaces:**
- Consumes: `SessionLevel` (Task 1).
- Produces: `internal fun mapTierKey(tier: String): String` — the pure tier-remap (`starter→easy`, `expert→hard`, blank→`easy`, else lowercased passthrough); `forTier` routes its lookup through it so `forTier("starter")` reads the `easy` bank and `forTier("expert")` reads the `hard` bank. The 3-tier `wait_quiz_bank.json` is unchanged.

**Why a pure helper:** the real `QuizBankRepository` is `@Inject constructor(@ApplicationContext context: Context, json: Json)` with a private `byTier by lazy { ... context.assets ... }` — there is no map-injection seam and `QuizItem` has no `prompt`/`answer` fields, so the mapping is unit-tested through an extracted pure function (same pattern as `steppedSliderSpec` in Task 2), not by constructing the repository.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jjundev.oneclickeng.feature.session.dialogue.quiz

import org.junit.Assert.assertEquals
import org.junit.Test

class QuizTierKeyTest {
    @Test
    fun `starter maps to easy and expert maps to hard`() {
        assertEquals("easy", mapTierKey("starter"))
        assertEquals("hard", mapTierKey("expert"))
    }

    @Test
    fun `known 3 tiers pass through and blank falls back to easy`() {
        assertEquals("easy", mapTierKey("easy"))
        assertEquals("normal", mapTierKey("normal"))
        assertEquals("hard", mapTierKey("hard"))
        assertEquals("easy", mapTierKey("  "))
        assertEquals("easy", mapTierKey(""))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `bash scripts/verify-android.sh`
Expected: FAIL — `mapTierKey` unresolved.

- [ ] **Step 3: Write minimal implementation**

`QuizBankRepository.kt` — add the pure top-level helper (file scope, `internal` so the test module sees it) and route `forTier` through it. Replace the `forTier` body (near line 48):

```kotlin
        override fun forTier(tier: String): List<QuizItem> {
            val key = mapTierKey(tier)
            return (byTier[key] ?: byTier[EASY].orEmpty()).shuffled()
        }
```

Add, at file scope (outside the class), the pure mapping — keep the existing `EASY` companion constant:

```kotlin
/** 세션 난이도 토큰 → 3-티어 퀴즈 뱅크 키. starter→easy, expert→hard, 빈 값→easy, 그 외 소문자 통과. */
internal fun mapTierKey(tier: String): String =
    when (val t = tier.lowercase().ifBlank { "easy" }) {
        "starter" -> "easy"
        "expert" -> "hard"
        else -> t
    }
```

`DialogueGenerationViewModel.kt` line 156:

```kotlin
            val FIRST_SESSION_TIER = com.jjundev.oneclickeng.core.session.SessionLevel.EASY.token
```

- [ ] **Step 4: Run test to verify it passes**

Run: `bash scripts/verify-android.sh`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/quiz/QuizBankRepository.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModel.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/quiz/QuizTierKeyTest.kt
git commit -m "feat(quiz): map starter->easy and expert->hard quiz tiers"
```

---

### Task 15: Analytics contract doc + full verification

**Files:**
- Modify: `feature/home/HomeAnalytics.kt:30-33`

**Interfaces:** none (KDoc only).

- [ ] **Step 1: Update the analytics KDoc contract**

Replace the `sessionSettingChanged` KDoc (line 30) so the documented enums match reality:

```kotlin
    /** 접힌 세션 설정 변경. [level] ∈ {starter,easy,normal,hard,expert}, [length] ∈ 짝수 6..20. */
```

- [ ] **Step 2: Run the full Android verification**

Run: `bash scripts/verify-android.sh`
Expected: PASS (compile + all unit/Robolectric tests green).

- [ ] **Step 3: Run the full backend suite**

Run: `cd functions && npx jest && npm run build`
Expected: PASS (all suites green, tsc clean).

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeAnalytics.kt
git commit -m "docs(home): update analytics level/length contract for 5 tiers"
```

---

## Self-Review

- **Spec coverage:** Level 5-tier SoT (T1), slider primitive (T2), backend level+length validation (T3), feedback validator (T4), prompt bands (T5), XP table (T6), Firestore rule (T7), Home model defaults+clamp (T8), Home settings sliders + description (T9), hero/header labels (T10), header progress n/N (T11), client XP map (T12), PERMISSION_DENIED (T13), quiz tier mapping (T14), analytics doc (T15). Confirmed exclusions: onboarding picker (out of scope), no persisted-data migration (tokens unchanged). All Global Constraints map to a task.
- **Placeholder scan:** every code step carries complete code; no TBD/TODO.
- **Type consistency:** `SessionLevel.fromToken`/`.token`/`.labelKo`/`.descKo`/`.entries` used identically in T8–T14; `SliderMode.Stepped(range, step, labelFormatter)` signature identical in T2 and T9; `clampLength`/`MIN_LENGTH`/`MAX_LENGTH`/`LENGTH_STEP` defined in T8 and consumed in T9; `LEVEL_TOKENS`/`isEven6to20` defined in T3 and consumed in T3/T4.

---

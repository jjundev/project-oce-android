# Turn Feedback Prompt — Tone and Level Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 이슈 [#108](https://github.com/jjundev/project-oce-android/issues/108)의 두 문제 — 턴 피드백이 해요체를 벗어나 하십시오체를 섞어 쓰는 것과 `level`을 완전히 무시하는 것 — 을 eval 하네스가 **자동으로 실패로 잡을 수 있게** 만든 뒤, 그 측정 위에서 프롬프트 문안을 고친다.

**Architecture:** 순서가 핵심이다. 현재 하네스는 이 두 문제를 **제대로 측정하지 못한다** — 말투 검사는 문자열의 마지막 어미 하나만 보므로 혼용의 절반을 놓치고([validate.ts:67-70](functions/src/eval/validate.ts:67)), 게다가 위반이 아닌 `-답니다`를 위반으로 세며, 등급이 `warn`이라 몇 건이 나오든 "통과"로 보인다. 레벨 민감도는 자동 검사가 아예 없고 사람이 리포트를 눈으로 볼 뿐이다. 그래서 (1) 검사기를 먼저 정확하게 고치고, (2) 말투를 `error`로 승격하되 구조 결함과 별도 열로 집계하고, (3) 응답 **쌍**을 비교하는 새 검사(`compareLevelSensitivity`)를 만들어 러너에 붙인 다음, (4) **새 검사기로 기준선을 한 번 측정하고**, (5) 그제서야 프롬프트를 고치고, (6) 같은 검사기로 다시 재서 개선을 증명한다. 기준선을 새 검사기로 다시 재는 이유는, 옛 검사기가 만든 153건에는 오탐(`-답니다` 18건)이 섞여 있고 미탐(역순 혼용)도 있어 전후 비교가 성립하지 않기 때문이다.

**Tech Stack:** TypeScript 5.4 (strict, noUnusedLocals), Node 20, Firebase Cloud Functions v5, Jest 29 + ts-jest, ESLint 8. Vertex AI express mode, 모델 `gemini-3.1-flash-lite`, `FEEDBACK_TEMPERATURE = 0`.

## Global Constraints

- **`-답니다` / `-랍니다`는 허용한다.** 이 둘은 `니다`로 끝나지만 하십시오체가 아니라 다정한 구어 종결어미이고 앱 톤에 맞는다. 2026-07-18 스윕에서 옛 검사기가 잡은 153건 중 18건이 이 계열이었고 전부 오탐이다. 금지 대상은 `-입니다`·`-습니다`·`-ㅂ니다`(합니다/됩니다/줍니다) 세 계열뿐이다.
- **`level`은 설명과 제안만 바꾼다. 점수는 절대 바꾸지 않는다.** `writingScore.score`는 영어 자체에 대한 절대 평가라 같은 문장은 모든 레벨에서 같은 점수가 나와야 한다 — 학습자가 레벨을 바꿨다고 점수가 튀면 자기 실력을 일관되게 볼 수 없다.
- **말투 위반은 `error`로 승격하되, 구조 위반과 같은 칸에 세지 않는다.** 스키마가 깨진 것과 말투가 어긋난 것은 다른 종류의 결함이고, 섞으면 리포트에서 서로를 가린다.
- **프롬프트를 고치기 전에 새 검사기로 기준선을 측정한다.** 옛 리포트의 153건은 새 검사기의 숫자와 비교 가능하지 않다.
- `docs/design/prompts/_shared/difficulty-bands.md`를 **그대로 접어 넣지 않는다.** 그 문서는 `:5`에서 "exactly three levels: easy, normal, hard", `:27`에서 "NO C1"이라고 못박는데, 서버 SoT([levels.ts:6-15](functions/src/config/levels.ts:6))는 5레벨이고 `expert = C1`이다. 그대로 접으면 정면충돌한다. `DIALOGUE_SYSTEM_PROMPT`([gemini.ts:736-738](functions/src/providers/gemini.ts:736))가 이미 5레벨로 **재작성**한 선례를 따른다.
- 새 프로덕션 코드는 `functions/src/` 아래에 두어 `tsc`(strict)와 eslint의 대상이 되게 한다. `functions/test/`는 네트워크·에뮬레이터를 타지 않는다.
- `FEEDBACK_SYSTEM_PROMPT`를 고치면 `FEEDBACK_PROMPT_VERSION`을 함께 bump한다([gemini.ts:792](functions/src/providers/gemini.ts:792)의 주석 지시). 런타임에 영향이 없어 빠뜨리기 쉽다 — 실제로 dialogue는 2026-07-14인데 feedback은 2026-07-03에 멈춰 있다.
- Conventional Commits.

---

## File Structure

**Modify:**
- `functions/src/eval/validate.ts` — 말투 검출기를 문장 단위 스캔으로 교체(Task 1), 등급 승격(Task 2), 쌍 비교 검사 `compareLevelSensitivity` 신설(Task 3). 여전히 import 0개·순수 함수를 유지한다.
- `functions/test/eval-harness.test.ts` — 위 셋의 테스트. 기존 `:145-151` 해요체 테스트는 픽스처가 `"훌륭하다"`(해라체 한 단어)라 실제 실패 모드를 전혀 반영하지 못하므로 실제 프로덕션 문장으로 교체하고, `:150`의 "해요체 위반은 절대 error가 아니다" 단언은 Task 2에서 뒤집는다.
- `functions/eval/run.js` — `summarise`에 말투 전용 집계 추가(Task 2), 레벨 민감도 섹션을 자동 판정으로 교체(Task 4). 계속 plain JS로 `src/`·`test/` 밖에 둔다.
- `functions/src/providers/gemini.ts` — `FEEDBACK_SYSTEM_PROMPT` 문안 수정, `FEEDBACK_PROMPT_VERSION` bump, JSDoc 갱신(Task 6).
- `functions/src/eval/cases.ts` — `:197-199`의 "FEEDBACK_SYSTEM_PROMPT never mentions `level`" 주석 갱신(Task 6).
- `docs/design/prompts/feedback-slim.md` — INPUT 계약의 `level` 값과 규칙을 서버와 일치시킴(Task 6).
- `docs/design/prompt-system.md` — 결과 기록(Task 7).

**Unchanged, relied upon:**
- `functions/src/config/levels.ts` — `LEVEL_TOKENS`(5개)와 `CEFR_BAND`가 레벨 SoT. 주석이 "`CEFR_BAND` ... is NEVER surfaced to the client"라고 명시하므로 프롬프트에 CEFR를 써도 안전하다. `DIALOGUE_SYSTEM_PROMPT`는 상수를 import하지 않고 A1~C1을 문자열에 하드코딩한 선례이며, 이 계획도 그 선례를 따른다(프롬프트는 하나의 문자열 상수여야 하고 런타임 조립을 도입하지 않는다).
- `functions/src/config/generation.ts` — `FEEDBACK_TEMPERATURE = 0`. 이 계획은 온도를 건드리지 않는다.
- `functions/eval/stats.js` — 통계 헬퍼. 변경 없음.

**주의 — "현재 상태를 서술한 주석"이 3곳 있고 프롬프트 수정과 함께 갱신해야 한다:** [cases.ts:197-199](functions/src/eval/cases.ts:197), [run.js:349-350](functions/eval/run.js:349), [gemini.ts:799-800](functions/src/providers/gemini.ts:799). 특히 run.js의 안내문은 수정 후 **의미가 반전**되므로 방치하면 다음 리포트가 독자를 오도한다.

---

## Task 1: 말투 검출기를 문장 단위로 다시 만든다

**Why first:** 지금 검출기는 두 가지로 틀렸고, 고치기 전까지는 개선을 측정할 수 없다. 이 태스크는 검출 **정확도만** 바꾸고 등급은 `warn`으로 둔다 — 그래야 리뷰어가 검출 로직만 따로 판단할 수 있다.

**Files:**
- Modify: `functions/src/eval/validate.ts` — `endsHaeyo`(67-70)를 교체, `checkKoreanString`(135-153)의 호출부 수정
- Modify: `functions/test/eval-harness.test.ts` — 기존 해요체 테스트(145-151) 교체·확장

**Interfaces:**
- Consumes: 없음(순수 함수, 파일 내부 완결).
- Produces: `export function findHasipsioSentences(s: string): string[]` — 하십시오체로 끝나는 문장들을 반환하고, 전부 해요체면 빈 배열. Task 2가 이 결과로 등급을 매기고 Task 4가 리포트에 쓴다.

**현재 검출기의 두 결함 (이 태스크가 고치는 것):**

1. **문자열 전체의 끝만 본다.** `endsHaeyo`는 `s.trim()`의 마지막 글자만 검사하므로 문장을 나누지 않는다. 실측 위반 다수가 `"문법적으로 완벽한 문장이에요. 상대방에게 정중하게 허락을 구하는 아주 좋은 표현입니다."` 형태 — 앞은 해요체, 뒤는 하십시오체 — 인데, **순서가 반대인 경우(하십시오체 → 해요체)는 전혀 잡히지 않는다.** 즉 옛 153건은 혼용의 일부만 센 값이고, 검출기를 그대로 두고 프롬프트만 고치면 개선폭이 과대평가된다.
2. **`-답니다`/`-랍니다`를 위반으로 센다.** `요/죠`로 끝나야 통과하는 화이트리스트 방식이라, 다정한 구어체인 `"충분하답니다"`, `"사용한답니다"`가 걸린다. 실측 153건 중 18건(12%)이 이 오탐이다.

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`functions/test/eval-harness.test.ts` 상단 import에 `findHasipsioSentences`를 추가한다(기존 `../src/eval/validate` import 목록에 이름만 덧붙일 것):

```ts
  findHasipsioSentences,
```

그리고 기존 `it("warns — but does not fail — on a Korean line that is not 해요체", ...)` 테스트(파일 내 `해요체`로 검색하면 나온다)를 **통째로 삭제하고** 그 자리에 다음을 넣는다:

```ts
  it("warns on a line that ends in 하십시오체", () => {
    const bad = clone(GOOD_SLIM);
    bad.writingScore.encouragementMessage = "정말 잘하셨습니다.";
    const violations = validateSlim(bad);
    expect(violations.some((v) => v.check === "haeyo" && v.severity === "warn")).toBe(true);
    expect(violations.some((v) => v.severity === "error")).toBe(false);
  });

  it("warns when 하십시오체 appears in a NON-final sentence", () => {
    // The old detector only tested the end of the whole string, so this direction of
    // mixed register went completely uncounted.
    const bad = clone(GOOD_SLIM);
    bad.grammar.explanation = "아주 좋은 표현입니다. 그대로 쓰시면 돼요.";
    expect(validateSlim(bad).some((v) => v.check === "haeyo")).toBe(true);
  });

  it("does not warn on 답니다 / 랍니다 — warm, not deferential", () => {
    const ok = clone(GOOD_SLIM);
    ok.grammar.explanation = "중복을 피하면 훨씬 깔끔해진답니다.";
    expect(validateSlim(ok).some((v) => v.check === "haeyo")).toBe(false);

    const ok2 = clone(GOOD_SLIM);
    ok2.naturalExpression.reason.description = "원어민이 가장 선호하는 방식이랍니다.";
    expect(validateSlim(ok2).some((v) => v.check === "haeyo")).toBe(false);
  });
```

같은 파일 끝에 검출기 자체의 단위 테스트를 추가한다:

```ts
describe("findHasipsioSentences", () => {
  it("returns nothing for a fully 해요체 line", () => {
    expect(findHasipsioSentences("지난 일을 말할 때는 met을 써요.")).toEqual([]);
    expect(findHasipsioSentences("정말 잘했어요! 자연스러운 표현이에요.")).toEqual([]);
  });

  it("catches every 하십시오체 family the sweep actually produced", () => {
    // -입니다 was 52% of all flagged strings on 2026-07-18; -습니다 and -ㅂ니다 the rest.
    expect(findHasipsioSentences("아주 자연스러운 표현입니다.")).toHaveLength(1);
    expect(findHasipsioSentences("정말 잘하셨습니다.")).toHaveLength(1);
    expect(findHasipsioSentences("훨씬 부드러운 인상을 줍니다.")).toHaveLength(1);
    expect(findHasipsioSentences("문장이 훨씬 깔끔해집니다.")).toHaveLength(1);
    expect(findHasipsioSentences("아주 좋습니다.")).toHaveLength(1);
  });

  it("allows 답니다 and 랍니다", () => {
    expect(findHasipsioSentences("하나만 써도 충분하답니다!")).toEqual([]);
    expect(findHasipsioSentences("원어민이 선호하는 방식이랍니다.")).toEqual([]);
  });

  it("scans every sentence, not just the last", () => {
    const mixed = "아주 좋은 표현입니다. 그대로 쓰시면 돼요.";
    expect(findHasipsioSentences(mixed)).toEqual(["아주 좋은 표현입니다"]);
  });

  it("returns each offending sentence, so a mixed line reports all of them", () => {
    const line = "문법이 완벽합니다. 잘하셨어요. 아주 좋은 표현입니다.";
    expect(findHasipsioSentences(line)).toEqual([
      "문법이 완벽합니다",
      "아주 좋은 표현입니다",
    ]);
  });

  it("handles a line with no terminal punctuation", () => {
    expect(findHasipsioSentences("아주 자연스러운 표현입니다")).toHaveLength(1);
    expect(findHasipsioSentences("아주 자연스러운 표현이에요")).toEqual([]);
  });

  it("ignores empty and whitespace-only input", () => {
    expect(findHasipsioSentences("")).toEqual([]);
    expect(findHasipsioSentences("   ")).toEqual([]);
    expect(findHasipsioSentences("...")).toEqual([]);
  });
});
```

- [ ] **Step 2: 실패를 확인한다**

Run: `cd functions && npx jest test/eval-harness.test.ts`
Expected: FAIL — `findHasipsioSentences`가 없어 `Cannot find module`/TS2305 계열 에러. 그리고 `does not warn on 답니다` 테스트가 현재 구현에서 실패한다(옛 검출기는 `답니다`를 위반으로 센다).

- [ ] **Step 3: 검출기를 교체한다**

`functions/src/eval/validate.ts`에서 `endsHaeyo`(67-70) 전체를 다음으로 교체한다:

```ts
/**
 * Sentence-final 하십시오체 (deferential formal) endings: `-입니다`, `-습니다`, and the
 * `-ㅂ니다` contractions (합니다/됩니다/줍니다/…). The prompt requires 해요체 throughout;
 * the 2026-07-18 sweep found these in 135 of 153 flagged strings, `-입니다` alone being 52%.
 *
 * `-답니다`/`-랍니다` also end in `니다` but are DELIBERATELY allowed — they are a warm,
 * conversational ending that suits the app's voice, not deferential formal speech. They were
 * 18 of those 153 flags and every one was a false positive. The negative lookbehind is the
 * whole mechanism separating them from `-ㅂ니다`.
 */
const HASIPSIO_ENDING = /(?<![답랍])니다$/u;

/** Split a Korean line into sentences on terminal punctuation, dropping empty fragments. */
function splitSentences(s: string): string[] {
  return s
    .split(/[.!?…]+/u)
    .map((part) => part.trim())
    .filter((part) => part !== "");
}

/**
 * The sentences in `s` that end in 하십시오체; empty when the line is fully 해요체.
 *
 * Scans EVERY sentence. The previous detector tested only the end of the whole string, so
 * "표현입니다. 그대로 쓰시면 돼요." — 하십시오체 first, 해요체 last — went entirely
 * uncounted, meaning it silently undercounted the mixed-register problem it existed to
 * measure. Returning the offending sentences (not just a boolean) lets the report name
 * exactly which clause drifted.
 */
export function findHasipsioSentences(s: string): string[] {
  return splitSentences(s).filter((sentence) => HASIPSIO_ENDING.test(sentence));
}
```

그리고 `checkKoreanString`(135-153)의 마지막 블록을 교체한다. 현재:

```ts
  if (!endsHaeyo(value)) {
    warn("haeyo", `${check} may not be 해요체: ${value}`);
  }
```

를 다음으로:

```ts
  const offenders = findHasipsioSentences(value);
  if (offenders.length > 0) {
    warn("haeyo", `${check} uses 하십시오체 (해요체 required): ${offenders.join(" / ")}`);
  }
```

`checkKoreanString`의 JSDoc(`:135`)도 `(softly) 해요체` → `and free of 하십시오체` 로 고친다.

- [ ] **Step 4: 테스트 통과를 확인한다**

Run: `cd functions && npx jest test/eval-harness.test.ts`
Expected: PASS. 이전보다 테스트 수가 늘어난다(해요체 관련 3개 + `findHasipsioSentences` describe 7개).

- [ ] **Step 5: 전체 검증**

Run: `cd functions && npx jest && npm run build && npm run lint`
Expected: 전부 통과, exit 0.

- [ ] **Step 6: 커밋한다**

```bash
git add functions/src/eval/validate.ts functions/test/eval-harness.test.ts
git commit -m "fix(eval): detect 하십시오체 per sentence and stop flagging 답니다"
```

---

## Task 2: 말투 위반을 error로 승격하고 별도 열로 집계한다

**Why now:** Task 1이 검출을 정확하게 만들었으니 이제 게이트로 쓸 수 있다. 지금은 `warn`이라 몇 건이 나오든 리포트가 "통과"로 보인다.

**Files:**
- Modify: `functions/src/eval/validate.ts` — `checkKoreanString`의 말투 호출을 `warn` → `err`
- Modify: `functions/test/eval-harness.test.ts` — Task 1이 넣은 말투 테스트들의 등급 단언 수정
- Modify: `functions/eval/run.js` — `summarise`(177-260)에 `toneErrors` 버킷 추가, 요약표에 열 추가, "열 읽는 법" 갱신

**Interfaces:**
- Consumes: `findHasipsioSentences` (Task 1).
- Produces: `summarise()`의 반환 객체에 `toneErrors: number` 추가. 기존 키(`total`, `transportFailed`, `parseFailed`, `validated`, `structuralErrors`, `expectMismatches`, `warns`, `meanSpread`, `maxSpread`, `meanStdDev`, `maxStdDev`)는 이름·의미 모두 그대로 유지한다.

**분류 규칙 (이 태스크가 확정하는 것):** 위반 하나는 정확히 한 버킷에만 들어간다. 판정 순서는 `expect.` 접두사 → `haeyo` → `severity === "error"` → 나머지. `haeyo`를 `severity` 판정보다 **먼저** 걸러야 하며, 그러지 않으면 승격된 말투 오류가 `structuralErrors`에 섞여 스키마 결함을 가린다.

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`functions/test/eval-harness.test.ts`에서 Task 1이 만든 말투 테스트 3개의 등급 단언을 바꾼다.

`it("warns on a line that ends in 하십시오체", ...)`를 다음으로 교체:

```ts
  it("fails — not merely warns — on a line that ends in 하십시오체", () => {
    // Tone is a rule the prompt states outright, so breaking it is a failure, not a note.
    const bad = clone(GOOD_SLIM);
    bad.writingScore.encouragementMessage = "정말 잘하셨습니다.";
    const violations = validateSlim(bad);
    expect(violations.some((v) => v.check === "haeyo" && v.severity === "error")).toBe(true);
  });
```

`it("warns when 하십시오체 appears in a NON-final sentence", ...)`의 이름과 본문을 다음으로 교체:

```ts
  it("fails when 하십시오체 appears in a NON-final sentence", () => {
    // The old detector only tested the end of the whole string, so this direction of
    // mixed register went completely uncounted.
    const bad = clone(GOOD_SLIM);
    bad.grammar.explanation = "아주 좋은 표현입니다. 그대로 쓰시면 돼요.";
    const violations = validateSlim(bad);
    expect(violations.some((v) => v.check === "haeyo" && v.severity === "error")).toBe(true);
  });
```

`it("does not warn on 답니다 / 랍니다 — warm, not deferential", ...)`는 이름만 바꾸고 본문은 그대로 둔다(위반이 아예 없어야 하므로 등급과 무관):

```ts
  it("accepts 답니다 / 랍니다 — warm, not deferential", () => {
```

그리고 `GOOD_SLIM`이 여전히 무위반인지 지키는 테스트를 추가한다:

```ts
  it("keeps a multi-sentence 해요체 response clean", () => {
    // Guards against the tone check becoming so strict it fires on good output — a noisy
    // gate gets ignored, which is worse than a lenient one. Multi-sentence specifically,
    // because sentence splitting is the new machinery and the existing well-formed-response
    // test only ever exercises single-sentence strings.
    const ok = clone(GOOD_SLIM);
    ok.grammar.explanation = "지난 일은 met을 써요. 이렇게 쓰면 훨씬 자연스러워요!";
    ok.writingScore.encouragementMessage = "잘했어요! 거의 다 맞았어요.";
    expect(validateSlim(ok)).toEqual([]);
  });
```

- [ ] **Step 2: 실패를 확인한다**

Run: `cd functions && npx jest test/eval-harness.test.ts`
Expected: FAIL — 말투 위반이 아직 `warn`이라 `severity === "error"` 단언 2건이 실패한다.

- [ ] **Step 3: 등급을 승격한다**

`functions/src/eval/validate.ts`의 `checkKoreanString`에서 Task 1이 넣은 블록을 다음으로 교체한다:

```ts
  const offenders = findHasipsioSentences(value);
  if (offenders.length > 0) {
    // `error`, not `warn`: 해요체 is a rule FEEDBACK_SYSTEM_PROMPT states outright, so
    // breaking it is a defect. It is counted in its own column (run.js `toneErrors`), NOT
    // folded into structural errors — a drifted register and a broken schema are different
    // kinds of failure and each hides the other when summed.
    err("haeyo", `${check} uses 하십시오체 (해요체 required): ${offenders.join(" / ")}`);
  }
```

**여기서 연쇄가 하나 발생하니 끝까지 따라가라.** `checkKoreanString`이 유일한 `warn` 생산자였으므로, 승격 후 `warn` 파라미터가 미사용이 된다. 그러면:

1. `checkKoreanString`의 `warn` 파라미터를 제거하고, 그 함수의 호출부 5곳(slim 3곳, deep 2곳)에서 해당 인자를 뺀다.
2. 그러면 이번엔 `validateSlim`과 `validateDeep`의 `const { out, err, warn } = makeCollector();` 에서 `warn`이 미사용이 된다 — eslint의 `@typescript-eslint/no-unused-vars`가 잡는다. 두 곳 모두 구조분해에서 `warn`을 뺀다.
3. `makeCollector` 자체는 **그대로 둔다.** Task 3의 `compareLevelSensitivity`가 `warn`을 다시 쓴다(`level.score`는 의도적으로 `warn`이다). `makeCollector`가 `warn`을 계속 반환하는 것은 미사용이 아니다 — 반환 객체의 속성이라 린트 대상이 아니다.

즉 이 태스크가 끝난 시점에 `warn`을 쓰는 곳이 일시적으로 0이 되고, Task 3이 되살린다. 이는 의도된 것이니 `makeCollector`에서 `warn`을 삭제하지 마라. 어디를 어떻게 정리했는지 보고서에 적어라.

- [ ] **Step 4: 테스트 통과를 확인한다**

Run: `cd functions && npx jest test/eval-harness.test.ts`
Expected: PASS.

- [ ] **Step 5: 러너의 집계에 말투 열을 추가한다**

`functions/eval/run.js`의 `summarise`(177-260)에서 카운터 선언부를 수정한다. 현재:

```js
  let structuralErrors = 0;
  let expectMismatches = 0;
  let warns = 0;
  for (const r of at) {
    for (const v of r.violations) {
      if (v.check.startsWith("expect.")) {
        expectMismatches++;
      } else if (v.severity === "error") {
        structuralErrors++;
      } else {
        warns++;
      }
    }
  }
```

를 다음으로:

```js
  let structuralErrors = 0;
  let toneErrors = 0;
  let expectMismatches = 0;
  let warns = 0;
  for (const r of at) {
    for (const v of r.violations) {
      // Order matters: `haeyo` is severity "error" since the tone promotion, so it must be
      // pulled out BEFORE the generic error branch or it lands in `structuralErrors` and
      // masks genuine schema breakage.
      if (v.check.startsWith("expect.")) {
        expectMismatches++;
      } else if (v.check === "haeyo") {
        toneErrors++;
      } else if (v.severity === "error") {
        structuralErrors++;
      } else {
        warns++;
      }
    }
  }
```

반환 객체(`:247-259`)에 `toneErrors`를 추가한다 — `structuralErrors` 바로 다음 줄에:

```js
    toneErrors,
```

- [ ] **Step 6: 요약표에 열을 넣는다**

`functions/eval/run.js`의 `writeReport`에서 헤더 문자열(`:280`)의 `구조 위반` 바로 뒤에 `말투 위반`을 끼워 넣는다:

```js
  L.push(
    "| temperature | 호출 | 전송 실패 | 파싱 실패 | 검증됨 | 구조 위반 | 말투 위반 | 기대치 불일치 | warn 위반 | 표준편차(평균) | 표준편차(최대) | 점수 스프레드(평균) | 점수 스프레드(최대) |"
  );
  L.push("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |");
```

(구분선의 `| --- |` 개수가 13개인지 확인할 것 — 열이 12개에서 13개로 늘었다.)

그리고 행 생성부(`:286`)의 `${s.structuralErrors}` 바로 뒤에 `${s.toneErrors}`를 끼워 넣는다:

```js
      `| ${temp} | ${s.total} | ${s.transportFailed} | ${s.parseFailed} | ${s.validated} | ${s.structuralErrors} | ${s.toneErrors} | ${s.expectMismatches} | ${s.warns} | ${s.meanStdDev} | ${s.maxStdDev} | ${s.meanSpread} | ${s.maxSpread} |`
```

- [ ] **Step 7: "열 읽는 법" 안내를 갱신한다**

`writeReport`의 "**열 읽는 법**"으로 시작하는 안내 문자열에서 `구조 위반`을 설명하는 문장 바로 뒤에 다음 문장을 추가한다:

```
`말투 위반`은 학습자에게 보이는 한국어가 해요체를 벗어나 하십시오체(`-입니다`/`-습니다`/`-ㅂ니다`)로 끝난 문장 수다 — 프롬프트가 명시적으로 요구한 규칙이라 error로 집계하되, 스키마가 깨진 것과는 다른 종류의 결함이므로 `구조 위반`과 열을 나눴다(섞으면 서로를 가린다). `-답니다`/`-랍니다`는 다정한 구어 종결어미라 의도적으로 허용하며 위반으로 세지 않는다.
```

- [ ] **Step 8: 터미널 요약에도 말투 건수를 넣는다**

`functions/eval/run.js`의 `main()` 끝에서 온도별 한 줄 요약을 `console.log`하는 부분(`t=${temp}: 검증됨 ...`로 시작하는 템플릿 문자열)에 `말투 위반 ${s.toneErrors}`를 `구조 위반 ${s.structuralErrors}` 바로 뒤에 끼워 넣는다. Task 5와 Task 7에서 사람이 스윕을 돌릴 때 이 줄이 첫 신호이므로, 리포트 파일을 열기 전에 말투 건수가 보여야 한다.

- [ ] **Step 9: 러너가 여전히 오프라인에서 안전한지 확인한다**

Run: `cd functions && npx jest && npm run build && npm run lint`
Expected: 전부 통과.

Run: `cd functions && npm run build && node eval/run.js --task=bogus`
Expected: `Error: bad task: bogus (expected feedback or feedbackDeep)`와 함께 exit 1. 네트워크를 타지 않는다.

- [ ] **Step 10: 커밋한다**

```bash
git add functions/src/eval/validate.ts functions/test/eval-harness.test.ts functions/eval/run.js
git commit -m "feat(eval): promote tone violations to errors with their own report column"
```

---

## Task 3: 레벨 민감도 쌍 비교 검사

**Why now:** 이슈 #108의 두 번째 문제를 자동으로 잡으려면 응답 **두 개**를 비교해야 하는데, `validateSlim(json, expect)`는 단일 응답만 받고 `CaseExpectation`의 네 필드 어느 것도 "쌍 간 차이"를 표현할 수 없다. 새 함수가 필요하다. 순수 함수라 오프라인 테스트가 가능하다.

**Files:**
- Modify: `functions/src/eval/validate.ts` — `compareLevelSensitivity` 및 헬퍼 추가
- Modify: `functions/test/eval-harness.test.ts` — 테스트 추가

**Interfaces:**
- Consumes: 파일 내부의 `isRecord`, `makeCollector`, `scoreOf`, `Violation`.
- Produces: `export function compareLevelSensitivity(lower: unknown, higher: unknown): Violation[]` — Task 4의 러너가 호출한다. check id는 `level.shape`, `level.explanation`, `level.naturalExpression`(모두 `error`), `level.score`(`warn`).

**무엇을 검사하는가 (Global Constraints의 결정을 그대로 옮긴 것):**
- `grammar.explanation`이 두 레벨에서 **같으면 error** — 레벨이 설명에 반영되지 않았다는 뜻.
- `naturalExpression.segments`를 이어붙인 제안 문장이 두 레벨에서 **같으면 error** — 레벨이 제안에 반영되지 않았다는 뜻.
- `writingScore.score`가 두 레벨에서 **다르면 warn** — 점수는 절대 평가라 같아야 한다. `error`가 아니라 `warn`인 이유: 채점이 레벨에 흔들리는 것은 결함이지만, 1점 차이는 모델의 미세한 요동일 수도 있어 하드 게이트로 삼으면 노이즈를 만든다.

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`functions/test/eval-harness.test.ts` 상단 import 목록에 추가:

```ts
  compareLevelSensitivity,
```

파일 끝에 추가:

```ts
describe("compareLevelSensitivity", () => {
  /** a response that differs from GOOD_SLIM in explanation and suggested phrasing */
  function variant(explanation: string, suggestion: string) {
    const v = clone(GOOD_SLIM);
    v.grammar.explanation = explanation;
    v.naturalExpression.segments = [{ text: suggestion, type: "normal" }];
    return v;
  }

  it("passes when explanation and suggestion both differ across levels", () => {
    const starter = variant("지난 일은 met을 써요.", "I met my friend.");
    const expert = variant("과거 시제 일치를 위해 met이 맞아요.", "I caught up with my friend.");
    expect(compareLevelSensitivity(starter, expert)).toEqual([]);
  });

  it("fails when the explanation is identical at both levels", () => {
    // This is the exact 2026-07-18 finding: lv-starter and lv-expert came back
    // character-for-character identical, proving `level` was ignored entirely.
    const same = variant("지난 일은 met을 써요.", "I met my friend.");
    const violations = compareLevelSensitivity(clone(same), clone(same));
    expect(violations.some((v) => v.check === "level.explanation" && v.severity === "error")).toBe(true);
  });

  it("fails when the suggested phrasing is identical at both levels", () => {
    const starter = variant("쉬운 설명이에요.", "I met my friend.");
    const expert = variant("정밀한 설명이에요.", "I met my friend.");
    const violations = compareLevelSensitivity(starter, expert);
    expect(violations.some((v) => v.check === "level.naturalExpression" && v.severity === "error")).toBe(true);
    expect(violations.some((v) => v.check === "level.explanation")).toBe(false);
  });

  it("ignores surrounding whitespace when comparing", () => {
    const starter = variant("같은 설명이에요.", "I met my friend.");
    const expert = variant("  같은 설명이에요.  ", "I met my friend.");
    expect(compareLevelSensitivity(starter, expert).some((v) => v.check === "level.explanation")).toBe(true);
  });

  it("warns — does not fail — when the score moves with level", () => {
    // Scoring is meant to be absolute so a learner's number stays comparable when they
    // change level. A drift is a defect, but a 1-point wobble is not worth a hard gate.
    const starter = variant("쉬운 설명이에요.", "I met my friend.");
    const expert = variant("정밀한 설명이에요.", "I caught up with my friend.");
    expert.writingScore.score = 70;
    const violations = compareLevelSensitivity(starter, expert);
    expect(violations.some((v) => v.check === "level.score" && v.severity === "warn")).toBe(true);
    expect(violations.some((v) => v.severity === "error")).toBe(false);
  });

  it("reports a shape problem rather than throwing", () => {
    expect(compareLevelSensitivity(null, GOOD_SLIM).some((v) => v.check === "level.shape")).toBe(true);
    expect(compareLevelSensitivity(GOOD_SLIM, "nope").some((v) => v.check === "level.shape")).toBe(true);
  });

  it("reports a missing explanation instead of calling it a difference", () => {
    const broken = clone(GOOD_SLIM) as Record<string, unknown>;
    delete (broken.grammar as Record<string, unknown>).explanation;
    const violations = compareLevelSensitivity(broken, GOOD_SLIM);
    expect(
      violations.some(
        (v) => v.check === "level.explanation" && v.detail.includes("missing")
      )
    ).toBe(true);
  });
});
```

- [ ] **Step 2: 실패를 확인한다**

Run: `cd functions && npx jest test/eval-harness.test.ts`
Expected: FAIL — `compareLevelSensitivity` export가 없다.

- [ ] **Step 3: 구현한다**

`functions/src/eval/validate.ts` 파일 끝(마지막 헬퍼 뒤)에 추가한다:

```ts
/** `grammar.explanation`, trimmed; null when absent or not a string. */
function explanationOf(json: Record<string, unknown>): string | null {
  const grammar = json.grammar;
  if (!isRecord(grammar) || typeof grammar.explanation !== "string") return null;
  const text = grammar.explanation.trim();
  return text === "" ? null : text;
}

/** The suggested natural phrasing, reassembled from `naturalExpression.segments`. */
function suggestionOf(json: Record<string, unknown>): string | null {
  const ne = json.naturalExpression;
  if (!isRecord(ne) || !Array.isArray(ne.segments)) return null;
  const text = ne.segments
    .map((s) => (isRecord(s) && typeof s.text === "string" ? s.text : ""))
    .join("")
    .trim();
  return text === "" ? null : text;
}

/**
 * Compare two slim responses to the SAME English sentence submitted at two different
 * `level`s. This is the one check `validateSlim` structurally cannot express: it takes a
 * single response, and level-awareness is only observable in the DIFFERENCE between a pair.
 *
 * What level must change, and what it must not:
 *   - `grammar.explanation` and the suggested phrasing MUST adapt — a starter needs simpler
 *     Korean and an easier alternative sentence than an expert. Identical output across the
 *     pair is the signature of `level` being ignored outright (exactly what the 2026-07-18
 *     sweep found: the two responses were character-for-character identical).
 *   - `writingScore.score` must NOT move. The score judges the English itself, so the same
 *     sentence scores the same at every level — otherwise a learner who changes level sees
 *     their number jump for no reason they can perceive. A drift is a `warn` rather than an
 *     `error` because a one-point wobble is model noise, not evidence of level-scaled grading.
 *
 * `lower`/`higher` name the lower- and higher-level response; the checks are symmetric and
 * the order only shapes the message.
 */
export function compareLevelSensitivity(lower: unknown, higher: unknown): Violation[] {
  const { out, err, warn } = makeCollector();
  if (!isRecord(lower) || !isRecord(higher)) {
    err("level.shape", "one or both responses are not JSON objects");
    return out;
  }

  const lowerExplanation = explanationOf(lower);
  const higherExplanation = explanationOf(higher);
  if (lowerExplanation === null || higherExplanation === null) {
    err("level.explanation", "grammar.explanation is missing from one or both responses");
  } else if (lowerExplanation === higherExplanation) {
    err(
      "level.explanation",
      `identical explanation at both levels — level is being ignored: ${lowerExplanation}`
    );
  }

  const lowerSuggestion = suggestionOf(lower);
  const higherSuggestion = suggestionOf(higher);
  if (lowerSuggestion === null || higherSuggestion === null) {
    err(
      "level.naturalExpression",
      "naturalExpression.segments is missing from one or both responses"
    );
  } else if (lowerSuggestion === higherSuggestion) {
    err(
      "level.naturalExpression",
      `identical suggested phrasing at both levels — level is being ignored: ${lowerSuggestion}`
    );
  }

  const lowerScore = scoreOf(lower);
  const higherScore = scoreOf(higher);
  if (lowerScore !== null && higherScore !== null && lowerScore !== higherScore) {
    warn(
      "level.score",
      `score moved with level (${lowerScore} vs ${higherScore}) — scoring is meant to be absolute so a learner's number stays comparable across levels`
    );
  }

  return out;
}
```

- [ ] **Step 4: 테스트 통과를 확인한다**

Run: `cd functions && npx jest test/eval-harness.test.ts`
Expected: PASS.

- [ ] **Step 5: 전체 검증**

Run: `cd functions && npx jest && npm run build && npm run lint`
Expected: 전부 통과.

- [ ] **Step 6: 커밋한다**

```bash
git add functions/src/eval/validate.ts functions/test/eval-harness.test.ts
git commit -m "feat(eval): add level-sensitivity pair comparison"
```

---

## Task 4: 레벨 민감도 섹션을 자동 판정으로 바꾼다

**Why now:** Task 3이 판정 로직을 만들었으니 리포트가 사람 눈 대신 그것을 쓰게 한다. 현재 섹션은 반복 5회 중 **1회만** 보여주므로 표본 1개에 의존하고, 안내문이 "프롬프트는 `level`을 전혀 언급하지 않으므로"라고 현재 상태를 하드코딩해 서술하고 있어 Task 6 이후 **거짓이 된다**.

**Files:**
- Modify: `functions/eval/run.js` — 레벨 민감도 섹션(342-382) 전체 교체, `compareLevelSensitivity` require 추가

**Interfaces:**
- Consumes: `compareLevelSensitivity(lower, higher): Violation[]` (Task 3), 기존 `scoreOf`, `renderSegments`, `get`.
- Produces: 없음(리포트 출력만).

- [ ] **Step 1: require에 새 함수를 추가한다**

`functions/eval/run.js`의 `../lib/eval/validate` require 구조분해 목록에 `compareLevelSensitivity`를 추가한다.

- [ ] **Step 2: 레벨 민감도 섹션을 교체한다**

`functions/eval/run.js`의 `// ── 레벨 민감도 (Fix 6): ...` 주석부터 그 `if` 블록 끝까지를 다음으로 교체한다:

```js
  // ── 레벨 민감도: lv-starter/lv-expert를 반복별로 짝지어 자동 판정 ────────────────
  // Compares repeat i of the lower level against repeat i of the higher level, for every
  // repeat — not one sampled pair. With FEEDBACK_TEMPERATURE at 0 the model is near
  // deterministic, so a single pair could look identical (or different) by luck; judging
  // all of them turns "level is ignored" from an impression into a count.
  const levelVariancePair = cases.filter((c) => c.category === "level-variance");
  if (levelVariancePair.length === 2) {
    const [lowerCase, higherCase] = levelVariancePair;
    L.push("## 레벨 민감도 (level-variance)");
    L.push("");
    L.push(
      `\`${lowerCase.id}\`(level=\`${lowerCase.payload.level}\`)와 \`${higherCase.id}\`(level=\`${higherCase.payload.level}\`)는 ` +
      "영어 문장이 완전히 동일하고 `level`만 다르다. 레벨이 반영된다면 **설명과 제안 문장이 서로 달라야** 하고, " +
      "**점수는 같아야** 한다 — 점수는 영어 자체에 대한 절대 평가라 학습자가 레벨을 바꿔도 흔들리면 안 된다."
    );
    L.push("");
    L.push(
      `**이 비교는 스윕의 가장 낮은 온도(t=${lowestTemp})에서만 의미가 있다** — 온도가 높아지면 두 출력의 차이가 ` +
      "레벨 인지인지 단순 샘플링 노이즈인지 구분할 수 없다(confound)."
    );
    L.push("");

    const pairRows = [];
    let identicalCount = 0;
    for (let repeat = 1; repeat <= opts.repeats; repeat++) {
      const findRun = (caseId) =>
        runs.find(
          (x) =>
            x.caseId === caseId &&
            x.temp === lowestTemp &&
            x.repeat === repeat &&
            !x.error &&
            !x.parseError
        );
      const lo = findRun(lowerCase.id);
      const hi = findRun(higherCase.id);
      if (!lo || !hi) {
        pairRows.push(`| ${repeat} | — | — | — | 호출 실패 |`);
        continue;
      }
      const violations = compareLevelSensitivity(lo.json, hi.json);
      const sameExplanation = violations.some((v) => v.check === "level.explanation");
      const sameSuggestion = violations.some((v) => v.check === "level.naturalExpression");
      const scoreMoved = violations.some((v) => v.check === "level.score");
      if (sameExplanation) identicalCount++;
      pairRows.push(
        `| ${repeat} | ${sameExplanation ? "✗ 동일" : "✓ 다름"} | ${sameSuggestion ? "✗ 동일" : "✓ 다름"} | ` +
        `${scoreOf(lo.json)} / ${scoreOf(hi.json)}${scoreMoved ? " ⚠ 점수가 레벨을 탐" : ""} | ` +
        `${violations.filter((v) => v.severity === "error").length}건 |`
      );
    }

    L.push(
      identicalCount === 0
        ? `**판정: 통과 — ${opts.repeats}회 모두 설명이 레벨에 따라 달라졌다.**`
        : `**판정: 실패 — ${opts.repeats}회 중 ${identicalCount}회에서 설명이 두 레벨에 걸쳐 동일했다.**`
    );
    L.push("");
    L.push("| # | 설명 | 제안 문장 | 점수 (낮은 레벨 / 높은 레벨) | error |");
    L.push("| --- | --- | --- | --- | --- |");
    for (const row of pairRows) L.push(row);
    L.push("");

    for (const c of levelVariancePair) {
      const r = runs.find(
        (x) => x.caseId === c.id && x.temp === lowestTemp && !x.error && !x.parseError
      );
      L.push(`### \`${c.id}\` (level=\`${c.payload.level}\`) — t=${lowestTemp}, 반복 1`);
      L.push("");
      if (!r) {
        L.push("_이 온도에서 모든 반복 호출이 실패했거나 파싱에 실패했다._");
        L.push("");
        continue;
      }
      const score = scoreOf(r.json);
      L.push(`- 점수: **${score === null ? "—" : score}** · incorrect 세그먼트 ${countIncorrectSegments(r.json)}개`);
      L.push(`- 교정: ${renderSegments(get(r.json, "grammar", "correctedSentence", "segments"))}`);
      L.push(`- 설명: ${get(r.json, "grammar", "explanation") || "—"}`);
      L.push(`- 제안: ${renderSegments(get(r.json, "naturalExpression", "segments"))}`);
      L.push("");
    }
  }
```

주의: 원래 코드에는 `isDeep`일 때 JSON을 통째로 덤프하는 분기가 있었다. 레벨 민감도는 slim 전용 개념(`grammar.explanation`이 없는 deep에는 적용 불가)이므로 그 분기를 제거했다. deep 실행 시 이 섹션이 무의미해지는 것을 막으려면 `if (levelVariancePair.length === 2)` 조건에 `&& !isDeep`을 함께 넣어라.

- [ ] **Step 3: 러너가 오프라인에서 안전한지 확인한다**

Run: `cd functions && npm run build && node eval/run.js --task=bogus`
Expected: `Error: bad task: bogus (expected feedback or feedbackDeep)`와 함께 exit 1.

Run: `cd functions && GEMINI_API_KEY= node eval/run.js --only=lv-starter,lv-expert --repeats=1 --temps=0`
Expected: `GEMINI_API_KEY is not set.` 안내 후 exit 1. 네트워크를 타지 않는다.

- [ ] **Step 4: 전체 검증**

Run: `cd functions && npx jest && npm run build && npm run lint`
Expected: 전부 통과.

- [ ] **Step 5: 커밋한다**

```bash
git add functions/eval/run.js
git commit -m "feat(eval): judge level sensitivity automatically across every repeat"
```

---

## Task 5: 기준선 측정 (사람이 실행)

**Why now:** 프롬프트를 고치기 **전에**, 새 검사기로 숫자를 한 번 받아둔다. 옛 리포트의 153건은 오탐 18건을 포함하고 역순 혼용을 놓친 값이라 새 숫자와 비교할 수 없다.

**Files:** 없음(측정만).

**Interfaces:**
- Consumes: Task 1~4의 하네스 전체.
- Produces: 기준선 숫자 — `말투 위반` 건수와 레벨 민감도 판정. Task 7이 이것과 비교한다.

- [ ] **Step 1: 기준선 스윕을 실행한다**

Run:
```bash
cd functions && GEMINI_API_KEY=$(firebase functions:secrets:access GEMINI_API_KEY) \
  npm run eval -- --temps=0 --repeats=5 --task=feedback
```
Expected: 70회 호출(14케이스 × 1온도 × 5회) 후 `리포트: .../eval/out/feedback-<stamp>.md` 경로 출력.

온도는 `0` 하나만 쓴다 — 이미 확정된 프로덕션 값이고, 이 계획은 온도를 재탐색하지 않는다. 레벨 민감도 판정도 최저 온도에서만 유효하므로 단일 온도가 맞다.

- [ ] **Step 2: 기준선 숫자를 기록한다**

리포트의 요약표에서 `말투 위반` 값과, `## 레벨 민감도` 섹션의 **판정** 줄을 그대로 옮겨 적는다. 예상되는 형태(2026-07-18 데이터 기준 추정): 말투 위반은 30건대, 레벨 판정은 "실패 — 5회 중 5회에서 설명이 동일".

이 두 숫자가 Task 7이 이겨야 하는 기준선이다. 리포트 파일 경로도 함께 적어라 — `eval/out/`은 gitignore되므로 경로를 잃으면 근거가 사라진다.

- [ ] **Step 3: 커밋할 것은 없다**

이 태스크는 코드를 바꾸지 않는다. 측정값은 Task 7의 문서 갱신에 들어간다.

---

## Task 6: 프롬프트 문안을 고친다

**Why now:** 이제 개선을 증명할 측정 수단과 기준선이 둘 다 있다.

**Files:**
- Modify: `functions/src/providers/gemini.ts` — `FEEDBACK_PROMPT_VERSION`(793), `FEEDBACK_SYSTEM_PROMPT`(803-833)와 그 JSDoc(795-802)
- Modify: `functions/src/eval/cases.ts` — `:197-199` 주석
- Modify: `docs/design/prompts/feedback-slim.md` — INPUT 계약의 level 값, 규칙 동기화

**Interfaces:**
- Consumes: 없음(문자열 상수 수정).
- Produces: 없음(런타임 시그니처 불변). `npm run eval`은 `npm run build`를 선행하므로 별도 빌드 없이 새 문안이 반영된다.

**세 가지를 바꾼다:**

1. **말투 규칙을 강화한다.** 지금 규칙은 `"Every learner-facing string is Korean in 해요체"` 한 줄뿐이고, 해요체가 무엇인지·어떤 어미가 금지인지 정의가 없다. 실측에서 `~표현입니다`가 단독 52%였고 **칭찬 문맥에 집중**되어 있으므로, 금지 어미를 이름으로 지목하고 대조 예시를 주고 칭찬 상황을 명시적으로 경고한다.
2. **레벨 지시를 추가한다.** `level`이 프롬프트에 아예 등장하지 않는다. 5토큰 전부를 CEFR와 함께 나열하고, **설명과 제안만** 바꾸고 **점수는 바꾸지 말라**고 명시한다.
3. **COMMON ERRORS 참조를 접어 넣는다.** `feedback-slim.md:34`의 `"Prioritize the COMMON ERRORS reference above."` 지시와 그것이 가리키는 `_shared/korean-error-reference.md`의 8개 오류 계열이 서버 상수에 둘 다 빠져 있다.

- [ ] **Step 1: 프롬프트 버전을 bump한다**

`functions/src/providers/gemini.ts:793`:

```ts
export const FEEDBACK_PROMPT_VERSION = "2026-07-18";
```

- [ ] **Step 2: JSDoc을 갱신한다**

`functions/src/providers/gemini.ts:795-802`의 JSDoc에서 `"with the shared safety + tone prefix folded in"` 부분을 다음으로 고친다(나머지 문장은 그대로 둔다):

```
 * Ported from docs/design/prompts/feedback-slim.md with the shared safety, tone, difficulty-band
 * and korean-error-reference prefixes folded in (the _shared/* files are not bundled). The
 * difficulty bands are REWRITTEN, not copied: _shared/difficulty-bands.md declares three levels
 * (easy/normal/hard) with a hard "no C1" ceiling, while the server's SoT (config/levels.ts) has
 * five and puts expert at C1 — DIALOGUE_SYSTEM_PROMPT set the same precedent.
```

- [ ] **Step 3: 프롬프트에 LEVEL과 COMMON ERRORS 블록을 추가한다**

`functions/src/providers/gemini.ts`의 `FEEDBACK_SYSTEM_PROMPT`에서, `"Emit the three sections in this order: writingScore → grammar → naturalExpression.\n"` 줄과 그 뒤의 `"\n"` **바로 다음**에 다음 두 블록을 삽입한다(`writingScore —` 로 시작하는 줄 앞):

```ts
  "LEVEL: `level` is the difficulty the learner chose — starter (CEFR A1), easy (A2), normal " +
  "(B1), hard (B2), expert (C1). Adapt exactly TWO things to it:\n" +
  "(a) `grammar.explanation` and `naturalExpression.reason.description` — for starter/easy use " +
  "very simple Korean, one idea per line, and name only the single most important fix; for " +
  "hard/expert be more precise and you may address a subtler point.\n" +
  "(b) the phrasing you suggest in `naturalExpression.segments` — keep it within reach at the " +
  "learner's level: never offer a C1 idiom to a starter, never offer a flat A1 sentence to an " +
  "expert.\n" +
  "`level` must NOT move `writingScore.score`. The score judges the English itself, so the same " +
  "sentence scores the same at every level — a learner who changes level must never see their " +
  "number jump.\n" +
  "\n" +
  "COMMON ERRORS OF KOREAN LEARNERS — prioritize these when choosing what to correct: " +
  "1. word order (Korean is verb-final: \"I yesterday store went\" → \"I went to the store " +
  "yesterday\"); 2. articles a/an/the (Korean has none, so they are dropped or misused); " +
  "3. plurals (the -s dropped or over-applied); 4. tense/aspect (past simple vs present perfect " +
  "vs present); 5. prepositions (in/on/at/for/to translated straight from Korean particles); " +
  "6. omitted subjects/pronouns (\"Is good\" → \"It is good\"); 7. adjective vs adverb " +
  "(good/well, quick/quickly); 8. Konglish and false friends (\"hand phone\" → \"cell phone\").\n" +
  "\n" +
```

- [ ] **Step 4: 말투 규칙을 강화한다**

같은 상수의 `"RULES:\n"` 뒤 1번 항목을 교체한다. 현재:

```ts
  "1. Every learner-facing string is Korean in 해요체 except English example text. Concise (≤2 " +
  "lines), benefit-first, no jargon.\n" +
```

를 다음으로:

```ts
  "1. TONE — every learner-facing Korean string must be 해요체 in EVERY sentence, not just the " +
  "last one. NEVER end a sentence in 하십시오체: `-입니다`, `-습니다`, `-ㅂ니다` (합니다/됩니다/" +
  "줍니다) are forbidden. Write \"자연스러운 표현이에요\" NOT \"자연스러운 표현입니다\"; write " +
  "\"정말 잘하셨어요\" NOT \"정말 잘하셨습니다\". `-답니다`/`-랍니다` are fine. Watch this most " +
  "closely when PRAISING a good sentence — that is where 하십시오체 slips in. English example " +
  "text is exempt. Concise (≤2 lines), benefit-first, no jargon.\n" +
```

- [ ] **Step 5: 오래된 주석을 갱신한다**

`functions/src/eval/cases.ts`의 `:197-199` 주석(`// FEEDBACK_SYSTEM_PROMPT never mentions ...`로 시작하는 3줄)을 다음으로 교체한다:

```ts
  // These two carry identical English and differ only in `level`. FEEDBACK_SYSTEM_PROMPT now
  // instructs the model to adapt its explanation and suggested phrasing to `level` while
  // holding the score absolute (2026-07-18), so identical output across the pair means the
  // instruction is not landing — compareLevelSensitivity in ./validate.ts is what judges it.
```

- [ ] **Step 6: 설계 문서를 서버와 일치시킨다**

`docs/design/prompts/feedback-slim.md`에서:

(a) `:5`의 INPUT 계약에서 `"level": "easy|normal|hard"` 를 `"level": "starter|easy|normal|hard|expert"` 로 고친다 — 서버 `LEVEL_TOKENS`가 5개이고 골든 케이스가 실제로 `starter`/`expert`를 태운다.

(b) `## Rules` 1번을 서버 상수와 같은 강도로 고친다:

```
1. TONE — every learner-facing Korean string is 해요체 in EVERY sentence, not just the last. Never end a sentence in 하십시오체 (`-입니다`/`-습니다`/`-ㅂ니다`); `-답니다`/`-랍니다` are fine. Watch this most closely when praising. English example text is exempt. Concise (≤2 lines), benefit-first, no jargon.
```

(c) `## Sections` 앞에 레벨 규칙 한 문단을 추가한다:

```
**Level** — `level` adapts exactly two things: the Korean explanations (simpler and narrower for starter/easy, more precise for hard/expert) and the difficulty of the phrasing suggested in `naturalExpression`. It must NOT move `writingScore.score`, which is an absolute judgement of the English so a learner's number stays comparable across levels.
```

- [ ] **Step 7: 빌드와 테스트를 확인한다**

Run: `cd functions && npx jest && npm run build && npm run lint`
Expected: 전부 통과. 프롬프트는 문자열 상수라 기존 테스트는 그 **내용**을 단언하지 않으므로 영향받지 않는다. 만약 어떤 테스트가 프롬프트 문자열을 하드코딩해 비교하고 있어 실패한다면, 그 테스트가 무엇을 지키려던 것인지 보고 판단하라 — 프롬프트를 되돌리지 말 것.

- [ ] **Step 8: 커밋한다**

```bash
git add functions/src/providers/gemini.ts functions/src/eval/cases.ts docs/design/prompts/feedback-slim.md
git commit -m "feat(functions): make feedback tone explicit and level-aware"
```

---

## Task 7: 개선 측정과 마무리 (사람이 실행)

**Why last:** 프롬프트 수정이 실제로 통했는지는 같은 하네스로 재서만 알 수 있다. 추측으로 닫지 않는다.

**Files:**
- Modify: `docs/design/prompt-system.md` — 결과 기록
- 이슈 #108 갱신

**Interfaces:**
- Consumes: Task 5의 기준선 숫자, Task 1~4의 하네스, Task 6의 새 프롬프트.
- Produces: 없음.

- [ ] **Step 1: 수정 후 스윕을 실행한다**

Run:
```bash
cd functions && GEMINI_API_KEY=$(firebase functions:secrets:access GEMINI_API_KEY) \
  npm run eval -- --temps=0 --repeats=5 --task=feedback
```
Expected: 70회 호출 후 새 리포트 경로 출력. Task 5와 **완전히 동일한 명령**이어야 비교가 성립한다.

- [ ] **Step 2: 기준선과 비교한다**

두 리포트의 요약표를 나란히 놓고 세 숫자를 본다:

| | 기준선 (Task 5) | 수정 후 |
|---|---|---|
| 말투 위반 | ? | ? |
| 레벨 민감도 판정 | 실패 n/5 | ? |
| 구조 위반 | 0이어야 함 | 0이어야 함 |

판정 기준:
- **말투** — 0건이 목표다. 남았다면 리포트의 `말투 위반` 행에서 실제 문장을 보고, 어떤 문맥에서 새는지 확인한다. 프롬프트가 이미 금지 어미를 이름으로 지목했는데도 남는다면 그것은 문안을 더 강화할 문제이지 하네스 문제가 아니다.
- **레벨** — "통과 — 5회 모두 설명이 레벨에 따라 달라졌다"가 목표다. 부분적으로만 달라졌다면(예: 5회 중 3회) 지시가 약한 것이므로 LEVEL 블록을 더 구체화한다.
- **구조 위반** — 반드시 0을 유지해야 한다. 프롬프트가 길어지면서 JSON 형식이 깨지면 그것은 명백한 회귀다. 0이 아니면 다음 스텝으로 넘어가지 말고 되돌아가 원인을 잡는다.

목표에 못 미치면 Task 6으로 돌아가 문안을 조정하고 이 스텝을 다시 돈다. 하네스가 있으니 반복 비용은 70회 호출이다.

- [ ] **Step 3: 설계 문서에 결과를 기록한다**

`docs/design/prompt-system.md`의 §9 의사결정 로그에, temperature 확정 줄 근처에 다음 형태로 한 줄을 추가한다(`<수치>`는 Step 2의 실제 값으로 채울 것):

```
**확정(2026-07-18, 프롬프트 문안):** feedback 프롬프트에 (a) 금지 어미를 이름으로 지목한 해요체 규칙, (b) 5레벨 LEVEL 블록(설명·제안만 적응, 점수는 절대 유지), (c) korean-error-reference 8개 오류 계열을 접어 넣었다. `functions/eval/`로 전후 측정: 말투 위반 `<기준선>`건 → `<수정후>`건, 레벨 민감도 `<기준선 판정>` → `<수정후 판정>`, 구조 위반 0 유지. `FEEDBACK_PROMPT_VERSION = "2026-07-18"`.
```

- [ ] **Step 4: 커밋한다**

```bash
git add docs/design/prompt-system.md
git commit -m "docs(design): record the tone and level prompt fix measurement"
```

- [ ] **Step 5: 이슈 #108을 갱신한다**

측정 결과를 코멘트로 남기고, 세 항목이 모두 해소됐으면 닫는다. 항목 3(shared 조각 미접힘)은 Task 6 Step 3에서 `korean-error-reference` 내용을 접었고 difficulty-bands는 5레벨로 재작성해 반영했으므로 함께 해소된 것으로 본다.

```bash
gh issue comment 108 --repo jjundev/project-oce-android --body "$(cat <<'EOF'
`functions/eval/`로 전후 측정 완료.

| | 기준선 | 수정 후 |
| --- | --- | --- |
| 말투 위반 | <값> | <값> |
| 레벨 민감도 | <판정> | <판정> |
| 구조 위반 | 0 | 0 |

수정 내용:
- 해요체 규칙에 금지 어미(`-입니다`/`-습니다`/`-ㅂ니다`)를 이름으로 지목하고 대조 예시를 넣음. 칭찬 문맥에서 가장 많이 샜기 때문에 그 상황을 명시적으로 경고.
- `-답니다`/`-랍니다`는 허용으로 결정 — 하십시오체가 아니라 다정한 구어 종결어미. 검사기도 이에 맞춰 넓혔고, 이전 측정의 오탐 18건이 사라짐.
- LEVEL 블록 추가: 5레벨(starter A1 ~ expert C1)에 대해 설명과 제안 문장만 적응시키고 점수는 절대 유지.
- `_shared/korean-error-reference.md`의 8개 오류 계열을 접어 넣고 "prioritize these" 지시 복원. difficulty-bands는 그대로 접으면 서버 SoT(5레벨, expert=C1)와 충돌하므로 dialogue 선례를 따라 재작성.

하네스 쪽 개선(이 작업에서 함께):
- 말투 검출이 문자열 마지막 어미만 보던 것을 문장 단위 스캔으로 교체 — 하십시오체가 앞 문장에 오는 혼용을 전혀 못 잡던 미탐을 해소.
- 말투 위반을 `warn`에서 `error`로 승격하되 `말투 위반` 전용 열로 분리(구조 결함과 서로를 가리지 않게).
- `compareLevelSensitivity` 신설 — 반복 전체를 짝지어 자동 판정. 이전에는 5회 중 1회만 사람이 눈으로 봤다.
EOF
)"
```

세 항목이 모두 해소됐다면 이어서:

```bash
gh issue close 108 --repo jjundev/project-oce-android
```

---

## 실행 후 남는 것

- 말투와 레벨이 **자동으로 실패로 잡히는** 하네스 — 프롬프트를 다시 건드릴 때마다 회귀가 즉시 드러난다
- 전후 숫자로 증명된 프롬프트 개선
- 설계 문서와 서버 상수의 레벨 계약 일치(5토큰)

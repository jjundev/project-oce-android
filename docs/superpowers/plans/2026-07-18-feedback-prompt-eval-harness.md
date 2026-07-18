# Turn Feedback Eval Harness & Temperature Tuning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 턴 피드백(`feedback.slim` / `feedback.deep`)의 출력 품질을 반복 측정할 수 있는 골든셋 + 구조 검증기 + 실 Vertex 호출 러너를 만들고, 그 측정 결과로 지금까지 미설정 상태였던 `temperature` 값을 확정한다.

**Architecture:** 현재 `buildGenerateBody`([functions/src/providers/gemini.ts:563](functions/src/providers/gemini.ts:563))는 `generationConfig`에 `responseMimeType`과 (선택적) `responseSchema`만 넣고 `temperature`를 전혀 설정하지 않는다 — 저장소 전체에서 temperature가 등장하는 유일한 곳은 [docs/design/prompt-system.md:106](docs/design/prompt-system.md:106)의 "가정(needs-you/튜닝)" 한 줄뿐이다. 더 큰 문제는 `buildGenerateBody`에 **단위 테스트가 0건**이라 지금 상태에서 무엇을 바꿔도 회귀를 잡을 안전망이 없다는 것. 그래서 이 계획은 (1) 현재 동작을 고정하는 특성화 테스트를 먼저 깔고, (2) [functions/src/config/models.ts](functions/src/config/models.ts)의 `Record<Task, T>` + `xFor(task)` 패턴을 그대로 복제한 `config/generation.ts`로 태스크별 튜닝을 주입하고, (3) 6개 카테고리 13개 골든 케이스와 기계 검증기를 `src/eval/`에 두고(jest로 오프라인 단위 테스트 가능), (4) 실제 Vertex를 때리는 러너는 `functions/eval/run.js`로 **jest 밖에** 분리한다 — [functions/jest.config.js:1](functions/jest.config.js:1)이 "no emulator, no network"를 계약으로 못박고 있기 때문. 러너는 온도 스윕 × 반복 호출 결과를 마크다운 리포트로 뽑고, 사람이 그 리포트를 읽고 온도를 확정한다.

**Tech Stack:** TypeScript 5.4 (strict), Node 20, Firebase Cloud Functions v5, Jest 29 + ts-jest, ESLint 8. Vertex AI express mode (`aiplatform.googleapis.com`, API-key auth via `x-goog-api-key`), 모델 `gemini-3.1-flash-lite`, 리전 `asia-northeast3`.

## Global Constraints

- **프롬프트 문안은 이 계획에서 수정하지 않는다.** `FEEDBACK_SYSTEM_PROMPT`([gemini.ts:790](functions/src/providers/gemini.ts:790))와 `FEEDBACK_DEEP_SYSTEM_PROMPT`([gemini.ts:913](functions/src/providers/gemini.ts:913))의 문자열 내용은 한 글자도 건드리지 않는다. 측정 기반을 먼저 만드는 것이 이 계획의 전부이고, 문안 수정은 리포트를 본 뒤 별도 계획으로 간다.
- **`functions/test/` 아래에는 네트워크를 타는 테스트를 추가하지 않는다.** [jest.config.js:1](functions/jest.config.js:1)의 "Offline unit tests — no emulator, no network" 계약을 유지한다. 실 호출은 전부 `functions/eval/run.js`에서만.
- **API 키를 파일에 쓰거나 커밋하지 않는다.** `GEMINI_API_KEY`는 환경변수로만 전달한다. 리포트 출력물(`functions/eval/out/`)도 커밋하지 않는다.
- **`temperature` 변경 범위는 `feedback` / `feedbackDeep` 두 태스크로 한정한다.** `dialogue`·`summary`는 eval 커버리지가 없으므로 미설정(프로바이더 기본값)으로 남겨 이 변경이 그들을 회귀시킬 수 없게 한다.
- 모든 새 프로덕션 코드는 `functions/src/` 아래에 두어 `tsc`(`npm run build`)와 `eslint`(`npm run lint`)의 대상이 되게 한다. `functions/tsconfig.json`의 `include`는 `["src"]`이고 `strict: true`, `noUnusedLocals: true`다.
- 커밋 메시지는 Conventional Commits(`feat:`, `test:`, `chore:`, `docs:`)를 따른다.

---

## File Structure

**Create:**
- `functions/src/config/generation.ts` — 태스크 → 생성 파라미터 튜닝 테이블. [config/models.ts](functions/src/config/models.ts)의 구조를 그대로 복제. `FEEDBACK_TEMPERATURE` 단일 상수가 Task 7에서 바뀌는 유일한 지점.
- `functions/src/eval/validate.ts` — 순수 구조 검증기. slim/deep 응답 JSON → `Violation[]`. 네트워크·I/O 없음, 전부 jest로 오프라인 검증 가능.
- `functions/src/eval/cases.ts` — 13개 골든 케이스 데이터(6 카테고리). 순수 데이터 + 타입.
- `functions/eval/run.js` — 실 Vertex 호출 러너(plain JS). `../lib/`에서 빌드 산출물을 require하므로 새 devDependency가 필요 없다. `functions/tsconfig.json`의 `include: ["src"]` 밖이라 `tsc`가 건드리지 않고, `functions/test/` 밖이라 jest가 수집하지 않는다.
- `functions/eval/README.md` — 키 획득·실행·비용을 적는 짧은 문서.
- `functions/test/generation-config.test.ts` — `buildGenerateBody` / `buildRepairBody` / `tuningFor` 단위 테스트.
- `functions/test/eval-harness.test.ts` — 골든셋 정합성 + 검증기 단위 테스트.

**Modify:**
- `functions/src/providers/gemini.ts` — `buildGenerateBody`(563-586)와 `buildRepairBody`(592-615)에 `tuning` 파라미터 추가, 호출부 3곳(`generateStream` 141-143, `generateOnce` 230-233, repair 경로)에서 `tuningFor(req.task)` 전달.
- `functions/package.json` — `eval` 스크립트 추가.
- `functions/.gitignore` — `eval/out/` 추가.
- `docs/design/prompt-system.md:106` — Task 7에서 확정된 temperature로 "needs-you" 가정 라인을 해소.

**Unchanged, relied upon:**
- `functions/src/config/prompts.ts` — 비어 있는 죽은 레지스트리. 프롬프트는 `providers/gemini.ts`에 인라인 상수로 산다는 현실을 그대로 둔다(이관은 이 계획의 범위 밖).
- `functions/src/llm/feedback.ts:210-217`, `functions/src/llm/feedbackDeep.ts:222-229` — `GenerateRequest` 조립부. 튜닝을 `config` 테이블에서 프로바이더가 조회하므로 **오케스트레이터는 수정하지 않는다.**
- `functions/src/config/levels.ts:6` — `LEVEL_TOKENS = ["starter", "easy", "normal", "hard", "expert"]`. 골든 케이스의 `level` 값은 반드시 이 중 하나.

---

## Task 1: `buildGenerateBody` 특성화 테스트 (안전망 먼저)

**Why first:** 프로덕션 코드를 한 줄도 바꾸지 않고 현재 동작을 테스트로 고정한다. Task 3에서 `generationConfig`를 건드릴 때 이 테스트가 회귀 감지기 역할을 한다. 지금은 이 함수에 테스트가 0건이다.

**Files:**
- Create: `functions/test/generation-config.test.ts`

**Interfaces:**
- Consumes: `buildGenerateBody(payload, system?, responseSchema?)`, `buildRepairBody(payload, system, responseSchema, badOutput, parseError)` — 둘 다 [functions/src/providers/gemini.ts](functions/src/providers/gemini.ts)에서 이미 export됨.
- Produces: 없음(테스트만).

- [ ] **Step 1: 현재 동작을 고정하는 테스트를 작성한다**

`functions/test/generation-config.test.ts` 생성:

```ts
import { buildGenerateBody, buildRepairBody } from "../src/providers/gemini";

describe("buildGenerateBody — current behaviour (characterisation)", () => {
  it("wraps the payload as one user text part with role", () => {
    const body = buildGenerateBody({ a: 1 });
    expect(body.contents).toEqual([
      { role: "user", parts: [{ text: '{"a":1}' }] },
    ]);
  });

  it("always requests JSON output", () => {
    const body = buildGenerateBody({});
    expect(body.generationConfig).toEqual({ responseMimeType: "application/json" });
  });

  it("includes responseSchema only when provided", () => {
    const schema = { type: "OBJECT" };
    expect(buildGenerateBody({}, undefined, schema).generationConfig).toEqual({
      responseMimeType: "application/json",
      responseSchema: schema,
    });
    expect(buildGenerateBody({}, undefined, undefined).generationConfig).toEqual({
      responseMimeType: "application/json",
    });
  });

  it("includes systemInstruction only when a non-empty system prompt is given", () => {
    expect(buildGenerateBody({}, "be nice").systemInstruction).toEqual({
      parts: [{ text: "be nice" }],
    });
    expect(buildGenerateBody({}, "").systemInstruction).toBeUndefined();
    expect(buildGenerateBody({}, undefined).systemInstruction).toBeUndefined();
  });

  it("serialises a nullish payload as an empty object", () => {
    expect(buildGenerateBody(undefined).contents).toEqual([
      { role: "user", parts: [{ text: "{}" }] },
    ]);
  });
});

describe("buildRepairBody — current behaviour (characterisation)", () => {
  it("appends the bad output and a repair instruction to the original contents", () => {
    const body = buildRepairBody({ a: 1 }, "sys", { type: "OBJECT" }, "not json", "boom");
    const contents = body.contents as Array<Record<string, unknown>>;
    expect(contents).toHaveLength(3);
    expect(contents[0]).toEqual({ role: "user", parts: [{ text: '{"a":1}' }] });
    expect(contents[1]).toEqual({ role: "model", parts: [{ text: "not json" }] });
    expect(contents[2].role).toBe("user");
    expect(String((contents[2].parts as Array<{ text: string }>)[0].text)).toContain("boom");
  });

  it("carries the original systemInstruction and schema through", () => {
    const schema = { type: "OBJECT" };
    const body = buildRepairBody({}, "sys", schema, "bad", "err");
    expect(body.systemInstruction).toEqual({ parts: [{ text: "sys" }] });
    expect(body.generationConfig).toEqual({
      responseMimeType: "application/json",
      responseSchema: schema,
    });
  });
});
```

- [ ] **Step 2: 테스트를 실행해 전부 통과하는지 확인한다**

Run: `cd functions && npx jest test/generation-config.test.ts`
Expected: PASS — 7 passed. (특성화 테스트이므로 현재 코드에 대해 처음부터 통과해야 정상이다. 하나라도 실패하면 내 가정이 틀린 것이니 실제 코드를 읽고 테스트를 현실에 맞게 고칠 것 — 프로덕션 코드를 고치지 말 것.)

- [ ] **Step 3: 커밋한다**

```bash
git add functions/test/generation-config.test.ts
git commit -m "test(functions): characterise buildGenerateBody/buildRepairBody before tuning"
```

---

## Task 2: `config/generation.ts` — 태스크별 튜닝 테이블

**Why now:** 주입 지점을 만들되 아직 아무도 쓰지 않는 상태로 둔다. 순수 데이터 + 조회 함수라 프로덕션 동작이 변하지 않는다.

**Files:**
- Create: `functions/src/config/generation.ts`
- Modify: `functions/test/generation-config.test.ts` (describe 블록 추가)

**Interfaces:**
- Consumes: `Task` from `functions/src/types/protocol.ts:12` — `"dialogue" | "speaking" | "feedback" | "feedbackDeep" | "summary" | "tts"`.
- Produces:
  - `export interface GenerationTuning { temperature?: number }`
  - `export const FEEDBACK_TEMPERATURE: number`
  - `export const GENERATION_TUNING: Record<Task, GenerationTuning>`
  - `export function tuningFor(task: string): GenerationTuning`

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`functions/test/generation-config.test.ts` 최상단 import에 추가:

```ts
import { tuningFor, GENERATION_TUNING, FEEDBACK_TEMPERATURE } from "../src/config/generation";
```

파일 끝에 describe 블록 추가:

```ts
describe("tuningFor", () => {
  it("gives feedback and feedbackDeep the confirmed feedback temperature", () => {
    expect(tuningFor("feedback")).toEqual({ temperature: FEEDBACK_TEMPERATURE });
    expect(tuningFor("feedbackDeep")).toEqual({ temperature: FEEDBACK_TEMPERATURE });
  });

  it("leaves dialogue, summary, speaking and tts unset (provider default)", () => {
    // Out of scope for this plan — they have no eval coverage, so setting a
    // temperature for them could regress behaviour nothing here measures.
    expect(tuningFor("dialogue")).toEqual({});
    expect(tuningFor("summary")).toEqual({});
    expect(tuningFor("speaking")).toEqual({});
    expect(tuningFor("tts")).toEqual({});
  });

  it("normalises a sub-task id to its family", () => {
    // "summary.expressions" is not in the closed Task map (gemini.ts:203-205).
    expect(tuningFor("summary.expressions")).toEqual(tuningFor("summary"));
  });

  it("falls back to empty tuning for an unknown task", () => {
    expect(tuningFor("nonsense")).toEqual({});
    expect(tuningFor("")).toEqual({});
  });

  it("keeps the feedback temperature within the valid Gemini range", () => {
    expect(FEEDBACK_TEMPERATURE).toBeGreaterThanOrEqual(0);
    expect(FEEDBACK_TEMPERATURE).toBeLessThanOrEqual(2);
  });

  it("covers every task in the closed Task map", () => {
    expect(Object.keys(GENERATION_TUNING).sort()).toEqual(
      ["dialogue", "feedback", "feedbackDeep", "speaking", "summary", "tts"].sort()
    );
  });
});
```

- [ ] **Step 2: 실패를 확인한다**

Run: `cd functions && npx jest test/generation-config.test.ts`
Expected: FAIL — `Cannot find module '../src/config/generation' from 'test/generation-config.test.ts'`

- [ ] **Step 3: 최소 구현을 작성한다**

`functions/src/config/generation.ts` 생성:

```ts
/**
 * task → generation tuning (sampling parameters). Server-only, mirroring
 * config/models.ts so tuning can be swapped without a client release.
 *
 * prompt-system.md:106 left temperature as an explicit `needs-you` assumption
 * (대본 0.8 / 피드백·요약 0.3) and it was never wired — every task ran on Gemini's
 * undocumented default. This table wires it for the feedback family ONLY: those two
 * are the tasks the eval harness (src/eval/) actually measures. dialogue and summary
 * stay unset until they have eval coverage of their own, so this change cannot
 * regress behaviour nothing here observes.
 */
import { Task } from "../types/protocol";

export interface GenerationTuning {
  /** sampling temperature; `undefined` = omit the key entirely (provider default) */
  temperature?: number;
}

/**
 * Slim + deep feedback temperature. Feedback is a GRADING call — the same learner
 * sentence should score the same on a re-run — so it wants low variance, not variety.
 * Confirmed by the temperature sweep in eval/run.js; see docs/design/prompt-system.md §9.
 */
export const FEEDBACK_TEMPERATURE = 0.3;

export const GENERATION_TUNING: Record<Task, GenerationTuning> = {
  dialogue: {},
  speaking: {},
  feedback: { temperature: FEEDBACK_TEMPERATURE },
  feedbackDeep: { temperature: FEEDBACK_TEMPERATURE },
  summary: {},
  tts: {},
};

/**
 * Resolve tuning for a task or sub-task id. Sub-task ids ("summary.expressions") are
 * normalised to their family ("summary") — the closed `Task` map has no entry for them
 * (gemini.ts:203-205). An unknown task falls back to empty tuning, which omits every
 * key and therefore preserves the provider default.
 */
export function tuningFor(task: string): GenerationTuning {
  const family = task.split(".")[0] as Task;
  return GENERATION_TUNING[family] ?? {};
}
```

- [ ] **Step 4: 테스트 통과를 확인한다**

Run: `cd functions && npx jest test/generation-config.test.ts`
Expected: PASS — 13 passed.

- [ ] **Step 5: 빌드와 린트를 확인한다**

Run: `cd functions && npm run build && npm run lint`
Expected: 양쪽 모두 에러 없이 종료(exit 0).

- [ ] **Step 6: 커밋한다**

```bash
git add functions/src/config/generation.ts functions/test/generation-config.test.ts
git commit -m "feat(functions): add per-task generation tuning table"
```

---

## Task 3: 튜닝을 프로바이더에 배선

**Why now:** Task 1의 특성화 테스트가 안전망으로 깔려 있으므로 이제 `generationConfig`를 안전하게 건드릴 수 있다. 여기서 처음으로 프로덕션 동작이 바뀐다(피드백 호출에 `temperature: 0.3`이 실림).

**Files:**
- Modify: `functions/src/providers/gemini.ts` — `buildGenerateBody`(567-586), `buildRepairBody`(592-615), 호출부 3곳: `generateStream`(141-143), `generateOnce`의 `buildGenerateBody` 호출(230), repair 경로(237-244). **줄번호는 안내용이다 — 아래 스텝이 주는 리터럴 코드로 위치를 찾을 것.**
- Modify: `functions/test/generation-config.test.ts`

**Interfaces:**
- Consumes: `GenerationTuning`, `tuningFor` (Task 2).
- Produces:
  - `buildGenerateBody(payload: unknown, system?: string, responseSchema?: unknown, tuning?: GenerationTuning): Record<string, unknown>`
  - `buildRepairBody(payload: unknown, system: string | undefined, responseSchema: unknown, badOutput: string, parseError: string, tuning?: GenerationTuning): Record<string, unknown>`
  - 두 함수 모두 `tuning`이 생략되면 Task 1의 특성화 테스트와 **동일한 바디**를 만든다(하위 호환).

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`functions/test/generation-config.test.ts`의 `describe("buildGenerateBody — current behaviour...")` 블록 **안**에 다음 it을 추가:

```ts
  it("adds temperature only when tuning provides it", () => {
    expect(buildGenerateBody({}, undefined, undefined, { temperature: 0.3 }).generationConfig)
      .toEqual({ responseMimeType: "application/json", temperature: 0.3 });
    // an empty tuning object must not introduce the key at all
    expect(buildGenerateBody({}, undefined, undefined, {}).generationConfig)
      .toEqual({ responseMimeType: "application/json" });
    expect(buildGenerateBody({}, undefined, undefined, undefined).generationConfig)
      .toEqual({ responseMimeType: "application/json" });
  });

  it("keeps temperature 0 — a falsy but meaningful value", () => {
    expect(buildGenerateBody({}, undefined, undefined, { temperature: 0 }).generationConfig)
      .toEqual({ responseMimeType: "application/json", temperature: 0 });
  });
```

그리고 `describe("buildRepairBody — current behaviour...")` 블록 안에 추가:

```ts
  it("carries tuning through to the repair attempt", () => {
    const body = buildRepairBody({}, "sys", undefined, "bad", "err", { temperature: 0.3 });
    expect(body.generationConfig).toEqual({
      responseMimeType: "application/json",
      temperature: 0.3,
    });
  });
```

- [ ] **Step 2: 실패를 확인한다**

Run: `cd functions && npx jest test/generation-config.test.ts`
Expected: FAIL — `temperature` 키가 없어서 `toEqual` 불일치 3건. (TypeScript 컴파일 에러로 먼저 죽을 수도 있다: `Expected 1-3 arguments, but got 4`. 둘 중 어느 쪽이든 실패면 정상.)

- [ ] **Step 3: `buildGenerateBody`와 `buildRepairBody`를 수정한다**

`functions/src/providers/gemini.ts`의 import 블록에 추가(기존 `import { modelFor } from "../config/models";` 근처):

```ts
import { GenerationTuning, tuningFor } from "../config/generation";
```

`buildGenerateBody`(563-586)를 다음으로 교체:

```ts
/**
 * Build a Gemini `:generateContent` body for structured JSON output (M2-01). The payload
 * slice is sent as one user text part; the resolved prompt goes in `systemInstruction`.
 * `tuning` carries sampling parameters (config/generation.ts); an omitted or empty tuning
 * leaves `generationConfig` exactly as it was before tuning existed, so the provider
 * default still applies to every task that opts out.
 */
export function buildGenerateBody(
  payload: unknown,
  system?: string,
  responseSchema?: unknown,
  tuning?: GenerationTuning
): Record<string, unknown> {
  const generationConfig: Record<string, unknown> = {
    responseMimeType: "application/json",
  };
  if (responseSchema !== undefined) {
    generationConfig.responseSchema = responseSchema;
  }
  // explicit undefined check — temperature 0 is meaningful and must survive
  if (tuning?.temperature !== undefined) {
    generationConfig.temperature = tuning.temperature;
  }
  const body: Record<string, unknown> = {
    contents: [{ role: "user", parts: [{ text: JSON.stringify(payload ?? {}) }] }],
    generationConfig,
  };
  if (system) {
    body.systemInstruction = { parts: [{ text: system }] };
  }
  return body;
}
```

`buildRepairBody`(592-615)의 시그니처와 첫 줄을 교체:

```ts
export function buildRepairBody(
  payload: unknown,
  system: string | undefined,
  responseSchema: unknown,
  badOutput: string,
  parseError: string,
  tuning?: GenerationTuning
): Record<string, unknown> {
  const body = buildGenerateBody(payload, system, responseSchema, tuning);
```

(`body.contents.push(...)` 이하 나머지는 그대로 둔다.)

- [ ] **Step 4: 호출부 3곳에 튜닝을 전달한다**

`generateStream`(141-143):

```ts
    const body = JSON.stringify(
      buildGenerateBody(req.payload, req.system, req.responseSchema, tuningFor(req.task))
    );
```

`generateOnce`의 summary 경로 — `const firstText = await this.requestText(` 로 시작하는 구문(230 부근):

```ts
    const firstText = await this.requestText(
      url,
      buildGenerateBody(req.payload, req.system, req.responseSchema, tuningFor(req.task))
    );
```

같은 `generateOnce` 안의 repair 경로 — `buildRepairBody(` 호출에 6번째 인자를 추가한다. 인자 순서는 `payload, system, responseSchema, badOutput, parseError, tuning`이므로 기존 마지막 인자(`parseError`) 뒤에 `tuningFor(req.task)`를 붙인다:

```ts
        buildRepairBody(
          req.payload,
          req.system,
          req.responseSchema,
          firstText,
          String(parseError),
          tuningFor(req.task)
        )
```

주의: 실제 `firstText`/`parseError` 인자 표현은 파일에 있는 그대로 유지하고 **6번째 인자만 추가**할 것. 기존 인자를 다시 쓰지 말 것.

- [ ] **Step 5: 전체 테스트와 빌드를 실행한다**

Run: `cd functions && npx jest && npm run build && npm run lint`
Expected: jest 전체 통과(기존 22개 스위트 + 신규 1개 = 23개). 특히 `dialogue-handler.test.ts`, `feedback-handler.test.ts`, `summary.test.ts`가 통과해야 한다 — 그들은 `LlmProvider` 인터페이스 레벨 stub을 쓰므로 이 변경의 영향을 받지 않는 것이 정상이다. `npm run build`, `npm run lint` 모두 exit 0.

- [ ] **Step 6: 커밋한다**

```bash
git add functions/src/providers/gemini.ts functions/test/generation-config.test.ts
git commit -m "feat(functions): wire per-task temperature into generateContent bodies"
```

---

## Task 4: 골든 케이스 세트

**Why now:** 검증기(Task 5)와 러너(Task 6)가 둘 다 이 데이터를 소비한다. 데이터를 먼저 확정한다.

**Files:**
- Create: `functions/src/eval/cases.ts`
- Create: `functions/test/eval-harness.test.ts`

**Interfaces:**
- Consumes: `FeedbackRequestPayload` from `functions/src/llm/feedback.ts:46` — `{ koreanPrompt: string; userEnglish: string; referenceEnglish: string; level: string }`. **`import type`으로 가져올 것** — 값으로 import하면 빌드 산출물이 오케스트레이터 모듈 전체를 끌어온다.
- Produces:
  - `export type EvalCategory = "typo-grammar" | "konglish" | "awkward-but-correct" | "already-good" | "minimal-input" | "level-variance"`
  - `export interface EvalCase { id: string; category: EvalCategory; note: string; payload: FeedbackRequestPayload; expect: CaseExpectation }`
  - `export const CASES: readonly EvalCase[]` — 13개
  - `CaseExpectation`은 Task 5의 `validate.ts`가 소유하고 `cases.ts`가 import한다.

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`functions/test/eval-harness.test.ts` 생성:

```ts
import { CASES, EvalCategory } from "../src/eval/cases";
import { parseFeedbackPayload } from "../src/llm/feedback";
import { LEVEL_TOKENS } from "../src/config/levels";

describe("golden case set", () => {
  it("has 13 cases", () => {
    expect(CASES).toHaveLength(13);
  });

  it("gives every case a unique id", () => {
    const ids = CASES.map((c) => c.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it("covers all six categories with at least two cases each", () => {
    const categories: EvalCategory[] = [
      "typo-grammar",
      "konglish",
      "awkward-but-correct",
      "already-good",
      "minimal-input",
      "level-variance",
    ];
    for (const cat of categories) {
      expect(CASES.filter((c) => c.category === cat).length).toBeGreaterThanOrEqual(2);
    }
  });

  it("uses only real level tokens", () => {
    for (const c of CASES) {
      expect(LEVEL_TOKENS).toContain(c.payload.level);
    }
  });

  it("survives the production payload parser unchanged", () => {
    // If a case is malformed the parser silently rewrites it (e.g. an unknown level
    // becomes "normal"), which would make the eval measure something other than the
    // case as written. Round-tripping catches that.
    for (const c of CASES) {
      expect(parseFeedbackPayload(c.payload)).toEqual(c.payload);
    }
  });

  it("explains what each case probes", () => {
    for (const c of CASES) {
      expect(c.note.trim().length).toBeGreaterThan(0);
    }
  });

  it("pairs the level-variance cases on identical English", () => {
    // The pair exists to isolate `level` as the only variable — the prompt never
    // mentions `level`, so identical output across the pair is the finding.
    const pair = CASES.filter((c) => c.category === "level-variance");
    expect(pair).toHaveLength(2);
    expect(pair[0].payload.userEnglish).toBe(pair[1].payload.userEnglish);
    expect(pair[0].payload.koreanPrompt).toBe(pair[1].payload.koreanPrompt);
    expect(pair[0].payload.level).not.toBe(pair[1].payload.level);
  });
});
```

- [ ] **Step 2: 실패를 확인한다**

Run: `cd functions && npx jest test/eval-harness.test.ts`
Expected: FAIL — `Cannot find module '../src/eval/cases'`

- [ ] **Step 3: 케이스 세트를 작성한다**

`functions/src/eval/cases.ts` 생성:

```ts
/**
 * Golden case set for the turn-feedback eval harness.
 *
 * Six categories, 13 cases. Each probes a distinct way a Korean learner's English can
 * be wrong (or right), because the failure modes need different things from the model:
 * catching a tense slip is not the same skill as declining to "fix" a sentence that is
 * already native-sounding. `expect` holds only what is MECHANICALLY checkable — whether
 * the correction is pedagogically good is a human call, made against the markdown report
 * that eval/run.js emits.
 *
 * Pure data. No network, no I/O — test/eval-harness.test.ts validates it offline.
 */
import type { FeedbackRequestPayload } from "../llm/feedback";
import { CaseExpectation } from "./validate";

export type EvalCategory =
  | "typo-grammar"
  | "konglish"
  | "awkward-but-correct"
  | "already-good"
  | "minimal-input"
  | "level-variance";

export interface EvalCase {
  /** stable id — used by `--only=` and as the report anchor */
  id: string;
  category: EvalCategory;
  /** what this case probes, in Korean — printed in the report to frame human review */
  note: string;
  payload: FeedbackRequestPayload;
  expect: CaseExpectation;
}

export const CASES: readonly EvalCase[] = [
  // ── typo-grammar — plain mistakes the model MUST catch ──────────────────────
  {
    id: "tg-past-tense",
    category: "typo-grammar",
    note: "과거 시제 오류(meet → met). 가장 기본적인 문법 실수를 잡는지.",
    payload: {
      koreanPrompt: "어제 친구를 만났어요.",
      userEnglish: "I meet my friend yesterday.",
      referenceEnglish: "I met my friend yesterday.",
      level: "normal",
    },
    expect: { requiresIncorrectSegments: true, scoreMax: 89 },
  },
  {
    id: "tg-typo-redundant",
    category: "typo-grammar",
    note: "오타(moring)와 중복 표현(every morning ... in the morning)이 함께 있는 경우.",
    payload: {
      koreanPrompt: "저는 매일 아침에 커피를 마셔요.",
      userEnglish: "I drink coffee every morning in the moring.",
      referenceEnglish: "I drink coffee every morning.",
      level: "normal",
    },
    expect: { requiresIncorrectSegments: true, scoreMax: 89 },
  },

  // ── konglish — grammatical but not what a native would say ──────────────────
  {
    id: "kl-hand-phone",
    category: "konglish",
    note: "'핸드폰'의 직역(hand phone). 콩글리시 어휘를 잡아내는지.",
    payload: {
      koreanPrompt: "핸드폰 충전기 좀 빌릴 수 있을까요?",
      userEnglish: "Can I borrow your hand phone charger?",
      referenceEnglish: "Could I borrow your phone charger?",
      level: "normal",
    },
    expect: { requiresIncorrectSegments: true },
  },
  {
    id: "kl-condition",
    category: "konglish",
    note: "'컨디션'의 오용. 영어 condition은 몸 상태를 뜻하지 않는다.",
    payload: {
      koreanPrompt: "저는 오늘 컨디션이 안 좋아요.",
      userEnglish: "My condition is not good today.",
      referenceEnglish: "I'm not feeling well today.",
      level: "normal",
    },
    expect: {},
  },
  {
    id: "kl-eat-rice",
    category: "konglish",
    note: "'밥 먹다'의 직역(eat rice). 문법은 완벽해서 grammar 섹션이 아니라 naturalExpression이 일해야 하는 케이스.",
    payload: {
      koreanPrompt: "다음에 밥 한번 먹어요.",
      userEnglish: "Let's eat rice next time.",
      referenceEnglish: "Let's grab a meal sometime.",
      level: "normal",
    },
    expect: {},
  },

  // ── awkward-but-correct — nothing to "correct", plenty to improve ───────────
  {
    id: "ab-possible-to-sit",
    category: "awkward-but-correct",
    note: "문법적으로 흠이 없지만 원어민은 이렇게 말하지 않는다. 과잉 문법 교정 없이 자연스러움만 다뤄야 한다.",
    payload: {
      koreanPrompt: "이 자리에 앉아도 될까요?",
      userEnglish: "Is it possible for me to sit on this seat?",
      referenceEnglish: "Is this seat taken?",
      level: "normal",
    },
    expect: {},
  },
  {
    id: "ab-move-meeting",
    category: "awkward-but-correct",
    note: "'the next week'의 불필요한 관사. 거의 맞는 문장에서 미세한 차이를 잡아내는지.",
    payload: {
      koreanPrompt: "회의를 다음 주로 미룰 수 있을까요?",
      userEnglish: "Can we move the meeting to the next week?",
      referenceEnglish: "Could we push the meeting to next week?",
      level: "normal",
    },
    expect: {},
  },

  // ── already-good — the over-correction trap ─────────────────────────────────
  {
    id: "ag-coffee-please",
    category: "already-good",
    note: "이미 자연스럽고 정중한 문장. 고칠 것이 없는데도 억지 교정을 만들어내는지 확인한다.",
    payload: {
      koreanPrompt: "커피 한 잔 주시겠어요?",
      userEnglish: "Could I get a coffee, please?",
      referenceEnglish: "Can I get a coffee?",
      level: "normal",
    },
    expect: { noIncorrectSegments: true, scoreMin: 85 },
  },
  {
    id: "ag-thank-you",
    category: "already-good",
    note: "완벽한 문장. 프롬프트의 '이미 훌륭하면 축하하라' 규칙이 실제로 지켜지는지.",
    payload: {
      koreanPrompt: "도와주셔서 정말 감사합니다.",
      userEnglish: "Thank you so much for your help.",
      referenceEnglish: "Thanks a lot for your help.",
      level: "normal",
    },
    expect: { noIncorrectSegments: true, scoreMin: 90 },
  },

  // ── minimal-input — can the model stay honest with almost nothing to go on ──
  {
    id: "mi-one-word",
    category: "minimal-input",
    note: "한 단어 입력. 의미는 전달되지만 문장이 아니다. 점수와 교정이 폭주하지 않는지.",
    payload: {
      koreanPrompt: "커피 주세요.",
      userEnglish: "Coffee.",
      referenceEnglish: "Can I get a coffee?",
      level: "normal",
    },
    expect: {},
  },
  {
    id: "mi-broken-fragment",
    category: "minimal-input",
    note: "의미를 알 수 없는 파편. 모델이 근거 없이 학습자 의도를 지어내는지 확인하는 케이스.",
    payload: {
      koreanPrompt: "그건 좀 그래요.",
      userEnglish: "That is little bit.",
      referenceEnglish: "That doesn't quite work for me.",
      level: "normal",
    },
    expect: { requiresIncorrectSegments: true },
  },

  // ── level-variance — identical English, different level ─────────────────────
  // FEEDBACK_SYSTEM_PROMPT never mentions `level`; it only rides along in the payload
  // JSON. These two exist to make that visible: near-identical output across the pair
  // is evidence that level-aware grading is unimplemented, not that it failed.
  {
    id: "lv-starter",
    category: "level-variance",
    note: "level=starter. 아래 lv-expert와 영어 문장이 동일하다 — level만 다르다.",
    payload: {
      koreanPrompt: "주말에 뭐 했어요?",
      userEnglish: "What you do weekend?",
      referenceEnglish: "What did you do over the weekend?",
      level: "starter",
    },
    expect: { requiresIncorrectSegments: true },
  },
  {
    id: "lv-expert",
    category: "level-variance",
    note: "level=expert. 위 lv-starter와 영어 문장이 동일하다 — level만 다르다.",
    payload: {
      koreanPrompt: "주말에 뭐 했어요?",
      userEnglish: "What you do weekend?",
      referenceEnglish: "What did you do over the weekend?",
      level: "expert",
    },
    expect: { requiresIncorrectSegments: true },
  },
];
```

- [ ] **Step 4: 아직 실패하는지 확인한다**

Run: `cd functions && npx jest test/eval-harness.test.ts`
Expected: FAIL — `Cannot find module './validate'` (Task 5에서 만든다). 이 실패는 예상된 것이다.

- [ ] **Step 5: 커밋하지 않고 Task 5로 넘어간다**

`cases.ts`는 `validate.ts` 없이는 컴파일되지 않는다. Task 5 종료 시 함께 커밋한다.

---

## Task 5: 구조 검증기

**Why now:** `cases.ts`가 `CaseExpectation`을 여기서 가져오고, 러너가 이 검증기를 호출한다. 순수 함수라 전부 오프라인 테스트 가능하다.

**Files:**
- Create: `functions/src/eval/validate.ts`
- Modify: `functions/test/eval-harness.test.ts`

**Interfaces:**
- Consumes: 없음(순수 함수, 외부 의존 없음).
- Produces:
  - `export interface CaseExpectation { scoreMin?: number; scoreMax?: number; noIncorrectSegments?: boolean; requiresIncorrectSegments?: boolean }`
  - `export interface Violation { severity: "error" | "warn"; check: string; detail: string }`
  - `export function validateSlim(json: unknown, expect?: CaseExpectation): Violation[]`
  - `export function validateDeep(json: unknown): Violation[]`
  - `export function scoreOf(json: unknown): number | null`
  - `export function countIncorrectSegments(json: unknown): number`

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`functions/test/eval-harness.test.ts` 상단 import에 추가:

```ts
import {
  validateSlim,
  validateDeep,
  scoreOf,
  countIncorrectSegments,
} from "../src/eval/validate";
```

파일 끝에 추가:

```ts
const GOOD_SLIM = {
  writingScore: { score: 85, encouragementMessage: "정말 잘했어요!" },
  grammar: {
    correctedSentence: {
      segments: [
        { text: "I ", type: "normal" },
        { text: "meet", type: "incorrect" },
        { text: "met", type: "correction" },
        { text: " my friend.", type: "normal" },
      ],
    },
    explanation: "지난 일을 말할 때는 met을 써요.",
  },
  naturalExpression: {
    segments: [
      { text: "I ", type: "normal" },
      { text: "caught up with", type: "highlight" },
      { text: " my friend.", type: "normal" },
    ],
    reason: { keyword: "caught up with", description: "친구를 만났다는 느낌이 더 살아나요." },
  },
};

/** deep-clone so a mutation in one test cannot leak into the next */
function clone<T>(o: T): T {
  return JSON.parse(JSON.stringify(o)) as T;
}

describe("validateSlim", () => {
  it("passes a well-formed response", () => {
    expect(validateSlim(GOOD_SLIM)).toEqual([]);
  });

  it("rejects a non-object", () => {
    expect(validateSlim(null).some((v) => v.check === "shape")).toBe(true);
    expect(validateSlim([]).some((v) => v.check === "shape")).toBe(true);
    expect(validateSlim("{}").some((v) => v.check === "shape")).toBe(true);
  });

  it("rejects a score outside 0-100", () => {
    const bad = clone(GOOD_SLIM);
    bad.writingScore.score = 120;
    expect(validateSlim(bad).some((v) => v.check === "writingScore.score")).toBe(true);
  });

  it("rejects a non-integer score", () => {
    const bad = clone(GOOD_SLIM);
    bad.writingScore.score = 85.5;
    expect(validateSlim(bad).some((v) => v.check === "writingScore.score")).toBe(true);
  });

  it("rejects an unknown segment type", () => {
    const bad = clone(GOOD_SLIM);
    bad.grammar.correctedSentence.segments[0].type = "bogus";
    expect(validateSlim(bad).some((v) => v.check === "grammar.segments")).toBe(true);
  });

  it("rejects `correction` in naturalExpression — only normal|highlight are allowed there", () => {
    const bad = clone(GOOD_SLIM);
    bad.naturalExpression.segments[1].type = "correction";
    expect(validateSlim(bad).some((v) => v.check === "naturalExpression.segments")).toBe(true);
  });

  it("rejects any colour — the client derives it from the score", () => {
    const bad = {
      ...clone(GOOD_SLIM),
      writingScore: { ...GOOD_SLIM.writingScore, color: "#FF0000" },
    };
    const violations = validateSlim(bad);
    expect(violations.filter((v) => v.check === "no-color").length).toBeGreaterThan(0);
  });

  it("rejects a non-Korean learner-facing string", () => {
    const bad = clone(GOOD_SLIM);
    bad.grammar.explanation = "Use the past tense here.";
    expect(validateSlim(bad).some((v) => v.check === "grammar.explanation")).toBe(true);
  });

  it("warns — but does not fail — on a Korean line that is not 해요체", () => {
    const bad = clone(GOOD_SLIM);
    bad.writingScore.encouragementMessage = "훌륭하다";
    const violations = validateSlim(bad);
    expect(violations.some((v) => v.check === "haeyo" && v.severity === "warn")).toBe(true);
    expect(violations.some((v) => v.severity === "error")).toBe(false);
  });

  it("flags over-correction when the case expects none", () => {
    // GOOD_SLIM has one `incorrect` segment
    const violations = validateSlim(GOOD_SLIM, { noIncorrectSegments: true });
    expect(violations.some((v) => v.check === "expect.noIncorrectSegments")).toBe(true);
  });

  it("flags a missed error when the case requires one", () => {
    const clean = clone(GOOD_SLIM);
    clean.grammar.correctedSentence.segments = [{ text: "I met my friend.", type: "normal" }];
    const violations = validateSlim(clean, { requiresIncorrectSegments: true });
    expect(violations.some((v) => v.check === "expect.requiresIncorrectSegments")).toBe(true);
  });

  it("enforces the score bounds a case declares", () => {
    expect(validateSlim(GOOD_SLIM, { scoreMin: 90 }).some((v) => v.check === "expect.scoreMin")).toBe(true);
    expect(validateSlim(GOOD_SLIM, { scoreMax: 80 }).some((v) => v.check === "expect.scoreMax")).toBe(true);
    expect(validateSlim(GOOD_SLIM, { scoreMin: 80, scoreMax: 90 })).toEqual([]);
  });
});

describe("scoreOf / countIncorrectSegments", () => {
  it("reads the score, or null when absent", () => {
    expect(scoreOf(GOOD_SLIM)).toBe(85);
    expect(scoreOf({})).toBeNull();
    expect(scoreOf(null)).toBeNull();
  });

  it("counts incorrect segments", () => {
    expect(countIncorrectSegments(GOOD_SLIM)).toBe(1);
    expect(countIncorrectSegments({})).toBe(0);
  });
});

const GOOD_DEEP = {
  conceptualBridge: {
    literalTranslation: "저는 밥을 먹어요.",
    explanation: "의도와 실제 의미가 조금 달라요.",
    venn: {
      guide: "두 단어의 쓰임을 비교해 봐요.",
      leftCircle: { word: "eat rice", items: ["밥을 먹다"] },
      rightCircle: { word: "grab a meal", items: ["식사하다", "가볍게 만나다"] },
      intersection: { items: ["먹다"] },
    },
  },
  toneStyle: {
    defaultLevel: 2,
    levels: [
      { level: 0, sentence: "Might we dine together?", sentenceTranslation: "함께 식사하시겠어요?" },
      { level: 1, sentence: "Would you like to have a meal?", sentenceTranslation: "식사 한번 하실래요?" },
      { level: 2, sentence: "Let's grab a meal sometime.", sentenceTranslation: "언제 밥 한번 먹어요." },
      { level: 3, sentence: "Let's get food sometime.", sentenceTranslation: "언제 밥 먹자." },
      { level: 4, sentence: "Yo, food sometime?", sentenceTranslation: "야, 언제 밥?" },
    ],
  },
  paraphrasing: [
    { level: 1, label: "Beginner", sentence: "Let's eat together.", sentenceTranslation: "같이 먹어요." },
    { level: 2, label: "Intermediate", sentence: "Let's grab a meal.", sentenceTranslation: "밥 한번 먹어요." },
    { level: 3, label: "Advanced", sentence: "We should catch up over a meal.", sentenceTranslation: "밥 먹으면서 얘기해요." },
  ],
};

describe("validateDeep", () => {
  it("passes a well-formed response", () => {
    expect(validateDeep(GOOD_DEEP)).toEqual([]);
  });

  it("requires exactly five tone levels", () => {
    // The schema does NOT enforce this (no minItems/maxItems) — only the prompt asks
    // for it, so it is exactly the kind of thing a temperature change can break.
    const bad = clone(GOOD_DEEP);
    bad.toneStyle.levels = bad.toneStyle.levels.slice(0, 4);
    expect(validateDeep(bad).some((v) => v.check === "toneStyle.levels")).toBe(true);
  });

  it("requires tone levels 0 through 4", () => {
    const bad = clone(GOOD_DEEP);
    bad.toneStyle.levels[4].level = 9;
    expect(validateDeep(bad).some((v) => v.check === "toneStyle.levels")).toBe(true);
  });

  it("requires exactly three paraphrases at levels 1-3", () => {
    const bad = clone(GOOD_DEEP);
    bad.paraphrasing = bad.paraphrasing.slice(0, 2);
    expect(validateDeep(bad).some((v) => v.check === "paraphrasing")).toBe(true);
  });

  it("requires a non-empty Korean translation on every sentence", () => {
    const bad = clone(GOOD_DEEP);
    bad.toneStyle.levels[0].sentenceTranslation = "";
    expect(validateDeep(bad).some((v) => v.check === "toneStyle.levels")).toBe(true);
  });

  it("rejects a venn item longer than four words", () => {
    const bad = clone(GOOD_DEEP);
    bad.conceptualBridge.venn.leftCircle.items = [
      "물건을 사고 받은 증명서를 건네줄 때 쓰는 표현",
    ];
    expect(validateDeep(bad).some((v) => v.check === "venn.items")).toBe(true);
  });

  it("rejects any colour in deep output", () => {
    const bad = clone(GOOD_DEEP);
    // cast at the leaf only — the surrounding object keeps its inferred type
    (bad.conceptualBridge.venn.leftCircle as Record<string, unknown>).color = "#00FF00";
    expect(validateDeep(bad).some((v) => v.check === "no-color")).toBe(true);
  });
});
```

- [ ] **Step 2: 실패를 확인한다**

Run: `cd functions && npx jest test/eval-harness.test.ts`
Expected: FAIL — `Cannot find module '../src/eval/validate'`

- [ ] **Step 3: 검증기를 구현한다**

`functions/src/eval/validate.ts` 생성:

```ts
/**
 * Structural validators for turn-feedback model output.
 *
 * These check only what is MECHANICALLY decidable — schema shape, enum membership,
 * cardinality, script (Hangul), and the absence of colours. Whether a correction is
 * pedagogically right is a human call; eval/run.js renders those into a markdown
 * report for review. Keeping the two apart is the point: a machine gate that tried
 * to judge teaching quality would be wrong often enough to be ignored.
 *
 * Pure functions — no network, no I/O, no imports. Fully unit-tested offline.
 */

/** what a golden case asserts beyond generic structural validity */
export interface CaseExpectation {
  scoreMin?: number;
  scoreMax?: number;
  /** the sentence is already good — marking anything `incorrect` is over-correction */
  noIncorrectSegments?: boolean;
  /** the sentence has a real error — failing to mark it is a miss */
  requiresIncorrectSegments?: boolean;
}

export interface Violation {
  severity: "error" | "warn";
  /** stable machine-readable id, e.g. "writingScore.score" — grouped in the report */
  check: string;
  detail: string;
}

const HANGUL = /[가-힣]/;
const HEX_COLOR = /#[0-9a-fA-F]{3,8}\b/;
const COLOR_KEY = /"colou?r"\s*:/i;
const GRAMMAR_SEGMENT_TYPES = new Set(["normal", "incorrect", "correction", "highlight"]);
const NATURAL_SEGMENT_TYPES = new Set(["normal", "highlight"]);
/** feedback-deep.md: venn items are single words or short phrases, never sentences */
const MAX_VENN_ITEM_WORDS = 4;

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === "object" && v !== null && !Array.isArray(v);
}

/** 해요체 heuristic — 해요체 lines end in 요/죠 once terminal punctuation is stripped. */
function endsHaeyo(s: string): boolean {
  return /[요죠]$/u.test(s.trim().replace(/[!?.…~\s]+$/u, ""));
}

function makeCollector(): { out: Violation[]; err: (c: string, d: string) => void; warn: (c: string, d: string) => void } {
  const out: Violation[] = [];
  return {
    out,
    err: (check, detail) => out.push({ severity: "error", check, detail }),
    warn: (check, detail) => out.push({ severity: "warn", check, detail }),
  };
}

/** Colours are computed client-side (feedback-slim.md, feedback-deep.md) — the model must emit none. */
function checkNoColor(root: Record<string, unknown>, err: (c: string, d: string) => void): void {
  const serialized = JSON.stringify(root);
  const hex = HEX_COLOR.exec(serialized);
  if (hex) {
    err("no-color", `hex colour in output: ${hex[0]}`);
  }
  if (COLOR_KEY.test(serialized)) {
    err("no-color", "a colour key is present in output");
  }
}

/** Learner-facing Korean: non-empty, actually Hangul, and (softly) 해요체. */
function checkKoreanString(
  value: unknown,
  check: string,
  err: (c: string, d: string) => void,
  warn: (c: string, d: string) => void
): void {
  if (typeof value !== "string" || value.trim() === "") {
    err(check, "missing or empty");
    return;
  }
  if (!HANGUL.test(value)) {
    err(check, `not Korean: ${value}`);
    return;
  }
  if (!endsHaeyo(value)) {
    warn("haeyo", `${check} may not be 해요체: ${value}`);
  }
}

function checkSegments(
  segments: unknown,
  check: string,
  allowed: Set<string>,
  err: (c: string, d: string) => void
): void {
  if (!Array.isArray(segments) || segments.length === 0) {
    err(check, "missing or empty");
    return;
  }
  segments.forEach((s, i) => {
    if (!isRecord(s)) {
      err(check, `[${i}] is not an object`);
      return;
    }
    if (typeof s.text !== "string") {
      err(check, `[${i}].text is not a string`);
    }
    if (typeof s.type !== "string" || !allowed.has(s.type)) {
      err(check, `[${i}].type invalid: ${JSON.stringify(s.type)}`);
    }
  });
}

export function scoreOf(json: unknown): number | null {
  if (!isRecord(json)) return null;
  const ws = json.writingScore;
  if (!isRecord(ws) || typeof ws.score !== "number") return null;
  return ws.score;
}

export function countIncorrectSegments(json: unknown): number {
  if (!isRecord(json)) return 0;
  const grammar = json.grammar;
  if (!isRecord(grammar)) return 0;
  const cs = grammar.correctedSentence;
  if (!isRecord(cs) || !Array.isArray(cs.segments)) return 0;
  return cs.segments.filter((s) => isRecord(s) && s.type === "incorrect").length;
}

export function validateSlim(json: unknown, expected: CaseExpectation = {}): Violation[] {
  const { out, err, warn } = makeCollector();
  if (!isRecord(json)) {
    err("shape", "top level is not a JSON object");
    return out;
  }
  checkNoColor(json, err);

  // ── writingScore ──────────────────────────────────────────────────────────
  const ws = json.writingScore;
  if (!isRecord(ws)) {
    err("writingScore", "missing");
  } else {
    const score = ws.score;
    if (typeof score !== "number" || !Number.isInteger(score)) {
      err("writingScore.score", `not an integer: ${JSON.stringify(score)}`);
    } else {
      if (score < 0 || score > 100) {
        err("writingScore.score", `outside 0-100: ${score}`);
      }
      if (expected.scoreMin !== undefined && score < expected.scoreMin) {
        err("expect.scoreMin", `score ${score} below expected minimum ${expected.scoreMin}`);
      }
      if (expected.scoreMax !== undefined && score > expected.scoreMax) {
        err("expect.scoreMax", `score ${score} above expected maximum ${expected.scoreMax}`);
      }
    }
    checkKoreanString(ws.encouragementMessage, "writingScore.encouragementMessage", err, warn);
  }

  // ── grammar ───────────────────────────────────────────────────────────────
  const grammar = json.grammar;
  if (!isRecord(grammar)) {
    err("grammar", "missing");
  } else {
    const cs = grammar.correctedSentence;
    if (!isRecord(cs)) {
      err("grammar.correctedSentence", "missing");
    } else {
      checkSegments(cs.segments, "grammar.segments", GRAMMAR_SEGMENT_TYPES, err);
    }
    checkKoreanString(grammar.explanation, "grammar.explanation", err, warn);
  }

  const incorrect = countIncorrectSegments(json);
  if (expected.noIncorrectSegments && incorrect > 0) {
    err(
      "expect.noIncorrectSegments",
      `over-correction: ${incorrect} incorrect segment(s) on a sentence that was already good`
    );
  }
  if (expected.requiresIncorrectSegments && incorrect === 0) {
    err("expect.requiresIncorrectSegments", "the learner's error was not marked incorrect");
  }

  // ── naturalExpression ─────────────────────────────────────────────────────
  const ne = json.naturalExpression;
  if (!isRecord(ne)) {
    err("naturalExpression", "missing");
  } else {
    checkSegments(ne.segments, "naturalExpression.segments", NATURAL_SEGMENT_TYPES, err);
    const reason = ne.reason;
    if (!isRecord(reason)) {
      err("naturalExpression.reason", "missing");
    } else {
      if (typeof reason.keyword !== "string" || reason.keyword.trim() === "") {
        err("naturalExpression.reason.keyword", "missing or empty");
      }
      checkKoreanString(reason.description, "naturalExpression.reason.description", err, warn);
    }
  }

  return out;
}

export function validateDeep(json: unknown): Violation[] {
  const { out, err, warn } = makeCollector();
  if (!isRecord(json)) {
    err("shape", "top level is not a JSON object");
    return out;
  }
  checkNoColor(json, err);

  // ── conceptualBridge ──────────────────────────────────────────────────────
  const cb = json.conceptualBridge;
  if (!isRecord(cb)) {
    err("conceptualBridge", "missing");
  } else {
    checkKoreanString(cb.literalTranslation, "conceptualBridge.literalTranslation", err, warn);
    checkKoreanString(cb.explanation, "conceptualBridge.explanation", err, warn);
    const venn = cb.venn;
    if (!isRecord(venn)) {
      err("venn", "missing");
    } else {
      if (typeof venn.guide !== "string" || venn.guide.trim() === "") {
        err("venn.guide", "missing or empty");
      }
      for (const side of ["leftCircle", "rightCircle"] as const) {
        const circle = venn[side];
        if (!isRecord(circle)) {
          err(`venn.${side}`, "missing");
          continue;
        }
        if (typeof circle.word !== "string" || circle.word.trim() === "") {
          err(`venn.${side}`, "word missing or empty");
        }
        checkVennItems(circle.items, `venn.items`, `${side}`, err);
      }
      const intersection = venn.intersection;
      if (!isRecord(intersection)) {
        err("venn.intersection", "missing");
      } else {
        checkVennItems(intersection.items, "venn.items", "intersection", err);
      }
    }
  }

  // ── toneStyle — EXACTLY 5 levels, 0..4. Not schema-enforced; prompt-only. ──
  const ts = json.toneStyle;
  if (!isRecord(ts)) {
    err("toneStyle", "missing");
  } else {
    if (typeof ts.defaultLevel !== "number" || ts.defaultLevel !== 2) {
      err("toneStyle.defaultLevel", `expected 2, got ${JSON.stringify(ts.defaultLevel)}`);
    }
    const levels = ts.levels;
    if (!Array.isArray(levels) || levels.length !== 5) {
      err(
        "toneStyle.levels",
        `expected exactly 5 levels, got ${Array.isArray(levels) ? levels.length : "none"}`
      );
    } else {
      const seen = new Set<number>();
      levels.forEach((lv, i) => {
        if (!isRecord(lv)) {
          err("toneStyle.levels", `[${i}] is not an object`);
          return;
        }
        if (typeof lv.level !== "number" || lv.level < 0 || lv.level > 4) {
          err("toneStyle.levels", `[${i}].level outside 0-4: ${JSON.stringify(lv.level)}`);
        } else {
          seen.add(lv.level);
        }
        if (typeof lv.sentence !== "string" || lv.sentence.trim() === "") {
          err("toneStyle.levels", `[${i}].sentence missing or empty`);
        }
        if (typeof lv.sentenceTranslation !== "string" || lv.sentenceTranslation.trim() === "") {
          err("toneStyle.levels", `[${i}].sentenceTranslation missing or empty`);
        } else if (!HANGUL.test(lv.sentenceTranslation)) {
          err("toneStyle.levels", `[${i}].sentenceTranslation is not Korean`);
        }
      });
      if (seen.size !== 5) {
        err("toneStyle.levels", `levels 0-4 not all present, got [${[...seen].sort().join(",")}]`);
      }
    }
  }

  // ── paraphrasing — EXACTLY 3, levels 1..3. Also prompt-only. ──────────────
  const para = json.paraphrasing;
  if (!Array.isArray(para) || para.length !== 3) {
    err(
      "paraphrasing",
      `expected exactly 3 alternatives, got ${Array.isArray(para) ? para.length : "none"}`
    );
  } else {
    const seen = new Set<number>();
    para.forEach((p, i) => {
      if (!isRecord(p)) {
        err("paraphrasing", `[${i}] is not an object`);
        return;
      }
      if (typeof p.level !== "number" || p.level < 1 || p.level > 3) {
        err("paraphrasing", `[${i}].level outside 1-3: ${JSON.stringify(p.level)}`);
      } else {
        seen.add(p.level);
      }
      if (typeof p.label !== "string" || p.label.trim() === "") {
        err("paraphrasing", `[${i}].label missing or empty`);
      }
      if (typeof p.sentence !== "string" || p.sentence.trim() === "") {
        err("paraphrasing", `[${i}].sentence missing or empty`);
      }
      if (typeof p.sentenceTranslation !== "string" || p.sentenceTranslation.trim() === "") {
        err("paraphrasing", `[${i}].sentenceTranslation missing or empty`);
      } else if (!HANGUL.test(p.sentenceTranslation)) {
        err("paraphrasing", `[${i}].sentenceTranslation is not Korean`);
      }
    });
    if (seen.size !== 3) {
      err("paraphrasing", `levels 1-3 not all present, got [${[...seen].sort().join(",")}]`);
    }
  }

  return out;
}

function checkVennItems(
  items: unknown,
  check: string,
  where: string,
  err: (c: string, d: string) => void
): void {
  if (!Array.isArray(items)) {
    err(check, `${where}: items is not an array`);
    return;
  }
  items.forEach((item, i) => {
    if (typeof item !== "string" || item.trim() === "") {
      err(check, `${where}[${i}] is not a non-empty string`);
      return;
    }
    const words = item.trim().split(/\s+/u).length;
    if (words > MAX_VENN_ITEM_WORDS) {
      err(check, `${where}[${i}] is ${words} words (max ${MAX_VENN_ITEM_WORDS}): ${item}`);
    }
  });
}
```

- [ ] **Step 4: 테스트 통과를 확인한다**

Run: `cd functions && npx jest test/eval-harness.test.ts`
Expected: PASS — Task 4의 7개 + Task 5의 21개, 총 28 passed. (Task 4에서 실패했던 `cases.ts`의 `./validate` import도 이제 해소된다.)

- [ ] **Step 5: 전체 스위트·빌드·린트를 확인한다**

Run: `cd functions && npx jest && npm run build && npm run lint`
Expected: 전부 통과, exit 0.

- [ ] **Step 6: 커밋한다**

```bash
git add functions/src/eval/cases.ts functions/src/eval/validate.ts functions/test/eval-harness.test.ts
git commit -m "feat(functions): add turn-feedback golden case set and structural validators"
```

---

## Task 6: 실 Vertex 호출 러너

**Why now:** 데이터와 검증기가 준비됐다. 이제 실제 모델을 때려 리포트를 뽑는 도구를 만든다. jest 밖에 있으므로 [jest.config.js:1](functions/jest.config.js:1)의 "no network" 계약을 깨지 않는다.

**Files:**
- Create: `functions/eval/run.js`
- Create: `functions/eval/README.md`
- Modify: `functions/package.json`
- Modify: `functions/.gitignore`

**Interfaces:**
- Consumes (전부 `../lib/`의 빌드 산출물에서): `CASES` (cases.ts), `validateSlim`/`validateDeep`/`scoreOf`/`countIncorrectSegments` (validate.ts), `buildGenerateBody`/`extractJson`/`FEEDBACK_SYSTEM_PROMPT`/`FEEDBACK_RESPONSE_SCHEMA`/`FEEDBACK_DEEP_SYSTEM_PROMPT`/`FEEDBACK_DEEP_RESPONSE_SCHEMA` (gemini.ts), `modelFor` (models.ts).
- Produces: `functions/eval/out/<task>-<timestamp>.md` 마크다운 리포트(gitignore됨).

- [ ] **Step 1: `.gitignore`에 출력 디렉토리를 추가한다**

`functions/.gitignore` 끝에 추가:

```
eval/out/
```

- [ ] **Step 2: 러너를 작성한다**

`functions/eval/run.js` 생성:

```js
#!/usr/bin/env node
"use strict";

/**
 * Live turn-feedback eval runner. Sweeps temperatures across the golden case set,
 * validates every response structurally, and writes a markdown report for human review.
 *
 * This lives OUTSIDE functions/test/ on purpose: jest.config.js:1 declares that suite
 * "no emulator, no network", and this script is nothing but network. It is also outside
 * tsconfig's `include: ["src"]`, so it is plain JS requiring the built output — that
 * keeps it dependency-free (no ts-node) at the cost of running `npm run build` first,
 * which `npm run eval` does for you.
 *
 * Usage:
 *   GEMINI_API_KEY=$(firebase functions:secrets:access GEMINI_API_KEY) \
 *     npm run eval -- --temps=0,0.3,0.7 --repeats=3 --task=feedback
 *
 * This SPENDS REAL QUOTA: cases × temps × repeats calls (default 13 × 3 × 3 = 117).
 */

const fs = require("fs");
const path = require("path");

const { CASES } = require("../lib/eval/cases");
const {
  validateSlim,
  validateDeep,
  scoreOf,
  countIncorrectSegments,
} = require("../lib/eval/validate");
const {
  buildGenerateBody,
  extractJson,
  FEEDBACK_SYSTEM_PROMPT,
  FEEDBACK_RESPONSE_SCHEMA,
  FEEDBACK_DEEP_SYSTEM_PROMPT,
  FEEDBACK_DEEP_RESPONSE_SCHEMA,
} = require("../lib/providers/gemini");
const { modelFor } = require("../lib/config/models");

/** Mirrors the unexported GEMINI_BASE_URL in src/providers/gemini.ts:67. */
const BASE_URL = "https://aiplatform.googleapis.com/v1/publishers/google";

const DEFAULTS = { temps: [0, 0.3, 0.7], repeats: 3, task: "feedback", only: null };

function parseArgs(argv) {
  const opts = { ...DEFAULTS };
  for (const arg of argv.slice(2)) {
    const match = /^--([a-zA-Z]+)=(.*)$/.exec(arg);
    if (!match) throw new Error(`bad flag: ${arg} (expected --key=value)`);
    const [, key, raw] = match;
    if (key === "temps") {
      opts.temps = raw.split(",").map((t) => {
        const n = Number(t);
        if (!Number.isFinite(n) || n < 0 || n > 2) throw new Error(`bad temperature: ${t}`);
        return n;
      });
    } else if (key === "repeats") {
      opts.repeats = Number(raw);
      if (!Number.isInteger(opts.repeats) || opts.repeats < 1) {
        throw new Error(`bad repeats: ${raw}`);
      }
    } else if (key === "task") {
      if (raw !== "feedback" && raw !== "feedbackDeep") {
        throw new Error(`bad task: ${raw} (expected feedback or feedbackDeep)`);
      }
      opts.task = raw;
    } else if (key === "only") {
      opts.only = raw.split(",");
    } else {
      throw new Error(`unknown flag: --${key}`);
    }
  }
  return opts;
}

async function callVertex(apiKey, model, body) {
  const res = await fetch(`${BASE_URL}/models/${model}:generateContent`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${text.slice(0, 300)}`);
  return extractJson(text);
}

/** Render slim segments as markdown so over-correction is visible at a glance. */
function renderSegments(segments) {
  if (!Array.isArray(segments)) return "_(no segments)_";
  return segments
    .map((s) => {
      const t = String(s && s.text !== undefined ? s.text : "");
      if (!s) return t;
      if (s.type === "incorrect") return `~~${t}~~`;
      if (s.type === "correction") return `**${t}**`;
      if (s.type === "highlight") return `__${t}__`;
      return t;
    })
    .join("");
}

function get(obj, ...keys) {
  let cur = obj;
  for (const k of keys) {
    if (cur === null || typeof cur !== "object") return undefined;
    cur = cur[k];
  }
  return cur;
}

function summarise(runs, temp) {
  const at = runs.filter((r) => r.temp === temp);
  const failed = at.filter((r) => r.error).length;
  let errors = 0;
  let warns = 0;
  for (const r of at) {
    for (const v of r.violations) {
      if (v.severity === "error") errors++;
      else warns++;
    }
  }
  // score spread per case: how far apart repeats of the SAME input land
  const spreads = [];
  const byCase = new Map();
  for (const r of at) {
    if (r.error) continue;
    const s = scoreOf(r.json);
    if (s === null) continue;
    if (!byCase.has(r.caseId)) byCase.set(r.caseId, []);
    byCase.get(r.caseId).push(s);
  }
  for (const scores of byCase.values()) {
    if (scores.length > 1) spreads.push(Math.max(...scores) - Math.min(...scores));
  }
  const meanSpread = spreads.length
    ? (spreads.reduce((a, b) => a + b, 0) / spreads.length).toFixed(1)
    : "n/a";
  const maxSpread = spreads.length ? String(Math.max(...spreads)) : "n/a";
  return { failed, errors, warns, meanSpread, maxSpread, total: at.length };
}

function writeReport(opts, cases, runs, model) {
  const outDir = path.join(__dirname, "out");
  fs.mkdirSync(outDir, { recursive: true });
  const stamp = new Date().toISOString().replace(/[:.]/g, "-");
  const file = path.join(outDir, `${opts.task}-${stamp}.md`);
  const isDeep = opts.task === "feedbackDeep";
  const L = [];

  L.push(`# ${opts.task} eval — ${stamp}`);
  L.push("");
  L.push(
    `model \`${model}\` · cases ${cases.length} · temps ${opts.temps.join(", ")} · repeats ${opts.repeats} · ${runs.length} calls`
  );
  L.push("");
  L.push("## 요약");
  L.push("");
  L.push("| temperature | 호출 | 실패 | error 위반 | warn 위반 | 점수 스프레드(평균) | 점수 스프레드(최대) |");
  L.push("| --- | --- | --- | --- | --- | --- | --- |");
  for (const temp of opts.temps) {
    const s = summarise(runs, temp);
    L.push(
      `| ${temp} | ${s.total} | ${s.failed} | ${s.errors} | ${s.warns} | ${s.meanSpread} | ${s.maxSpread} |`
    );
  }
  L.push("");
  L.push(
    "점수 스프레드 = 같은 입력을 반복 호출했을 때 나온 점수의 최대-최소 차이. 피드백은 채점이므로 이 값이 작을수록 좋다."
  );
  L.push("");

  if (!isDeep) {
    L.push("## 케이스별 점수");
    L.push("");
    L.push(`| case | category | ${opts.temps.map((t) => `t=${t}`).join(" | ")} |`);
    L.push(`| --- | --- | ${opts.temps.map(() => "---").join(" | ")} |`);
    for (const c of cases) {
      const cells = opts.temps.map((temp) => {
        const scores = runs
          .filter((r) => r.caseId === c.id && r.temp === temp && !r.error)
          .map((r) => scoreOf(r.json))
          .filter((s) => s !== null);
        return scores.length ? scores.join(" / ") : "—";
      });
      L.push(`| \`${c.id}\` | ${c.category} | ${cells.join(" | ")} |`);
    }
    L.push("");
  }

  L.push("## 위반 목록");
  L.push("");
  const withViolations = runs.filter((r) => r.violations.length > 0 || r.error);
  if (withViolations.length === 0) {
    L.push("_구조 위반 없음._");
  } else {
    L.push("| case | t | #  | severity | check | detail |");
    L.push("| --- | --- | --- | --- | --- | --- |");
    for (const r of withViolations) {
      if (r.error) {
        L.push(`| \`${r.caseId}\` | ${r.temp} | ${r.repeat} | error | call-failed | ${r.error} |`);
      }
      for (const v of r.violations) {
        L.push(
          `| \`${r.caseId}\` | ${r.temp} | ${r.repeat} | ${v.severity} | ${v.check} | ${String(v.detail).replace(/\|/g, "\\|")} |`
        );
      }
    }
  }
  L.push("");

  L.push("## 사람이 읽을 출력");
  L.push("");
  L.push(
    "구조 검증은 여기까지가 한계다. 아래는 **교정이 학습자에게 실제로 옳은가**를 눈으로 판정하기 위한 원문이다. 각 케이스의 `note`가 무엇을 봐야 하는지 알려준다."
  );
  L.push("");
  for (const c of cases) {
    L.push(`### \`${c.id}\` — ${c.category}`);
    L.push("");
    L.push(`> ${c.note}`);
    L.push("");
    L.push(`- 한국어: ${c.payload.koreanPrompt}`);
    L.push(`- 학습자 영어: \`${c.payload.userEnglish}\``);
    L.push(`- 참고 영어: \`${c.payload.referenceEnglish}\``);
    L.push(`- level: \`${c.payload.level}\``);
    L.push("");
    for (const temp of opts.temps) {
      // one representative run per temperature — the rest are in the score table
      const r = runs.find((x) => x.caseId === c.id && x.temp === temp && !x.error);
      L.push(`**t=${temp}**`);
      L.push("");
      if (!r) {
        L.push("_모든 반복 호출이 실패했다._");
        L.push("");
        continue;
      }
      if (isDeep) {
        L.push("```json");
        L.push(JSON.stringify(r.json, null, 2));
        L.push("```");
      } else {
        const score = scoreOf(r.json);
        L.push(`- 점수: **${score === null ? "—" : score}** · incorrect 세그먼트 ${countIncorrectSegments(r.json)}개`);
        L.push(`- 격려: ${get(r.json, "writingScore", "encouragementMessage") || "—"}`);
        L.push(`- 교정: ${renderSegments(get(r.json, "grammar", "correctedSentence", "segments"))}`);
        L.push(`- 설명: ${get(r.json, "grammar", "explanation") || "—"}`);
        L.push(`- 자연스러운 표현: ${renderSegments(get(r.json, "naturalExpression", "segments"))}`);
        L.push(
          `- 이유: **${get(r.json, "naturalExpression", "reason", "keyword") || "—"}** — ${get(r.json, "naturalExpression", "reason", "description") || "—"}`
        );
      }
      L.push("");
    }
  }

  fs.writeFileSync(file, L.join("\n"), "utf8");
  return file;
}

async function main() {
  const opts = parseArgs(process.argv);
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    console.error(
      "GEMINI_API_KEY is not set.\n" +
        "  GEMINI_API_KEY=$(firebase functions:secrets:access GEMINI_API_KEY) npm run eval\n" +
        "Do not write the key to a file."
    );
    process.exit(1);
  }

  const isDeep = opts.task === "feedbackDeep";
  const system = isDeep ? FEEDBACK_DEEP_SYSTEM_PROMPT : FEEDBACK_SYSTEM_PROMPT;
  const schema = isDeep ? FEEDBACK_DEEP_RESPONSE_SCHEMA : FEEDBACK_RESPONSE_SCHEMA;
  const model = modelFor(opts.task);
  const cases = opts.only ? CASES.filter((c) => opts.only.includes(c.id)) : CASES;
  if (cases.length === 0) throw new Error(`--only matched no cases: ${opts.only}`);

  const total = cases.length * opts.temps.length * opts.repeats;
  const runs = [];
  let n = 0;

  for (const temp of opts.temps) {
    for (const c of cases) {
      for (let repeat = 1; repeat <= opts.repeats; repeat++) {
        n++;
        process.stderr.write(`[${n}/${total}] ${c.id} t=${temp} #${repeat}\n`);
        try {
          const body = buildGenerateBody(c.payload, system, schema, { temperature: temp });
          const json = await callVertex(apiKey, model, body);
          const violations = isDeep ? validateDeep(json) : validateSlim(json, c.expect);
          runs.push({ caseId: c.id, temp, repeat, json, violations, error: null });
        } catch (e) {
          runs.push({
            caseId: c.id,
            temp,
            repeat,
            json: null,
            violations: [],
            error: String((e && e.message) || e),
          });
        }
      }
    }
  }

  const file = writeReport(opts, cases, runs, model);
  const failures = runs.filter((r) => r.error).length;
  console.log(`\n리포트: ${file}`);
  console.log(`호출 ${runs.length}건 중 실패 ${failures}건`);
  for (const temp of opts.temps) {
    const s = summarise(runs, temp);
    console.log(
      `  t=${temp}: error ${s.errors} · warn ${s.warns} · 점수 스프레드 평균 ${s.meanSpread} / 최대 ${s.maxSpread}`
    );
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
```

- [ ] **Step 3: `package.json`에 스크립트를 추가한다**

`functions/package.json`의 `scripts`에서 `"smoke"` 줄 **뒤**에 추가:

```json
    "eval": "npm run build && node eval/run.js",
```

- [ ] **Step 4: README를 작성한다**

`functions/eval/README.md` 생성:

```markdown
# 턴 피드백 eval 하네스

`feedback.slim` / `feedback.deep`의 출력을 골든 케이스 세트로 반복 측정한다.
실제 Vertex AI를 호출하므로 **쿼터를 소모한다** — jest 스위트(`npm test`)와는 완전히 분리되어 있다.

## 구성

| 위치 | 역할 |
| --- | --- |
| `src/eval/cases.ts` | 골든 케이스 13개(6 카테고리). 순수 데이터 |
| `src/eval/validate.ts` | 구조 검증기. 순수 함수, `npm test`로 오프라인 검증됨 |
| `eval/run.js` | 실 호출 러너. 마크다운 리포트를 `eval/out/`에 쓴다(gitignore) |

## 실행

```bash
GEMINI_API_KEY=$(firebase functions:secrets:access GEMINI_API_KEY) \
  npm run eval -- --temps=0,0.3,0.7 --repeats=3 --task=feedback
```

키를 파일에 쓰거나 커밋하지 말 것 — 환경변수로만 전달한다.

### 플래그

| 플래그 | 기본값 | 설명 |
| --- | --- | --- |
| `--temps` | `0,0.3,0.7` | 스윕할 temperature 목록 |
| `--repeats` | `3` | 케이스·온도당 반복 호출 수. 점수 분산 측정에 쓰인다 |
| `--task` | `feedback` | `feedback` 또는 `feedbackDeep` |
| `--only` | (전체) | 케이스 id를 콤마로. 예: `--only=ag-coffee-please,tg-past-tense` |

호출 수 = 케이스 × 온도 × 반복. 기본값이면 13 × 3 × 3 = 117회.

## 리포트 읽는 법

1. **요약 표** — 온도별 구조 위반 수와 점수 스프레드. 피드백은 채점이므로 스프레드가 작을수록 좋다.
2. **케이스별 점수** — 같은 입력의 반복 점수가 나란히 찍힌다. 흔들리는 케이스가 바로 보인다.
3. **위반 목록** — 기계가 잡은 것 전부.
4. **사람이 읽을 출력** — 구조 검증이 닿지 못하는 것, 즉 *이 교정이 학습자에게 실제로 옳은가*를 눈으로 판정하는 구역. 각 케이스의 `note`가 무엇을 봐야 하는지 알려준다.

특히 `already-good` 카테고리를 먼저 볼 것 — 고칠 게 없는 문장에 억지 교정을 만들어내는지가 가장 잘 드러난다.
```

- [ ] **Step 5: 네트워크 없이 러너가 로드되고 인자 검증이 동작하는지 확인한다**

Run: `cd functions && npm run build && node eval/run.js --task=bogus`
Expected: `Error: bad task: bogus (expected feedback or feedbackDeep)`와 함께 exit 1. (require 체인이 전부 해소된다는 것 — 즉 `lib/eval/*`가 실제로 빌드됐다는 것 — 도 함께 확인된다.)

Run: `cd functions && GEMINI_API_KEY= node eval/run.js --only=ag-coffee-please --repeats=1 --temps=0`
Expected: `GEMINI_API_KEY is not set.` 안내와 함께 exit 1. **여기까지는 네트워크를 타지 않는다.**

- [ ] **Step 6: jest 스위트가 러너를 수집하지 않는지 확인한다**

Run: `cd functions && npx jest --listTests`
Expected: 출력된 목록에 `eval/run.js`가 **없다**(`roots: ["<rootDir>/test"]`이므로). `test/` 아래 파일만 나열되어야 한다.

- [ ] **Step 7: 커밋한다**

```bash
git add functions/eval/run.js functions/eval/README.md functions/package.json functions/.gitignore
git commit -m "feat(functions): add live turn-feedback eval runner with temperature sweep"
```

---

## Task 7: 스윕 실행 → temperature 확정

**Why last:** 앞의 모든 것이 이 한 번의 측정을 위한 준비다. 이 태스크는 **사람의 판정이 들어가는 지점**이며, 자동으로 통과시킬 수 없다.

**Files:**
- Modify: `functions/src/config/generation.ts` (`FEEDBACK_TEMPERATURE`)
- Modify: `docs/design/prompt-system.md:106`

**Interfaces:**
- Consumes: Task 6의 러너, Task 2의 `FEEDBACK_TEMPERATURE`.
- Produces: 확정된 temperature 값과 그 근거 기록.

- [ ] **Step 1: slim 스윕을 실행한다**

Run:
```bash
cd functions && GEMINI_API_KEY=$(firebase functions:secrets:access GEMINI_API_KEY) \
  npm run eval -- --temps=0,0.3,0.7 --repeats=3 --task=feedback
```
Expected: 117회 호출 진행 로그가 stderr에 찍히고, 마지막에 `리포트: .../eval/out/feedback-<stamp>.md` 경로와 온도별 요약이 출력된다. 실패 호출이 몇 건 있어도 리포트는 생성된다(해당 셀이 `—`로 표시됨).

키 조회가 안 되면 Secret Manager 콘솔에서 `GEMINI_API_KEY` 값을 가져온다. **어느 경우에도 키를 파일에 쓰지 말 것.**

- [ ] **Step 2: deep 스윕을 실행한다**

Run:
```bash
cd functions && GEMINI_API_KEY=$(firebase functions:secrets:access GEMINI_API_KEY) \
  npm run eval -- --temps=0,0.3,0.7 --repeats=2 --task=feedbackDeep
```
Expected: 78회 호출 후 `eval/out/feedbackDeep-<stamp>.md` 생성. deep에는 점수가 없으므로 요약 표의 스프레드 칸은 `n/a`이고, 판정 근거는 **구조 위반 수**(특히 `toneStyle.levels` 5개·`paraphrasing` 3개 카디널리티, `venn.items` 길이)다 — 이들은 responseSchema가 강제하지 않고 프롬프트 문장으로만 지탱되므로 온도에 가장 먼저 무너지는 지점이다.

- [ ] **Step 3: 리포트를 읽고 온도를 판정한다**

두 리포트를 열어 다음 순서로 본다:

1. **요약 표의 error 위반 수** — 구조를 깨는 온도는 그 자체로 탈락이다.
2. **점수 스프레드(slim)** — 같은 문장을 다시 채점했을 때 점수가 얼마나 흔들리는가. 학습자가 같은 답을 두 번 내고 다른 점수를 받으면 앱을 신뢰하지 않는다. 평균 스프레드가 가장 작은 온도가 유력하다.
3. **`already-good` 케이스의 사람이 읽을 출력** — 온도가 낮다고 무조건 좋은 게 아니다. 과잉 교정(고칠 게 없는데 `incorrect`를 만들어냄)이 온도와 무관하게 나타나면 그건 **프롬프트 문안 문제**이지 온도 문제가 아니다. 별도 계획으로 넘길 근거가 된다.
4. **`level-variance` 쌍** — `lv-starter`와 `lv-expert`의 출력이 사실상 같다면, 레벨별 채점 보정이 미구현이라는 것이 측정으로 확인된 것이다. 이것도 프롬프트 문안 문제이므로 후속 계획 항목으로 기록한다.

판정 기준: **구조 위반이 없으면서 점수 스프레드가 가장 작은 온도**를 고른다. 동률이면 [prompt-system.md:106](docs/design/prompt-system.md:106)이 이미 가정해 둔 `0.3`을 택한다(문서와 코드를 일치시키는 편이 낫다).

- [ ] **Step 4: 확정된 값을 코드에 반영한다**

Step 3의 판정이 `0.3`이 아니라면, `functions/src/config/generation.ts`의 `FEEDBACK_TEMPERATURE`를 그 값으로 바꾸고 주석의 근거 문장도 함께 고친다:

```ts
export const FEEDBACK_TEMPERATURE = 0.3; // ← 판정된 값으로 교체
```

판정이 `0.3`이면 코드 변경은 없다. 어느 쪽이든 다음 스텝의 문서 갱신은 반드시 한다.

- [ ] **Step 5: 테스트가 여전히 통과하는지 확인한다**

Run: `cd functions && npx jest && npm run build && npm run lint`
Expected: 전부 통과. `generation-config.test.ts`는 `FEEDBACK_TEMPERATURE`를 리터럴이 아니라 상수 참조로 비교하므로 값이 바뀌어도 통과해야 한다 — 만약 실패한다면 테스트가 값을 하드코딩한 것이니 상수 참조로 고친다.

- [ ] **Step 6: 설계 문서의 "needs-you" 가정을 해소한다**

[docs/design/prompt-system.md:106](docs/design/prompt-system.md:106)의 다음 줄을 찾는다:

```
**가정(needs-you/튜닝):** temperature(대본 0.8/피드백·요약 0.3)·max-tokens·실제 문안 authoring·deep coaching 배열 vs 문자열(→ 레거시 `{positive, toImprove}` 채택).
```

다음으로 교체한다(`<확정값>`과 `<날짜>`, 근거 수치는 실제 리포트에서 옮겨 적을 것):

```
**확정(2026-07-18):** feedback/feedbackDeep temperature = `<확정값>` — `functions/eval/`의 골든셋 13케이스 × 온도 스윕으로 측정해 확정했다(구조 위반 0건, 점수 스프레드 평균 `<수치>`). `config/generation.ts`가 SoT. dialogue/summary는 eval 커버리지가 없어 여전히 미설정(프로바이더 기본값)이며 아직 needs-you다.
**가정(needs-you/튜닝):** dialogue/summary temperature·max-tokens·실제 문안 authoring·deep coaching 배열 vs 문자열(→ 레거시 `{positive, toImprove}` 채택).
```

- [ ] **Step 7: 커밋한다**

```bash
git add functions/src/config/generation.ts docs/design/prompt-system.md
git commit -m "docs(design): confirm feedback temperature from eval sweep"
```

- [ ] **Step 8: 후속 항목을 기록한다**

Step 3에서 드러난 **프롬프트 문안 문제**(과잉 교정, level 미반영, slim 프롬프트에 `difficulty-bands`·`korean-error-reference`가 접혀 있지 않은 것)를 GitHub 이슈로 남긴다. 이 계획은 측정 기반까지가 범위이고, 문안 수정은 이제 근거를 갖춘 별도 작업이다.

Run:
```bash
gh issue create --repo jjundev/project-oce-android \
  --title "턴 피드백 프롬프트 문안 개선 (eval 근거 확보됨)" \
  --label needs-triage \
  --body "$(cat <<'EOF'
`functions/eval/` 하네스로 측정한 결과 드러난 프롬프트 문안 문제들. 온도 튜닝(별도 완료)으로는 해소되지 않는 항목이다.

- [ ] 과잉 교정: `already-good` 케이스에서 고칠 것이 없는데 `incorrect` 세그먼트를 만들어내는지 — 리포트 수치 첨부
- [ ] `level` 미반영: `FEEDBACK_SYSTEM_PROMPT`가 `level`을 채점에 쓰라고 지시하지 않는다. `lv-starter`/`lv-expert` 쌍의 출력이 사실상 동일함
- [ ] slim 프롬프트에 `docs/design/prompts/_shared/difficulty-bands.md`와 `korean-error-reference.md`가 접혀 있지 않다 — 설계 문서는 PREPEND를 지시하지만 서버 상수에는 없음

재현: `npm run eval -- --task=feedback` (functions/eval/README.md 참조)
EOF
)"
```
Expected: 생성된 이슈 URL이 출력된다.

---

## 실행 후 남는 것

- `npm test`로 언제든 도는 오프라인 검증기 + 골든셋 정합성 테스트
- `npm run eval`로 언제든 도는 실측 루프 — 프롬프트를 고칠 때마다 같은 세트로 회귀를 잡을 수 있다
- 근거를 가진 temperature 값과, 그 근거가 적힌 설계 문서
- 프롬프트 문안 수정을 위한 **측정 가능한** 출발점 (이슈)

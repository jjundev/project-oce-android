# 서버 비용·한도 방어 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `/llm`·`mergeGuestData` Cloud Functions 에서 한 사람이 전체 사용자를 막거나 Gemini 요금을 무한히 쓰는 경로(전역 하루 한도, 재시도 키 무한 재생성, 요약·음성 무제한, 비익명 병합 토큰)를 서버만 고쳐 막는다.

**Architecture:** 기존 게이트 패턴(순수 판정 함수 + Firestore 트랜잭션 래퍼 + `handle.ts` 에서 스트림 열기 전 판정)을 그대로 확장한다. 하루 사용량은 `users/{uid}/usage/{yyyymmdd}` 한 문서에 `sessionCount`(대화)·`ttsCount`(음성)로 모으고, 요약은 `sessions/{id}.summaryCount` 별도 카운터로 센다. 크기·ID 형식 검사는 새 `request-guards.ts` 에 모아 모든 태스크 앞단에서 400 으로 끊는다.

**Tech Stack:** TypeScript 5, Node 22, firebase-functions v2 (`onRequest`/`onCall`), firebase-admin 12 (Firestore 트랜잭션), Jest 29 + ts-jest, GitHub Actions.

**Spec:** [`docs/superpowers/specs/2026-09-24-server-cost-limit-defense-design.md`](../specs/2026-09-24-server-cost-limit-defense-design.md)

## Global Constraints

- Android 코드는 한 줄도 바꾸지 않는다 — 현재 스토어 앱(1.1.1)이 그대로 동작해야 한다.
- 모든 명령은 `functions/` 디렉터리에서 실행한다. 워크트리라면 먼저 `npm ci` (node_modules 는 공유되지 않음).
- 단일 테스트: `npx jest test/<file>.test.ts`. 전체: `npm test`. 타입/빌드: `npm run build`. 린트: `npm run lint`.
- 시작 기준선: `npm test` → 26 suites / 295 tests 통과, `npm run lint` 무경고. 각 태스크 끝에서 전체 `npm test` 가 통과해야 한다.
- 상수(정확한 값): `FREE_REPLAYS = 2`, `SUMMARY_CAP = 6`, `DEFAULT_DAILY_TTS_LINES = 300`, `MAX_TEXT_PAYLOAD_CHARS = 65_536`, `MAX_SPEAKING_PAYLOAD_CHARS = 1_500_000`, `MAX_TTS_TEXT_CHARS = 500`, `LLM_MAX_INSTANCES = 10`.
- 운영 설정값(Task 7): `config/limits.dailyFreeSessions = 3`, `config/limits.dailyTtsLines = 300`.
- 사용량 문서 경로: `users/{uid}/usage/{yyyymmdd}` (KST, 기존 `kstDateKey`). 재시도 키 문서: `idempotency/{uid}_{idempotencyKey}`.
- ID(sessionId·idempotencyKey) 형식: `/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i`.
- 한도 거절 코드는 기존 것만 쓴다: 대화·음성 하루 한도 = 429 `DAILY_LIMIT_EXCEEDED`, 요약 세션 한도 = 429 `CAP_EXCEEDED`, 남의/없는 세션 = 403 `SESSION_INVALID`, 형식·크기 = 400 `INVALID_PAYLOAD`. 새 `ErrorCode` 를 만들지 않는다.
- 커밋 메시지 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` 를 붙인다.
- 배포(Task 7)는 사용자가 명시적으로 승인한 뒤에만 한다.

## File Structure

| 파일 | 책임 |
|---|---|
| `functions/src/llm/request-guards.ts` (신규) | UUID 형식 검사, 태스크별 payload 크기 상한 |
| `functions/src/llm/start-gate.ts` | 1인당 usage 경로, uid 네임스페이스 재시도 키, 무료 재요청 2회 후 차감, `charged` 기반 환불, 한도 필드 지정 가능한 limit provider |
| `functions/src/llm/session-cap.ts` | 요약 전용 `SummaryGate`(`summaryCount < SUMMARY_CAP`) 추가 |
| `functions/src/llm/tts-quota.ts` (신규) | 1인당 하루 `ttsCount` 게이트 |
| `functions/src/llm/tts.ts` | tts 문장 500자 상한 |
| `functions/src/llm/handle.ts` | 위 게이트들을 스트림/합성 전에 호출, 크기·ID 검사 연결 |
| `functions/src/llm/handler.ts` | 실제 Firestore 게이트 주입, `maxInstances` |
| `functions/src/llm/options.ts` | `LLM_MAX_INSTANCES` |
| `functions/src/merge/merge.ts`, `mergeGuestData.ts` | 익명 게스트 토큰만 허용 |
| `.github/workflows/functions.yml` (신규) | functions build·lint·test CI |
| `docs/design/backend-functions.md`, `docs/design/firestore-schema.md` | 계약 문서 갱신 |

---

### Task 1: 요청 크기·ID 형식 가드

지금은 `sessionId`/`idempotencyKey` 가 검사 없이 Firestore 문서 id 로 쓰여 `a/b` 같은 값이 500 을 내고, payload 크기 상한이 없어 수 MB 입력이 그대로 Gemini 로 간다. 모든 태스크 앞단에서 400 으로 끊는다.

**Files:**
- Create: `functions/src/llm/request-guards.ts`
- Create: `functions/test/request-guards.test.ts`
- Modify: `functions/src/llm/handle.ts` (import 블록, `handle()` 의 task 검증 직후, `handleDialogue`·`handleFeedback`·`handleFeedbackDeep`·`handleSpeaking` 의 id 검사)
- Modify (fixture): `functions/test/dialogue-handler.test.ts`, `functions/test/feedback-handler.test.ts`, `functions/test/feedback-deep-handler.test.ts`, `functions/test/speaking.test.ts`

**Interfaces:**
- Produces: `isUuid(value: string): boolean`, `payloadWithinLimit(task: Task, payload: unknown): boolean`, `MAX_TEXT_PAYLOAD_CHARS = 65_536`, `MAX_SPEAKING_PAYLOAD_CHARS = 1_500_000` (모두 `src/llm/request-guards.ts`). Task 3 이 `isUuid` 를 쓴다.
- Test fixture 규칙(이후 태스크가 전제): 핸들러 테스트의 sessionId 는 `"00000000-0000-4000-8000-000000000001"`, dialogue 테스트의 idempotencyKey 는 `"00000000-0000-4000-8000-0000000000a1"`.

- [ ] **Step 1: 기존 테스트 fixture 를 UUID 로 바꾼다 (동작 변화 없음)**

기존 테스트가 `"s1"`, `"k1"` 을 id 로 쓰므로 새 형식 검사에 걸린다. 먼저 치환한다(macOS `sed`):

```bash
cd functions
sed -i '' 's/"s1"/"00000000-0000-4000-8000-000000000001"/g' test/feedback-handler.test.ts test/feedback-deep-handler.test.ts test/speaking.test.ts
sed -i '' 's/"k1"/"00000000-0000-4000-8000-0000000000a1"/g' test/dialogue-handler.test.ts
grep -c '"s1"\|"k1"' test/feedback-handler.test.ts test/feedback-deep-handler.test.ts test/speaking.test.ts test/dialogue-handler.test.ts
```

Expected: 마지막 grep 이 네 파일 모두 `0`. (`test/sse.test.ts` 의 `"s1"` 은 단순 데이터라 건드리지 않는다.)

Run: `npm test`
Expected: 295 passed (치환만 했으므로 전부 통과).

- [ ] **Step 2: 가드 단위 테스트 작성**

`functions/test/request-guards.test.ts`:

```ts
import {
  MAX_SPEAKING_PAYLOAD_CHARS,
  MAX_TEXT_PAYLOAD_CHARS,
  isUuid,
  payloadWithinLimit,
} from "../src/llm/request-guards";

describe("isUuid", () => {
  it("accepts lowercase and uppercase UUIDs", () => {
    expect(isUuid("3f2b8c1e-9d4a-4f6b-8a2c-1e5d7f9b0a3c")).toBe(true);
    expect(isUuid("3F2B8C1E-9D4A-4F6B-8A2C-1E5D7F9B0A3C")).toBe(true);
  });

  it("rejects empty, path-like, reserved and oversized ids", () => {
    expect(isUuid("")).toBe(false);
    expect(isUuid("a/b")).toBe(false);
    expect(isUuid("__x__")).toBe(false);
    expect(isUuid("k1")).toBe(false);
    expect(isUuid("x".repeat(2000))).toBe(false);
  });
});

describe("payloadWithinLimit", () => {
  it("allows a normal text payload and rejects one over the text cap", () => {
    expect(payloadWithinLimit("summary", { words: ["a"] })).toBe(true);
    expect(
      payloadWithinLimit("summary", { blob: "x".repeat(MAX_TEXT_PAYLOAD_CHARS) })
    ).toBe(false);
  });

  it("gives speaking the larger audio budget only", () => {
    const audio = { audioBase64: "A".repeat(900_000) }; // ≈ 20s 16kHz mono WAV in base64
    expect(payloadWithinLimit("speaking", audio)).toBe(true);
    expect(payloadWithinLimit("feedback", audio)).toBe(false);
    expect(
      payloadWithinLimit("speaking", { audioBase64: "A".repeat(MAX_SPEAKING_PAYLOAD_CHARS) })
    ).toBe(false);
  });

  it("treats an absent payload as empty", () => {
    expect(payloadWithinLimit("tts", undefined)).toBe(true);
  });
});
```

- [ ] **Step 3: 핸들러 레벨 실패 테스트 작성**

`functions/test/dialogue-handler.test.ts` — `describe("handle task=dialogue", ...)` 안, 기존 첫 테스트("400 INVALID_PAYLOAD when idempotencyKey is missing") 바로 뒤에 추가:

```ts
  it("400 INVALID_PAYLOAD when idempotencyKey is not a UUID (never reaches the gate)", async () => {
    const res = recorder();
    const { gate } = fakeGate({});
    const reserve = jest.spyOn(gate, "reserve");
    await handle(
      req({
        task: "dialogue",
        idempotencyKey: "a/b",
        payload: { level: "easy", topic: "t", length: 10, firstSession: false },
      }),
      res,
      { startGate: gate, provider: streamProvider([]) }
    );
    expect(res.statusCode).toBe(400);
    expect(res.jsonBody).toEqual({ code: ErrorCode.INVALID_PAYLOAD });
    expect(reserve).not.toHaveBeenCalled();
  });

  it("400 INVALID_PAYLOAD for an oversized payload before the gate runs", async () => {
    const res = recorder();
    const { gate } = fakeGate({});
    const reserve = jest.spyOn(gate, "reserve");
    await handle(
      req({
        task: "dialogue",
        idempotencyKey: "00000000-0000-4000-8000-0000000000a1",
        payload: { level: "easy", topic: "x".repeat(70_000), length: 10, firstSession: false },
      }),
      res,
      { startGate: gate, provider: streamProvider([]) }
    );
    expect(res.statusCode).toBe(400);
    expect(res.jsonBody).toEqual({ code: ErrorCode.INVALID_PAYLOAD });
    expect(reserve).not.toHaveBeenCalled();
  });
```

`functions/test/feedback-handler.test.ts` — `describe("handle task=feedback", ...)` 안, 기존 첫 테스트("400 INVALID_PAYLOAD when sessionId is missing") 바로 뒤에 추가:

```ts
  it("400 INVALID_PAYLOAD when sessionId is not a UUID (never reaches the cap gate)", async () => {
    const res = recorder();
    const { gate } = fakeGate();
    const reserve = jest.spyOn(gate, "reserve");
    await handle(
      req({ task: "feedback", sessionId: "../x", payload: validPayload }),
      res,
      { provider: streamProvider([]), sessionGate: gate }
    );
    expect(res.statusCode).toBe(400);
    expect(res.jsonBody).toEqual({ code: ErrorCode.INVALID_PAYLOAD });
    expect(reserve).not.toHaveBeenCalled();
  });
```

- [ ] **Step 4: 실패 확인**

Run: `npx jest test/request-guards.test.ts test/dialogue-handler.test.ts test/feedback-handler.test.ts`
Expected: `request-guards.test.ts` 는 "Cannot find module '../src/llm/request-guards'" 로 FAIL, 새 핸들러 테스트 3개는 `reserve` 가 호출되거나 status 가 400 이 아니어서 FAIL.

- [ ] **Step 5: 가드 구현**

`functions/src/llm/request-guards.ts`:

```ts
/**
 * Cheap request-shape guards shared by every `/llm` task. They run BEFORE any Firestore transaction
 * or Gemini call, so a malformed id or an oversized body is a 400 — never a 500 (an id like `a/b`
 * used as a Firestore doc id throws) and never a large bill (a multi-MB summary prompt).
 */
import { Task } from "../types/protocol";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** sessionId(서버 randomUUID)·idempotencyKey(클라 UUID.randomUUID)는 모두 UUID 문자열이다. */
export function isUuid(value: string): boolean {
  return UUID_RE.test(value);
}

/** speaking 은 최대 20초 16kHz·16bit mono WAV(base64 ≈ 853KB)를 싣는다 — 여유 포함 상한. */
export const MAX_SPEAKING_PAYLOAD_CHARS = 1_500_000;

/** 텍스트 태스크 상한 — 20턴 요약 payload 도 ≈ 20KB 라 정상 사용은 닿지 않는다. */
export const MAX_TEXT_PAYLOAD_CHARS = 65_536;

/** true when the serialized payload fits the task's budget (absent payload = empty). */
export function payloadWithinLimit(task: Task, payload: unknown): boolean {
  const size = JSON.stringify(payload ?? {}).length;
  const max = task === "speaking" ? MAX_SPEAKING_PAYLOAD_CHARS : MAX_TEXT_PAYLOAD_CHARS;
  return size <= max;
}
```

- [ ] **Step 6: `handle.ts` 에 연결**

(a) import 블록의 `import { DailyLimitError, StartGate, StartResult } from "./start-gate";` 바로 위에 추가:

```ts
import { isUuid, payloadWithinLimit } from "./request-guards";
```

(b) `handle()` 안에서

```ts
  const task = body.task;
```

바로 뒤에 추가:

```ts

  // 2b. size guard — before any Firestore transaction or Gemini call (cost defense).
  if (!payloadWithinLimit(task, body.payload)) {
    res.status(400).json({ code: ErrorCode.INVALID_PAYLOAD });
    return;
  }
```

(c) `handleDialogue` 에서

```ts
    if (!idempotencyKey) {
      throw new InvalidDialoguePayloadError("missing idempotencyKey");
    }
```

를 다음으로 교체:

```ts
    if (!isUuid(idempotencyKey)) {
      throw new InvalidDialoguePayloadError("missing or malformed idempotencyKey");
    }
```

(d) `handleFeedback`·`handleFeedbackDeep`·`handleSpeaking` 세 곳의 `    if (!sessionId) {` 를 모두 `    if (!isUuid(sessionId)) {` 로 바꾼다(Edit `replace_all`). 바로 아래 throw 줄은 그대로 둔다.

- [ ] **Step 7: 통과 확인**

Run: `npx jest test/request-guards.test.ts test/dialogue-handler.test.ts test/feedback-handler.test.ts`
Expected: PASS

Run: `npm test && npm run lint`
Expected: 전체 통과(295 + 새 테스트), 린트 무경고.

- [ ] **Step 8: Commit**

```bash
git add functions/src/llm/request-guards.ts functions/src/llm/handle.ts functions/test/request-guards.test.ts functions/test/dialogue-handler.test.ts functions/test/feedback-handler.test.ts functions/test/feedback-deep-handler.test.ts functions/test/speaking.test.ts
git commit -m "$(cat <<'EOF'
fix(functions): reject malformed ids and oversized /llm payloads up front

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: 1인당 하루 대화 한도 + 재시도 키 재생성 제한

지금은 `usage/{날짜}` 문서 하나를 모든 사용자가 공유하고(한 명이 다 쓰면 전원 429), 이미 있는 재시도 키로 다시 오면 사용량 차감 없이 매번 대본을 새로 생성한다. 사용량을 `users/{uid}/usage/{날짜}` 로 옮기고, 재시도 키를 uid 로 네임스페이스하고, 무료 재요청은 2회까지만 허용한다.

**Files:**
- Modify: `functions/src/llm/start-gate.ts` (전체 교체)
- Modify: `functions/src/llm/handle.ts` (`handleDialogue` 환불 분기와 docstring)
- Modify: `functions/test/start-gate.test.ts` (전체 교체)
- Modify: `functions/test/dialogue-handler.test.ts` (`fakeGate`, 환불 관련 테스트)
- Modify: `docs/design/backend-functions.md` §7

**Interfaces:**
- Consumes: 없음
- Produces (Task 4 가 사용):
  - `usageDocPath(uid: string, usageKey: string): string` → `users/${uid}/usage/${usageKey}`
  - `idempotencyDocId(uid: string, idempotencyKey: string): string` → `${uid}_${idempotencyKey}`
  - `FREE_REPLAYS = 2`
  - `StartResult { sessionId: string; remaining: number; deduped: boolean; charged: boolean; usageKey: string }`
  - `StartGate.refund(uid: string, idempotencyKey: string, start: StartResult): Promise<void>` (시그니처 변경)
  - `TxnLike.set(ref: unknown, data: Record<string, unknown>, options?: { merge?: boolean }): void` (옵션 추가)
  - `DbLike.doc(path: string): unknown` (추가)
  - `DailyLimitError`, `LimitProvider`, `firestoreLimitProvider` (기존 그대로 export)

- [ ] **Step 1: start-gate 테스트를 새 계약으로 교체**

`functions/test/start-gate.test.ts` 전체를 다음으로 교체:

```ts
import {
  DailyLimitError,
  DbLike,
  DocSnapLike,
  FREE_REPLAYS,
  StartGate,
  TxnLike,
  evaluateStart,
  firestoreStartGate,
} from "../src/llm/start-gate";
import { SESSION_TTL_MS } from "../src/llm/session-cap";
import { kstDateKey } from "../src/config/kst";

const NOW = 1_700_000_000_000; // fixed instant
const USAGE_KEY = kstDateKey(NOW);
const KEY = "00000000-0000-4000-8000-0000000000a1";
const usagePath = (uid: string) => `users/${uid}/usage/${USAGE_KEY}`;
const idemPath = (uid: string, key = KEY) => `idempotency/${uid}_${key}`;

/**
 * In-memory Firestore double. Refs carry their full slash path so tests assert the EXACT document
 * each write lands on — the old global `usage/{day}` bug passed a fake that never checked paths.
 * `set(..., {merge:true})` merges like Firestore so the shared usage doc keeps other counters.
 */
function makeDb(seed: Record<string, Record<string, unknown>> = {}): {
  db: DbLike;
  store: Map<string, Record<string, unknown>>;
} {
  const store = new Map<string, Record<string, unknown>>(Object.entries(seed));
  const pathOf = (ref: unknown) => (ref as { path: string }).path;
  const txn: TxnLike = {
    async get(ref) {
      const value = store.get(pathOf(ref));
      const snap: DocSnapLike = { exists: value !== undefined, data: () => value };
      return snap;
    },
    set(ref, data, options) {
      const path = pathOf(ref);
      store.set(path, options?.merge ? { ...(store.get(path) ?? {}), ...data } : { ...data });
    },
    update(ref, data) {
      const path = pathOf(ref);
      store.set(path, { ...(store.get(path) ?? {}), ...data });
    },
    delete(ref) {
      store.delete(pathOf(ref));
    },
  };
  const db: DbLike = {
    collection(name) {
      return { doc: (id: string) => ({ path: `${name}/${id}` }) };
    },
    doc(path) {
      return { path };
    },
    async runTransaction(fn) {
      return fn(txn);
    },
  };
  return { db, store };
}

const limit3 = async () => 3;

function gateWith(
  db: DbLike,
  uuid: () => string = () => "sess-1",
  limit: () => Promise<number> = limit3
): StartGate {
  return firestoreStartGate(limit, db, uuid, () => NOW);
}

describe("evaluateStart (pure daily-limit decision)", () => {
  it("returns remaining-after-count when under the limit", () => {
    expect(evaluateStart(0, 3)).toBe(2);
    expect(evaluateStart(2, 3)).toBe(0);
  });

  it("throws DailyLimitError at or over the limit", () => {
    expect(() => evaluateStart(3, 3)).toThrow(DailyLimitError);
    expect(() => evaluateStart(5, 3)).toThrow(DailyLimitError);
  });
});

describe("firestoreStartGate.reserve — fresh start", () => {
  it("counts on the caller's own usage doc and writes uid-namespaced idempotency + session", async () => {
    const { db, store } = makeDb();
    const result = await gateWith(db, () => "sess-uuid").reserve("u1", KEY, 10);

    expect(result).toEqual({
      sessionId: "sess-uuid",
      remaining: 2,
      deduped: false,
      charged: true,
      usageKey: USAGE_KEY,
    });
    expect(store.get(usagePath("u1"))?.sessionCount).toBe(1);
    expect(store.has(`usage/${USAGE_KEY}`)).toBe(false); // old global doc is never written
    const idem = store.get(idemPath("u1"));
    expect(idem?.uid).toBe("u1");
    expect(idem?.sessionId).toBe("sess-uuid");
    expect(idem?.replayCount).toBe(0);
    const session = store.get("sessions/sess-uuid");
    expect(session?.uid).toBe("u1");
    expect(session?.turnCount).toBe(10);
    expect(session?.callCount).toBe(0);
    expect((session?.expiresAt as { toMillis(): number }).toMillis()).toBe(NOW + SESSION_TTL_MS);
  });

  it("keeps other counters (ttsCount) on the shared usage doc", async () => {
    const { db, store } = makeDb({ [usagePath("u1")]: { sessionCount: 1, ttsCount: 7 } });
    await gateWith(db).reserve("u1", KEY, 6);
    expect(store.get(usagePath("u1"))).toMatchObject({ sessionCount: 2, ttsCount: 7 });
  });

  it("one user at the limit does not block another user", async () => {
    const { db } = makeDb({ [usagePath("u1")]: { sessionCount: 3 } });
    await expect(gateWith(db).reserve("u1", KEY, 6)).rejects.toBeInstanceOf(DailyLimitError);
    const other = await gateWith(db).reserve("u2", KEY, 6);
    expect(other.deduped).toBe(false);
    expect(other.remaining).toBe(2);
  });

  it("the same key from two users never collides", async () => {
    const { db, store } = makeDb();
    let n = 0;
    const gate = gateWith(db, () => `sess-${++n}`);
    const a = await gate.reserve("u1", KEY, 6);
    const b = await gate.reserve("u2", KEY, 6);
    expect(a.sessionId).toBe("sess-1");
    expect(b.sessionId).toBe("sess-2");
    expect(b.deduped).toBe(false);
    expect(store.get(idemPath("u2"))?.sessionId).toBe("sess-2");
  });
});

describe("firestoreStartGate.reserve — replay of the same key", () => {
  it(`is free for ${FREE_REPLAYS} replays, then charges a daily slot per replay`, async () => {
    const { db, store } = makeDb();
    const gate = gateWith(db, () => "sess-A");
    const first = await gate.reserve("u1", KEY, 6);
    expect(first.charged).toBe(true);

    for (let i = 0; i < FREE_REPLAYS; i++) {
      const replay = await gate.reserve("u1", KEY, 6);
      expect(replay).toMatchObject({ sessionId: "sess-A", deduped: true, charged: false });
    }
    expect(store.get(usagePath("u1"))?.sessionCount).toBe(1);

    const paid = await gate.reserve("u1", KEY, 6);
    expect(paid).toMatchObject({ sessionId: "sess-A", deduped: true, charged: true, remaining: 1 });
    expect(store.get(usagePath("u1"))?.sessionCount).toBe(2);
    expect(store.get(idemPath("u1"))?.replayCount).toBe(FREE_REPLAYS + 1);
  });

  it("a replay past the allowance is rejected at the daily limit", async () => {
    const { db, store } = makeDb({
      [usagePath("u1")]: { sessionCount: 3 },
      [idemPath("u1")]: { uid: "u1", sessionId: "s", replayCount: FREE_REPLAYS },
    });
    await expect(gateWith(db).reserve("u1", KEY, 6)).rejects.toBeInstanceOf(DailyLimitError);
    expect(store.get(idemPath("u1"))?.replayCount).toBe(FREE_REPLAYS); // nothing committed
  });

  it("an expired key is treated as a fresh start", async () => {
    const { db, store } = makeDb({
      [idemPath("u1")]: {
        uid: "u1",
        sessionId: "old",
        replayCount: 0,
        expiresAt: { toMillis: () => NOW - 1 },
      },
    });
    const result = await gateWith(db, () => "new").reserve("u1", KEY, 6);
    expect(result).toMatchObject({ sessionId: "new", deduped: false, charged: true });
    expect(store.get(usagePath("u1"))?.sessionCount).toBe(1);
  });
});

describe("firestoreStartGate.refund", () => {
  it("fresh start: decrements usage AND deletes the idempotency key", async () => {
    const { db, store } = makeDb();
    const gate = gateWith(db);
    const start = await gate.reserve("u1", KEY, 6);
    await gate.refund("u1", KEY, start);
    expect(store.get(usagePath("u1"))?.sessionCount).toBe(0);
    expect(store.has(idemPath("u1"))).toBe(false);
  });

  it("paid replay: decrements usage but keeps the original key", async () => {
    const { db, store } = makeDb({
      [usagePath("u1")]: { sessionCount: 1 },
      [idemPath("u1")]: { uid: "u1", sessionId: "s", replayCount: FREE_REPLAYS },
    });
    const gate = gateWith(db);
    const start = await gate.reserve("u1", KEY, 6);
    expect(start.charged).toBe(true);
    await gate.refund("u1", KEY, start);
    expect(store.get(usagePath("u1"))?.sessionCount).toBe(1);
    expect(store.has(idemPath("u1"))).toBe(true);
  });

  it("free replay: is a no-op", async () => {
    const { db, store } = makeDb({
      [usagePath("u1")]: { sessionCount: 1 },
      [idemPath("u1")]: { uid: "u1", sessionId: "s", replayCount: 0 },
    });
    const gate = gateWith(db);
    const start = await gate.reserve("u1", KEY, 6);
    await gate.refund("u1", KEY, start);
    expect(store.get(usagePath("u1"))?.sessionCount).toBe(1);
    expect(store.has(idemPath("u1"))).toBe(true);
  });

  it("does not throw when the usage doc is missing", async () => {
    const { db } = makeDb();
    await expect(
      gateWith(db).refund("u1", KEY, {
        sessionId: "s",
        remaining: 0,
        deduped: false,
        charged: true,
        usageKey: USAGE_KEY,
      })
    ).resolves.toBeUndefined();
  });
});
```

- [ ] **Step 2: dialogue 핸들러 테스트를 새 환불 계약으로 수정**

`functions/test/dialogue-handler.test.ts` 의 `fakeGate` 함수 전체를 다음으로 교체:

```ts
/** start gate double with a spyable refund and a configurable reserve result/throw. */
function fakeGate(opts: {
  reserve?: Partial<StartResult>;
  throwLimit?: boolean;
}): { gate: StartGate; refunds: Array<[string, string, string]> } {
  const refunds: Array<[string, string, string]> = [];
  const gate: StartGate = {
    async reserve() {
      if (opts.throwLimit) {
        throw new DailyLimitError("at limit");
      }
      return {
        sessionId: "sess-1",
        remaining: 2,
        deduped: false,
        charged: true,
        usageKey: "20231114",
        ...opts.reserve,
      };
    },
    async refund(uid, idempotencyKey, start) {
      refunds.push([uid, idempotencyKey, start.usageKey]);
    },
  };
  return { gate, refunds };
}
```

같은 파일에서:
- `expect(refunds).toEqual([["00000000-0000-4000-8000-0000000000a1", "20231114"]]); // fresh start → refunded` 를
  `expect(refunds).toEqual([["u1", "00000000-0000-4000-8000-0000000000a1", "20231114"]]); // fresh start → refunded` 로 바꾼다.
- `it("does NOT refund a deduped replay whose generation fails"` 테스트의 `fakeGate({ reserve: { deduped: true } })` 를 `fakeGate({ reserve: { deduped: true, charged: false } })` 로 바꾸고, 테스트 이름을 `"does NOT refund a free (uncharged) replay whose generation fails"` 로 바꾼다.
- 그 테스트 바로 뒤에 추가:

```ts
  it("refunds a paid (charged) replay whose generation fails", async () => {
    const res = recorder();
    const { gate, refunds } = fakeGate({ reserve: { deduped: true, charged: true } });
    await handle(
      req({
        task: "dialogue",
        idempotencyKey: "00000000-0000-4000-8000-0000000000a1",
        payload: { level: "easy", topic: "t", length: 10, firstSession: false },
      }),
      res,
      { startGate: gate, provider: streamProvider([], true) }
    );
    expect(refunds).toEqual([["u1", "00000000-0000-4000-8000-0000000000a1", "20231114"]]);
  });
```

- [ ] **Step 3: 실패 확인**

Run: `npx jest test/start-gate.test.ts test/dialogue-handler.test.ts`
Expected: FAIL — `FREE_REPLAYS` 미export, `charged` 속성·`doc` 메서드 타입 오류(ts-jest 컴파일 에러).

- [ ] **Step 4: `start-gate.ts` 전체 교체**

`functions/src/llm/start-gate.ts`:

```ts
/**
 * Dialogue start gate — M1-02 (backend-functions.md §7). A single Firestore transaction serializes
 * three concerns per `task=dialogue`: idempotent dedup, the PER-USER daily-free-session limit, and
 * ephemeral `sessions/{id}` creation — all in one commit. On terminal generation failure a separate
 * best-effort refund transaction reverses a charged usage increment (and, for a fresh start, deletes
 * the idempotency key so a reused key becomes a fresh start).
 *
 * Design mirrors session-cap.ts:
 * - `evaluateStart` is the PURE limit decision (free of firebase-admin types), unit-testable alone.
 * - `firestoreStartGate` wraps it in a transaction and owns UUID minting, session creation, refund.
 *
 * Usage lives at `users/{uid}/usage/{yyyymmdd}` (one counter doc per user per KST day — shared with
 * the tts quota's `ttsCount`, so writes MERGE). Idempotency keys are namespaced `{uid}_{key}` so two
 * users' keys never collide. A replayed key regenerates the script, so only the first
 * `FREE_REPLAYS` replays are free (legit transport retries); later replays charge a daily slot.
 */
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { randomUUID } from "node:crypto";
import { kstDateKey } from "../config/kst";
import { ErrorCode } from "../types/protocol";
import { SESSION_TTL_MS } from "./session-cap";

/** default daily free-session limit when config/limits is absent (firestore-schema.md:286). */
export const DEFAULT_DAILY_FREE_SESSIONS = 3;

/** idempotency dedup window — decision #21; must outlive the transport retry window. */
const IDEMPOTENCY_TTL_MS = 24 * 60 * 60 * 1000; // 24h

/**
 * Replays of one idempotency key that do NOT consume a daily slot. The client's retry reuses the
 * key (DialogueGenerationCoordinator.retry), e.g. after a mid-stream drop where the server already
 * succeeded — those stay free. Every replay regenerates the script, so past this allowance each
 * replay is charged like a new start (blocks unlimited free generation by resending one key).
 */
export const FREE_REPLAYS = 2;

/** daily free-session limit reached — mapped to 429 DAILY_LIMIT_EXCEEDED. */
export class DailyLimitError extends Error {
  readonly code = ErrorCode.DAILY_LIMIT_EXCEEDED;
  constructor(what: string) {
    super(`DAILY_LIMIT_EXCEEDED: ${what}`);
    this.name = "DailyLimitError";
  }
}

/**
 * Pure daily-limit decision. Returns the remaining count AFTER this start would be counted
 * (limit − (sessionCount + 1)), or throws DailyLimitError when already at/over the limit.
 */
export function evaluateStart(sessionCount: number, limit: number): number {
  if (sessionCount >= limit) {
    throw new DailyLimitError(`sessionCount ${sessionCount} >= limit ${limit}`);
  }
  return limit - (sessionCount + 1);
}

/** Result of a start reservation. */
export interface StartResult {
  /** server-minted (fresh) or replayed (dedup) session id. */
  sessionId: string;
  /** free sessions left for the caller's KST day. */
  remaining: number;
  /** true when an existing, unexpired idempotency key was replayed (sessionId is the original). */
  deduped: boolean;
  /** true when THIS call incremented usage (fresh start, or a replay past FREE_REPLAYS). Only a
   *  charged start is refunded on terminal failure. */
  charged: boolean;
  /** the KST day key this start counted against — refund() targets the exact day. */
  usageKey: string;
}

/** Reserve a start slot (single transaction) and, on terminal failure, refund it (best-effort). */
export interface StartGate {
  reserve(
    uid: string,
    idempotencyKey: string,
    turnCount: number
  ): Promise<StartResult>;
  refund(uid: string, idempotencyKey: string, start: StartResult): Promise<void>;
}

/** resolves a live limit (config/limits, with fallback). */
export type LimitProvider = () => Promise<number>;

/** Minimal structural view of the Firestore APIs used here — lets tests inject a fake. */
export interface DocSnapLike {
  exists: boolean;
  data(): Record<string, unknown> | undefined;
}
export interface DocRefLike {
  get(): Promise<DocSnapLike>;
}
export interface TxnLike {
  get(ref: unknown): Promise<DocSnapLike>;
  set(ref: unknown, data: Record<string, unknown>, options?: { merge?: boolean }): void;
  update(ref: unknown, data: Record<string, unknown>): void;
  delete(ref: unknown): void;
}
export interface DbLike {
  collection(name: string): { doc(id: string): unknown };
  /** slash-separated document path — reaches the per-user `users/{uid}/usage/{day}` doc. */
  doc(path: string): unknown;
  runTransaction<T>(fn: (txn: TxnLike) => Promise<T>): Promise<T>;
}

/** per-user daily usage doc (sessionCount for dialogue starts, ttsCount for tts). */
export function usageDocPath(uid: string, usageKey: string): string {
  return `users/${uid}/usage/${usageKey}`;
}

/** idempotency doc id, namespaced by uid so keys never collide or leak across users. */
export function idempotencyDocId(uid: string, idempotencyKey: string): string {
  return `${uid}_${idempotencyKey}`;
}

/** read a numeric field off a snapshot, defaulting to 0 when absent/non-number. */
function readNumber(snap: DocSnapLike, field: string): number {
  const v = snap.exists ? snap.data()?.[field] : undefined;
  return typeof v === "number" ? v : 0;
}

/** epoch millis of a Timestamp-like field, or undefined when absent. */
function readMillis(snap: DocSnapLike, field: string): number | undefined {
  const v = snap.exists ? snap.data()?.[field] : undefined;
  if (v && typeof (v as { toMillis?: unknown }).toMillis === "function") {
    return (v as { toMillis(): number }).toMillis();
  }
  return undefined;
}

/**
 * Live limit provider reading `config/limits.dailyFreeSessions` with a constant fallback
 * (decision #22). Read OUTSIDE the start transaction: the limit is a slowly-tuned config value,
 * not part of the atomic dedup+usage+session invariant, so a slightly-stale read is acceptable
 * and it keeps config/limits out of every start's contention set.
 */
export function firestoreLimitProvider(
  db: DbLike = getFirestore() as unknown as DbLike,
  fallback: number = DEFAULT_DAILY_FREE_SESSIONS
): LimitProvider {
  return async () => {
    try {
      const ref = db.collection("config").doc("limits") as DocRefLike;
      const snap = await ref.get();
      const v = snap.exists ? snap.data()?.dailyFreeSessions : undefined;
      return typeof v === "number" && v > 0 ? v : fallback;
    } catch {
      return fallback;
    }
  };
}

/**
 * Firestore-backed start gate. `uuid`/`now` are injectable for deterministic tests; `db` defaults
 * to `getFirestore()`. The limit is read via `limitProvider` before the transaction body.
 */
export function firestoreStartGate(
  limitProvider: LimitProvider,
  db: DbLike = getFirestore() as unknown as DbLike,
  uuid: () => string = () => randomUUID(),
  now: () => number = () => Date.now()
): StartGate {
  return {
    async reserve(uid, idempotencyKey, turnCount) {
      const limit = await limitProvider();
      const nowMs = now();
      const usageKey = kstDateKey(nowMs);
      return db.runTransaction(async (txn) => {
        const idemRef = db.collection("idempotency").doc(idempotencyDocId(uid, idempotencyKey));
        const usageRef = db.doc(usageDocPath(uid, usageKey));
        const idemSnap = await txn.get(idemRef);
        const usageSnap = await txn.get(usageRef);
        const sessionCount = readNumber(usageSnap, "sessionCount");
        const stamp = Timestamp.fromMillis(nowMs);

        const expiresAtMs = readMillis(idemSnap, "expiresAt");
        const liveKey =
          idemSnap.exists && (expiresAtMs === undefined || expiresAtMs > nowMs);
        if (liveKey) {
          const sessionId = String(idemSnap.data()?.sessionId ?? "");
          const replayCount = readNumber(idemSnap, "replayCount");
          if (replayCount < FREE_REPLAYS) {
            // Free replay: usage untouched; `remaining` reflects the already-counted state.
            txn.update(idemRef, { replayCount: replayCount + 1 });
            return {
              sessionId,
              remaining: Math.max(0, limit - sessionCount),
              deduped: true,
              charged: false,
              usageKey,
            };
          }
          // Past the free allowance: charge like a new start (throws at the limit → no commit).
          const remaining = evaluateStart(sessionCount, limit);
          txn.set(usageRef, { sessionCount: sessionCount + 1, updatedAt: stamp }, { merge: true });
          txn.update(idemRef, { replayCount: replayCount + 1 });
          return { sessionId, remaining, deduped: true, charged: true, usageKey };
        }

        // Fresh start (no key, or an expired one) — throws DailyLimitError at the limit.
        const remaining = evaluateStart(sessionCount, limit);
        const sessionId = uuid();
        txn.set(usageRef, { sessionCount: sessionCount + 1, updatedAt: stamp }, { merge: true });
        txn.set(idemRef, {
          uid,
          sessionId,
          createdAt: stamp,
          expiresAt: Timestamp.fromMillis(nowMs + IDEMPOTENCY_TTL_MS),
          replayCount: 0,
        });
        txn.set(db.collection("sessions").doc(sessionId), {
          uid,
          createdAt: stamp,
          expiresAt: Timestamp.fromMillis(nowMs + SESSION_TTL_MS),
          turnCount,
          callCount: 0,
        });
        return { sessionId, remaining, deduped: false, charged: true, usageKey };
      });
    },

    async refund(uid, idempotencyKey, start) {
      // Only a charged start moved usage. A fresh start also deletes its key so a retry is a fresh
      // start; a paid replay keeps the key (it belongs to the original attempt). Best-effort: a
      // failed refund tolerates slot loss (backend-functions.md §7).
      if (!start.charged) {
        return;
      }
      try {
        await db.runTransaction(async (txn) => {
          const usageRef = db.doc(usageDocPath(uid, start.usageKey));
          const idemRef = db.collection("idempotency").doc(idempotencyDocId(uid, idempotencyKey));
          const usageSnap = await txn.get(usageRef);
          if (usageSnap.exists) {
            const sessionCount = readNumber(usageSnap, "sessionCount");
            txn.update(usageRef, { sessionCount: Math.max(0, sessionCount - 1) });
          }
          if (!start.deduped) {
            txn.delete(idemRef);
          }
        });
      } catch {
        // swallow — slot-loss tolerance (backend-functions.md §7).
      }
    },
  };
}
```

- [ ] **Step 5: `handleDialogue` 환불 분기 수정**

`functions/src/llm/handle.ts` 의 `handleDialogue` 에서

```ts
    if (!start.deduped) {
      await gate.refund(idempotencyKey, start.usageKey);
    }
```

를 다음으로 교체:

```ts
    if (start.charged) {
      await gate.refund(uid, idempotencyKey, start);
    }
```

같은 함수 docstring 의 마지막 두 문장
`Only a FRESH (non-deduped) start refunds: a replayed key's slot belongs to the original attempt, and deleting its idempotency doc would corrupt that attempt's dedup.`
을 다음으로 교체:
`Only a CHARGED start refunds (fresh, or a replay past FREE_REPLAYS); a free replay's slot belongs to the original attempt. The gate decides whether the idempotency doc is deleted.`

- [ ] **Step 6: 통과 확인**

Run: `npx jest test/start-gate.test.ts test/dialogue-handler.test.ts`
Expected: PASS

Run: `npm run build && npm test && npm run lint`
Expected: 빌드 성공(`handler.ts` 는 `firestoreStartGate(firestoreLimitProvider())` 그대로라 수정 불필요), 전체 테스트 통과, 린트 무경고.

- [ ] **Step 7: 계약 문서 갱신**

`docs/design/backend-functions.md` §7 의 번호 목록 1·2 를 다음으로 교체:

```markdown
1. `idempotency/{uid}_{idempotencyKey}` 읽기(uid 네임스페이스 — 사용자 간 키 충돌·sessionId 누출 없음). **있고 `expiresAt` 이 지나지 않았으면** 그 `sessionId` 로 재생성한다. 처음 `FREE_REPLAYS`(=2)회 재요청은 **usage 미증가**(전송 재시도 멱등), 그 이후 재요청은 대본을 매번 새로 생성하므로 **새 시작처럼 usage +1**(한도면 거부) — 한 키 재전송으로 무한 무료 생성 차단(2026-09-24).
2. **없거나 만료됐으면**: `users/{uid}/usage/{kstDate}.sessionCount < config.limits.dailyFreeSessions` 확인 → +1(merge — 같은 문서의 `ttsCount` 보존), **서버 UUID `sessionId`** 발급, `idempotency/{uid}_{key}` 에 `{uid, sessionId, createdAt, expiresAt, replayCount:0}` 기록, **ephemeral 세션 레코드 생성**(§8) — 모두 같은 커밋. 한도 초과면 거부(`{remaining:0}`). 한도는 **사용자별**이다(2026-09-24 이전 구현은 전역 `usage/{date}` 한 문서를 공유하던 버그).
```

같은 절의 환불 문단 첫 문장 뒤에 추가: `환불은 이번 호출이 usage 를 올린 경우(새 시작 또는 유료 재요청)에만 하며, 키 삭제는 새 시작일 때만 한다.`

그리고 `> KST 일경계로 \`usage/{yyyymmdd}\` 산출` 줄을 `> KST 일경계로 \`users/{uid}/usage/{yyyymmdd}\` 산출(streak와 일관). 일일 캡은 **dialogue 시작만** 카운트(tts 는 같은 문서의 \`ttsCount\`, §12).` 로 교체.

- [ ] **Step 8: Commit**

```bash
git add functions/src/llm/start-gate.ts functions/src/llm/handle.ts functions/test/start-gate.test.ts functions/test/dialogue-handler.test.ts docs/design/backend-functions.md
git commit -m "$(cat <<'EOF'
fix(functions): per-user daily usage and bounded free idempotency replays

The daily session counter was one global usage/{day} doc shared by every
user, and replaying an idempotency key regenerated the script for free
forever. Usage now lives at users/{uid}/usage/{day}, keys are namespaced
by uid, and only the first 2 replays are free.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: 요약 세션당 6회 상한

`summary` 는 요청 1번에 Gemini 3번을 부르는데 게이트가 전혀 없다. 앱은 이미 `sessionId` 를 보내고(`SummaryContracts.kt:21`) 429 를 중립 한도 상태로 처리한다(`SummarySseStream.kt:89-90`). 세션 문서에 피드백용 `callCount` 와 분리된 `summaryCount` 를 둔다.

**Files:**
- Modify: `functions/src/llm/session-cap.ts` (파일 끝에 요약 게이트 추가)
- Create: `functions/test/summary-gate.test.ts`
- Modify: `functions/src/llm/handle.ts` (`HandlerDeps`, summary 라우팅, `handleSummary`)
- Modify: `functions/src/llm/handler.ts` (실제 게이트 주입)
- Modify: `functions/test/summary.test.ts`
- Modify: `docs/design/backend-functions.md` §8

**Interfaces:**
- Consumes: `isUuid` (Task 1, `./request-guards`), `CapExceededError`, `SessionInvalidError`, `SESSION_TTL_MS`, `DbLike`(session-cap 의 것: `collection` + `runTransaction`, TxnLike `get`/`update`)
- Produces:
  - `SUMMARY_CAP = 6`
  - `evaluateSummarySlot(state: { uid: string; summaryCount: number } | undefined, uid: string, cap: number): number`
  - `interface SummaryGate { reserve(uid: string, sessionId: string): Promise<void> }`
  - `firestoreSummaryGate(cap?: number, db?: DbLike, now?: () => number): SummaryGate`
  - `HandlerDeps.summaryGate?: SummaryGate`

- [ ] **Step 1: 게이트 단위 테스트 작성**

`functions/test/summary-gate.test.ts`:

```ts
import {
  CapExceededError,
  DbLike,
  SUMMARY_CAP,
  SessionInvalidError,
  evaluateSummarySlot,
  firestoreSummaryGate,
} from "../src/llm/session-cap";

describe("evaluateSummarySlot (pure)", () => {
  it("returns the next count under the cap", () => {
    expect(evaluateSummarySlot({ uid: "u1", summaryCount: 0 }, "u1", SUMMARY_CAP)).toBe(1);
  });

  it("rejects a missing or foreign session", () => {
    expect(() => evaluateSummarySlot(undefined, "u1", SUMMARY_CAP)).toThrow(SessionInvalidError);
    expect(() => evaluateSummarySlot({ uid: "u2", summaryCount: 0 }, "u1", SUMMARY_CAP)).toThrow(
      SessionInvalidError
    );
  });

  it("rejects at the cap", () => {
    expect(() =>
      evaluateSummarySlot({ uid: "u1", summaryCount: SUMMARY_CAP }, "u1", SUMMARY_CAP)
    ).toThrow(CapExceededError);
  });
});

function makeDb(seed: Record<string, Record<string, unknown>>): {
  db: DbLike;
  store: Map<string, Record<string, unknown>>;
} {
  const store = new Map<string, Record<string, unknown>>(Object.entries(seed));
  const pathOf = (ref: unknown) => (ref as { path: string }).path;
  const db: DbLike = {
    collection: (name) => ({ doc: (id: string) => ({ path: `${name}/${id}` }) }),
    async runTransaction(fn) {
      return fn({
        async get(ref) {
          const value = store.get(pathOf(ref));
          return { exists: value !== undefined, data: () => value };
        },
        update(ref, data) {
          const path = pathOf(ref);
          store.set(path, { ...(store.get(path) ?? {}), ...data });
        },
      });
    },
  };
  return { db, store };
}

describe("firestoreSummaryGate", () => {
  it("counts summaryCount without touching the shared feedback callCount", async () => {
    // callCount 30 = turnCount 10 × factor 3 → the feedback budget is spent, summary must still work.
    const { db, store } = makeDb({ "sessions/s": { uid: "u1", turnCount: 10, callCount: 30 } });
    await firestoreSummaryGate(SUMMARY_CAP, db, () => 0).reserve("u1", "s");
    expect(store.get("sessions/s")?.summaryCount).toBe(1);
    expect(store.get("sessions/s")?.callCount).toBe(30);
  });

  it(`rejects call ${SUMMARY_CAP + 1}`, async () => {
    const { db } = makeDb({ "sessions/s": { uid: "u1", summaryCount: SUMMARY_CAP } });
    await expect(firestoreSummaryGate(SUMMARY_CAP, db, () => 0).reserve("u1", "s")).rejects.toBeInstanceOf(
      CapExceededError
    );
  });

  it("rejects a foreign session", async () => {
    const { db } = makeDb({ "sessions/s": { uid: "u2", summaryCount: 0 } });
    await expect(firestoreSummaryGate(SUMMARY_CAP, db, () => 0).reserve("u1", "s")).rejects.toBeInstanceOf(
      SessionInvalidError
    );
  });
});
```

- [ ] **Step 2: 기존 summary 핸들러 테스트에 sessionId·게이트를 넣는다**

```bash
cd functions
sed -i '' 's/{ task: "summary", payload/{ task: "summary", sessionId: SID, payload/g' test/summary.test.ts
sed -i '' 's/^      { provider }$/      { provider, summaryGate: okSummaryGate }/' test/summary.test.ts
grep -c 'summaryGate: okSummaryGate' test/summary.test.ts
```

Expected: `8` (summary 핸들러 테스트 중 `{ provider }` 로 부르던 8곳).

`test/summary.test.ts` 의 import 에 추가:

```ts
import {
  CapExceededError,
  SessionInvalidError,
  SummaryGate,
} from "../src/llm/session-cap";
```

`const FULL_PAYLOAD = {...};` 선언 바로 뒤에 추가:

```ts
const SID = "00000000-0000-4000-8000-000000000001";
const okSummaryGate: SummaryGate = {
  async reserve() {
    /* allow */
  },
};
```

- [ ] **Step 3: 새 핸들러 실패 테스트 작성**

`test/summary.test.ts` 파일 끝에 추가:

```ts
describe("summary session gate", () => {
  function spyGate(err?: Error): { gate: SummaryGate; calls: Array<[string, string]> } {
    const calls: Array<[string, string]> = [];
    const gate: SummaryGate = {
      async reserve(uid, sessionId) {
        calls.push([uid, sessionId]);
        if (err) {
          throw err;
        }
      },
    };
    return { gate, calls };
  }

  it("400 INVALID_PAYLOAD when sessionId is missing (gate untouched, no stream)", async () => {
    const res = recorder();
    const { provider } = summaryProvider();
    const { gate, calls } = spyGate();
    await handle(
      req({ authorization: "Bearer valid" }, { task: "summary", payload: FULL_PAYLOAD }),
      res,
      { provider, summaryGate: gate }
    );
    expect(res.statusCode).toBe(400);
    expect(res.jsonBody).toEqual({ code: ErrorCode.INVALID_PAYLOAD });
    expect(calls).toHaveLength(0);
    expect(res.writes).toHaveLength(0);
  });

  it("reserves a slot for the caller's session before streaming", async () => {
    const res = recorder();
    const { provider } = summaryProvider();
    const { gate, calls } = spyGate();
    await handle(
      req({ authorization: "Bearer valid" }, { task: "summary", sessionId: SID, payload: FULL_PAYLOAD }),
      res,
      { provider, summaryGate: gate }
    );
    expect(calls).toEqual([["u1", SID]]);
    expect(res.writes.length).toBeGreaterThan(0);
  });

  it("429 CAP_EXCEEDED pre-stream when the summary cap is reached (no Gemini call)", async () => {
    const res = recorder();
    const { provider, calls: geminiCalls } = summaryProvider();
    const { gate } = spyGate(new CapExceededError("summary cap"));
    await handle(
      req({ authorization: "Bearer valid" }, { task: "summary", sessionId: SID, payload: FULL_PAYLOAD }),
      res,
      { provider, summaryGate: gate }
    );
    expect(res.statusCode).toBe(429);
    expect(res.jsonBody).toEqual({ code: ErrorCode.CAP_EXCEEDED });
    expect(geminiCalls).toHaveLength(0);
    expect(res.writes).toHaveLength(0);
  });

  it("403 SESSION_INVALID for a foreign or missing session", async () => {
    const res = recorder();
    const { provider } = summaryProvider();
    const { gate } = spyGate(new SessionInvalidError("foreign"));
    await handle(
      req({ authorization: "Bearer valid" }, { task: "summary", sessionId: SID, payload: FULL_PAYLOAD }),
      res,
      { provider, summaryGate: gate }
    );
    expect(res.statusCode).toBe(403);
    expect(res.jsonBody).toEqual({ code: ErrorCode.SESSION_INVALID });
  });
});
```

- [ ] **Step 4: 실패 확인**

Run: `npx jest test/summary-gate.test.ts test/summary.test.ts`
Expected: FAIL — `SUMMARY_CAP`/`SummaryGate` 등 미export 컴파일 에러.

- [ ] **Step 5: 요약 게이트 구현**

`functions/src/llm/session-cap.ts` 파일 끝에 추가:

```ts

/**
 * Per-session summary call cap (2026-09-24). `task=summary` fans out to THREE Gemini calls per
 * request and was previously ungated. It gets its own counter (`summaryCount`), separate from the
 * shared feedback/speaking/deep `callCount`, so a session that spent its feedback budget can still
 * produce a summary. 6 = the initial call + up to five per-section retries (summary.ts retries by
 * resending only the failed sections).
 */
export const SUMMARY_CAP = 6;

/** Pure summary-cap decision: returns the summaryCount to commit, or throws like evaluateSlot. */
export function evaluateSummarySlot(
  state: { uid: string; summaryCount: number } | undefined,
  uid: string,
  cap: number
): number {
  if (!state) {
    throw new SessionInvalidError("no session record");
  }
  if (state.uid !== uid) {
    throw new SessionInvalidError("session not owned by caller");
  }
  if (state.summaryCount >= cap) {
    throw new CapExceededError(`summaryCount ${state.summaryCount} >= cap ${cap}`);
  }
  return state.summaryCount + 1;
}

/** Reserve a summary slot on the caller's session (throws on cap/invalid). No refund: partial
 *  failures are normal and retried per section within the cap. */
export interface SummaryGate {
  reserve(uid: string, sessionId: string): Promise<void>;
}

/** Firestore-backed summary gate — same transaction shape as firestoreSessionGate.reserve. */
export function firestoreSummaryGate(
  cap: number = SUMMARY_CAP,
  db: DbLike = getFirestore() as unknown as DbLike,
  now: () => number = () => Date.now()
): SummaryGate {
  return {
    async reserve(uid, sessionId) {
      const ref = db.collection("sessions").doc(sessionId);
      await db.runTransaction(async (txn) => {
        const snap = await txn.get(ref);
        const d = snap.exists ? snap.data() ?? {} : undefined;
        const state =
          d === undefined
            ? undefined
            : {
                uid: typeof d.uid === "string" ? d.uid : "",
                summaryCount: typeof d.summaryCount === "number" ? d.summaryCount : 0,
              };
        const next = evaluateSummarySlot(state, uid, cap);
        txn.update(ref, {
          summaryCount: next,
          expiresAt: Timestamp.fromMillis(now() + SESSION_TTL_MS),
        });
      });
    },
  };
}
```

- [ ] **Step 6: `handle.ts` 에 연결**

(a) import 교체 — 기존

```ts
import {
  CapExceededError,
  SessionGate,
  SessionInvalidError,
} from "./session-cap";
```

를

```ts
import {
  CapExceededError,
  SessionGate,
  SessionInvalidError,
  SummaryGate,
} from "./session-cap";
```

로 바꾼다.

(b) `HandlerDeps` 의 `startGate?: StartGate;` 뒤에 추가:

```ts
  /** per-session summary cap (`sessions/{id}.summaryCount`). When absent, summary falls back to
   *  the NOT_IMPLEMENTED stub — same pattern as the other gates. */
  summaryGate?: SummaryGate;
```

(c) 라우팅 교체 — 기존

```ts
      if (task === "summary" && deps.provider) {
        // task=summary, implemented — 3-call orchestration over a single SSE (M2-01).
        await handleSummary(body.payload, deps.provider, res);
```

를

```ts
      if (task === "summary" && deps.provider && deps.summaryGate) {
        // task=summary — per-session summary cap, then 3-call orchestration over one SSE (M2-01).
        await handleSummary(body, uid, deps.provider, deps.summaryGate, res);
```

로 바꾼다.

(d) `handleSummary` 함수 전체(docstring 포함)를 다음으로 교체:

```ts
/**
 * Handle `task=summary` (SSE). Validates sessionId + payload and reserves a per-session summary
 * slot BEFORE opening the stream, so a malformed body → 400, a foreign/missing session → 403 and a
 * cap rejection → 429 all land with headers unsent (the client maps the pre-stream 429 to its
 * neutral QuotaExceeded state — SummarySseStream.kt). Once reserved, opens the stream and hands off
 * to the 3-call orchestrator, which owns all card/done emission and closes the stream.
 */
async function handleSummary(
  body: Partial<RequestBody>,
  uid: string,
  provider: LlmProvider,
  gate: SummaryGate,
  res: HandlerResponse
): Promise<void> {
  const sessionId =
    typeof body.sessionId === "string" ? body.sessionId.trim() : "";
  let parsed;
  try {
    if (!isUuid(sessionId)) {
      throw new InvalidSummaryPayloadError("missing or malformed sessionId");
    }
    parsed = parseSummaryPayload(body.payload);
  } catch (e) {
    if (e instanceof InvalidSummaryPayloadError) {
      res.status(400).json({ code: ErrorCode.INVALID_PAYLOAD });
      return;
    }
    throw e;
  }

  try {
    await gate.reserve(uid, sessionId);
  } catch (e) {
    if (e instanceof CapExceededError) {
      res.status(429).json({ code: ErrorCode.CAP_EXCEEDED });
      return;
    }
    if (e instanceof SessionInvalidError) {
      res.status(403).json({ code: ErrorCode.SESSION_INVALID });
      return;
    }
    throw e; // → outer catch 500 (headers still unsent)
  }

  openSse(res);
  await orchestrateSummary(parsed, provider, res);
}
```

(e) `functions/src/llm/handler.ts` — import 에 `firestoreSummaryGate` 추가:

```ts
import { firestoreSessionGate, firestoreSummaryGate } from "./session-cap";
```

그리고 `const startGate = ...;` 줄 뒤에 추가:

```ts
    // Per-session summary cap (summaryCount, separate from callCount) — 2026-09-24.
    const summaryGate = firestoreSummaryGate();
```

`handle(...)` 호출의 deps 를 `{ provider, sessionGate, startGate, summaryGate }` 로 바꾼다.

- [ ] **Step 7: 통과 확인**

Run: `npx jest test/summary-gate.test.ts test/summary.test.ts test/handler.test.ts`
Expected: PASS

Run: `npm run build && npm test && npm run lint`
Expected: 모두 통과.

- [ ] **Step 8: 계약 문서 갱신**

`docs/design/backend-functions.md` §8 의 `- **필드:**` 줄에서 `{uid, createdAt, expiresAt, turnCount, callCount}` 를 `{uid, createdAt, expiresAt, turnCount, callCount, summaryCount}` 로 바꾸고, `- **검증(feedback/speaking/summary 매 호출):**` 를 `- **검증(feedback/feedbackDeep/speaking 매 호출):**` 로 바꾼 뒤, 그 항목 바로 아래에 추가:

```markdown
- **요약 캡(2026-09-24):** `summary` 는 공유 `callCount` 가 아니라 별도 `summaryCount < SUMMARY_CAP(=6)` 로 센다(첫 요청 + 섹션 재시도). 피드백 예산을 다 쓴 세션도 요약은 받을 수 있다. 존재·소유 검사는 동일(403), 캡 도달은 스트림 전 429 `CAP_EXCEEDED`(클라 중립 QuotaExceeded). 부분 실패는 환불하지 않는다.
```

- [ ] **Step 9: Commit**

```bash
git add functions/src/llm/session-cap.ts functions/src/llm/handle.ts functions/src/llm/handler.ts functions/test/summary-gate.test.ts functions/test/summary.test.ts docs/design/backend-functions.md
git commit -m "$(cat <<'EOF'
fix(functions): cap summary calls per session

Summary fanned out to three Gemini calls per request with no gate. It now
requires the caller's sessionId and a separate summaryCount (max 6).

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: 서버 음성(tts) 1인당 하루 300문장 + 문장 길이 500자

tts 는 로그인 토큰만 있으면 무제한이고 문장 길이 제한도 없다. tts 요청에는 sessionId 가 없고 복습 화면에서도 쓰이므로 uid 하루 카운터를 쓴다. 앱은 tts 실패 시 기기 음성으로 대체한다(`TtsPlaybackCoordinator.kt:303-306`).

**Files:**
- Modify: `functions/src/llm/start-gate.ts` (`firestoreLimitProvider` 에 `field` 인자)
- Create: `functions/src/llm/tts-quota.ts`
- Create: `functions/test/tts-quota.test.ts`
- Modify: `functions/src/llm/tts.ts` (`MAX_TTS_TEXT_CHARS`)
- Modify: `functions/src/llm/handle.ts` (`HandlerDeps`, tts 라우팅, `handleTts`)
- Modify: `functions/src/llm/handler.ts`
- Modify: `functions/test/tts.test.ts`
- Modify: `docs/design/backend-functions.md` §12, `docs/design/firestore-schema.md:33`

**Interfaces:**
- Consumes (Task 2): `usageDocPath`, `DbLike` (with `doc`), `TxnLike.set(..., {merge})`, `DailyLimitError`, `LimitProvider`, `firestoreLimitProvider`
- Produces:
  - `firestoreLimitProvider(db?: DbLike, fallback?: number, field?: string): LimitProvider` (`field` 기본 `"dailyFreeSessions"`)
  - `DEFAULT_DAILY_TTS_LINES = 300`
  - `interface TtsQuota { reserve(uid: string): Promise<void> }` (한도면 `DailyLimitError`)
  - `firestoreTtsQuota(limitProvider: LimitProvider, db?: DbLike, now?: () => number): TtsQuota`
  - `MAX_TTS_TEXT_CHARS = 500` (`src/llm/tts.ts`)
  - `HandlerDeps.ttsQuota?: TtsQuota`

- [ ] **Step 1: 쿼터 단위 테스트 작성**

`functions/test/tts-quota.test.ts`:

```ts
import { kstDateKey } from "../src/config/kst";
import {
  DailyLimitError,
  DbLike,
  TxnLike,
  firestoreLimitProvider,
} from "../src/llm/start-gate";
import { DEFAULT_DAILY_TTS_LINES, firestoreTtsQuota } from "../src/llm/tts-quota";

const NOW = 1_700_000_000_000;
const DAY = kstDateKey(NOW);
const usagePath = (uid: string) => `users/${uid}/usage/${DAY}`;

function makeDb(seed: Record<string, Record<string, unknown>> = {}): {
  db: DbLike;
  store: Map<string, Record<string, unknown>>;
} {
  const store = new Map<string, Record<string, unknown>>(Object.entries(seed));
  const pathOf = (ref: unknown) => (ref as { path: string }).path;
  const txn: TxnLike = {
    async get(ref) {
      const value = store.get(pathOf(ref));
      return { exists: value !== undefined, data: () => value };
    },
    set(ref, data, options) {
      const path = pathOf(ref);
      store.set(path, options?.merge ? { ...(store.get(path) ?? {}), ...data } : { ...data });
    },
    update(ref, data) {
      const path = pathOf(ref);
      store.set(path, { ...(store.get(path) ?? {}), ...data });
    },
    delete(ref) {
      store.delete(pathOf(ref));
    },
  };
  const db: DbLike = {
    collection: (name) => ({ doc: (id: string) => ({ path: `${name}/${id}` }) }),
    doc: (path) => ({ path }),
    runTransaction: async (fn) => fn(txn),
  };
  return { db, store };
}

const limit = (n: number) => async () => n;

describe("firestoreTtsQuota", () => {
  it("counts a line on the caller's usage doc and keeps sessionCount", async () => {
    const { db, store } = makeDb({ [usagePath("u1")]: { sessionCount: 2 } });
    await firestoreTtsQuota(limit(300), db, () => NOW).reserve("u1");
    expect(store.get(usagePath("u1"))).toMatchObject({ sessionCount: 2, ttsCount: 1 });
  });

  it("throws DailyLimitError at the limit and writes nothing", async () => {
    const { db, store } = makeDb({ [usagePath("u1")]: { ttsCount: 300 } });
    await expect(firestoreTtsQuota(limit(300), db, () => NOW).reserve("u1")).rejects.toBeInstanceOf(
      DailyLimitError
    );
    expect(store.get(usagePath("u1"))?.ttsCount).toBe(300);
  });

  it("keeps users independent", async () => {
    const { db, store } = makeDb({ [usagePath("u1")]: { ttsCount: 300 } });
    await firestoreTtsQuota(limit(300), db, () => NOW).reserve("u2");
    expect(store.get(usagePath("u2"))?.ttsCount).toBe(1);
  });

  it("defaults to 300 lines", () => {
    expect(DEFAULT_DAILY_TTS_LINES).toBe(300);
  });
});

describe("firestoreLimitProvider field", () => {
  function configDb(data: Record<string, unknown> | undefined): DbLike {
    return {
      collection: () => ({
        doc: () => ({ get: async () => ({ exists: data !== undefined, data: () => data }) }),
      }),
      doc: () => ({}),
      runTransaction: async () => {
        throw new Error("unused");
      },
    };
  }

  it("reads the named field", async () => {
    await expect(
      firestoreLimitProvider(configDb({ dailyTtsLines: 50, dailyFreeSessions: 3 }), 300, "dailyTtsLines")()
    ).resolves.toBe(50);
  });

  it("falls back when the field is absent", async () => {
    await expect(
      firestoreLimitProvider(configDb({ dailyFreeSessions: 3 }), 300, "dailyTtsLines")()
    ).resolves.toBe(300);
  });

  it("still defaults to dailyFreeSessions", async () => {
    await expect(firestoreLimitProvider(configDb({ dailyFreeSessions: 7 }))()).resolves.toBe(7);
  });
});
```

- [ ] **Step 2: tts 핸들러 테스트 갱신 + 실패 테스트 작성**

```bash
cd functions
sed -i '' 's/^      { provider }$/      { provider, ttsQuota: okQuota }/' test/tts.test.ts
grep -c 'ttsQuota: okQuota' test/tts.test.ts
```

Expected: `5` (tts 핸들러 테스트 중 `{ provider }` 로 부르던 5곳. "falls back to the 501 stub when no provider" 테스트는 provider 가 없으므로 그대로).

`test/tts.test.ts` 의 기존 `import { resolveVoiceName, parseTtsPayload } from "../src/llm/tts";` 줄을 다음 세 줄로 교체:

```ts
import { InvalidTtsPayloadError, MAX_TTS_TEXT_CHARS, parseTtsPayload, resolveVoiceName } from "../src/llm/tts";
import { DailyLimitError } from "../src/llm/start-gate";
import { TtsQuota } from "../src/llm/tts-quota";
```

`function req(...)` 선언 바로 뒤에 추가:

```ts
const okQuota: TtsQuota = {
  async reserve() {
    /* allow */
  },
};
```

`describe("tts handler pipeline", ...)` 안 마지막에 추가:

```ts
  it("reserves the caller's daily tts quota before synthesizing", async () => {
    const res = recorder();
    const { provider } = fakeProvider();
    const uids: string[] = [];
    const quota: TtsQuota = {
      async reserve(uid) {
        uids.push(uid);
      },
    };
    await handle(
      req({ authorization: "Bearer valid" }, { task: "tts", payload: { text: "Hi" } }),
      res,
      { provider, ttsQuota: quota }
    );
    expect(res.statusCode).toBe(200);
    expect(uids).toEqual(["u1"]);
  });

  it("429 DAILY_LIMIT_EXCEEDED when the daily tts quota is spent (no synthesis)", async () => {
    const res = recorder();
    const { provider, calls } = fakeProvider();
    const quota: TtsQuota = {
      async reserve() {
        throw new DailyLimitError("tts");
      },
    };
    await handle(
      req({ authorization: "Bearer valid" }, { task: "tts", payload: { text: "Hi" } }),
      res,
      { provider, ttsQuota: quota }
    );
    expect(res.statusCode).toBe(429);
    expect(res.jsonBody).toEqual({ code: ErrorCode.DAILY_LIMIT_EXCEEDED });
    expect(calls).toHaveLength(0);
  });

  it("400 INVALID_PAYLOAD for text over the length cap (quota untouched)", async () => {
    const res = recorder();
    const { provider } = fakeProvider();
    const uids: string[] = [];
    const quota: TtsQuota = {
      async reserve(uid) {
        uids.push(uid);
      },
    };
    await handle(
      req(
        { authorization: "Bearer valid" },
        { task: "tts", payload: { text: "a".repeat(MAX_TTS_TEXT_CHARS + 1) } }
      ),
      res,
      { provider, ttsQuota: quota }
    );
    expect(res.statusCode).toBe(400);
    expect(res.jsonBody).toEqual({ code: ErrorCode.INVALID_PAYLOAD });
    expect(uids).toHaveLength(0);
  });
```

`describe("tts payload parsing", ...)` 안 마지막에 추가:

```ts
  it("rejects text longer than MAX_TTS_TEXT_CHARS", () => {
    expect(() => parseTtsPayload({ text: "a".repeat(MAX_TTS_TEXT_CHARS + 1) })).toThrow(
      InvalidTtsPayloadError
    );
    expect(parseTtsPayload({ text: "a".repeat(MAX_TTS_TEXT_CHARS) }).text).toHaveLength(
      MAX_TTS_TEXT_CHARS
    );
  });
```

- [ ] **Step 3: 실패 확인**

Run: `npx jest test/tts-quota.test.ts test/tts.test.ts`
Expected: FAIL — `../src/llm/tts-quota` 모듈 없음, `MAX_TTS_TEXT_CHARS` 미export.

- [ ] **Step 4: limit provider 에 필드 인자 추가**

`functions/src/llm/start-gate.ts` 의 `firestoreLimitProvider` 를 다음으로 교체:

```ts
/**
 * Live limit provider reading one `config/limits` field (default `dailyFreeSessions`; the tts quota
 * reads `dailyTtsLines`) with a constant fallback (decision #22). Read OUTSIDE the transaction: the
 * limit is a slowly-tuned config value, not part of the atomic invariant, so a slightly-stale read
 * is acceptable and it keeps config/limits out of every request's contention set.
 */
export function firestoreLimitProvider(
  db: DbLike = getFirestore() as unknown as DbLike,
  fallback: number = DEFAULT_DAILY_FREE_SESSIONS,
  field = "dailyFreeSessions"
): LimitProvider {
  return async () => {
    try {
      const ref = db.collection("config").doc("limits") as DocRefLike;
      const snap = await ref.get();
      const v = snap.exists ? snap.data()?.[field] : undefined;
      return typeof v === "number" && v > 0 ? v : fallback;
    } catch {
      return fallback;
    }
  };
}
```

- [ ] **Step 5: tts 쿼터 구현**

`functions/src/llm/tts-quota.ts`:

```ts
/**
 * Per-user daily tts quota (2026-09-24). `task=tts` carries no sessionId (it also serves the review
 * screen), so it is bounded per uid per KST day on the SAME usage doc as the dialogue start count:
 * `users/{uid}/usage/{yyyymmdd}.ttsCount`. Writes merge so `sessionCount` is preserved. Over the
 * limit the handler answers 429 and the client falls back to device TTS
 * (TtsPlaybackCoordinator.synthesize → null → device), so the learner never sees an error.
 */
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { kstDateKey } from "../config/kst";
import { DailyLimitError, DbLike, LimitProvider, usageDocPath } from "./start-gate";

/** default server-voice lines per user per KST day when config/limits.dailyTtsLines is absent. */
export const DEFAULT_DAILY_TTS_LINES = 300;

/** Reserve one server tts line for the caller's KST day, or throw DailyLimitError. */
export interface TtsQuota {
  reserve(uid: string): Promise<void>;
}

export function firestoreTtsQuota(
  limitProvider: LimitProvider,
  db: DbLike = getFirestore() as unknown as DbLike,
  now: () => number = () => Date.now()
): TtsQuota {
  return {
    async reserve(uid) {
      const limit = await limitProvider();
      const nowMs = now();
      const ref = db.doc(usageDocPath(uid, kstDateKey(nowMs)));
      await db.runTransaction(async (txn) => {
        const snap = await txn.get(ref);
        const v = snap.exists ? snap.data()?.ttsCount : undefined;
        const count = typeof v === "number" ? v : 0;
        if (count >= limit) {
          throw new DailyLimitError(`ttsCount ${count} >= limit ${limit}`);
        }
        txn.set(
          ref,
          { ttsCount: count + 1, updatedAt: Timestamp.fromMillis(nowMs) },
          { merge: true }
        );
      });
    },
  };
}
```

- [ ] **Step 6: 문장 길이 상한**

`functions/src/llm/tts.ts` — `export class InvalidTtsPayloadError extends Error {}` 바로 뒤에 추가:

```ts

/**
 * Max opponent-line / review-card text per tts call. Real lines are one or two sentences; the cap
 * bounds audio-output cost and the room for instructions smuggled into the synthesis prompt.
 */
export const MAX_TTS_TEXT_CHARS = 500;
```

`parseTtsPayload` 안의

```ts
  if (!text) {
    throw new InvalidTtsPayloadError("tts payload requires non-empty text");
  }
```

바로 뒤에 추가:

```ts
  if (text.length > MAX_TTS_TEXT_CHARS) {
    throw new InvalidTtsPayloadError(`tts text exceeds ${MAX_TTS_TEXT_CHARS} chars`);
  }
```

- [ ] **Step 7: `handle.ts` 에 연결**

(a) import 추가(`import { isUuid, payloadWithinLimit } from "./request-guards";` 아래):

```ts
import { TtsQuota } from "./tts-quota";
```

(b) `HandlerDeps` 의 `summaryGate?: SummaryGate;` 뒤에 추가:

```ts
  /** per-user daily tts quota (`users/{uid}/usage/{day}.ttsCount`). When absent, tts falls back
   *  to the 501 stub. */
  ttsQuota?: TtsQuota;
```

(c) 라우팅 교체 — 기존

```ts
    } else if (task === "tts" && deps.provider) {
      // JSON transport, implemented — synthesize and return base64 PCM (M1-05).
      await handleTts(body.payload, deps.provider, res);
```

를

```ts
    } else if (task === "tts" && deps.provider && deps.ttsQuota) {
      // JSON transport — per-user daily quota, then synthesize and return base64 PCM (M1-05).
      await handleTts(body.payload, uid, deps.provider, deps.ttsQuota, res);
```

로 바꾼다.

(d) `handleTts` 의 시그니처와 파싱 직후를 수정 — 기존

```ts
async function handleTts(
  payload: unknown,
  provider: LlmProvider,
  res: HandlerResponse
): Promise<void> {
```

를

```ts
async function handleTts(
  payload: unknown,
  uid: string,
  provider: LlmProvider,
  quota: TtsQuota,
  res: HandlerResponse
): Promise<void> {
```

로 바꾸고, 파싱 try/catch 블록(`request = parseTtsPayload(payload);` 를 감싼 블록) 바로 뒤, `try { const response = await synthesizeTts(...` 앞에 추가:

```ts

  // Daily quota BEFORE spending on synthesis. Over the limit → 429; the client falls back to
  // device TTS, so the learner never sees an error.
  try {
    await quota.reserve(uid);
  } catch (e) {
    if (e instanceof DailyLimitError) {
      res.status(429).json({ code: ErrorCode.DAILY_LIMIT_EXCEEDED });
      return;
    }
    throw e;
  }
```

그리고 `handleTts` docstring 의 상태 매핑 설명 끝에 `a spent daily quota → 429 DAILY_LIMIT_EXCEEDED (checked after the payload, before synthesis).` 를 덧붙인다.

(e) `functions/src/llm/handler.ts` — import 수정/추가:

```ts
import { firestoreLimitProvider, firestoreStartGate } from "./start-gate";
import { DEFAULT_DAILY_TTS_LINES, firestoreTtsQuota } from "./tts-quota";
```

`const summaryGate = firestoreSummaryGate();` 뒤에 추가:

```ts
    // Per-user daily tts quota on the same usage doc (config/limits.dailyTtsLines, fallback 300).
    const ttsQuota = firestoreTtsQuota(
      firestoreLimitProvider(undefined, DEFAULT_DAILY_TTS_LINES, "dailyTtsLines")
    );
```

deps 를 `{ provider, sessionGate, startGate, summaryGate, ttsQuota }` 로 바꾼다.

- [ ] **Step 8: 통과 확인**

Run: `npx jest test/tts-quota.test.ts test/tts.test.ts test/start-gate.test.ts`
Expected: PASS

Run: `npm run build && npm test && npm run lint`
Expected: 모두 통과.

- [ ] **Step 9: 문서 갱신**

`docs/design/firestore-schema.md:33` 의 `└─ usage/{yyyymmdd}            # Functions 전용; {sessionCount, updatedAt}` 를 `└─ usage/{yyyymmdd}            # Functions 전용; {sessionCount, ttsCount, updatedAt}` 로 바꾼다.

`docs/design/backend-functions.md` §12 의 `- **rate-limit:**` 항목을 다음으로 교체:

```markdown
- **rate-limit:** 별도 per-instance 리미터 없음(인스턴스>1서 깨짐). 비용은 **(1인당 일일 시작 캡) + (§8 per-session 캡·요약 캡) + (1인당 일일 tts 캡: `users/{uid}/usage/{day}.ttsCount < config.limits.dailyTtsLines`(기본 300), 초과 시 429 → 클라 기기 음성 폴백) + (payload 크기 상한: 텍스트 64KB·speaking 1.5MB·tts 문장 500자) + `maxInstances`(10) + 인증**으로 한정(2026-09-24). 게스트 계정 대량 생성 우회는 App Check 라운드에서 막는다.
```

- [ ] **Step 10: Commit**

```bash
git add functions/src/llm/start-gate.ts functions/src/llm/tts-quota.ts functions/src/llm/tts.ts functions/src/llm/handle.ts functions/src/llm/handler.ts functions/test/tts-quota.test.ts functions/test/tts.test.ts docs/design/backend-functions.md docs/design/firestore-schema.md
git commit -m "$(cat <<'EOF'
fix(functions): per-user daily tts quota and 500-char text cap

tts was unlimited for any signed-in (including anonymous) caller. Each
user now gets 300 server-voice lines per KST day (config/limits.dailyTtsLines);
past that the client falls back to device TTS.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: 병합은 익명 게스트 토큰만

`mergeGuestData` 는 `guestIdToken` 이 익명 계정 것인지 보지 않는다. 다른 사람의 정식 계정 토큰을 가진 사람이 그 데이터를 자기 계정으로 옮기고 원 계정(Firestore + Auth)을 삭제시킬 수 있다. 정상 앱은 충돌 시점의 익명 게스트 토큰만 보낸다(`GoogleAccountLinker.kt` FR-3b (a)).

**Files:**
- Modify: `functions/src/merge/merge.ts` (순수 판정 함수 추가)
- Modify: `functions/src/merge/mergeGuestData.ts:98-109` (토큰 검증 블록)
- Modify: `functions/test/merge.test.ts`

**Interfaces:**
- Produces: `interface GuestTokenClaims { uid: string; firebase?: { sign_in_provider?: string } }`, `isAnonymousGuest(claims: GuestTokenClaims): boolean` (`src/merge/merge.ts`)

- [ ] **Step 1: 실패 테스트 작성**

`functions/test/merge.test.ts` — 파일 상단 `from "../src/merge/merge"` import 목록의 `GuestDoc,` 다음 줄에 `isAnonymousGuest,` 를 추가하고, 파일 끝에 추가:

```ts
describe("isAnonymousGuest (merge source must be a guest)", () => {
  it("accepts an anonymous-provider token", () => {
    expect(isAnonymousGuest({ uid: "g", firebase: { sign_in_provider: "anonymous" } })).toBe(true);
  });

  it("rejects a real account token or one without a provider", () => {
    expect(isAnonymousGuest({ uid: "v", firebase: { sign_in_provider: "google.com" } })).toBe(false);
    expect(isAnonymousGuest({ uid: "v", firebase: { sign_in_provider: "password" } })).toBe(false);
    expect(isAnonymousGuest({ uid: "v" })).toBe(false);
  });
});
```

- [ ] **Step 2: 실패 확인**

Run: `npx jest test/merge.test.ts`
Expected: FAIL — `isAnonymousGuest` 미export.

- [ ] **Step 3: 판정 함수 구현**

`functions/src/merge/merge.ts` 끝에 추가:

```ts

/** the decoded-ID-token fields the guest check reads (DecodedIdToken is structurally assignable). */
export interface GuestTokenClaims {
  uid: string;
  firebase?: { sign_in_provider?: string };
}

/**
 * Only an anonymous (guest) account may be a merge SOURCE. runMerge deletes the source's Firestore
 * subtree and Auth record, so accepting any account's token would let a holder of a stolen real-
 * account token absorb that user's data and delete the account. The client only ever sends the
 * guest token captured before Google sign-in (GoogleAccountLinker FR-3b (a)).
 */
export function isAnonymousGuest(claims: GuestTokenClaims): boolean {
  return claims.firebase?.sign_in_provider === "anonymous";
}
```

- [ ] **Step 4: 콜러블에 연결**

`functions/src/merge/mergeGuestData.ts` 의 import 를 `import { DocData, GuestTokenClaims, MergeStore, isAnonymousGuest, runMerge } from "./merge";` 로 바꾸고, 다음 블록

```ts
  let guestUid: string;
  try {
    guestUid = (await getAuth().verifyIdToken(guestIdToken)).uid;
  } catch {
    throw new HttpsError("unauthenticated", "invalid guest token");
  }

  // 인플레이스 승격(FR-3a)은 클라가 linkWithCredential 로 처리 — target==guest 면 이관할 게 없다.
  if (guestUid === targetUid) {
    return { ok: true, merged: null };
  }
```

를 다음으로 교체:

```ts
  let claims: GuestTokenClaims;
  try {
    claims = await getAuth().verifyIdToken(guestIdToken);
  } catch {
    throw new HttpsError("unauthenticated", "invalid guest token");
  }
  const guestUid = claims.uid;

  // 인플레이스 승격(FR-3a)은 클라가 linkWithCredential 로 처리 — target==guest 면 이관할 게 없다.
  if (guestUid === targetUid) {
    return { ok: true, merged: null };
  }

  // 이관 원본은 익명 게스트만 — 탈취한 정식 계정 토큰으로 남의 데이터를 가져가고 원 계정을 삭제시키는 경로 차단.
  if (!isAnonymousGuest(claims)) {
    throw new HttpsError("permission-denied", "guest token must belong to an anonymous account");
  }
```

- [ ] **Step 5: 통과 확인**

Run: `npx jest test/merge.test.ts`
Expected: PASS

Run: `npm run build && npm test && npm run lint`
Expected: 모두 통과.

- [ ] **Step 6: Commit**

```bash
git add functions/src/merge/merge.ts functions/src/merge/mergeGuestData.ts functions/test/merge.test.ts
git commit -m "$(cat <<'EOF'
fix(functions): only anonymous guest tokens can be a merge source

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: 동시 실행 상한(maxInstances 10) + functions CI

`llm` 에 `maxInstances` 가 없어 공격 시 비용 상한이 없고, functions 테스트는 CI 에서 한 번도 돌지 않는다(`android-ci.yml`·`firestore-rules.yml` 만 존재).

**Files:**
- Modify: `functions/src/llm/options.ts`
- Modify: `functions/src/llm/handler.ts`
- Modify: `functions/test/options.test.ts`
- Create: `.github/workflows/functions.yml`

**Interfaces:**
- Produces: `LLM_MAX_INSTANCES = 10` (`src/llm/options.ts`)

- [ ] **Step 1: 실패 테스트 작성**

`functions/test/options.test.ts` import 에 `LLM_MAX_INSTANCES` 를 추가하고 `describe` 안 끝에 추가:

```ts
  it("caps instances at 10 so a traffic spike or abuse has a hard cost ceiling", () => {
    expect(LLM_MAX_INSTANCES).toBe(10);
  });
```

- [ ] **Step 2: 실패 확인**

Run: `npx jest test/options.test.ts`
Expected: FAIL — `LLM_MAX_INSTANCES` 미export.

- [ ] **Step 3: 구현**

`functions/src/llm/options.ts` 끝에 추가:

```ts

/**
 * Hard ceiling on concurrent `llm` instances (2026-09-24). Each 2nd-gen instance serves many
 * concurrent requests, so 10 covers hundreds of simultaneous learners while bounding the worst-case
 * bill if the endpoint is abused.
 */
export const LLM_MAX_INSTANCES = 10;
```

`functions/src/llm/handler.ts` — options import 에 `LLM_MAX_INSTANCES` 를 추가하고 `onRequest` 옵션에 추가:

```ts
    minInstances: LLM_MIN_INSTANCES,
    maxInstances: LLM_MAX_INSTANCES,
```

- [ ] **Step 4: 통과 확인**

Run: `npx jest test/options.test.ts && npm run build`
Expected: PASS, 빌드 성공.

- [ ] **Step 5: CI 워크플로 추가**

`.github/workflows/functions.yml`:

```yaml
name: Functions

on:
  push:
    branches: [ master ]
    paths: [ 'functions/**', '.github/workflows/functions.yml' ]
  pull_request:
    paths: [ 'functions/**', '.github/workflows/functions.yml' ]

permissions:
  contents: read

jobs:
  test:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: functions
    steps:
      - uses: actions/checkout@v4

      - name: Set up Node 22
        uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: npm
          cache-dependency-path: functions/package-lock.json

      - run: npm ci
      - run: npm run build
      - run: npm run lint
      - run: npm test
```

- [ ] **Step 6: 워크플로가 부르는 명령을 로컬에서 그대로 확인**

```bash
ruby -ryaml -e 'YAML.load_file("../.github/workflows/functions.yml"); puts "yaml ok"'
npm ci && npm run build && npm run lint && npm test
```

Expected: `yaml ok`, 그리고 빌드·린트·테스트 모두 통과.

- [ ] **Step 7: Commit**

```bash
git add functions/src/llm/options.ts functions/src/llm/handler.ts functions/test/options.test.ts .github/workflows/functions.yml
git commit -m "$(cat <<'EOF'
chore(functions): cap llm at 10 instances and run functions tests in CI

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: 배포 · 운영 설정 · 실서버 확인 (사용자 승인 필요)

> 이 태스크는 운영 프로젝트(`oce-v1`)를 바꾼다. **사용자가 명시적으로 승인한 뒤에만** 진행한다. 서브에이전트에 맡기지 않는다.

**Files:** 없음 (배포와 Firestore 콘솔 설정)

- [ ] **Step 1: 배포 전 점검**

```bash
cd functions
ls -a | grep '^\.env'
npm run build && npm test && npm run lint
```

Expected: `.env*` 파일에 `LLM_MIN_INSTANCES` 값이 있다(없으면 비대화형 배포가 파라미터를 요구해 멈춘다 — 현재 운영값 `0`). 테스트 전부 통과.

- [ ] **Step 2: 바뀐 두 함수만 배포 (사용자 승인 후)**

```bash
cd ..
firebase deploy --only functions:llm,functions:mergeGuestData --project oce-v1
```

Expected: 두 함수 모두 `Successful update operation`.

- [ ] **Step 3: 운영 한도 설정 (사용자가 Firebase 콘솔에서)**

Firestore → `config/limits` 문서에서:
- `dailyFreeSessions` = `3` (지금은 테스트용 `100`)
- `dailyTtsLines` = `300` (새 필드, number)

재배포 없이 즉시 반영된다(limit provider 가 요청마다 읽음).

- [ ] **Step 4: 실서버 확인 — 한 사람의 한도가 다른 사람을 막지 않는지**

`API_KEY` 는 `firebase apps:sdkconfig ANDROID --project oce-v1` 출력의 `current_key`. 익명 게스트 두 명을 만들어 A 로 4번, B 로 1번 시작한다(대본 생성 4번 분 비용 발생):

```bash
API_KEY=<current_key>
tok() { curl -s "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$API_KEY" -H 'Content-Type: application/json' -d '{"returnSecureToken":true}' | node -pe 'JSON.parse(require("fs").readFileSync(0,"utf8")).idToken'; }
start() { curl -s -o /dev/null -w '%{http_code}\n' https://asia-northeast3-oce-v1.cloudfunctions.net/llm -H "Authorization: Bearer $1" -H 'Content-Type: application/json' -d "{\"task\":\"dialogue\",\"idempotencyKey\":\"$(uuidgen | tr 'A-Z' 'a-z')\",\"payload\":{\"level\":\"easy\",\"topic\":\"ordering coffee\",\"length\":6,\"firstSession\":false}}"; }
A=$(tok); B=$(tok)
for i in 1 2 3 4; do start "$A"; done
start "$B"
```

Expected: A 는 `200 200 200 429`, B 는 `200`.

- [ ] **Step 5: 실서버 확인 — tts 문장 길이와 형식 가드**

```bash
curl -s -w ' %{http_code}\n' https://asia-northeast3-oce-v1.cloudfunctions.net/llm -H "Authorization: Bearer $B" -H 'Content-Type: application/json' -d "{\"task\":\"tts\",\"payload\":{\"text\":\"$(printf 'a%.0s' $(seq 1 501))\"}}"
curl -s -w ' %{http_code}\n' https://asia-northeast3-oce-v1.cloudfunctions.net/llm -H "Authorization: Bearer $B" -H 'Content-Type: application/json' -d '{"task":"summary","sessionId":"a/b","payload":{}}'
```

Expected: 둘 다 `{"code":"INVALID_PAYLOAD"} 400`.

- [ ] **Step 6: 앱 스모크 (사용자 실기기, 현재 스토어 빌드 그대로)**

대화 1회 완주 → 상대 음성이 서버 목소리로 재생되고, 요약 화면의 세 카드가 뜨는지 확인한다. 앱 업데이트 없이 동작해야 한다.

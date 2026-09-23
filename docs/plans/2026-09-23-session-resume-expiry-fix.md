# 오래된 세션 이어하기 403 수정 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** "이어하기"로 2시간 넘게 지난 대화를 열어도 feedback·speaking·feedbackDeep·summary 호출이 403 `SESSION_INVALID` 로 막히지 않게 한다 — 서버 배포만으로, 앱 업데이트 없이.

**Architecture:** per-session 게이트(`functions/src/llm/session-cap.ts`)의 "미만료" 판정을 제거한다. 비용 상한은 원래 `callCount < turnCount × factor` 가 보장하므로 만료 판정은 비용 보호에 기여하지 않는다. `expiresAt` 은 정리용 값으로 남기되 30일로 늘리고 reserve 때마다 연장(sliding)한다.

**Tech Stack:** Firebase Cloud Functions v2 (Node 22, TypeScript), firebase-admin Firestore, Jest.

**Spec:** 이 계획의 스펙은 2026-09-23 세션의 grill-yourself 설계(채팅)이며 별도 파일이 없다. 요지는 아래 "배경"에 옮겨 적었다.

## 배경 (스펙 요지)

- 증상: 특정 기기에서 9/21부터 모든 AI 기능 실패. 서버 로그상 앱(okhttp) 요청이 **403**, 응답 ~0.03s(Gemini 호출 전 거절). curl 직접 호출은 5개 task 모두 200 — Gemini 모델 deprecated 아님. 새 대화 시작 시 정상(사용자 확인).
- 원인: `sessions/{id}` 는 생성 후 **2h** 뒤 만료(`start-gate.ts:27`) → `evaluateSlot` 이 만료를 403 으로 거부(`session-cap.ts:79-81`). 반면 앱의 이어하기 스냅샷은 **시간 만료 없음**이 명세(`docs/ui/04-screen-02-home.md:48` H5, `SessionSnapshotStore.kt`) → 2h 지난 세션ID를 계속 보냄. 앱은 403 을 재시도형 Error 로만 처리.
- 확정 결정:
  1. 서버만 고친다(설치된 모든 앱 버전에 즉시 적용).
  2. 만료 판정 삭제. 소유자(uid) 판정·레코드 부재 판정·캡 판정은 유지.
  3. `expiresAt` = 30일, reserve 마다 now+30일로 연장.
  4. 레코드 부재는 여전히 403(소유 검증 불가 → 새로 만들어주면 임의 ID로 무료 캡을 얻는 구멍).
  5. N1 앱 403 처리 = 다음 라운드 [confirmed by default]. N2 이어하기 기간 = 무기한 [confirmed by default].

## Global Constraints

- 리전 `asia-northeast3`, 프로젝트 `oce-v1`, 함수 `llm` 만 재배포.
- 세션 캡 공식 `cap = turnCount × DEFAULT_CAP_FACTOR(3)` 불변.
- 소유자 불일치·레코드 부재 → 403 `SESSION_INVALID` 불변. 캡 도달 → 429 `CAP_EXCEEDED` 불변.
- Firestore TTL 정책은 이번에 켜지 않는다(현재 `firestore.indexes.json` `fieldOverrides: []`).
- 앱(android/) 코드는 건드리지 않는다.
- 작업 브랜치: `fix/session-resume-expiry` (master 에서 분기).

---

## File Structure

| 파일 | 역할 / 변경 |
|---|---|
| `functions/src/llm/session-cap.ts` | 게이트. 만료 판정 삭제, `SESSION_TTL_MS` 소유(export), reserve 에서 `expiresAt` 연장 |
| `functions/src/llm/start-gate.ts` | 세션 생성. 로컬 2h 상수 삭제 → `session-cap` 의 `SESSION_TTL_MS` import |
| `functions/test/session-cap.test.ts` | `evaluateSlot` 새 시그니처 반영, 만료-통과·연장·만료+캡 테스트 |
| `docs/design/backend-functions.md` | §8 계약 문구 갱신 |

`SESSION_TTL_MS` 를 `session-cap.ts` 로 옮기는 이유: 이제 이 값의 의미(정리용·sliding)를 정의하고 매 reserve 마다 쓰는 쪽이 게이트다. `start-gate.ts` → `session-cap.ts` 단방향 import 라 순환 없음(현재 `session-cap.ts` 는 `start-gate` 를 import 하지 않음).

---

### Task 1: 세션 게이트에서 만료 판정 제거 + sliding expiresAt

**Files:**
- Modify: `functions/src/llm/session-cap.ts` (헤더 주석 :19-21, `SessionState` :54-60, `evaluateSlot` :63-86, `toMillis`/`toState` :108-127, `reserve` :140-147, import :22)
- Modify: `functions/src/llm/start-gate.ts:17-27`
- Test: `functions/test/session-cap.test.ts`
- Modify: `docs/design/backend-functions.md:97-98`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `export const SESSION_TTL_MS: number` (= 30 × 24 × 60 × 60 × 1000) — `session-cap.ts`
  - `export function evaluateSlot(state: SessionState | undefined, uid: string, factor: number): number` — **`nowMs` 파라미터 삭제**
  - `export interface SessionState { uid: string; turnCount: number; callCount: number }` — **`expiresAtMs` 삭제**
  - `firestoreSessionGate(factor?, db?, now?)` 시그니처 불변. `reserve` 가 `{ callCount, expiresAt: Timestamp }` 를 update.

- [ ] **Step 0: 브랜치 생성**

```bash
cd /Users/hyunjun_macbook_pro/Documents/Project/project-oce-android
git checkout -b fix/session-resume-expiry
```

- [ ] **Step 1: 실패하는 테스트로 교체**

`functions/test/session-cap.test.ts` 상단(import ~ `describe("evaluateSlot"...` 블록 끝까지)을 아래로 교체:

```ts
import { Timestamp } from "firebase-admin/firestore";
import {
  CapExceededError,
  DbLike,
  DocSnapLike,
  SESSION_TTL_MS,
  SessionInvalidError,
  SessionState,
  TxnLike,
  evaluateSlot,
  firestoreSessionGate,
} from "../src/llm/session-cap";

const NOW = 1_000_000;
const FUTURE = NOW + 60_000;
const PAST = NOW - 1;

function state(over: Partial<SessionState> = {}): SessionState {
  return { uid: "u1", turnCount: 3, callCount: 0, ...over };
}

describe("evaluateSlot (pure cap decision)", () => {
  it("returns callCount+1 when under cap and owned", () => {
    expect(evaluateSlot(state({ callCount: 2 }), "u1", 2)).toBe(3);
  });

  it("allows exactly up to turnCount×factor", () => {
    // turnCount 3 × factor 2 = cap 6; callCount 5 is the last allowed slot.
    expect(evaluateSlot(state({ turnCount: 3, callCount: 5 }), "u1", 2)).toBe(6);
  });

  it("throws CapExceeded at the cap", () => {
    expect(() => evaluateSlot(state({ turnCount: 3, callCount: 6 }), "u1", 2)).toThrow(
      CapExceededError
    );
  });

  it("throws SessionInvalid for a missing record", () => {
    expect(() => evaluateSlot(undefined, "u1", 2)).toThrow(SessionInvalidError);
  });

  it("throws SessionInvalid for a foreign uid", () => {
    expect(() => evaluateSlot(state({ uid: "someone-else" }), "u1", 2)).toThrow(
      SessionInvalidError
    );
  });
});
```

`describe("firestoreSessionGate", ...)` 블록 안, 마지막 `it(...)` 뒤에 추가:

```ts
  it("reserve accepts a session past its expiresAt (resume has no time expiry)", async () => {
    const f = fakeDb({
      uid: "u1",
      expiresAt: Timestamp.fromMillis(PAST),
      turnCount: 3,
      callCount: 1,
    });
    const gate = firestoreSessionGate(2, f.db, now);
    await gate.reserve("u1", "s1");
    expect(f.doc?.callCount).toBe(2);
  });

  it("reserve slides expiresAt forward to now + SESSION_TTL_MS", async () => {
    const f = fakeDb({
      uid: "u1",
      expiresAt: Timestamp.fromMillis(PAST),
      turnCount: 3,
      callCount: 0,
    });
    const gate = firestoreSessionGate(2, f.db, now);
    await gate.reserve("u1", "s1");
    expect((f.doc?.expiresAt as Timestamp).toMillis()).toBe(NOW + SESSION_TTL_MS);
  });

  it("reserve still enforces the cap on a session past its expiresAt", async () => {
    const f = fakeDb({
      uid: "u1",
      expiresAt: Timestamp.fromMillis(PAST),
      turnCount: 2,
      callCount: 4,
    });
    const gate = firestoreSessionGate(2, f.db, now); // cap = 4
    await expect(gate.reserve("u1", "s1")).rejects.toBeInstanceOf(CapExceededError);
    expect(f.doc?.callCount).toBe(4);
  });

  it("SESSION_TTL_MS is 30 days", () => {
    expect(SESSION_TTL_MS).toBe(30 * 24 * 60 * 60 * 1000);
  });
```

(`FUTURE` 는 기존 gate 테스트들이 계속 쓰므로 유지.)

- [ ] **Step 2: 실패 확인**

Run: `cd functions && npx jest test/session-cap.test.ts`
Expected: FAIL — `SESSION_TTL_MS` 미export(TS 컴파일 에러), `evaluateSlot` 인자 수 불일치.

- [ ] **Step 3: `session-cap.ts` 구현**

(a) import (:22) 교체:

```ts
import { getFirestore, Timestamp } from "firebase-admin/firestore";
```

(b) 헤더 주석 :19-21 교체:

```ts
 * Ordering: `sessions/{sessionId}` records are CREATED by the dialogue start-gate (M1-02, §7);
 * this gate only VERIFIES + increments. A missing or foreign record is rejected as
 * SESSION_INVALID. Time is NOT judged: the home resume snapshot has no time expiry
 * (docs/ui/04-screen-02-home.md H5), so a resumed session must keep working days later — cost is
 * bounded by the per-session cap alone. `expiresAt` is only a cleanup horizon (SESSION_TTL_MS).
```

(c) `DEFAULT_CAP_FACTOR` 선언 바로 아래에 추가:

```ts
/**
 * Cleanup horizon for `sessions/{id}.expiresAt` — NOT an access deadline. Set on creation
 * (start-gate) and slid forward on every reserve, so a future Firestore TTL policy only reaps
 * sessions untouched for this long. Cost is bounded by turnCount × factor, not by time.
 */
export const SESSION_TTL_MS = 30 * 24 * 60 * 60 * 1000; // 30d
```

(d) `SessionState` 교체:

```ts
/** the fields of `sessions/{sessionId}` this gate judges (backend-functions.md:98). */
export interface SessionState {
  uid: string;
  turnCount: number;
  callCount: number;
}
```

(e) `evaluateSlot` 교체:

```ts
/**
 * Pure cap decision. Returns the callCount to commit (current + 1), or throws:
 * SessionInvalidError (absent / foreign uid) or CapExceededError (at cap).
 */
export function evaluateSlot(
  state: SessionState | undefined,
  uid: string,
  factor: number
): number {
  if (!state) {
    throw new SessionInvalidError("no session record");
  }
  if (state.uid !== uid) {
    throw new SessionInvalidError("session not owned by caller");
  }
  const cap = state.turnCount * factor;
  if (state.callCount >= cap) {
    throw new CapExceededError(`callCount ${state.callCount} >= cap ${cap}`);
  }
  return state.callCount + 1;
}
```

(f) `toMillis` 함수 전체 삭제, `toState` 교체:

```ts
function toState(snap: DocSnapLike): SessionState | undefined {
  if (!snap.exists) {
    return undefined;
  }
  const d = snap.data() ?? {};
  return {
    uid: typeof d.uid === "string" ? d.uid : "",
    turnCount: typeof d.turnCount === "number" ? d.turnCount : 0,
    callCount: typeof d.callCount === "number" ? d.callCount : 0,
  };
}
```

(g) `reserve` 교체:

```ts
    async reserve(uid, sessionId) {
      const ref = db.collection("sessions").doc(sessionId);
      await db.runTransaction(async (txn) => {
        const snap = await txn.get(ref);
        const next = evaluateSlot(toState(snap), uid, factor);
        txn.update(ref, {
          callCount: next,
          expiresAt: Timestamp.fromMillis(now() + SESSION_TTL_MS),
        });
      });
    },
```

- [ ] **Step 4: `start-gate.ts` 가 공유 상수를 쓰게 변경**

`functions/src/llm/start-gate.ts` 에서 :27 두 줄 삭제:

```ts
/** ephemeral session hard-expiry window — decision #20 (backend-functions.md:98). */
const SESSION_TTL_MS = 2 * 60 * 60 * 1000; // 2h
```

import 블록(:17-20) 끝에 추가:

```ts
import { SESSION_TTL_MS } from "./session-cap";
```

(`:176` 의 `expiresAt: Timestamp.fromMillis(nowMs + SESSION_TTL_MS)` 는 그대로 — 이제 30일.)

- [ ] **Step 5: 테스트·빌드·린트 통과 확인**

Run: `cd functions && npx jest && npm run build && npm run lint`
Expected: 전체 PASS(기존 169개 안팎 + 신규 4개), tsc 에러 0, eslint 에러 0. `start-gate.test.ts:99` 의 `expiresAt toBeDefined` 도 그대로 통과.

- [ ] **Step 6: 설계 문서 갱신**

`docs/design/backend-functions.md` :97-98 두 줄을 교체:

```markdown
- **필드:** `{uid, createdAt, expiresAt, turnCount, callCount}`. `expiresAt` 은 **접근 기한이 아니라 정리 기한**이다 — 생성 시 +30일, **reserve 마다 now+30일로 연장**(sliding). 홈 이어하기 스냅샷이 시간 만료 없음(04-screen-02-home H5)이므로 게이트는 시간을 판정하지 않는다(2026-09-23, 2h 만료가 이어하기를 403 으로 깨던 버그 수정). TTL 정책은 현재 미활성.
- **검증(feedback/speaking/summary 매 호출):** 트랜잭션 `{존재·소유(uid) 확인 → callCount < cap(=turnCount × factor)이면 +1(+expiresAt 연장), 아니면 거부}`. → 무계량 비싼 오디오 경로 차단(FR-27/NFR-2). 비용 상한 = (일일 시작 캡) × (turnCount × factor) — 시간과 무관하게 유지.
```

- [ ] **Step 7: 커밋**

```bash
cd /Users/hyunjun_macbook_pro/Documents/Project/project-oce-android
git add functions/src/llm/session-cap.ts functions/src/llm/start-gate.ts functions/test/session-cap.test.ts docs/design/backend-functions.md docs/plans/2026-09-23-session-resume-expiry-fix.md
git commit -m "$(cat <<'EOF'
fix(functions): stop rejecting resumed sessions past their 2h expiry

The home resume snapshot has no time expiry, but the per-session gate
403'd (SESSION_INVALID) any session older than 2h, so every AI call in a
resumed dialogue failed. Cost is bounded by turnCount x factor, not time:
drop the expiry judgment, make expiresAt a 30d sliding cleanup horizon.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: 배포 + 종단 검증

**Files:** 없음(운영 작업)

**Interfaces:**
- Consumes: Task 1 커밋
- Produces: 배포된 `llm` 함수

- [ ] **Step 1: 배포**

`functions/.env.oce-v1` 이 존재하는지 확인(`ls functions/.env.oce-v1`) — 비대화형 배포가 `LLM_MIN_INSTANCES` 값을 요구할 때 여기서 읽는다. 없으면 `LLM_MIN_INSTANCES=0` 한 줄로 만든다(기본값 0, `functions/src/llm/options.ts:21`).

```bash
cd /Users/hyunjun_macbook_pro/Documents/Project/project-oce-android
firebase deploy --only functions:llm --project oce-v1
```

Expected: `✔ functions[llm(asia-northeast3)] Successful update operation.`

- [ ] **Step 2: curl 스모크(정상 경로 회귀)**

익명 로그인 → dialogue → feedback 이 200 인지 확인. 스크래치 디렉터리에서:

```bash
KEY=$(firebase apps:sdkconfig ANDROID --project oce-v1 2>/dev/null | grep -o '"current_key": *"[^"]*"' | head -1 | sed 's/.*: *"\(.*\)"/\1/')
TOKEN=$(curl -s -X POST "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$KEY" -H 'Content-Type: application/json' -d '{"returnSecureToken":true}' | python3 -c 'import json,sys;print(json.load(sys.stdin)["idToken"])')
U=https://asia-northeast3-oce-v1.cloudfunctions.net/llm
SID=$(curl -s -N -m 90 -X POST $U -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"task":"dialogue","idempotencyKey":"verify-'$(date +%s)'","payload":{"level":"easy","topic":"ordering coffee","length":6,"firstSession":false}}' | grep -o '"sessionId":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "SID=$SID"
curl -s -N -m 90 -w "\nHTTP %{http_code}\n" -X POST $U -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"task":"feedback","sessionId":"'$SID'","payload":{"koreanPrompt":"아이스 아메리카노 한 잔 주세요.","userEnglish":"I want ice americano one","referenceEnglish":"I would like an iced Americano, please.","level":"easy"}}' | tail -3
```

Expected: `SID=` 에 UUID, 마지막 줄 `HTTP 200` 과 `event: done`.

- [ ] **Step 3: 실기기 최종 검증 (사용자 수행)**

문제가 났던 기기에서 **"새로 시작"을 누르지 않은** 옛 미완 대화가 남아 있다면 홈 → 이어하기 → 한 턴 답변 → 피드백이 뜨는지 확인. (그 기기에서 이미 새 대화를 시작했다면 스냅샷이 교체됐으므로, 아무 기기에서 대화를 1턴 진행 → 앱 종료 → **2시간 이상 뒤** 이어하기로 재확인.)

- [ ] **Step 4: 로그 확인**

```bash
firebase functions:log --only llm --project oce-v1 -n 50 --json | python3 -c "
import json,sys
for e in json.load(sys.stdin).get('result',[]):
  h=e.get('httpRequest') or {}
  if h: print(e['timestamp'], h.get('status'), (h.get('userAgent') or '')[:20])"
```

Expected: 배포 이후 okhttp 요청에 403 없음.

**남는 위험:** 그 기기의 옛 세션 문서가 Firestore 에서 삭제됐다면(TTL 정책은 없으므로 가능성 낮음) 계속 403 — 그 경우 다음 라운드(N1: 앱의 403 처리)로 해결.

/**
 * `resetMetrics` callable — 누적 기록 초기화 (M3-09, FR-22). SoT: firestore-schema.md §4.3.
 *
 * Admin SDK 경유 필수: 규칙이 `gamification/progress` write:false, `studytime` update 는 monotonic
 * (`totalSeconds >= resource.data.totalSeconds`) 이라 클라가 0 으로 낮출 수 없다(firestore.rules). Admin 은
 * 규칙을 우회하므로 규칙 변경 불필요.
 *
 * mergeGuestData.ts 관례 미러: onCall 바인딩(리전 = 기존 `llm`·모든 함수와 동일 asia-northeast3)은 얇게 유지하고,
 * 순수 오케스트레이션은 [runReset] 에 위임, Firestore 접근은 [firestoreResetStore] 로 격리 → 에뮬레이터 없이 단위테스트.
 *
 * 비원자성 안전(§4.3 "원자적으로"의 실질 보증): progress 를 `resetAt` 워터마크와 함께 **먼저** 쓴다. 이후
 * in-flight onLedgerCreate 트리거는 `awardedAt <= resetAt → skip`(aggregate.ts) 으로 부활이 차단되므로,
 * progress_marks 삭제·studytime 리셋이 뒤이어 비원자적으로 진행돼도 이중계산/부활이 없다.
 *
 * 보존: `saved_cards`·`point_ledger` 는 건드리지 않는다(ledger 는 내부 멱등 로그, 이미 소비돼 재발화 안 함).
 */
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import type { Firestore } from "firebase-admin/firestore";
import { LLM_REGION } from "../llm/options";

const USERS = "users";
const GAMIFICATION = "gamification";
const PROGRESS = "progress";
const STUDYTIME = "studytime";
const PROGRESS_MARKS = "progress_marks";

/** The `gamification/progress` reset shape (firestore-schema.md:152). `resetAt` is a server sentinel. */
export interface ProgressReset {
  xp: number;
  streak: number;
  studyDays: number;
  lastStudyDate: string | null;
  resetAt: unknown;
}

/** The `gamification/studytime` reset shape (firestore-schema.md:154). */
export interface StudytimeReset {
  totalSeconds: number;
  today: Record<string, never>;
}

/** Firestore ops behind the reset, injectable so [runReset] unit-tests with an in-memory double. */
export interface ResetStore {
  writeProgress(uid: string, data: ProgressReset): Promise<void>;
  deleteProgressMarks(uid: string): Promise<void>;
  writeStudytime(uid: string, data: StudytimeReset): Promise<void>;
  serverTimestamp(): unknown;
}

/**
 * Firestore-backed [ResetStore]. `db` is injectable (tests); default lazy `getFirestore()`
 * (initializeApp() ran in index.ts). progress_marks 는 `recursiveDelete(collectionRef)` 로 일괄 삭제.
 */
export function firestoreResetStore(db: Firestore = getFirestore()): ResetStore {
  const userDoc = (uid: string) => db.collection(USERS).doc(uid);
  return {
    async writeProgress(uid, data) {
      // Full set (not merge): the reset overwrites every progress field including a fresh resetAt watermark.
      await userDoc(uid).collection(GAMIFICATION).doc(PROGRESS).set(data);
    },
    async deleteProgressMarks(uid) {
      await db.recursiveDelete(userDoc(uid).collection(PROGRESS_MARKS));
    },
    async writeStudytime(uid, data) {
      await userDoc(uid).collection(GAMIFICATION).doc(STUDYTIME).set(data);
    },
    serverTimestamp: () => FieldValue.serverTimestamp(),
  };
}

/**
 * 순수 오케스트레이션. 순서가 안전을 보장한다(파일 헤더): (1) resetAt 워터마크와 함께 progress 리셋 →
 * (2) progress_marks 일괄 삭제 → (3) studytime 리셋. 재호출은 멱등(같은 0 상태로 수렴).
 */
export async function runReset(uid: string, store: ResetStore): Promise<void> {
  await store.writeProgress(uid, {
    xp: 0,
    streak: 0,
    studyDays: 0,
    lastStudyDate: null,
    resetAt: store.serverTimestamp(),
  });
  await store.deleteProgressMarks(uid);
  await store.writeStudytime(uid, { totalSeconds: 0, today: {} });
}

export const resetMetrics = onCall({ region: LLM_REGION }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "sign-in required");
  }
  await runReset(uid, firestoreResetStore());
  return { ok: true };
});

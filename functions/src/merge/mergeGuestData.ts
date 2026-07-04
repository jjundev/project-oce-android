/**
 * `mergeGuestData` callable — 게스트 → Google 계정 데이터 이관 (M3-03, FR-3b).
 * SoT: firestore-schema.md §4.4 · backend-functions.md:37. 실제 머지 규칙은 merge.ts(정본) 소유.
 *
 * handler.ts 관례 미러: onCall 바인딩(리전 = 기존 `llm` 과 동일 asia-northeast3)은 얇게 유지하고,
 * 순수 오케스트레이션은 [runMerge] 에 위임한다. Firestore/Auth 접근은 [firestoreMergeStore] 로 격리.
 *
 * 양측 신원 확인(firestore-schema.md:163): 콜러블은 caller(=target)의 `request.auth` 를 자동 검증하고,
 * 게스트 측은 `verifyIdToken(guestIdToken)`(익명도 진짜 Firebase 토큰)으로 수동 검증한다(llm/auth.ts 미러).
 *
 * 멱등 재시도(client 재부팅 복구): 머지 성공 후 게스트가 삭제된 상태에서 같은 토큰으로 재호출되면
 * `verifyIdToken`(checkRevoked=false, 기본)은 만료 전까지 성공하고 [runMerge] 는 빈 서브트리에 대해
 * no-op + `deleteAuthUser` user-not-found 삼킴으로 안전하게 수렴한다.
 */
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import type { Firestore } from "firebase-admin/firestore";
import type { Auth } from "firebase-admin/auth";
import { LLM_REGION } from "../llm/options";
import { DocData, MergeStore, runMerge } from "./merge";

const USERS = "users";
const SAVED_CARDS = "saved_cards";
const POINT_LEDGER = "point_ledger";
const GAMIFICATION = "gamification";
const STUDYTIME = "studytime";

/**
 * Firestore/Auth 백엔드 [MergeStore]. `db`/`auth` 는 주입 가능(테스트용); 기본은 lazy `getFirestore()`
 * /`getAuth()` (initializeApp() 은 index.ts 에서 선실행).
 */
export function firestoreMergeStore(
  db: Firestore = getFirestore(),
  auth: Auth = getAuth()
): MergeStore {
  const userDoc = (uid: string) => db.collection(USERS).doc(uid);
  return {
    async listSavedCards(uid) {
      const snap = await userDoc(uid).collection(SAVED_CARDS).get();
      return snap.docs.map((d) => ({ id: d.id, data: d.data() as DocData }));
    },
    async getSavedCard(uid, cardId) {
      const s = await userDoc(uid).collection(SAVED_CARDS).doc(cardId).get();
      return s.exists ? (s.data() as DocData) : undefined;
    },
    async writeSavedCard(uid, cardId, data) {
      await userDoc(uid).collection(SAVED_CARDS).doc(cardId).set(data, { merge: true });
    },
    async listPointLedger(uid) {
      const snap = await userDoc(uid).collection(POINT_LEDGER).get();
      return snap.docs.map((d) => ({ id: d.id, data: d.data() as DocData }));
    },
    async hasPointLedger(uid, sessionId) {
      const s = await userDoc(uid).collection(POINT_LEDGER).doc(sessionId).get();
      return s.exists;
    },
    async writePointLedger(uid, sessionId, data) {
      // create-only 복사 — awardedAt(원본 Timestamp) 보존 → target onLedgerCreate 재유도.
      await userDoc(uid).collection(POINT_LEDGER).doc(sessionId).set(data);
    },
    async getStudytime(uid) {
      const s = await userDoc(uid).collection(GAMIFICATION).doc(STUDYTIME).get();
      return s.exists ? (s.data() as DocData) : undefined;
    },
    async writeStudytime(uid, totalSeconds) {
      await userDoc(uid)
        .collection(GAMIFICATION)
        .doc(STUDYTIME)
        .set({ totalSeconds, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    },
    async deleteUserSubtree(uid) {
      await db.recursiveDelete(userDoc(uid));
    },
    async deleteAuthUser(uid) {
      try {
        await auth.deleteUser(uid);
      } catch (e) {
        // 이미 삭제됨 → 멱등 no-op. 그 외는 재던짐.
        if ((e as { code?: string }).code !== "auth/user-not-found") {
          throw e;
        }
      }
    },
  };
}

export const mergeGuestData = onCall({ region: LLM_REGION }, async (request) => {
  const targetUid = request.auth?.uid;
  if (!targetUid) {
    throw new HttpsError("unauthenticated", "sign-in required");
  }
  const guestIdToken = (request.data as { guestIdToken?: unknown } | undefined)?.guestIdToken;
  if (typeof guestIdToken !== "string" || guestIdToken.length === 0) {
    throw new HttpsError("invalid-argument", "guestIdToken required");
  }

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

  const merged = await runMerge(guestUid, targetUid, firestoreMergeStore());
  return { ok: true, merged };
});

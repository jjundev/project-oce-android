/**
 * `deleteAccount` callable — 계정 삭제(탈퇴) (M3-09, FR-23). SoT: settings-data-account.md §8.3.
 *
 * Google Play User Data 정책: 계정 생성(Google 로그인) 허용 앱은 인앱 계정 삭제 경로를 제공해야 한다.
 *
 * Admin SDK 경유 필수: 규칙이 `users delete: if false`(firestore.rules) 라 클라 직접삭제 불가. Admin 은 규칙 우회.
 *
 * data-first·Auth-last(§8.3): `recursiveDelete(users/{uid})` 서브트리(`saved_cards`·`point_ledger`·
 * `progress_marks`·`gamification/*`·`usage`) 일괄 삭제 → 성공 후 `auth.deleteUser`. 비원자 배치라 중간 실패 시
 * "일부 데이터 남은 Auth" orphan 이 남을 수 있으나, 클라가 로컬 "삭제 진행 중" 플래그로 멱등 재호출해 수렴한다.
 *
 * top-level ephemeral(`sessions/{id}`·`idempotency/{key}`)은 `users/{uid}` 서브트리가 아니라 루트 직속이라
 * recursiveDelete 가 순회하지 않는다(firestore-schema.md:40-41). 이들은 `expiresAt` TTL 로 자동 만료됨에 의존한다.
 *
 * mergeGuestData.ts 의 deleteUserSubtree/deleteAuthUser seam 을 그대로 미러한다.
 */
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import type { Firestore } from "firebase-admin/firestore";
import type { Auth } from "firebase-admin/auth";
import { LLM_REGION } from "../llm/options";

const USERS = "users";

/** Firestore/Auth ops behind account deletion, injectable so [runDeleteAccount] unit-tests offline. */
export interface AccountStore {
  deleteUserSubtree(uid: string): Promise<void>;
  deleteAuthUser(uid: string): Promise<void>;
}

/**
 * Firestore/Auth-backed [AccountStore]. `db`/`auth` injectable (tests); defaults lazy
 * `getFirestore()`/`getAuth()`. `deleteAuthUser` swallows `auth/user-not-found` → 멱등 재호출 안전.
 */
export function firestoreAccountStore(
  db: Firestore = getFirestore(),
  auth: Auth = getAuth()
): AccountStore {
  return {
    async deleteUserSubtree(uid) {
      await db.recursiveDelete(db.collection(USERS).doc(uid));
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

/** 순수 오케스트레이션: 데이터-우선(서브트리) → Auth-최후. 재호출은 빈 서브트리 no-op + user-not-found 삼킴으로 수렴. */
export async function runDeleteAccount(uid: string, store: AccountStore): Promise<void> {
  await store.deleteUserSubtree(uid);
  await store.deleteAuthUser(uid);
}

export const deleteAccount = onCall({ region: LLM_REGION }, async (request) => {
  // 호출자 자신의 계정만 삭제(uid = context.auth). 노출 차등(Google 만)은 클라가 담당.
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "sign-in required");
  }
  await runDeleteAccount(uid, firestoreAccountStore());
  return { ok: true };
});

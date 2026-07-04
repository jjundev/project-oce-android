/**
 * 게스트 → Google 계정 데이터 이관 — 순수 오케스트레이션 + 결정 헬퍼 (M3-03, FR-3b).
 * SoT: firestore-schema.md §4.4 (sign-in-then-migrate). 서버가 정본 — 이 파일이 실제 머지 규칙을 소유한다.
 *
 * session-cap.ts 관례를 미러한다: 순수 결정(resolveSavedCardWrite / resolveStudytimeTotal)은
 * firebase-admin 타입 없이 단위테스트하고, Firestore/Auth 접근은 [MergeStore] 뒤로 숨겨 fake 로 주입한다.
 *
 * 멱등성(firestore-schema.md:164-168):
 * - saved_cards: 결정적 cardId union(deletedAt 톰스톤 우선) — 재실행이 같은 결과로 수렴.
 * - point_ledger: sessionId create-only 복사(awardedAt 보존) — target `onLedgerCreate` 가 progress 재유도.
 * - studytime: totalSeconds 가산.
 * - progress / progress_marks / usage: 복사하지 않음(이중계산·계정별 쿼터).
 */

export type DocData = Record<string, unknown>;

/** 서브컬렉션 문서(id + data) — 게스트 측 열거 결과. */
export interface GuestDoc {
  id: string;
  data: DocData;
}

/** 이관 결과 카운트(로깅/반환용). */
export interface MergeResult {
  cardsCopied: number;
  cardsTombstoned: number;
  ledgersCopied: number;
  studytimeAdded: boolean;
}

/** Firestore/Auth 접근을 감춘 최소 seam — 순수 [runMerge] 를 fake 로 단위테스트하게 한다. */
export interface MergeStore {
  listSavedCards(uid: string): Promise<GuestDoc[]>;
  getSavedCard(uid: string, cardId: string): Promise<DocData | undefined>;
  writeSavedCard(uid: string, cardId: string, data: DocData): Promise<void>;
  listPointLedger(uid: string): Promise<GuestDoc[]>;
  hasPointLedger(uid: string, sessionId: string): Promise<boolean>;
  writePointLedger(uid: string, sessionId: string, data: DocData): Promise<void>;
  getStudytime(uid: string): Promise<DocData | undefined>;
  writeStudytime(uid: string, totalSeconds: number): Promise<void>;
  /** 게스트 users/{uid} 서브트리 전체 삭제(recursive). */
  deleteUserSubtree(uid: string): Promise<void>;
  /** 게스트 익명 Auth 레코드 삭제. 이미 없으면 no-op(멱등). */
  deleteAuthUser(uid: string): Promise<void>;
}

function numeric(v: unknown): number {
  return typeof v === "number" && Number.isFinite(v) ? v : 0;
}

function isDeleted(card: DocData | undefined): boolean {
  return card != null && card.deletedAt != null;
}

/**
 * saved_cards union 결정(결정적 cardId 기준, deletedAt 톰스톤 우선).
 * - target 부재 → 게스트 카드 그대로 복사(createdAt·deletedAt 보존).
 * - target 존재 & 게스트만 삭제됨 → 삭제 우선: target content·createdAt 유지 + deletedAt 만 게스트 값으로 set.
 * - 그 외(둘 다 활성 / target 이미 삭제) → 쓰기 없음(같은 결정적 문서로 이미 수렴).
 * 반환: target 에 쓸 데이터, 또는 쓰기 불필요면 null.
 */
export function resolveSavedCardWrite(
  guest: DocData,
  target: DocData | undefined
): DocData | null {
  if (!target) {
    return guest;
  }
  if (isDeleted(guest) && !isDeleted(target)) {
    return { ...target, deletedAt: guest.deletedAt };
  }
  return null;
}

/**
 * studytime 가산 병합 — totalSeconds 는 단조 증가(가산). 게스트가 0/부재면 쓰기 없음(null).
 * `today` 는 표시용이라 이관하지 않는다(target 기기 값 보존).
 */
export function resolveStudytimeTotal(
  guest: DocData | undefined,
  target: DocData | undefined
): number | null {
  const g = numeric(guest?.totalSeconds);
  if (g <= 0) {
    return null;
  }
  return numeric(target?.totalSeconds) + g;
}

/**
 * 게스트 서브트리를 target 으로 멱등 이관하고, 마지막에 게스트 Firestore 서브트리 → Auth 레코드 순으로 폐기.
 *
 * 삭제 순서(Firestore 먼저, Auth 마지막)는 비가역 연산을 맨 뒤로 미뤄 중도 크래시 시 guestIdToken 재검증
 * 가능성을 보존한다. 게스트 데이터가 없어도 폐기 단계는 항상 실행 — 이전 크래시로 서브트리만 지워진
 * 재시도가 남은 Auth 레코드까지 마무리한다(guest-absent no-op 도 완결, A9).
 */
export async function runMerge(
  guestUid: string,
  targetUid: string,
  store: MergeStore
): Promise<MergeResult> {
  const result: MergeResult = {
    cardsCopied: 0,
    cardsTombstoned: 0,
    ledgersCopied: 0,
    studytimeAdded: false,
  };

  // saved_cards union (deletedAt 톰스톤 우선)
  for (const card of await store.listSavedCards(guestUid)) {
    const target = await store.getSavedCard(targetUid, card.id);
    const write = resolveSavedCardWrite(card.data, target);
    if (write) {
      await store.writeSavedCard(targetUid, card.id, write);
      if (target) {
        result.cardsTombstoned += 1;
      } else {
        result.cardsCopied += 1;
      }
    }
  }

  // point_ledger union (create-only; awardedAt 보존 → target onLedgerCreate 재유도)
  for (const ledger of await store.listPointLedger(guestUid)) {
    if (!(await store.hasPointLedger(targetUid, ledger.id))) {
      await store.writePointLedger(targetUid, ledger.id, ledger.data);
      result.ledgersCopied += 1;
    }
  }

  // studytime 가산
  const guestStudy = await store.getStudytime(guestUid);
  const targetStudy = await store.getStudytime(targetUid);
  const total = resolveStudytimeTotal(guestStudy, targetStudy);
  if (total != null) {
    await store.writeStudytime(targetUid, total);
    result.studytimeAdded = true;
  }

  // 폐기: Firestore 서브트리 먼저, Auth 레코드 마지막(비가역).
  await store.deleteUserSubtree(guestUid);
  await store.deleteAuthUser(guestUid);

  return result;
}

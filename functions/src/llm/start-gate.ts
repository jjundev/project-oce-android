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

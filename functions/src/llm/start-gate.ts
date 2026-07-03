/**
 * Dialogue start gate — M1-02 (backend-functions.md §7). A single Firestore transaction serializes
 * three concerns per `task=dialogue`: idempotent dedup, daily-free-session limit judgment, and
 * ephemeral `sessions/{id}` creation — all in one commit. On terminal generation failure a separate
 * best-effort refund transaction reverses the usage increment and deletes the idempotency key so a
 * reused key becomes a fresh start.
 *
 * Design mirrors session-cap.ts:
 * - `evaluateStart` is the PURE limit decision (free of firebase-admin types), unit-testable alone.
 * - `firestoreStartGate` wraps it in a transaction (serialized against concurrent retries on the
 *   idempotency + usage docs) and owns UUID minting, session creation, and refund.
 *
 * Cap counting parity with §8: usage is incremented BEFORE the expensive LLM call (so concurrent
 * starts can't bypass the daily cap), and refunded on a terminal server error so only successful
 * starts ultimately count. A non-terminal / client-side failure that never reaches a terminal
 * server error leaves the slot consumed — the accepted slot-loss tolerance (backend-functions.md §7).
 */
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { randomUUID } from "node:crypto";
import { kstDateKey } from "../config/kst";
import { ErrorCode } from "../types/protocol";

/** default daily free-session limit when config/limits is absent (firestore-schema.md:286). */
export const DEFAULT_DAILY_FREE_SESSIONS = 3;

/** ephemeral session hard-expiry window — decision #20 (backend-functions.md:98). */
const SESSION_TTL_MS = 2 * 60 * 60 * 1000; // 2h
/** idempotency dedup window — decision #21; must outlive the transport retry window. */
const IDEMPOTENCY_TTL_MS = 24 * 60 * 60 * 1000; // 24h

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
  /** free sessions left for the KST day. On dedup, reflects the already-counted state. */
  remaining: number;
  /** true when an existing idempotency key was replayed (usage NOT incremented by this call). */
  deduped: boolean;
  /** the `usage/{yyyymmdd}` key this start counted against — passed back to refund() so a
   *  cross-midnight refund targets the exact day that was incremented. */
  usageKey: string;
}

/** Reserve a start slot (single transaction) and, on terminal failure, refund it (best-effort). */
export interface StartGate {
  reserve(
    uid: string,
    idempotencyKey: string,
    turnCount: number
  ): Promise<StartResult>;
  refund(idempotencyKey: string, usageKey: string): Promise<void>;
}

/** resolves the live daily-free-session limit (config/limits, with fallback). */
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
  set(ref: unknown, data: Record<string, unknown>): void;
  update(ref: unknown, data: Record<string, unknown>): void;
  delete(ref: unknown): void;
}
export interface DbLike {
  collection(name: string): { doc(id: string): unknown };
  runTransaction<T>(fn: (txn: TxnLike) => Promise<T>): Promise<T>;
}

/** read a numeric field off a snapshot, defaulting to 0 when absent/non-number. */
function readNumber(snap: DocSnapLike, field: string): number {
  const v = snap.exists ? snap.data()?.[field] : undefined;
  return typeof v === "number" ? v : 0;
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
        const idemRef = db.collection("idempotency").doc(idempotencyKey);
        const usageRef = db.collection("usage").doc(usageKey);
        const idemSnap = await txn.get(idemRef);
        const usageSnap = await txn.get(usageRef);
        const sessionCount = readNumber(usageSnap, "sessionCount");

        // Replay: return the original sessionId, usage untouched. `remaining` reflects the
        // already-counted state (this call did not consume a slot).
        if (idemSnap.exists) {
          const sessionId = String(idemSnap.data()?.sessionId ?? "");
          return {
            sessionId,
            remaining: Math.max(0, limit - sessionCount),
            deduped: true,
            usageKey,
          };
        }

        // Fresh start — throws DailyLimitError (aborts the whole transaction, nothing commits)
        // when at the limit; otherwise commits usage+1, idempotency, and the session record.
        const remaining = evaluateStart(sessionCount, limit);
        const sessionId = uuid();
        const createdAt = Timestamp.fromMillis(nowMs);

        txn.set(usageRef, {
          sessionCount: sessionCount + 1,
          updatedAt: createdAt,
        });
        txn.set(idemRef, {
          sessionId,
          createdAt,
          expiresAt: Timestamp.fromMillis(nowMs + IDEMPOTENCY_TTL_MS),
        });
        txn.set(db.collection("sessions").doc(sessionId), {
          uid,
          createdAt,
          expiresAt: Timestamp.fromMillis(nowMs + SESSION_TTL_MS),
          turnCount,
          callCount: 0,
        });
        return { sessionId, remaining, deduped: false, usageKey };
      });
    },

    async refund(idempotencyKey, usageKey) {
      // Best-effort: atomically decrement usage AND delete the idempotency key so a retry is a
      // fresh start (no slot leak, no double-charge). A failed refund tolerates slot loss (§7).
      try {
        await db.runTransaction(async (txn) => {
          const idemRef = db.collection("idempotency").doc(idempotencyKey);
          const usageRef = db.collection("usage").doc(usageKey);
          const usageSnap = await txn.get(usageRef);
          if (usageSnap.exists) {
            const sessionCount = readNumber(usageSnap, "sessionCount");
            txn.update(usageRef, { sessionCount: Math.max(0, sessionCount - 1) });
          }
          txn.delete(idemRef);
        });
      } catch {
        // swallow — slot-loss tolerance (backend-functions.md §7).
      }
    },
  };
}

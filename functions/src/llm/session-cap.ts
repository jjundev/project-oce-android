/**
 * Per-session call cap gate — M1-06 (backend-functions.md §8). Blocks unmetered, expensive
 * audio calls (NFR-2): every `speaking` call must reserve a slot in the caller's ephemeral
 * `sessions/{sessionId}` record before the Gemini call runs.
 *
 * Design:
 * - `evaluateSlot` is the PURE cap decision (owner + expiry + callCount < turnCount×factor),
 *   free of firebase-admin types so it unit-tests without the emulator.
 * - `firestoreSessionGate` wraps it in a Firestore transaction (serialized against concurrent
 *   calls) and adds best-effort refund.
 *
 * Cap counting (A1, backend-functions.md:100): slots are RESERVED before the call (so
 * concurrent calls can't bypass the cap — NFR-2), and REFUNDED on a terminal server error, so
 * only successful (non-server-error) calls ultimately count. NOTE: a non-terminal / client-side
 * failure (network drop, app kill, client timeout) that never reaches a terminal server error
 * leaves the reservation consumed — the same accepted slot-loss tolerance as the §7 dialogue
 * refund. This is deliberate, not a bug.
 *
 * Ordering: `sessions/{sessionId}` records are CREATED by the dialogue start-gate (M1-02, §7);
 * this gate only VERIFIES + increments. A missing record (e.g. M1-02 not yet shipped, or an
 * expired/foreign session) is rejected as SESSION_INVALID.
 */
import { getFirestore } from "firebase-admin/firestore";
import { ErrorCode } from "../types/protocol";

/**
 * default per-session cap multiplier: cap = turnCount × factor (backend-functions.md:99,150).
 *
 * Bumped 2→3 for M2-03: `feedbackDeep` joins `feedback` + `speaking` as a THIRD per-turn consumer
 * of the shared `sessions/{id}.callCount` budget (user-confirmed shared-counter policy). Deep is
 * on-demand (≤1/turn, cached after first expand — turn-feedback-ia.md P3), so the added pressure is
 * bounded, but the per-turn allowance widens to cover slim + speaking + deep without starving them.
 */
export const DEFAULT_CAP_FACTOR = 3;

/** per-session cap reached — mapped to 429 CAP_EXCEEDED. */
export class CapExceededError extends Error {
  readonly code = ErrorCode.CAP_EXCEEDED;
  constructor(what: string) {
    super(`CAP_EXCEEDED: ${what}`);
    this.name = "CapExceededError";
  }
}

/** session missing / expired / not owned by the caller — mapped to 403 SESSION_INVALID. */
export class SessionInvalidError extends Error {
  readonly code = ErrorCode.SESSION_INVALID;
  constructor(what: string) {
    super(`SESSION_INVALID: ${what}`);
    this.name = "SessionInvalidError";
  }
}

/** the fields of `sessions/{sessionId}` this gate reads (backend-functions.md:98). */
export interface SessionState {
  uid: string;
  /** session hard-expiry as epoch millis (Firestore Timestamp → toMillis()). */
  expiresAtMs: number;
  turnCount: number;
  callCount: number;
}

/**
 * Pure cap decision. Returns the callCount to commit (current + 1), or throws:
 * SessionInvalidError (absent / foreign uid / expired) or CapExceededError (at cap).
 */
export function evaluateSlot(
  state: SessionState | undefined,
  uid: string,
  nowMs: number,
  factor: number
): number {
  if (!state) {
    throw new SessionInvalidError("no session record");
  }
  if (state.uid !== uid) {
    throw new SessionInvalidError("session not owned by caller");
  }
  if (state.expiresAtMs <= nowMs) {
    throw new SessionInvalidError("session expired");
  }
  const cap = state.turnCount * factor;
  if (state.callCount >= cap) {
    throw new CapExceededError(`callCount ${state.callCount} >= cap ${cap}`);
  }
  return state.callCount + 1;
}

/** Reserve a call slot (throws on cap/invalid) and, on terminal failure, refund it. */
export interface SessionGate {
  reserve(uid: string, sessionId: string): Promise<void>;
  refund(sessionId: string): Promise<void>;
}

/** Minimal structural view of the Firestore APIs used here — lets tests inject a fake. */
export interface DocSnapLike {
  exists: boolean;
  data(): Record<string, unknown> | undefined;
}
export interface TxnLike {
  get(ref: unknown): Promise<DocSnapLike>;
  update(ref: unknown, data: Record<string, unknown>): void;
}
export interface DbLike {
  collection(name: string): { doc(id: string): unknown };
  runTransaction<T>(fn: (txn: TxnLike) => Promise<T>): Promise<T>;
}

/** Read `expiresAt` (Firestore Timestamp or millis) off a doc → epoch millis. */
function toMillis(value: unknown): number {
  if (value && typeof (value as { toMillis?: unknown }).toMillis === "function") {
    return (value as { toMillis(): number }).toMillis();
  }
  return typeof value === "number" ? value : 0;
}

function toState(snap: DocSnapLike): SessionState | undefined {
  if (!snap.exists) {
    return undefined;
  }
  const d = snap.data() ?? {};
  return {
    uid: typeof d.uid === "string" ? d.uid : "",
    expiresAtMs: toMillis(d.expiresAt),
    turnCount: typeof d.turnCount === "number" ? d.turnCount : 0,
    callCount: typeof d.callCount === "number" ? d.callCount : 0,
  };
}

/**
 * Firestore-backed gate. `now` is injectable for deterministic tests; `db` defaults to
 * `getFirestore()` (the first Firestore use in functions — initializeApp() runs in index.ts).
 */
export function firestoreSessionGate(
  factor: number = DEFAULT_CAP_FACTOR,
  db: DbLike = getFirestore() as unknown as DbLike,
  now: () => number = () => Date.now()
): SessionGate {
  return {
    async reserve(uid, sessionId) {
      const ref = db.collection("sessions").doc(sessionId);
      await db.runTransaction(async (txn) => {
        const snap = await txn.get(ref);
        const next = evaluateSlot(toState(snap), uid, now(), factor);
        txn.update(ref, { callCount: next });
      });
    },
    async refund(sessionId) {
      const ref = db.collection("sessions").doc(sessionId);
      try {
        await db.runTransaction(async (txn) => {
          const snap = await txn.get(ref);
          const state = toState(snap);
          if (!state) {
            return;
          }
          txn.update(ref, { callCount: Math.max(0, state.callCount - 1) });
        });
      } catch {
        // best-effort: a failed refund tolerates slot loss (backend-functions.md §8).
      }
    },
  };
}

/**
 * `point_ledger/{sessionId}` onCreate aggregation trigger — M3-05 (firestore-schema.md §5).
 *
 * The FIRST Firestore trigger in this codebase (everything else is onRequest). A single
 * transaction, keyed by the session id, folds one immutable ledger entry into the
 * `gamification/progress` aggregate and stamps a `progress_marks/{sessionId}` idempotency marker:
 *
 *   1. marker exists          → return (at-least-once redelivery / guest-merge recopy → no-op)
 *   2. ledger predates reset   → return WITHOUT a marker (bare return, firestore-schema.md:179)
 *   3. otherwise               → merge progress + `create` the marker in the same commit
 *
 * The marker is `create`d (not set): two concurrent deliveries for the same session both read "no
 * marker", both attempt the create, and the loser's transaction re-runs, sees the marker, and
 * no-ops — so studyDays/streak can never double-count (firestore-schema.md:195). Progress is
 * written with merge so the Functions-owned `resetAt` watermark survives untouched.
 *
 * Design mirrors start-gate.ts: `aggregate` (aggregate.ts) is the pure decision; `applyLedger`
 * wraps it in a transaction over an injectable `DbLike`, so the whole thing unit-tests with an
 * in-memory double (no emulator), and `onLedgerCreate` is a thin binding.
 */
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { kstDayKeyIso } from "../config/kst";
import {
  aggregate,
  isDifficulty,
  ProgressState,
} from "./aggregate";

/** Minimal structural view of the Firestore APIs used here — lets tests inject a fake. */
export interface DocSnapLike {
  exists: boolean;
  data(): Record<string, unknown> | undefined;
}
export interface TxnLike {
  get(ref: unknown): Promise<DocSnapLike>;
  set(ref: unknown, data: Record<string, unknown>, options?: { merge?: boolean }): void;
  create(ref: unknown, data: Record<string, unknown>): void;
}
export interface DbLike {
  doc(path: string): unknown;
  runTransaction<T>(fn: (txn: TxnLike) => Promise<T>): Promise<T>;
}

/** server-timestamp sentinel — injectable so tests assert a stable value. */
export type ServerTimestamp = () => unknown;

/** Read a Firestore Timestamp (or raw millis) → epoch millis, or null when absent. */
function toMillisOrNull(value: unknown): number | null {
  if (value && typeof (value as { toMillis?: unknown }).toMillis === "function") {
    return (value as { toMillis(): number }).toMillis();
  }
  return typeof value === "number" ? value : null;
}

function readNumber(data: Record<string, unknown> | undefined, field: string): number {
  const v = data?.[field];
  return typeof v === "number" ? v : 0;
}

/** Normalize a raw `gamification/progress` doc into the pure `ProgressState`. */
function toProgressState(data: Record<string, unknown> | undefined): ProgressState {
  return {
    xp: readNumber(data, "xp"),
    streak: readNumber(data, "streak"),
    studyDays: readNumber(data, "studyDays"),
    lastStudyDate:
      typeof data?.lastStudyDate === "string" ? (data.lastStudyDate as string) : null,
    resetAtMs: toMillisOrNull(data?.resetAt),
  };
}

/**
 * Firestore-backed aggregation. `db` defaults to `getFirestore()`; `serverTs` is injectable for
 * deterministic tests. Returns a `run(uid, sessionId, ledger)` that performs the single-transaction
 * fold described in the file header.
 */
export function ledgerAggregator(
  db: DbLike = getFirestore() as unknown as DbLike,
  serverTs: ServerTimestamp = () => FieldValue.serverTimestamp()
) {
  return {
    async run(
      uid: string,
      sessionId: string,
      ledger: Record<string, unknown> | undefined
    ): Promise<void> {
      const difficulty = ledger?.difficulty;
      const awardedAtMs = toMillisOrNull(ledger?.awardedAt);
      const markRef = db.doc(`users/${uid}/progress_marks/${sessionId}`);
      const progressRef = db.doc(`users/${uid}/gamification/progress`);

      await db.runTransaction(async (txn) => {
        // (1) Already aggregated — redelivery / recopy is a no-op.
        if ((await txn.get(markRef)).exists) {
          return;
        }

        // Defensive: rules already enforce the enum + server `awardedAt`, but a malformed doc
        // must not poison retries. Consume it with a marker and touch nothing.
        if (!isDifficulty(difficulty) || awardedAtMs === null) {
          txn.create(markRef, { processedAt: serverTs(), skipped: "malformed" });
          return;
        }

        const prev = toProgressState((await txn.get(progressRef)).data());
        const { skip, next } = aggregate(prev, {
          difficulty,
          awardedAtMs,
          dayKey: kstDayKeyIso(awardedAtMs),
        });

        // (2) Reset watermark: bare return — no write, no marker (firestore-schema.md:179).
        if (skip) {
          return;
        }

        // (3) Fold + mark, atomically. Merge preserves the Functions-owned `resetAt`.
        txn.set(
          progressRef,
          {
            xp: next.xp,
            streak: next.streak,
            studyDays: next.studyDays,
            lastStudyDate: next.lastStudyDate,
            updatedAt: serverTs(),
          },
          { merge: true }
        );
        txn.create(markRef, { processedAt: serverTs() });
      });
    },
  };
}

/**
 * Trigger binding. `onDocumentCreated` delivers at-least-once; all idempotency lives in `run`.
 * `initializeApp()` ran in index.ts, so `getFirestore()` is ready.
 */
export const onLedgerCreate = onDocumentCreated(
  "users/{uid}/point_ledger/{sessionId}",
  async (event) => {
    const { uid, sessionId } = event.params as { uid: string; sessionId: string };
    await ledgerAggregator().run(uid, sessionId, event.data?.data());
  }
);

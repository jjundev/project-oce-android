import {
  DbLike,
  DocSnapLike,
  TxnLike,
  ledgerAggregator,
} from "../src/gamification/onLedgerCreate";

const TS = "SERVER_TS"; // stable server-timestamp sentinel for assertions

/** A Firestore Timestamp-like wrapper so `toMillis()` works in the double. */
function ts(ms: number) {
  return { toMillis: () => ms };
}

/** millis for KST midnight of yyyy-MM-dd. */
function kstMidnightMs(dayKey: string): number {
  const [y, m, d] = dayKey.split("-").map(Number);
  return Date.UTC(y, m - 1, d) - 9 * 60 * 60 * 1000;
}

/**
 * In-memory Firestore double keyed by full doc path, mirroring start-gate.test.ts's makeDb but with
 * `db.doc(path)`, merge-set, and fail-if-exists `create` (the marker's serialization primitive).
 */
function makeDb(seed: Record<string, Record<string, unknown>> = {}): {
  db: DbLike;
  store: Map<string, Record<string, unknown>>;
} {
  const store = new Map<string, Record<string, unknown>>(Object.entries(seed));
  const txn: TxnLike = {
    async get(ref) {
      const path = (ref as { path: string }).path;
      const value = store.get(path);
      const snap: DocSnapLike = { exists: value !== undefined, data: () => value };
      return snap;
    },
    set(ref, data, options) {
      const path = (ref as { path: string }).path;
      if (options?.merge) {
        store.set(path, { ...(store.get(path) ?? {}), ...data });
      } else {
        store.set(path, { ...data });
      }
    },
    create(ref, data) {
      const path = (ref as { path: string }).path;
      if (store.has(path)) {
        throw new Error(`ALREADY_EXISTS: ${path}`);
      }
      store.set(path, { ...data });
    },
  };
  const db: DbLike = {
    doc(path) {
      return { path };
    },
    async runTransaction(fn) {
      return fn(txn);
    },
  };
  return { db, store };
}

const UID = "u1";
const SID = "sess-1";
const PROGRESS = `users/${UID}/gamification/progress`;
const MARK = `users/${UID}/progress_marks/${SID}`;

function ledger(difficulty: string, dayKey: string) {
  return { difficulty, modeId: "free", awardedAt: ts(kstMidnightMs(dayKey) + 1) };
}

function aggWith(db: DbLike) {
  return ledgerAggregator(db, () => TS);
}

describe("ledgerAggregator.run — fresh aggregation", () => {
  it("folds xp/streak/studyDays into progress and creates the marker", async () => {
    const { db, store } = makeDb();
    await aggWith(db).run(UID, SID, ledger("normal", "2026-07-04"));

    expect(store.get(PROGRESS)).toEqual({
      xp: 20,
      streak: 1,
      studyDays: 1,
      lastStudyDate: "2026-07-04",
      updatedAt: TS,
    });
    expect(store.get(MARK)).toEqual({ processedAt: TS });
  });

  it("merges onto existing progress, preserving the Functions-owned resetAt", async () => {
    const { db, store } = makeDb({
      [PROGRESS]: {
        xp: 100,
        streak: 5,
        studyDays: 9,
        lastStudyDate: "2026-07-04",
        resetAt: ts(kstMidnightMs("2026-01-01")),
      },
    });
    await aggWith(db).run(UID, SID, ledger("easy", "2026-07-05"));

    const p = store.get(PROGRESS)!;
    expect(p.xp).toBe(110);
    expect(p.streak).toBe(6);
    expect(p.studyDays).toBe(10);
    expect(p.lastStudyDate).toBe("2026-07-05");
    expect(p.resetAt).toBeDefined(); // untouched by the merge
  });
});

describe("ledgerAggregator.run — idempotency", () => {
  it("is a no-op when the marker already exists (redelivery / recopy)", async () => {
    const { db, store } = makeDb({
      [MARK]: { processedAt: "earlier" },
      [PROGRESS]: { xp: 20, streak: 1, studyDays: 1, lastStudyDate: "2026-07-04" },
    });
    await aggWith(db).run(UID, SID, ledger("hard", "2026-07-04"));

    // progress unchanged (no double-count), marker unchanged
    expect(store.get(PROGRESS)!.xp).toBe(20);
    expect(store.get(MARK)).toEqual({ processedAt: "earlier" });
  });

  it("re-running the same session twice counts exactly once", async () => {
    const { db, store } = makeDb();
    const agg = aggWith(db);
    await agg.run(UID, SID, ledger("normal", "2026-07-04"));
    await agg.run(UID, SID, ledger("normal", "2026-07-04"));

    expect(store.get(PROGRESS)!.xp).toBe(20); // not 40
    expect(store.get(PROGRESS)!.studyDays).toBe(1);
  });
});

describe("ledgerAggregator.run — reset watermark", () => {
  it("skips a pre-reset ledger with NO write and NO marker", async () => {
    const resetAtMs = kstMidnightMs("2026-07-10");
    const seededProgress = {
      xp: 0,
      streak: 0,
      studyDays: 0,
      lastStudyDate: null,
      resetAt: ts(resetAtMs),
    };
    const { db, store } = makeDb({ [PROGRESS]: seededProgress });
    // awarded before the watermark
    await aggWith(db).run(UID, SID, {
      difficulty: "hard",
      modeId: "free",
      awardedAt: ts(resetAtMs - 1),
    });

    // untouched — the stored doc is the exact seeded object (no set was issued)
    expect(store.get(PROGRESS)).toBe(seededProgress);
    expect(store.has(MARK)).toBe(false); // bare return — no marker
  });
});

describe("ledgerAggregator.run — malformed ledger", () => {
  it("consumes an unknown difficulty with a marker but no progress write", async () => {
    const { db, store } = makeDb();
    await aggWith(db).run(UID, SID, {
      difficulty: "EASY",
      modeId: "free",
      awardedAt: ts(kstMidnightMs("2026-07-04")),
    });
    expect(store.has(PROGRESS)).toBe(false);
    expect(store.get(MARK)).toEqual({ processedAt: TS, skipped: "malformed" });
  });

  it("consumes a missing awardedAt with a marker but no progress write", async () => {
    const { db, store } = makeDb();
    await aggWith(db).run(UID, SID, { difficulty: "easy", modeId: "free" });
    expect(store.has(PROGRESS)).toBe(false);
    expect(store.get(MARK)!.skipped).toBe("malformed");
  });
});

describe("ledgerAggregator.run — at-least-once redelivery dedup", () => {
  // NOTE: this offline double runs each transaction to completion sequentially — it cannot model a
  // TRUE interleaved-transaction race (both txns reading "no marker" before either commits). That
  // serialization is enforced by Firestore's optimistic-retry on the marker `create` conflict and is
  // exercised at the emulator/rules layer, not here. What this asserts is the observable outcome the
  // trigger must guarantee: a redelivery AFTER a prior commit sees the marker and no-ops — no double-count.
  it("a redelivery after the first commit no-ops (marker-exists guard), counting once", async () => {
    const { db, store } = makeDb();
    const agg = aggWith(db);
    await agg.run(UID, SID, ledger("normal", "2026-07-04"));
    await expect(agg.run(UID, SID, ledger("normal", "2026-07-04"))).resolves.toBeUndefined();
    expect(store.get(PROGRESS)!.xp).toBe(20);
  });
});

import {
  DailyLimitError,
  DbLike,
  DocSnapLike,
  StartGate,
  TxnLike,
  evaluateStart,
  firestoreStartGate,
} from "../src/llm/start-gate";
import { kstDateKey } from "../src/config/kst";

const NOW = 1_700_000_000_000; // fixed instant
const USAGE_KEY = kstDateKey(NOW);

/**
 * In-memory Firestore double spanning multiple collections. `collection(name).doc(id)` returns a
 * ref whose `path` keys the store; the transaction reads/writes that map synchronously (Firestore
 * would retry on contention, but the logic under test doesn't depend on that).
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
      const snap: DocSnapLike = {
        exists: value !== undefined,
        data: () => value,
      };
      return snap;
    },
    set(ref, data) {
      store.set((ref as { path: string }).path, { ...data });
    },
    update(ref, data) {
      const path = (ref as { path: string }).path;
      store.set(path, { ...(store.get(path) ?? {}), ...data });
    },
    delete(ref) {
      store.delete((ref as { path: string }).path);
    },
  };
  const db: DbLike = {
    collection(name) {
      return { doc: (id: string) => ({ path: `${name}/${id}` }) };
    },
    async runTransaction(fn) {
      return fn(txn);
    },
  };
  return { db, store };
}

const limit3 = async () => 3;

function gateWith(
  db: DbLike,
  uuid: () => string = () => "sess-1",
  limit: () => Promise<number> = limit3
): StartGate {
  return firestoreStartGate(limit, db, uuid, () => NOW);
}

describe("evaluateStart (pure daily-limit decision)", () => {
  it("returns remaining-after-count when under the limit", () => {
    expect(evaluateStart(0, 3)).toBe(2);
    expect(evaluateStart(2, 3)).toBe(0);
  });

  it("throws DailyLimitError at or over the limit", () => {
    expect(() => evaluateStart(3, 3)).toThrow(DailyLimitError);
    expect(() => evaluateStart(5, 3)).toThrow(DailyLimitError);
  });
});

describe("firestoreStartGate.reserve — fresh start", () => {
  it("increments usage, mints a session, writes idempotency + session records", async () => {
    const { db, store } = makeDb();
    const result = await gateWith(db, () => "sess-uuid").reserve("u1", "key-1", 10);

    expect(result).toEqual({
      sessionId: "sess-uuid",
      remaining: 2,
      deduped: false,
      usageKey: USAGE_KEY,
    });
    // usage +1 for the KST day
    expect(store.get(`usage/${USAGE_KEY}`)?.sessionCount).toBe(1);
    // idempotency maps key → sessionId
    expect(store.get("idempotency/key-1")?.sessionId).toBe("sess-uuid");
    // ephemeral session record with the exact fields session-cap.ts reads
    const session = store.get("sessions/sess-uuid");
    expect(session?.uid).toBe("u1");
    expect(session?.turnCount).toBe(10);
    expect(session?.callCount).toBe(0);
    expect(session?.expiresAt).toBeDefined();
  });

  it("counts against an existing same-day usage doc", async () => {
    const { db, store } = makeDb({ [`usage/${USAGE_KEY}`]: { sessionCount: 1 } });
    const result = await gateWith(db).reserve("u1", "key-2", 5);
    expect(result.remaining).toBe(1); // limit 3, now 2 used → 1 left
    expect(store.get(`usage/${USAGE_KEY}`)?.sessionCount).toBe(2);
  });
});

describe("firestoreStartGate.reserve — idempotent replay", () => {
  it("returns the stored sessionId without incrementing usage", async () => {
    const { db, store } = makeDb({
      "idempotency/key-1": { sessionId: "orig-session" },
      [`usage/${USAGE_KEY}`]: { sessionCount: 2 },
    });
    const result = await gateWith(db, () => "should-not-be-used").reserve(
      "u1",
      "key-1",
      10
    );

    expect(result.sessionId).toBe("orig-session");
    expect(result.deduped).toBe(true);
    expect(result.remaining).toBe(1); // limit 3 − current 2 (unchanged)
    // usage NOT incremented, no new session record
    expect(store.get(`usage/${USAGE_KEY}`)?.sessionCount).toBe(2);
    expect(store.has("sessions/should-not-be-used")).toBe(false);
  });

  it("serializes a same-key retry: second call dedups, usage counted once", async () => {
    const { db, store } = makeDb();
    const gate = gateWith(db, () => "sess-A");
    const first = await gate.reserve("u1", "key-dup", 10);
    const second = await gate.reserve("u1", "key-dup", 10);

    expect(first.deduped).toBe(false);
    expect(second.deduped).toBe(true);
    expect(second.sessionId).toBe("sess-A");
    expect(store.get(`usage/${USAGE_KEY}`)?.sessionCount).toBe(1); // counted once
  });
});

describe("firestoreStartGate.reserve — daily limit", () => {
  it("throws DailyLimitError and commits nothing when at the limit", async () => {
    const { db, store } = makeDb({ [`usage/${USAGE_KEY}`]: { sessionCount: 3 } });
    await expect(gateWith(db).reserve("u1", "key-x", 10)).rejects.toBeInstanceOf(
      DailyLimitError
    );
    // unchanged, no idempotency/session written
    expect(store.get(`usage/${USAGE_KEY}`)?.sessionCount).toBe(3);
    expect(store.has("idempotency/key-x")).toBe(false);
  });
});

describe("firestoreStartGate.refund", () => {
  it("decrements usage AND deletes the idempotency key atomically", async () => {
    const { db, store } = makeDb({
      [`usage/${USAGE_KEY}`]: { sessionCount: 2 },
      "idempotency/key-1": { sessionId: "s" },
    });
    await gateWith(db).refund("key-1", USAGE_KEY);
    expect(store.get(`usage/${USAGE_KEY}`)?.sessionCount).toBe(1);
    expect(store.has("idempotency/key-1")).toBe(false);
  });

  it("reserve then refund nets zero usage (success-only counting on terminal failure)", async () => {
    const { db, store } = makeDb();
    const gate = gateWith(db);
    const start = await gate.reserve("u1", "key-1", 10);
    await gate.refund("key-1", start.usageKey);
    expect(store.get(`usage/${USAGE_KEY}`)?.sessionCount).toBe(0);
    expect(store.has("idempotency/key-1")).toBe(false);
  });

  it("is a no-op (does not throw) when usage doc is missing", async () => {
    const { db } = makeDb({ "idempotency/key-1": { sessionId: "s" } });
    await expect(gateWith(db).refund("key-1", USAGE_KEY)).resolves.toBeUndefined();
  });
});

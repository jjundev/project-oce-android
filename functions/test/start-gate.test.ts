import {
  DailyLimitError,
  DbLike,
  DocSnapLike,
  FREE_REPLAYS,
  StartGate,
  TxnLike,
  evaluateStart,
  firestoreStartGate,
} from "../src/llm/start-gate";
import { SESSION_TTL_MS } from "../src/llm/session-cap";
import { kstDateKey } from "../src/config/kst";

const NOW = 1_700_000_000_000; // fixed instant
const USAGE_KEY = kstDateKey(NOW);
const KEY = "00000000-0000-4000-8000-0000000000a1";
const usagePath = (uid: string) => `users/${uid}/usage/${USAGE_KEY}`;
const idemPath = (uid: string, key = KEY) => `idempotency/${uid}_${key}`;

/**
 * In-memory Firestore double. Refs carry their full slash path so tests assert the EXACT document
 * each write lands on — the old global `usage/{day}` bug passed a fake that never checked paths.
 * `set(..., {merge:true})` merges like Firestore so the shared usage doc keeps other counters.
 */
function makeDb(seed: Record<string, Record<string, unknown>> = {}): {
  db: DbLike;
  store: Map<string, Record<string, unknown>>;
} {
  const store = new Map<string, Record<string, unknown>>(Object.entries(seed));
  const pathOf = (ref: unknown) => (ref as { path: string }).path;
  const txn: TxnLike = {
    async get(ref) {
      const value = store.get(pathOf(ref));
      const snap: DocSnapLike = { exists: value !== undefined, data: () => value };
      return snap;
    },
    set(ref, data, options) {
      const path = pathOf(ref);
      store.set(path, options?.merge ? { ...(store.get(path) ?? {}), ...data } : { ...data });
    },
    update(ref, data) {
      const path = pathOf(ref);
      store.set(path, { ...(store.get(path) ?? {}), ...data });
    },
    delete(ref) {
      store.delete(pathOf(ref));
    },
  };
  const db: DbLike = {
    collection(name) {
      return { doc: (id: string) => ({ path: `${name}/${id}` }) };
    },
    doc(path) {
      return { path };
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
  it("counts on the caller's own usage doc and writes uid-namespaced idempotency + session", async () => {
    const { db, store } = makeDb();
    const result = await gateWith(db, () => "sess-uuid").reserve("u1", KEY, 10);

    expect(result).toEqual({
      sessionId: "sess-uuid",
      remaining: 2,
      deduped: false,
      charged: true,
      usageKey: USAGE_KEY,
    });
    expect(store.get(usagePath("u1"))?.sessionCount).toBe(1);
    expect(store.has(`usage/${USAGE_KEY}`)).toBe(false); // old global doc is never written
    const idem = store.get(idemPath("u1"));
    expect(idem?.uid).toBe("u1");
    expect(idem?.sessionId).toBe("sess-uuid");
    expect(idem?.replayCount).toBe(0);
    const session = store.get("sessions/sess-uuid");
    expect(session?.uid).toBe("u1");
    expect(session?.turnCount).toBe(10);
    expect(session?.callCount).toBe(0);
    expect((session?.expiresAt as { toMillis(): number }).toMillis()).toBe(NOW + SESSION_TTL_MS);
  });

  it("keeps other counters (ttsCount) on the shared usage doc", async () => {
    const { db, store } = makeDb({ [usagePath("u1")]: { sessionCount: 1, ttsCount: 7 } });
    await gateWith(db).reserve("u1", KEY, 6);
    expect(store.get(usagePath("u1"))).toMatchObject({ sessionCount: 2, ttsCount: 7 });
  });

  it("one user at the limit does not block another user", async () => {
    const { db } = makeDb({ [usagePath("u1")]: { sessionCount: 3 } });
    await expect(gateWith(db).reserve("u1", KEY, 6)).rejects.toBeInstanceOf(DailyLimitError);
    const other = await gateWith(db).reserve("u2", KEY, 6);
    expect(other.deduped).toBe(false);
    expect(other.remaining).toBe(2);
  });

  it("the same key from two users never collides", async () => {
    const { db, store } = makeDb();
    let n = 0;
    const gate = gateWith(db, () => `sess-${++n}`);
    const a = await gate.reserve("u1", KEY, 6);
    const b = await gate.reserve("u2", KEY, 6);
    expect(a.sessionId).toBe("sess-1");
    expect(b.sessionId).toBe("sess-2");
    expect(b.deduped).toBe(false);
    expect(store.get(idemPath("u2"))?.sessionId).toBe("sess-2");
  });
});

describe("firestoreStartGate.reserve — replay of the same key", () => {
  it(`is free for ${FREE_REPLAYS} replays, then charges a daily slot per replay`, async () => {
    const { db, store } = makeDb();
    const gate = gateWith(db, () => "sess-A");
    const first = await gate.reserve("u1", KEY, 6);
    expect(first.charged).toBe(true);

    for (let i = 0; i < FREE_REPLAYS; i++) {
      const replay = await gate.reserve("u1", KEY, 6);
      expect(replay).toMatchObject({ sessionId: "sess-A", deduped: true, charged: false });
    }
    expect(store.get(usagePath("u1"))?.sessionCount).toBe(1);

    const paid = await gate.reserve("u1", KEY, 6);
    expect(paid).toMatchObject({ sessionId: "sess-A", deduped: true, charged: true, remaining: 1 });
    expect(store.get(usagePath("u1"))?.sessionCount).toBe(2);
    expect(store.get(idemPath("u1"))?.replayCount).toBe(FREE_REPLAYS + 1);
  });

  it("a replay past the allowance is rejected at the daily limit", async () => {
    const { db, store } = makeDb({
      [usagePath("u1")]: { sessionCount: 3 },
      [idemPath("u1")]: { uid: "u1", sessionId: "s", replayCount: FREE_REPLAYS },
    });
    await expect(gateWith(db).reserve("u1", KEY, 6)).rejects.toBeInstanceOf(DailyLimitError);
    expect(store.get(idemPath("u1"))?.replayCount).toBe(FREE_REPLAYS); // nothing committed
  });

  it("an expired key is treated as a fresh start", async () => {
    const { db, store } = makeDb({
      [idemPath("u1")]: {
        uid: "u1",
        sessionId: "old",
        replayCount: 0,
        expiresAt: { toMillis: () => NOW - 1 },
      },
    });
    const result = await gateWith(db, () => "new").reserve("u1", KEY, 6);
    expect(result).toMatchObject({ sessionId: "new", deduped: false, charged: true });
    expect(store.get(usagePath("u1"))?.sessionCount).toBe(1);
  });
});

describe("firestoreStartGate.refund", () => {
  it("fresh start: decrements usage AND deletes the idempotency key", async () => {
    const { db, store } = makeDb();
    const gate = gateWith(db);
    const start = await gate.reserve("u1", KEY, 6);
    await gate.refund("u1", KEY, start);
    expect(store.get(usagePath("u1"))?.sessionCount).toBe(0);
    expect(store.has(idemPath("u1"))).toBe(false);
  });

  it("fresh start after a free replay was admitted: refunds nothing and keeps the key", async () => {
    const { db, store } = makeDb();
    const gate = gateWith(db);
    const freshStart = await gate.reserve("u1", KEY, 6);
    const replay = await gate.reserve("u1", KEY, 6);
    expect(replay.deduped).toBe(true);
    expect(replay.charged).toBe(false);
    await gate.refund("u1", KEY, freshStart);
    expect(store.get(usagePath("u1"))?.sessionCount).toBe(1);
    expect(store.has(idemPath("u1"))).toBe(true);
  });

  it("paid replay: decrements usage but keeps the original key", async () => {
    const { db, store } = makeDb({
      [usagePath("u1")]: { sessionCount: 1 },
      [idemPath("u1")]: { uid: "u1", sessionId: "s", replayCount: FREE_REPLAYS },
    });
    const gate = gateWith(db);
    const start = await gate.reserve("u1", KEY, 6);
    expect(start.charged).toBe(true);
    await gate.refund("u1", KEY, start);
    expect(store.get(usagePath("u1"))?.sessionCount).toBe(1);
    expect(store.has(idemPath("u1"))).toBe(true);
  });

  it("free replay: is a no-op", async () => {
    const { db, store } = makeDb({
      [usagePath("u1")]: { sessionCount: 1 },
      [idemPath("u1")]: { uid: "u1", sessionId: "s", replayCount: 0 },
    });
    const gate = gateWith(db);
    const start = await gate.reserve("u1", KEY, 6);
    await gate.refund("u1", KEY, start);
    expect(store.get(usagePath("u1"))?.sessionCount).toBe(1);
    expect(store.has(idemPath("u1"))).toBe(true);
  });

  it("does not throw when the usage doc is missing", async () => {
    const { db } = makeDb();
    await expect(
      gateWith(db).refund("u1", KEY, {
        sessionId: "s",
        remaining: 0,
        deduped: false,
        charged: true,
        usageKey: USAGE_KEY,
      })
    ).resolves.toBeUndefined();
  });
});

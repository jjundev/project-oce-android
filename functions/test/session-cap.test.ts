import {
  CapExceededError,
  DbLike,
  DocSnapLike,
  SessionInvalidError,
  SessionState,
  TxnLike,
  evaluateSlot,
  firestoreSessionGate,
} from "../src/llm/session-cap";

const NOW = 1_000_000;
const FUTURE = NOW + 60_000;
const PAST = NOW - 1;

function state(over: Partial<SessionState> = {}): SessionState {
  return { uid: "u1", expiresAtMs: FUTURE, turnCount: 3, callCount: 0, ...over };
}

describe("evaluateSlot (pure cap decision)", () => {
  it("returns callCount+1 when under cap, owned, unexpired", () => {
    expect(evaluateSlot(state({ callCount: 2 }), "u1", NOW, 2)).toBe(3);
  });

  it("allows exactly up to turnCount×factor", () => {
    // turnCount 3 × factor 2 = cap 6; callCount 5 is the last allowed slot.
    expect(evaluateSlot(state({ turnCount: 3, callCount: 5 }), "u1", NOW, 2)).toBe(6);
  });

  it("throws CapExceeded at the cap", () => {
    expect(() =>
      evaluateSlot(state({ turnCount: 3, callCount: 6 }), "u1", NOW, 2)
    ).toThrow(CapExceededError);
  });

  it("throws SessionInvalid for a missing record", () => {
    expect(() => evaluateSlot(undefined, "u1", NOW, 2)).toThrow(SessionInvalidError);
  });

  it("throws SessionInvalid for a foreign uid", () => {
    expect(() => evaluateSlot(state({ uid: "someone-else" }), "u1", NOW, 2)).toThrow(
      SessionInvalidError
    );
  });

  it("throws SessionInvalid for an expired session", () => {
    expect(() => evaluateSlot(state({ expiresAtMs: PAST }), "u1", NOW, 2)).toThrow(
      SessionInvalidError
    );
  });
});

/** In-memory Firestore double: one `sessions` doc, transactional get/update. */
function fakeDb(initial: Record<string, unknown> | undefined): {
  db: DbLike;
  doc: Record<string, unknown> | undefined;
} {
  const store: { value: Record<string, unknown> | undefined } = { value: initial };
  const snap: DocSnapLike = {
    get exists() {
      return store.value !== undefined;
    },
    data() {
      return store.value;
    },
  };
  const txn: TxnLike = {
    async get() {
      return snap;
    },
    update(_ref, data) {
      store.value = { ...(store.value ?? {}), ...data };
    },
  };
  const db: DbLike = {
    collection() {
      return { doc: () => ({}) };
    },
    async runTransaction(fn) {
      return fn(txn);
    },
  };
  return {
    db,
    get doc() {
      return store.value;
    },
  } as { db: DbLike; doc: Record<string, unknown> | undefined };
}

describe("firestoreSessionGate", () => {
  const now = () => NOW;

  it("reserve increments callCount on a valid, under-cap session", async () => {
    const f = fakeDb({ uid: "u1", expiresAt: FUTURE, turnCount: 3, callCount: 1 });
    const gate = firestoreSessionGate(2, f.db, now);
    await gate.reserve("u1", "s1");
    expect(f.doc?.callCount).toBe(2);
  });

  it("reserve rejects (and does not increment) a foreign session", async () => {
    const f = fakeDb({ uid: "other", expiresAt: FUTURE, turnCount: 3, callCount: 1 });
    const gate = firestoreSessionGate(2, f.db, now);
    await expect(gate.reserve("u1", "s1")).rejects.toBeInstanceOf(SessionInvalidError);
    expect(f.doc?.callCount).toBe(1); // unchanged
  });

  it("reserve rejects a call at the cap", async () => {
    const f = fakeDb({ uid: "u1", expiresAt: FUTURE, turnCount: 2, callCount: 4 });
    const gate = firestoreSessionGate(2, f.db, now); // cap = 4
    await expect(gate.reserve("u1", "s1")).rejects.toBeInstanceOf(CapExceededError);
    expect(f.doc?.callCount).toBe(4);
  });

  it("refund decrements callCount (best-effort, floored at 0)", async () => {
    const f = fakeDb({ uid: "u1", expiresAt: FUTURE, turnCount: 3, callCount: 2 });
    const gate = firestoreSessionGate(2, f.db, now);
    await gate.refund("s1");
    expect(f.doc?.callCount).toBe(1);
  });

  it("reserve then refund nets zero — models a success-only count on terminal failure", async () => {
    const f = fakeDb({ uid: "u1", expiresAt: FUTURE, turnCount: 3, callCount: 0 });
    const gate = firestoreSessionGate(2, f.db, now);
    await gate.reserve("u1", "s1"); // +1 → 1
    await gate.refund("s1"); // -1 → 0
    expect(f.doc?.callCount).toBe(0);
  });

  it("refund on a missing record is a no-op that does not throw", async () => {
    const f = fakeDb(undefined);
    const gate = firestoreSessionGate(2, f.db, now);
    await expect(gate.refund("s1")).resolves.toBeUndefined();
  });
});

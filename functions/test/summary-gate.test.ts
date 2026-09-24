import {
  CapExceededError,
  DbLike,
  SUMMARY_CAP,
  SessionInvalidError,
  evaluateSummarySlot,
  firestoreSummaryGate,
} from "../src/llm/session-cap";

describe("evaluateSummarySlot (pure)", () => {
  it("returns the next count under the cap", () => {
    expect(evaluateSummarySlot({ uid: "u1", summaryCount: 0 }, "u1", SUMMARY_CAP)).toBe(1);
  });

  it("rejects a missing or foreign session", () => {
    expect(() => evaluateSummarySlot(undefined, "u1", SUMMARY_CAP)).toThrow(SessionInvalidError);
    expect(() => evaluateSummarySlot({ uid: "u2", summaryCount: 0 }, "u1", SUMMARY_CAP)).toThrow(
      SessionInvalidError
    );
  });

  it("rejects at the cap", () => {
    expect(() =>
      evaluateSummarySlot({ uid: "u1", summaryCount: SUMMARY_CAP }, "u1", SUMMARY_CAP)
    ).toThrow(CapExceededError);
  });
});

function makeDb(seed: Record<string, Record<string, unknown>>): {
  db: DbLike;
  store: Map<string, Record<string, unknown>>;
} {
  const store = new Map<string, Record<string, unknown>>(Object.entries(seed));
  const pathOf = (ref: unknown) => (ref as { path: string }).path;
  const db: DbLike = {
    collection: (name) => ({ doc: (id: string) => ({ path: `${name}/${id}` }) }),
    async runTransaction(fn) {
      return fn({
        async get(ref) {
          const value = store.get(pathOf(ref));
          return { exists: value !== undefined, data: () => value };
        },
        update(ref, data) {
          const path = pathOf(ref);
          store.set(path, { ...(store.get(path) ?? {}), ...data });
        },
      });
    },
  };
  return { db, store };
}

describe("firestoreSummaryGate", () => {
  it("counts summaryCount without touching the shared feedback callCount", async () => {
    // callCount 30 = turnCount 10 × factor 3 → the feedback budget is spent, summary must still work.
    const { db, store } = makeDb({ "sessions/s": { uid: "u1", turnCount: 10, callCount: 30 } });
    await firestoreSummaryGate(SUMMARY_CAP, db, () => 0).reserve("u1", "s");
    expect(store.get("sessions/s")?.summaryCount).toBe(1);
    expect(store.get("sessions/s")?.callCount).toBe(30);
  });

  it(`rejects call ${SUMMARY_CAP + 1}`, async () => {
    const { db } = makeDb({ "sessions/s": { uid: "u1", summaryCount: SUMMARY_CAP } });
    await expect(firestoreSummaryGate(SUMMARY_CAP, db, () => 0).reserve("u1", "s")).rejects.toBeInstanceOf(
      CapExceededError
    );
  });

  it("rejects a foreign session", async () => {
    const { db } = makeDb({ "sessions/s": { uid: "u2", summaryCount: 0 } });
    await expect(firestoreSummaryGate(SUMMARY_CAP, db, () => 0).reserve("u1", "s")).rejects.toBeInstanceOf(
      SessionInvalidError
    );
  });
});

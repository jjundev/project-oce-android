import { kstDateKey } from "../src/config/kst";
import {
  DailyLimitError,
  DbLike,
  TxnLike,
  firestoreLimitProvider,
} from "../src/llm/start-gate";
import { DEFAULT_DAILY_TTS_LINES, firestoreTtsQuota } from "../src/llm/tts-quota";

const NOW = 1_700_000_000_000;
const DAY = kstDateKey(NOW);
const usagePath = (uid: string) => `users/${uid}/usage/${DAY}`;

function makeDb(seed: Record<string, Record<string, unknown>> = {}): {
  db: DbLike;
  store: Map<string, Record<string, unknown>>;
} {
  const store = new Map<string, Record<string, unknown>>(Object.entries(seed));
  const pathOf = (ref: unknown) => (ref as { path: string }).path;
  const txn: TxnLike = {
    async get(ref) {
      const value = store.get(pathOf(ref));
      return { exists: value !== undefined, data: () => value };
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
    collection: (name) => ({ doc: (id: string) => ({ path: `${name}/${id}` }) }),
    doc: (path) => ({ path }),
    runTransaction: async (fn) => fn(txn),
  };
  return { db, store };
}

const limit = (n: number) => async () => n;

describe("firestoreTtsQuota", () => {
  it("counts a line on the caller's usage doc and keeps sessionCount", async () => {
    const { db, store } = makeDb({ [usagePath("u1")]: { sessionCount: 2 } });
    await firestoreTtsQuota(limit(300), db, () => NOW).reserve("u1");
    expect(store.get(usagePath("u1"))).toMatchObject({ sessionCount: 2, ttsCount: 1 });
  });

  it("throws DailyLimitError at the limit and writes nothing", async () => {
    const { db, store } = makeDb({ [usagePath("u1")]: { ttsCount: 300 } });
    await expect(firestoreTtsQuota(limit(300), db, () => NOW).reserve("u1")).rejects.toBeInstanceOf(
      DailyLimitError
    );
    expect(store.get(usagePath("u1"))?.ttsCount).toBe(300);
  });

  it("keeps users independent", async () => {
    const { db, store } = makeDb({ [usagePath("u1")]: { ttsCount: 300 } });
    await firestoreTtsQuota(limit(300), db, () => NOW).reserve("u2");
    expect(store.get(usagePath("u2"))?.ttsCount).toBe(1);
  });

  it("defaults to 300 lines", () => {
    expect(DEFAULT_DAILY_TTS_LINES).toBe(300);
  });
});

describe("firestoreLimitProvider field", () => {
  function configDb(data: Record<string, unknown> | undefined): DbLike {
    return {
      collection: () => ({
        doc: () => ({ get: async () => ({ exists: data !== undefined, data: () => data }) }),
      }),
      doc: () => ({}),
      runTransaction: async () => {
        throw new Error("unused");
      },
    };
  }

  it("reads the named field", async () => {
    await expect(
      firestoreLimitProvider(configDb({ dailyTtsLines: 50, dailyFreeSessions: 3 }), 300, "dailyTtsLines")()
    ).resolves.toBe(50);
  });

  it("falls back when the field is absent", async () => {
    await expect(
      firestoreLimitProvider(configDb({ dailyFreeSessions: 3 }), 300, "dailyTtsLines")()
    ).resolves.toBe(300);
  });

  it("still defaults to dailyFreeSessions", async () => {
    await expect(firestoreLimitProvider(configDb({ dailyFreeSessions: 7 }))()).resolves.toBe(7);
  });
});

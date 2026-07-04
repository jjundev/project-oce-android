import {
  DocData,
  GuestDoc,
  MergeStore,
  resolveSavedCardWrite,
  resolveStudytimeTotal,
  runMerge,
} from "../src/merge/merge";

describe("resolveSavedCardWrite (union, tombstone priority)", () => {
  const active = (over: DocData = {}): DocData => ({
    cardType: "WORD",
    createdAt: 1,
    deletedAt: null,
    ...over,
  });
  const deleted = (over: DocData = {}): DocData => active({ deletedAt: 99, ...over });

  it("copies the guest card verbatim when target is absent", () => {
    const guest = active({ english: "hi", createdAt: 5 });
    expect(resolveSavedCardWrite(guest, undefined)).toEqual(guest);
  });

  it("no-op when both active (already converged on deterministic id)", () => {
    expect(resolveSavedCardWrite(active(), active())).toBeNull();
  });

  it("deletion wins: guest deleted, target active -> tombstone onto target (createdAt preserved)", () => {
    const target = active({ createdAt: 7 });
    const write = resolveSavedCardWrite(deleted({ deletedAt: 42 }), target);
    expect(write).toEqual({ ...target, deletedAt: 42 });
    expect(write?.createdAt).toBe(7); // target sort position kept
  });

  it("no-op when target already deleted", () => {
    expect(resolveSavedCardWrite(deleted(), deleted())).toBeNull();
    expect(resolveSavedCardWrite(active(), deleted())).toBeNull();
  });
});

describe("resolveStudytimeTotal (additive)", () => {
  it("sums totalSeconds", () => {
    expect(resolveStudytimeTotal({ totalSeconds: 30 }, { totalSeconds: 100 })).toBe(130);
  });

  it("treats absent/target as 0", () => {
    expect(resolveStudytimeTotal({ totalSeconds: 30 }, undefined)).toBe(30);
  });

  it("no write when guest has nothing to add", () => {
    expect(resolveStudytimeTotal(undefined, { totalSeconds: 100 })).toBeNull();
    expect(resolveStudytimeTotal({ totalSeconds: 0 }, { totalSeconds: 100 })).toBeNull();
  });
});

/**
 * In-memory MergeStore double: per-uid saved_cards / point_ledger maps + studytime,
 * and records of the subtree/auth deletions. Lets runMerge be exercised without Firestore.
 */
function fakeStore(seed: {
  guestCards?: GuestDoc[];
  targetCards?: Record<string, DocData>;
  guestLedger?: GuestDoc[];
  targetLedgerIds?: string[];
  guestStudy?: DocData;
  targetStudy?: DocData;
}): MergeStore & {
  targetCards: Record<string, DocData>;
  targetLedger: Record<string, DocData>;
  study: { total: number | null };
  subtreeDeleted: string[];
  authDeleted: string[];
} {
  const targetCards: Record<string, DocData> = { ...(seed.targetCards ?? {}) };
  const targetLedger: Record<string, DocData> = {};
  for (const id of seed.targetLedgerIds ?? []) targetLedger[id] = { seeded: true };
  // Mutable holder read directly by the assertions (a bare number would be copied by value on
  // spread and miss writeStudytime's later write).
  const study = { total: null as number | null };
  const subtreeDeleted: string[] = [];
  const authDeleted: string[] = [];
  const store: MergeStore = {
    async listSavedCards() {
      return seed.guestCards ?? [];
    },
    async getSavedCard(_uid, cardId) {
      return targetCards[cardId];
    },
    async writeSavedCard(_uid, cardId, data) {
      targetCards[cardId] = data;
    },
    async listPointLedger() {
      return seed.guestLedger ?? [];
    },
    async hasPointLedger(_uid, sessionId) {
      return sessionId in targetLedger;
    },
    async writePointLedger(_uid, sessionId, data) {
      targetLedger[sessionId] = data;
    },
    async getStudytime(uid) {
      return uid.startsWith("guest") ? seed.guestStudy : seed.targetStudy;
    },
    async writeStudytime(_uid, totalSeconds) {
      study.total = totalSeconds;
    },
    async deleteUserSubtree(uid) {
      subtreeDeleted.push(uid);
    },
    async deleteAuthUser(uid) {
      authDeleted.push(uid);
    },
  };
  return {
    ...store,
    targetCards,
    targetLedger,
    subtreeDeleted,
    authDeleted,
    study,
  };
}

describe("runMerge", () => {
  it("copies new cards, tombstones deletions, skips converged, and preserves ledger awardedAt", async () => {
    const s = fakeStore({
      guestCards: [
        { id: "c1", data: { cardType: "WORD", createdAt: 1, deletedAt: null } }, // new -> copy
        { id: "c2", data: { cardType: "WORD", createdAt: 2, deletedAt: 50 } }, // deletion -> tombstone
        { id: "c3", data: { cardType: "WORD", createdAt: 3, deletedAt: null } }, // converged -> skip
      ],
      targetCards: {
        c2: { cardType: "WORD", createdAt: 9, deletedAt: null },
        c3: { cardType: "WORD", createdAt: 9, deletedAt: null },
      },
      guestLedger: [{ id: "s1", data: { difficulty: "easy", awardedAt: 12345 } }],
      targetLedgerIds: ["s0"],
      guestStudy: { totalSeconds: 40 },
      targetStudy: { totalSeconds: 60 },
    });

    const r = await runMerge("guest-1", "target-1", s);

    expect(r).toEqual({ cardsCopied: 1, cardsTombstoned: 1, ledgersCopied: 1, studytimeAdded: true });
    expect(s.targetCards.c1).toEqual({ cardType: "WORD", createdAt: 1, deletedAt: null });
    expect(s.targetCards.c2).toEqual({ cardType: "WORD", createdAt: 9, deletedAt: 50 }); // target createdAt kept
    expect(s.targetLedger.s1).toEqual({ difficulty: "easy", awardedAt: 12345 }); // awardedAt preserved
    expect(s.study.total).toBe(100);
    // deletion order: subtree first, auth last (irreversible)
    expect(s.subtreeDeleted).toEqual(["guest-1"]);
    expect(s.authDeleted).toEqual(["guest-1"]);
  });

  it("does not re-copy a ledger the target already has (create-only idempotency)", async () => {
    const s = fakeStore({
      guestLedger: [{ id: "s1", data: { difficulty: "hard", awardedAt: 1 } }],
      targetLedgerIds: ["s1"],
    });
    const r = await runMerge("guest-1", "target-1", s);
    expect(r.ledgersCopied).toBe(0);
    expect(s.targetLedger.s1).toEqual({ seeded: true }); // untouched
  });

  it("guest-absent: no writes but still completes subtree + auth deletion (retry safety)", async () => {
    const s = fakeStore({});
    const r = await runMerge("guest-1", "target-1", s);
    expect(r).toEqual({ cardsCopied: 0, cardsTombstoned: 0, ledgersCopied: 0, studytimeAdded: false });
    expect(s.subtreeDeleted).toEqual(["guest-1"]);
    expect(s.authDeleted).toEqual(["guest-1"]);
  });
});

import { AccountStore, runDeleteAccount } from "../src/account/deleteAccount";

/**
 * In-memory AccountStore double recording the deletion order — data-first (subtree) then Auth-last
 * is the irreversible-op ordering the callable must guarantee (settings-data-account.md §8.3).
 */
function fakeStore(opts: { authThrows?: unknown } = {}): {
  store: AccountStore;
  order: string[];
} {
  const order: string[] = [];
  const store: AccountStore = {
    async deleteUserSubtree(uid) {
      order.push(`subtree:${uid}`);
    },
    async deleteAuthUser(uid) {
      if (opts.authThrows) throw opts.authThrows;
      order.push(`auth:${uid}`);
    },
  };
  return { store, order };
}

describe("runDeleteAccount", () => {
  it("deletes the Firestore subtree before the Auth user (data-first, Auth-last)", async () => {
    const { store, order } = fakeStore();
    await runDeleteAccount("u1", store);
    expect(order).toEqual(["subtree:u1", "auth:u1"]);
  });

  it("propagates a non-user-not-found Auth error (caller retries)", async () => {
    const { store } = fakeStore({ authThrows: { code: "auth/internal-error" } });
    await expect(runDeleteAccount("u1", store)).rejects.toEqual({ code: "auth/internal-error" });
  });
});

describe("firestoreAccountStore.deleteAuthUser (idempotency)", () => {
  it("swallows auth/user-not-found so a re-call after a prior deletion converges", async () => {
    // Build the store with a fake Auth that throws user-not-found; a fake Firestore that no-ops.
    const { firestoreAccountStore } = await import("../src/account/deleteAccount");
    const fakeDb = {
      collection: () => ({ doc: () => ({}) }),
      recursiveDelete: async () => undefined,
    } as unknown as import("firebase-admin/firestore").Firestore;
    const fakeAuth = {
      deleteUser: async () => {
        throw { code: "auth/user-not-found" };
      },
    } as unknown as import("firebase-admin/auth").Auth;
    const store = firestoreAccountStore(fakeDb, fakeAuth);
    await expect(runDeleteAccount("u1", store)).resolves.toBeUndefined();
  });
});

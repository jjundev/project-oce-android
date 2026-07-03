import { authenticate, AuthError, extractBearer } from "../src/llm/auth";

// Offline: mock the Admin Auth SDK so no Firebase app / network is needed.
jest.mock("firebase-admin/auth", () => ({
  getAuth: () => ({
    verifyIdToken: async (token: string) => {
      if (token === "valid-anon") {
        return { uid: "u1", firebase: { sign_in_provider: "anonymous" } };
      }
      throw new Error("invalid token");
    },
  }),
}));

describe("extractBearer", () => {
  it("throws AuthError when the header is missing", () => {
    expect(() => extractBearer(undefined)).toThrow(AuthError);
  });

  it("throws AuthError when the header is malformed", () => {
    expect(() => extractBearer("Token abc")).toThrow(AuthError);
  });

  it("returns the token from a well-formed header", () => {
    expect(extractBearer("Bearer xyz.123")).toBe("xyz.123");
  });
});

describe("authenticate", () => {
  it("accepts an anonymous (guest) token", async () => {
    const decoded = await authenticate("Bearer valid-anon");
    expect(decoded.uid).toBe("u1");
  });

  it("rejects an invalid token", async () => {
    await expect(authenticate("Bearer nope")).rejects.toBeInstanceOf(AuthError);
  });

  it("rejects a missing Authorization header", async () => {
    await expect(authenticate(undefined)).rejects.toBeInstanceOf(AuthError);
  });
});

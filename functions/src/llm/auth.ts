/**
 * Firebase ID-token auth for /llm — backend-functions.md:47.
 * Anonymous (guest) tokens ARE allowed; only missing/invalid tokens are rejected.
 */
import { getAuth } from "firebase-admin/auth";
import type { DecodedIdToken } from "firebase-admin/auth";
import { ErrorCode } from "../types/protocol";

export class AuthError extends Error {
  readonly code = ErrorCode.UNAUTHENTICATED;
  constructor(message: string) {
    super(message);
    this.name = "AuthError";
  }
}

/** Pull the bearer token out of an Authorization header, or throw AuthError. */
export function extractBearer(authHeader: string | undefined): string {
  if (!authHeader) {
    throw new AuthError("missing Authorization header");
  }
  const match = /^Bearer (.+)$/.exec(authHeader);
  if (!match) {
    throw new AuthError("malformed Authorization header");
  }
  return match[1];
}

/** Verify the ID token (anonymous allowed). Throws AuthError on missing/invalid. */
export async function authenticate(
  authHeader: string | undefined
): Promise<DecodedIdToken> {
  const token = extractBearer(authHeader);
  try {
    return await getAuth().verifyIdToken(token);
  } catch {
    throw new AuthError("invalid ID token");
  }
}

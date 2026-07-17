/**
 * B-1 prompt / output-schema injection point (backend-functions.md:6, §6).
 *
 * B-1 is an EXTERNAL dependency not yet available. The registry is intentionally
 * empty for the scaffold — the proxy builds and unit-tests without it. task
 * handlers wire real prompts here in M1+ — inline `systemInstruction` only;
 * explicit `cachedContents` is dropped (backend-functions.md §6).
 */
import { Task } from "../types/protocol";

export interface PromptSpec {
  /** static system instruction + reference content (shared by cache & inline paths) */
  system: string;
  /** version marker for the changelog only — no cache key consumes this (backend-functions.md §6) */
  promptVersion: string;
}

export const PROMPTS: Partial<Record<Task, PromptSpec>> = {};

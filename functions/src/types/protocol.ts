/**
 * Shared protocol types for the /llm proxy — used by BOTH transports
 * (SSE for dialogue/feedback/summary, single-shot JSON for speaking/tts).
 *
 * SoT: docs/design/backend-functions.md §4 (proxy contract). The SoT defines the
 * envelope shape `event:error → {code}` but does NOT enumerate concrete code
 * values — the members below marked "(plan-introduced)" are this scaffold's own
 * additions, not ratified in backend-functions.md.
 */

/** task discriminant — backend-functions.md:46 */
export type Task = "dialogue" | "speaking" | "feedback" | "summary" | "tts";

/** per-task response transport — backend-functions.md:50-52 */
export type ResponseMode = "sse" | "json";

/**
 * /llm request body — backend-functions.md:45-48.
 * The scaffold validates only `task`; per-task refinement (dialogue requires
 * `idempotencyKey`; feedback/speaking/summary require `sessionId`;
 * speaking carries `payload.audioBase64`) lands with the gate/parser in M1-02.
 * This type is the seam M1 extends — it is intentionally loose for now.
 */
export interface RequestBody {
  task: Task;
  sessionId?: string;
  idempotencyKey?: string;
  payload?: unknown;
}

/** Error codes surfaced to the client (SSE `event:error` body or JSON error body). */
export enum ErrorCode {
  /** missing/invalid Firebase ID token — backend-functions.md:47 */
  UNAUTHENTICATED = "UNAUTHENTICATED",
  /** malformed request body / unknown task (plan-introduced) */
  UNKNOWN_TASK = "UNKNOWN_TASK",
  /** handler stub — real behavior lands in M1+ (plan-introduced) */
  NOT_IMPLEMENTED = "NOT_IMPLEMENTED",
  /** unexpected server-side failure (plan-introduced) */
  INTERNAL = "INTERNAL",
}

/**
 * Error body — shared by the SSE `error` event and the JSON error path.
 * NOTE: the SSE `error` event is emitted as `{code}` ONLY, to match the SoT
 * envelope (backend-functions.md:57). `message` is an optional server-side
 * affordance (JSON path / logging) and is deliberately NOT sent on the SSE event.
 */
export interface ErrorBody {
  code: ErrorCode;
  message?: string;
}

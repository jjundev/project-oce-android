/**
 * Pure request pipeline for /llm — kept free of firebase-functions bindings so it
 * is directly unit-testable with mock req/res. The onRequest wrapper lives in
 * handler.ts. Flow (backend-functions.md §4):
 *   1. auth (Bearer → verifyIdToken, anonymous allowed) → 401 on failure
 *   2. body shape validation → 400 UNKNOWN_TASK on bad/unknown task
 *   3. dispatch by response mode → stub responds NOT_IMPLEMENTED
 *      (SSE: error+done events / JSON: 501 + typed body)
 *   4. single catch → typed INTERNAL error
 */
import { authenticate } from "./auth";
import { isTask, responseModeFor } from "./dispatch";
import { openSse, writeEvent } from "./sse";
import { ErrorCode, RequestBody } from "../types/protocol";

export interface HandlerRequest {
  headers: Record<string, string | string[] | undefined>;
  body: unknown;
}

export interface HandlerResponse {
  status(code: number): HandlerResponse;
  json(body: unknown): unknown;
  set(field: string, value: string): unknown;
  write(chunk: string): unknown;
  end(): unknown;
  flush?(): void;
  headersSent?: boolean;
}

function header(req: HandlerRequest, name: string): string | undefined {
  const value = req.headers[name];
  return Array.isArray(value) ? value[0] : value;
}

export async function handle(
  req: HandlerRequest,
  res: HandlerResponse
): Promise<void> {
  // 1. auth — any failure is an opaque 401 (before any stream is opened)
  try {
    await authenticate(header(req, "authorization"));
  } catch {
    res.status(401).json({ code: ErrorCode.UNAUTHENTICATED });
    return;
  }

  // 2. body shape — only `task` is validated here; gate/parser land in M1-02
  const body = (req.body ?? {}) as Partial<RequestBody>;
  if (!isTask(body.task)) {
    res.status(400).json({ code: ErrorCode.UNKNOWN_TASK });
    return;
  }
  const task = body.task;

  // 3. dispatch to a stub by response mode
  try {
    if (responseModeFor(task) === "sse") {
      openSse(res);
      // SoT emits the error event as `{code}` only (backend-functions.md:57).
      writeEvent(res, {
        event: "error",
        data: { code: ErrorCode.NOT_IMPLEMENTED },
      });
      writeEvent(res, { event: "done", data: { status: "error" } });
      res.end();
    } else {
      // JSON transport — same `{code}` body shape as the SSE error event
      res.status(501).json({ code: ErrorCode.NOT_IMPLEMENTED });
    }
  } catch {
    // 4. single catch → typed error (only if nothing was committed yet)
    if (!res.headersSent) {
      res.status(500).json({ code: ErrorCode.INTERNAL });
    }
  }
}

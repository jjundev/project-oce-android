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
import {
  InvalidTtsPayloadError,
  parseTtsPayload,
  synthesizeTts,
} from "./tts";
import {
  InvalidSpeakingPayloadError,
  analyzeSpeaking,
  parseSpeakingPayload,
} from "./speaking";
import {
  CapExceededError,
  SessionGate,
  SessionInvalidError,
} from "./session-cap";
import { SpeakingAnalyzeError, TtsSynthError } from "../providers/gemini";
import { LlmProvider } from "../providers/LlmProvider";
import { ErrorCode, RequestBody } from "../types/protocol";

export interface HandlerRequest {
  headers: Record<string, string | string[] | undefined>;
  body: unknown;
}

/**
 * Request-scoped dependencies. The provider is constructed in handler.ts with the
 * Gemini Secret (only available in the onRequest context) and injected here so the
 * pipeline stays pure/testable. When absent (e.g. tests that don't exercise tts),
 * live tasks fall back to the NOT_IMPLEMENTED stub.
 */
export interface HandlerDeps {
  provider?: LlmProvider;
  /** per-session cap gate for `speaking` (backend-functions.md §8). When absent, speaking
   *  falls back to the NOT_IMPLEMENTED stub — same pattern as an absent `provider`. */
  sessionGate?: SessionGate;
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
  res: HandlerResponse,
  deps: HandlerDeps = {}
): Promise<void> {
  // 1. auth — any failure is an opaque 401 (before any stream is opened). Capture the
  // decoded token: `speaking` needs the caller uid for the per-session cap gate (§8).
  let uid: string;
  try {
    const decoded = await authenticate(header(req, "authorization"));
    uid = decoded.uid;
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
    } else if (task === "tts" && deps.provider) {
      // JSON transport, implemented — synthesize and return base64 PCM (M1-05).
      await handleTts(body.payload, deps.provider, res);
    } else if (task === "speaking" && deps.provider && deps.sessionGate) {
      // JSON transport, implemented — transcribe + encourage (M1-06).
      await handleSpeaking(body, uid, deps.provider, deps.sessionGate, res);
    } else {
      // JSON transport stub — tts/speaking without their injected dependencies.
      res.status(501).json({ code: ErrorCode.NOT_IMPLEMENTED });
    }
  } catch {
    // 4. single catch → typed error (only if nothing was committed yet)
    if (!res.headersSent) {
      res.status(500).json({ code: ErrorCode.INTERNAL });
    }
  }
}

/**
 * Synthesize a tts request and write the JSON response. Maps the two typed failures to
 * distinct statuses: a malformed payload → 400 INVALID_PAYLOAD; a synthesis failure
 * (after provider retries) → 502 TTS_SYNTH_FAILED. Any other throw propagates to the
 * outer catch → 500 INTERNAL.
 */
async function handleTts(
  payload: unknown,
  provider: LlmProvider,
  res: HandlerResponse
): Promise<void> {
  let request;
  try {
    request = parseTtsPayload(payload);
  } catch (e) {
    if (e instanceof InvalidTtsPayloadError) {
      res.status(400).json({ code: ErrorCode.INVALID_PAYLOAD });
      return;
    }
    throw e;
  }

  try {
    const response = await synthesizeTts(request, provider);
    res.status(200).json(response);
  } catch (e) {
    if (e instanceof TtsSynthError) {
      res.status(502).json({ code: ErrorCode.TTS_SYNTH_FAILED });
      return;
    }
    throw e;
  }
}

/**
 * Handle a speaking request: validate the envelope + payload, reserve a per-session call slot
 * BEFORE the expensive Gemini call (NFR-2), analyze, and refund the slot on a terminal server
 * error so only successful calls count (A1, backend-functions.md §8). Status mapping:
 *   missing sessionId/audio → 400 INVALID_PAYLOAD
 *   session invalid/expired/foreign → 403 SESSION_INVALID
 *   at cap → 429 CAP_EXCEEDED
 *   analysis failed → 502 SPEAKING_ANALYZE_FAILED (slot refunded first)
 * An empty/unintelligible clip is a 200 with `transcript: ""` (not an error) and DOES count.
 */
async function handleSpeaking(
  body: Partial<RequestBody>,
  uid: string,
  provider: LlmProvider,
  gate: SessionGate,
  res: HandlerResponse
): Promise<void> {
  // sessionId is a top-level envelope field (backend-functions.md:45,47), not in payload.
  const sessionId =
    typeof body.sessionId === "string" ? body.sessionId.trim() : "";
  let request;
  try {
    if (!sessionId) {
      throw new InvalidSpeakingPayloadError("missing sessionId");
    }
    request = parseSpeakingPayload(body.payload);
  } catch (e) {
    if (e instanceof InvalidSpeakingPayloadError) {
      res.status(400).json({ code: ErrorCode.INVALID_PAYLOAD });
      return;
    }
    throw e;
  }

  // Cap gate BEFORE spending on Gemini — blocks unmetered audio (NFR-2).
  try {
    await gate.reserve(uid, sessionId);
  } catch (e) {
    if (e instanceof CapExceededError) {
      res.status(429).json({ code: ErrorCode.CAP_EXCEEDED });
      return;
    }
    if (e instanceof SessionInvalidError) {
      res.status(403).json({ code: ErrorCode.SESSION_INVALID });
      return;
    }
    throw e;
  }

  try {
    const response = await analyzeSpeaking(request, provider);
    res.status(200).json(response);
  } catch (e) {
    // Terminal server error → refund the reserved slot so it doesn't count (A1).
    await gate.refund(sessionId);
    if (e instanceof SpeakingAnalyzeError) {
      res.status(502).json({ code: ErrorCode.SPEAKING_ANALYZE_FAILED });
      return;
    }
    throw e;
  }
}

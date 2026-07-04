/**
 * `task=feedbackDeep` server logic — M2-03 (backend-functions.md §4·§8, feedback-deep.md).
 *
 * On-demand "더 보기" deep analysis: a single JSON object with three fixed-order top-level sections
 * (conceptualBridge → toneStyle → paraphrasing, feedback-deep.md propertyOrdering). A SEPARATE call
 * from slim `feedback` (M1-07) sharing the same per-session cap (§8). Like slim, the backend reads
 * Gemini's streamed text, extracts ONLY completed values, and re-emits each as a typed SSE envelope —
 * the client renders completed sections and never parses raw JSON (FR-6).
 *
 * Two pieces:
 * - `parseFeedbackDeepPayload` validates/normalizes the analysis inputs (feedback-deep.md INPUT —
 *   identical to slim, so it reuses the slim payload shape).
 * - `IncrementalFeedbackDeepParser` REUSES the slim parser's string-escape/depth scan but generalizes
 *   it to the three deep sections: conceptualBridge/toneStyle are OBJECT values, paraphrasing is an
 *   ARRAY value (so its section frame wraps the array under `items`).
 *
 * The outer `event:object type` is fixed to "feedbackDeepSection" (NOT slim's "feedbackSection", so
 * the two contracts stay disjoint) and the inner `data.section` names which section this frame
 * carries (mirrors feedbackSection.data.section, backend-functions.md:55).
 */
import { modelFor } from "../config/models";
import { cacheKey } from "./cacheKey";
import { SseWritable, writeEvent } from "./sse";
import {
  FEEDBACK_DEEP_PROMPT_VERSION,
  FEEDBACK_DEEP_RESPONSE_SCHEMA,
  FEEDBACK_DEEP_SYSTEM_PROMPT,
} from "../providers/gemini";
import { GenerateRequest, LlmProvider } from "../providers/LlmProvider";
import { DeepFeedbackSection } from "../types/sse";
import {
  FeedbackRequestPayload,
  InvalidFeedbackPayloadError,
  parseFeedbackPayload,
} from "./feedback";

/**
 * One deep section's streaming shape: its top-level key, whether its value is an OBJECT (`{…}`) or
 * ARRAY (`[…]`), and — for array values, which cannot be spread into `{ section, … }` — the wrapper
 * key the parsed array is emitted under.
 */
interface DeepSectionSpec {
  section: DeepFeedbackSection;
  open: "{" | "[";
  close: "}" | "]";
  /** for ARRAY sections only: the payload key the parsed array is nested under. */
  arrayKey?: string;
}

/** the three deep sections in their fixed render/emit order (feedback-deep.md). */
const DEEP_SECTIONS: readonly DeepSectionSpec[] = [
  { section: "conceptualBridge", open: "{", close: "}" },
  { section: "toneStyle", open: "{", close: "}" },
  { section: "paraphrasing", open: "[", close: "]", arrayKey: "items" },
];

/**
 * `task=feedbackDeep` request payload — M2-03. Identical inputs to slim `feedback` (feedback-deep.md:6
 * INPUT: same turn as feedback.slim). `sessionId` is a TOP-LEVEL envelope field, validated in
 * handle.ts — not part of this payload.
 */
export type FeedbackDeepRequestPayload = FeedbackRequestPayload;

/** Validate + normalize an untrusted deep payload — reuses the slim validator (same input shape). */
export function parseFeedbackDeepPayload(payload: unknown): FeedbackDeepRequestPayload {
  return parseFeedbackPayload(payload);
}

/** re-exported so callers (handle.ts) can map a bad deep payload to 400 without a slim import. */
export { InvalidFeedbackPayloadError };

/** one completed deep section, flattened as the wire `data` body `{ section, ...payload }`. */
export type DeepFeedbackSectionObject = { section: DeepFeedbackSection } & Record<
  string,
  unknown
>;

/**
 * Incrementally extracts completed deep-section values from streamed JSON text. Object sections
 * (conceptualBridge/toneStyle) emit `{ section, ...object }`; the array section (paraphrasing) emits
 * `{ section, items: [...] }`. Each section emits once, in fixed order, as its value completes.
 */
export class IncrementalFeedbackDeepParser {
  private buffer = "";
  private readonly emitted = new Set<DeepFeedbackSection>();

  addChunk(chunk: string): DeepFeedbackSectionObject[] {
    if (chunk) {
      this.buffer += chunk;
    }
    const out: DeepFeedbackSectionObject[] = [];
    for (const spec of DEEP_SECTIONS) {
      if (this.emitted.has(spec.section)) {
        continue;
      }
      const raw = extractCompletedValue(this.buffer, spec.section, spec.open, spec.close);
      if (raw === undefined) {
        // Sections are fixed-order (propertyOrdering); a later one can't complete before this one.
        break;
      }
      const parsed = parseDeepValue(raw, spec.arrayKey);
      if (parsed) {
        this.emitted.add(spec.section);
        out.push({ section: spec.section, ...parsed });
      } else {
        break;
      }
    }
    return out;
  }
}

/**
 * Parse a completed section substring. For an object value returns the object; for an array value
 * (arrayKey set) returns `{ [arrayKey]: parsedArray }` so it can be spread into the section frame.
 * undefined if the substring isn't the expected JSON shape (defensive — should not happen at a
 * depth-0 boundary).
 */
function parseDeepValue(
  raw: string,
  arrayKey: string | undefined
): Record<string, unknown> | undefined {
  try {
    const value = JSON.parse(raw) as unknown;
    if (arrayKey) {
      return Array.isArray(value) ? { [arrayKey]: value } : undefined;
    }
    if (value && typeof value === "object" && !Array.isArray(value)) {
      return value as Record<string, unknown>;
    }
    return undefined;
  } catch {
    return undefined;
  }
}

/**
 * Read the completed VALUE (`"key": {…}` or `"key": […]`) of the first occurrence of a quoted key.
 * Returns the `{…}`/`[…]` substring once its matching close bracket has streamed (string-escape and
 * depth aware, so a partially-received section never emits early), or undefined if the key/colon/
 * opening bracket is absent or the value hasn't closed yet. Generalizes feedback.ts's object-only
 * extractor to a configurable open/close bracket pair (nested braces inside an array are consumed by
 * the same depth scan before the outer bracket closes).
 */
export function extractCompletedValue(
  source: string,
  key: string,
  open: "{" | "[",
  close: "}" | "]"
): string | undefined {
  const quotedKey = `"${key}"`;
  const keyIndex = source.indexOf(quotedKey);
  if (keyIndex < 0) {
    return undefined;
  }
  const colonIndex = source.indexOf(":", keyIndex + quotedKey.length);
  if (colonIndex < 0) {
    return undefined;
  }
  // skip whitespace to the opening bracket; a non-bracket, non-space char means a different value.
  let valueStart = -1;
  for (let i = colonIndex + 1; i < source.length; i++) {
    const ch = source[i];
    if (ch === " " || ch === "\t" || ch === "\n" || ch === "\r") {
      continue;
    }
    if (ch !== open) {
      return undefined;
    }
    valueStart = i;
    break;
  }
  if (valueStart < 0) {
    return undefined;
  }

  let inString = false;
  let escaping = false;
  let depth = 0;
  for (let i = valueStart; i < source.length; i++) {
    const ch = source[i];
    if (inString) {
      if (escaping) {
        escaping = false;
      } else if (ch === "\\") {
        escaping = true;
      } else if (ch === '"') {
        inString = false;
      }
      continue;
    }
    if (ch === '"') {
      inString = true;
      continue;
    }
    if (ch === open) {
      depth++;
    } else if (ch === close) {
      depth--;
      if (depth === 0) {
        return source.slice(valueStart, i + 1);
      }
    }
  }
  return undefined; // closing bracket not yet arrived
}

/** response surface the orchestrator needs: SSE writes + stream close. */
export interface FeedbackDeepResponse extends SseWritable {
  end(): unknown;
}

/**
 * Consume the deep-feedback stream and re-emit each completed deep section as the typed SSE envelope.
 * The caller (handle.ts) has already reserved the per-session cap slot and opened the stream; this
 * emits `object type=feedbackDeepSection` (each section once, in order) then a terminal `done`. Any
 * provider throw propagates to the caller, which emits `error`+`done` and refunds the slot.
 */
export async function orchestrateFeedbackDeep(
  payload: FeedbackDeepRequestPayload,
  provider: LlmProvider,
  res: FeedbackDeepResponse
): Promise<void> {
  const modelId = modelFor("feedbackDeep");
  const request: GenerateRequest = {
    task: "feedbackDeep",
    modelId,
    payload,
    system: FEEDBACK_DEEP_SYSTEM_PROMPT,
    responseSchema: FEEDBACK_DEEP_RESPONSE_SCHEMA,
    cacheKey: cacheKey("feedbackDeep", FEEDBACK_DEEP_PROMPT_VERSION, modelId),
  };

  const parser = new IncrementalFeedbackDeepParser();
  for await (const chunk of provider.generateStream(request)) {
    for (const section of parser.addChunk(chunk.raw)) {
      writeEvent(res, {
        event: "object",
        data: { type: "feedbackDeepSection", data: section },
      });
    }
  }

  writeEvent(res, { event: "done", data: { status: "ok" } });
  res.end();
}

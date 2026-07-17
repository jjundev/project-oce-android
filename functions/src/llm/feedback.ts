/**
 * `task=feedback` server logic — M1-07 (backend-functions.md §4·§8, feedback-slim.md).
 *
 * Slim per-turn feedback: a single JSON object with three fixed-order top-level sections
 * (writingScore → grammar → naturalExpression, feedback-slim.md propertyOrdering). Like dialogue,
 * the backend reads Gemini's streamed text, extracts ONLY completed objects, and re-emits each as
 * a typed SSE envelope — the client renders completed sections and never parses raw JSON (FR-6).
 *
 * Two pieces:
 * - `parseFeedbackPayload` validates/normalizes the analysis inputs (feedback-slim.md INPUT).
 * - `IncrementalFeedbackParser` REUSES the dialogue parser's brace-depth/string-escape machinery
 *   (dialogue.ts) but keys on the three known top-level property names rather than array elements:
 *   each section object emits ONCE, as its `}` boundary at top-level depth completes.
 *
 * Each emitted section is a TYPED object `{ section, ...payload }` — the outer `event:object type`
 * is fixed to "feedbackSection" and the inner `data.section` names which section this frame carries
 * (mirrors summaryCard.data.kind, backend-functions.md:55).
 */
import { modelFor } from "../config/models";
import { LEVEL_TOKENS } from "../config/levels";
import { SseWritable, writeEvent } from "./sse";
import {
  FEEDBACK_RESPONSE_SCHEMA,
  FEEDBACK_SYSTEM_PROMPT,
} from "../providers/gemini";
import { GenerateRequest, LlmProvider } from "../providers/LlmProvider";
import { FeedbackSection } from "../types/sse";

/** thrown when the feedback payload is malformed — mapped to 400 INVALID_PAYLOAD. */
export class InvalidFeedbackPayloadError extends Error {}

const VALID_LEVELS = new Set<string>(LEVEL_TOKENS);

/** the three slim sections in their fixed render/emit order (feedback-slim.md:2). */
const FEEDBACK_SECTIONS: readonly FeedbackSection[] = [
  "writingScore",
  "grammar",
  "naturalExpression",
];

/**
 * `task=feedback` request payload — M1-07. The learner's turn inputs (feedback-slim.md:5);
 * `sessionId` is a TOP-LEVEL envelope field (RequestBody.sessionId), validated in handle.ts —
 * it is NOT part of this payload (backend-functions.md:45,47).
 */
export interface FeedbackRequestPayload {
  koreanPrompt: string;
  userEnglish: string;
  referenceEnglish: string;
  level: string;
}

/**
 * Validate + normalize an untrusted feedback payload. `koreanPrompt` and `userEnglish` are the
 * essential inputs and must be non-empty; `referenceEnglish` may be empty (a turn without a scripted
 * target), and `level` defaults to "normal" if out of range (a hint, not a hard gate).
 */
export function parseFeedbackPayload(payload: unknown): FeedbackRequestPayload {
  const p = (payload ?? {}) as Record<string, unknown>;
  const koreanPrompt = typeof p.koreanPrompt === "string" ? p.koreanPrompt.trim() : "";
  const userEnglish = typeof p.userEnglish === "string" ? p.userEnglish.trim() : "";
  if (!koreanPrompt) {
    throw new InvalidFeedbackPayloadError("missing koreanPrompt");
  }
  if (!userEnglish) {
    throw new InvalidFeedbackPayloadError("missing userEnglish");
  }
  const referenceEnglish =
    typeof p.referenceEnglish === "string" ? p.referenceEnglish.trim() : "";
  const level = typeof p.level === "string" && VALID_LEVELS.has(p.level) ? p.level : "normal";
  return { koreanPrompt, userEnglish, referenceEnglish, level };
}

/** one completed slim section, flattened as the wire `data` body `{ section, ...payload }`. */
export type FeedbackSectionObject = { section: FeedbackSection } & Record<string, unknown>;

/**
 * Incrementally extracts completed slim-section objects from streamed JSON text. Reuses the
 * dialogue parser's string-escape/brace-depth scan (a completed `{…}` back to top-level depth),
 * but keyed on the three known top-level property names instead of `script` array elements. Each
 * section emits once, in fixed order, as its object value completes.
 */
export class IncrementalFeedbackParser {
  private buffer = "";
  private readonly emitted = new Set<FeedbackSection>();

  addChunk(chunk: string): FeedbackSectionObject[] {
    if (chunk) {
      this.buffer += chunk;
    }
    const out: FeedbackSectionObject[] = [];
    for (const section of FEEDBACK_SECTIONS) {
      if (this.emitted.has(section)) {
        continue;
      }
      const raw = extractCompletedObjectValue(this.buffer, section);
      if (raw === undefined) {
        // Sections are fixed-order (propertyOrdering); a later one can't complete before this one.
        break;
      }
      const parsed = parseSection(raw);
      if (parsed) {
        this.emitted.add(section);
        out.push({ section, ...parsed });
      } else {
        break;
      }
    }
    return out;
  }
}

/** Parse a completed section substring into an object; undefined if it isn't a usable object. */
function parseSection(raw: string): Record<string, unknown> | undefined {
  try {
    const obj = JSON.parse(raw) as unknown;
    if (obj && typeof obj === "object" && !Array.isArray(obj)) {
      return obj as Record<string, unknown>;
    }
    return undefined;
  } catch {
    // Should not happen at a brace-depth-0 boundary; defensive against a malformed object.
    return undefined;
  }
}

/**
 * Read the completed OBJECT value (`"key": { … }`) of the FIRST occurrence of a quoted key. Returns
 * the `{…}` substring once its matching close brace has streamed (string-escape/brace-depth aware),
 * or undefined if the key/colon/opening-brace is absent or the object hasn't closed yet (so a
 * partially-received section never emits early). Mirrors dialogue.ts's brace-depth extractor.
 */
export function extractCompletedObjectValue(
  source: string,
  key: string
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
  // skip whitespace to the opening brace; a non-brace, non-space char means not an object value.
  let objectStart = -1;
  for (let i = colonIndex + 1; i < source.length; i++) {
    const ch = source[i];
    if (ch === " " || ch === "\t" || ch === "\n" || ch === "\r") {
      continue;
    }
    if (ch !== "{") {
      return undefined;
    }
    objectStart = i;
    break;
  }
  if (objectStart < 0) {
    return undefined;
  }

  let inString = false;
  let escaping = false;
  let braceDepth = 0;
  for (let i = objectStart; i < source.length; i++) {
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
    if (ch === "{") {
      braceDepth++;
    } else if (ch === "}") {
      braceDepth--;
      if (braceDepth === 0) {
        return source.slice(objectStart, i + 1);
      }
    }
  }
  return undefined; // closing brace not yet arrived
}

/** response surface the orchestrator needs: SSE writes + stream close. */
export interface FeedbackResponse extends SseWritable {
  end(): unknown;
}

/**
 * Consume the feedback stream and re-emit each completed slim section as the typed SSE envelope.
 * The caller (handle.ts) has already reserved the per-session cap slot and opened the stream; this
 * emits `object type=feedbackSection` (each section once, in order) then a terminal `done`. Any
 * provider throw propagates to the caller, which emits `error`+`done` and refunds the slot.
 */
export async function orchestrateFeedback(
  payload: FeedbackRequestPayload,
  provider: LlmProvider,
  res: FeedbackResponse
): Promise<void> {
  const modelId = modelFor("feedback");
  const request: GenerateRequest = {
    task: "feedback",
    modelId,
    payload,
    system: FEEDBACK_SYSTEM_PROMPT,
    responseSchema: FEEDBACK_RESPONSE_SCHEMA,
  };

  const parser = new IncrementalFeedbackParser();
  for await (const chunk of provider.generateStream(request)) {
    for (const section of parser.addChunk(chunk.raw)) {
      writeEvent(res, {
        event: "object",
        data: { type: "feedbackSection", data: section },
      });
    }
  }

  writeEvent(res, { event: "done", data: { status: "ok" } });
  res.end();
}

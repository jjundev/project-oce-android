/**
 * `task=dialogue` server logic — M1-02 (backend-functions.md §7·§9, prompt-system.md §3).
 *
 * Two pieces:
 * - `parseDialoguePayload` validates/normalizes the generation inputs (firstSession → easy/5).
 * - `IncrementalDialogueParser` ports the archived brace-depth turn extractor and adds NEW
 *   metadata extraction: the archive parser (IncrementalDialogueScriptParser.java) scanned only
 *   topic/opponentName/opponentGender and emitted on `topic` alone with defaults — that behavior
 *   is deliberately NOT reused here. The client requires all four fields (topic, opponentName,
 *   opponentGender, opponentRole) non-null (DialogueContracts.kt:75-80), so metadata is emitted
 *   ONCE, only when all four are present (no partial meta, no defaults). `opponentRole` has no
 *   counterpart in the archive parser and is new extraction logic.
 *
 * Every emitted turn/meta is a TYPED object, never a raw JSON substring: the client renders
 * completed objects and never parses raw JSON (FR-6, DialogueContracts.kt:37-40). So extracted
 * turn substrings are `JSON.parse`d into `{ko,en,role}` before emission — the brace-depth pass
 * only guarantees a complete object boundary; the parse turns it into the wire shape.
 */
import { modelFor } from "../config/models";
import { cacheKey } from "./cacheKey";
import { SseWritable, writeEvent } from "./sse";
import {
  DIALOGUE_PROMPT_VERSION,
  DIALOGUE_RESPONSE_SCHEMA,
  DIALOGUE_SYSTEM_PROMPT,
} from "../providers/gemini";
import { GenerateRequest, LlmProvider } from "../providers/LlmProvider";
import { DialoguePayload } from "../types/protocol";

/** thrown when the dialogue payload is malformed — mapped to 400 INVALID_PAYLOAD. */
export class InvalidDialoguePayloadError extends Error {}

const VALID_LEVELS = new Set<string>(["easy", "normal", "hard"]);
const VALID_LENGTHS = new Set<number>([5, 10]);

/**
 * Validate + normalize an untrusted dialogue payload. `firstSession:true` COERCES level→easy and
 * length→5 regardless of the supplied values (dialogue-generate.md:39 "regardless of input") — it
 * is not a rejection. Only a non-firstSession request with an out-of-range level/length, or a
 * missing topic, is rejected.
 */
export function parseDialoguePayload(payload: unknown): DialoguePayload {
  const p = (payload ?? {}) as Record<string, unknown>;
  const topic = typeof p.topic === "string" ? p.topic.trim() : "";
  if (!topic) {
    throw new InvalidDialoguePayloadError("missing topic");
  }
  const firstSession = p.firstSession === true;

  if (firstSession) {
    // guaranteed-success first session — force easy/5, ignore whatever the client sent.
    return { level: "easy", topic, length: 5, firstSession: true };
  }

  const level = typeof p.level === "string" ? p.level : "";
  const length = typeof p.length === "number" ? p.length : NaN;
  if (!VALID_LEVELS.has(level)) {
    throw new InvalidDialoguePayloadError(`invalid level: ${String(p.level)}`);
  }
  if (!VALID_LENGTHS.has(length)) {
    throw new InvalidDialoguePayloadError(`invalid length: ${String(p.length)}`);
  }
  return {
    level: level as DialoguePayload["level"],
    topic,
    length,
    firstSession: false,
  };
}

/** completed opponent/topic metadata — mirrors the client DialogueMeta DTO. */
export interface DialogueMetaObject {
  topic: string;
  opponentName: string;
  opponentGender: string;
  opponentRole: string;
}

/** one completed dialogue turn — mirrors the client DialogueTurn DTO. */
export interface DialogueTurnObject {
  ko: string;
  en: string;
  role: string;
}

/** result of feeding one streamed chunk to the parser. */
export interface ParseUpdate {
  /** present only on the single chunk that first completes all four metadata fields. */
  meta?: DialogueMetaObject;
  /** turns whose object boundary completed in this chunk, in order, parsed to typed objects. */
  turns: DialogueTurnObject[];
}

/**
 * Incrementally extracts completed metadata + turn objects from streamed JSON text. Ported from
 * archive IncrementalDialogueScriptParser: turn extraction is brace-depth/string-escape aware
 * (extractCompletedTurnStrings); metadata is key-scan based (readJsonStringValue). Metadata emits
 * once, only when all four fields have arrived; turns emit as their `}` boundary completes.
 */
export class IncrementalDialogueParser {
  private buffer = "";
  private emittedTurnCount = 0;
  private metaEmitted = false;

  addChunk(chunk: string): ParseUpdate {
    if (chunk) {
      this.buffer += chunk;
    }

    let meta: DialogueMetaObject | undefined;
    if (!this.metaEmitted) {
      meta = tryExtractMeta(this.buffer);
      if (meta) {
        this.metaEmitted = true;
      }
    }

    const all = extractCompletedTurnStrings(this.buffer);
    const turns: DialogueTurnObject[] = [];
    for (let i = this.emittedTurnCount; i < all.length; i++) {
      const parsed = parseTurn(all[i]);
      if (parsed) {
        turns.push(parsed);
      }
    }
    this.emittedTurnCount = all.length;
    return { meta, turns };
  }
}

/** All four metadata fields, or undefined until every one has fully arrived (no defaults). */
function tryExtractMeta(source: string): DialogueMetaObject | undefined {
  const topic = readJsonStringValue(source, "topic");
  const opponentName = readJsonStringValue(source, "opponentName");
  const opponentGender = readJsonStringValue(source, "opponentGender");
  const opponentRole = readJsonStringValue(source, "opponentRole");
  if (!topic || !opponentName || !opponentGender || !opponentRole) {
    return undefined;
  }
  return {
    topic: topic.trim(),
    opponentName: opponentName.trim(),
    opponentGender: opponentGender.trim(),
    opponentRole: opponentRole.trim(),
  };
}

/** Parse a completed turn substring into `{ko,en,role}`; undefined if it isn't a usable turn. */
function parseTurn(raw: string): DialogueTurnObject | undefined {
  try {
    const obj = JSON.parse(raw) as Record<string, unknown>;
    const ko = typeof obj.ko === "string" ? obj.ko : "";
    const en = typeof obj.en === "string" ? obj.en : "";
    if (!ko && !en) {
      return undefined; // not a dialogue turn (e.g. a nested object that isn't a line)
    }
    const role = obj.role === "user" ? "user" : "model";
    return { ko, en, role };
  } catch {
    // Should not happen at a brace-depth-0 boundary; defensive against a malformed object.
    return undefined;
  }
}

const SCRIPT_KEY = '"script"';

/**
 * Read the string value of the FIRST occurrence of a quoted key (`"key": "value"`). Handles
 * backslash escapes inside the value; returns undefined if the key/colon/opening-quote is absent
 * or the closing quote hasn't streamed yet (so a partially-received value never emits early).
 */
function readJsonStringValue(source: string, key: string): string | undefined {
  const quotedKey = `"${key}"`;
  const keyIndex = source.indexOf(quotedKey);
  if (keyIndex < 0) {
    return undefined;
  }
  const colonIndex = source.indexOf(":", keyIndex + quotedKey.length);
  if (colonIndex < 0) {
    return undefined;
  }
  // skip whitespace to the opening quote; a non-quote, non-space char means not a string value.
  let valueStart = -1;
  for (let i = colonIndex + 1; i < source.length; i++) {
    const ch = source[i];
    if (ch === " " || ch === "\t" || ch === "\n" || ch === "\r") {
      continue;
    }
    if (ch !== '"') {
      return undefined;
    }
    valueStart = i + 1;
    break;
  }
  if (valueStart < 0) {
    return undefined;
  }
  let out = "";
  let escaping = false;
  for (let i = valueStart; i < source.length; i++) {
    const ch = source[i];
    if (escaping) {
      out += ch;
      escaping = false;
      continue;
    }
    if (ch === "\\") {
      escaping = true;
      continue;
    }
    if (ch === '"') {
      return out;
    }
    out += ch;
  }
  return undefined; // closing quote not yet arrived
}

/**
 * Extract every completed (`{ … }`, brace-depth back to 0) object inside the `script` array.
 * String-escape aware so braces/quotes inside string values don't miscount depth. Ported from
 * IncrementalDialogueScriptParser.extractCompletedTurnObjects.
 */
function extractCompletedTurnStrings(source: string): string[] {
  const result: string[] = [];
  const arrayStart = resolveScriptArrayStart(source);
  if (arrayStart < 0) {
    return result;
  }

  let inString = false;
  let escaping = false;
  let objectStart = -1;
  let braceDepth = 0;

  for (let i = arrayStart + 1; i < source.length; i++) {
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

    if (objectStart < 0) {
      if (ch === "{") {
        objectStart = i;
        braceDepth = 1;
      } else if (ch === "]") {
        break; // end of the script array
      }
      continue;
    }

    if (ch === "{") {
      braceDepth++;
    } else if (ch === "}") {
      braceDepth--;
      if (braceDepth === 0) {
        result.push(source.slice(objectStart, i + 1));
        objectStart = -1;
      }
    }
  }

  return result;
}

/** Find the `[` that opens the `script` array, tolerating whitespace after the key/colon. */
function resolveScriptArrayStart(source: string): number {
  let searchFrom = 0;
  for (;;) {
    const keyIndex = source.indexOf(SCRIPT_KEY, searchFrom);
    if (keyIndex < 0) {
      return -1;
    }
    const arrayStart = findArrayStart(source, keyIndex + SCRIPT_KEY.length);
    if (arrayStart >= 0) {
      return arrayStart;
    }
    searchFrom = keyIndex + SCRIPT_KEY.length;
  }
}

function findArrayStart(source: string, fromIndex: number): number {
  for (let i = fromIndex; i < source.length; i++) {
    const ch = source[i];
    if (ch === "[") {
      return i;
    }
    if (ch !== " " && ch !== "\t" && ch !== "\n" && ch !== "\r" && ch !== ":") {
      return -1;
    }
  }
  return -1;
}

/** response surface the orchestrator needs: SSE writes + stream close. */
export interface DialogueResponse extends SseWritable {
  end(): unknown;
}

/**
 * Consume the dialogue stream and re-emit completed objects as the typed SSE envelope. The caller
 * has already emitted `event:meta`; this emits `object type=dialogueMeta` (once) and `object
 * type=turn` (each arrival), then a terminal `done`. Any provider throw propagates to the caller
 * (handle.ts), which emits `error`+`done` and best-effort refunds.
 */
export async function orchestrateDialogue(
  payload: DialoguePayload,
  provider: LlmProvider,
  res: DialogueResponse
): Promise<void> {
  const modelId = modelFor("dialogue");
  const request: GenerateRequest = {
    task: "dialogue",
    modelId,
    payload,
    system: DIALOGUE_SYSTEM_PROMPT,
    responseSchema: DIALOGUE_RESPONSE_SCHEMA,
    // Reserved cache handle (explicit cachedContents deferred → inline system path, decision #15).
    cacheKey: cacheKey("dialogue", DIALOGUE_PROMPT_VERSION, modelId),
  };

  const parser = new IncrementalDialogueParser();
  for await (const chunk of provider.generateStream(request)) {
    const update = parser.addChunk(chunk.raw);
    if (update.meta) {
      writeEvent(res, {
        event: "object",
        data: { type: "dialogueMeta", data: update.meta },
      });
    }
    for (const turn of update.turns) {
      writeEvent(res, { event: "object", data: { type: "turn", data: turn } });
    }
  }

  writeEvent(res, { event: "done", data: { status: "ok" } });
  res.end();
}

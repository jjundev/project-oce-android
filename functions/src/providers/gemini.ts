/**
 * GeminiProvider — the only v1 provider (backend-functions.md:70).
 *
 * SCAFFOLD → M1-05: `tts()` is now implemented (real Gemini `:generateContent` with
 * AUDIO modality). `generateStream`/`generateOnce` remain NOT_IMPLEMENTED until their
 * milestones (M1-02 dialogue, M2-01 summary).
 *
 * NOTE: the Gemini TTS request/response shape below (responseModalities:["AUDIO"],
 * speechConfig.voiceConfig.prebuiltVoiceConfig, inlineData audio parts) is ported from
 * the archived `GeminiTtsManager` and mirrors the public Gemini TTS API — it should be
 * re-verified against current Gemini docs before production (plan decision #4/#9).
 */
import { modelFor } from "../config/models";
import { ErrorCode, Task } from "../types/protocol";
import {
  GenerateRequest,
  LlmProvider,
  RawChunk,
  RawJson,
  TtsResult,
} from "./LlmProvider";

export class NotImplementedError extends Error {
  readonly code = ErrorCode.NOT_IMPLEMENTED;
  constructor(what: string) {
    super(`NOT_IMPLEMENTED: ${what}`);
    this.name = "NotImplementedError";
  }
}

/** Raised when synthesis fails after retries — mapped to 502 TTS_SYNTH_FAILED. */
export class TtsSynthError extends Error {
  readonly code = ErrorCode.TTS_SYNTH_FAILED;
  constructor(what: string) {
    super(`TTS_SYNTH_FAILED: ${what}`);
    this.name = "TtsSynthError";
  }
}

const GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
/** default PCM rate when the response mimeType omits `rate=` (Gemini TTS is 24kHz). */
const DEFAULT_SAMPLE_RATE_HZ = 24000;
const MIME_RATE_RE = /rate=(\d+)/;
/** per-attempt request timeout; watchdog on the client is the harder bound (tts.md §4). */
const REQUEST_TIMEOUT_MS = 7000;
const MAX_ATTEMPTS = 2;

/** minimal shape of `fetch` we depend on — lets tests inject a fake. */
export type FetchFn = (
  url: string,
  init: {
    method: string;
    headers: Record<string, string>;
    body: string;
    signal?: AbortSignal;
  }
) => Promise<{ ok: boolean; status: number; text(): Promise<string> }>;

/** dependencies injected into the provider (kept behind accessors for testability) */
export interface GeminiConfig {
  /** resolve task → model ID (see config/models) */
  modelFor(task: string): string;
  /** lazily read the Gemini API key (Firebase Secret) — never logged */
  apiKey(): string;
  /** HTTP transport — defaults to global fetch, overridable in tests */
  fetchFn?: FetchFn;
}

export class GeminiProvider implements LlmProvider {
  constructor(private readonly config: GeminiConfig) {}

  // eslint-disable-next-line require-yield
  async *generateStream(req: GenerateRequest): AsyncIterable<RawChunk> {
    // seam is wired (task→model resolves); real Gemini streaming lands in M1-02.
    throw new NotImplementedError(
      `GeminiProvider.generateStream (model=${this.config.modelFor(req.task)})`
    );
  }

  /**
   * Single-shot structured JSON generation — summary sub-calls (M2-01). Resolves the
   * model from `req.modelId` (NOT `modelFor(req.task)`: `req.task` is a sub-task string
   * like "summary.expressions" that isn't in the closed `Task` map). On a parse failure
   * it repairs once (prompt-system.md:91); a repeated failure throws, and the summary
   * orchestrator maps any throw → `sections[k]="failed"` (backend-functions.md:117), so
   * no dedicated error class is needed here (plan decision #1).
   */
  async generateOnce(req: GenerateRequest): Promise<RawJson> {
    const url = `${GEMINI_BASE_URL}/models/${req.modelId}:generateContent`;
    const firstText = await this.requestText(
      url,
      buildGenerateBody(req.payload, req.system, req.responseSchema)
    );
    try {
      return extractJson(firstText);
    } catch (parseError) {
      const repaired = await this.requestText(
        url,
        buildRepairBody(
          req.payload,
          req.system,
          req.responseSchema,
          firstText,
          parseError instanceof Error ? parseError.message : String(parseError)
        )
      );
      return extractJson(repaired);
    }
  }

  /**
   * POST a generateContent body and return the raw response text. Mirrors tts()'s
   * reliability shape: per-attempt AbortController timeout, MAX_ATTEMPTS retries on
   * network/5xx, and no retry on 4xx (deterministic). Throws a plain Error on terminal
   * failure.
   */
  private async requestText(url: string, body: unknown): Promise<string> {
    const fetchFn = this.config.fetchFn ?? (globalThis.fetch as FetchFn);
    const payload = JSON.stringify(body);
    let lastError = "";
    for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
      try {
        const res = await fetchFn(url, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "x-goog-api-key": this.config.apiKey(),
          },
          body: payload,
          signal: controller.signal,
        });
        const text = await res.text();
        if (res.ok) {
          return text;
        }
        lastError = `HTTP ${res.status}`;
        // 4xx are deterministic (bad request/quota) — no point retrying.
        if (res.status >= 400 && res.status < 500) {
          throw new Error(`generateOnce ${lastError}`);
        }
        // 5xx — fall through to retry
      } catch (e) {
        if (e instanceof Error && e.message.startsWith("generateOnce HTTP 4")) {
          throw e;
        }
        lastError = e instanceof Error ? e.message : String(e);
      } finally {
        clearTimeout(timer);
      }
    }
    throw new Error(`generateOnce failed after ${MAX_ATTEMPTS} attempts: ${lastError}`);
  }

  async tts(
    text: string,
    voice: string,
    speechRate: number
  ): Promise<TtsResult> {
    const model = this.config.modelFor("tts");
    const url = `${GEMINI_BASE_URL}/models/${model}:generateContent`;
    const body = JSON.stringify(
      buildSynthesisBody(text, voice, speechRate)
    );
    const fetchFn = this.config.fetchFn ?? (globalThis.fetch as FetchFn);

    let lastError = "";
    for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
      try {
        const res = await fetchFn(url, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "x-goog-api-key": this.config.apiKey(),
          },
          body,
          signal: controller.signal,
        });
        const responseText = await res.text();
        if (!res.ok) {
          lastError = `HTTP ${res.status}`;
          // 4xx are deterministic (bad request/quota) — no point retrying.
          if (res.status >= 400 && res.status < 500) {
            throw new TtsSynthError(lastError);
          }
          continue;
        }
        return parseTtsResponse(responseText);
      } catch (e) {
        if (e instanceof TtsSynthError) {
          throw e;
        }
        lastError = e instanceof Error ? e.message : String(e);
      } finally {
        clearTimeout(timer);
      }
    }
    throw new TtsSynthError(`after ${MAX_ATTEMPTS} attempts: ${lastError}`);
  }
}

/**
 * Build the Gemini `:generateContent` body for AUDIO synthesis. The speaking-rate is
 * injected as a prose hint (Gemini TTS has no structured speed param) — best-effort per
 * tts.md §2. Locale is fixed en-US (tts.md §2). Ported from `GeminiTtsManager`.
 */
export function buildSynthesisBody(
  text: string,
  voice: string,
  speechRate: number
): Record<string, unknown> {
  const rate = clampRate(speechRate);
  const prompt =
    "Read the following text out loud only. " +
    "Use pronunciation for locale en-US. " +
    `Aim for speaking speed multiplier ${rate.toFixed(2)}. ` +
    "Do not add commentary or extra words. " +
    `Text: ${text}`;
  return {
    contents: [{ parts: [{ text: prompt }] }],
    generationConfig: {
      responseModalities: ["AUDIO"],
      speechConfig: {
        voiceConfig: { prebuiltVoiceConfig: { voiceName: voice } },
      },
    },
  };
}

/**
 * Extract the first inline audio part → base64 PCM + sample rate. Ported from
 * `GeminiTtsManager.parseAudioFromResponseBody`. Throws TtsSynthError on any shape
 * mismatch (empty body, missing candidates/parts/inlineData).
 */
export function parseTtsResponse(responseBody: string): TtsResult {
  const trimmed = (responseBody ?? "").trim();
  if (!trimmed) {
    throw new TtsSynthError("empty response");
  }
  let root: unknown;
  try {
    root = JSON.parse(trimmed);
  } catch {
    throw new TtsSynthError("invalid JSON response");
  }

  const candidates = (root as { candidates?: unknown }).candidates;
  if (!Array.isArray(candidates) || candidates.length === 0) {
    throw new TtsSynthError("no candidates");
  }
  for (const candidate of candidates) {
    const parts = (candidate as { content?: { parts?: unknown } })?.content
      ?.parts;
    if (!Array.isArray(parts)) {
      continue;
    }
    for (const part of parts) {
      const inline = (part as { inlineData?: { data?: unknown; mimeType?: unknown } })
        ?.inlineData;
      const data = inline?.data;
      if (typeof data !== "string" || data.length === 0) {
        continue;
      }
      const mimeType =
        typeof inline?.mimeType === "string" && inline.mimeType.length > 0
          ? inline.mimeType
          : `audio/L16;rate=${DEFAULT_SAMPLE_RATE_HZ}`;
      return {
        pcmBase64: data,
        sampleRate: parseSampleRate(mimeType),
        mimeType,
      };
    }
  }
  throw new TtsSynthError("missing inline audio data");
}

/** parse `rate=NNN` out of a mimeType; falls back to 24kHz. */
export function parseSampleRate(mimeType: string): number {
  const match = MIME_RATE_RE.exec(mimeType ?? "");
  if (!match) {
    return DEFAULT_SAMPLE_RATE_HZ;
  }
  const parsed = Number.parseInt(match[1], 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : DEFAULT_SAMPLE_RATE_HZ;
}

/** clamp to the ratified 0.5–1.5 range (tts.md:12). */
export function clampRate(rate: number): number {
  if (!Number.isFinite(rate)) {
    return 1.0;
  }
  return Math.min(1.5, Math.max(0.5, rate));
}

/**
 * Factory wiring the seam to real config: task→model via config/models and the
 * Gemini API key (resolved from the `GEMINI_API_KEY` Secret at call time — pass
 * `GEMINI_API_KEY.value()` from the function handler).
 */
export function createGeminiProvider(apiKey: string): GeminiProvider {
  return new GeminiProvider({
    modelFor: (task: string) => modelFor(task as Task),
    apiKey: () => apiKey,
  });
}

/**
 * Build a Gemini `:generateContent` body for structured JSON output (M2-01). The payload
 * slice is sent as one user text part; the resolved prompt goes in `systemInstruction`.
 */
export function buildGenerateBody(
  payload: unknown,
  system?: string,
  responseSchema?: unknown
): Record<string, unknown> {
  const generationConfig: Record<string, unknown> = {
    responseMimeType: "application/json",
  };
  if (responseSchema !== undefined) {
    generationConfig.responseSchema = responseSchema;
  }
  const body: Record<string, unknown> = {
    contents: [{ role: "user", parts: [{ text: JSON.stringify(payload ?? {}) }] }],
    generationConfig,
  };
  if (system) {
    body.systemInstruction = { parts: [{ text: system }] };
  }
  return body;
}

/**
 * Build a repair request: the original body plus the malformed model output and the
 * parse error, asking for valid JSON (prompt-system.md:91 — repair once).
 */
export function buildRepairBody(
  payload: unknown,
  system: string | undefined,
  responseSchema: unknown,
  badOutput: string,
  parseError: string
): Record<string, unknown> {
  const body = buildGenerateBody(payload, system, responseSchema);
  (body.contents as unknown[]).push(
    { role: "model", parts: [{ text: badOutput }] },
    {
      role: "user",
      parts: [
        {
          text:
            `The previous response was not valid JSON (${parseError}). ` +
            "Respond again with ONE valid JSON object only — no code fences, " +
            "no commentary — matching the required schema.",
        },
      ],
    }
  );
  return body;
}

/**
 * Pull the first text part out of a generateContent response and JSON.parse it into a
 * top-level object. Throws on a shapeless response or non-object/invalid JSON — the
 * caller (generateOnce) treats a throw as a parse failure and repairs once.
 */
export function extractJson(responseBody: string): RawJson {
  const text = extractFirstText(responseBody);
  const parsed = JSON.parse(stripCodeFences(text));
  if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
    throw new Error("generateOnce: expected a top-level JSON object");
  }
  return parsed as RawJson;
}

function extractFirstText(responseBody: string): string {
  const trimmed = (responseBody ?? "").trim();
  if (!trimmed) {
    throw new Error("generateOnce: empty response");
  }
  const root = JSON.parse(trimmed) as {
    candidates?: Array<{ content?: { parts?: Array<{ text?: unknown }> } }>;
  };
  const parts = root.candidates?.[0]?.content?.parts;
  if (!Array.isArray(parts)) {
    throw new Error("generateOnce: no candidate parts");
  }
  const text = parts
    .map((p) => (typeof p.text === "string" ? p.text : ""))
    .join("");
  if (!text) {
    throw new Error("generateOnce: no text part");
  }
  return text;
}

/** Strip a leading/trailing ```json … ``` fence if the model added one. */
function stripCodeFences(text: string): string {
  const trimmed = text.trim();
  const fenced = /^```(?:json)?\s*([\s\S]*?)\s*```$/.exec(trimmed);
  return (fenced ? fenced[1] : trimmed).trim();
}

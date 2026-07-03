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

/** Raised when speaking analysis fails after retries — mapped to 502 SPEAKING_ANALYZE_FAILED. */
export class SpeakingAnalyzeError extends Error {
  readonly code = ErrorCode.SPEAKING_ANALYZE_FAILED;
  constructor(what: string) {
    super(`SPEAKING_ANALYZE_FAILED: ${what}`);
    this.name = "SpeakingAnalyzeError";
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
   * Single-shot speaking analysis — M1-06. Sends the WAV audio as an inline part plus the
   * (inline) system prompt and a structured `responseSchema`, then returns the model's
   * parsed `{transcript, feedbackMessage}` JSON. Only `speaking` is wired; other one-shot
   * tasks (summary sub-calls) land in their own milestones.
   */
  async generateOnce(req: GenerateRequest): Promise<RawJson> {
    if (req.task !== "speaking") {
      throw new NotImplementedError(
        `GeminiProvider.generateOnce (model=${this.config.modelFor(req.task)})`
      );
    }
    const audioBase64 = (req.payload as { audioBase64?: unknown } | undefined)
      ?.audioBase64;
    if (typeof audioBase64 !== "string" || audioBase64.length === 0) {
      throw new SpeakingAnalyzeError("missing audioBase64 in payload");
    }
    const model = this.config.modelFor("speaking");
    const url = `${GEMINI_BASE_URL}/models/${model}:generateContent`;
    const body = JSON.stringify(buildAnalysisBody(audioBase64));
    const responseText = await this.postWithRetry(
      url,
      body,
      (msg) => new SpeakingAnalyzeError(msg)
    );
    return parseAnalysisResponse(responseText);
  }

  async tts(
    text: string,
    voice: string,
    speechRate: number
  ): Promise<TtsResult> {
    const model = this.config.modelFor("tts");
    const url = `${GEMINI_BASE_URL}/models/${model}:generateContent`;
    const body = JSON.stringify(buildSynthesisBody(text, voice, speechRate));
    const responseText = await this.postWithRetry(
      url,
      body,
      (msg) => new TtsSynthError(msg)
    );
    return parseTtsResponse(responseText);
  }

  /**
   * Shared Gemini `:generateContent` transport: per-attempt timeout + up to MAX_ATTEMPTS,
   * with 4xx treated as terminal (no retry). Returns the raw response text on success, or
   * throws the caller's typed error (TtsSynthError / SpeakingAnalyzeError) — so a failed
   * tts and a failed speaking analysis map to their own distinct error codes.
   */
  private async postWithRetry(
    url: string,
    body: string,
    makeError: (message: string) => Error
  ): Promise<string> {
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
            throw makeError(lastError);
          }
          continue;
        }
        return responseText;
      } catch (e) {
        // A typed terminal error (from the 4xx branch or a caller parser) is final.
        if (e instanceof TtsSynthError || e instanceof SpeakingAnalyzeError) {
          throw e;
        }
        lastError = e instanceof Error ? e.message : String(e);
      } finally {
        clearTimeout(timer);
      }
    }
    throw makeError(`after ${MAX_ATTEMPTS} attempts: ${lastError}`);
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
 * Speaking-analysis system prompt — B-1 content (speaking-analyze.md rules 1-4) inlined as
 * a server constant, mirroring how `buildSynthesisBody` inlines the tts prompt (config/
 * prompts + cachedContents migration is a documented follow-up, backend-functions.md §6).
 * The output is intentionally narrowed to `{transcript, feedbackMessage}` — NO numeric
 * score field (PRD A8/R3), enforced structurally by `responseSchema` below.
 */
export const SPEAKING_SYSTEM_PROMPT =
  "You are an English speaking coach for Korean learners. Listen to the user's speaking " +
  "audio and return ONE valid JSON object with keys `transcript` and `feedbackMessage`.\n" +
  "1. `transcript` = a faithful, verbatim transcription of what the user ACTUALLY said, " +
  "including hesitations or partial words. Do NOT guess, complete, or 'correct' it toward " +
  "any expected sentence. If the audio is unintelligible or empty, return an empty string.\n" +
  "2. `feedbackMessage` = one short, warm Korean coaching line in 해요체 about the delivery " +
  "(natural, clear, confident, or e.g. '조금만 더 천천히 말해볼까요?'). It is emotional/" +
  "encouraging support, NOT a correctness judgment of the English. Max 2 lines, benefit-" +
  "first, no jargon.\n" +
  "3. Do NOT output any score, number, or rating. No fluency/confidence/hesitation counts.\n" +
  "4. Return JSON only — no code fences, no extra keys, no text outside the object.";

/**
 * Build the Gemini `:generateContent` body for speaking analysis: the WAV audio as an
 * inline part + the system prompt, with a structured `responseSchema` forcing exactly
 * `{transcript, feedbackMessage}` (both strings). `responseMimeType: application/json`
 * makes Gemini emit the object as the candidate's text part.
 */
export function buildAnalysisBody(audioBase64: string): Record<string, unknown> {
  return {
    contents: [
      {
        parts: [
          { inlineData: { mimeType: "audio/wav", data: audioBase64 } },
          { text: SPEAKING_SYSTEM_PROMPT },
        ],
      },
    ],
    generationConfig: {
      responseMimeType: "application/json",
      responseSchema: {
        type: "OBJECT",
        properties: {
          transcript: { type: "STRING" },
          feedbackMessage: { type: "STRING" },
        },
        required: ["transcript", "feedbackMessage"],
      },
    },
  };
}

/**
 * Extract the first candidate's JSON text part and parse it into the raw model object.
 * With `responseMimeType: application/json` Gemini returns the object as text, so we
 * JSON.parse the text part. Throws SpeakingAnalyzeError on any shape mismatch (empty body,
 * missing candidates/parts/text, or non-JSON text). Shape validation of the parsed keys
 * happens one layer up in `analyzeSpeaking`.
 */
export function parseAnalysisResponse(responseBody: string): RawJson {
  const trimmed = (responseBody ?? "").trim();
  if (!trimmed) {
    throw new SpeakingAnalyzeError("empty response");
  }
  let root: unknown;
  try {
    root = JSON.parse(trimmed);
  } catch {
    throw new SpeakingAnalyzeError("invalid JSON response");
  }

  const candidates = (root as { candidates?: unknown }).candidates;
  if (!Array.isArray(candidates) || candidates.length === 0) {
    throw new SpeakingAnalyzeError("no candidates");
  }
  for (const candidate of candidates) {
    const parts = (candidate as { content?: { parts?: unknown } })?.content
      ?.parts;
    if (!Array.isArray(parts)) {
      continue;
    }
    for (const part of parts) {
      const text = (part as { text?: unknown })?.text;
      if (typeof text !== "string" || text.trim().length === 0) {
        continue;
      }
      try {
        return JSON.parse(text.trim()) as RawJson;
      } catch {
        throw new SpeakingAnalyzeError("candidate text is not valid JSON");
      }
    }
  }
  throw new SpeakingAnalyzeError("missing text part");
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

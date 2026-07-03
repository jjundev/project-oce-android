/**
 * Vendor-neutral LLM seam — backend-functions.md:62-71.
 * v1 has a single implementation (GeminiProvider); a future vendor swap requires
 * zero client changes. `transcribe` is intentionally absent — Gemini handles
 * audio via generateContent, unified into `generateOnce`.
 */

/** one completed vendor chunk from a streaming response */
export interface RawChunk {
  raw: string;
}

/** parsed single-shot vendor JSON */
export type RawJson = Record<string, unknown>;

/** base64-encoded PCM audio (TTS output) */
export type Base64Pcm = string;

/**
 * TTS synthesis result — M1-05 (decision #1-new-seam). Deliberately WIDER than a
 * bare `Base64Pcm` string: the caller needs the real `sampleRate` (Gemini declares it
 * in the audio part's `mimeType`, e.g. `audio/L16;rate=24000`) to play the PCM back at
 * the correct pitch. Widening this ratified seam (was `Promise<Base64Pcm>`) is an
 * intentional, recorded interface change, not an accident.
 */
export interface TtsResult {
  pcmBase64: Base64Pcm;
  sampleRate: number;
  mimeType: string;
}

export interface GenerateRequest {
  task: string;
  modelId: string;
  payload: unknown;
}

export interface LlmProvider {
  /** SSE source — dialogue / feedback / summary */
  generateStream(req: GenerateRequest): AsyncIterable<RawChunk>;
  /** single-shot — speaking (audio payload), summary sub-calls */
  generateOnce(req: GenerateRequest): Promise<RawJson>;
  /**
   * text-to-speech. `voice` is the resolved Gemini prebuilt voice name (caller maps
   * gender→voice); `speechRate` is a best-effort prompt hint (0.5–1.5).
   */
  tts(text: string, voice: string, speechRate: number): Promise<TtsResult>;
}

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
  /** text-to-speech */
  tts(text: string, voice: string): Promise<Base64Pcm>;
}

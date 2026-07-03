/**
 * GeminiProvider — the only v1 provider (backend-functions.md:70).
 *
 * SCAFFOLD: methods are wired to config (model IDs, API-key accessor) but their
 * bodies throw NOT_IMPLEMENTED. Real Gemini calls (streaming parse, cachedContents,
 * audio, TTS) land in M1+. The proxy therefore builds and unit-tests without B-1.
 */
import { modelFor } from "../config/models";
import { ErrorCode, Task } from "../types/protocol";
import {
  Base64Pcm,
  GenerateRequest,
  LlmProvider,
  RawChunk,
  RawJson,
} from "./LlmProvider";

export class NotImplementedError extends Error {
  readonly code = ErrorCode.NOT_IMPLEMENTED;
  constructor(what: string) {
    super(`NOT_IMPLEMENTED: ${what}`);
    this.name = "NotImplementedError";
  }
}

/** dependencies injected into the provider (kept behind accessors for testability) */
export interface GeminiConfig {
  /** resolve task → model ID (see config/models) */
  modelFor(task: string): string;
  /** lazily read the Gemini API key (Firebase Secret) — never logged */
  apiKey(): string;
}

export class GeminiProvider implements LlmProvider {
  constructor(private readonly config: GeminiConfig) {}

  // eslint-disable-next-line require-yield
  async *generateStream(req: GenerateRequest): AsyncIterable<RawChunk> {
    // seam is wired (task→model resolves); real Gemini streaming lands in M1+.
    throw new NotImplementedError(
      `GeminiProvider.generateStream (model=${this.config.modelFor(req.task)})`
    );
  }

  async generateOnce(req: GenerateRequest): Promise<RawJson> {
    throw new NotImplementedError(
      `GeminiProvider.generateOnce (model=${this.config.modelFor(req.task)})`
    );
  }

  async tts(_text: string, voice: string): Promise<Base64Pcm> {
    throw new NotImplementedError(`GeminiProvider.tts (voice=${voice})`);
  }
}

/**
 * Factory wiring the seam to real config: task→model via config/models and the
 * Gemini API key (resolved from the `GEMINI_API_KEY` Secret at call time — pass
 * `GEMINI_API_KEY.value()` from the function handler). This is the construction
 * anchor for M1; the provider itself still throws NOT_IMPLEMENTED until then.
 */
export function createGeminiProvider(apiKey: string): GeminiProvider {
  return new GeminiProvider({
    modelFor: (task: string) => modelFor(task as Task),
    apiKey: () => apiKey,
  });
}

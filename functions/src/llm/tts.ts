/**
 * `task=tts` handler — M1-05. Single-shot JSON: synthesize the opponent's line via the
 * provider and return base64 PCM + sample rate (backend-functions.md:52, tts.md §3).
 *
 * Server owns the voice/locale policy (code-fixed, non-exposed): the client sends only
 * `{text, gender, speechRate}`; this handler maps gender→voice (tts.md:7) and hands the
 * resolved voice to the provider. No sessionId / cap gate: the SoT (backend-functions.md
 * §8) is silent on tts cost — shipping tts authenticated-but-ungated in v1 is a
 * deliberate, user-confirmed decision (#16), NOT an SoT-sanctioned exclusion. Cost is
 * observed via usageMetadata logging + budget alerts (§12); a coarse per-session tts
 * counter is a documented follow-up, not part of M1-05.
 */
import { LlmProvider, TtsResult } from "../providers/LlmProvider";
import { TtsRequestPayload, TtsResponse } from "../types/protocol";

/** Gemini prebuilt voice for the opponent's gender — ports `resolveGeminiVoiceName`. */
export function resolveVoiceName(gender: string | undefined): string {
  return gender?.toLowerCase() === "male" ? "Puck" : "Kore";
}

/** thrown when the tts payload is malformed (empty text) — mapped to 400. */
export class InvalidTtsPayloadError extends Error {}

/**
 * Validate + narrow an untrusted payload into a `TtsRequestPayload`. `text` is required
 * and non-empty; `gender`/`speechRate` are optional and defaulted downstream.
 */
export function parseTtsPayload(payload: unknown): TtsRequestPayload {
  const p = (payload ?? {}) as Partial<TtsRequestPayload>;
  const text = typeof p.text === "string" ? p.text.trim() : "";
  if (!text) {
    throw new InvalidTtsPayloadError("tts payload requires non-empty text");
  }
  const gender = p.gender === "male" || p.gender === "female" ? p.gender : undefined;
  const speechRate = typeof p.speechRate === "number" ? p.speechRate : 1.0;
  return { text, gender, speechRate };
}

/**
 * Run tts synthesis for a validated payload. Voice mapping + rate default live here;
 * the provider clamps the rate and shapes the Gemini request. Returns the wire response.
 */
export async function synthesizeTts(
  payload: TtsRequestPayload,
  provider: LlmProvider
): Promise<TtsResponse> {
  const voice = resolveVoiceName(payload.gender);
  const result: TtsResult = await provider.tts(
    payload.text,
    voice,
    payload.speechRate ?? 1.0
  );
  return {
    pcmBase64: result.pcmBase64,
    sampleRate: result.sampleRate,
    mimeType: result.mimeType,
  };
}

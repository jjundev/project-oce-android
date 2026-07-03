/**
 * `task=speaking` handler logic — M1-06. Single-shot JSON: forward the learner's WAV audio
 * to Gemini via the provider and return `{transcript, feedbackMessage}` (speaking-analyze.md,
 * backend-functions.md:52). Mirrors `tts.ts` in shape.
 *
 * Contract notes:
 * - `sessionId` is a TOP-LEVEL envelope field (RequestBody.sessionId), validated in
 *   handle.ts — it is NOT part of this payload (backend-functions.md:45,47).
 * - No numeric score is produced or accepted; the response shape is narrowed by design
 *   (PRD A8/R3). An unintelligible/empty clip yields `transcript: ""` (a 200, not an error)
 *   per the prompt's rule 1 — the client renders a gentle retry (M1-08), not a failure.
 */
import { modelFor } from "../config/models";
import { LlmProvider } from "../providers/LlmProvider";
import { SpeakingAnalyzeError } from "../providers/gemini";
import { SpeakingRequestPayload, SpeakingResponse } from "../types/protocol";

/** thrown when the speaking payload is malformed (empty audio) — mapped to 400. */
export class InvalidSpeakingPayloadError extends Error {}

/**
 * Validate + narrow an untrusted payload into a `SpeakingRequestPayload`. Only `audioBase64`
 * lives here and must be a non-empty string; `sessionId` is validated separately at the
 * envelope level (handle.ts).
 */
export function parseSpeakingPayload(payload: unknown): SpeakingRequestPayload {
  const p = (payload ?? {}) as Partial<SpeakingRequestPayload>;
  const audioBase64 = typeof p.audioBase64 === "string" ? p.audioBase64.trim() : "";
  if (!audioBase64) {
    throw new InvalidSpeakingPayloadError(
      "speaking payload requires non-empty audioBase64"
    );
  }
  return { audioBase64 };
}

/**
 * Run speaking analysis for a validated payload via `generateOnce`, then narrow the raw model
 * object to the wire response. A shape mismatch (missing/non-string keys) is a synthesis-side
 * failure → SpeakingAnalyzeError (502). `transcript` may legitimately be the empty string.
 */
export async function analyzeSpeaking(
  payload: SpeakingRequestPayload,
  provider: LlmProvider
): Promise<SpeakingResponse> {
  const raw = await provider.generateOnce({
    task: "speaking",
    modelId: modelFor("speaking"),
    payload: { audioBase64: payload.audioBase64 },
  });
  const transcript = typeof raw.transcript === "string" ? raw.transcript : undefined;
  const feedbackMessage =
    typeof raw.feedbackMessage === "string" ? raw.feedbackMessage : undefined;
  if (transcript === undefined || feedbackMessage === undefined) {
    throw new SpeakingAnalyzeError(
      "response missing transcript/feedbackMessage keys"
    );
  }
  return { transcript, feedbackMessage };
}

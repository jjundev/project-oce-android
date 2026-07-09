/**
 * task → model ID mapping. Server-only, never exposed to the client, so model
 * IDs can be swapped without a client release (backend-functions.md:76).
 *
 * Decision C (user-confirmed): these are PLACEHOLDER IDs for the scaffold. The
 * real GA Gemini / TTS model IDs are an ops choice and should be moved to the
 * `config/models` Firestore doc for no-redeploy swap once confirmed.
 */
import { Task } from "../types/protocol";

export const MODEL_IDS: Record<Task, string> = {
  // All text tasks run on gemini-3.1-flash-lite (Vertex express) — newer generation and cheaper
  // than 2.5-flash (output -40%, audio input -50%). dialogue was validated 2026-07-08 against the
  // real prompt+responseSchema; the rest moved 2026-07-09 on the same key (only 3.1 model exposed;
  // 3.1-pro/3.1-flash are 404). TTS stays on 2.5 (3.1 TTS costs 2x per audio-output token).
  dialogue: "gemini-3.1-flash-lite",
  speaking: "gemini-3.1-flash-lite",
  feedback: "gemini-3.1-flash-lite",
  feedbackDeep: "gemini-3.1-flash-lite",
  summary: "gemini-3.1-flash-lite",
  tts: "gemini-2.5-flash-preview-tts",
};

export function modelFor(task: Task): string {
  return MODEL_IDS[task];
}

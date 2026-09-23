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
  // dialogue/speaking/summary run on gemini-3.5-flash-lite (Vertex express), the official
  // successor to 3.1-flash-lite. Moved 2026-09-23: ~5x lower time-to-first-token for +20% input /
  // +67% output price ($0.30/$2.50 vs $0.25/$1.50 per 1M). Thinking is pinned to MINIMAL in
  // config/generation.ts so thought tokens (billed as output) stay near zero.
  // feedback/feedbackDeep STAY on gemini-3.1-flash-lite: the 2026-09-23 eval (14 cases x 3, t=0)
  // showed 3.5 regress on grading — 5-7 missed/over-marked learner errors vs 0 and score stddev
  // 1.6-2.0 vs 0.0 — at both MINIMAL and LOW thinking. 3.1 shuts down 2027-05-07, so the
  // feedback prompts must be re-tuned for 3.5 and re-evaluated before then.
  // TTS stays on 2.5 (3.1 TTS costs 2x per audio-output token).
  dialogue: "gemini-3.5-flash-lite",
  speaking: "gemini-3.5-flash-lite",
  feedback: "gemini-3.1-flash-lite",
  feedbackDeep: "gemini-3.1-flash-lite",
  summary: "gemini-3.5-flash-lite",
  tts: "gemini-2.5-flash-preview-tts",
};

export function modelFor(task: Task): string {
  return MODEL_IDS[task];
}

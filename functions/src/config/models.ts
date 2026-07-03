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
  dialogue: "gemini-2.5-flash",
  speaking: "gemini-2.5-flash",
  feedback: "gemini-2.5-flash",
  summary: "gemini-2.5-flash",
  tts: "gemini-2.5-flash-preview-tts",
};

export function modelFor(task: Task): string {
  return MODEL_IDS[task];
}

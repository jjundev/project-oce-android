/**
 * task → generation tuning (sampling parameters). Server-only, mirroring
 * config/models.ts so tuning can be swapped without a client release.
 *
 * prompt-system.md:106 left temperature as an explicit `needs-you` assumption
 * (대본 0.8 / 피드백·요약 0.3) and it was never wired — every task ran on Gemini's
 * undocumented default. This table wires it for the feedback family ONLY: those two
 * are the tasks the eval harness (src/eval/) actually measures. dialogue and summary
 * stay unset until they have eval coverage of their own, so this change cannot
 * regress behaviour nothing here observes.
 */
import { Task } from "../types/protocol";

export interface GenerationTuning {
  /** sampling temperature; `undefined` = omit the key entirely (provider default) */
  temperature?: number;
}

/**
 * Slim + deep feedback temperature. Feedback is a GRADING call — the same learner
 * sentence should score the same on a re-run — so it wants low variance, not variety.
 * Confirmed by the temperature sweep in eval/run.js; see docs/design/prompt-system.md §9.
 */
export const FEEDBACK_TEMPERATURE = 0.3;

export const GENERATION_TUNING: Record<Task, GenerationTuning> = {
  dialogue: {},
  speaking: {},
  feedback: { temperature: FEEDBACK_TEMPERATURE },
  feedbackDeep: { temperature: FEEDBACK_TEMPERATURE },
  summary: {},
  tts: {},
};

/**
 * Resolve tuning for a task or sub-task id. Sub-task ids ("summary.expressions") are
 * normalised to their family ("summary") — the closed `Task` map has no entry for them
 * (gemini.ts:203-205). An unknown task falls back to empty tuning, which omits every
 * key and therefore preserves the provider default.
 */
export function tuningFor(task: string): GenerationTuning {
  const family = task.split(".")[0] as Task;
  return GENERATION_TUNING[family] ?? {};
}

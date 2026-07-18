/**
 * task → generation tuning (sampling parameters). Server-only, mirroring
 * config/models.ts so tuning can be swapped without a client release.
 *
 * prompt-system.md:106 left temperature as an explicit `needs-you` assumption
 * (대본 0.8 / 피드백·요약 0.3) and it was never wired — every task ran on Gemini's
 * undocumented default. STATUS QUO for this branch: every task, including feedback
 * and feedbackDeep, STILL runs on the provider default — this branch ships as pure
 * measurement infrastructure (the eval harness in functions/eval/) with ZERO production
 * behaviour change. `FEEDBACK_TEMPERATURE` below is the staged candidate value the
 * harness exists to validate; a later task runs the sweep and, once it confirms the
 * value, flips `feedback`/`feedbackDeep` in the table below to apply it.
 */
import { Task } from "../types/protocol";

export interface GenerationTuning {
  /** sampling temperature; `undefined` = omit the key entirely (provider default) */
  temperature?: number;
}

/**
 * Staged candidate for slim + deep feedback temperature — NOT currently applied (see
 * `GENERATION_TUNING` below, where `feedback`/`feedbackDeep` are still `{}`). Feedback is
 * a GRADING call — the same learner sentence should score the same on a re-run — so it
 * wants low variance, not variety, which is why 0.3 (inherited from
 * docs/design/prompt-system.md §9) is the target worth testing. A later task runs the
 * temperature sweep in functions/eval/ against this value; only once that sweep confirms
 * it does someone put `{ temperature: FEEDBACK_TEMPERATURE }` back into the table.
 */
export const FEEDBACK_TEMPERATURE = 0.3;

/**
 * Every task is currently on the provider default (`{}`). This is deliberate, not an
 * oversight: this branch measures feedback quality first (functions/eval/) and changes
 * sampling behaviour second, in a follow-up task, once the sweep confirms
 * `FEEDBACK_TEMPERATURE` is actually an improvement.
 */
export const GENERATION_TUNING: Record<Task, GenerationTuning> = {
  dialogue: {},
  speaking: {},
  feedback: {},
  feedbackDeep: {},
  summary: {},
  tts: {},
};

/**
 * Resolve tuning for a task or sub-task id. Sub-task ids ("summary.expressions") are
 * normalised to their family ("summary") — the closed `Task` map has no entry for them
 * (gemini.ts:203-205). An unknown task falls back to empty tuning, which omits every
 * key and therefore preserves the provider default. Uses `Object.prototype.hasOwnProperty`
 * (not a plain index) so a task string like "__proto__" can't fall through to
 * `Object.prototype` instead of the intended empty-tuning fallback, and returns a fresh
 * copy so a caller mutating the result cannot corrupt the shared table.
 */
export function tuningFor(task: string): GenerationTuning {
  const family = task.split(".")[0];
  if (!Object.prototype.hasOwnProperty.call(GENERATION_TUNING, family)) {
    return {};
  }
  return { ...GENERATION_TUNING[family as Task] };
}

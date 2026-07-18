/**
 * task → generation tuning (sampling parameters). Server-only, mirroring
 * config/models.ts so tuning can be swapped without a client release.
 *
 * prompt-system.md:106 originally left temperature as an explicit `needs-you` assumption
 * (대본 0.8 / 피드백·요약 0.3) and it was never wired — every task ran on Gemini's
 * undocumented default. The feedback/feedbackDeep temperature below is no longer a
 * guess: it was measured by the eval harness in functions/eval/ (280 real calls against
 * `gemini-3.1-flash-lite`, 2026-07-18) and confirmed at `0`, which OVERRIDES the design
 * doc's unverified `0.3` assumption — see docs/design/prompt-system.md §9 for the
 * decision-log entry. dialogue/speaking/summary/tts still have no eval coverage and stay
 * on the provider default until they get one.
 */
import { Task } from "../types/protocol";

export interface GenerationTuning {
  /** sampling temperature; `undefined` = omit the key entirely (provider default) */
  temperature?: number;
}

/**
 * Slim + deep feedback temperature. Feedback is a GRADING call — the same learner
 * sentence should score the same on a re-run — so it wants low variance, not variety.
 *
 * Confirmed by a 280-call temperature sweep (2026-07-18, `gemini-3.1-flash-lite`,
 * 14 golden cases × temps 0/0.1/0.2/0.3 × 5 repeats): 0 transport failures, 0 parse
 * failures, 0 structural violations and 0 expectation mismatches at every temperature
 * tested. Score stability (mean population stddev across repeats of the same input) was
 * 0.6 at t=0 versus 1.2–1.4 at t=0.1/0.2/0.3 — the three non-zero temperatures are
 * statistically indistinguishable from each other, so the only choice that matters is
 * zero vs. not-zero, and zero roughly halves the variance at no measured cost. An
 * earlier, since-dropped sweep at t=0.7 additionally showed the model failing to mark a
 * learner error at all on one run, which is why higher temperatures were ruled out
 * rather than explored further. See functions/eval/out/ and functions/eval/README.md.
 */
export const FEEDBACK_TEMPERATURE = 0;

/**
 * feedback/feedbackDeep now carry the measured temperature above. dialogue, speaking,
 * summary and tts are deliberately left unset (provider default) — they have no eval
 * coverage, so setting a temperature for them could regress behaviour nothing here
 * measures.
 *
 * Note: `0` is falsy. `buildGenerateBody` (providers/gemini.ts) uses an explicit
 * `tuning?.temperature !== undefined` check rather than `if (tuning?.temperature)` so a
 * zero temperature actually survives into the request body — that defensive choice is
 * load-bearing now, not hypothetical. Do not "simplify" it to a truthiness check.
 */
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

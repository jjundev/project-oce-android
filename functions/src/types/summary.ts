/**
 * Summary orchestration types — M2-01 (backend-functions.md §10, prompt-system.md §4.2).
 *
 * The `summary` task fans out to three internal Gemini calls. Their SECTION names are
 * PLURAL (`expressions|words|coaching`, matching `done.sections` — backend-functions.md:56)
 * while the per-card `kind` is SINGULAR (`expression|word|coaching`, `SummaryCardKind` in
 * types/sse.ts:22). The orchestrator maps section → kind; keep the two vocabularies
 * straight — they are easy to typo for each other.
 */

/** the three summary sub-calls, keyed by their (plural) section name */
export type SummarySection = "expressions" | "words" | "coaching";

/** canonical section order (also the default when the client sends no `sections` filter) */
export const SUMMARY_SECTIONS: readonly SummarySection[] = [
  "expressions",
  "words",
  "coaching",
];

/** one turn from the client buffer — feeds coaching (and, via score, totalScore). */
export interface SummaryTurn {
  koreanPrompt?: string;
  before?: string;
  after?: string;
  /** per-turn slim writingScore (0–100) — averaged into totalScore when absent. */
  score?: number;
}

/** one before/after candidate — feeds the expressions filter. */
export interface ExpressionCandidate {
  type?: string;
  koreanPrompt?: string;
  before?: string;
  after?: string;
  explanation?: string;
}

/**
 * `task=summary` request payload. The CLIENT projects its turn buffer into these
 * already-shaped fields before sending (buffer→sub-call input projection is the
 * client's job / M2-02, prompt-system.md:71) — the backend does not derive `words[]`
 * from raw turns. `parseSummaryPayload` only validates presence/type + the `sections`
 * filter; the actual filter/dedupe/rewrite is done by the Gemini sub-calls.
 */
export interface SummaryPayload {
  expressionCandidates: ExpressionCandidate[];
  words: string[];
  sentences: string[];
  userOriginalSentences: string[];
  turns: SummaryTurn[];
  /** session score (0–100). If absent, the server averages `turns[].score`. */
  totalScore?: number;
  /**
   * Retry filter — run only these sections and report only them in `done.sections`.
   * Absent = all three. When present it MUST be a non-empty subset of the valid keys
   * (empty / unknown → 400 INVALID_PAYLOAD).
   */
  sections?: SummarySection[];
}

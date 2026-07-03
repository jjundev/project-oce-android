/**
 * `task=summary` orchestration — M2-01 (backend-functions.md §10, prompt-system.md §4.2).
 *
 * The three summary sub-calls (expressions·words·coaching) run in PARALLEL; each card is
 * streamed the moment its call settles (per-promise `.then`/`.catch`, NOT a batch after
 * all settle), so a fast card shows while slower calls are still in flight. Partial
 * failure is carried by `done.sections`, never `event:error` — a per-section `ok|failed`
 * lets the client tell "empty" from "retry". The client retries by resending only the
 * failed sections via `payload.sections` and keeps its already-received cards
 * (backend derivation is stateless — no server-side turn store, prompt-system.md:71).
 */
import { modelFor } from "../config/models";
import { SUMMARY_PROMPTS } from "../config/summary-prompts";
import { cacheKey } from "./cacheKey";
import { SseWritable, writeEvent } from "./sse";
import { GenerateRequest, LlmProvider, RawJson } from "../providers/LlmProvider";
import { SummaryCardKind } from "../types/sse";
import {
  SUMMARY_SECTIONS,
  SummaryPayload,
  SummarySection,
} from "../types/summary";

/** thrown when the summary payload is malformed — mapped to 400 INVALID_PAYLOAD. */
export class InvalidSummaryPayloadError extends Error {}

/** response surface the orchestrator needs: SSE writes + stream close. */
export interface SummaryResponse extends SseWritable {
  end(): unknown;
}

/** section (plural) → card kind (singular), types/sse.ts:22. */
const SECTION_TO_KIND: Record<SummarySection, SummaryCardKind> = {
  expressions: "expression",
  words: "word",
  coaching: "coaching",
};

function stringArray(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((v): v is string => typeof v === "string")
    : [];
}

/**
 * Validate + narrow an untrusted payload. Arrays default to empty (an empty section is
 * a valid "nothing to keep" → `ok` with an empty card, not a failure). Only a malformed
 * `sections` filter is rejected: it must be a non-empty subset of the valid keys.
 */
export function parseSummaryPayload(payload: unknown): SummaryPayload {
  const p = (payload ?? {}) as Record<string, unknown>;

  let sections: SummarySection[] | undefined;
  if (p.sections !== undefined) {
    if (!Array.isArray(p.sections) || p.sections.length === 0) {
      throw new InvalidSummaryPayloadError("sections must be a non-empty array");
    }
    const valid = new Set<string>(SUMMARY_SECTIONS);
    for (const s of p.sections) {
      if (typeof s !== "string" || !valid.has(s)) {
        throw new InvalidSummaryPayloadError(`unknown section: ${String(s)}`);
      }
    }
    // dedupe while preserving first-seen order
    sections = [...new Set(p.sections as SummarySection[])];
  }

  return {
    expressionCandidates: Array.isArray(p.expressionCandidates)
      ? p.expressionCandidates
      : [],
    words: stringArray(p.words),
    sentences: stringArray(p.sentences),
    userOriginalSentences: stringArray(p.userOriginalSentences),
    turns: Array.isArray(p.turns) ? p.turns : [],
    totalScore: typeof p.totalScore === "number" ? p.totalScore : undefined,
    sections,
  };
}

/**
 * Session score used by the expressions filter + coaching. Prefer the client value;
 * otherwise average the per-turn scores server-side (decision #14). Returns undefined
 * when neither is available (the prompts tolerate a missing score).
 */
export function resolveTotalScore(payload: SummaryPayload): number | undefined {
  if (typeof payload.totalScore === "number") {
    return payload.totalScore;
  }
  const scores = payload.turns
    .map((t) => t.score)
    .filter((s): s is number => typeof s === "number");
  if (scores.length === 0) {
    return undefined;
  }
  return Math.round(scores.reduce((a, b) => a + b, 0) / scores.length);
}

/**
 * Project the buffer into a section's sub-call input. `totalScore` is merged ONLY into
 * expressions (filter strictness) and coaching — NOT words, which doesn't consume it
 * (summary-expressions.md:4, summary-coaching.md:4; decision #14).
 */
function sliceFor(
  section: SummarySection,
  payload: SummaryPayload,
  totalScore: number | undefined
): unknown {
  switch (section) {
    case "expressions":
      return { totalScore, expressionCandidates: payload.expressionCandidates };
    case "words":
      return {
        words: payload.words,
        sentences: payload.sentences,
        userOriginalSentences: payload.userOriginalSentences,
      };
    case "coaching":
      return { totalScore, turns: payload.turns };
  }
}

/** Shape a sub-call result into the typed `summaryCard` data for its section. */
function cardData(
  section: SummarySection,
  result: RawJson
): { kind: SummaryCardKind } & Record<string, unknown> {
  const kind = SECTION_TO_KIND[section];
  if (section === "coaching") {
    const feedback =
      (result.futureSelfFeedback as Record<string, unknown> | undefined) ?? {
        positive: "",
        toImprove: "",
      };
    return { kind, futureSelfFeedback: feedback };
  }
  return { kind, items: Array.isArray(result.items) ? result.items : [] };
}

/**
 * Run the requested sections in parallel, streaming each card on settle, then emit a
 * single terminal `done` carrying the per-section verdicts. Never throws: each sub-call
 * is individually caught → `sections[k]="failed"`, so a section failure degrades to a
 * `failed` verdict rather than aborting the stream.
 */
export async function orchestrateSummary(
  payload: SummaryPayload,
  provider: LlmProvider,
  res: SummaryResponse
): Promise<void> {
  const sections = payload.sections ?? [...SUMMARY_SECTIONS];
  const totalScore = resolveTotalScore(payload);
  const modelId = modelFor("summary");
  const verdicts: Record<string, "ok" | "failed"> = {};

  await Promise.all(
    sections.map(async (section) => {
      const promptSpec = SUMMARY_PROMPTS[section];
      const request: GenerateRequest = {
        task: `summary.${section}`,
        modelId,
        payload: sliceFor(section, payload, totalScore),
        system: promptSpec.system,
        responseSchema: promptSpec.responseSchema,
        // Reserved cache handle (explicit cachedContents deferred → inline path, #9).
        cacheKey: cacheKey(`summary.${section}`, promptSpec.promptVersion, modelId),
      };
      try {
        const result = await provider.generateOnce(request);
        // Stream the card the moment THIS call settles (arrival order).
        writeEvent(res, {
          event: "object",
          data: { type: "summaryCard", data: cardData(section, result) },
        });
        verdicts[section] = "ok";
      } catch {
        verdicts[section] = "failed";
      }
    })
  );

  // done.status is a plan-introduced convention: "ok" whenever the orchestration ran to
  // completion (transport succeeded); per-section success lives entirely in `sections`.
  // A wholly-failed run is still {status:"ok", sections:{all failed}} (never event:error).
  writeEvent(res, { event: "done", data: { status: "ok", sections: verdicts } });
  res.end();
}

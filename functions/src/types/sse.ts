/**
 * Typed SSE envelope — backend-functions.md:53-57.
 * The backend parses raw vendor chunks and re-emits ONLY completed objects as
 * these typed events; the client renders without parsing raw JSON.
 */
import { ErrorBody } from "./protocol";

/** `event: meta` → dialogue start */
export interface MetaEvent {
  event: "meta";
  data: { sessionId: string; remaining: number };
}

/** payload object kinds carried by `event: object` */
export type SseObjectType =
  | "dialogueMeta"
  | "turn"
  | "feedbackSection"
  | "feedbackDeepSection"
  | "summaryCard";

/** summary card variants — backend-functions.md:55 */
export type SummaryCardKind = "expression" | "word" | "coaching";

/**
 * slim feedback section discriminator — M1-07. Mirrors `SummaryCardKind`: the outer
 * `event:object type` is fixed to "feedbackSection", so an inner `data.section` names
 * WHICH of the three fixed-order slim sections this frame carries (feedback-slim.md
 * propertyOrdering: writingScore → grammar → naturalExpression).
 */
export type FeedbackSection = "writingScore" | "grammar" | "naturalExpression";

/**
 * deep feedback section discriminator — M2-03. Deep analysis is a SEPARATE on-demand call
 * ("더 보기") with its OWN outer `event:object type` fixed to "feedbackDeepSection" — kept
 * distinct from slim's "feedbackSection" so the two contracts don't share a section union
 * (widening `FeedbackSection` with deep-only names would pollute the slim type). Fixed emit
 * order: conceptualBridge → toneStyle → paraphrasing (feedback-deep.md propertyOrdering).
 */
export type DeepFeedbackSection =
  | "conceptualBridge"
  | "toneStyle"
  | "paraphrasing";

/**
 * Per-object payload, discriminated by `type`. `turn` / dialogue meta bodies are
 * filled in by the M1 handlers; `feedbackSection` (M1-07), `feedbackDeepSection` (M2-03),
 * and `summaryCard` carry an inner section/kind discriminator the SoT fixes
 * (backend-functions.md:53-55).
 */
export type SseObject =
  | { type: "dialogueMeta"; data: unknown }
  | { type: "turn"; data: unknown }
  | {
      type: "feedbackSection";
      data: { section: FeedbackSection } & Record<string, unknown>;
    }
  | {
      type: "feedbackDeepSection";
      data: { section: DeepFeedbackSection } & Record<string, unknown>;
    }
  | { type: "summaryCard"; data: { kind: SummaryCardKind } & Record<string, unknown> };

/** `event: object` → a completed, typed payload object */
export interface ObjectEvent {
  event: "object";
  data: SseObject;
}

/**
 * `event: done` → terminal. `sections` used by summary
 * (`{expressions|words|coaching: ok|failed}`) — backend-functions.md:56.
 */
export interface DoneEvent {
  event: "done";
  data: { status: string; sections?: Record<string, "ok" | "failed"> };
}

/** `event: error` → terminal typed error */
export interface ErrorEvent {
  event: "error";
  data: ErrorBody;
}

export type SseEnvelope = MetaEvent | ObjectEvent | DoneEvent | ErrorEvent;

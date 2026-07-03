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
 * Per-object payload, discriminated by `type`. `turn` / dialogue meta bodies are
 * filled in by the M1 handlers; `feedbackSection` (M1-07) and `summaryCard` carry an
 * inner section/kind discriminator the SoT fixes (backend-functions.md:53-55).
 */
export type SseObject =
  | { type: "dialogueMeta"; data: unknown }
  | { type: "turn"; data: unknown }
  | {
      type: "feedbackSection";
      data: { section: FeedbackSection } & Record<string, unknown>;
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

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
 * Per-object payload, discriminated by `type`. The concrete payload shapes
 * (`turn`, `feedbackSection`, dialogue meta, summary card body) are filled in by
 * the M1 handlers — typed `unknown` until then, except `summaryCard.kind` which
 * the SoT fixes. This is the seam M1 tightens (backend-functions.md:53-55).
 */
export type SseObject =
  | { type: "dialogueMeta"; data: unknown }
  | { type: "turn"; data: unknown }
  | { type: "feedbackSection"; data: unknown }
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

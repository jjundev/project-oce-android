/**
 * Cheap request-shape guards shared by every `/llm` task. They run BEFORE any Firestore transaction
 * or Gemini call, so a malformed id or an oversized body is a 400 — never a 500 (an id like `a/b`
 * used as a Firestore doc id throws) and never a large bill (a multi-MB summary prompt).
 */
import { Task } from "../types/protocol";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** sessionId(서버 randomUUID)·idempotencyKey(클라 UUID.randomUUID)는 모두 UUID 문자열이다. */
export function isUuid(value: string): boolean {
  return UUID_RE.test(value);
}

/** speaking 은 최대 20초 16kHz·16bit mono WAV(base64 ≈ 853KB)를 싣는다 — 여유 포함 상한. */
export const MAX_SPEAKING_PAYLOAD_CHARS = 1_500_000;

/** 텍스트 태스크 상한 — 20턴 요약 payload 도 ≈ 20KB 라 정상 사용은 닿지 않는다. */
export const MAX_TEXT_PAYLOAD_CHARS = 65_536;

/** true when the serialized payload fits the task's budget (absent payload = empty). */
export function payloadWithinLimit(task: Task, payload: unknown): boolean {
  const size = JSON.stringify(payload ?? {}).length;
  const max = task === "speaking" ? MAX_SPEAKING_PAYLOAD_CHARS : MAX_TEXT_PAYLOAD_CHARS;
  return size <= max;
}

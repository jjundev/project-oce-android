/**
 * Shared protocol types for the /llm proxy — used by BOTH transports
 * (SSE for dialogue/feedback/summary, single-shot JSON for speaking/tts).
 *
 * SoT: docs/design/backend-functions.md §4 (proxy contract). The SoT defines the
 * envelope shape `event:error → {code}` but does NOT enumerate concrete code
 * values — the members below marked "(plan-introduced)" are this scaffold's own
 * additions, not ratified in backend-functions.md.
 */

/** task discriminant — backend-functions.md:46 */
export type Task =
  | "dialogue"
  | "speaking"
  | "feedback"
  | "feedbackDeep"
  | "summary"
  | "tts";

/** per-task response transport — backend-functions.md:50-52 */
export type ResponseMode = "sse" | "json";

/**
 * `task=dialogue` request payload — M1-02. Generation inputs; `sessionId` is NOT sent by the
 * client (the server mints it in the start-gate transaction and returns it via `event:meta`,
 * backend-functions.md §7). `firstSession:true` forces `easy`/`length=5` server-side regardless
 * of the supplied values (dialogue-generate.md:39). `length` (turn count, 5|10) also becomes
 * `sessions/{id}.turnCount` — the per-session call-cap divisor (session-cap.ts:75).
 */
export interface DialoguePayload {
  level: "starter" | "easy" | "normal" | "hard" | "expert";
  topic: string;
  length: number;
  firstSession: boolean;
}

/**
 * /llm request body — backend-functions.md:45-48.
 * The scaffold validates only `task`; per-task refinement (dialogue requires
 * `idempotencyKey`; feedback/speaking/summary require `sessionId`;
 * speaking carries `payload.audioBase64`) lands with the gate/parser in M1-02.
 * This type is the seam M1 extends — it is intentionally loose for now.
 */
export interface RequestBody {
  task: Task;
  sessionId?: string;
  idempotencyKey?: string;
  payload?: unknown;
}

/**
 * `task=tts` request payload — M1-05. The client sends only text + the opponent's
 * gender + a speaking rate; voice / provider / locale are code-fixed server-side and
 * never exposed to the client (tts.md §1, §2). `gender` drives the server voice
 * mapping (male→Puck / else→Kore, tts.md:7); `speechRate` is a best-effort prompt
 * hint clamped to 0.5–1.5 (tts.md:12).
 */
export interface TtsRequestPayload {
  text: string;
  gender?: "male" | "female";
  speechRate?: number;
}

/**
 * `task=tts` single-shot JSON response — M1-05 (backend-functions.md:52, tts.md:15).
 * `pcmBase64` matches the field name ratified in audio-pipeline.md:98,130; the client
 * MUST honor `sampleRate` (do not assume 24kHz) when initializing AudioTrack.
 */
export interface TtsResponse {
  pcmBase64: string;
  sampleRate: number;
  mimeType: string;
}

/**
 * `task=speaking` request payload — M1-06. Carries ONLY the audio; `sessionId` is a
 * top-level envelope field (RequestBody.sessionId), NOT nested here, per the ratified
 * `/llm` contract (backend-functions.md:45,47-48). `audioBase64` is 16kHz·16bit·mono
 * WAV produced client-side (WavEncoder.wrap); the server forwards it to Gemini as an
 * inline `audio/wav` part.
 */
export interface SpeakingRequestPayload {
  audioBase64: string;
}

/**
 * `task=speaking` single-shot JSON response — M1-06 (speaking-analyze.md:13). Output is
 * deliberately narrowed to a faithful transcript + one qualitative Korean encouragement
 * line. NO numeric score / rating field exists here by design (PRD A8, R3) — the "no
 * score exposure" guarantee is structural, not a runtime filter.
 */
export interface SpeakingResponse {
  transcript: string;
  feedbackMessage: string;
}

/** Error codes surfaced to the client (SSE `event:error` body or JSON error body). */
export enum ErrorCode {
  /** missing/invalid Firebase ID token — backend-functions.md:47 */
  UNAUTHENTICATED = "UNAUTHENTICATED",
  /** malformed request body / unknown task (plan-introduced) */
  UNKNOWN_TASK = "UNKNOWN_TASK",
  /** malformed task payload (e.g. tts with empty text) — M1-05 */
  INVALID_PAYLOAD = "INVALID_PAYLOAD",
  /** handler stub — real behavior lands in M1+ (plan-introduced) */
  NOT_IMPLEMENTED = "NOT_IMPLEMENTED",
  /** Gemini TTS synthesis failed after retries — M1-05 (backend-functions.md §12) */
  TTS_SYNTH_FAILED = "TTS_SYNTH_FAILED",
  /**
   * daily free-session limit reached for the KST day — M1-02 (backend-functions.md §7). Emitted
   * pre-stream as HTTP 429 `{code, remaining:0}`; the dialogue client also accepts it as an SSE
   * `event:error` frame (DialogueSseStream.kt:150). Distinct from CAP_EXCEEDED (per-session call
   * cap, a different invariant). (plan-introduced)
   */
  DAILY_LIMIT_EXCEEDED = "DAILY_LIMIT_EXCEEDED",
  /** per-session call cap reached (callCount ≥ turnCount×factor) — M1-06 (backend-functions.md §8) */
  CAP_EXCEEDED = "CAP_EXCEEDED",
  /** sessionId missing/expired/not-owned — M1-06 (backend-functions.md §8) */
  SESSION_INVALID = "SESSION_INVALID",
  /** Gemini speaking analysis failed after retries — M1-06 (backend-functions.md §12) */
  SPEAKING_ANALYZE_FAILED = "SPEAKING_ANALYZE_FAILED",
  /** unexpected server-side failure (plan-introduced) */
  INTERNAL = "INTERNAL",
}

/**
 * Error body — shared by the SSE `error` event and the JSON error path.
 * NOTE: the SSE `error` event is emitted as `{code}` ONLY, to match the SoT
 * envelope (backend-functions.md:57). `message` is an optional server-side
 * affordance (JSON path / logging) and is deliberately NOT sent on the SSE event.
 */
export interface ErrorBody {
  code: ErrorCode;
  message?: string;
}

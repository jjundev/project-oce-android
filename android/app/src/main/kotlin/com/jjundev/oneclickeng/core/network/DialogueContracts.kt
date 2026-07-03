package com.jjundev.oneclickeng.core.network

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * `/llm` request envelope for a `dialogue` task (M1-01). Unlike [SpeakingRequest] this carries
 * NO `sessionId` — the server mints it and returns it via `event:meta` (backend-functions.md §4·§7).
 * `idempotencyKey` is REQUIRED for dialogue and is reused verbatim on retry: the server returns the
 * same `sessionId` without re-charging usage while the key exists, and deletes it on a terminal
 * refund so a reused key becomes a fresh start automatically (backend-functions.md §7) — the client
 * never needs a refund signal.
 */
@Serializable
data class DialogueRequest(
    // ALWAYS: the shared Json has encodeDefaults=false, but the server dispatches on `task`, so it
    // must be on the wire even though it equals its default.
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val task: String = "dialogue",
    val idempotencyKey: String,
    val payload: DialoguePayload,
)

/** dialogue payload — generation inputs. First session forces easy/5 server-side (PRD §8.2.1). */
@Serializable
data class DialoguePayload(
    val level: String,
    val topic: String,
    val length: Int,
    val firstSession: Boolean,
)

/**
 * Post-parse domain model of the typed SSE envelope (backend-functions.md §4). This is NOT a 1:1
 * mirror of the wire frame: the wire `event:object` carries a `{type, data}` discriminator that the
 * parser ([DialogueSseStream]) resolves into [Meta]/[Turn] before the client ever sees it. The
 * client renders completed objects and never parses raw JSON itself (FR-6).
 *
 * Naming note: the wire `event:meta` (session start envelope) maps to [Start]; the completed
 * `object type=dialogueMeta` (opponent metadata) maps to [Meta]. They are distinct.
 */
sealed interface DialogueEvent {
    /** `event:meta` — session start. Carries no renderable content, so it does NOT flip Ready. */
    data class Start(val sessionId: String, val remaining: Int) : DialogueEvent

    /**
     * 일일 무료 세션 한도 도달 거부(M3-04, FR-26/27). SoT `dialogue-learning-flow.md` StartGate 의
     * `Rejected(remaining=0)` 에 대응하는 전송층 이름 — 상태층은 [DialogueGenState.QuotaBlocked] 로 매핑된다.
     * [Start]/[Error] 와 별개인 이유: 거부는 재시도 대상이 아니라 중립 한도 패널로 분기해야 한다(패널 !=
     * 실패 배너). 이 이벤트는 두 채널 어느 쪽에서 와도 [DialogueSseStream] 내부에서 방출된다 —
     * (i) 사전-게이트 HTTP 비200(예: 429), (ii) SSE `event:error` 프레임의 일일-한도 code. `remaining` 은
     * 거부 시 상수 0(정본은 잔여 수를 노출하지 않으므로 표시에 쓰지 않는다).
     */
    data class QuotaExceeded(val remaining: Int) : DialogueEvent

    /** `event:object type=dialogueMeta` — opponent/topic metadata. */
    data class Meta(val meta: DialogueMeta) : DialogueEvent

    /** `event:object type=turn` — one completed dialogue turn. */
    data class Turn(val turn: DialogueTurn) : DialogueEvent

    /**
     * `event:done` — stream complete. `status` is retained for forward-compat with the generic
     * envelope (`{status, sections?}`); the dialogue path has no partial-section semantics so it
     * is unused today (kept optional to absorb the field rather than reject the frame).
     */
    data class Done(val status: String? = null) : DialogueEvent

    /** `event:error` — typed terminal error. `code` is opaque to the client (retry, no branching). */
    data class Error(val code: String) : DialogueEvent
}

/** Completed `dialogueMeta` object. Fields mirror the generation schema (dialogue-generate.md). */
@Serializable
data class DialogueMeta(
    val topic: String,
    val opponentName: String,
    val opponentGender: String,
    val opponentRole: String,
)

/** Completed `turn` object. `role` ∈ {model, user}; index 0 is always `model` (dialogue-generate.md). */
@Serializable
data class DialogueTurn(
    val ko: String,
    val en: String,
    val role: String,
)

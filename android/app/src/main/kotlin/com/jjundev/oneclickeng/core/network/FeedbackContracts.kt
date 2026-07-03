package com.jjundev.oneclickeng.core.network

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * `/llm` request envelope for a `feedback` task (M1-07). Unlike [DialogueRequest] this carries a
 * top-level `sessionId` (minted by the dialogue start `event:meta`, M1-01/M1-02) — feedback reuses
 * the per-session cap gate rather than the daily start gate (backend-functions.md §4·§8). There is
 * NO idempotencyKey: feedback is not deduped, and a failed section retries in place (M1-07 §9.1).
 */
@Serializable
data class FeedbackRequest(
    // ALWAYS: the shared Json has encodeDefaults=false, but the server dispatches on `task`, so it
    // must reach the wire even though it equals its default (same fix as DialogueRequest).
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val task: String = "feedback",
    val sessionId: String,
    val payload: FeedbackPayload,
)

/** feedback payload — the learner's turn inputs (feedback-slim.md:5 INPUT). */
@Serializable
data class FeedbackPayload(
    val koreanPrompt: String,
    val userEnglish: String,
    val referenceEnglish: String,
    val level: String,
)

/**
 * Post-parse domain model of the feedback SSE envelope (backend-functions.md §4). The wire
 * `event:object type=feedbackSection` carries an inner `data.section` discriminator (mirroring
 * `summaryCard.data.kind`); [FeedbackSseStream] resolves it into one of the three [Section] variants
 * before the client ever sees it. The client renders completed sections and never parses raw JSON
 * (FR-6). Unlike [DialogueEvent] there is no `Start`/`Meta` — feedback emits no `event:meta`.
 */
sealed interface FeedbackEvent {
    /** `event:object type=feedbackSection` — one completed slim section, in fixed emit order. */
    sealed interface Section : FeedbackEvent {
        /** ① writingScore — 점수 + 격려. */
        data class WritingScore(val value: WritingScoreDto) : Section

        /** ② grammar — 교정 세그먼트 + 설명. */
        data class Grammar(val value: GrammarDto) : Section

        /** ③ naturalExpression — 자연 표현 세그먼트 + 이유. */
        data class NaturalExpression(val value: NaturalExpressionDto) : Section
    }

    /** `event:done` — stream complete. `status` retained for forward-compat (see [DialogueEvent.Done]). */
    data class Done(val status: String? = null) : FeedbackEvent

    /** `event:error` / mid-stream failure — typed terminal error. `code` is opaque (retry, no branching). */
    data class Error(val code: String) : FeedbackEvent

    /**
     * 세션 호출 캡 도달 거부(backend-functions.md §8, CAP_EXCEEDED). [DialogueEvent.QuotaExceeded] 대칭 —
     * 캡 거부는 스트림이 열리기 전 사전-게이트 HTTP 429 로 오며, 재시도 대상이 아니라 중립 상태로 분기해야
     * 하므로 [Error] 와 별개다(패널 != 실패 배너). feedback 는 일일 게이트가 없으므로 그 429 의 유일 원인은
     * 세션 캡이다. `remaining` 은 거부 시 상수 0(정본은 잔여 수를 노출하지 않는다).
     */
    data class QuotaExceeded(val remaining: Int) : FeedbackEvent
}

/** Completed `writingScore` section (feedback-slim.md). Client derives the color from `score`. */
@Serializable
data class WritingScoreDto(
    val score: Int,
    val encouragementMessage: String,
)

/** Completed `grammar` section — the learner's sentence rebuilt as typed segments + explanation. */
@Serializable
data class GrammarDto(
    val correctedSentence: CorrectedSentenceDto,
    val explanation: String,
)

@Serializable
data class CorrectedSentenceDto(
    val segments: List<FeedbackSegmentDto>,
)

/** Completed `naturalExpression` section — one native version as segments + a single reason. */
@Serializable
data class NaturalExpressionDto(
    val segments: List<FeedbackSegmentDto>,
    val reason: ReasonDto,
)

@Serializable
data class ReasonDto(
    val keyword: String,
    val description: String,
)

/**
 * One feedback text segment (feedback-slim.md schema). `type` ∈ grammar {normal,incorrect,
 * correction,highlight} / naturalExpression {normal,highlight}; the renderer maps it to a
 * `RichSegment` (C15). An unknown `type` degrades to plain text (defensive).
 */
@Serializable
data class FeedbackSegmentDto(
    val text: String,
    val type: String,
)

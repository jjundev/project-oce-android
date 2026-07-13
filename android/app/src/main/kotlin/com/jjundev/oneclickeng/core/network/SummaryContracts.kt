package com.jjundev.oneclickeng.core.network

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * `/llm` request envelope for a `summary` task (M2-02). Carries a top-level `sessionId` (minted by the
 * dialogue start `event:meta`, M1-01/M1-02) — summary reuses the per-session cap gate like feedback,
 * not the daily start gate (backend-functions.md §8). There is NO idempotencyKey: summary is not
 * deduped; a partially-failed section retries in place by re-issuing the call (§10).
 *
 * The summary SSE bundles three internal Gemini calls (표현 필터·단어 추출·코칭) into a single stream
 * (backend-functions.md §10); the payload feeds all three from the client-buffered turn data.
 */
@Serializable
data class SummaryRequest(
    // ALWAYS: the shared Json has encodeDefaults=false, but the server dispatches on `task`, so it
    // must reach the wire even though it equals its default (same fix as DialogueRequest/FeedbackRequest).
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val task: String = "summary",
    val sessionId: String,
    val payload: SummaryPayload,
)

/**
 * summary payload — the client PROJECTS its whole-session turn buffer into these already-shaped fields
 * before sending (buffer→sub-call input projection is the client's job, prompt-system.md:71). Field
 * names/shape mirror the backend contract `functions/src/types/summary.ts` exactly: the expressions
 * sub-call reads [expressionCandidates], the words sub-call reads [words]/[sentences]/[userOriginalSentences],
 * coaching reads [turns]. Omitting an empty array is fine — the backend defaults absent arrays to [].
 *
 * [sections] — retry filter (backend-functions.md §10): run ONLY these sections and report only them in
 * `done.sections`. `null`(초기 호출) 은 wire 에서 생략돼 "세 섹션 전부"를 뜻한다. 값이 있으면 백엔드 PLURAL
 * 키(`expressions`/`words`/`coaching`)의 비어있지 않은 부분집합이어야 한다(빈/미지 키는 400).
 */
@Serializable
data class SummaryPayload(
    val totalScore: Int,
    val turns: List<SummaryTurnDto> = emptyList(),
    val expressionCandidates: List<SummaryExpressionCandidateDto> = emptyList(),
    val words: List<String> = emptyList(),
    val sentences: List<String> = emptyList(),
    val userOriginalSentences: List<String> = emptyList(),
    val sections: List<String>? = null,
)

/**
 * One before/after candidate feeding the expressions filter (backend `ExpressionCandidate`). [type] 은
 * 클라의 힌트(`natural`|`accurate`)이며 백엔드 프롬프트가 최종 재분류한다. [explanation] 은 선택(백엔드가 채움).
 */
@Serializable
data class SummaryExpressionCandidateDto(
    val type: String,
    val koreanPrompt: String,
    val before: String,
    val after: String,
    val explanation: String? = null,
)

/**
 * One turn feeding coaching (backend `SummaryTurn`): [before]=사용자 원문, [after]=교정/자연스러운 개선문,
 * [score]=slim writingScore. 스킵/실패 턴은 after/score 가 null 로 들어가 백엔드가 낮은 신뢰도로 처리한다.
 */
@Serializable
data class SummaryTurnDto(
    val koreanPrompt: String,
    val before: String,
    val after: String? = null,
    val score: Int? = null,
)

/**
 * Post-parse domain model of the summary SSE envelope (backend-functions.md §4·§10). The wire
 * `event:object type=summaryCard` carries an inner `data.kind ∈ {expression, word, coaching}`
 * discriminator (mirroring feedback's `data.section`); [SummarySseStream] resolves it into one of the
 * three [Card] variants before the client sees it. The client renders completed cards and never parses
 * raw JSON (FR-6). `event:done` carries per-section `ok|failed` so the client distinguishes "empty" vs
 * "failed→retry" (§10).
 */
sealed interface SummaryEvent {
    /** `event:object type=summaryCard` — one completed summary section (one per kind). */
    sealed interface Card : SummaryEvent {
        /** `kind:expression` — the session's kept before/after expression cards (≤8). */
        data class Expression(val items: List<ExpressionItemDto>) : Card

        /** `kind:word` — the session's new-vocabulary cards (≤12). */
        data class Word(val items: List<WordItemDto>) : Card

        /** `kind:coaching` — 잘한 점/개선점 two strings (empty string hides that block). */
        data class Coaching(val value: CoachingDto) : Card
    }

    /**
     * `event:done` — stream complete, carrying each SSE section's terminal `ok|failed` outcome
     * (§10). A section whose card already streamed is `ok`; the coordinator uses `failed` to mark a
     * never-arrived section retryable rather than merely empty.
     */
    data class Done(
        val expressions: SectionOutcome,
        val words: SectionOutcome,
        val coaching: SectionOutcome,
    ) : SummaryEvent

    /** `event:error` / mid-stream failure — typed terminal error. `code` is opaque (retry, no branching). */
    data class Error(val code: String) : SummaryEvent

    /**
     * 세션 호출 캡 도달 거부(backend-functions.md §8, CAP_EXCEEDED). 요약은 완주-후 표면이라 캡 거부 시
     * 로컬 블록만 렌더하고 중립 문구로 분기한다(섹션 개별 Failed 아님) — feedback [FeedbackEvent.QuotaExceeded]
     * 대칭. 캡 거부는 스트림이 열리기 전 사전-게이트 HTTP 429 로 오며, `remaining` 은 상수 0(정본은 잔여
     * 수를 노출하지 않는다).
     */
    data class QuotaExceeded(val remaining: Int) : SummaryEvent
}

/** One SSE section's terminal outcome from the `event:done` frame (§10). */
enum class SectionOutcome {
    Ok,
    Failed,
    ;

    companion object {
        /** Wire `"ok"` → [Ok]; anything else (incl. absent) → [Failed] (defensive). */
        fun fromWire(value: String?): SectionOutcome = if (value == "ok") Ok else Failed
    }
}

/**
 * One kept expression card (summary-expressions.md items[]). `type` ∈ {natural, accurate}; the client
 * maps `before`/`after` to a before→after block and shows `explanation`. Save toggle is display-only
 * here (wiring is M2-04).
 */
@Serializable
data class ExpressionItemDto(
    val type: String,
    val koreanPrompt: String,
    val before: String,
    val after: String,
    val explanation: String,
)

/** One new-vocabulary card (summary-words.md items[]). Optional notes are null when absent. */
@Serializable
data class WordItemDto(
    val en: String,
    val ko: String,
    val partOfSpeech: String,
    val level: String,
    val example: WordExampleDto,
    val collocationNote: String? = null,
    val confusionNote: String? = null,
)

@Serializable
data class WordExampleDto(
    val en: String,
    val ko: String,
)

/** Completed `coaching` card (summary-coaching.md). Empty string hides that block in the UI (Rule 4). */
@Serializable
data class CoachingDto(
    val futureSelfFeedback: FutureSelfFeedbackDto,
)

@Serializable
data class FutureSelfFeedbackDto(
    val positive: String,
    val toImprove: String,
)

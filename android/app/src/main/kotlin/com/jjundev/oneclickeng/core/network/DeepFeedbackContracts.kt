package com.jjundev.oneclickeng.core.network

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * `/llm` request envelope for a `feedbackDeep` task (M2-03) — the on-demand "더 보기" deep analysis.
 * Mirrors [FeedbackRequest]: a top-level `sessionId` (session cap gate, backend-functions.md §8) and
 * the SAME [FeedbackPayload] inputs as slim (feedback-deep.md:6 — deep runs on the same turn inputs).
 * No idempotencyKey: deep is not deduped, and a failed call retries the whole request (M2-03 §9.2).
 */
@Serializable
data class FeedbackDeepRequest(
    // ALWAYS: the shared Json has encodeDefaults=false, but the server dispatches on `task`, so it
    // must reach the wire even though it equals its default (same fix as FeedbackRequest).
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val task: String = "feedbackDeep",
    val sessionId: String,
    val payload: FeedbackPayload,
)

/**
 * Post-parse domain model of the deep-feedback SSE envelope (backend-functions.md §4). The wire
 * `event:object type=feedbackDeepSection` carries an inner `data.section` discriminator (kept DISJOINT
 * from slim's `feedbackSection`, so the two contracts never share a section union); [DeepFeedbackSseStream]
 * resolves it into one of the three [Section] variants before the client sees it (FR-6). There is no
 * `Meta`; the deep call emits no `event:meta`. `Canceled` is NOT a wire event — it is a coordinator
 * transition (next-turn supersede), so it lives in the state axis, not here.
 */
sealed interface FeedbackDeepEvent {
    /** `event:object type=feedbackDeepSection` — one completed deep section, in fixed emit order. */
    sealed interface Section : FeedbackDeepEvent {
        /** ④ conceptualBridge — 직역 + 설명 + 벤다이어그램(색은 클라 대비 가드가 산출). */
        data class ConceptualBridge(val value: ConceptualBridgeDto) : Section

        /** ⑤ toneStyle — 5단계 톤 + 각 레벨 EN/KO. */
        data class ToneStyle(val value: ToneStyleDto) : Section

        /** ⑥ paraphrasing — 3카드(Beginner/Intermediate/Advanced). */
        data class Paraphrasing(val value: ParaphrasingDto) : Section
    }

    /** `event:done` — stream complete. `status` retained for forward-compat (see [FeedbackEvent.Done]). */
    data class Done(val status: String? = null) : FeedbackDeepEvent

    /** `event:error` / mid-stream failure — typed terminal error. `code` is opaque (retry, no branching). */
    data class Error(val code: String) : FeedbackDeepEvent

    /**
     * 세션 호출 캡 도달 거부(backend-functions.md §8, CAP_EXCEEDED). Deep 는 슬림 `feedback`/`speaking` 과
     * per-session 캡을 공유하므로(shared counter) 사전-게이트 HTTP 429 로 온다. [FeedbackEvent.QuotaExceeded]
     * 대칭 — 재시도 대상이 아니라 중립 상태로 분기해야 하므로 [Error] 와 별개다. `remaining` 은 거부 시 상수 0.
     */
    data class QuotaExceeded(val remaining: Int) : FeedbackDeepEvent
}

/**
 * Completed `conceptualBridge` section (feedback-deep.md). The Venn carries words/items ONLY — colors
 * are computed client-side by the contrast guard (feedback-deep.md:8), so there is no color field.
 */
@Serializable
data class ConceptualBridgeDto(
    val literalTranslation: String,
    val explanation: String,
    val venn: VennDto,
)

@Serializable
data class VennDto(
    val guide: String,
    val leftCircle: VennCircleDto,
    val rightCircle: VennCircleDto,
    val intersection: VennIntersectionDto,
)

@Serializable
data class VennCircleDto(
    val word: String,
    val items: List<String>,
)

@Serializable
data class VennIntersectionDto(
    val items: List<String>,
)

/** Completed `toneStyle` section — exactly 5 levels (0 Very Formal → 4 Very Casual), defaultLevel=2. */
@Serializable
data class ToneStyleDto(
    val defaultLevel: Int,
    val levels: List<ToneLevelDto>,
)

@Serializable
data class ToneLevelDto(
    val level: Int,
    val sentence: String,
    val sentenceTranslation: String,
)

/**
 * Completed `paraphrasing` section. On the wire this section is an ARRAY, so the backend nests it
 * under `items` in the `feedbackDeepSection` frame (`{ section, items:[…] }`); this DTO decodes that
 * wrapper. Exactly 3 items (Beginner / Intermediate / Advanced).
 */
@Serializable
data class ParaphrasingDto(
    val items: List<ParaphraseItemDto>,
)

@Serializable
data class ParaphraseItemDto(
    val level: Int,
    val label: String,
    val sentence: String,
    val sentenceTranslation: String,
)

package com.jjundev.oneclickeng.feature.session.feedback

import com.jjundev.oneclickeng.core.network.GrammarDto
import com.jjundev.oneclickeng.core.network.NaturalExpressionDto
import com.jjundev.oneclickeng.core.network.WritingScoreDto
import com.jjundev.oneclickeng.ui.component.RichSegment

/**
 * 턴 슬림 피드백(M1-07)의 UI 상태축. 단일 바텀시트에 slim 3섹션(writingScore·grammar·naturalExpression)을
 * 고정 순서로 점진 렌더한다. 정본: turn-feedback-ia.md §2·§3, dialogue-learning-flow.md §7(FeedbackSlimRequest).
 *
 * 요청단위 축 `FeedbackSlimRequest`(Idle/Loading/Ready/ErrorRecoverable/ErrorBlocked)를 **섹션단위로 투영**한다:
 * 섹션별 [SectionState] 가 Loading/Ready/Failed(ErrorRecoverable|ErrorBlocked)/Skipped 를 담고, 요청단위
 * 집계는 [Active.nextEnabled] 하나뿐이다(별도 단일 요청 상태값을 산출하지 않는다). 캡 거부만 예외로 요청
 * 레벨 [QuotaBlocked] 로 분기한다(§2b — 섹션 3개를 개별 Failed 로 만들지 않는다).
 */
sealed interface SlimFeedbackState {
    /** 시트 열기 전(초기/리셋). */
    data object Idle : SlimFeedbackState

    /**
     * 정상 피드백 시트. [header] 는 즉시 데이터(스켈레톤 없음, §2.1). 3섹션은 각자 점진 렌더된다.
     * [nextEnabled] = 세 섹션이 모두 settled(Ready 또는 Skipped)일 때만 "다음" 활성 — 점수 gate 없음(§7).
     */
    data class Active(
        val header: RecapHeader,
        val writingScore: SectionState<WritingScore>,
        val grammar: SectionState<Grammar>,
        val natural: SectionState<NaturalExpression>,
    ) : SlimFeedbackState {
        val nextEnabled: Boolean
            get() = writingScore.settled && grammar.settled && natural.settled
    }

    /**
     * 세션 호출 캡 도달로 서버가 거부한 요청-레벨 상태(§2b, backend-functions.md §8). [Active] 와 분리한
     * 이유: 캡 거부는 생성 전 사전-게이트 이벤트라 "3섹션 중 하나"에서 날 수 없고, 재시도 어포던스가 아니라
     * 중립 문구로 분기해야 한다(dialogue QuotaBlocked 대칭). 헤더는 유지하고 "다음"은 여전히 가능하다
     * (피드백 실패는 턴 진행을 막지 않는다, §9.2). [remaining] 은 거부 시 상수 0이며 표시엔 쓰지 않는다.
     */
    data class QuotaBlocked(
        val header: RecapHeader,
        val remaining: Int,
    ) : SlimFeedbackState
}

/** ⓪ 과제 리캡 헤더 — koreanPrompt(과제) + userText(내 답변 원문 echo). 즉시 렌더(§2.1). */
data class RecapHeader(
    val koreanPrompt: String,
    val userText: String,
)

/**
 * 슬림 섹션 하나의 상태. [Failed.canRetry] 는 누적 [attempts] < [MAX_ATTEMPTS] 일 때만 참 — 소비처(시트)가
 * canRetry 에 따라 `재시도`(Recoverable) vs `건너뛰고 다음으로`(Blocked)를 고른다(FB8, 04-screen-04:63).
 */
sealed interface SectionState<out T> {
    /** 도착 전 — 시머 스켈레톤(C6). */
    data object Loading : SectionState<Nothing>

    /** 도착 완료 — 실데이터 렌더. */
    data class Ready<T>(val value: T) : SectionState<T>

    /** 실패 — 인라인 에러. [attempts] 누적 2회 도달 시 canRetry=false(수동 스킵만). */
    data class Failed(val attempts: Int) : SectionState<Nothing> {
        val canRetry: Boolean get() = attempts < MAX_ATTEMPTS
    }

    /** 사용자가 건너뜀 — 해당 buffer 키를 null 로 둔다(§9.1). */
    data object Skipped : SectionState<Nothing>

    companion object {
        /** 동일 섹션 누적 재시도 임계(최초 + 재시도 1회) — 초과 시 스킵만 가능(FB8). */
        const val MAX_ATTEMPTS = 2
    }
}

/** settled = 진행을 막지 않는 종결 상태(Ready 또는 Skipped). "다음" 게이팅 집계에 쓰인다(§7). */
val SectionState<*>.settled: Boolean
    get() = this is SectionState.Ready<*> || this is SectionState.Skipped

/**
 * Ready 값을 안전하게 꺼낸다(아니면 null). sealed 계층이 타입 파라미터를 보존하므로 `is Ready` smart-cast 로
 * `value` 가 `T` 로 좁혀진다(Result<T>.getOrNull 관용구) — 소거된 제네릭 `as`/`is<T>` 를 피한다.
 */
fun <T> SectionState<T>.readyValueOrNull(): T? =
    when (this) {
        is SectionState.Ready -> value
        else -> null
    }

/** ① 작문 점수 — 점수(파생색은 클라 산출) + 격려문 1장. 음성 점수 없음(§3.1). */
data class WritingScore(
    val score: Int,
    val encouragement: String,
)

/**
 * ② 문법 교정 — 세그먼트(C15) + 설명. "정답"(§3.2, 교정 세그먼트 없음)은 **렌더 분기 없이** 표현된다:
 * all-`normal` 세그먼트는 [OneClickRichText] 가 취소선·코랄 없이 원문 그대로 그리고, 정답 축하는 서버가
 * `explanation` 에 담아 보낸다(feedback-slim Rule 2). §6 의미색 소유(grammar=코랄)상 별도 긍정 색/라벨을
 * 두지 않는다 — 그래서 클라에 `isCorrect` 판정 프로퍼티가 필요 없다(naturalExpression 의 긍정 분기와 비대칭).
 */
data class Grammar(
    val segments: List<RichSegment>,
    val explanation: String,
)

/**
 * ③ 자연 표현 — 세그먼트 + 이유. [isAlreadyNatural] = highlight 세그먼트가 하나도 없으면 "이미 자연스러움"
 * (§3.3, feedback-slim Rule 3) — 대체 diff 대신 긍정 상태로 렌더하고 [reason] 을 숨긴다. `reason` 은 계약상
 * 항상 도착하므로(null 아님) 숨김 트리거는 세그먼트 부재이지 reason 부재가 아니다.
 */
data class NaturalExpression(
    val segments: List<RichSegment>,
    val reason: Reason,
) {
    val isAlreadyNatural: Boolean
        get() = segments.none { it is RichSegment.Highlight }
}

/** 자연 표현이 더 native 한 이유 1개(keyword + description). */
data class Reason(
    val keyword: String,
    val description: String,
)

/**
 * 요약용 turn buffer 스냅샷(dialogue-learning-flow.md §8). 실패/스킵 섹션은 해당 키가 null 이다(§9.1).
 * [correctedText] 는 grammar 세그먼트를 평탄화한 평문(§17): `incorrect`(취소선=제거 대상)를 제외하고
 * normal·correction·highlight 텍스트를 원순서로 연결한 최종 문장.
 */
data class TurnFeedbackBuffer(
    val slimScore: Int?,
    val correctedText: String?,
    val naturalExpression: String?,
)

/** DTO→도메인 매핑: writingScore. */
fun WritingScoreDto.toDomain(): WritingScore = WritingScore(score = score, encouragement = encouragementMessage)

/** DTO→도메인 매핑: grammar 세그먼트를 [RichSegment] 로 직결(§10). */
fun GrammarDto.toDomain(): Grammar =
    Grammar(
        segments = correctedSentence.segments.map { it.toRichSegment() },
        explanation = explanation,
    )

/** DTO→도메인 매핑: naturalExpression. */
fun NaturalExpressionDto.toDomain(): NaturalExpression =
    NaturalExpression(
        segments = segments.map { it.toRichSegment() },
        reason = Reason(keyword = reason.keyword, description = reason.description),
    )

/** feedback-slim 세그먼트 타입 → C15 [RichSegment]. 미인식 타입은 평문으로 강등(방어적). */
private fun com.jjundev.oneclickeng.core.network.FeedbackSegmentDto.toRichSegment(): RichSegment =
    when (type) {
        "incorrect" -> RichSegment.Incorrect(text)
        "correction" -> RichSegment.Correction(text)
        "highlight" -> RichSegment.Highlight(text)
        else -> RichSegment.Normal(text)
    }

/**
 * grammar 세그먼트 → 요약용 평문(§17): `incorrect` 제외, normal·correction·highlight 원순서 연결.
 */
fun List<RichSegment>.flattenCorrectedText(): String {
    return filterNot { it is RichSegment.Incorrect }.joinToString(separator = "") { it.text }
}

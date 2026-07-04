package com.jjundev.oneclickeng.feature.session.summary

import com.jjundev.oneclickeng.core.network.CoachingDto
import com.jjundev.oneclickeng.core.network.ExpressionItemDto
import com.jjundev.oneclickeng.core.network.WordItemDto

/**
 * 세션 요약 화면(M2-02)의 UI 상태축. 정본: 04-screen-05-summary.md · gamification-emphasis.md §4 ·
 * dialogue-learning-flow.md §9.
 *
 * 화면은 **두 출처를 합성**한다:
 * 1. 로컬 즉시 데이터([totalScore]·[highlight]·[bookmarks]·[accrual]) — turn buffer/로컬 소스에서 즉시
 *    산출되며 스켈레톤을 두지 않는다(§9 "즉시/로컬 데이터라 스켈레톤을 두지 않는다").
 * 2. 요약 SSE 번들([bundle]) — 표현/단어/코칭 3섹션. slim 식 섹션별 점진 스켈레톤이 아니라 **초기 단일
 *    번들 스켈레톤** 하나를 두고, `event:done` 수신 후 섹션별로 렌더/재시도한다(§9). 이 화면의 net-new
 *    설계 결정으로, SM2("각 시머 스켈레톤")와 dialogue-learning-flow §9 사이의 SoT 상충은 후자(캐노니컬)를
 *    따른다 — SoT 재결정 필요 항목([docs/ui/04-screen-05-summary.md] SM2 rev2 정합화).
 */
data class SummaryState(
    /** 종합 점수 = slim writingScore 평균(null 턴 제외). 없으면 null(전 턴 스킵) — 56sp 헤로. */
    val totalScore: Int?,
    /** 하이라이트 base = slim 점수 최고 턴(≤1). coaching 편승 enrich 는 M2-01 스키마 확정 후(#6). */
    val highlight: HighlightTurn?,
    /** 북마크 문장(SENTENCE) 최신순 ≤8, 표시 전용. M2-04 착지 전엔 빈 리스트(BookmarkSource seam). */
    val bookmarks: List<BookmarkCard>,
    /** 적립 스트립(streak/학습시간/XP) — 주입 seam 정적 값. 실제 배선 M3-05, 카운트업 M3-06. */
    val accrual: AccrualStrip,
    /** 요약 SSE 번들 영역 상태. */
    val bundle: SectionBundle,
)

/**
 * 요약 SSE 3섹션의 렌더 상태.
 *
 * [BundleLoading] 은 **초기 단일 스켈레톤**(첫 `done` 이전) — 3섹션 공용 하나. `done` 수신 시 [Sectioned]
 * 로 전이해 섹션별 [SummarySectionState] 로 갈라진다(성공=Ready / 실패=Failed 인라인 재시도, §10).
 * [QuotaBlocked] 는 세션 캡 거부의 top-level 배타 상태(#16) — 섹션 재시도 어포던스 없이 중립 문구만.
 * 로컬 블록([SummaryState] 상단 4필드)은 어느 상태에서도 항상 렌더된다.
 */
sealed interface SectionBundle {
    /** 초기 대기 — SSE 영역 전체에 번들 단위 단일 스켈레톤 하나(C6). */
    data object BundleLoading : SectionBundle

    /** `done` 이후 — 섹션별 렌더/재시도. */
    data class Sectioned(
        val expression: SummarySectionState<List<ExpressionCard>>,
        val word: SummarySectionState<List<WordCard>>,
        val coaching: SummarySectionState<Coaching>,
    ) : SectionBundle

    /** 세션 캡 거부 — 로컬 블록 유지 + 중립 문구, 재시도 없음(§8·#16). */
    data object QuotaBlocked : SectionBundle
}

/**
 * 요약 SSE 한 섹션의 상태. feedback 의 `SectionState` 를 계승하되 **Skipped 변형을 두지 않는다**(#17):
 * 요약은 terminal·non-blocking 화면이라 진행을 풀 forward gate 가 없어 "건너뛰고 다음으로"가 무의미하다 —
 * 반복 실패는 [Failed] 로 종결하고 [Failed.canRetry] 가 false 가 되면 재시도 버튼만 비활성한다.
 */
sealed interface SummarySectionState<out T> {
    /** (Sectioned 진입 후) 재시도 재요청 대기 중 — 인라인 스피너/스켈레톤. */
    data object Loading : SummarySectionState<Nothing>

    /** 도착 완료 — 실데이터 렌더(빈 리스트도 Ready: "비어있음" vs "실패" 구분, §10). */
    data class Ready<T>(val value: T) : SummarySectionState<T>

    /** 실패 — 인라인 에러. [attempts] 누적 [MAX_ATTEMPTS] 도달 시 canRetry=false. */
    data class Failed(val attempts: Int) : SummarySectionState<Nothing> {
        val canRetry: Boolean get() = attempts < MAX_ATTEMPTS
    }

    companion object {
        /** 동일 섹션 누적 재시도 임계(최초 + 재시도 1회) — 초과 시 재시도 비활성(#17, FB8 대칭). */
        const val MAX_ATTEMPTS = 2
    }
}

/** Ready 값을 안전하게 꺼낸다(아니면 null). sealed 계층이 타입 파라미터를 보존해 smart-cast 로 좁힌다. */
fun <T> SummarySectionState<T>.readyValueOrNull(): T? =
    when (this) {
        is SummarySectionState.Ready -> value
        else -> null
    }

// ---- 도메인 모델 ----

/** 하이라이트(가장 잘한 순간) base — slim 최고점 턴 1개. */
data class HighlightTurn(
    val koreanPrompt: String,
    val userText: String,
    val score: Int,
)

/** 표현 개선 카드(≤8). [type] 로 "자연스러운/정확한 표현" 라벨을 고른다. 저장 토글 표시 전용(M2-04). */
data class ExpressionCard(
    val type: ExpressionType,
    val koreanPrompt: String,
    val before: String,
    val after: String,
    val explanation: String,
)

/** 표현 개선 분류(summary-expressions.md). 미인식 값은 [Natural] 로 강등(방어적). */
enum class ExpressionType {
    /** 자연스러운 표현 — 맞았지만 더 native. */
    Natural,

    /** 정확한 표현 — 오류를 교정. */
    Accurate,
}

/** 신규 단어 카드(≤12). 선택 노트는 없으면 null. 저장 토글 표시 전용(M2-04). */
data class WordCard(
    val en: String,
    val ko: String,
    val partOfSpeech: String,
    val level: String,
    val exampleEn: String,
    val exampleKo: String,
    val collocationNote: String? = null,
    val confusionNote: String? = null,
)

/** 코칭(잘한 점/개선점). 빈 문자열 블록은 UI 에서 숨긴다(summary-coaching Rule 4). */
data class Coaching(
    val positive: String,
    val toImprove: String,
) {
    val hasPositive: Boolean get() = positive.isNotBlank()
    val hasToImprove: Boolean get() = toImprove.isNotBlank()
}

/** 북마크 문장(SENTENCE) — deep 패러프레이즈 소스(saved-cards.md §3.3). 표시 전용. */
data class BookmarkCard(
    val english: String,
    val korean: String,
)

/**
 * 적립 스트립 정적 값(주입 seam). 순서 = streak → 학습시간 → XP(gamification-emphasis.md §4.3). 이 화면은
 * 값을 정적으로 렌더만 한다 — 실제 데이터 소스 배선은 M3-05, 슬롯머신 카운트업(및 same-day 2번째 세션의
 * streak 정적 유지 규칙, SM1)은 카운트업 컴포넌트가 착지하는 M3-06 에서 함께 도입한다(현 [OneClickStreakChip]
 * 은 애니메이션 변형이 없어 정적/동적 구분이 아직 무의미).
 */
data class AccrualStrip(
    val streakDays: Int,
    val studyTimeLabel: String,
    val xp: Int,
)

// ---- DTO→도메인 매핑 ----

/** DTO→도메인: 표현 개선 카드. */
fun ExpressionItemDto.toDomain(): ExpressionCard =
    ExpressionCard(
        type = if (type == "accurate") ExpressionType.Accurate else ExpressionType.Natural,
        koreanPrompt = koreanPrompt,
        before = before,
        after = after,
        explanation = explanation,
    )

/** DTO→도메인: 신규 단어 카드. */
fun WordItemDto.toDomain(): WordCard =
    WordCard(
        en = en,
        ko = ko,
        partOfSpeech = partOfSpeech,
        level = level,
        exampleEn = example.en,
        exampleKo = example.ko,
        collocationNote = collocationNote,
        confusionNote = confusionNote,
    )

/** DTO→도메인: 코칭. */
fun CoachingDto.toDomain(): Coaching =
    Coaching(
        positive = futureSelfFeedback.positive,
        toImprove = futureSelfFeedback.toImprove,
    )

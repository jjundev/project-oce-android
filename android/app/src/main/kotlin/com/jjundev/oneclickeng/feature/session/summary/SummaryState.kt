package com.jjundev.oneclickeng.feature.session.summary

import com.jjundev.oneclickeng.core.network.CoachingDto
import com.jjundev.oneclickeng.core.network.ExpressionItemDto
import com.jjundev.oneclickeng.core.network.WordItemDto
import com.jjundev.oneclickeng.feature.session.saved.SavedCard

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
    /** 북마크 문장(SENTENCE) 최신순 ≤8. BookmarkSource 가 읽고 SummaryCoordinator 가 저장 해제를 처리한다. */
    val bookmarks: List<BookmarkCard>,
    /** 적립 스트립(streak/학습시간/XP) — 실데이터 배선 M3-05, 슬롯머신 카운트업 M3-06([AccrualStrip.animate]). */
    val accrual: AccrualStrip,
    /** 요약 SSE 번들 영역 상태. */
    val bundle: SectionBundle,
    /**
     * 저장(북마크)된 단어/표현 카드의 sourceIndex 집합(M2-04, 낙관적 UI 축). deep 북마크([bookmarkedLevels])의
     * 요약 대칭 — 토글 즉시 채워짐 표시하고 [SavedCardRepository] 로 영속화한다. 인덱스=표시 인덱스이며 Ready
     * 섹션에서만 저장 어포던스가 열리고 retry 는 Failed 섹션만 대상이라 저장된 섹션은 재생성되지 않는다.
     */
    val savedWordIndices: Set<Int> = emptySet(),
    val savedExprIndices: Set<Int> = emptySet(),
    /**
     * 온보딩 첫 세션 여부(M3-02). true 면 격려 카피를 더 따뜻한 변형으로 고른다("보장된 승리"의 격려 강조,
     * 01-onboarding §8). 일반 세션은 false. 점수 계산·SSE 로직에는 영향이 없다 — 카피 톤만 바꾼다.
     */
    val isFirstSession: Boolean = false,
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

/**
 * 하이라이트(가장 잘한 순간) base — slim 최고점 턴 1개.
 *
 * [rationale] = "왜 잘했는지" 한 줄 설명(프로토타입 realization-SoT: 카드 하단 설명줄). 요약 백엔드/스키마
 * (M2-01)가 아직 미배선이라 nullable — 값이 있으면 표시, 없으면 [koreanPrompt]로 폴백한다(#6 coaching 편승).
 */
data class HighlightTurn(
    val koreanPrompt: String,
    val userText: String,
    val score: Int,
    val rationale: String? = null,
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

/** 북마크 문장(SENTENCE) — deep 패러프레이즈 소스(saved-cards.md §3.3). Firestore 문서 ID를 보존한다. */
data class BookmarkCard(
    val cardId: String,
    val english: String,
    val korean: String,
) {
    /** 기존 정적 미리보기 호출부를 위한 호환 생성자. 실제 저장 카드에는 Firestore ID를 사용한다. */
    constructor(english: String, korean: String) : this(cardId = "", english = english, korean = korean)
}

/**
 * 적립 스트립 값(주입 seam). 순서 = streak → 학습시간 → XP(gamification-emphasis.md §4.3). 실데이터 소스
 * 배선은 M3-05, 슬롯머신 카운트업(I3)은 M3-06 에서 착지한다.
 *
 * **카운트업 게이팅(M3-06, gamification §4.4):** [animate] 가 true 일 때만 세 지표를 굴린다 — 주입 초기/EMPTY
 * 값은 false 라 정적(0→0 죽은 애니메이션 방지). XP 는 0→델타, 학습시간은 오늘 누계
 * [todayStudySecondsBefore]→[todayStudySecondsAfter] 로 롤업하고, streak 는 0→N 이되 same-day 2번째 세션
 * ([streakStatic]=true)은 정적으로 유지한다(§4.4). [todayStudySecondsBefore] 가 null(이관/롤오버로 before 불명)
 * 이거나 before·after 가 같은 분이면 학습시간은 정적 스냅한다.
 */
data class AccrualStrip(
    val streakDays: Int,
    val xp: Int,
    /** 오늘 학습시간 세션 전 누계(초). null=before 불명(이관/롤오버) → 학습시간 정적. */
    val todayStudySecondsBefore: Int? = null,
    /** 오늘 학습시간 세션 후 누계(초) = 롤업 target. */
    val todayStudySecondsAfter: Int = 0,
    /** same-day 2번째 세션 = streak 정적(§4.4). */
    val streakStatic: Boolean = false,
    /** 실데이터 착지 여부. false=주입 초기/EMPTY(정적), true=M3-05 실값(카운트업 대상). */
    val animate: Boolean = false,
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

// ---- 도메인→저장 카드(M2-04) ----

/** WORD 카드 저장 페이로드(firestore-schema §3). 스키마에 없는 partOfSpeech/level/note 는 싣지 않는다. */
fun WordCard.toSavedCard(): SavedCard.Word =
    SavedCard.Word(
        english = en,
        korean = ko,
        exampleEnglish = exampleEn,
        exampleKorean = exampleKo,
    )

/** EXPRESSION 카드 저장 페이로드. [type] 은 스키마 문자열; afterHighlights 는 소스 필드가 없어 생략(#6). */
fun ExpressionCard.toSavedCard(): SavedCard.Expression =
    SavedCard.Expression(
        type = if (type == ExpressionType.Accurate) "accurate" else "natural",
        koreanPrompt = koreanPrompt,
        before = before,
        after = after,
        explanation = explanation,
    )

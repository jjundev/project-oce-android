package com.jjundev.oneclickeng.feature.session.feedback

import com.jjundev.oneclickeng.core.network.ConceptualBridgeDto
import com.jjundev.oneclickeng.core.network.ParaphrasingDto
import com.jjundev.oneclickeng.core.network.ToneStyleDto
import com.jjundev.oneclickeng.core.network.VennDto

/**
 * 온디맨드 깊은 분석("더 보기", M2-03)의 UI 상태축. 슬림([SlimFeedbackState])과 별도 축인
 * `FeedbackDeepSideRequest`(dialogue-learning-flow.md:30, 정본)를 투영한다 — 요청-레벨 축이며 섹션별
 * 재시도가 없다(deep 실패 = 인라인 1영역 + 재시도, turn-feedback-ia.md §9.2).
 *
 * 정본 축은 Idle/Loading/Ready/Canceled/Error 5값이고, [QuotaBlocked] 는 슬림 [SlimFeedbackState.QuotaBlocked]
 * 선례를 따른 축-외 확장이다(캡 거부는 재시도 어포던스가 아니라 중립 상태로 분기해야 하므로 [Error] 와 별개 —
 * backend-functions.md §8). 단일 시트 하단에 conceptualBridge → toneStyle → paraphrasing 을 고정 순서로 펼친다.
 *
 * **점진 렌더 + sticky 실패:** [Loading] 은 세 블록을 nullable 로 담아(도착=non-null, 미도착=null → 스켈레톤)
 * 블록별 점진 렌더를 실현한다. 실패 시 이미 도착한 블록은 [Error] 에 그대로 보존하고(슬림 sticky 일관) 영역에
 * 인라인 재시도만 노출한다 — 도착 블록을 폐기하지 않는다. 세 블록이 모두 도착하면 [Ready] 로 승격한다.
 */
sealed interface DeepFeedbackState {
    /** "더 보기" 미확장(초기/리셋). */
    data object Idle : DeepFeedbackState

    /** 스트리밍 중 — 도착 블록은 실데이터, 미도착(null)은 시머 스켈레톤(turn-feedback-ia.md:111). */
    data class Loading(
        val conceptualBridge: ConceptualBridge? = null,
        val toneStyle: ToneStyle? = null,
        val paraphrasing: Paraphrasing? = null,
    ) : DeepFeedbackState

    /** 세 블록 모두 도착 — 전체 실데이터 렌더(스켈레톤·재시도 없음). */
    data class Ready(
        val conceptualBridge: ConceptualBridge,
        val toneStyle: ToneStyle,
        val paraphrasing: Paraphrasing,
    ) : DeepFeedbackState

    /** 다음 턴으로 넘어가며 진행 중 deep 이 취소됨(늦은 응답 무시, turn-feedback-ia.md:159). */
    data object Canceled : DeepFeedbackState

    /**
     * 실패 — 인라인 영역 에러 + 재시도([SlimFeedbackState] 와 달리 섹션별 아님, 영역 1개). 이미 도착한 블록은
     * 보존하여 렌더하고 미도착만 에러 대상이다(sticky). 재시도는 전체 재호출([DeepFeedbackCoordinator.retry]).
     */
    data class Error(
        val conceptualBridge: ConceptualBridge? = null,
        val toneStyle: ToneStyle? = null,
        val paraphrasing: Paraphrasing? = null,
    ) : DeepFeedbackState

    /**
     * 세션 호출 캡 도달로 서버가 거부한 요청-레벨 상태(§8). deep 영역에만 중립 문구를 노출하고 "다음"·슬림
     * 섹션에는 영향을 주지 않는다(deep 실패는 턴 진행 미차단, §9.2). 재시도 어포던스가 아니다.
     */
    data object QuotaBlocked : DeepFeedbackState
}

/** ④ 개념 브릿지 — 직역 + 간극 설명 + 벤(색은 클라 대비 가드 산출, feedback-deep.md:8). */
data class ConceptualBridge(
    val literalTranslation: String,
    val explanation: String,
    val venn: VennData,
)

/** 벤 데이터(단어/의미만, 색 없음). intersection 은 items 만 갖는다. */
data class VennData(
    val guide: String,
    val left: VennCircle,
    val right: VennCircle,
    val intersectionItems: List<String>,
)

data class VennCircle(
    val word: String,
    val items: List<String>,
)

/** ⑤ 톤 스펙트럼 — 5단계(0 Very Formal → 4 Very Casual), 기본 선택 [defaultLevel](=2). */
data class ToneStyle(
    val defaultLevel: Int,
    val levels: List<ToneLevel>,
)

data class ToneLevel(
    val level: Int,
    val sentence: String,
    val sentenceTranslation: String,
)

/** ⑥ 패러프레이징 — 3카드(Beginner/Intermediate/Advanced). 각 카드에 북마크 토글이 붙는다(§5). */
data class Paraphrasing(
    val items: List<Paraphrase>,
)

data class Paraphrase(
    val level: Int,
    val label: String,
    val sentence: String,
    val sentenceTranslation: String,
)

// --- DTO → 도메인 매핑 ---

fun ConceptualBridgeDto.toDomain(): ConceptualBridge =
    ConceptualBridge(
        literalTranslation = literalTranslation,
        explanation = explanation,
        venn = venn.toDomain(),
    )

fun VennDto.toDomain(): VennData =
    VennData(
        guide = guide,
        left = VennCircle(word = leftCircle.word, items = leftCircle.items),
        right = VennCircle(word = rightCircle.word, items = rightCircle.items),
        intersectionItems = intersection.items,
    )

fun ToneStyleDto.toDomain(): ToneStyle =
    ToneStyle(
        defaultLevel = defaultLevel,
        levels = levels.map { ToneLevel(it.level, it.sentence, it.sentenceTranslation) },
    )

fun ParaphrasingDto.toDomain(): Paraphrasing =
    Paraphrasing(
        items = items.map { Paraphrase(it.level, it.label, it.sentence, it.sentenceTranslation) },
    )

/**
 * 저장 훅(seam, M2-03) 페이로드. Firestore 영속·턴 간 생존은 M2-04 소관 — 본 마일스톤은 이 seam 만 노출하고
 * 턴 내 ephemeral 토글 상태만 보유한다. cardId 파생은 M2-04 가 `{sessionId}__SENTENCE__{turnIndex}__{level}`
 * 로 수행한다(firestore-schema.md §3) — 그래서 훅에 [turnIndex] 와 [level] 을 함께 싣는다.
 */
data class ParaphraseBookmark(
    val turnIndex: Int,
    val level: Int,
    val sentence: String,
    val sentenceTranslation: String,
)

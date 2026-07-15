package com.jjundev.oneclickeng.feature.review.data

import com.jjundev.oneclickeng.feature.session.saved.SavedCard

/** saved_cards 문서의 SRS 상태(평면 필드로 영속). nextReviewAt/lastReviewedAt 은 절대 epoch-millis. */
data class ReviewState(
    val box: Int,
    val nextReviewAt: Long,
    val lastReviewedAt: Long,
    val reps: Int,
    val lapses: Int,
)

/**
 * 복습 풀 항목. review 가 null 이면 srs 없는 신규 카드(첫 복습 = box 0 취급).
 *
 * [aheadOfSchedule] = due 도, 신규(미복습)도 없을 때 폴백으로 당겨온 "미리 복습" 카드(다음 복습일이 아직
 * 안 됐지만 자발적으로 연습). 이 카드를 채점해도 SRS 스케줄(box/nextReviewAt 등)은 갱신하지 않는다
 * ([ReviewViewModel.record] 가드) — 조기 복습이 정식 간격반복 주기를 흐트러뜨리지 않도록.
 */
data class ReviewItem(
    val cardId: String,
    val card: SavedCard,
    val review: ReviewState?,
    val aheadOfSchedule: Boolean = false,
)

/** 복습 세션 화면 단계. Done = 세션 종료(완료 화면). */
enum class ReviewPhase { Front, Back, Ask, Reveal, Done }

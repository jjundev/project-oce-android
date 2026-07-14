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

/** 복습 풀 항목. review 가 null 이면 srs 없는 신규 카드(첫 복습 = box 0 취급). */
data class ReviewItem(
    val cardId: String,
    val card: SavedCard,
    val review: ReviewState?,
)

/** 복습 세션 화면 단계. Done = 세션 종료(완료 화면). */
enum class ReviewPhase { Front, Back, Ask, Reveal, Done }

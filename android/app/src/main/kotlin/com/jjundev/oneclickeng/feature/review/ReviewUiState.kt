package com.jjundev.oneclickeng.feature.review

import com.jjundev.oneclickeng.feature.review.data.ReviewItem
import com.jjundev.oneclickeng.feature.review.data.ReviewPhase

/** 옵션 A(before)=0 / B(after)=1, 정답=after. */
const val EXPRESSION_CORRECT_INDEX = 1

data class ReviewUiState(
    val loading: Boolean = true,
    val items: List<ReviewItem> = emptyList(),
    val index: Int = 0,
    val phase: ReviewPhase = ReviewPhase.Front,
    val pick: Int? = null,
    val done: Int = 0,
    val again: Int = 0,
    val finished: Boolean = false,
) {
    val current: ReviewItem? get() = items.getOrNull(index)
    val total: Int get() = items.size
}

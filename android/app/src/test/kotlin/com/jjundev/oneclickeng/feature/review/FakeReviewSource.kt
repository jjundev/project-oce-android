package com.jjundev.oneclickeng.feature.review

import com.jjundev.oneclickeng.feature.review.data.ReviewItem
import com.jjundev.oneclickeng.feature.review.data.ReviewSource

class FakeReviewSource(
    private val items: List<ReviewItem> = emptyList(),
    private val due: Int = 0,
) : ReviewSource {
    override suspend fun pool(
        nowMs: Long,
        target: Int,
    ): List<ReviewItem> = items.take(target)

    override suspend fun dueCount(
        nowMs: Long,
        cap: Int,
    ): Int = due.coerceAtMost(cap)
}

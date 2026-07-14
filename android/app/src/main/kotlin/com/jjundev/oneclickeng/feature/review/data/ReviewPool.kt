package com.jjundev.oneclickeng.feature.review.data

/** due(먼저) + 신규 카드(보충)를 cardId dedupe(due 우선) 후 target 개로 자른다. */
object ReviewPool {
    fun merge(
        due: List<ReviewItem>,
        newCards: List<ReviewItem>,
        target: Int,
    ): List<ReviewItem> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<ReviewItem>()
        for (item in due + newCards) {
            if (out.size >= target) break
            if (seen.add(item.cardId)) out += item
        }
        return out
    }
}

package com.jjundev.oneclickeng.feature.review.data

/**
 * 복습 풀 읽기 seam. pool = due 쿼리 + srs 없는 신규 보충(dedupe·cap). dueCount = 배너용 캡된 due 수.
 * Firestore 구현은 FirestoreReviewSource(Task 6).
 */
interface ReviewSource {
    suspend fun pool(
        nowMs: Long,
        target: Int = 20,
    ): List<ReviewItem>

    suspend fun dueCount(
        nowMs: Long,
        cap: Int = 20,
    ): Int
}

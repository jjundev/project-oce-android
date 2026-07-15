package com.jjundev.oneclickeng.feature.review.data

/**
 * Leitner 5박스 스케줄러(순수). 정답 → box=min(box+1,5), 오답 → box=1. box 0(=srs 없는 신규)에서
 * 정답/오답 모두 box 1. nextReviewAt = nowMs + 간격일×DAY_MS(rolling, KST day-key 미사용).
 */
object LeitnerLogic {
    private const val DAY_MS = 86_400_000L
    private val INTERVAL_DAYS = mapOf(1 to 1, 2 to 3, 3 to 7, 4 to 16, 5 to 35)

    fun onGrade(
        prev: ReviewState?,
        correct: Boolean,
        nowMs: Long,
    ): ReviewState {
        val prevBox = prev?.box ?: 0
        val newBox = if (correct) (prevBox + 1).coerceIn(1, 5) else 1
        val days = INTERVAL_DAYS.getValue(newBox)
        return ReviewState(
            box = newBox,
            nextReviewAt = nowMs + days * DAY_MS,
            lastReviewedAt = nowMs,
            reps = (prev?.reps ?: 0) + 1,
            lapses = (prev?.lapses ?: 0) + if (correct) 0 else 1,
        )
    }
}

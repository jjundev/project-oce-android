package com.jjundev.oneclickeng.feature.review.data

import javax.inject.Inject

/** 테스트 주입 가능한 현재시각(millis). due 계산·스케줄 write 에 쓰인다. */
interface ReviewClock {
    fun nowMs(): Long
}

class SystemReviewClock
    @Inject
    constructor() : ReviewClock {
        override fun nowMs(): Long = System.currentTimeMillis()
    }

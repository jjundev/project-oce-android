package com.jjundev.oneclickeng.core.network

import javax.inject.Inject

/**
 * Typed telemetry seam for the WaitQuiz `wait_quiz_card_answered` event (loading-quiz-interstitial.md
 * §8 / analytics-events.md §6.6). Field names/types match that contract exactly — no free-form log
 * line. M1-01 ships the seam and a [NoOpWaitQuizAnalytics] default binding; the real Firebase
 * dispatch is owned by the analytics-instrumentation milestone (M4-01). PII boundary: only
 * enum/bool/count/id — never the quiz text (loading-quiz-interstitial.md §8).
 */
interface WaitQuizAnalytics {
    fun cardAnswered(
        sessionId: String?,
        cardId: String,
        choseCorrect: Boolean,
        cardIndex: Int,
    )
}

/** Default no-op binding until M4-01 wires real dispatch. */
class NoOpWaitQuizAnalytics
    @Inject
    constructor() : WaitQuizAnalytics {
        override fun cardAnswered(
            sessionId: String?,
            cardId: String,
            choseCorrect: Boolean,
            cardIndex: Int,
        ) = Unit
    }

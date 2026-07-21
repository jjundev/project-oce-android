package com.jjundev.oneclickeng.core.network

import com.jjundev.oneclickeng.core.analytics.AnalyticsSink
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

    fun waitQuizShown(
        sessionId: String?,
        surface: String,
        delayMsAtShow: Long,
    )

    fun waitQuizEnded(
        sessionId: String?,
        surface: String,
        reason: String,
        cardsAnswered: Int,
        dwellMs: Long,
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

        override fun waitQuizShown(
            sessionId: String?,
            surface: String,
            delayMsAtShow: Long,
        ) = Unit

        override fun waitQuizEnded(
            sessionId: String?,
            surface: String,
            reason: String,
            cardsAnswered: Int,
            dwellMs: Long,
        ) = Unit
    }

/** Firebase dispatch (M4-01). `wait_quiz_card_answered` — analytics-events.md §4. */
class FirebaseWaitQuizAnalytics
    @Inject
    constructor(
        private val sink: AnalyticsSink,
    ) : WaitQuizAnalytics {
        override fun cardAnswered(
            sessionId: String?,
            cardId: String,
            choseCorrect: Boolean,
            cardIndex: Int,
        ) = sink.log(
            "wait_quiz_card_answered",
            buildMap {
                sessionId?.let { put("session_id", it) }
                put("card_id", cardId)
                put("chose_correct", choseCorrect)
                put("card_index", cardIndex.toLong())
            },
        )

        override fun waitQuizShown(
            sessionId: String?,
            surface: String,
            delayMsAtShow: Long,
        ) = sink.log(
            "wait_quiz_shown",
            buildMap {
                sessionId?.let { put("session_id", it) }
                put("surface", surface)
                put("delay_ms_at_show", delayMsAtShow)
            },
        )

        override fun waitQuizEnded(
            sessionId: String?,
            surface: String,
            reason: String,
            cardsAnswered: Int,
            dwellMs: Long,
        ) = sink.log(
            "wait_quiz_ended",
            buildMap {
                sessionId?.let { put("session_id", it) }
                put("surface", surface)
                put("reason", reason)
                put("cards_answered", cardsAnswered.toLong())
                put("dwell_ms", dwellMs)
            },
        )
    }

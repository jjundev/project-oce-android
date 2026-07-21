package com.jjundev.oneclickeng.core.network

import com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink
import org.junit.Assert.assertEquals
import org.junit.Test

class LimitWaitQuizAnalyticsDispatchTest {
    private val sink = RecordingAnalyticsSink()

    @Test
    fun `limit_reached carries remaining and surface`() {
        FirebaseLimitAnalytics(sink).limitReached(remaining = 0, surface = "dialogue_start_gate")
        assertEquals(
            RecordingAnalyticsSink.Event(
                "limit_reached",
                mapOf("remaining" to 0L, "surface" to "dialogue_start_gate"),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `wait_quiz_card_answered carries all fields and omits null session_id`() {
        FirebaseWaitQuizAnalytics(sink).cardAnswered(
            sessionId = null,
            cardId = "q7",
            choseCorrect = true,
            cardIndex = 2,
        )
        assertEquals(
            RecordingAnalyticsSink.Event(
                "wait_quiz_card_answered",
                mapOf("card_id" to "q7", "chose_correct" to true, "card_index" to 2L),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `wait_quiz_card_answered includes session_id when present`() {
        FirebaseWaitQuizAnalytics(sink).cardAnswered(
            sessionId = "sess-3",
            cardId = "q1",
            choseCorrect = false,
            cardIndex = 0,
        )
        assertEquals(
            mapOf(
                "session_id" to "sess-3",
                "card_id" to "q1",
                "chose_correct" to false,
                "card_index" to 0L,
            ),
            sink.events.single().params,
        )
    }
}

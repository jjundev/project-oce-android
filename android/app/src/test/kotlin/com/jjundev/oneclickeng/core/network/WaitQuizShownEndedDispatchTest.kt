package com.jjundev.oneclickeng.core.network

import com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink
import org.junit.Assert.assertEquals
import org.junit.Test

class WaitQuizShownEndedDispatchTest {
    private val sink = RecordingAnalyticsSink()
    private val analytics = FirebaseWaitQuizAnalytics(sink)

    @Test
    fun `wait_quiz_shown carries surface and delay, omits null session_id`() {
        analytics.waitQuizShown(sessionId = null, surface = "home", delayMsAtShow = 1000L)
        assertEquals(
            RecordingAnalyticsSink.Event(
                "wait_quiz_shown",
                mapOf("surface" to "home", "delay_ms_at_show" to 1000L),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `wait_quiz_ended carries reason cards_answered dwell and session_id when present`() {
        analytics.waitQuizEnded(
            sessionId = "s1",
            surface = "onboarding_first_session",
            reason = "ready",
            cardsAnswered = 2,
            dwellMs = 3400L,
        )
        assertEquals(
            mapOf(
                "session_id" to "s1",
                "surface" to "onboarding_first_session",
                "reason" to "ready",
                "cards_answered" to 2L,
                "dwell_ms" to 3400L,
            ),
            sink.events.single().params,
        )
        assertEquals("wait_quiz_ended", sink.events.single().name)
    }
}

package com.jjundev.oneclickeng.feature.onboarding

import com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingAnalyticsDispatchTest {
    private val sink = RecordingAnalyticsSink()
    private val analytics = FirebaseOnboardingAnalytics(sink)

    @Test
    fun `onboarding_started carries is_returning`() {
        analytics.onboardingStarted(isReturning = true)
        assertEquals(
            RecordingAnalyticsSink.Event("onboarding_started", mapOf("is_returning" to true)),
            sink.events.single(),
        )
    }

    @Test
    fun `level_selected carries level`() {
        analytics.levelSelected("hard")
        assertEquals(
            RecordingAnalyticsSink.Event("level_selected", mapOf("level" to "hard")),
            sink.events.single(),
        )
    }

    @Test
    fun `topic_selected carries topic_id and beginner_friendly`() {
        analytics.topicSelected(topicId = "cafe_order", beginnerFriendly = true)
        assertEquals(
            RecordingAnalyticsSink.Event(
                "topic_selected",
                mapOf("topic_id" to "cafe_order", "beginner_friendly" to true),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `google_link_succeeded carries session_id`() {
        analytics.googleLinkSucceeded(sessionId = "sess-9")
        assertEquals(
            RecordingAnalyticsSink.Event("google_link_succeeded", mapOf("session_id" to "sess-9")),
            sink.events.single(),
        )
    }

    @Test
    fun `reauth_link_failed has no params`() {
        analytics.reauthLinkFailed()
        assertEquals(
            RecordingAnalyticsSink.Event("reauth_link_failed", emptyMap()),
            sink.events.single(),
        )
    }
}

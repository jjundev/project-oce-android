package com.jjundev.oneclickeng.feature.home

import com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAnalyticsDispatchTest {
    private val sink = RecordingAnalyticsSink()
    private val analytics = FirebaseHomeAnalytics(sink)

    @Test
    fun `paramless home events log the right ids`() {
        analytics.homeView()
        analytics.homeCtaTap()
        analytics.resumeContinue()
        analytics.resumeStartNew()
        assertEquals(
            listOf("home_view", "home_cta_tap", "resume_continue", "resume_start_new"),
            sink.events.map { it.name },
        )
        assertEquals(emptyMap<String, Any>(), sink.events.first().params)
    }

    @Test
    fun `topic_selected omits topic_id when null and carries custom`() {
        analytics.topicSelected(topicId = null, custom = true)
        assertEquals(
            RecordingAnalyticsSink.Event("topic_selected", mapOf("custom" to true)),
            sink.events.single(),
        )
    }

    @Test
    fun `topic_selected includes topic_id for a curated seed`() {
        analytics.topicSelected(topicId = "cafe_order", custom = false)
        assertEquals(
            RecordingAnalyticsSink.Event(
                "topic_selected",
                mapOf("topic_id" to "cafe_order", "custom" to false),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `session_setting_changed carries level and length`() {
        analytics.sessionSettingChanged(level = "normal", length = 10)
        assertEquals(
            RecordingAnalyticsSink.Event(
                "session_setting_changed",
                mapOf("level" to "normal", "length" to 10L),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `offlineBlocked reuses offline_blocked_action with surface home`() {
        analytics.offlineBlocked()
        assertEquals(
            RecordingAnalyticsSink.Event("offline_blocked_action", mapOf("surface" to "home")),
            sink.events.single(),
        )
    }
}

package com.jjundev.oneclickeng.core.connectivity

import com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineAnalyticsDispatchTest {
    private val sink = RecordingAnalyticsSink()
    private val analytics = FirebaseOfflineAnalytics(sink)

    @Test
    fun `connectivity_changed carries online flag`() {
        analytics.connectivityChanged(online = false)
        assertEquals(
            RecordingAnalyticsSink.Event("connectivity_changed", mapOf("online" to false)),
            sink.events.single(),
        )
    }

    @Test
    fun `offline_blocked_action carries surface`() {
        analytics.offlineBlocked(surface = "dialogue_start_gate")
        assertEquals(
            RecordingAnalyticsSink.Event(
                "offline_blocked_action",
                mapOf("surface" to "dialogue_start_gate"),
            ),
            sink.events.single(),
        )
    }
}

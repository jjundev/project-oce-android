package com.jjundev.oneclickeng.feature.session.analytics

import com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink
import org.junit.Assert.assertEquals
import org.junit.Test

class MicPermissionAnalyticsDispatchTest {
    private val sink = RecordingAnalyticsSink()
    private val analytics = FirebaseMicPermissionAnalytics(sink)

    @Test
    fun `requested logs mic_permission_requested with source`() {
        analytics.requested(MicPermissionAnalytics.SOURCE_SESSION)
        assertEquals(
            RecordingAnalyticsSink.Event("mic_permission_requested", mapOf("source" to "session")),
            sink.events.single(),
        )
    }

    @Test
    fun `result logs mic_permission_result with source and granted`() {
        analytics.result(MicPermissionAnalytics.SOURCE_SESSION, granted = true)
        assertEquals(
            RecordingAnalyticsSink.Event("mic_permission_result", mapOf("source" to "session", "granted" to true)),
            sink.events.single(),
        )
    }
}

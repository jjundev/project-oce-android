// android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/analytics/LatencyAnalyticsDispatchTest.kt
package com.jjundev.oneclickeng.feature.session.analytics

import com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink
import org.junit.Assert.assertEquals
import org.junit.Test

class LatencyAnalyticsDispatchTest {
    private val sink = RecordingAnalyticsSink()
    private val analytics = FirebaseLatencyAnalytics(sink)

    @Test
    fun `logs script_gen_latency_ms with outcome and latency_ms`() {
        analytics.latency(LatencyAnalytics.OPERATION_SCRIPT_GEN, LatencyAnalytics.OUTCOME_SUCCESSFUL, 850L)
        assertEquals(
            RecordingAnalyticsSink.Event(
                "script_gen_latency_ms",
                mapOf("outcome" to "successful", "latency_ms" to 850L),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `logs speaking_analyze_latency_ms for the pinned operation id`() {
        analytics.latency(LatencyAnalytics.OPERATION_SPEAKING_ANALYZE, LatencyAnalytics.OUTCOME_FAILED, 1200L)
        assertEquals(
            RecordingAnalyticsSink.Event(
                "speaking_analyze_latency_ms",
                mapOf("outcome" to "failed", "latency_ms" to 1200L),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `logs slim_latency_ms`() {
        analytics.latency(LatencyAnalytics.OPERATION_SLIM, LatencyAnalytics.OUTCOME_SUCCESSFUL, 400L)
        assertEquals(
            RecordingAnalyticsSink.Event("slim_latency_ms", mapOf("outcome" to "successful", "latency_ms" to 400L)),
            sink.events.single(),
        )
    }

    @Test
    fun `logs deep_latency_ms with canceled outcome`() {
        analytics.latency(LatencyAnalytics.OPERATION_DEEP, LatencyAnalytics.OUTCOME_CANCELED, 300L)
        assertEquals(
            RecordingAnalyticsSink.Event("deep_latency_ms", mapOf("outcome" to "canceled", "latency_ms" to 300L)),
            sink.events.single(),
        )
    }

    @Test
    fun `logs summary_latency_ms`() {
        analytics.latency(LatencyAnalytics.OPERATION_SUMMARY, LatencyAnalytics.OUTCOME_SUCCESSFUL, 2000L)
        assertEquals(
            RecordingAnalyticsSink.Event("summary_latency_ms", mapOf("outcome" to "successful", "latency_ms" to 2000L)),
            sink.events.single(),
        )
    }

    @Test
    fun `logs tts_latency_ms`() {
        analytics.latency(LatencyAnalytics.OPERATION_TTS, LatencyAnalytics.OUTCOME_FAILED, 900L)
        assertEquals(
            RecordingAnalyticsSink.Event("tts_latency_ms", mapOf("outcome" to "failed", "latency_ms" to 900L)),
            sink.events.single(),
        )
    }
}

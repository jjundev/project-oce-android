package com.jjundev.oneclickeng.feature.session.analytics

import com.jjundev.oneclickeng.core.analytics.AnalyticsSink
import javax.inject.Inject

/**
 * Shared latency telemetry seam (M4-01f, `analytics-events.md` §10) for the `*_latency_ms` series —
 * auxiliary round-trip timing, NOT a 5대 지표 numerator/denominator. One method covers every timed
 * operation so there is a single dispatch impl + one contract test for all operation ids. PII:
 * enum/duration only.
 */
interface LatencyAnalytics {
    fun latency(operation: String, outcome: String, latencyMs: Long)

    companion object {
        const val OPERATION_SCRIPT_GEN = "script_gen"
        const val OPERATION_SPEAKING_ANALYZE = "speaking_analyze"
        const val OPERATION_SLIM = "slim"
        const val OPERATION_DEEP = "deep"
        const val OPERATION_SUMMARY = "summary"

        const val OUTCOME_SUCCESSFUL = "successful"
        const val OUTCOME_FAILED = "failed"
        const val OUTCOME_CANCELED = "canceled"
    }
}

/** Default no-op binding (test/fallback). */
class NoOpLatencyAnalytics
    @Inject
    constructor() : LatencyAnalytics {
        override fun latency(operation: String, outcome: String, latencyMs: Long) = Unit
    }

/** Firebase dispatch via the shared [AnalyticsSink] (M4-01a). Event id = `"${operation}_latency_ms"`. */
class FirebaseLatencyAnalytics
    @Inject
    constructor(
        private val sink: AnalyticsSink,
    ) : LatencyAnalytics {
        override fun latency(operation: String, outcome: String, latencyMs: Long) =
            sink.log("${operation}_latency_ms", mapOf("outcome" to outcome, "latency_ms" to latencyMs))
    }

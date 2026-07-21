package com.jjundev.oneclickeng.feature.session.analytics

/** Records latency calls for emit-site behavior tests (repo convention = fakes). */
class RecordingLatencyAnalytics : LatencyAnalytics {
    data class Call(val operation: String, val outcome: String, val latencyMs: Long)

    val calls = mutableListOf<Call>()

    override fun latency(operation: String, outcome: String, latencyMs: Long) {
        calls += Call(operation, outcome, latencyMs)
    }
}

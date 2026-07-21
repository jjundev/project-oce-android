package com.jjundev.oneclickeng.feature.session.analytics

import com.jjundev.oneclickeng.core.analytics.AnalyticsSink
import javax.inject.Inject

/**
 * RECORD_AUDIO permission telemetry seam (M4-01d, analytics-events.md §4). `source` is a single
 * constant today (the in-session mic request); the param exists for a future settings re-request path.
 * PII: only source enum + granted bool.
 */
interface MicPermissionAnalytics {
    fun requested(source: String)

    fun result(source: String, granted: Boolean)

    companion object {
        const val SOURCE_SESSION = "session"
    }
}

/** Default no-op binding (test/fallback). */
class NoOpMicPermissionAnalytics
    @Inject
    constructor() : MicPermissionAnalytics {
        override fun requested(source: String) = Unit

        override fun result(source: String, granted: Boolean) = Unit
    }

/** Firebase dispatch via the shared [AnalyticsSink] (M4-01a). */
class FirebaseMicPermissionAnalytics
    @Inject
    constructor(
        private val sink: AnalyticsSink,
    ) : MicPermissionAnalytics {
        override fun requested(source: String) = sink.log("mic_permission_requested", mapOf("source" to source))

        override fun result(source: String, granted: Boolean) =
            sink.log("mic_permission_result", mapOf("source" to source, "granted" to granted))
    }

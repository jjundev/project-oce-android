package com.jjundev.oneclickeng.core.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single dispatch seam for Firebase Analytics (M4-01). Feature-specific `Firebase*Analytics` impls
 * map their typed calls to GA4 events through this seam so the event-name/param contract is
 * unit-testable with [RecordingAnalyticsSink] (repo convention = no mockk). The Firebase wrapper
 * itself is thin and not unit-tested; only [toAnalyticsBundle] is pinned.
 *
 * PII boundary (analytics-events.md §7): callers pass only enum/bool/count/duration/id — never free text.
 */
interface AnalyticsSink {
    fun log(event: String, params: Map<String, Any> = emptyMap())

    fun setUserId(userId: String?)

    fun setUserProperty(name: String, value: String?)
}

@Singleton
class FirebaseAnalyticsSink
    @Inject
    constructor(
        private val analytics: FirebaseAnalytics,
    ) : AnalyticsSink {
        override fun log(event: String, params: Map<String, Any>) =
            analytics.logEvent(event, params.toAnalyticsBundle())

        override fun setUserId(userId: String?) = analytics.setUserId(userId)

        override fun setUserProperty(name: String, value: String?) = analytics.setUserProperty(name, value)
    }

/** GA4 params accept String/Long/Double (+Boolean via Bundle). Int/Float are widened. */
internal fun Map<String, Any>.toAnalyticsBundle(): Bundle =
    Bundle().apply {
        forEach { (key, value) ->
            when (value) {
                is String -> putString(key, value)
                is Boolean -> putBoolean(key, value)
                is Int -> putLong(key, value.toLong())
                is Long -> putLong(key, value)
                is Double -> putDouble(key, value)
                is Float -> putDouble(key, value.toDouble())
                else -> putString(key, value.toString())
            }
        }
    }

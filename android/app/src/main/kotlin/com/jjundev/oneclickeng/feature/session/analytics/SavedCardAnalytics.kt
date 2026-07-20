package com.jjundev.oneclickeng.feature.session.analytics

import com.jjundev.oneclickeng.core.analytics.AnalyticsSink
import com.jjundev.oneclickeng.feature.session.saved.CardType
import javax.inject.Inject

/**
 * `saved_card_create` telemetry seam (M4-01c, analytics-events.md §4/§6.3). Fires on each save action
 * (explicit toggle-add + save-by-default auto-save) from both surfaces. `card_type` = [CardType.wire]
 * (same encoding as FirebaseHistoryAnalytics). PII: only session_id/surface enum/card_type enum.
 */
interface SavedCardAnalytics {
    fun savedCardCreate(sessionId: String, surface: String, cardType: CardType)

    companion object {
        const val SURFACE_SUMMARY = "summary"
        const val SURFACE_DEEP_FEEDBACK = "deep_feedback"
    }
}

/** Default no-op binding (test/fallback). */
class NoOpSavedCardAnalytics
    @Inject
    constructor() : SavedCardAnalytics {
        override fun savedCardCreate(sessionId: String, surface: String, cardType: CardType) = Unit
    }

/** Firebase dispatch via the shared [AnalyticsSink] (M4-01a). */
class FirebaseSavedCardAnalytics
    @Inject
    constructor(
        private val sink: AnalyticsSink,
    ) : SavedCardAnalytics {
        override fun savedCardCreate(sessionId: String, surface: String, cardType: CardType) =
            sink.log(
                "saved_card_create",
                mapOf("session_id" to sessionId, "surface" to surface, "card_type" to cardType.wire),
            )
    }

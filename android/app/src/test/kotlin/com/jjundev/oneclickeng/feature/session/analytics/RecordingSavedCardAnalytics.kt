package com.jjundev.oneclickeng.feature.session.analytics

import com.jjundev.oneclickeng.feature.session.saved.CardType

/** Records saved-card calls for emit-site behavior tests (repo convention = fakes). */
class RecordingSavedCardAnalytics : SavedCardAnalytics {
    data class Call(val sessionId: String, val surface: String, val cardType: CardType)

    val calls = mutableListOf<Call>()

    override fun savedCardCreate(
        sessionId: String,
        surface: String,
        cardType: CardType,
    ) {
        calls += Call(sessionId, surface, cardType)
    }
}

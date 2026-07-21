package com.jjundev.oneclickeng.feature.session.analytics

import com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink
import com.jjundev.oneclickeng.feature.session.saved.CardType
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedCardAnalyticsDispatchTest {
    private val sink = RecordingAnalyticsSink()
    private val analytics = FirebaseSavedCardAnalytics(sink)

    @Test
    fun `summary word save logs saved_card_create with card_type wire`() {
        analytics.savedCardCreate("s1", SavedCardAnalytics.SURFACE_SUMMARY, CardType.WORD)
        assertEquals(
            RecordingAnalyticsSink.Event(
                "saved_card_create",
                mapOf("session_id" to "s1", "surface" to "summary", "card_type" to CardType.WORD.wire),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `deep feedback sentence save logs the deep_feedback surface`() {
        analytics.savedCardCreate("s2", SavedCardAnalytics.SURFACE_DEEP_FEEDBACK, CardType.SENTENCE)
        assertEquals(
            RecordingAnalyticsSink.Event(
                "saved_card_create",
                mapOf("session_id" to "s2", "surface" to "deep_feedback", "card_type" to CardType.SENTENCE.wire),
            ),
            sink.events.single(),
        )
    }
}

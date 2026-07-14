package com.jjundev.oneclickeng.feature.session.saved

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedCardSrsPayloadTest {
    @Test
    fun `srs payload carries flat srs fields plus cardType for update rule`() {
        val payload =
            SavedCardPayload.srs(
                cardType = CardType.EXPRESSION,
                box = 3,
                nextReviewAt = 1_700_000_000_000L,
                lastReviewedAt = 1_699_000_000_000L,
                reps = 4,
                lapses = 1,
            )
        assertEquals("EXPRESSION", payload["cardType"])
        assertEquals(3, payload["srsBox"])
        assertEquals(1_700_000_000_000L, payload["srsNextReviewAt"])
        assertEquals(1_699_000_000_000L, payload["srsLastReviewedAt"])
        assertEquals(4, payload["srsReps"])
        assertEquals(1, payload["srsLapses"])
    }

    @Test
    fun `srs payload does not touch content or createdAt or deletedAt`() {
        val payload = SavedCardPayload.srs(CardType.WORD, 1, 1L, 1L, 1, 0)
        assertEquals(false, payload.containsKey("createdAt"))
        assertEquals(false, payload.containsKey("deletedAt"))
        assertEquals(false, payload.containsKey("english"))
    }
}

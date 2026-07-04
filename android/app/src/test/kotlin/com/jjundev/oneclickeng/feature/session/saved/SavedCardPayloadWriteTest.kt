package com.jjundev.oneclickeng.feature.session.saved

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * create/revive/tombstone write 페이로드의 회귀 잠금(사양 #4·#13). 특히 **revive 는 createdAt 을 절대 싣지
 * 않는다** — 서버가 createdAt 불변을 강제하지 않으므로(saved-cards.md:57-58), 향후 코드가 revive 에서
 * createdAt 을 덮으면 조용한 재정렬이 생긴다. 이 테스트가 그 불변식을 잠근다(순수 함수라 Firestore 불필요).
 */
class SavedCardPayloadWriteTest {
    private val word = SavedCard.Word("grab", "잽싸게", "grab a bite", "간단히")
    private val serverTs = "SERVER_TS" // 리포지토리는 FieldValue.serverTimestamp() 를 주입 — 여기선 마커로 대체.

    @Test
    fun `create carries createdAt and deletedAt=null`() {
        val payload = SavedCardPayload.create(word, serverTs)
        assertEquals(serverTs, payload["createdAt"])
        assertTrue(payload.containsKey("deletedAt"))
        assertNull(payload["deletedAt"])
        assertEquals("WORD", payload["cardType"])
    }

    @Test
    fun `revive never carries createdAt (sort-order preservation)`() {
        val payload = SavedCardPayload.revive(word)
        assertFalse("revive must not rewrite createdAt", payload.containsKey("createdAt"))
        assertTrue(payload.containsKey("deletedAt"))
        assertNull(payload["deletedAt"]) // 되살림
        assertEquals("WORD", payload["cardType"])
        // content 필드도 함께 실려 revive 가 내용 refresh 를 겸한다.
        assertEquals("grab", payload["english"])
    }

    @Test
    fun `tombstone carries cardType (update rule) and the given deletedAt`() {
        val deletePayload = SavedCardPayload.tombstone(CardType.SENTENCE, serverTs)
        assertEquals("SENTENCE", deletePayload["cardType"])
        assertEquals(serverTs, deletePayload["deletedAt"])
        assertFalse(deletePayload.containsKey("createdAt"))
    }

    @Test
    fun `tombstone revive passes null deletedAt`() {
        val revivePayload = SavedCardPayload.tombstone(CardType.WORD, null)
        assertEquals("WORD", revivePayload["cardType"])
        assertTrue(revivePayload.containsKey("deletedAt"))
        assertNull(revivePayload["deletedAt"])
    }
}

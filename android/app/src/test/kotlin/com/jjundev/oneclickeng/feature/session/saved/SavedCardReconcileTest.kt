package com.jjundev.oneclickeng.feature.session.saved

import com.jjundev.oneclickeng.feature.session.saved.SavedCardReconcile.SavedCardDoc
import org.junit.Assert.assertEquals
import org.junit.Test

/** delete-wins union 정책(firestore-schema.md:164). "union/tombstone 머지 테스트" 검증 라인. */
class SavedCardReconcileTest {
    @Test
    fun `tombstone beats a live copy`() {
        val live = SavedCardDoc(createdAt = 100, deletedAt = null)
        val tombstone = SavedCardDoc(createdAt = 100, deletedAt = 200)
        assertEquals(tombstone, SavedCardReconcile.deleteWins(live, tombstone))
        assertEquals(tombstone, SavedCardReconcile.deleteWins(tombstone, live))
    }

    @Test
    fun `two tombstones keep the later deletedAt`() {
        val early = SavedCardDoc(createdAt = 100, deletedAt = 150)
        val late = SavedCardDoc(createdAt = 100, deletedAt = 300)
        assertEquals(late, SavedCardReconcile.deleteWins(early, late))
        assertEquals(late, SavedCardReconcile.deleteWins(late, early))
    }

    @Test
    fun `two live copies keep the later createdAt`() {
        val older = SavedCardDoc(createdAt = 100, deletedAt = null)
        val newer = SavedCardDoc(createdAt = 400, deletedAt = null)
        assertEquals(newer, SavedCardReconcile.deleteWins(older, newer))
        assertEquals(newer, SavedCardReconcile.deleteWins(newer, older))
    }

    @Test
    fun `null createdAt is treated as oldest`() {
        val unknown = SavedCardDoc(createdAt = null, deletedAt = null)
        val known = SavedCardDoc(createdAt = 1, deletedAt = null)
        assertEquals(known, SavedCardReconcile.deleteWins(unknown, known))
    }
}

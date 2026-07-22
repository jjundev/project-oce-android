package com.jjundev.oneclickeng.feature.review

import com.jjundev.oneclickeng.feature.review.data.ReviewItem
import com.jjundev.oneclickeng.feature.review.data.ReviewPool
import com.jjundev.oneclickeng.feature.review.data.ReviewState
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewPoolTest {
    private fun item(
        id: String,
        hasSrs: Boolean = true,
    ) = ReviewItem(
        cardId = id,
        card = SavedCard.Sentence(english = "s-$id", korean = "문장-$id"),
        review = if (hasSrs) ReviewState(1, 0, 0, 1, 0) else null,
    )

    @Test
    fun `due comes first, then new cards, capped at target`() {
        val due = listOf(item("d1"), item("d2"))
        val fresh = listOf(item("n1", hasSrs = false), item("n2", hasSrs = false))
        val merged = ReviewPool.merge(due, fresh, target = 3)
        assertEquals(listOf("d1", "d2", "n1"), merged.map { it.cardId })
    }

    @Test
    fun `duplicates by cardId are removed, due winning`() {
        val due = listOf(item("x"), item("d1"))
        val fresh = listOf(item("x", hasSrs = false), item("n1", hasSrs = false))
        val merged = ReviewPool.merge(due, fresh, target = 10)
        assertEquals(listOf("x", "d1", "n1"), merged.map { it.cardId })
        assertEquals(true, merged.first { it.cardId == "x" }.review != null)
    }

    @Test
    fun `target larger than supply returns all available`() {
        val merged = ReviewPool.merge(listOf(item("d1")), emptyList(), target = 20)
        assertEquals(listOf("d1"), merged.map { it.cardId })
    }
}

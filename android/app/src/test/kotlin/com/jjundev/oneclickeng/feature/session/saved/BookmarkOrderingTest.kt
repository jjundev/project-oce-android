package com.jjundev.oneclickeng.feature.session.saved

import com.jjundev.oneclickeng.feature.session.summary.BookmarkCard
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkOrderingTest {
    @Test
    fun `newest createdAt first, capped to limit`() {
        val docs =
            listOf(
                BookmarkDoc("a-id", "a", "가", createdAtMillis = 100),
                BookmarkDoc("b-id", "b", "나", createdAtMillis = 300),
                BookmarkDoc("c-id", "c", "다", createdAtMillis = 200),
            )
        assertEquals(
            listOf(
                BookmarkCard("b-id", "b", "나"),
                BookmarkCard("c-id", "c", "다"),
            ),
            BookmarkOrdering.latest(docs, limit = 2),
        )
    }

    @Test
    fun `pending write (null createdAt) is treated as newest`() {
        val docs =
            listOf(
                BookmarkDoc("old-id", "old", "옛", createdAtMillis = 500),
                BookmarkDoc("just-saved-id", "justSaved", "방금", createdAtMillis = null),
            )
        assertEquals(
            listOf(
                BookmarkCard("just-saved-id", "justSaved", "방금"),
                BookmarkCard("old-id", "old", "옛"),
            ),
            BookmarkOrdering.latest(docs, limit = 8),
        )
    }

    @Test
    fun `empty input yields empty output`() {
        assertEquals(emptyList<BookmarkCard>(), BookmarkOrdering.latest(emptyList(), limit = 8))
    }

    @Test
    fun latestOrderingPreservesCardIds() {
        val docs =
            listOf(
                BookmarkDoc(cardId = "old-id", english = "old", korean = "옛", createdAtMillis = 100),
                BookmarkDoc(cardId = "new-id", english = "new", korean = "새", createdAtMillis = 300),
            )

        assertEquals(
            listOf(BookmarkCard(cardId = "new-id", english = "new", korean = "새")),
            BookmarkOrdering.latest(docs, limit = 1),
        )
    }
}

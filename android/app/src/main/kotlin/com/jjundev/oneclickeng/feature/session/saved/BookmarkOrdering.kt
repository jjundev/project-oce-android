package com.jjundev.oneclickeng.feature.session.saved

import com.jjundev.oneclickeng.feature.session.summary.BookmarkCard

/** 정렬용 북마크 doc — [createdAtMillis] 는 estimate 로 해석된 생성시각(미해결 pending write 는 null). */
data class BookmarkDoc(
    val cardId: String,
    val english: String,
    val korean: String,
    val createdAtMillis: Long?,
)

/**
 * "최신 N개" 북마크 정렬(순수). 방금 저장한(pending server timestamp) 카드가 반드시 최상단에 오도록,
 * createdAt 이 여전히 null 인 doc 을 **가장 최신**으로 취급한다(estimate 로도 못 푼 = 막 큐된 로컬 쓰기).
 * 이는 표시 정렬 전용 규칙으로, 충돌 해소의 [SavedCardReconcile](null=가장 오래됨)과는 관심사가 다르다.
 */
object BookmarkOrdering {
    fun latest(
        docs: List<BookmarkDoc>,
        limit: Int,
    ): List<BookmarkCard> =
        docs
            .sortedByDescending { it.createdAtMillis ?: Long.MAX_VALUE }
            .take(limit)
            .map { doc -> BookmarkCard(cardId = doc.cardId, english = doc.english, korean = doc.korean) }
}

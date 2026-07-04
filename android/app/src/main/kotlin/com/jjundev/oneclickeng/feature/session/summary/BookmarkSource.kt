package com.jjundev.oneclickeng.feature.session.summary

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 요약 북마크 문장 섹션의 read seam(M2-02, plan v3 #7 확정). 요약은 사용자가 턴 중 deep 피드백에서 저장한
 * 패러프레이즈(`saved_cards` 의 SENTENCE 카드)를 **최신순 ≤8** 로 표시만 한다(saved-cards.md §3.3, 저장
 * 토글 배선은 M2-04).
 *
 * **범위 경계:** `saved_cards` 영속/쿼리층은 M2-04 소관이며 현재 Android 구현이 없다(M2-04-saved-cards.md
 * `blocked_by:[M0-08, M2-02]`). 따라서 M2-02 는 이 seam 만 도입하고 M2-04 착지 전엔 [EmptyBookmarkSource]
 * 가 빈 리스트를 반환한다 — 북마크 섹션은 빈 상태(OneClickEmptyState)로 완결된다. M2-04 가 Firestore 쿼리
 * 구현을 이 인터페이스에 주입한다([SessionTurnBufferStore] #17 seam 패턴 대칭).
 */
interface BookmarkSource {
    /** 세션의 SENTENCE 북마크를 최신순 [limit] 개까지 반환. */
    suspend fun latestSentences(
        sessionId: String,
        limit: Int,
    ): List<BookmarkCard>
}

/**
 * M2-04 이전의 빈 구현. 항상 빈 리스트를 반환해 북마크 섹션이 빈 상태로 렌더되게 한다. M2-04 가 Firestore
 * `saved_cards` (type=SENTENCE, deletedAt=null, createdAt desc) 쿼리 구현으로 이 바인딩을 교체한다.
 */
@Singleton
class EmptyBookmarkSource
    @Inject
    constructor() : BookmarkSource {
        override suspend fun latestSentences(
            sessionId: String,
            limit: Int,
        ): List<BookmarkCard> = emptyList()
    }

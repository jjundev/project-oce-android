package com.jjundev.oneclickeng.feature.session.summary

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 요약 북마크 문장 섹션의 read seam(M2-02, plan v3 #7 확정). 요약은 사용자가 턴 중 deep 피드백에서 저장한
 * 패러프레이즈(`saved_cards` 의 활성 SENTENCE 카드)를 **최신순 ≤8** 로 읽고, [SummaryCoordinator] 가 저장
 * 해제 토글을 영속 계층에 전달한다(saved-cards.md §3.3).
 *
 * **범위 경계:** 이 인터페이스는 활성 SENTENCE 카드의 읽기 계약만 소유한다. 저장/삭제 영속화는
 * [SummaryCoordinator] 가 [com.jjundev.oneclickeng.feature.session.saved.SavedCardRepository] 에 위임한다
 * ([SessionTurnBufferStore] #17 seam 패턴 대칭).
 */
interface BookmarkSource {
    /** 세션의 SENTENCE 북마크를 최신순 [limit] 개까지 반환. */
    suspend fun latestSentences(
        sessionId: String,
        limit: Int,
    ): List<BookmarkCard>
}

/** 기본 바인딩. 저장 카드 모듈이 없을 때만 빈 리스트를 반환한다. */
@Singleton
class EmptyBookmarkSource
    @Inject
    constructor() : BookmarkSource {
        override suspend fun latestSentences(
            sessionId: String,
            limit: Int,
        ): List<BookmarkCard> = emptyList()
    }

package com.jjundev.oneclickeng.feature.session.saved

import com.jjundev.oneclickeng.feature.session.summary.BookmarkSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 저장 카드(M2-04) seam 바인딩.
 * - [SavedCardRepository] → [FirestoreSavedCardRepository] (쓰기: 결정적 cardId union + 톰스톤).
 * - [BookmarkSource] → [FirestoreBookmarkSource] (읽기: SENTENCE 최신순). M2-02 의 `EmptyBookmarkSource`
 *   바인딩을 이 모듈이 대체한다(요약 코디네이터는 인터페이스에만 의존해 배선 변경에 무영향).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SavedCardModule {
    @Binds
    @Singleton
    abstract fun bindSavedCardRepository(impl: FirestoreSavedCardRepository): SavedCardRepository

    @Binds
    @Singleton
    abstract fun bindBookmarkSource(impl: FirestoreBookmarkSource): BookmarkSource
}

package com.jjundev.oneclickeng.feature.session.summary

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 요약 화면(M2-02)의 feature seam 바인딩. 요약 SSE 스트림([com.jjundev.oneclickeng.core.network.SummaryStream])
 * 은 core `SummaryModule` 이 바인딩하고, 여기서는 요약 화면 고유의 seam 을 바인딩한다:
 * - [CompletionLedger] → [FirestoreCompletionLedger] (요약 진입 시 point_ledger create 시도, #20).
 *
 * [BookmarkSource] 바인딩은 M2-04 에서 `saved` 패키지의 `SavedCardModule`(→ `FirestoreBookmarkSource`)로
 * 이전했다(M2-02 의 `EmptyBookmarkSource` 는 테스트/프리뷰용으로 존치). 코디네이터가 쓰는 앱 CoroutineScope
 * 는 `TtsProvideModule.provideAppScope` 가 제공한다(M1-05 공유).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SummaryFeatureModule {
    @Binds
    @Singleton
    abstract fun bindCompletionLedger(impl: FirestoreCompletionLedger): CompletionLedger
}

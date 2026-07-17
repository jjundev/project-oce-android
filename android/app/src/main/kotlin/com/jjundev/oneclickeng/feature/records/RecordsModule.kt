package com.jjundev.oneclickeng.feature.records

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 기록 탭(M2-05) seam 바인딩.
 * - [SavedCardQuerySource] → [FirestoreSavedCardQuerySource] (읽기: 3종 커서 증분).
 * - [LifetimeStatsSource] → [FirestoreLifetimeStatsSource] (서버 progress + 로컬 studytime 합성).
 * - [HistoryAnalytics] → [FirebaseHistoryAnalytics].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RecordsModule {
    @Binds
    @Singleton
    abstract fun bindSavedCardQuerySource(impl: FirestoreSavedCardQuerySource): SavedCardQuerySource

    @Binds
    @Singleton
    abstract fun bindLifetimeStatsSource(impl: FirestoreLifetimeStatsSource): LifetimeStatsSource

    @Binds
    @Singleton
    abstract fun bindHistoryAnalytics(impl: FirebaseHistoryAnalytics): HistoryAnalytics
}

package com.jjundev.oneclickeng.feature.records

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 기록 탭(M2-05) seam 바인딩.
 * - [SavedCardQuerySource] → [FirestoreSavedCardQuerySource] (읽기: 3종 커서 증분).
 * - [LifetimeStatsSource] → [StubLifetimeStatsSource] (M3-05 배선 전 스텁 — 헤더 정적 0).
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
    abstract fun bindLifetimeStatsSource(impl: StubLifetimeStatsSource): LifetimeStatsSource

    @Binds
    @Singleton
    abstract fun bindHistoryAnalytics(impl: FirebaseHistoryAnalytics): HistoryAnalytics
}

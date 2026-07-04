package com.jjundev.oneclickeng.core.connectivity

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 연결성 레이어(M4-04) seam 바인딩.
 * - [ConnectivityObserver] → [AndroidConnectivityObserver] (ConnectivityManager 기반 도달성 소스).
 * - [OfflineAnalytics] → [NoOpOfflineAnalytics] (M4-01 실 디스패치 배선 전 기본 no-op).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectivityModule {
    @Binds
    @Singleton
    abstract fun bindConnectivityObserver(impl: AndroidConnectivityObserver): ConnectivityObserver

    @Binds
    @Singleton
    abstract fun bindOfflineAnalytics(impl: NoOpOfflineAnalytics): OfflineAnalytics
}

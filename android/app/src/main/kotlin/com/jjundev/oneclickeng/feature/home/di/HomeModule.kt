package com.jjundev.oneclickeng.feature.home.di

import com.jjundev.oneclickeng.feature.home.FirebaseHomeAnalytics
import com.jjundev.oneclickeng.feature.home.HomeAnalytics
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 홈 seam 바인딩(M3-08). 실 디스패치(M4-01)는 [FirebaseHomeAnalytics] 로 배선한다.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {
    @Binds
    @Singleton
    abstract fun bindHomeAnalytics(impl: FirebaseHomeAnalytics): HomeAnalytics
}

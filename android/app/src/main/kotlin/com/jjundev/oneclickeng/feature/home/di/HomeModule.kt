package com.jjundev.oneclickeng.feature.home.di

import com.jjundev.oneclickeng.feature.home.HomeAnalytics
import com.jjundev.oneclickeng.feature.home.NoOpHomeAnalytics
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 홈 seam 바인딩(M3-08). 분석은 실제 디스패치(M4-01)까지 [NoOpHomeAnalytics] 로 바인딩한다
 * (OnboardingModule / WaitQuizAnalytics no-op 선례와 동일).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {
    @Binds
    @Singleton
    abstract fun bindHomeAnalytics(impl: NoOpHomeAnalytics): HomeAnalytics
}

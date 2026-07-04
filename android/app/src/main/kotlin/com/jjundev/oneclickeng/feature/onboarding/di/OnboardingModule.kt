package com.jjundev.oneclickeng.feature.onboarding.di

import com.jjundev.oneclickeng.feature.onboarding.NoOpOnboardingAnalytics
import com.jjundev.oneclickeng.feature.onboarding.OnboardingAnalytics
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 온보딩 seam 바인딩(M3-02). 분석은 실제 디스패치(M4-01)까지 [NoOpOnboardingAnalytics] 로 바인딩한다 —
 * `WaitQuizAnalytics` 의 no-op 바인딩 선례와 동일.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingModule {
    @Binds
    @Singleton
    abstract fun bindOnboardingAnalytics(impl: NoOpOnboardingAnalytics): OnboardingAnalytics
}

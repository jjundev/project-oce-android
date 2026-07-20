package com.jjundev.oneclickeng.feature.onboarding.di

import com.jjundev.oneclickeng.feature.onboarding.FirebaseOnboardingAnalytics
import com.jjundev.oneclickeng.feature.onboarding.OnboardingAnalytics
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 온보딩 seam 바인딩(M3-02). 분석은 실제 Firebase 디스패치(M4-01)로 [FirebaseOnboardingAnalytics] 를 바인딩한다.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingModule {
    @Binds
    @Singleton
    abstract fun bindOnboardingAnalytics(impl: FirebaseOnboardingAnalytics): OnboardingAnalytics
}

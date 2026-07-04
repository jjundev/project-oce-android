package com.jjundev.oneclickeng.feature.settings.di

import com.jjundev.oneclickeng.feature.settings.SettingsAnalytics
import com.jjundev.oneclickeng.feature.settings.FirebaseSettingsAnalytics
import com.jjundev.oneclickeng.feature.settings.data.CardPurgeRepository
import com.jjundev.oneclickeng.feature.settings.data.FirestoreCardPurgeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 설정 탭(M3-09) seam 바인딩. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    @Singleton
    abstract fun bindCardPurgeRepository(impl: FirestoreCardPurgeRepository): CardPurgeRepository

    @Binds
    @Singleton
    abstract fun bindSettingsAnalytics(impl: FirebaseSettingsAnalytics): SettingsAnalytics
}

package com.jjundev.oneclickeng.core.analytics

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the single analytics dispatch seam to its Firebase impl (M4-01). */
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {
    @Binds
    @Singleton
    abstract fun bindAnalyticsSink(impl: FirebaseAnalyticsSink): AnalyticsSink
}

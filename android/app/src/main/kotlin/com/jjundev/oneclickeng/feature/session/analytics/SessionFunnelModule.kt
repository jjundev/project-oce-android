package com.jjundev.oneclickeng.feature.session.analytics

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the session funnel seam to its Firebase dispatch impl (M4-01b). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SessionFunnelModule {
    @Binds
    @Singleton
    abstract fun bindSessionFunnelAnalytics(impl: FirebaseSessionFunnelAnalytics): SessionFunnelAnalytics

    @Binds
    @Singleton
    abstract fun bindSavedCardAnalytics(impl: FirebaseSavedCardAnalytics): SavedCardAnalytics
}

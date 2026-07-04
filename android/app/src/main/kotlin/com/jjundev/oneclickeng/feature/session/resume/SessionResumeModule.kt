package com.jjundev.oneclickeng.feature.session.resume

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** Distinguishes the session-resume DataStore from the gamification/tts/reminder ones (one per feature). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SessionResumePrefs

private val Context.sessionResumeDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "session_resume_prefs")

/**
 * DI for the M3-08 durable session-resume layer. [SessionSnapshotStore] and [SessionLimitHolder] are
 * constructor-@Inject Singletons — only the qualified DataStore needs a provider (GamificationModule
 * precedent).
 */
@Module
@InstallIn(SingletonComponent::class)
object SessionResumeModule {
    @Provides
    @Singleton
    @SessionResumePrefs
    fun provideSessionResumeDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.sessionResumeDataStore
}

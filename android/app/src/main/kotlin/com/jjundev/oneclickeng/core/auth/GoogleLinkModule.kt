package com.jjundev.oneclickeng.core.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Google 연결 이관 마커 전용 DataStore 한정자(M3-03). 기능별 1 DataStore 관례(ReminderModule 미러) — 기존
 * 무한정자 `DataStore<Preferences>`(tts_settings) 와의 중복 바인딩을 피한다.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GoogleLinkPrefs

private val Context.googleLinkDataStore: DataStore<Preferences> by preferencesDataStore(name = "google_link_prefs")

/** [GoogleAccountLinker]·[PendingMergeStore] seam 바인딩(M3-03). */
@Module
@InstallIn(SingletonComponent::class)
abstract class GoogleLinkBindModule {
    @Binds
    @Singleton
    abstract fun bindGoogleAccountLinker(impl: FirebaseGoogleAccountLinker): GoogleAccountLinker

    @Binds
    @Singleton
    abstract fun bindPendingMergeStore(impl: DataStorePendingMergeStore): PendingMergeStore
}

@Module
@InstallIn(SingletonComponent::class)
object GoogleLinkProvideModule {
    @Provides
    @Singleton
    @GoogleLinkPrefs
    fun provideGoogleLinkDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.googleLinkDataStore
}

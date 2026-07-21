package com.jjundev.oneclickeng.core.settings

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
 * 요약 저장 기본값 전용 DataStore 를 구분하는 한정자. `TtsProvideModule` 이 이미 무한정자
 * `DataStore<Preferences>`(tts_settings)를 제공하므로, 중복 바인딩을 피하려면 이 저장소도 한정된 인스턴스를
 * 받아야 한다(기능별 1 DataStore 관례 — `ReminderModule` 미러).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SummarySavePrefs

private val Context.summarySaveDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "summary_save_settings")

private typealias SummarySettingsImpl = DataStoreSummarySaveSettingsRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class SummarySaveSettingsBindModule {
    @Binds
    @Singleton
    abstract fun bindSummarySaveSettingsRepository(impl: SummarySettingsImpl): SummarySaveSettingsRepository
}

@Module
@InstallIn(SingletonComponent::class)
object SummarySaveSettingsProvideModule {
    @Provides
    @Singleton
    @SummarySavePrefs
    fun provideSummarySaveDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.summarySaveDataStore
}

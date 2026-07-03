package com.jjundev.oneclickeng.feature.reminder.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.jjundev.oneclickeng.feature.reminder.FirebaseReminderAnalytics
import com.jjundev.oneclickeng.feature.reminder.ReminderAnalytics
import com.jjundev.oneclickeng.feature.reminder.data.DataStoreReminderRepository
import com.jjundev.oneclickeng.feature.reminder.data.ReminderRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * 리마인더 전용 DataStore 를 구분하기 위한 한정자. TtsProvideModule 이 무한정자
 * `DataStore<Preferences>`(tts_settings)를 이미 제공하므로, 중복 바인딩을 피하려면 리마인더 저장소는
 * 한정된 인스턴스를 받아야 한다(기능별 1 DataStore 관례 — TtsModule 미러).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReminderPrefs

private val Context.reminderDataStore: DataStore<Preferences> by preferencesDataStore(name = "reminder_prefs")

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderBindModule {
    @Binds
    @Singleton
    abstract fun bindReminderRepository(impl: DataStoreReminderRepository): ReminderRepository

    @Binds
    @Singleton
    abstract fun bindReminderAnalytics(impl: FirebaseReminderAnalytics): ReminderAnalytics
}

@Module
@InstallIn(SingletonComponent::class)
object ReminderProvideModule {
    @Provides
    @Singleton
    @ReminderPrefs
    fun provideReminderDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.reminderDataStore
}

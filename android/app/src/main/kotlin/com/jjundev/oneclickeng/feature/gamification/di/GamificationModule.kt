package com.jjundev.oneclickeng.feature.gamification.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.jjundev.oneclickeng.feature.gamification.FirestoreStudytimeRepository
import com.jjundev.oneclickeng.feature.gamification.StudytimeRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Distinguishes the gamification DataStore from the tts/reminder ones (one DataStore per feature —
 * TtsModule / ReminderModule precedent).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GamificationPrefs

private val Context.gamificationDataStore: DataStore<Preferences> by preferencesDataStore(name = "gamification_prefs")

/**
 * DI for M3-05 gamification. [StudytimeRepository] is bound to the Firestore impl; [StudytimeStore]
 * and the coordinator's [CoroutineScope] are constructor-injected (StudytimeStore via @Inject,
 * the app scope via TtsProvideModule.provideAppScope). SessionTurnBufferStore stays auto-@Inject —
 * no binding here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class GamificationBindModule {
    @Binds
    @Singleton
    abstract fun bindStudytimeRepository(impl: FirestoreStudytimeRepository): StudytimeRepository
}

@Module
@InstallIn(SingletonComponent::class)
object GamificationProvideModule {
    @Provides
    @Singleton
    @GamificationPrefs
    fun provideGamificationDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.gamificationDataStore
}

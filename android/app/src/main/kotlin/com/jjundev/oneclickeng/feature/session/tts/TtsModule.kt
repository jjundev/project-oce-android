package com.jjundev.oneclickeng.feature.session.tts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.jjundev.oneclickeng.core.audio.PcmAudioPlayer
import com.jjundev.oneclickeng.core.audio.PcmPlayer
import com.jjundev.oneclickeng.core.settings.DataStoreTtsSettingsRepository
import com.jjundev.oneclickeng.core.settings.TtsSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

private val Context.ttsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tts_settings")

/** Binds the TTS seams and provides the DataStore + app coroutine scope (M1-05). */
@Module
@InstallIn(SingletonComponent::class)
abstract class TtsBindModule {
    @Binds
    @Singleton
    abstract fun bindPcmPlayer(impl: PcmAudioPlayer): PcmPlayer

    @Binds
    @Singleton
    abstract fun bindDeviceTts(impl: AndroidDeviceTts): DeviceTts

    @Binds
    @Singleton
    abstract fun bindTtsSettingsRepository(impl: DataStoreTtsSettingsRepository): TtsSettingsRepository
}

@Module
@InstallIn(SingletonComponent::class)
object TtsProvideModule {
    @Provides
    @Singleton
    fun provideTtsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.ttsDataStore

    // Main.immediate: coordinator state is UI-facing; suspend calls hop dispatchers internally.
    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}

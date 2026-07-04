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
 * 설정 전용 DataStore 한정자(M3-09). "삭제 진행 중" 플래그([AccountStateStore])를 담으며, 로그아웃/삭제가
 * 비우는 다른 기능 DataStore 들([LocalStateCleaner])과 **분리**돼 cold-start 재개 신호를 보존한다. 기능별 1
 * DataStore 관례(GoogleLinkModule 미러).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsPrefs

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")

/** [AccountRepository] seam 바인딩(M3-09). LocalStateCleaner·AccountStateStore·AccountResetBus 는 @Inject concrete. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AccountBindModule {
    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: FirebaseAccountRepository): AccountRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AccountProvideModule {
    @Provides
    @Singleton
    @SettingsPrefs
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.settingsDataStore
}

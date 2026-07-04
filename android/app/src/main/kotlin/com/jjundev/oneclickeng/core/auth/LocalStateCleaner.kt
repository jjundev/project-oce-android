package com.jjundev.oneclickeng.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.jjundev.oneclickeng.feature.gamification.di.GamificationPrefs
import com.jjundev.oneclickeng.feature.reminder.di.ReminderPrefs
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wipes all per-feature local state on logout / account deletion (M3-09, settings §8.2/§8.3): the
 * unqualified TTS DataStore plus the reminder, gamification, and google-link (`pendingGuestMerge`)
 * DataStores. Clearing google_link_prefs here is what drops any stale pending-merge marker during a
 * deletion.
 *
 * **Deliberately does NOT touch `settings_prefs`** ([AccountStateStore]) — the "삭제 진행 중" flag must
 * survive a full clear so a cold-start can resume an interrupted deletion.
 */
@Singleton
class LocalStateCleaner
    @Inject
    constructor(
        private val ttsDataStore: DataStore<Preferences>,
        @ReminderPrefs private val reminderDataStore: DataStore<Preferences>,
        @GamificationPrefs private val gamificationDataStore: DataStore<Preferences>,
        @GoogleLinkPrefs private val googleLinkDataStore: DataStore<Preferences>,
    ) {
        /** Clear every feature DataStore (excluding settings_prefs / AccountStateStore). */
        suspend fun clearAll() {
            ttsDataStore.edit { it.clear() }
            reminderDataStore.edit { it.clear() }
            gamificationDataStore.edit { it.clear() }
            googleLinkDataStore.edit { it.clear() }
        }
    }

package com.jjundev.oneclickeng.feature.settings

import com.jjundev.oneclickeng.core.auth.AccountRepository
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.core.auth.PendingMerge
import com.jjundev.oneclickeng.core.auth.PendingMergeStore
import com.jjundev.oneclickeng.core.auth.ProfileRepository
import com.jjundev.oneclickeng.core.settings.FakeSummarySaveSettingsRepository
import com.jjundev.oneclickeng.core.settings.SummarySaveSettingsRepository
import com.jjundev.oneclickeng.core.settings.TtsQuality
import com.jjundev.oneclickeng.core.settings.TtsSettings
import com.jjundev.oneclickeng.core.settings.TtsSettingsRepository
import com.jjundev.oneclickeng.feature.gamification.AccrualSnapshot
import com.jjundev.oneclickeng.feature.gamification.StudytimeRepository
import com.jjundev.oneclickeng.feature.reminder.ReminderOrchestrator
import com.jjundev.oneclickeng.feature.reminder.ReminderPromptDecision
import com.jjundev.oneclickeng.feature.reminder.ReminderRunResult
import com.jjundev.oneclickeng.feature.reminder.data.ReminderConfig
import com.jjundev.oneclickeng.feature.settings.data.CardPurgeRepository
import com.jjundev.oneclickeng.feature.settings.data.PurgeScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * [SettingsViewModel.resetMetrics] 성공/실패 분기 고정. 레포 관례 = mockk 미사용 → 손수 만든 fake.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `resetMetrics success shows MetricsReset and logs metrics_reset once`() =
        runTest {
            val studytime = FakeStudytimeRepository()
            val analytics = RecordingSettingsAnalytics()
            val model = settingsViewModel(studytime, analytics)
            advanceUntilIdle()

            model.resetMetrics()
            advanceUntilIdle()

            assertEquals(SettingsMessage.MetricsReset, model.uiState.value.message)
            assertEquals(false, model.uiState.value.metricsResetInFlight)
            assertEquals(1, analytics.metricsResetCount)
            assertEquals(1, studytime.resetCalls)
        }

    @Test
    fun `resetMetrics failure shows MetricsResetFailed and does not log metrics_reset`() =
        runTest {
            val studytime = FakeStudytimeRepository(failReset = true)
            val analytics = RecordingSettingsAnalytics()
            val model = settingsViewModel(studytime, analytics)
            advanceUntilIdle()

            model.resetMetrics()
            advanceUntilIdle()

            assertEquals(SettingsMessage.MetricsResetFailed, model.uiState.value.message)
            assertEquals(false, model.uiState.value.metricsResetInFlight)
            assertEquals(0, analytics.metricsResetCount)
            assertEquals(1, studytime.resetCalls)
        }

    @Test
    fun `onSummarySaveDefaultChange persists the toggle, updates state and logs once`() =
        runTest {
            val saveSettings = FakeSummarySaveSettingsRepository()
            val analytics = RecordingSettingsAnalytics()
            val model = settingsViewModel(FakeStudytimeRepository(), analytics, summarySaveSettings = saveSettings)
            advanceUntilIdle()
            assertEquals(false, model.uiState.value.summarySaveByDefault)

            model.onSummarySaveDefaultChange(true)
            advanceUntilIdle()

            assertEquals(true, model.uiState.value.summarySaveByDefault)
            assertEquals(true, saveSettings.currentValue())
            assertEquals(1, analytics.summarySaveDefaultToggledCount)
        }

    private fun settingsViewModel(
        studytimeRepository: StudytimeRepository,
        analytics: SettingsAnalytics,
        summarySaveSettings: SummarySaveSettingsRepository = FakeSummarySaveSettingsRepository(),
    ) = SettingsViewModel(
        authRepository = FakeAuth,
        profileRepository = FakeProfile,
        ttsSettings = FakeTtsSettingsRepository(),
        reminderOrchestrator = FakeReminderOrchestrator(),
        studytimeRepository = studytimeRepository,
        cardPurgeRepository = object : CardPurgeRepository {
            override suspend fun count(scope: PurgeScope): Int = 0
            override suspend fun purge(scope: PurgeScope): Int = 0
        },
        accountRepository = FakeAccount,
        pendingMergeStore = FakePendingMergeStore(),
        summarySaveSettings = summarySaveSettings,
        analytics = analytics,
    )
}

private object FakeAuth : AuthRepository {
    override val currentUid: String? = "uid"

    override suspend fun ensureSignedIn(): String = "uid"
}

private object FakeProfile : ProfileRepository {
    override suspend fun ensureProfile(uid: String) = Unit

    override suspend fun saveLevel(
        uid: String,
        level: String,
    ) = Unit

    override suspend fun readLevel(uid: String): String = "easy"

    override suspend fun saveNickname(
        uid: String,
        nickname: String,
    ) = Unit

    override suspend fun readNickname(uid: String): String? = null
}

private object FakeAccount : AccountRepository {
    override fun isGuest(): Boolean = false

    override suspend fun signOut() = Unit

    override suspend fun deleteAccount() = Unit

    override suspend fun completePendingDeletion(): Boolean = false
}

private class FakePendingMergeStore : PendingMergeStore {
    override suspend fun get(): PendingMerge? = null

    override suspend fun put(
        guestUid: String,
        guestToken: String,
    ) = Unit

    override suspend fun setTargetUid(targetUid: String) = Unit

    override suspend fun clear() = Unit
}

private class FakeTtsSettingsRepository : TtsSettingsRepository {
    override val settings: Flow<TtsSettings> = MutableStateFlow(TtsSettings())

    override suspend fun current(): TtsSettings = TtsSettings()

    override suspend fun setQuality(quality: TtsQuality) = Unit

    override suspend fun setSpeechRate(rate: Float) = Unit

    override suspend fun setMuted(muted: Boolean) = Unit
}

private class FakeReminderOrchestrator : ReminderOrchestrator {
    override val config: Flow<ReminderConfig> = MutableStateFlow(ReminderConfig.DISABLED)

    override suspend fun evaluateOptInPrompt(): ReminderPromptDecision = ReminderPromptDecision.DoNotShow

    override suspend fun acceptOptIn() = Unit

    override suspend fun dismissOptIn() = Unit

    override suspend fun enableReminder() = Unit

    override suspend fun disableReminder() = Unit

    override suspend fun setReminderTime(
        hour: Int,
        minute: Int,
    ) = Unit

    override suspend fun markPermissionAsked() = Unit

    override suspend fun repairSchedule() = Unit

    override suspend fun handleTimezoneChanged() = Unit

    override suspend fun runDueReminder(): ReminderRunResult = ReminderRunResult.DisabledNoOp

    override suspend fun recordSessionCompleted(
        streak: Int,
        lastStudyDate: LocalDate,
    ) = Unit

    override suspend fun recordSavedReviewText(text: String) = Unit

    override suspend fun clearProgressCache() = Unit
}

private class RecordingSettingsAnalytics : SettingsAnalytics {
    var metricsResetCount = 0
        private set
    var summarySaveDefaultToggledCount = 0
        private set

    override fun ttsQualityChanged(provider: String) = Unit

    override fun ttsSpeedChanged(speed: Float) = Unit

    override fun muteToggled(muted: Boolean) = Unit

    override fun metricsReset() {
        metricsResetCount++
    }

    override fun cardsPurged(
        scope: String,
        count: Int,
    ) = Unit

    override fun accountDeleted() = Unit

    override fun logout() = Unit

    override fun summarySaveDefaultToggled(enabled: Boolean) {
        summarySaveDefaultToggledCount++
    }
}

private class FakeStudytimeRepository(private val failReset: Boolean = false) : StudytimeRepository {
    var resetCalls = 0
        private set

    override suspend fun recordSession(
        sessionId: String,
        elapsedSeconds: Long,
        dayKey: String,
    ): AccrualSnapshot = AccrualSnapshot(todaySeconds = 0, streak = 0, todaySecondsBefore = 0, streakStatic = false)

    override suspend fun seedFromServerIfEmpty() = Unit

    override suspend fun drain() = Unit

    override suspend fun reconcileAfterMerge() = Unit

    override suspend fun resetMetrics() {
        resetCalls++
        if (failReset) error("resetMetrics callable failed")
    }
}

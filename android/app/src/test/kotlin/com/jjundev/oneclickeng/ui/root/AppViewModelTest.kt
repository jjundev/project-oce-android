package com.jjundev.oneclickeng.ui.root

import com.jjundev.oneclickeng.core.auth.AccountRepository
import com.jjundev.oneclickeng.core.auth.AccountResetBus
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.core.auth.GoogleAccountLinker
import com.jjundev.oneclickeng.core.auth.LinkOutcome
import com.jjundev.oneclickeng.core.auth.ProfileRepository
import com.jjundev.oneclickeng.core.connectivity.Connectivity
import com.jjundev.oneclickeng.core.connectivity.ConnectivityObserver
import com.jjundev.oneclickeng.core.connectivity.OfflineAnalytics
import com.jjundev.oneclickeng.feature.gamification.AccrualSnapshot
import com.jjundev.oneclickeng.feature.gamification.StudytimeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * [AppViewModel.observeConnectivity] 재접속 훅 검증(M4-04, 결정 9b·11). offline→online 전이에서만 studytime
 * write-ahead 큐를 재드레인하고, 모든 전이에서 `connectivity_changed` 를 계측하는지 고정한다. 부트스트랩
 * seed/drain(앱 시작 시 1회)과 전이 드레인을 구분하려 드레인 횟수의 델타로 단언한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `offline to online transition re-drains the WAQ and logs connectivity_changed`() =
        runTest {
            val studytime = RecordingStudytime()
            val offlineAnalytics = RecordingOfflineAnalytics()
            val connectivity = MutableConnectivity(Connectivity.Offline)
            appViewModel(studytime, connectivity, offlineAnalytics)
            advanceUntilIdle()
            val baseline = studytime.drainCount // 부트스트랩 seed/drain 이 이미 1회 호출

            connectivity.set(Connectivity.Online)
            advanceUntilIdle()

            assertEquals(baseline + 1, studytime.drainCount) // 재접속 훅이 정확히 1회 추가 드레인
            assertEquals(listOf(true), offlineAnalytics.transitions) // connectivity_changed(online=true)
        }

    @Test
    fun `online to offline transition logs connectivity_changed but does not drain`() =
        runTest {
            val studytime = RecordingStudytime()
            val offlineAnalytics = RecordingOfflineAnalytics()
            val connectivity = MutableConnectivity(Connectivity.Online)
            appViewModel(studytime, connectivity, offlineAnalytics)
            advanceUntilIdle()
            val baseline = studytime.drainCount

            connectivity.set(Connectivity.Offline)
            advanceUntilIdle()

            assertEquals(baseline, studytime.drainCount) // online→offline 은 드레인하지 않음
            assertEquals(listOf(false), offlineAnalytics.transitions) // connectivity_changed(online=false)
        }

    private fun appViewModel(
        studytime: StudytimeRepository,
        connectivity: ConnectivityObserver,
        offlineAnalytics: OfflineAnalytics,
    ) = AppViewModel(
        authRepository = FakeAuth,
        profileRepository = FakeProfile,
        googleAccountLinker = FakeLinker,
        studytimeRepository = studytime,
        accountRepository = FakeAccount,
        accountResetBus = AccountResetBus(),
        connectivity = connectivity,
        offlineAnalytics = offlineAnalytics,
    )
}

private object FakeAccount : AccountRepository {
    override fun isGuest(): Boolean = false

    override suspend fun signOut() = Unit

    override suspend fun deleteAccount() = Unit

    override suspend fun completePendingDeletion(): Boolean = false
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

private object FakeLinker : GoogleAccountLinker {
    override suspend fun linkGuest(googleIdToken: String): LinkOutcome = LinkOutcome.Merged

    override suspend fun retryPendingMerge(): LinkOutcome = LinkOutcome.Merged
}

private class RecordingStudytime : StudytimeRepository {
    var drainCount = 0
        private set

    override suspend fun recordSession(
        sessionId: String,
        elapsedSeconds: Long,
        dayKey: String,
    ): AccrualSnapshot = AccrualSnapshot(todaySeconds = 0, streak = 0, todaySecondsBefore = 0, streakStatic = false)

    override suspend fun seedFromServerIfEmpty() = Unit

    override suspend fun drain() {
        drainCount++
    }

    override suspend fun resetMetrics() = Unit
}

private class RecordingOfflineAnalytics : OfflineAnalytics {
    val transitions = mutableListOf<Boolean>()

    override fun connectivityChanged(online: Boolean) {
        transitions += online
    }

    override fun offlineBlocked(surface: String) = Unit
}

private class MutableConnectivity(initial: Connectivity) : ConnectivityObserver {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<Connectivity> = _state

    fun set(value: Connectivity) {
        _state.value = value
    }
}

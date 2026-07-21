package com.jjundev.oneclickeng.ui.root

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjundev.oneclickeng.core.auth.AccountRepository
import com.jjundev.oneclickeng.core.auth.AccountResetBus
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.core.auth.GoogleAccountLinker
import com.jjundev.oneclickeng.core.auth.ProfileRepository
import com.jjundev.oneclickeng.core.connectivity.Connectivity
import com.jjundev.oneclickeng.core.connectivity.ConnectivityObserver
import com.jjundev.oneclickeng.core.connectivity.OfflineAnalytics
import com.jjundev.oneclickeng.feature.gamification.StudytimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-entry bootstrap + onboarding gate (M3-01/M3-02). Instantiated once at [AppRoot]; its `init`
 * starts the guest session in the background — anonymous sign-in (FR-1, no login screen) then
 * profile creation — and then resolves [uiState] by reading `profile.level`.
 *
 * The gate ([uiState]) exists because the start destination is decided asynchronously: [AppRoot]
 * cannot compose its NavHost until we know whether this user still needs onboarding, and Compose
 * Navigation freezes `startDestination` at first composition. So [AppRoot] renders a splash while
 * [uiState] is [BootState.Loading] and only composes the NavHost once it resolves to
 * [BootState.NeedsOnboarding] or [BootState.MainReady].
 *
 * Routing signal is Firestore `profile.level` alone (no local mirror): an absent/unreadable level
 * fails open to onboarding (the correct behavior for a fresh install, whose profile has no level
 * yet), and a returning user resolves their level from Firestore's default on-disk cache even
 * offline. A fresh-install launch can fail at anonymous sign-in itself (AuthRepository throws); that
 * failure resolves to [BootState.AuthFailed] so the root can show the existing retry gate instead of
 * leaving the user on an indeterminate splash.
 *
 * Scoped to the Activity's ViewModelStore, so bootstrap fires once per process and survives
 * configuration changes (no re-sign-in on rotation). `ensureSignedIn`/`ensureProfile` are both
 * no-ops when the session/profile already exist, so re-running on the next launch is cheap and safe.
 */
@Suppress("LongParameterList")
@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val profileRepository: ProfileRepository,
        private val googleAccountLinker: GoogleAccountLinker,
        private val studytimeRepository: StudytimeRepository,
        private val accountRepository: AccountRepository,
        accountResetBus: AccountResetBus,
        private val connectivity: ConnectivityObserver,
        private val offlineAnalytics: OfflineAnalytics,
        private val analytics: com.jjundev.oneclickeng.core.analytics.AnalyticsSink,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<BootState>(BootState.Loading)
        val uiState: StateFlow<BootState> = _uiState.asStateFlow()

        /**
         * 글로벌 오프라인 배너(C4)용 앱 스코프 연결 상태(M3-08, H7/P8). M4-04 [ConnectivityObserver] 를 단일
         * 연결성 소스로 삼아 Boolean 으로 파생한다(M3-08 의 별도 ConnectivityMonitor 는 이 병합에서 폐기).
         */
        val isOnline: StateFlow<Boolean> =
            connectivity.state
                .map { it == Connectivity.Online }
                .stateIn(viewModelScope, SharingStarted.Eagerly, connectivity.state.value == Connectivity.Online)

        init {
            viewModelScope.launch { bootstrap() }
            // Logout / account-deletion (M3-09) re-run bootstrap through here: reset the gate to Loading
            // (which drops the outer NavHost, AppRoot.kt) then re-bootstrap → fresh anonymous guest →
            // onboarding. The collect serializes overlapping resets. The bus emits ONLY on explicit
            // signOut/delete, never on the anonymous re-sign-in bootstrap itself (no re-entrancy loop).
            viewModelScope.launch {
                accountResetBus.events.collect {
                    _uiState.value = BootState.Loading
                    bootstrap()
                }
            }
        }

        /** Retries an explicit anonymous-auth bootstrap failure from the root blocking gate. */
        fun retryBootstrap() {
            if (_uiState.value != BootState.AuthFailed) return
            _uiState.value = BootState.Loading
            viewModelScope.launch { bootstrap() }
        }

        /**
         * App-entry bootstrap, re-runnable (init + [AccountResetBus] reset). Resumes an interrupted
         * account deletion FIRST (precedence over guest-merge resume: a to-be-deleted identity must not be
         * merged into), then signs in / gates on `profile.level`.
         */
        private suspend fun bootstrap() {
            // (0) Resume a pending deletion before anything else. Never throws; true = deletion in
            //     progress → skip guest-merge resume this pass (routes to a fresh guest once torn down).
            val resumedDelete = accountRepository.completePendingDeletion()

            runCatching {
                val uid = authRepository.ensureSignedIn()
                if (!resumedDelete) {
                    // 중도 종료된 게스트 이관(FR-3b) 복구: target 재로그인 + 유효 마커면 mergeGuestData 재시도.
                    // ensureProfile/readLevel 이전에 await 해 부트 게이트가 post-merge 상태를 관측하게 한다(결정 A8).
                    // 실패/무관은 no-op 로 삼켜 부트를 막지 않는다(마커는 다음 실행에서 재시도).
                    runCatching { googleAccountLinker.retryPendingMerge() }
                }
                // Identity as early as the first custom event (§3a): after any resumed merge so the
                // effective identity is stitched, before profile reads that could emit.
                analytics.setUserId(authRepository.currentUid ?: uid)
                analytics.setUserProperty("auth_state", authStateFor(authRepository.isAnonymous))
                profileRepository.ensureProfile(uid)
                profileRepository.readLevel(uid)
            }.onSuccess { level ->
                analytics.setUserProperty("level", level)
                _uiState.value = bootStateForLevel(level)
            }.onFailure {
                // Anonymous Auth can be disabled in the Firebase console or a first launch can be
                // offline. Surface a retryable gate; leaving this as Loading strands the user on
                // the splash forever and prevents the next `/llm` call from being reachable.
                _uiState.value = BootState.AuthFailed
                runCatching { Log.w(TAG, "Guest bootstrap failed — showing retry gate", it) }
            }

            // Gamification studytime seed/drain (M3-05). Sequenced after sign-in (both no-op without
            // a uid) but in its OWN runCatching so a gamification hiccup is never swallowed by — nor
            // swallows — the auth/profile bootstrap above. Retries on the next launch.
            runCatching {
                studytimeRepository.seedFromServerIfEmpty()
                studytimeRepository.drain()
            }.onFailure {
                Log.w(TAG, "Gamification seed/drain failed — retries on next launch", it)
            }

            observeConnectivity()
        }

        /**
         * 연결성 전이 관측(M4-04): (1) `connectivity_changed` 계측, (2) offline→online 복귀 시 studytime
         * write-ahead 큐를 재드레인 — 프로세스 재시작 없이 연결이 돌아와도 재동기화한다(재접속 훅). 초기값은
         * [drop] 로 건너뛰어 전이(edge)만 다룬다. `distinctUntilChanged` 소스라 emit 은 실제 전이에서만 온다.
         * saved_cards/point_ledger 는 Firestore SDK 가 자동 재생하므로 여기 훅은 studytime WAQ 전용이다.
         */
        private fun observeConnectivity() {
            viewModelScope.launch {
                var prev = connectivity.state.value
                connectivity.state.drop(1).collect { current ->
                    offlineAnalytics.connectivityChanged(online = current == Connectivity.Online)
                    if (prev == Connectivity.Offline && current == Connectivity.Online) {
                        runCatching { studytimeRepository.drain() }
                            .onFailure { Log.w(TAG, "reconnect drain failed — retries on next transition", it) }
                    }
                    prev = current
                }
            }
        }

        private companion object {
            const val TAG = "AppViewModel"
        }
    }

/**
 * Pure routing decision for the app-entry gate (M3-02): an absent/blank `profile.level` means the
 * user has not onboarded → [BootState.NeedsOnboarding]; a saved level → [BootState.MainReady]. Kept
 * side-effect-free so the gate rule is unit-testable without Firebase.
 */
internal fun bootStateForLevel(level: String?): BootState =
    if (level.isNullOrBlank()) BootState.NeedsOnboarding else BootState.MainReady

/** guest|linked auth_state for the analytics user property (analytics-events.md §3). */
internal fun authStateFor(isAnonymous: Boolean): String = if (isAnonymous) "guest" else "linked"

/**
 * App-entry routing state resolved once per process after guest bootstrap (M3-02).
 *
 * [Loading] gates NavHost composition (splash); the two resolved states pick the outer NavHost
 * start destination — onboarding vs the 3-tab shell.
 */
sealed interface BootState {
    /** Bootstrap in flight — [AppRoot] shows a splash. */
    data object Loading : BootState

    /** Anonymous bootstrap failed — [AppRoot] shows the existing auth retry gate. */
    data object AuthFailed : BootState

    /** Signed in, but `profile.level` is absent → run onboarding (M3-02). */
    data object NeedsOnboarding : BootState

    /** Signed in with a saved `profile.level` → go straight to the 3-tab shell. */
    data object MainReady : BootState
}

package com.jjundev.oneclickeng.ui.root

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.core.auth.GoogleAccountLinker
import com.jjundev.oneclickeng.core.auth.ProfileRepository
import com.jjundev.oneclickeng.core.network.ConnectivityMonitor
import com.jjundev.oneclickeng.feature.gamification.StudytimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * offline. A fresh-install *offline* launch fails at anonymous sign-in itself (AuthRepository throws)
 * and stays [BootState.Loading] — that retry surface is handled before the gate, so the cache-miss
 * edge never reaches level resolution.
 *
 * Scoped to the Activity's ViewModelStore, so bootstrap fires once per process and survives
 * configuration changes (no re-sign-in on rotation). `ensureSignedIn`/`ensureProfile` are both
 * no-ops when the session/profile already exist, so re-running on the next launch is cheap and safe.
 */
@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val profileRepository: ProfileRepository,
        private val googleAccountLinker: GoogleAccountLinker,
        private val studytimeRepository: StudytimeRepository,
        connectivityMonitor: ConnectivityMonitor,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<BootState>(BootState.Loading)
        val uiState: StateFlow<BootState> = _uiState.asStateFlow()

        /** 글로벌 오프라인 배너(C4)용 앱 스코프 연결 상태(M3-08, H7/P8). */
        val isOnline: StateFlow<Boolean> = connectivityMonitor.isOnline

        init {
            viewModelScope.launch {
                runCatching {
                    val uid = authRepository.ensureSignedIn()
                    // 중도 종료된 게스트 이관(FR-3b) 복구: target 재로그인 상태 + 유효 마커면 mergeGuestData 재시도.
                    // ensureProfile/readLevel 이전에 await 해 부트 게이트가 post-merge 상태를 관측하게 한다(결정 A8).
                    // 실패/무관은 no-op 로 삼켜 부트를 막지 않는다(마커는 다음 실행에서 재시도).
                    runCatching { googleAccountLinker.retryPendingMerge() }
                    profileRepository.ensureProfile(uid)
                    profileRepository.readLevel(uid)
                }.onSuccess { level ->
                    _uiState.value = bootStateForLevel(level)
                }.onFailure {
                    // Stay Loading: the next launch re-runs this bootstrap, and the next `/llm` call
                    // re-attempts sign-in lazily via the token provider. (A dedicated auth-failure
                    // retry surface is a follow-up seam — OneClickBlockingGate/Auth already exists.)
                    Log.w(TAG, "Guest bootstrap failed — retries on next launch or /llm call", it)
                }

                // Gamification studytime seed/drain (M3-05). Sequenced after sign-in (both no-op without
                // a uid) but in its OWN runCatching so a gamification hiccup is never swallowed by — nor
                // swallows — the auth/profile bootstrap above. Retries on the next launch.
                runCatching {
                    studytimeRepository.seedFromServerIfEmpty()
                    studytimeRepository.drainOnStart()
                }.onFailure {
                    Log.w(TAG, "Gamification seed/drain failed — retries on next launch", it)
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

/**
 * App-entry routing state resolved once per process after guest bootstrap (M3-02).
 *
 * [Loading] gates NavHost composition (splash); the two resolved states pick the outer NavHost
 * start destination — onboarding vs the 3-tab shell.
 */
sealed interface BootState {
    /** Bootstrap in flight (or failed and awaiting retry) — [AppRoot] shows a splash. */
    data object Loading : BootState

    /** Signed in, but `profile.level` is absent → run onboarding (M3-02). */
    data object NeedsOnboarding : BootState

    /** Signed in with a saved `profile.level` → go straight to the 3-tab shell. */
    data object MainReady : BootState
}

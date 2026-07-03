package com.jjundev.oneclickeng.ui.root

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.core.auth.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-root bootstrap (M3-01). Instantiated once at [AppRoot]; its `init` starts the guest
 * session in the background — anonymous sign-in (FR-1, no login screen) then profile
 * creation — without gating the UI, which renders immediately.
 *
 * Scoped to the Activity's ViewModelStore, so bootstrap fires once per process and
 * survives configuration changes (no re-sign-in on rotation). A failure is logged and
 * left to recover naturally: the next launch re-runs this bootstrap, and the next `/llm`
 * call re-attempts sign-in lazily via the token provider. `ensureSignedIn`/`ensureProfile`
 * are both no-ops when the session/profile already exist, so re-running is cheap and safe.
 */
@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val profileRepository: ProfileRepository,
    ) : ViewModel() {
        init {
            viewModelScope.launch {
                runCatching {
                    val uid = authRepository.ensureSignedIn()
                    profileRepository.ensureProfile(uid)
                }.onFailure {
                    Log.w(TAG, "Guest bootstrap failed — retries on next launch or /llm call", it)
                }
            }
        }

        private companion object {
            const val TAG = "AppViewModel"
        }
    }

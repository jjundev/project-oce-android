package com.jjundev.oneclickeng.ui.root

import androidx.lifecycle.ViewModel
import com.jjundev.oneclickeng.core.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Read-only account state for the splash shown before the update gate completes. Unlike
 * [AppViewModel], this ViewModel deliberately has no bootstrap side effects: reading
 * [isAnonymous] only inspects Firebase's persisted current-user state.
 */
@HiltViewModel
class BootAuthStateViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        /** Anonymous and unauthenticated states use the guest loading copy. */
        val isAnonymous: Boolean
            get() = authRepository.isAnonymous
    }

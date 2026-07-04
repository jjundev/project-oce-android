package com.jjundev.oneclickeng.core.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-identity reset signal (M3-09). Logout / account-deletion happen in a settings-scoped
 * [AccountRepository], but the app-entry gate ([com.jjundev.oneclickeng.ui.root.AppViewModel]) owns
 * re-bootstrap. This singleton bridges them without a FirebaseAuth listener (an explicit event avoids
 * re-entrancy from the anonymous re-sign-in that bootstrap itself performs).
 *
 * `extraBufferCapacity = 1` so a [signal] issued while the collector is momentarily busy is buffered,
 * not dropped. AppViewModel collects [events] in its viewModelScope and re-runs bootstrap on each.
 */
@Singleton
class AccountResetBus
    @Inject
    constructor() {
        private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val events: SharedFlow<Unit> = _events.asSharedFlow()

        /** Emit a reset request. Non-suspending; safe to call from repository coroutines. */
        fun signal() {
            _events.tryEmit(Unit)
        }
    }

package com.jjundev.oneclickeng.feature.session.resume

import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenState
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenerationCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-scoped "fresh remaining" mirror for the home at-limit notice (M3-08, H6 / daily-limit-ux §6).
 *
 * `remaining` is only ever known transiently — it rides the generation coordinator's state
 * ([DialogueGenState.Ready.remaining] / [DialogueGenState.QuotaBlocked.remaining]); there is no ambient
 * count at rest. This holder observes that state and records the latest value **for this process
 * lifetime only**. A cold launch starts at [freshRemaining] = null → home suppresses the notice
 * ("unknown → suppress"); only after a generation attempt this foreground does a `remaining==0` surface
 * the notice. That in-memory-only scope is exactly the "fresh (현재 포그라운드) value" the SoT requires.
 *
 * Instantiated when the home screen's ViewModel first injects it (home is always the boot destination),
 * so the collector is live before any CTA → generation attempt.
 */
@Singleton
class SessionLimitHolder
    @Inject
    constructor(
        coordinator: DialogueGenerationCoordinator,
        appScope: CoroutineScope,
    ) {
        private val _freshRemaining = MutableStateFlow<Int?>(null)

        /** Latest server `remaining` seen this process, or null when none observed yet (cold). */
        val freshRemaining: StateFlow<Int?> = _freshRemaining.asStateFlow()

        init {
            appScope.launch {
                coordinator.state.collect { state ->
                    when (state) {
                        is DialogueGenState.Ready -> state.remaining?.let { _freshRemaining.value = it }
                        is DialogueGenState.QuotaBlocked -> _freshRemaining.value = state.remaining
                        else -> Unit
                    }
                }
            }
        }
    }

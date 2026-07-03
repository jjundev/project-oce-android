package com.jjundev.oneclickeng.feature.session.dialogue

import com.jjundev.oneclickeng.core.network.DialogueMeta
import com.jjundev.oneclickeng.core.network.DialogueTurn

/**
 * UI state axis of dialogue-script generation (M1-01). The 1000ms delay gate that decides whether
 * WaitQuiz appears is owned by the consuming screen, NOT this state.
 */
sealed interface DialogueGenState {
    /** No generation started (initial). */
    data object Idle : DialogueGenState

    /** Streaming, but no completed turn has arrived yet (WaitQuiz-eligible surface). */
    data object Generating : DialogueGenState

    /**
     * At least one completed turn received — "ready" (loading-quiz-interstitial.md §5). Sticky: once
     * reached, a late error/stream close does NOT tear it down (already-rendered turns are usable).
     * [turns] accumulates in arrival order for M1-03 to render; [sessionId] feeds the turn loop.
     */
    data class Ready(
        val sessionId: String?,
        val remaining: Int?,
        val meta: DialogueMeta?,
        val turns: List<DialogueTurn>,
    ) : DialogueGenState

    /** Generation failed before producing any turn (error, premature close, or idle watchdog). */
    data object Failed : DialogueGenState
}

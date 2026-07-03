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

    /**
     * 일일 무료 세션 한도 도달로 서버가 시작을 거부한 종료 상태(M3-04). [Failed] 와 분리한 이유: 실패는
     * 재시도 어포던스를 주지만 한도는 재시도가 아니라 중립 한도 패널(비상업 문구)로 분기해야 한다.
     * [DialogueEvent.QuotaExceeded] 수신 시에만, 그리고 아직 [Ready] 가 아닐 때만 진입한다(Ready 이후엔
     * 이미 렌더된 대본이 우선 — sticky). [remaining] 은 거부 시 상수 0이며 표시엔 쓰지 않는다(비숫자 정본).
     */
    data class QuotaBlocked(val remaining: Int) : DialogueGenState
}

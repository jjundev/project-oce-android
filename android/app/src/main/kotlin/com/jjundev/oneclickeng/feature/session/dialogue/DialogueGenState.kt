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
        val streamStatus: DialogueStreamStatus = DialogueStreamStatus.Streaming,
    ) : DialogueGenState

    /** Generation failed before producing any turn (error, premature close, or idle watchdog). */
    data object Failed : DialogueGenState

    /**
     * 오프라인이라 새 세션(핵심 루프)을 시작할 수 없는 종료 상태(M4-04, exception-states.md 결정 #4·#5). [Failed]
     * 와 분리한 이유: 일반 실패는 인라인 재시도[A] + 일반 카피지만, 오프라인은 차단 게이트[C] + 오프라인 카피
     * 로 분기해야 한다(신호 분리, exception-states.md:56-59). 두 진입: (1) pre-flight — [DialogueGenerationViewModel]
     * 이 시작 전 [com.jjundev.oneclickeng.core.connectivity.ConnectivityObserver.isOffline] 로 게이트, (2) in-flight
     * — [DialogueGenerationCoordinator.fail] 이 실패 시점 오프라인이면 [Failed] 대신 이 상태로 분류.
     */
    data object OfflineBlocked : DialogueGenState

    /**
     * 일일 무료 세션 한도 도달로 서버가 시작을 거부한 종료 상태(M3-04). [Failed] 와 분리한 이유: 실패는
     * 재시도 어포던스를 주지만 한도는 재시도가 아니라 중립 한도 패널(비상업 문구)로 분기해야 한다.
     * [DialogueEvent.QuotaExceeded] 수신 시에만, 그리고 아직 [Ready] 가 아닐 때만 진입한다(Ready 이후엔
     * 이미 렌더된 대본이 우선 — sticky). [remaining] 은 거부 시 상수 0이며 표시엔 쓰지 않는다(비숫자 정본).
     */
    data class QuotaBlocked(val remaining: Int) : DialogueGenState
}

/**
 * Terminal status of the dialogue stream after the first renderable turn.
 * [Ready] stays sticky for already-rendered content, while generated-session handoff still needs to know
 * whether an unpaired final opponent line can be treated as a real closing turn.
 */
enum class DialogueStreamStatus {
    Streaming,
    Done,
    FailedAfterReady,
}

/**
 * Result of [DialogueGenerationCoordinator.start] (M4-04). [OfflineGated] = pre-flight 오프라인이라 스트림을
 * 열지 않고 [DialogueGenState.OfflineBlocked] 로 막힘(호출자는 퀴즈 스킵·`offline_blocked_action` 계측만
 * 분기). [Started] = 정상 시작.
 */
enum class StartOutcome {
    Started,
    OfflineGated,
}

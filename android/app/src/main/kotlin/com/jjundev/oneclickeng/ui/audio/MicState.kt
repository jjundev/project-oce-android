package com.jjundev.oneclickeng.ui.audio

/**
 * 음성 4상태 마이크(I1)의 **정착(settled) 상태축**(M1-08). core/audio 의 예약 주석
 * ([com.jjundev.oneclickeng.core.audio.RecordingState] 등)이 "MicState 는 M1-08 소관"으로 남겨둔 자리다.
 * core/audio 가 Compose-free 계약이라 UI 개념인 이 타입은 여기 `ui/audio`(파형 [WaveformCanvas] 와 동거)에 둔다.
 *
 * 정본: [docs/ui/03-signature-interactions.md] I1 · product-design-system §3.1.
 * Ready(회색 동심원/"말할 차례") → Recording(빨강+리플/"녹음 중") → Analyzing(블루그레이+프로그레스 아크/"분석 중")
 * → Complete(초록/"완료"). 각 값의 [description] 은 `stateDescription`(A3)이자 전환 assertive announce 문구로
 * 1:1 재사용된다(03-signature-interactions.md:30).
 *
 * 권한 요청 중·레코더 기동 중 같은 UI-local 과도기는 이 축을 확장하지 않고 [MicTransientReason] 으로 분리한다
 * (dialogue-learning-flow.md §6.1 "저장/복원 대상 아님"). 정착 축만 SavedState 로 영속된다([SessionTurnSnapshot]).
 */
enum class MicState(val description: String) {
    Ready("말할 차례"),
    Recording("녹음 중"),
    Analyzing("분석 중"),
    Complete("완료"),
}

/**
 * 마이크의 **과도(transient) 사유** — 정착 [MicState] 위에 얹히는 UI-local 순간 상태로, 스냅샷/복원 대상이
 * 아니다(dialogue-learning-flow.md §6.1). 탭↔실제 상태 전이 사이의 짧은 창을 표현해 중복 탭을 막는다.
 *
 * - [PermissionRequesting]: 권한 프라이밍/시스템 다이얼로그 노출 중(정착 상태는 여전히 [MicState.Ready]).
 * - [RecorderStarting]: [MicState.Ready] 탭 후 [com.jjundev.oneclickeng.core.audio.RecordingController.start]
 *   완료 전. 이 창에서 재탭은 무시된다.
 */
enum class MicTransientReason {
    PermissionRequesting,
    RecorderStarting,
}

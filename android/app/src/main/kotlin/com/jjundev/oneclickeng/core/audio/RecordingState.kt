package com.jjundev.oneclickeng.core.audio

/**
 * 녹음 컨트롤러의 연속 상태축(M1-04 한정).
 *
 * 마이크 4상태(Ready/Recording/Analyzing/Complete = MicState)는 M1-08 소관이며,
 * 여기서는 오디오 레이어가 진실을 소유하는 두 값만 노출한다.
 * 정지 후의 종결 결과(Captured/TooQuiet/Failed)는 상태축이 아니라
 * [RecordingController.stop] 의 반환값([RecordingResult])으로 전달한다.
 */
enum class RecordingState {
    Idle,
    Recording,
}

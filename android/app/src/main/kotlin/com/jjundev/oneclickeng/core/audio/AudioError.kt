package com.jjundev.oneclickeng.core.audio

/**
 * 오디오 캡처 계층의 타입드 실패(FR-14 stale-guard·중도 abort 모델).
 *
 * 네트워크·전사 실패는 여기서 다루지 않는다(M1-06). M1-04 는 마이크 초기화와
 * PCM 읽기 단계의 실패만 표현한다.
 */
sealed interface AudioError {
    /** AudioRecord 버퍼 계산 또는 초기화 실패(권한 보유 전제에서도 발생 가능). */
    data object AudioInitError : AudioError

    /** [android.media.AudioRecord.read] 가 음수 오류 코드를 반환. */
    data class ReadError(val code: Int) : AudioError
}

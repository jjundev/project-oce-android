package com.jjundev.oneclickeng.core.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * 학습자 발화 녹음 계약(M1-04).
 *
 * 16kHz·mono·PCM16 을 캡처하며, 캡처 중 청크별 정규화 RMS 로 실시간 파형을 흘려보낸다.
 * 정지 시 무음 게이트를 거쳐 타입드 [RecordingResult] 를 반환한다.
 * MicState·전사·TTS 는 이 계약의 범위가 아니다(M1-06/M1-08).
 */
interface RecordingController {
    /** crackle 파형 프레임([AudioMath.BAR_COUNT] 값, [0.05,1]). */
    val waveform: StateFlow<FloatArray>

    /** 연속 상태축(Idle/Recording). */
    val state: StateFlow<RecordingState>

    /**
     * 녹음 시작. RECORD_AUDIO 권한 보유를 전제한다.
     * @throws AudioCaptureException [AudioError.AudioInitError] 로 초기화 실패 시.
     */
    suspend fun start()

    /** 녹음 정지 후 종결 결과 반환. 무음이면 [RecordingResult.TooQuiet]. */
    suspend fun stop(): RecordingResult
}

/** [RecordingController.start] 초기화 실패 신호. */
class AudioCaptureException(
    val error: AudioError,
    cause: Throwable? = null,
) : Exception(error.toString(), cause)

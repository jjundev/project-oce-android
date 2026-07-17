package com.jjundev.oneclickeng.core.audio

/**
 * Plays a fully-decoded PCM buffer. Abstracted so the playback coordinator stays free of
 * Android framework types and is unit-testable with a fake.
 */
interface PcmPlayer {
    /**
     * Play 16-bit mono PCM at [sampleRateHz], suspending until playback completes.
     * The caller passes the server-declared sample rate (never a hardcoded 24kHz —
     * Gemini declares it per response, plan #4/#9). Throws on playback failure.
     *
     * [speed] 는 피치를 보존하는 재생 배속(1.0 = 원본). Gemini TTS 는 구조적 속도 파라미터가 없어
     * 서버 합성은 항상 중립으로 받고 여기서 배속한다 — 값은 [com.jjundev.oneclickeng.core.audio.TtsSpeedCalibration]
     * 이 계산한다. 학습자 자기 녹음 재생은 배속하지 않는다(기본 1.0).
     */
    suspend fun play(
        pcm: ByteArray,
        sampleRateHz: Int,
        speed: Float = 1.0f,
    )

    /** Cancel any in-flight playback immediately. */
    fun stop()
}

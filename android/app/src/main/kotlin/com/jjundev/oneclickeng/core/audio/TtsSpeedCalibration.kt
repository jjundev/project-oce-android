package com.jjundev.oneclickeng.core.audio

/**
 * 경로별 절대 발화 속도 보정.
 *
 * 같은 슬라이더 값이 경로마다 다른 절대 속도를 냈다. DEVICE 는 `setSpeechRate` 로 엔진 배속을 직접
 * 먹지만, SERVER(Gemini TTS `generateContent`)는 구조적 속도 파라미터가 아예 없어 "Aim for speaking
 * speed multiplier N" 이라는 **산문 힌트**에 의존했다(`functions/src/providers/gemini.ts` `buildSynthesisBody`).
 * 모델은 이 힌트를 느슨하게만 따르므로 배속이 라인마다 흔들렸다. 이제 SERVER 는 항상 중립으로 합성하고
 * ([TtsPlaybackCoordinator.NEUTRAL_SYNTHESIS_RATE]) 재생 시점에 배속을 걸며, 두 경로 모두 여기서
 * 계산한 계수를 곱해 절대 속도를 맞춘다.
 *
 * **기준점: Gemini `Kore` 보이스의 자연 속도 = 1.0x.** 계수는 `w = D_source / D_reference`
 * (동일 문장·중립 속도·무음 트림 후 길이의 비)로 정의된다 — 느린 소스일수록 D 가 커서 w > 1 이 되고,
 * 그만큼 재생이 빨라져 기준에 붙는다. 값의 출처와 재측정 절차는 `TtsCalibrationReceiver` 참고.
 */
object TtsSpeedCalibration {
    /** 기준 보이스(Gemini `Kore`, gender != male)의 계수 — 정의상 1.0. */
    const val WEIGHT_SERVER_FEMALE = 1.0f

    /** Gemini `Puck`(gender == male) 계수 — Task 6 실측으로 대체. */
    const val WEIGHT_SERVER_MALE = 1.0f

    /** 온디바이스 Android TTS 계수 — Task 6 실측으로 대체. */
    const val WEIGHT_DEVICE = 1.0f

    /**
     * 보정 후 배속의 상·하한. 슬라이더 상한(1.5x)에 계수가 곱해져 튀어도 재생이 깨지지 않게 잘라낸다.
     * [android.media.AudioTrack.setPlaybackParams] 와 [android.speech.tts.TextToSpeech.setSpeechRate]
     * 모두 이 범위는 안전하게 지원한다.
     */
    const val MIN_EFFECTIVE_RATE = 0.5f
    const val MAX_EFFECTIVE_RATE = 2.0f

    /**
     * SERVER PCM 을 재생할 배속. gender→보이스 매핑은 서버 `resolveVoiceName`
     * (male=`Puck` / 그 외=`Kore`, `functions/src/llm/tts.ts:16`)의 **거울**이다 —
     * 서버가 매핑을 바꾸면 여기도 같이 바꿔야 한다.
     */
    fun serverPlaybackSpeed(
        userRate: Float,
        gender: String?,
    ): Float = clamp(userRate * if (isMale(gender)) WEIGHT_SERVER_MALE else WEIGHT_SERVER_FEMALE)

    /** 온디바이스 엔진에 넘길 `setSpeechRate` 값. */
    fun deviceSpeechRate(userRate: Float): Float = clamp(userRate * WEIGHT_DEVICE)

    private fun isMale(gender: String?): Boolean = gender.equals("male", ignoreCase = true)

    private fun clamp(rate: Float): Float = rate.coerceIn(MIN_EFFECTIVE_RATE, MAX_EFFECTIVE_RATE)
}

package com.jjundev.oneclickeng.core.audio

/**
 * 경로별 절대 발화 속도 보정.
 *
 * 같은 슬라이더 값이 경로마다 다른 절대 속도를 냈다. DEVICE 는 `setSpeechRate` 로 엔진 배속을 직접
 * 먹지만, SERVER(Gemini TTS `generateContent`)는 구조적 속도 파라미터가 아예 없어 "Aim for speaking
 * speed multiplier N" 이라는 **산문 힌트**에 의존했다(`functions/src/providers/gemini.ts` `buildSynthesisBody`).
 * 모델은 이 힌트를 느슨하게만 따르므로 배속이 라인마다 흔들렸다. 이제 SERVER 는 항상 중립으로 합성하고
 * ([com.jjundev.oneclickeng.feature.session.tts.TtsPlaybackCoordinator.NEUTRAL_SYNTHESIS_RATE]) 재생
 * 시점에 배속을 걸며, 두 경로 모두 여기서
 * 계산한 계수를 곱해 절대 속도를 맞춘다.
 *
 * **기준점: Gemini `Kore` 보이스의 자연 속도 = 1.0x.** 계수는 `w = D_source / D_reference`
 * (동일 문장·중립 속도·무음 트림 후 길이의 비)로 정의된다 — 느린 소스일수록 D 가 커서 w > 1 이 되고,
 * 그만큼 재생이 빨라져 기준에 붙는다. 값의 출처와 재측정 절차는 `TtsCalibrationReceiver` 참고.
 */
object TtsSpeedCalibration {
    /** 기준 보이스(Gemini `Kore`, gender != male)의 계수 — 정의상 1.0. */
    const val WEIGHT_SERVER_FEMALE = 1.0f

    /**
     * Gemini `Puck`(gender == male) 계수.
     *
     * 실측 2026-07-17(`TtsCalibrationProbe`, 참조 문장 3개 × **3회 반복**, 실기기 Samsung SM-S911N).
     * **주의 — 이 값은 안정된 배수가 아니라 잡음 속 평균이다.** 같은 문장을 3회 반복 측정한 결과, Kore
     * 자체의 합성 길이가 동일 텍스트에서도 회차마다 최대 11.6% 흔들렸고(예: line3 4.510~5.100초),
     * Puck/Kore 비율은 회차별로 0.867~1.160 까지 벌어졌다(9개 표본 평균 0.991). 즉 Gemini TTS 는
     * **중립 속도로 고정해도 호출마다 실제 발화 길이가 비결정적**이다 — 이건 이 브랜치가 애초에 없애려던
     * "Gemini 가 라인마다 속도를 다르게 따른다"는 문제가 프롬프트 힌트 제거 후에도 백엔드 자체에 남아있다는
     * 뜻이며, 클라이언트 상수로는 근본적으로 고칠 수 없다. 9개 표본 평균이 1.0 에 가까워(재현 가능한
     * 성별 간 속도차가 통계적으로 유의하지 않음) 이 값은 사실상 "보정 없음"에 가깝다. 개별 발화는 이
     * 상수를 곱해도 여전히 Gemini 자체 잡음만큼(±10~20%) 빠르거나 느리게 들릴 수 있다 — 그건 회귀가
     * 아니라 알려진 백엔드 한계다.
     */
    const val WEIGHT_SERVER_MALE = 0.99f

    /**
     * 온디바이스 Android TTS, 여성 보이스 계수.
     *
     * 실측 2026-07-17(`TtsCalibrationProbe`, 참조 문장 3개 × 3회 반복, 실기기 Samsung SM-S911N).
     * 단말 합성은 **완전히 결정론적**임을 3회 반복으로 확인했다(문장별 길이가 회차 간 1ms 차이도 없이
     * 동일) — 위 [WEIGHT_SERVER_MALE] 의 잡음과 달리 이 값은 재현 가능한 신호다. 계산은 회차별 Kore
     * 길이의 3회 평균을 분모로 써서(Gemini 쪽 잡음이 분모에 섞여 들어오는 것을 줄임) 문장별 비율을
     * 낸 뒤 평균했다(문장 간 잔여 편차 ≈0.12 — 실제 문장 리듬 차이로 보이며 0.05 기준은 넘지만, 지금
     * 계수 없음 상태보다는 훨씬 낫다). 기기·엔진이 바뀌면 재측정 대상.
     */
    const val WEIGHT_DEVICE_FEMALE = 0.855f

    /**
     * 온디바이스 Android TTS, 남성 보이스 계수.
     *
     * 실측 2026-07-17(`TtsCalibrationProbe`, 참조 문장 3개 × 3회 반복, 실기기 Samsung SM-S911N).
     * [WEIGHT_DEVICE_FEMALE] 과 동일한 방법(3회 평균 Kore 분모)으로 측정했다 — 이쪽도 단말 합성
     * 자체는 결정론적으로 재현됐다(문장 간 잔여 편차 ≈0.09). 기기·엔진이 바뀌면 재측정 대상.
     */
    const val WEIGHT_DEVICE_MALE = 0.707f

    /**
     * 보정 후 배속의 상·하한. **정상 범위 안에서는 절대 걸리지 않아야 한다** — 걸리면 클램프가
     * 조용히 미보정 상태로 되돌리는 것과 같다(이 브랜치가 없애려는 바로 그 버그). 그래서 두 경계
     * 모두 "슬라이더 실사용 범위 × 그럴듯한 계수 범위"로 정의한다:
     *
     * - 하한: 슬라이더 최소(`TtsSettings.MIN_SPEECH_RATE`=0.5) × 그럴듯한 계수 최소(~0.5) = 0.25.
     *   슬라이더 최소값 자체가 0.5 이므로, 하한을 슬라이더 최소와 같게 두면(과거 값) 계수가 1.0
     *   미만인 순간 슬라이더 하단 전체가 클램프에 먹혀 미보정으로 되돌아간다.
     * - 상한: 슬라이더 최대(`TtsSettings.MAX_SPEECH_RATE`=1.5) × 그럴듯한 계수 최대(~1.3) = 1.95,
     *   여유를 둬 2.0.
     *
     * [android.media.AudioTrack.setPlaybackParams] 와 [android.speech.tts.TextToSpeech.setSpeechRate]
     * 모두 이 범위는 안전하게 지원하므로, 클램프는 오직 병적인(계수 계측 오류 등) 값만 잘라낸다.
     */
    const val MIN_EFFECTIVE_RATE = 0.25f
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

    /**
     * 온디바이스 엔진에 넘길 `setSpeechRate` 값. `playFromDevice` 가 `deviceTts.speak` 에 턴의 gender 를
     * 넘기면 [com.jjundev.oneclickeng.feature.session.tts.AndroidDeviceTts] 가 성별별 보이스를 고르므로
     * (`selectGenderVoice`), 보이스마다 자연 속도가 다를 수 있다 — SERVER 와 대칭으로 여기도 gender 를
     * 받아 [WEIGHT_DEVICE_MALE]/[WEIGHT_DEVICE_FEMALE] 중 하나를 고른다.
     */
    fun deviceSpeechRate(
        userRate: Float,
        gender: String?,
    ): Float = clamp(userRate * if (isMale(gender)) WEIGHT_DEVICE_MALE else WEIGHT_DEVICE_FEMALE)

    private fun isMale(gender: String?): Boolean = gender.equals("male", ignoreCase = true)

    private fun clamp(rate: Float): Float = rate.coerceIn(MIN_EFFECTIVE_RATE, MAX_EFFECTIVE_RATE)
}

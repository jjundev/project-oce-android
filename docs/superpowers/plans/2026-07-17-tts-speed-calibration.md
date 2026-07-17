# TTS 발화 속도 보정(Speed Calibration) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 같은 슬라이더 배속에서 SERVER(Gemini)·DEVICE(온디바이스) 두 경로가 **같은 절대 발화 속도**를 내도록, 실측 기반 보정 계수를 도입한다.

**Architecture:** 세 조각이다. (1) **결정론적 배속** — SERVER 는 이제 Gemini 에게 속도를 *부탁*하지 않는다. 항상 중립(1.0)으로 합성하고, 받은 PCM 을 `AudioTrack.setPlaybackParams(speed)`(API 23+, 피치 보존 시간축 신축)로 재생 시점에 배속한다. (2) **보정 계수** — `TtsSpeedCalibration` 이 경로·보이스별 계수를 곱해 절대 속도를 기준점에 맞춘다. (3) **실측 하네스** — 디버그 전용 프로브가 동일 참조 문장을 양 경로로 합성해 무음 트림 후 길이(초)를 재고, 계수를 계산해 logcat 에 찍고 WAV 를 덤프한다(사람이 직접 들어 확인).

부수 효과: 배속이 캐시 키에서 빠져 **합성 1회로 모든 배속을 재사용**한다 — 세션 중 속도를 바꿔도 재합성이 없다(콜드스타트 노출 감소).

**Tech Stack:** Kotlin · Coroutines · Hilt · Android `AudioTrack` / `TextToSpeech` · JUnit4 · Gradle(`scripts/verify-android.sh`)

## Global Constraints

- **모듈:** `android/` 단일 앱 모듈. `minSdk = 26`, `compileSdk = 36`, JDK 17 (`android/app/build.gradle.kts:15-20`). `AudioTrack.setPlaybackParams` 는 API 23+ 이므로 버전 분기 불필요.
- **백엔드 배포 없음.** `functions/` 는 **한 줄도 건드리지 않는다.** 와이어 계약(`TtsPayload.speechRate`)은 그대로 두고, 클라이언트가 항상 `1.0` 을 보내 서버의 산문 힌트를 중립화한다. (서버 힌트 문장 자체를 제거하는 건 후속 — 배포 리스크를 이 작업에 끌어들이지 않는다.)
- **기준점(확정):** **Gemini `Kore` 보이스의 자연 속도 = 1.0x.** 다른 모든 소스를 여기에 맞춘다. Gemini 를 억지로 가속하지 않아 자연스러움이 보존된다.
- **계수 정의(확정):** `w = D_source / D_reference`. `D` = 동일 문장을 중립 속도로 합성해 **앞뒤 무음을 트림한 뒤**의 길이(초). 느린 소스일수록 `D` 가 커져 `w > 1` 이 되고, 그만큼 재생이 빨라져 기준에 붙는다.
- **"자연스러운 발음"/"빠른 발음" 라벨은 속도가 아니다.** `TtsQuality.SERVER`/`DEVICE`(합성 엔진) 라벨이고 "빠른"은 *즉시 재생*(네트워크 왕복 없음)을 뜻한다(`strings.xml:30-37`, `tts.md` §5). 이 작업은 라벨·문구를 **바꾸지 않는다.**
- **검증 명령은 항상** `scripts/verify-android.sh` (워크트리 gradle 오염·`google-services.json` 부재 우회). 맨 `./gradlew` 금지.
- **ktlint·detekt 통과 필수.** 주변 코드 관례를 따른다: KDoc 은 한국어(신규 코드) 또는 영어(기존 파일의 기존 톤 유지), 4-space, trailing comma.
- **스크린샷 정합 금지구역:** `SettingsContent` 는 Roborazzi 스크린샷 seam 이다(`SettingsScreenScreenshotTest`). 설정 UI 에 디버그 행을 넣지 않는다 — 프로브 트리거는 `src/debug` 소스셋의 BroadcastReceiver 다.

---

### Task 1: 보정 계수 seam (`TtsSpeedCalibration`)

순수 계산 객체를 먼저 세운다. 계수는 이 시점엔 **전부 1.0 플레이스홀더**(= 현재 동작 유지)이고, Task 6 에서 실측값으로 채운다. 그래서 이 태스크의 테스트는 **계수 값에 의존하지 않는 계약**(성별 라우팅·클램프·단조성)만 검증한다 — Task 6 에서 숫자를 바꿔도 테스트가 깨지지 않아야 한다.

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/audio/TtsSpeedCalibration.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/audio/TtsSpeedCalibrationTest.kt`

**Interfaces:**
- Consumes: 없음(순수).
- Produces:
  - `object TtsSpeedCalibration`
  - `fun serverPlaybackSpeed(userRate: Float, gender: String?): Float`
  - `fun deviceSpeechRate(userRate: Float): Float`
  - `const val WEIGHT_SERVER_FEMALE: Float`, `WEIGHT_SERVER_MALE: Float`, `WEIGHT_DEVICE: Float`
  - `const val MIN_EFFECTIVE_RATE = 0.5f`, `MAX_EFFECTIVE_RATE = 2.0f`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`android/app/src/test/kotlin/com/jjundev/oneclickeng/core/audio/TtsSpeedCalibrationTest.kt` 를 새로 만든다:

```kotlin
package com.jjundev.oneclickeng.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 계수 자체(WEIGHT_*)는 Task 6 실측으로 바뀌는 값이므로 **숫자를 하드코딩하지 않는다.**
 * 여기서 고정하는 건 값이 바뀌어도 유지돼야 하는 계약이다: 성별 라우팅, 클램프, 단조성.
 */
class TtsSpeedCalibrationTest {
    @Test
    fun `male gender routes to the male weight and is case-insensitive`() {
        val lower = TtsSpeedCalibration.serverPlaybackSpeed(1.0f, "male")
        val upper = TtsSpeedCalibration.serverPlaybackSpeed(1.0f, "MALE")

        assertEquals(lower, upper, TOLERANCE)
        assertEquals(TtsSpeedCalibration.WEIGHT_SERVER_MALE, lower, TOLERANCE)
    }

    @Test
    fun `null and non-male genders route to the female weight`() {
        val expected = TtsSpeedCalibration.WEIGHT_SERVER_FEMALE

        assertEquals(expected, TtsSpeedCalibration.serverPlaybackSpeed(1.0f, null), TOLERANCE)
        assertEquals(expected, TtsSpeedCalibration.serverPlaybackSpeed(1.0f, "female"), TOLERANCE)
        assertEquals(expected, TtsSpeedCalibration.serverPlaybackSpeed(1.0f, "unknown"), TOLERANCE)
    }

    @Test
    fun `device rate at 1x is the device weight`() {
        assertEquals(TtsSpeedCalibration.WEIGHT_DEVICE, TtsSpeedCalibration.deviceSpeechRate(1.0f), TOLERANCE)
    }

    @Test
    fun `effective rate is clamped at both ends`() {
        assertEquals(
            TtsSpeedCalibration.MAX_EFFECTIVE_RATE,
            TtsSpeedCalibration.serverPlaybackSpeed(99f, null),
            TOLERANCE,
        )
        assertEquals(
            TtsSpeedCalibration.MIN_EFFECTIVE_RATE,
            TtsSpeedCalibration.serverPlaybackSpeed(0.001f, null),
            TOLERANCE,
        )
        assertEquals(TtsSpeedCalibration.MAX_EFFECTIVE_RATE, TtsSpeedCalibration.deviceSpeechRate(99f), TOLERANCE)
        assertEquals(TtsSpeedCalibration.MIN_EFFECTIVE_RATE, TtsSpeedCalibration.deviceSpeechRate(0.001f), TOLERANCE)
    }

    @Test
    fun `a faster slider never yields a slower effective rate`() {
        // 슬라이더 실사용 범위(0.5~1.5x)를 훑어 단조 증가를 확인한다.
        var previous = 0f
        var slider = 0.5f
        while (slider <= 1.5f) {
            val speed = TtsSpeedCalibration.serverPlaybackSpeed(slider, null)
            assertTrue("slider=$slider produced $speed after $previous", speed >= previous)
            previous = speed
            slider += 0.1f
        }
    }

    private companion object {
        const val TOLERANCE = 1e-4f
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*TtsSpeedCalibrationTest*'`
Expected: FAIL — 컴파일 에러 `Unresolved reference: TtsSpeedCalibration`

- [ ] **Step 3: 최소 구현**

`android/app/src/main/kotlin/com/jjundev/oneclickeng/core/audio/TtsSpeedCalibration.kt`:

```kotlin
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
```

- [ ] **Step 4: 통과를 확인한다**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*TtsSpeedCalibrationTest*'`
Expected: PASS (5 tests)

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/core/audio/TtsSpeedCalibration.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/core/audio/TtsSpeedCalibrationTest.kt
git commit -m "feat(tts): add speed calibration seam with placeholder weights"
```

---

### Task 2: 재생 배속 seam (`PcmPlayer.play(speed)`)

`PcmPlayer` 에 `speed` 파라미터를 추가하고 `PcmAudioPlayer` 가 `AudioTrack.setPlaybackParams` 로 적용한다. 기본값 `1.0f` 이므로 이 태스크 단독으로는 **동작 변화가 없다**(seam 만 생긴다). `PcmAudioPlayer` 는 프레임워크 어댑터라 단위테스트 대상이 아니다(코디네이터가 `PcmPlayer` 페이크로 검증되는 기존 설계 — `TtsPlaybackCoordinator` KDoc 참고). 실제 `AudioTrack` 동작은 Task 6 에서 실기기로 확인한다.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/audio/PcmPlayer.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/audio/PcmAudioPlayer.kt:30-33, 49-50`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt:58-70` (`FakePcmPlayer`)
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModelTest.kt:124-127` (`NoopPcmPlayer`)

> **`PcmPlayer` 구현체는 프로덕션 1개 + 테스트 페이크 2개, 총 3개다.** Kotlin 은 파라미터에 기본값이
> 있어도 override 가 전체 파라미터 목록을 선언해야 하므로, **셋 다 고치지 않으면 컴파일이 깨진다.**
> 확인: `grep -rn ": PcmPlayer" android/app/src`

**Interfaces:**
- Consumes: 없음.
- Produces: `suspend fun PcmPlayer.play(pcm: ByteArray, sampleRateHz: Int, speed: Float = 1.0f)` — Task 3 이 이 `speed` 를 채운다. 테스트 페이크 `FakePcmPlayer.speeds: MutableList<Float>` 도 Task 3 이 단언에 쓴다.

- [ ] **Step 1: 인터페이스에 `speed` 를 추가한다**

`PcmPlayer.kt` 의 `play` 를 통째로 아래로 교체:

```kotlin
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
```

- [ ] **Step 2: `PcmAudioPlayer` 가 배속을 적용하게 한다**

`PcmAudioPlayer.kt` 의 `play` 시그니처(30-33행)를 교체:

```kotlin
        override suspend fun play(
            pcm: ByteArray,
            sampleRateHz: Int,
            speed: Float,
        ) {
```

그리고 49-50행의 마커 설정 바로 뒤에 `applySpeed` 호출을 끼운다 — 즉 이 두 줄을

```kotlin
                // 2 bytes per 16-bit sample — marker at the last frame signals completion.
                audioTrack.setNotificationMarkerPosition(pcm.size / 2)
```

아래처럼 바꾼다:

```kotlin
                // 2 bytes per 16-bit sample — marker at the last frame signals completion.
                // 마커는 *소스 프레임* 기준이라 배속을 걸어도 그대로 유효하다(벽시계만 짧아진다).
                audioTrack.setNotificationMarkerPosition(pcm.size / 2)
                applySpeed(audioTrack, speed)
```

`buildTrack` 아래(105행 뒤)에 헬퍼를 추가:

```kotlin
        /**
         * 재생 배속 적용. [android.media.PlaybackParams] 는 피치를 보존하는 시간축 신축(Sonic)이라
         * 목소리 톤이 변하지 않는다. 기기가 지원하지 않는 값이면 IllegalArgumentException 을 던지므로,
         * 실패하면 배속 없이(1.0x) 재생을 이어간다 — 속도가 조금 틀린 게 무음보다 낫다.
         */
        private fun applySpeed(
            track: AudioTrack,
            speed: Float,
        ) {
            if (speed == 1.0f) return
            runCatching { track.playbackParams = track.playbackParams.setSpeed(speed) }
        }
```

- [ ] **Step 3: 테스트 페이크가 배속을 기록하게 한다**

`TtsPlaybackCoordinatorTest.kt` 의 `FakePcmPlayer`(58-70행)를 교체. **기존 `played` 는 그대로 둔다** — 다른 테스트 12개가 이 필드를 단언한다:

```kotlin
private class FakePcmPlayer(var throwOnPlay: Boolean = false) : PcmPlayer {
    val played = mutableListOf<Pair<ByteArray, Int>>()

    /** 재생 호출별 배속(= [played] 와 같은 인덱스). Task 3 의 보정 단언이 쓴다. */
    val speeds = mutableListOf<Float>()

    override suspend fun play(
        pcm: ByteArray,
        sampleRateHz: Int,
        speed: Float,
    ) {
        if (throwOnPlay) error("playback boom")
        played += pcm to sampleRateHz
        speeds += speed
    }

    override fun stop() = Unit
}
```

- [ ] **Step 4: 두 번째 페이크(`NoopPcmPlayer`)도 시그니처를 맞춘다**

`DialogueGenerationViewModelTest.kt:124-127` 의 `NoopPcmPlayer` 는 이 스위트의 별개 페이크다. 배속을
쓰지 않지만 **override 는 전체 파라미터 목록을 선언해야 하므로** 안 고치면 컴파일이 깨진다:

```kotlin
private class NoopPcmPlayer : PcmPlayer {
    override suspend fun play(pcm: ByteArray, sampleRateHz: Int, speed: Float) = Unit
    override fun stop() = Unit
}
```

- [ ] **Step 5: 기존 스위트가 그대로 통과하는지 확인한다**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*TtsPlaybackCoordinatorTest*' --tests '*DialogueGenerationViewModelTest*'`
Expected: PASS — 기본값 1.0f 이라 동작 변화가 없다.

컴파일이 `NoopPcmPlayer`/`FakePcmPlayer` 외의 다른 `PcmPlayer` 구현에서 깨지면, 그 구현에도 같은
`speed: Float` 파라미터를 추가하라(`grep -rn ": PcmPlayer" android/app/src` 로 전수 확인).

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/core/audio/PcmPlayer.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/core/audio/PcmAudioPlayer.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModelTest.kt
git commit -m "feat(audio): add pitch-preserving playback speed to PcmPlayer"
```

---

### Task 3: 코디네이터 배선 — 중립 합성 + 보정 적용

여기서 실제 동작이 바뀐다. SERVER 는 항상 중립 합성 → 재생 배속. DEVICE 는 보정된 `setSpeechRate`. 배속이 캐시 키에서 빠진다.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt` (캐시 키 25-32행, `playTurn` 122-145, `replay` 147-165, `prefetch` 194-206, `warmUpModel` 218-226, `awaitWarm` 235-248, `synthesize` 286-306, `obtainAudio` 317-332, `playFromServer` 337-353, `playPcm` 355-373, `playFromDevice` 375-397, companion 418-438)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt` (38-56행 `FakeLlmApi`, 72-93행 `FakeDeviceTts`, 405-419행 기존 테스트 **교체**, 신규 테스트 추가)

**Interfaces:**
- Consumes: `TtsSpeedCalibration.serverPlaybackSpeed(userRate, gender)` / `.deviceSpeechRate(userRate)` (Task 1). `PcmPlayer.play(pcm, sampleRateHz, speed)` (Task 2).
- Produces: `TtsPlaybackCoordinator.NEUTRAL_SYNTHESIS_RATE = 1.0f` (companion const). 공개 API(`playTurn`/`replay`/`playClip`/`prefetch`/`awaitWarm`/`warmUpModel`/`clearCache`/`stop`) 시그니처는 **전부 그대로**다 — 호출부 변경 없음.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

먼저 페이크 두 개를 확장한다. `FakeLlmApi`(38-56행)에 요청 기록을 추가 — `override suspend fun tts` 안 `callCount++` 바로 아래에 한 줄, 그리고 필드 선언:

```kotlin
private class FakeLlmApi(
    var response: TtsResponse = okResponse(),
    var error: Throwable? = null,
    var delayMs: Long = 0,
    var failFirst: Int = 0, // first N calls throw — models a cold call the server aborts (it still preheats)
) : LlmApi {
    var callCount = 0

    /** 마지막으로 나간 tts 요청 — 와이어에 실린 speechRate 단언용. */
    var lastRequest: TtsRequest? = null

    @Suppress("TooGenericExceptionThrown") // models an opaque server abort — real callers see no typed cause either.
    override suspend fun tts(body: TtsRequest): TtsResponse {
        callCount++
        lastRequest = body
        if (delayMs > 0) delay(delayMs)
        if (callCount <= failFirst) throw RuntimeException("cold synth aborted by server")
        error?.let { throw it }
        return response
    }

    override suspend fun speaking(body: SpeakingRequest): SpeakingResponse = error("unused")
}
```

`FakeDeviceTts`(72-93행)에 마지막 배속 기록을 추가 — 필드 선언과 `speak` 안 `callCount++` 아래 한 줄:

```kotlin
private class FakeDeviceTts(
    var result: DeviceTtsResult = DeviceTtsResult.COMPLETED,
    var delayMs: Long = 0,
) : DeviceTts {
    var callCount = 0

    /** 엔진에 실제로 넘어간 배속 — 보정 단언용. */
    var lastRate: Float? = null

    override suspend fun speak(
        text: String,
        gender: String?,
        speechRate: Float,
        onStart: () -> Unit,
    ): DeviceTtsResult {
        callCount++
        lastRate = speechRate
        if (delayMs > 0) delay(delayMs)
        // Faithful to AndroidDeviceTts: onStart fires only when audio actually begins — i.e. the
        // engine initialized and the utterance started. LANGUAGE_MISSING / ERROR return before that.
        if (result == DeviceTtsResult.COMPLETED) onStart()
        return result
    }

    override fun stop() = Unit
}
```

기존 테스트 `cache key includes speech rate so a rate change re-synthesizes`(404-419행)를 **통째로 교체**한다 — 그 계약이 의도적으로 뒤집혔다:

```kotlin
    @Test
    fun `a rate change reuses the cached synthesis instead of re-synthesizing`() =
        runTest {
            val api = FakeLlmApi()
            val settings = FakeSettings(TtsSettings(quality = TtsQuality.SERVER, speechRate = 1.0f))
            val player = FakePcmPlayer()
            val coordinator = TtsPlaybackCoordinator(api, player, FakeDeviceTts(), settings, coordScope())

            coordinator.playTurn("Hello", null)
            advanceUntilIdle()
            settings.setSpeechRate(1.5f)
            coordinator.playTurn("Hello", null)
            advanceUntilIdle()

            // 서버 오디오는 이제 중립으로 합성되고 배속은 재생 시점에 걸린다 → 키에 rate 가 없다.
            assertEquals(1, api.callCount)
            // 그래도 두 번째 재생은 새 속도로 나가야 한다.
            assertEquals(
                TtsSpeedCalibration.serverPlaybackSpeed(1.0f, null),
                player.speeds[0],
                SPEED_TOLERANCE,
            )
            assertEquals(
                TtsSpeedCalibration.serverPlaybackSpeed(1.5f, null),
                player.speeds[1],
                SPEED_TOLERANCE,
            )
        }
```

그리고 신규 테스트 4개를 그 아래에 추가:

```kotlin
    @Test
    fun `server synthesis always requests the neutral rate whatever the slider says`() =
        runTest {
            val api = FakeLlmApi()
            val settings = FakeSettings(TtsSettings(quality = TtsQuality.SERVER, speechRate = 1.4f))
            val coordinator = TtsPlaybackCoordinator(api, FakePcmPlayer(), FakeDeviceTts(), settings, coordScope())

            coordinator.playTurn("Hello", "male")
            advanceUntilIdle()

            // Gemini 에 속도를 부탁하지 않는다 — 힌트는 늘 중립이고 배속은 클라가 건다.
            assertEquals(
                TtsPlaybackCoordinator.NEUTRAL_SYNTHESIS_RATE,
                api.lastRequest!!.payload.speechRate,
                SPEED_TOLERANCE,
            )
        }

    @Test
    fun `server playback applies the voice-specific calibration weight`() =
        runTest {
            val player = FakePcmPlayer()
            val settings = FakeSettings(TtsSettings(quality = TtsQuality.SERVER, speechRate = 1.2f))
            val coordinator = TtsPlaybackCoordinator(FakeLlmApi(), player, FakeDeviceTts(), settings, coordScope())

            coordinator.playTurn("Hello", "male")
            advanceUntilIdle()

            assertEquals(
                TtsSpeedCalibration.serverPlaybackSpeed(1.2f, "male"),
                player.speeds.single(),
                SPEED_TOLERANCE,
            )
        }

    @Test
    fun `device path applies the device calibration weight`() =
        runTest {
            val device = FakeDeviceTts()
            val settings = FakeSettings(TtsSettings(quality = TtsQuality.DEVICE, speechRate = 1.2f))
            val coordinator = TtsPlaybackCoordinator(FakeLlmApi(), FakePcmPlayer(), device, settings, coordScope())

            coordinator.playTurn("Hello", "male")
            advanceUntilIdle()

            assertEquals(TtsSpeedCalibration.deviceSpeechRate(1.2f), device.lastRate!!, SPEED_TOLERANCE)
        }

    @Test
    fun `replay honors a speed changed after the turn played`() =
        runTest {
            val player = FakePcmPlayer()
            val settings = FakeSettings(TtsSettings(quality = TtsQuality.SERVER, speechRate = 1.0f))
            val coordinator = TtsPlaybackCoordinator(FakeLlmApi(), player, FakeDeviceTts(), settings, coordScope())

            coordinator.playTurn("Hello", "male")
            advanceUntilIdle()
            settings.setSpeechRate(1.5f)
            coordinator.replay()
            advanceUntilIdle()

            // replay 는 캐시된 중립 PCM 을 *현재* 설정으로 다시 배속한다(마지막 턴의 성별 계수로).
            assertEquals(
                TtsSpeedCalibration.serverPlaybackSpeed(1.5f, "male"),
                player.speeds.last(),
                SPEED_TOLERANCE,
            )
        }

    @Test
    fun `self clip plays at unmodified speed`() =
        runTest {
            val player = FakePcmPlayer()
            val settings = FakeSettings(TtsSettings(quality = TtsQuality.SERVER, speechRate = 1.5f))
            val coordinator = TtsPlaybackCoordinator(FakeLlmApi(), player, FakeDeviceTts(), settings, coordScope())

            coordinator.playClip(byteArrayOf(9, 9), 16000)
            advanceUntilIdle()

            // 학습자 자기 녹음은 보정 대상이 아니다 — 자기 목소리를 왜곡하지 않는다.
            assertEquals(1.0f, player.speeds.single(), SPEED_TOLERANCE)
        }
```

테스트 클래스 맨 아래(`collectAudioReady` 헬퍼 뒤, 707행 근처)에 톨러런스 상수를 추가:

```kotlin
    private companion object {
        const val SPEED_TOLERANCE = 1e-4f
    }
```

파일 상단 import 에 추가:

```kotlin
import com.jjundev.oneclickeng.core.audio.TtsSpeedCalibration
```

- [ ] **Step 2: 실패를 확인한다**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*TtsPlaybackCoordinatorTest*'`
Expected: FAIL — 컴파일 에러 `Unresolved reference: NEUTRAL_SYNTHESIS_RATE`

- [ ] **Step 3: 코디네이터를 구현한다**

`TtsPlaybackCoordinator.kt` 상단 import 에 추가:

```kotlin
import com.jjundev.oneclickeng.core.audio.TtsSpeedCalibration
```

**(a) 캐시 키(25-32행)를 교체:**

```kotlin
/** Cache key for a synthesized opponent line. 서버 오디오는 이제 항상 중립 속도로 합성되고
 *  ([TtsPlaybackCoordinator.NEUTRAL_SYNTHESIS_RATE]) 배속은 재생 시점에 걸리므로
 *  ([TtsSpeedCalibration]), 출력은 (text, gender) 에만 의존한다 — 합성 1회가 모든 슬라이더 값을
 *  커버하고, 세션 중 속도를 바꿔도 재합성 없이 캐시에서 다시 재생된다. */
internal data class TtsCacheKey(
    val text: String,
    val gender: String?,
)
```

**(b) `lastGender` 필드 추가** — `lastSampleRate`(87-88행) 선언 바로 뒤:

```kotlin
        // replay 가 *현재* 설정으로 배속을 다시 계산하려면 마지막 서버 턴의 성별이 필요하다
        // (보정 계수가 보이스별이므로 — TtsSpeedCalibration). [lastPcm] 과 수명이 같다.
        @Volatile
        private var lastGender: String? = null
```

**(c) `playTurn`(122-145행)** — `lastPcm = null` 줄 아래에 `lastGender = null` 을 추가. 나머지는 그대로:

```kotlin
                    lastPcm = null
                    lastGender = null
                    _state.value = PlaybackState.LOADING
```

**(d) `replay`(147-165행)를 교체** — mute 재확인 + 현재 설정으로 배속 재계산:

```kotlin
        /** Replay the current turn's audio from memory — no re-synthesis (plan #18).
         *  배속은 *현재* 설정으로 다시 계산하므로, 턴 재생 뒤 속도를 바꾸고 "다시 듣기" 하면
         *  새 속도로 나온다(재합성 없이). */
        fun replay() {
            val token = startNewSession()
            currentJob =
                scope.launch {
                    // Re-read mute: if muted mid-turn, replay is a no-op that still advances (#14).
                    val settings = settingsRepo.current()
                    if (settings.muted) {
                        finish(token, PlaybackState.IDLE, advance = true)
                        return@launch
                    }
                    val pcm = lastPcm
                    val rate = lastSampleRate
                    if (pcm == null || rate <= 0) {
                        finish(token, PlaybackState.IDLE, advance = true)
                        return@launch
                    }
                    playPcm(token, pcm, rate, TtsSpeedCalibration.serverPlaybackSpeed(settings.speechRate, lastGender))
                }
        }
```

**(e) `prefetch`(198-203행 내부)** — `obtainAudio` 호출에서 rate 인자를 뺀다:

```kotlin
                    obtainAudio(text, gender) // result cached as a side effect
```

**(f) `warmUpModel`(224행)** — `synthesize` 에서 rate 인자를 뺀다:

```kotlin
                    synthesize(WARM_UP_TEXT, gender = null) // discarded on purpose
```

**(g) `awaitWarm`(246-247행)**:

```kotlin
            return obtainAudio(text, gender) != null ||
                obtainAudio(text, gender) != null
```

**(h) `synthesize`(286-306행)를 교체** — rate 파라미터를 없애고 항상 중립을 보낸다:

```kotlin
        /** Server synthesis + base64 decode under the watchdog. Returns null on
         *  timeout / network / HTTP / malformed / undecodable payload (caller falls back
         *  to device, or — for prefetch — simply skips). Never touches player/state/token,
         *  so it is safe to call from a background prefetch.
         *
         *  와이어의 speechRate 는 항상 [NEUTRAL_SYNTHESIS_RATE] 다 — 사용자 배속은 여기가 아니라
         *  재생 시점에 걸린다([TtsSpeedCalibration]). */
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private suspend fun synthesize(
            text: String,
            gender: String?,
        ): CachedAudio? {
            val response =
                withTimeoutOrNull(SYNTH_WATCHDOG_MS) {
                    try {
                        api.tts(
                            TtsRequest(
                                payload =
                                    TtsPayload(
                                        text = text,
                                        gender = gender,
                                        speechRate = NEUTRAL_SYNTHESIS_RATE,
                                    ),
                            ),
                        )
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                } ?: return null
            return try {
                CachedAudio(Base64.getDecoder().decode(response.pcmBase64), response.sampleRate)
            } catch (e: IllegalArgumentException) {
                null // undecodable payload
            }
        }
```

**(i) `obtainAudio`(317-332행)** — 시그니처와 키에서 rate 를 뺀다. KDoc 은 그대로 두고 본문만:

```kotlin
        private suspend fun obtainAudio(
            text: String,
            gender: String?,
        ): CachedAudio? {
            val key = TtsCacheKey(text, gender)
            cache[key]?.let { return it }
            val deferred =
                inFlight[key] ?: scope.async {
                    synthesize(text, gender)?.also { cache[key] = it }
                }.also { d ->
                    inFlight[key] = d
                    d.invokeOnCompletion { inFlight.remove(key, d) } // identity remove; tied to the job, not awaiters
                }
            return deferred.await()
        }
```

**(j) `playFromServer`(337-353행)를 교체** — `rate` 는 이제 *사용자* 배속이고, 합성이 아니라 재생에 쓰인다:

```kotlin
        /** @return true if the server path terminally handled the turn (played or swallowed
         *  as stale); false if synthesis failed and the caller should try device TTS.
         *  A cache hit / in-flight join plays without a fresh network call.
         *  [userRate] 는 슬라이더 값 — 합성이 아니라 재생 배속 계산에 쓰인다. */
        @Suppress("ReturnCount")
        private suspend fun playFromServer(
            token: Long,
            text: String,
            gender: String?,
            userRate: Float,
        ): Boolean {
            // Live playback waits at most SERVER_WATCHDOG_MS for the audio, then falls back to
            // device. The synthesis is a sibling job on `scope`, so this timeout only abandons the
            // *wait* — the cold synthesis finishes in the background and caches for the next need.
            val audio = withTimeoutOrNull(SERVER_WATCHDOG_MS) { obtainAudio(text, gender) } ?: return false
            if (token != sessionToken) return true // stale: swallow, don't advance
            lastPcm = audio.pcm
            lastSampleRate = audio.sampleRate
            lastGender = gender
            playPcm(token, audio.pcm, audio.sampleRate, TtsSpeedCalibration.serverPlaybackSpeed(userRate, gender))
            return true
        }
```

**(k) `playPcm`(355-373행)** — `speed` 파라미터를 추가(기본 1.0 = 학습자 클립용)하고 플레이어에 넘긴다:

```kotlin
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private suspend fun playPcm(
            token: Long,
            pcm: ByteArray,
            sampleRate: Int,
            speed: Float = 1.0f,
        ) {
            if (token != sessionToken) return
            _state.value = PlaybackState.PLAYING
            _audioReady.tryEmit(Unit)
            try {
                player.play(pcm, sampleRate, speed)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                finish(token, PlaybackState.FAILED, advance = true)
                return
            }
            finish(token, PlaybackState.IDLE, advance = true)
        }
```

**(l) `playFromDevice`(375-397행)** — 시그니처의 `rate` 를 `userRate` 로 바꾸고 보정해서 넘긴다. 387행:

```kotlin
        private suspend fun playFromDevice(
            token: Long,
            text: String,
            gender: String?,
            userRate: Float,
        ) {
            if (token != sessionToken) return
            // No premature PLAYING here: the device engine may still be initializing on the first
            // utterance (the 1–2s first-audio load). PLAYING + audioReady fire from onStart, when
            // audio actually begins, so the opponent skeleton stays up until then.
            val result =
                withTimeoutOrNull(DEVICE_WATCHDOG_MS) {
                    deviceTts.speak(text, gender, TtsSpeedCalibration.deviceSpeechRate(userRate)) {
                        onPlaybackStarted(token)
                    }
                }
```

(그 아래 `if (token != sessionToken) return` 부터 `when (result)` 블록까지는 그대로.)

**(m) companion(418-438행)에 상수 추가** — `WARM_UP_TEXT` 아래:

```kotlin
            // 서버로 나가는 speechRate 는 항상 이 값이다. Gemini TTS 는 구조적 속도 파라미터가 없어
            // 서버가 이 숫자를 산문 힌트에 끼워 넣을 뿐이고(gemini.ts buildSynthesisBody), 모델은 그걸
            // 느슨하게만 따른다. 중립으로 고정해 합성을 결정론적으로 만들고, 실제 배속은 재생 시점에
            // 건다(TtsSpeedCalibration). 와이어 필드 자체는 서버 계약이라 남겨 둔다(백엔드 미배포).
            const val NEUTRAL_SYNTHESIS_RATE = 1.0f
```

- [ ] **Step 4: 통과를 확인한다**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*TtsPlaybackCoordinatorTest*'`
Expected: PASS — 42 tests (기존 37 + 교체 1 + 신규 5, 삭제 1).

기존 테스트가 하나라도 깨지면 **먼저 그 계약이 의도적으로 바뀐 건지 판단하라.** `a rate change reuses...` 외에 rate 를 키의 일부로 가정하는 테스트가 남아 있으면 안 된다.

- [ ] **Step 5: 전체 검증**

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL — 컴파일·detekt·ktlint·전체 단위테스트.

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt
git commit -m "feat(tts): synthesize server audio neutrally and apply speed at playback"
```

---

### Task 4: 계측 순수 헬퍼 (무음 트림 · WAV 코덱)

프로브의 계산 부분을 순수 함수로 분리해 JVM 테스트로 고정한다. 두 파일로 나눈다 — 하나는 *측정*, 하나는 *인코딩*.

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/audio/AudioMeasurement.kt`
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/audio/WavCodec.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/audio/AudioMeasurementTest.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/audio/WavCodecTest.kt`

**Interfaces:**
- Consumes: 없음(순수).
- Produces (Task 5 가 전부 사용):
  - `internal fun trimmedDurationSeconds(pcm: ByteArray, sampleRateHz: Int): Double`
  - `internal fun calibrationWeight(sourceSeconds: Double, referenceSeconds: Double): Float`
  - `internal data class WavPcm(val pcm: ByteArray, val sampleRateHz: Int)`
  - `internal fun parseWavPcm16(bytes: ByteArray): WavPcm?`
  - `internal fun encodeWavPcm16(pcm: ByteArray, sampleRateHz: Int): ByteArray`

- [ ] **Step 1: 실패하는 테스트를 쓴다 — 측정**

`android/app/src/test/kotlin/com/jjundev/oneclickeng/core/audio/AudioMeasurementTest.kt`:

```kotlin
package com.jjundev.oneclickeng.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

private const val RATE = 16_000

/** [frames] 프레임짜리 PCM16 LE 모노 버퍼. [amplitude] 가 0 이면 무음. */
private fun pcm(
    frames: Int,
    amplitude: Int,
): ByteArray {
    val out = ByteArray(frames * 2)
    for (f in 0 until frames) {
        out[f * 2] = (amplitude and 0xFF).toByte()
        out[f * 2 + 1] = ((amplitude shr 8) and 0xFF).toByte()
    }
    return out
}

class AudioMeasurementTest {
    @Test
    fun `duration counts only the speech region between the silent padding`() {
        // 0.5s 무음 + 1.0s 발화 + 0.25s 무음 → 발화 1.0s 만 세야 한다.
        val buffer = pcm(RATE / 2, 0) + pcm(RATE, 8000) + pcm(RATE / 4, 0)

        assertEquals(1.0, trimmedDurationSeconds(buffer, RATE), 0.02)
    }

    @Test
    fun `an all-silent buffer measures zero`() {
        assertEquals(0.0, trimmedDurationSeconds(pcm(RATE, 0), RATE), 1e-9)
    }

    @Test
    fun `low-level room tone below the threshold is treated as silence`() {
        // 진폭 100 은 임계(≈1% full-scale) 아래 → 발화로 세지 않는다.
        assertEquals(0.0, trimmedDurationSeconds(pcm(RATE, 100), RATE), 1e-9)
    }

    @Test
    fun `an empty buffer measures zero`() {
        assertEquals(0.0, trimmedDurationSeconds(ByteArray(0), RATE), 1e-9)
    }

    @Test
    fun `negative samples count as speech`() {
        // -8000 은 상위 바이트가 부호 확장된다 — 부호 처리를 틀리면 여기서 잡힌다.
        val buffer = pcm(RATE / 2, 0) + pcm(RATE, -8000 and 0xFFFF) + pcm(RATE / 2, 0)

        assertEquals(1.0, trimmedDurationSeconds(buffer, RATE), 0.02)
    }

    @Test
    fun `a slower source yields a weight above one`() {
        // 기준보다 오래 걸린다 = 더 느리다 → 가속해야 하므로 w > 1.
        assertEquals(1.25f, calibrationWeight(sourceSeconds = 2.5, referenceSeconds = 2.0), 1e-4f)
    }

    @Test
    fun `a faster source yields a weight below one`() {
        assertEquals(0.8f, calibrationWeight(sourceSeconds = 1.6, referenceSeconds = 2.0), 1e-4f)
    }
}
```

- [ ] **Step 2: 실패하는 테스트를 쓴다 — WAV**

`android/app/src/test/kotlin/com/jjundev/oneclickeng/core/audio/WavCodecTest.kt`:

```kotlin
package com.jjundev.oneclickeng.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WavCodecTest {
    @Test
    fun `encode then parse round-trips the samples and rate`() {
        val samples = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)

        val parsed = parseWavPcm16(encodeWavPcm16(samples, 24_000))!!

        assertArrayEquals(samples, parsed.pcm)
        assertEquals(24_000, parsed.sampleRateHz)
    }

    @Test
    fun `parse skips unknown chunks before data`() {
        // Android TTS 가 쓰는 WAV 에는 LIST/fact 같은 청크가 끼어들 수 있다.
        val samples = byteArrayOf(0x11, 0x22)
        val base = encodeWavPcm16(samples, 16_000)
        val listChunk =
            "LIST".toByteArray(Charsets.US_ASCII) +
                byteArrayOf(4, 0, 0, 0) + byteArrayOf(9, 9, 9, 9)
        // fmt 청크(12..35) 뒤, data 청크(36..) 앞에 끼워 넣는다.
        val withChunk = base.copyOfRange(0, 36) + listChunk + base.copyOfRange(36, base.size)

        val parsed = parseWavPcm16(withChunk)!!

        assertArrayEquals(samples, parsed.pcm)
        assertEquals(16_000, parsed.sampleRateHz)
    }

    @Test
    fun `parse rejects a non-RIFF buffer`() {
        assertNull(parseWavPcm16("not a wav file at all".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun `parse rejects a truncated header`() {
        assertNull(parseWavPcm16(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `parse recovers a data chunk whose declared size overruns the buffer`() {
        // 엔진이 스트리밍으로 쓰면 헤더의 길이가 실제보다 클 수 있다 — 남은 바이트를 그대로 쓴다.
        val encoded = encodeWavPcm16(byteArrayOf(1, 2, 3, 4), 16_000)
        val truncated = encoded.copyOfRange(0, encoded.size - 2)

        val parsed = parseWavPcm16(truncated)!!

        assertArrayEquals(byteArrayOf(1, 2), parsed.pcm)
    }
}
```

- [ ] **Step 3: 실패를 확인한다**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*AudioMeasurementTest*' --tests '*WavCodecTest*'`
Expected: FAIL — `Unresolved reference: trimmedDurationSeconds` 등 컴파일 에러

- [ ] **Step 4: 측정 헬퍼를 구현한다**

`android/app/src/main/kotlin/com/jjundev/oneclickeng/core/audio/AudioMeasurement.kt`:

```kotlin
package com.jjundev.oneclickeng.core.audio

import kotlin.math.abs

/**
 * 발화 속도 보정 계수를 실측하기 위한 오디오 계측(디버그 프로브 전용 — `TtsCalibrationReceiver`).
 *
 * 속도를 "듣지 않고" 재는 방법: **같은 문장**을 중립 속도로 합성해 길이(초)를 비교한다. 같은 텍스트라면
 * 길이는 발화 속도에 정확히 반비례하므로, 길이의 비가 곧 속도의 비다. 단, 원시 길이는 엔진이 붙이는
 * 앞뒤 무음 패딩까지 세므로 반드시 트림한 뒤 비교해야 한다.
 */

/** 발화로 칠 최소 진폭 — 16-bit full-scale 의 약 1%. 이 아래는 룸톤/패딩으로 본다. */
private const val SILENCE_THRESHOLD = 328

/** 트림 판정 윈도우(ms). 샘플 하나의 스파이크로 무음이 깨지지 않게 묶어서 본다. */
private const val WINDOW_MS = 10

private const val MS_PER_SECOND = 1000

/**
 * [pcm](16-bit LE 모노)에서 앞뒤 무음을 뺀 실제 발화 길이(초).
 * 전부 무음이거나 비었으면 0.0.
 */
internal fun trimmedDurationSeconds(
    pcm: ByteArray,
    sampleRateHz: Int,
): Double {
    require(sampleRateHz > 0) { "sampleRateHz must be positive, was $sampleRateHz" }
    val frames = pcm.size / 2
    if (frames == 0) return 0.0

    val window = (sampleRateHz * WINDOW_MS / MS_PER_SECOND).coerceAtLeast(1)
    var firstVoiced = -1
    var lastVoiced = -1
    var frame = 0
    while (frame < frames) {
        val end = minOf(frame + window, frames)
        if (peakAmplitude(pcm, frame, end) >= SILENCE_THRESHOLD) {
            if (firstVoiced < 0) firstVoiced = frame
            lastVoiced = end
        }
        frame = end
    }
    if (firstVoiced < 0) return 0.0
    return (lastVoiced - firstVoiced).toDouble() / sampleRateHz
}

/** `[fromFrame, toFrame)` 구간의 최대 절대 진폭. */
private fun peakAmplitude(
    pcm: ByteArray,
    fromFrame: Int,
    toFrame: Int,
): Int {
    var peak = 0
    for (frame in fromFrame until toFrame) {
        // little-endian s16: 하위 바이트는 부호 없이, 상위 바이트는 부호 확장해서 합친다.
        val low = pcm[frame * 2].toInt() and 0xFF
        val high = pcm[frame * 2 + 1].toInt()
        val magnitude = abs((high shl 8) or low)
        if (magnitude > peak) peak = magnitude
    }
    return peak
}

/**
 * 보정 계수 `w = D_source / D_reference`.
 *
 * 기준보다 느린 소스는 같은 문장에 더 오래 걸리므로 `w > 1` 이 되고, 재생이 그만큼 빨라져 기준에 붙는다.
 * 두 길이 모두 **같은 문장**을 **중립 속도**로 합성해 [trimmedDurationSeconds] 로 잰 값이어야 한다.
 */
internal fun calibrationWeight(
    sourceSeconds: Double,
    referenceSeconds: Double,
): Float {
    require(sourceSeconds > 0.0) { "sourceSeconds must be positive, was $sourceSeconds" }
    require(referenceSeconds > 0.0) { "referenceSeconds must be positive, was $referenceSeconds" }
    return (sourceSeconds / referenceSeconds).toFloat()
}
```

- [ ] **Step 5: WAV 코덱을 구현한다**

`android/app/src/main/kotlin/com/jjundev/oneclickeng/core/audio/WavCodec.kt`:

```kotlin
package com.jjundev.oneclickeng.core.audio

/**
 * 최소 WAV(RIFF/PCM16 모노) 리더·라이터 — 발화 속도 계측 프로브 전용(`TtsCalibrationReceiver`).
 *
 * 왜 필요한가: 온디바이스 TTS 는 `synthesizeToFile` 로 **WAV** 를 내놓고(길이를 재려면 파싱해야 한다),
 * 서버는 헤더 없는 **raw PCM** 을 준다(사람이 들어 보려면 헤더를 씌워야 한다). 양방향이 다 필요하다.
 * 범용 코덱이 아니다 — 16-bit 모노만 다룬다.
 */

private const val HEADER_SIZE = 44
private const val PCM_FORMAT = 1
private const val CHANNELS = 1
private const val BITS_PER_SAMPLE = 16
private const val BYTES_PER_FRAME = 2

/** 파싱된 WAV 본문 — raw PCM16 과 그 샘플레이트. */
internal data class WavPcm(
    val pcm: ByteArray,
    val sampleRateHz: Int,
) {
    // ByteArray 를 든 data class 는 equals/hashCode 가 참조 비교라 직접 구현한다(detekt ArrayPrimitive 관례).
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WavPcm) return false
        return sampleRateHz == other.sampleRateHz && pcm.contentEquals(other.pcm)
    }

    override fun hashCode(): Int = 31 * pcm.contentHashCode() + sampleRateHz
}

/**
 * RIFF 청크를 훑어 `fmt ` 의 샘플레이트와 `data` 본문을 뽑는다. WAV 가 아니거나 둘 중 하나가
 * 없으면 null. 마지막 `data` 청크의 선언 길이가 실제 버퍼를 넘으면(엔진이 스트리밍으로 쓴 파일)
 * 남은 바이트를 그대로 본문으로 쓴다.
 */
@Suppress("ReturnCount")
internal fun parseWavPcm16(bytes: ByteArray): WavPcm? {
    if (bytes.size < 12) return null
    if (ascii(bytes, 0) != "RIFF" || ascii(bytes, 8) != "WAVE") return null

    var sampleRate = 0
    var data: ByteArray? = null
    var pos = 12
    while (pos + 8 <= bytes.size) {
        val id = ascii(bytes, pos)
        val declared = readLeInt(bytes, pos + 4)
        val body = pos + 8
        if (declared < 0 || body + declared > bytes.size) {
            if (id == "data") data = bytes.copyOfRange(body, bytes.size)
            break
        }
        when (id) {
            // fmt 레이아웃: audioFormat(2) channels(2) sampleRate(4) …
            "fmt " -> if (body + 8 <= bytes.size) sampleRate = readLeInt(bytes, body + 4)
            "data" -> data = bytes.copyOfRange(body, body + declared)
        }
        pos = body + declared + (declared and 1) // 청크는 워드 정렬 — 홀수 길이면 패딩 1바이트
    }

    val pcm = data ?: return null
    if (sampleRate <= 0) return null
    return WavPcm(pcm, sampleRate)
}

/** raw PCM16 모노에 44바이트 표준 헤더를 씌운다(사람이 들어 볼 수 있게). */
internal fun encodeWavPcm16(
    pcm: ByteArray,
    sampleRateHz: Int,
): ByteArray {
    require(sampleRateHz > 0) { "sampleRateHz must be positive, was $sampleRateHz" }
    val byteRate = sampleRateHz * CHANNELS * BYTES_PER_FRAME
    val out = ByteArray(HEADER_SIZE + pcm.size)

    writeAscii(out, 0, "RIFF")
    writeLeInt(out, 4, HEADER_SIZE - 8 + pcm.size)
    writeAscii(out, 8, "WAVE")
    writeAscii(out, 12, "fmt ")
    writeLeInt(out, 16, 16) // PCM fmt 청크 길이
    writeLeShort(out, 20, PCM_FORMAT)
    writeLeShort(out, 22, CHANNELS)
    writeLeInt(out, 24, sampleRateHz)
    writeLeInt(out, 28, byteRate)
    writeLeShort(out, 32, CHANNELS * BYTES_PER_FRAME) // block align
    writeLeShort(out, 34, BITS_PER_SAMPLE)
    writeAscii(out, 36, "data")
    writeLeInt(out, 40, pcm.size)
    pcm.copyInto(out, HEADER_SIZE)
    return out
}

private fun ascii(
    bytes: ByteArray,
    at: Int,
): String = String(bytes, at, 4, Charsets.US_ASCII)

private fun readLeInt(
    bytes: ByteArray,
    at: Int,
): Int =
    (bytes[at].toInt() and 0xFF) or
        ((bytes[at + 1].toInt() and 0xFF) shl 8) or
        ((bytes[at + 2].toInt() and 0xFF) shl 16) or
        ((bytes[at + 3].toInt() and 0xFF) shl 24)

private fun writeAscii(
    out: ByteArray,
    at: Int,
    value: String,
) {
    value.toByteArray(Charsets.US_ASCII).copyInto(out, at)
}

private fun writeLeInt(
    out: ByteArray,
    at: Int,
    value: Int,
) {
    for (i in 0 until 4) out[at + i] = ((value shr (8 * i)) and 0xFF).toByte()
}

private fun writeLeShort(
    out: ByteArray,
    at: Int,
    value: Int,
) {
    for (i in 0 until 2) out[at + i] = ((value shr (8 * i)) and 0xFF).toByte()
}
```

- [ ] **Step 6: 통과를 확인한다**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*AudioMeasurementTest*' --tests '*WavCodecTest*'`
Expected: PASS (12 tests)

- [ ] **Step 7: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/core/audio/AudioMeasurement.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/core/audio/WavCodec.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/core/audio/AudioMeasurementTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/core/audio/WavCodecTest.kt
git commit -m "feat(audio): add silence-trimmed duration measurement and minimal WAV codec"
```

---

### Task 5: 디버그 계측 프로브 (`src/debug` 전용)

실측 하네스. **`src/debug` 소스셋에만** 둔다 — 릴리스 APK 에 들어가지 않고, 프로덕션 코드(설정 화면·ViewModel)를 전혀 건드리지 않아 `SettingsScreenScreenshotTest` 정합이 안전하다. adb 브로드캐스트로 트리거한다.

이 태스크는 I/O 껍데기다(계산은 Task 4 에서 이미 고정됨). 단위테스트 없음 — 검증은 Task 6 의 실기기 실행이다.

**Files:**
- Create: `android/app/src/debug/AndroidManifest.xml`
- Create: `android/app/src/debug/kotlin/com/jjundev/oneclickeng/debug/TtsCalibrationProbe.kt`
- Create: `android/app/src/debug/kotlin/com/jjundev/oneclickeng/debug/TtsCalibrationReceiver.kt`

**Interfaces:**
- Consumes: `LlmApi.tts(TtsRequest(payload = TtsPayload(text, gender, speechRate)))`, `TtsResponse.pcmBase64/sampleRate` (`core/network/LlmApi.kt`). `trimmedDurationSeconds` / `calibrationWeight` / `encodeWavPcm16` / `parseWavPcm16` (Task 4). `pickGenderVoiceName(voices, gender, language, country)` — `feature/session/tts/AndroidDeviceTts.kt:144` 의 `internal` 함수(같은 모듈이라 접근 가능).
- Produces: logcat 태그 `TtsCalib` 의 실측 리포트 + `<외부 files>/tts-calib/*.wav`. Task 6 이 소비한다.

- [ ] **Step 1: debug 매니페스트에 리시버를 등록한다**

`android/app/src/debug/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
  디버그 변이 전용. 발화 속도 보정 계수 실측 프로브를 adb 로 트리거한다.
  릴리스 매니페스트에는 병합되지 않으므로 출시 APK 에 이 리시버는 없다.
-->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <receiver
            android:name="com.jjundev.oneclickeng.debug.TtsCalibrationReceiver"
            android:exported="true" />
    </application>
</manifest>
```

- [ ] **Step 2: 프로브를 구현한다**

`android/app/src/debug/kotlin/com/jjundev/oneclickeng/debug/TtsCalibrationProbe.kt`:

```kotlin
package com.jjundev.oneclickeng.debug

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.jjundev.oneclickeng.core.audio.calibrationWeight
import com.jjundev.oneclickeng.core.audio.encodeWavPcm16
import com.jjundev.oneclickeng.core.audio.parseWavPcm16
import com.jjundev.oneclickeng.core.audio.trimmedDurationSeconds
import com.jjundev.oneclickeng.core.network.LlmApi
import com.jjundev.oneclickeng.core.network.TtsPayload
import com.jjundev.oneclickeng.core.network.TtsRequest
import com.jjundev.oneclickeng.feature.session.tts.VoiceInfo
import com.jjundev.oneclickeng.feature.session.tts.pickGenderVoiceName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.util.Base64
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

internal const val CALIB_TAG = "TtsCalib"

/**
 * 발화 속도 보정 계수 실측 프로브 — **디버그 전용**.
 *
 * 같은 참조 문장을 세 소스(Gemini `Kore`, Gemini `Puck`, 온디바이스 TTS)로 **중립 속도**에 합성해
 * 무음 트림 후 길이를 재고, 기준(`Kore`) 대비 비율로 계수를 계산한다. 결과는 logcat(`TtsCalib`)에
 * 찍고 WAV 를 외부 files 디렉터리에 덤프한다 — 숫자는 계수 산출용, WAV 는 사람이 직접 들어
 * "정말 같은 속도로 들리는지" 확인하는 용도다.
 *
 * 산출된 계수는 `TtsSpeedCalibration.WEIGHT_*` 상수에 **손으로** 옮겨 적는다. 자동 반영하지 않는
 * 이유: 계수는 빌드 타임 상수여야 하고(런타임 측정은 매 실행 네트워크 비용), 사람의 청취 확인을
 * 반드시 거쳐야 하기 때문이다.
 *
 * Gemini 모델이 바뀌면(`functions/src/config/models.ts`) 재측정해야 한다.
 */
@Singleton
class TtsCalibrationProbe
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val api: LlmApi,
    ) {
        /** 참조 문장 — 길이를 섞어 한 문장의 우연을 지운다. 숫자·약어는 엔진마다 읽는 법이 달라 제외. */
        private val referenceLines =
            listOf(
                "Hi, welcome in. What can I get started for you today?",
                "Sure, I can help with that. Could you tell me a little more about what you need?",
                "No problem at all. Your order will be ready in about five minutes.",
            )

        /** 한 문장에 대한 세 소스의 발화 길이(초). 잰 데 실패하면 null. */
        private data class LineDurations(
            val kore: Double?,
            val puck: Double?,
            val device: Double?,
        )

        @Suppress("TooGenericExceptionCaught")
        suspend fun run() {
            val outDir = File(context.getExternalFilesDir(null), "tts-calib").apply { mkdirs() }
            Log.i(CALIB_TAG, "=== 발화 속도 보정 계측 시작 (기준 = Gemini Kore 자연 속도) ===")
            Log.i(CALIB_TAG, "wav dir = $outDir")

            val measured =
                referenceLines.mapIndexed { index, line ->
                    val durations = measureLine(index, line, outDir)
                    Log.i(
                        CALIB_TAG,
                        "line ${index + 1} | kore=${fmt(durations.kore)} puck=${fmt(durations.puck)} " +
                            "device=${fmt(durations.device)}",
                    )
                    durations
                }

            report("WEIGHT_SERVER_MALE", measured) { it.puck }
            report("WEIGHT_DEVICE", measured) { it.device }
            Log.i(CALIB_TAG, "WEIGHT_SERVER_FEMALE = 1.000 (기준 보이스 — 정의상 고정)")
            Log.i(CALIB_TAG, "=== 계측 끝 — 위 값을 TtsSpeedCalibration 상수에 옮겨 적으세요 ===")
        }

        private suspend fun measureLine(
            index: Int,
            line: String,
            outDir: File,
        ): LineDurations {
            val slot = index + 1
            return LineDurations(
                kore = measureServer(line, gender = "female", File(outDir, "line${slot}_kore.wav")),
                puck = measureServer(line, gender = "male", File(outDir, "line${slot}_puck.wav")),
                device = measureDevice(line, gender = "female", File(outDir, "line${slot}_device.wav")),
            )
        }

        /** 서버 합성(중립 속도) → raw PCM 길이 측정 + WAV 덤프. */
        @Suppress("TooGenericExceptionCaught")
        private suspend fun measureServer(
            text: String,
            gender: String,
            outFile: File,
        ): Double? =
            try {
                val response =
                    api.tts(TtsRequest(payload = TtsPayload(text = text, gender = gender, speechRate = 1.0f)))
                val pcm = Base64.getDecoder().decode(response.pcmBase64)
                outFile.writeBytes(encodeWavPcm16(pcm, response.sampleRate))
                trimmedDurationSeconds(pcm, response.sampleRate).takeIf { it > 0.0 }
            } catch (e: Exception) {
                Log.w(CALIB_TAG, "server 합성 실패 ($gender): ${e.message}")
                null
            }

        /** 온디바이스 합성(배속 1.0) → WAV 로 받아 파싱해 길이 측정. */
        @Suppress("TooGenericExceptionCaught")
        private suspend fun measureDevice(
            text: String,
            gender: String,
            outFile: File,
        ): Double? =
            try {
                if (!synthesizeDeviceToFile(text, gender, outFile)) {
                    null
                } else {
                    parseWavPcm16(outFile.readBytes())
                        ?.let { trimmedDurationSeconds(it.pcm, it.sampleRateHz) }
                        ?.takeIf { it > 0.0 }
                }
            } catch (e: Exception) {
                Log.w(CALIB_TAG, "device 합성 실패: ${e.message}")
                null
            }

        /**
         * 프로덕션 [com.jjundev.oneclickeng.feature.session.tts.AndroidDeviceTts] 와 **같은 보이스**를
         * 골라 파일로 합성한다(같은 [pickGenderVoiceName] 를 씀). 재생이 아니라 파일이 필요해
         * `speak` 대신 `synthesizeToFile` 을 쓴다 — 정확한 샘플 수를 얻어야 길이가 정확하다.
         */
        private suspend fun synthesizeDeviceToFile(
            text: String,
            gender: String,
            outFile: File,
        ): Boolean {
            val ready = CompletableDeferred<Boolean>()
            val engine = TextToSpeech(context) { status -> ready.complete(status == TextToSpeech.SUCCESS) }
            try {
                if (!ready.await()) {
                    Log.w(CALIB_TAG, "device TTS 초기화 실패")
                    return false
                }
                val lang = engine.setLanguage(Locale.US)
                if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(CALIB_TAG, "device TTS 영어 데이터 없음 — 기기 설정에서 설치 후 재실행하세요")
                    return false
                }
                selectVoice(engine, gender)
                engine.setSpeechRate(1.0f) // 중립 — 보정 계수를 재는 중이다
                return awaitSynthesis(engine, text, outFile)
            } finally {
                engine.shutdown()
            }
        }

        private fun selectVoice(
            engine: TextToSpeech,
            gender: String,
        ) {
            val voices = runCatching { engine.voices }.getOrNull()?.toList() ?: return
            val infos =
                voices.mapNotNull { voice ->
                    val locale = voice.locale ?: return@mapNotNull null
                    VoiceInfo(voice.name, locale.language, locale.country)
                }
            val chosen =
                pickGenderVoiceName(infos, gender, Locale.US.language, Locale.US.country) ?: return
            voices.firstOrNull { it.name == chosen }?.let { engine.voice = it }
        }

        private suspend fun awaitSynthesis(
            engine: TextToSpeech,
            text: String,
            outFile: File,
        ): Boolean {
            val done = CompletableDeferred<Boolean>()
            val utteranceId = "calib_${outFile.name}"
            engine.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(id: String?) = Unit

                    override fun onDone(id: String?) {
                        if (id == utteranceId) done.complete(true)
                    }

                    @Deprecated("deprecated in API 21", ReplaceWith(""))
                    override fun onError(id: String?) {
                        if (id == utteranceId) done.complete(false)
                    }

                    override fun onError(
                        id: String?,
                        errorCode: Int,
                    ) {
                        if (id == utteranceId) done.complete(false)
                    }
                },
            )
            val queued = engine.synthesizeToFile(text, Bundle(), outFile, utteranceId)
            if (queued != TextToSpeech.SUCCESS) return false
            return done.await()
        }

        /** 문장별 비율의 평균을 계수로 보고한다 — 한 문장의 우연한 편차를 눌러 준다. */
        private fun report(
            name: String,
            measured: List<LineDurations>,
            select: (LineDurations) -> Double?,
        ) {
            val ratios =
                measured.mapNotNull { line ->
                    val source = select(line) ?: return@mapNotNull null
                    val reference = line.kore ?: return@mapNotNull null
                    calibrationWeight(source, reference)
                }
            if (ratios.isEmpty()) {
                Log.w(CALIB_TAG, "$name = 측정 불가 (합성 실패 — 위 경고 확인)")
                return
            }
            val mean = ratios.average()
            val spread = ratios.maxOrNull()!! - ratios.minOrNull()!!
            Log.i(
                CALIB_TAG,
                // Locale.US 명시: detekt ImplicitDefaultLocale 위반 방지 + 기기 로케일이 소수점을
                // 쉼표로 찍어 상수에 옮겨 적을 때 헷갈리는 일이 없게.
                String.format(Locale.US, "%s = %.3f  (n=%d, 문장 간 편차=%.3f)", name, mean, ratios.size, spread),
            )
        }

        private fun fmt(seconds: Double?): String =
            seconds?.let { String.format(Locale.US, "%.3fs", it) } ?: "실패"
    }
```

- [ ] **Step 3: 리시버를 구현한다**

`android/app/src/debug/kotlin/com/jjundev/oneclickeng/debug/TtsCalibrationReceiver.kt`:

```kotlin
package com.jjundev.oneclickeng.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [TtsCalibrationProbe] 의 adb 트리거 — **디버그 변이 전용**(`src/debug/AndroidManifest.xml`).
 *
 * 실행:
 * ```
 * adb shell am broadcast -a com.jjundev.oneclickeng.RUN_TTS_CALIBRATION \
 *   -n com.jjundev.oneclickeng/com.jjundev.oneclickeng.debug.TtsCalibrationReceiver
 * ```
 *
 * **앱이 실행 중이고 로그인된 상태여야 한다** — 서버 합성이 살아 있는 Hilt 그래프의 인증 토큰을 쓴다.
 * 결과는 `adb logcat -s TtsCalib`, WAV 는 `adb pull` (경로는 로그 첫 줄에 찍힌다).
 *
 * 설정 화면에 디버그 행을 넣지 않은 이유: `SettingsContent` 는 Roborazzi 스크린샷 seam 이라
 * 행이 하나만 늘어도 프로토타입 정합 테스트가 깨진다.
 */
@AndroidEntryPoint
class TtsCalibrationReceiver : BroadcastReceiver() {
    @Inject
    lateinit var probe: TtsCalibrationProbe

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION) return
        // goAsync: 계측은 네트워크 6회 + 온디바이스 합성 3회라 onReceive 의 10초 예산을 넘는다.
        val pending = goAsync()
        Log.i(CALIB_TAG, "계측 요청 수신 — 수십 초 걸립니다")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                probe.run()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val ACTION = "com.jjundev.oneclickeng.RUN_TTS_CALIBRATION"
    }
}
```

- [ ] **Step 4: 컴파일과 정적 분석을 확인한다**

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL — 디버그 소스셋까지 컴파일되고 detekt/ktlint 통과, 기존 테스트 전부 통과.

`pickGenderVoiceName` / `VoiceInfo` 가 `internal` 이라 import 가 안 되면, 같은 모듈이므로 접근은 되지만 패키지가 다르니 import 가 필요하다 — 위 import 문이 맞는지 확인하라(`com.jjundev.oneclickeng.feature.session.tts`).

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/debug
git commit -m "feat(debug): add adb-triggered TTS speed calibration probe"
```

---

### Task 6: 실측 → 계수 확정 → 실기기 검증

여기서 실제 숫자가 나온다. **이 태스크는 실기기가 필요하다** — 온디바이스 TTS 는 에뮬레이터/Robolectric 으로 대체 불가고, `AudioTrack.setPlaybackParams` 도 실기기에서만 진짜로 확인된다.

> **참고:** Task 1~5 는 이미 실행됐고, 그 과정에서 최종 리뷰를 반영해 **계수 구조가 바뀌었다** — 단일 `WEIGHT_DEVICE` 가 `WEIGHT_DEVICE_FEMALE`/`WEIGHT_DEVICE_MALE` 로 갈라졌고(단말도 성별별로 다른 보이스를 쓰므로), `MIN_EFFECTIVE_RATE` 는 0.5 → **0.25** 로 내려갔다(0.5 는 슬라이더 최소와 같아 계수가 1.0 미만이면 슬라이더 하단이 통째로 미보정으로 되돌아갔다). 아래 Task 6 본문은 그 최종 상태 기준으로 갱신돼 있다. Task 1~5 의 스텝 본문은 **실행 당시의 기록**이므로 옛 이름(`WEIGHT_DEVICE` 등)이 남아 있다 — 그건 히스토리이지 지시가 아니다.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/audio/TtsSpeedCalibration.kt` (`WEIGHT_SERVER_MALE`, `WEIGHT_DEVICE_FEMALE`, `WEIGHT_DEVICE_MALE` — `WEIGHT_SERVER_FEMALE` 은 기준이라 1.0 고정)
- Modify: `docs/design/tts.md` §2

**Interfaces:**
- Consumes: `TtsCalibrationProbe` 의 logcat 리포트 (Task 5).
- Produces: 확정된 `WEIGHT_*` 상수 값.

- [x] **Step 1: 디버그 빌드를 설치하고 로그인한 채로 둔다** — 완료(2026-07-17, 실기기 Samsung SM-S911N, 게스트 로그인).

```bash
scripts/verify-android.sh :app:installDebug
adb shell am start -n com.jjundev.oneclickeng/.MainActivity
```

앱이 열리고 (게스트든 Google 이든) 로그인된 상태를 확인한다. 서버 합성이 그 세션의 인증 토큰을 쓴다.

- [x] **Step 2: 계측을 돌린다** — 완료(2026-07-17). 1회차는 3개 지표 전부 편차>0.05 (0.114~0.180) 로 "멈추고 보고" 게이트에 걸림 → 위 ⚠️ 권장대로 2회 더 반복(총 3회). DEVICE 는 3회 내내 문장별 길이가 완전히 동일(결정론적) 확인 — 편차는 대부분 분모(Kore) 자체의 실행 간 흔들림(최대 ~12%)에서 왔다. Puck/Kore 9표본 평균은 0.991(재현 가능한 성별차 없음). 값 채택은 사용자 결정(아래 Step 4).

```bash
adb logcat -c
adb shell am broadcast -a com.jjundev.oneclickeng.RUN_TTS_CALIBRATION \
  -n com.jjundev.oneclickeng/com.jjundev.oneclickeng.debug.TtsCalibrationReceiver
adb logcat -s TtsCalib
```

Expected: 아래 모양의 출력(숫자는 실측값):

```
TtsCalib: === 발화 속도 보정 계측 시작 (기준 = Gemini Kore 자연 속도) ===
TtsCalib: wav dir = /storage/emulated/0/Android/data/com.jjundev.oneclickeng/files/tts-calib
TtsCalib: line 1 | kore=2.841s puck=2.615s deviceFemale=2.190s deviceMale=2.204s
TtsCalib: line 2 | kore=4.102s puck=3.780s deviceFemale=3.160s deviceMale=3.181s
TtsCalib: line 3 | kore=3.055s puck=2.810s deviceFemale=2.360s deviceMale=2.372s
TtsCalib: WEIGHT_SERVER_MALE = 0.921  (n=3, 문장 간 편차=0.008)
TtsCalib: WEIGHT_DEVICE_FEMALE = 0.772  (n=3, 문장 간 편차=0.011)
TtsCalib: WEIGHT_DEVICE_MALE = 0.777  (n=3, 문장 간 편차=0.009)
TtsCalib: WEIGHT_SERVER_FEMALE = 1.000 (기준 보이스 — 정의상 고정)
```

**판정 기준:**
- `측정 불가` 나 `실패` 가 보이면 그 경고를 먼저 해결한다(로그인 안 됨 / 영어 TTS 데이터 미설치 / 네트워크).
- **부분 측정 경고(`Log.w`, "이 값을 상수로 옮기지 말고 재측정하세요")가 보이면 그 값을 쓰지 마라.** 문장 3개 중 일부만 측정되면 표본이 1개여도 `문장 간 편차` 가 **항상 0.000** 으로 찍혀 아래 편차 게이트를 거짓 통과한다. 그래서 부분 측정은 `Log.i` 가 아니라 `Log.w` 로 분리해 두었다.
- **문장 간 편차가 0.05 를 넘으면** 그 소스는 문장마다 속도가 흔들린다는 뜻이다. 단일 상수로 못 잡으니 **멈추고 보고하라** — 계수를 억지로 채우지 말 것.
- `WEIGHT_SERVER_MALE` 이 1.00 ± 0.03 안이면 Puck 과 Kore 의 자연 속도 차이가 유의미하지 않다는 뜻이다. 그래도 실측값을 그대로 적는다(상수가 우연히 같아질 뿐, 계수 구조는 유지).
- `WEIGHT_DEVICE_FEMALE` 과 `WEIGHT_DEVICE_MALE` 이 사실상 같게 나오면 그것도 **정보**다(단말 엔진의 두 보이스가 같은 속도라는 뜻). 그래도 각각 실측값을 적는다.

> **⚠️ 최종 리뷰가 지적한 계측 한계 — 이 계획의 전제를 흔들 수 있는 항목:**
> 이 프로브는 문장당 **1회만** 합성한다. 그런데 이 작업의 근본 원인 자체가 "Gemini 가 속도 힌트를
> 라인마다 다르게 따른다"였다. 만약 Gemini 의 **중립 속도조차 실행할 때마다 흔들린다면** 어떤 단일
> 상수로도 SERVER 경로를 고칠 수 없다 — 그런데 지금의 `문장 간 편차` 지표는 *문장 간 차이*와
> *Gemini 의 실행 간 비결정성*을 구분하지 못한다(각 문장이 딱 한 번씩만 합성되므로). 편차 게이트는
> 운 좋게 통과할 수 있다.
> **권장:** 계측을 **2~3회 반복 실행**해 같은 문장의 회차 간 값이 얼마나 흔들리는지 먼저 보라.
> 회차 간 편차가 문장 간 편차만큼 크면, 계수 상수 접근 자체를 재검토해야 한다(그 경우 멈추고 보고).

- [ ] **Step 3: WAV 를 뽑아 사람이 직접 듣는다** — **미실행(의도적 대체).** Step 2 의 3회 반복이 "숫자와 귀의 인상이 같은 방향인가"라는 이 스텝의 목적을 정량적으로(그리고 더 강하게) 이미 답했다 — DEVICE 는 3회 내내 바이트 단위로 재현되고, SERVER 는 회차 간 최대 25% 흔들리는 게 직접 측정으로 나왔다. 원본 WAV 를 직접 pull 해서 듣는 건 생략했다.

```bash
adb pull /storage/emulated/0/Android/data/com.jjundev.oneclickeng/files/tts-calib ./tts-calib
open ./tts-calib
```

`line1_kore.wav` / `line1_puck.wav` / `line1_device_female.wav` / `line1_device_male.wav` 를 차례로 듣는다. 이건 **보정 전 원본**이다 — 속도가 서로 다르게 들리는 게 정상이고, 숫자(위 로그)와 귀의 인상이 같은 방향인지 확인하는 게 목적이다. 예: `device_female` 의 길이가 가장 짧게 측정됐다면 실제로 가장 빠르게 들려야 한다. 어긋나면 트림 임계나 측정이 틀린 것이므로 멈추고 보고한다.

단말 WAV 가 깨져 들리거나 길이가 터무니없으면 **OEM TTS 엔진이 비표준 WAV 컨테이너를 썼을 가능성**이 있다(`parseWavPcm16` 은 그 경우 조용히 실패한다) — 이건 보고 대상이다.

반면 `device_female` 과 `device_male` 이 **똑같이 들리는 건 정상일 수 있다.** 기기에 en-US 보이스가 하나뿐이면 `pickGenderVoiceName` 의 Pass 4 가 양쪽에 같은 보이스를 돌려주고, 프로브는 두 단말 계수를 동일하게 보고한다 — 그건 **올바른 출력이지 측정 실패가 아니다**(그 기기에선 실제로 성별별 속도 차이가 없는 것). 쫓지 말 것.

- [x] **Step 4: 계수를 상수에 옮겨 적는다** — 완료(commit 74e5cc4). 실측: `WEIGHT_SERVER_MALE=0.99`(9표본 평균, 재현 가능한 차이 없음 — 사용자 확인 후 잡음 평균으로 채택), `WEIGHT_DEVICE_FEMALE=0.855`, `WEIGHT_DEVICE_MALE=0.707`(둘 다 3회 반복으로 재현성 확인).

`TtsSpeedCalibration.kt` 의 **세 상수**(`WEIGHT_SERVER_MALE`·`WEIGHT_DEVICE_FEMALE`·`WEIGHT_DEVICE_MALE`)를 Step 2 로그의 값으로 바꾼다. `WEIGHT_SERVER_FEMALE` 은 기준 보이스라 1.0 고정 — 건드리지 않는다. **아래 숫자는 예시다 — 실제 측정값으로 대체하라.** 측정 근거를 주석에 남긴다:

```kotlin
    /**
     * Gemini `Puck`(gender == male) 계수.
     * 실측 2026-07-17(`TtsCalibrationProbe`, 참조 문장 3개 평균): Puck 이 Kore 보다 약간 빠르다.
     */
    const val WEIGHT_SERVER_MALE = 0.921f

    /**
     * 온디바이스 Android TTS, 여성 보이스 계수.
     * 실측 2026-07-17(`TtsCalibrationProbe`, 참조 문장 3개 평균): 엔진 기본 속도가 Gemini 보다
     * 뚜렷하게 빨라 늦춰야 기준에 붙는다. 기기·엔진마다 기본 속도가 다르므로 측정 기기가 바뀌면
     * 재측정 대상이다.
     */
    const val WEIGHT_DEVICE_FEMALE = 0.772f

    /**
     * 온디바이스 Android TTS, 남성 보이스 계수.
     * 실측 2026-07-17(`TtsCalibrationProbe`, 참조 문장 3개 평균). 여성 보이스와 따로 재는 이유:
     * `AndroidDeviceTts.selectGenderVoice` 가 성별별로 **다른 보이스**를 고르고, 보이스마다 자연
     * 속도가 다를 수 있기 때문이다(한쪽만 재서 양쪽에 적용하면 남성 경로가 조용히 어긋난다).
     */
    const val WEIGHT_DEVICE_MALE = 0.777f
```

**계수를 적기 전 확인:** 어떤 계수든 `MIN_EFFECTIVE_RATE`(0.25) / `MAX_EFFECTIVE_RATE`(2.0) 경계에 슬라이더 실사용 범위(0.5~1.5)를 곱한 값이 닿지 않아야 한다. 즉 계수가 **0.5 미만이거나 1.33 초과**로 나오면 클램프가 정상 범위 안에서 발동해 **슬라이더 끝단이 조용히 미보정으로 되돌아간다.** 그런 값이 나오면 상수를 적지 말고 멈추고 보고하라(경계를 넓혀야 하는지 판단 필요).

- [x] **Step 5: 단위테스트가 계수 변경을 견디는지 확인한다** — 완료. `scripts/verify-android.sh` BUILD SUCCESSFUL, 92 tasks. 동어반복이던 2개 테스트도 실제 계수에서 정상 통과(라우팅 정상 확인).

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL — **전부 통과해야 한다.** Task 1/3 의 테스트는 계수 값이 아니라 계약(클램프·성별 라우팅·단조성)에 걸려 있으므로 숫자가 바뀌어도 깨지지 않는다. 여기서 깨진다면 그 테스트가 계수를 하드코딩한 것이니, 상수를 참조하도록 고쳐라(계수를 되돌리지 말 것).

**이 Step 이 갖는 숨은 의미:** 계수가 전부 1.0 이던 동안 `server playback applies the voice-specific calibration weight` 와 `device path applies the device calibration weight` 두 테스트는 **동어반복**이었다(1.0 을 곱하나 안 곱하나 같으므로, 성별 라우팅이 반전돼 있어도 통과). 계수가 실제로 갈라지는 지금이 그 두 테스트가 처음으로 이빨을 갖는 순간이다 — 여기서 라우팅 버그가 잡히면 그건 **테스트가 제 일을 한 것**이니 계수가 아니라 라우팅을 고쳐라.

- [x] **Step 6: 실기기에서 두 경로를 A/B 로 듣는다** — 완료(2026-07-17, Samsung SM-S911N). 사용자 청취로 "비슷하다" 종합 확인(1.0x/1.5x/0.5x·남성 상황 포함, 항목별 개별 보고는 아니고 전체적인 인상 확인). logcat 재검증(중간에 USB 일시 단절 있었으나 재연결 후 버퍼 확보, 총 14분·11만 줄): `AudioTrack`/`setPlaybackParams` 관련 예외·경고 0건(0.5x 하한 포함 거부 없음), `FATAL EXCEPTION` 0건. 캐시 재사용은 정황 증거로 확인(테스트 구간 전체에서 TTS 크기 응답이 4건뿐 — 클릭 단위 대조는 아님).

```bash
scripts/verify-android.sh :app:installDebug
adb shell am start -n com.jjundev.oneclickeng/.MainActivity
```

세션을 하나 시작하고, 설정에서 배속을 **1.0x** 로 둔 채:
1. 음질 = **"자연스러운 발음"(SERVER)** 으로 상대 대사를 듣는다.
2. 설정에서 음질 = **"빠른 발음"(DEVICE)** 으로 바꾸고 같은 대사를 다시 듣는다("다시 듣기" 또는 새 세션).
3. **두 경로의 발화 속도가 같게 들려야 한다.** 목소리와 자연스러움은 당연히 다르다 — 판정 대상은 오직 속도다.
4. 배속을 **1.5x**(상한)로 올려 같은 A/B 를 반복한다. 양쪽 모두 빨라지고, 여전히 서로 비슷해야 한다.
5. **배속을 `0.5x`(하한)로 내려 같은 A/B 를 반복한다.** ← 빠뜨리지 말 것. 슬라이더 0.5 × 계수(~0.77)면 실제로 `AudioTrack.setPlaybackParams` 에 **~0.39** 가 들어가는데, 이게 기기가 요구받는 **가장 낮은 배속**이라 거부당할 가능성이 가장 높다. 거부되면 `applySpeed` 가 조용히 1.0x 로 폴백하는데(`Log.w` 만 남는다), 하필 0.5x 설정에서의 1.0x 폴백은 **귀에 확 띌 만큼 어긋난다**. 1.0/1.5 만 확인하면 이 구간을 영영 못 본다(최종 리뷰 지적). 하단에서도 두 경로가 같게 들려야 하고, 아래 logcat 확인을 **이 배속에서** 반드시 하라.
6. 상대역이 **남성**인 상황(예: 남성 점원)으로 세션을 하나 더 돌려 **Puck(서버-남성)** 과 **단말-남성 보이스** 를 둘 다 확인한다. 남성 상황에서도 SERVER↔DEVICE 속도가 같게 들려야 한다.
7. **1.5x 에서 마지막 음절이 잘리는지** 특히 주의해서 듣는다. `PcmAudioPlayer` 는 마지막 *소스* 프레임에서 완료 마커를 받고 곧바로 트랙을 release 하는데, 시간축 신축(Sonic)은 소스를 렌더 출력보다 앞서 소비하므로 이론상 꼬리가 잘릴 수 있다(최종 리뷰 지적). 1.0x 에선 안 생기고 1.5x 에서만 드러난다. 잘리면 보고하라.

동시에 배속 회귀가 없는지 확인:

```bash
adb logcat -c && adb logcat | grep -iE "playbackParams|AudioTrack|배속"
```
Expected: `setPlaybackParams` 관련 예외/경고가 없다. `applySpeed` 는 실패 시 `Log.w` 로 거부된 배속값을 남기므로(크래시는 막지만 배속은 조용히 1.0x 로 무시된다), **그 경고가 보이면 그 기기가 해당 배속을 지원하지 않는 것**이다 — `MAX_EFFECTIVE_RATE`/`MIN_EFFECTIVE_RATE` 를 조정하고 재확인한다.

캐시 재사용도 확인 — 세션 중 속도를 바꾸고 "다시 듣기" 를 눌렀을 때 **즉시** 재생돼야 한다(재합성 지연 없음):

```bash
adb logcat -c && adb logcat | grep -i "okhttp\|llm"
```
Expected: 속도 변경 후 "다시 듣기" 에서 새 `/llm` 요청이 **나가지 않는다.**

- [x] **Step 7: 설계 문서를 갱신한다** — 완료(commit 74e5cc4). §2 에 실측 결과·Gemini 비결정성 한계 추가 기재.

`docs/design/tts.md` §2 의 속도 줄(12행)을 교체한다. 기존:

```markdown
- 말하기 속도: 기본 **1.0x**, 범위 0.5~1.5x(설정 슬라이더, 옛 clamp 계승). 0.9x는 오버라이드 후보.
```

교체:

```markdown
- 말하기 속도: 기본 **1.0x**, 범위 0.5~1.5x(설정 슬라이더, 옛 clamp 계승). 0.9x는 오버라이드 후보.
- **경로별 속도 보정(2026-07-17):** 같은 슬라이더 값이 경로마다 다른 절대 속도를 냈다 — DEVICE 는
  `setSpeechRate` 로 엔진 배속을 직접 먹지만, SERVER(Gemini TTS `generateContent`)는 구조적 속도
  파라미터가 없어 산문 힌트("Aim for speaking speed multiplier N")에 의존했고 모델이 이를 느슨하게만
  따랐다. 이제:
  - SERVER 는 **항상 중립(1.0)으로 합성**하고(`TtsPlaybackCoordinator.NEUTRAL_SYNTHESIS_RATE`),
    받은 PCM 을 `AudioTrack.setPlaybackParams`(피치 보존)로 **재생 시점에 배속**한다. 산문 힌트는
    중립값이 나가 사실상 무력화된다(서버 계약·`functions/` 는 미변경 — 힌트 문장 제거는 후속).
  - 두 경로 모두 `TtsSpeedCalibration` 의 실측 계수를 곱해 절대 속도를 맞춘다.
    **기준점 = Gemini `Kore` 자연 속도 = 1.0x**(Gemini 를 억지로 가속하지 않아 자연스러움 보존).
    계수 `w = D_source / D_reference`(동일 문장·중립 속도·무음 트림 후 길이의 비).
  - **계수는 보이스별 4개**(`WEIGHT_SERVER_FEMALE`=Kore=1.0 고정 · `WEIGHT_SERVER_MALE`=Puck ·
    `WEIGHT_DEVICE_FEMALE` · `WEIGHT_DEVICE_MALE`). 단말도 성별별로 다른 보이스를 고르므로
    (`AndroidDeviceTts.selectGenderVoice`) 한쪽만 재서 양쪽에 쓰면 남성 경로가 조용히 어긋난다.
  - **클램프 경계는 "슬라이더 범위 × 그럴듯한 계수 범위" 로 정한다**(`MIN_EFFECTIVE_RATE`=0.25 /
    `MAX_EFFECTIVE_RATE`=2.0). 정상 범위 안에서 클램프가 걸리면 그 구간이 조용히 미보정으로
    되돌아가므로 — 하한을 슬라이더 최소(0.5)와 같게 두면 계수가 1.0 미만인 순간 슬라이더 하단
    전체가 먹힌다(초기 설계 결함, 최종 리뷰에서 교정).
  - 계수는 디버그 프로브(`TtsCalibrationProbe`, adb 브로드캐스트)로 실측해 상수에 손으로 적는다.
    **Gemini TTS 모델이 바뀌거나 측정 기기가 바뀌면 재측정 대상.**
  - 부수 효과: 배속이 캐시 키에서 빠져 **합성 1회가 모든 배속을 커버**한다(§3 의 세션 내 캐시 —
    세션 중 속도 변경이 재합성을 유발하지 않는다).
```

- [x] **Step 8: 커밋** — 완료(commit 74e5cc4, Step 4/7 과 함께 한 커밋으로 반영).

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/core/audio/TtsSpeedCalibration.kt \
        docs/design/tts.md
git commit -m "feat(tts): land measured per-path speed calibration weights"
```

---

## 완료 기준

- 배속 1.0x 에서 SERVER 와 DEVICE 의 발화 속도가 **귀로 같게** 들린다(Task 6 Step 6).
- 배속 1.5x 에서도 두 경로가 서로 비슷하고, 양쪽 다 실제로 빨라진다.
- 세션 중 속도를 바꿔도 재합성 네트워크 호출이 없다.
- `scripts/verify-android.sh` 가 통과한다.
- `functions/` 에 변경이 없다(`git diff --stat master -- functions/` 가 비어 있다).

## 후속(이 계획의 범위 밖)

- 서버 `buildSynthesisBody` 의 산문 속도 힌트 문장 제거 + `TtsPayload.speechRate` 폐기 — 백엔드 배포가 필요하므로 분리.
- 기기별 온디바이스 엔진 기본 속도 편차 — 현재는 측정 기기 기준 단일 상수. 기기마다 크게 다르다는 근거가 나오면 런타임 1회 측정 seam 을 검토(지금은 YAGNI).

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
 * 같은 참조 문장을 네 보이스(Gemini `Kore`, Gemini `Puck`, 온디바이스 TTS 여성/남성)로 **중립 속도**에
 * 합성해 무음 트림 후 길이를 재고, 기준(`Kore`) 대비 비율로 계수를 계산한다. 온디바이스도 서버와
 * 대칭으로 성별별로 측정한다 — `playFromDevice` 가 턴의 gender 로 보이스를 고르므로(`AndroidDeviceTts.
 * selectGenderVoice`), 한 계수로 두 보이스를 덮으면 측정에 쓰이지 않은 쪽이 어긋난다. 결과는
 * logcat(`TtsCalib`)에 찍고 WAV 를 외부 files 디렉터리에 덤프한다 — 숫자는 계수 산출용, WAV 는
 * 사람이 직접 들어 "정말 같은 속도로 들리는지" 확인하는 용도다.
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

        /** 한 문장에 대한 네 소스의 발화 길이(초). 잰 데 실패하면 null. */
        private data class LineDurations(
            val kore: Double?,
            val puck: Double?,
            val deviceFemale: Double?,
            val deviceMale: Double?,
        )

        @Suppress("TooGenericExceptionCaught")
        suspend fun run() {
            val baseDir = context.getExternalFilesDir(null)
            if (baseDir == null) {
                Log.e(
                    CALIB_TAG,
                    "외부 저장소를 사용할 수 없습니다(getExternalFilesDir == null) — " +
                        "WAV 를 덤프할 수 없어 계측을 중단합니다.",
                )
                return
            }
            val outDir = File(baseDir, "tts-calib").apply { mkdirs() }
            Log.i(CALIB_TAG, "=== 발화 속도 보정 계측 시작 (기준 = Gemini Kore 자연 속도) ===")
            Log.i(CALIB_TAG, "wav dir = $outDir")

            val measured =
                referenceLines.mapIndexed { index, line ->
                    val durations = measureLine(index, line, outDir)
                    Log.i(
                        CALIB_TAG,
                        "line ${index + 1} | kore=${fmt(durations.kore)} puck=${fmt(durations.puck)} " +
                            "deviceFemale=${fmt(durations.deviceFemale)} deviceMale=${fmt(durations.deviceMale)}",
                    )
                    durations
                }

            report("WEIGHT_SERVER_MALE", measured) { it.puck }
            report("WEIGHT_DEVICE_FEMALE", measured) { it.deviceFemale }
            report("WEIGHT_DEVICE_MALE", measured) { it.deviceMale }
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
                deviceFemale = measureDevice(line, gender = "female", File(outDir, "line${slot}_device_female.wav")),
                deviceMale = measureDevice(line, gender = "male", File(outDir, "line${slot}_device_male.wav")),
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
            if (ratios.size < referenceLines.size) {
                // 일부 문장만 측정됐다 — 이 경우 spread 는 일관성 신호로 신뢰할 수 없다.
                // 특히 n=1 이면 max-min 이 항상 0.000 이 되어 "완벽히 일관됨"처럼 보이지만
                // 실제로는 문장 하나짜리 미검증 값이다. Log.i 성공 라인과 절대 헷갈리면 안 되므로
                // Log.w + 명시적 경고 문구로 구분한다.
                Log.w(
                    CALIB_TAG,
                    String.format(
                        Locale.US,
                        "%s = %.3f  (n=%d, 문장 간 편차=%.3f) — 경고: 부분 측정치입니다. " +
                            "문장 %d개 중 %d개만 측정되어 이 편차 값은 유효한 일관성 신호가 아닙니다" +
                            "(표본이 1개면 편차는 항상 0.000으로 계산됩니다). " +
                            "이 값을 상수로 옮기지 말고 재측정하세요.",
                        name,
                        mean,
                        ratios.size,
                        spread,
                        referenceLines.size,
                        ratios.size,
                    ),
                )
                return
            }
            Log.i(
                CALIB_TAG,
                // Locale.US 명시: detekt ImplicitDefaultLocale 위반 방지 + 기기 로케일이 소수점을
                // 쉼표로 찍어 상수에 옮겨 적을 때 헷갈리는 일이 없게.
                String.format(Locale.US, "%s = %.3f  (n=%d, 문장 간 편차=%.3f)", name, mean, ratios.size, spread),
            )
        }

        private fun fmt(seconds: Double?): String = seconds?.let { String.format(Locale.US, "%.3fs", it) } ?: "실패"
    }

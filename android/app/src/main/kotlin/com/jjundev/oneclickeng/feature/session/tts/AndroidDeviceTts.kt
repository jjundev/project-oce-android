package com.jjundev.oneclickeng.feature.session.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * On-device Android TextToSpeech implementation of [DeviceTts]. Ports the archived
 * coordinator's language check + gender voice selection. Locale is en-US (tts.md §2).
 * All outcomes map to a [DeviceTtsResult] — this never throws, so the coordinator's
 * branch stays exhaustive (plan #12).
 */
@Singleton
class AndroidDeviceTts
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : DeviceTts {
        private val ready = CompletableDeferred<Boolean>()
        private val utteranceSeq = AtomicLong(0)

        private val tts: TextToSpeech =
            TextToSpeech(context) { status ->
                ready.complete(status == TextToSpeech.SUCCESS)
            }

        @Suppress("ReturnCount")
        override suspend fun speak(
            text: String,
            gender: String?,
            speechRate: Float,
            onStart: () -> Unit,
        ): DeviceTtsResult {
            val initialized = ready.await()
            if (!initialized) return DeviceTtsResult.ERROR

            // English data present? Missing/unsupported → text-only (tts.md §4).
            val lang = tts.setLanguage(Locale.US)
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                return DeviceTtsResult.LANGUAGE_MISSING
            }
            selectGenderVoice(gender)
            tts.setSpeechRate(speechRate)

            val utteranceId = "oce_tts_${utteranceSeq.incrementAndGet()}"
            val notifyStarted = onStart
            return suspendCancellableCoroutine { cont ->
                tts.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {
                            if (id == utteranceId) notifyStarted()
                        }

                        override fun onDone(id: String?) {
                            if (id == utteranceId && cont.isActive) cont.resume(DeviceTtsResult.COMPLETED)
                        }

                        @Deprecated("deprecated in API 21", ReplaceWith(""))
                        override fun onError(id: String?) {
                            if (id == utteranceId && cont.isActive) cont.resume(DeviceTtsResult.ERROR)
                        }

                        override fun onError(
                            id: String?,
                            errorCode: Int,
                        ) {
                            if (id == utteranceId && cont.isActive) cont.resume(DeviceTtsResult.ERROR)
                        }
                    },
                )
                val queued = tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
                if (queued != TextToSpeech.SUCCESS && cont.isActive) {
                    cont.resume(DeviceTtsResult.ERROR)
                }
                cont.invokeOnCancellation { tts.stop() }
            }
        }

        override fun stop() {
            tts.stop()
        }

        /**
         * en-US 성별 보이스 선택(아카이브 `DialoguePlaybackCoordinator.setTtsVoice` 4단계 폴백 이식).
         * 실기기 Google TTS 보이스는 이름에 "male"/"female" 리터럴이 없고 `iol`/`sfg`/`tpf` 같은 품질 코드를
         * 쓰므로 리터럴 매칭만으론 늘 실패한다. 프레임워크 [android.speech.tts.Voice] 는 순수 테스트가 어려워
         * 이름/로케일만 [pickGenderVoiceName] 로 넘겨 고른 이름을 실제 Voice 로 되돌려 세팅한다.
         */
        private fun selectGenderVoice(gender: String?) {
            val voices = runCatching { tts.voices }.getOrNull()?.toList() ?: return
            val infos =
                voices.mapNotNull { voice ->
                    val locale = voice.locale ?: return@mapNotNull null
                    VoiceInfo(voice.name, locale.language, locale.country)
                }
            val chosenName =
                pickGenderVoiceName(infos, gender, Locale.US.language, Locale.US.country) ?: return
            voices.firstOrNull { it.name == chosenName }?.let { tts.voice = it }
        }
    }

/** 프레임워크 [android.speech.tts.Voice] 의 순수-테스트 가능한 최소 투영(이름·언어·국가). */
internal data class VoiceInfo(
    val name: String,
    val language: String,
    val country: String,
)

/** Google TTS en-US 남성 보이스 품질 코드(부분 문자열). 이 밖에 이름이 `-m` 로 끝나는 것도 남성으로 본다. */
private val MALE_VOICE_CODES = listOf("iol", "-im-", "guy", "-m-", ".male")

/** Google TTS en-US 여성 보이스 품질 코드(부분 문자열). 이 밖에 이름이 `-f` 로 끝나는 것도 여성으로 본다. */
private val FEMALE_VOICE_CODES = listOf("sfg", "tpf", "lady", "-f-", ".female")

/** 소문자 보이스 이름이 요청 성별의 엔진 코드와 맞는지(아카이브 Pass 2). */
private fun matchesGenderCode(
    name: String,
    isMale: Boolean,
): Boolean =
    if (isMale) {
        MALE_VOICE_CODES.any { name.contains(it) } || name.endsWith("-m")
    } else {
        FEMALE_VOICE_CODES.any { name.contains(it) } || name.endsWith("-f")
    }

/**
 * 성별 보이스 선택 순수 로직 — 아카이브 `setTtsVoice` 4단계 폴백 이식.
 * 1) 이름의 "male"/"female" 리터럴 → 2) 엔진 품질 코드(Google TTS: 남 iol/-im-/guy/-m-/.male,
 * 여 sfg/tpf/lady/-f-/.female) → 3) 반대 성별 마커 회피(국가 일치 우선) → 4) 아무 [targetLanguage] 보이스.
 * gender null/female 은 여성으로 취급한다.
 *
 * @return 고른 보이스 이름, 없으면 null.
 */
@Suppress("ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth", "LoopWithTooManyJumpStatements")
internal fun pickGenderVoiceName(
    voices: List<VoiceInfo>,
    gender: String?,
    targetLanguage: String,
    targetCountry: String,
): String? {
    val isMale = gender.equals("male", ignoreCase = true)

    // Pass 1: 이름에 male/female 리터럴.
    for (voice in voices) {
        if (!voice.language.equals(targetLanguage, ignoreCase = true)) continue
        val name = voice.name.lowercase(Locale.US)
        if (isMale) {
            if (name.contains("male") && !name.contains("female")) return voice.name
        } else if (name.contains("female")) {
            return voice.name
        }
    }

    // Pass 2: 엔진 품질 코드(Google TTS).
    for (voice in voices) {
        if (!voice.language.equals(targetLanguage, ignoreCase = true)) continue
        if (matchesGenderCode(voice.name.lowercase(Locale.US), isMale)) return voice.name
    }

    // Pass 3: 반대 성별 마커 회피 — 국가 일치면 즉시 확정, 아니면 마지막 비반대 후보를 유지.
    var selected: String? = null
    for (voice in voices) {
        if (!voice.language.equals(targetLanguage, ignoreCase = true)) continue
        val name = voice.name.lowercase(Locale.US)
        val isOpposite =
            if (isMale) {
                name.contains("female") || name.contains("sfg") ||
                    name.contains("tpf") || name.contains("lady")
            } else {
                name.contains("male") || name.contains("iol") ||
                    name.contains("-im-") || name.contains("guy")
            }
        if (isOpposite) continue
        selected = voice.name
        if (targetCountry.isNotEmpty() && voice.country.equals(targetCountry, ignoreCase = true)) {
            return selected
        }
    }
    if (selected != null) return selected

    // Pass 4: 최후 — 언어(+국가) 일치 아무 보이스.
    return voices.firstOrNull { voice ->
        voice.language.equals(targetLanguage, ignoreCase = true) &&
            (targetCountry.isEmpty() || voice.country.equals(targetCountry, ignoreCase = true))
    }?.name
}

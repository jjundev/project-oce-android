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
            return suspendCancellableCoroutine { cont ->
                tts.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(id: String?) = Unit

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

        /** Pick a gender-matched en-US voice by name heuristic (ported from the archive). */
        private fun selectGenderVoice(gender: String?) {
            val voices = runCatching { tts.voices }.getOrNull() ?: return
            val wantMale = gender?.equals("male", ignoreCase = true) == true
            val match =
                voices.firstOrNull { voice ->
                    voice.locale.language.equals(Locale.US.language, ignoreCase = true) &&
                        run {
                            val name = voice.name.lowercase(Locale.US)
                            if (wantMale) {
                                name.contains("male") && !name.contains("female")
                            } else {
                                name.contains("female")
                            }
                        }
                }
            if (match != null) tts.voice = match
        }
    }

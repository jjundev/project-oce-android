package com.jjundev.oneclickeng.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * `/llm` proxy client — the app's first network surface (shared with M1-01's SSE
 * dialogue client, which extends it). M1-05 lands the `tts` single-shot JSON call
 * (backend-functions.md §4).
 */
interface LlmApi {
    @POST("llm")
    suspend fun tts(
        @Body body: TtsRequest,
    ): TtsResponse

    @POST("llm")
    suspend fun speaking(
        @Body body: SpeakingRequest,
    ): SpeakingResponse
}

/** `/llm` request envelope for a tts task. `task` is fixed to "tts". */
@Serializable
data class TtsRequest(
    val task: String = "tts",
    val payload: TtsPayload,
)

/**
 * tts payload — only text + opponent gender + speaking rate. Voice/provider/locale are
 * server-fixed and non-exposed (tts.md §1). Field name `speechRate` aligns with
 * audio-pipeline.md:130.
 */
@Serializable
data class TtsPayload(
    val text: String,
    val gender: String? = null,
    val speechRate: Float,
)

/**
 * tts response — base64 PCM + its real sample rate. Field name `pcmBase64` matches
 * audio-pipeline.md:98. The client MUST play at `sampleRate` (do not assume 24kHz).
 */
@Serializable
data class TtsResponse(
    val pcmBase64: String,
    val sampleRate: Int,
    val mimeType: String,
)

/**
 * `/llm` request envelope for a speaking task (M1-06). `sessionId` is a TOP-LEVEL envelope
 * field (not inside [SpeakingPayload]) per the `/llm` contract (backend-functions.md §4);
 * feedback/summary reuse the same envelope shape.
 */
@Serializable
data class SpeakingRequest(
    val task: String = "speaking",
    val sessionId: String,
    val payload: SpeakingPayload,
)

/** speaking payload — the learner's utterance as base64 16kHz·16bit·mono WAV. */
@Serializable
data class SpeakingPayload(
    val audioBase64: String,
)

/**
 * speaking response — a faithful transcript + one qualitative Korean encouragement line.
 * There is NO numeric score field by design (speaking-analyze.md, PRD A8/R3); the "no score"
 * guarantee is structural. `transcript` may be empty for an unintelligible clip.
 */
@Serializable
data class SpeakingResponse(
    val transcript: String,
    val feedbackMessage: String,
)

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

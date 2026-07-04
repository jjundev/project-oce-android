package com.jjundev.oneclickeng.core.network

import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * okhttp-sse implementation of [DeepFeedbackStream] (M2-03) — a structural twin of [FeedbackSseStream].
 * Each named SSE frame (`object`/`done`/`error`) is parsed into a typed [FeedbackDeepEvent] and emitted;
 * `done`/`error`/failure close the flow. Cancelling the collector runs `awaitClose` →
 * [EventSource.cancel], tearing down the socket (this is how the coordinator supersedes an in-flight
 * deep request when the user moves to the next turn).
 *
 * The `object` frame's `type` is always `feedbackDeepSection` (kept DISJOINT from slim's
 * `feedbackSection`); the inner `data.section` names WHICH of the three fixed-order deep sections it
 * carries (conceptualBridge → toneStyle → paraphrasing).
 *
 * 캡 거부 채널 (backend-functions.md §8): deep 는 슬림 `feedback`/`speaking` 과 per-session 캡을 공유하므로
 * 세션 캡 도달은 스트림이 열리기 전 사전-게이트 HTTP 429 로 온다 → [FeedbackDeepEvent.QuotaExceeded](중립
 * 상태). 그 외(네트워크·비-429 HTTP·mid-stream)는 재시도형 [FeedbackDeepEvent.Error] 로 둔다 — 슬림 선례와 정합.
 *
 * Timeout policy mirrors [FeedbackSseStream]: the SSE call disables the socket read timeout and lets
 * the coordinator's idle watchdog be the authoritative bound.
 */
@Singleton
class DeepFeedbackSseStream
    @Inject
    constructor(
        client: OkHttpClient,
        private val json: Json,
        retrofit: Retrofit,
    ) : DeepFeedbackStream {
        // readTimeout(0): the idle watchdog (coordinator) — not the socket — bounds a live stream.
        private val factory =
            EventSources.createFactory(
                client.newBuilder().readTimeout(0, TimeUnit.SECONDS).build(),
            )

        // Full `/llm` endpoint, resolved off the shared Retrofit base so the origin never drifts.
        private val endpoint = retrofit.baseUrl().resolve("llm").toString()

        override fun events(request: FeedbackDeepRequest): Flow<FeedbackDeepEvent> =
            callbackFlow {
                val body =
                    json.encodeToString(FeedbackDeepRequest.serializer(), request)
                        .toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder().url(endpoint).post(body).build()

                val listener =
                    object : EventSourceListener() {
                        override fun onEvent(
                            eventSource: EventSource,
                            id: String?,
                            type: String?,
                            data: String,
                        ) {
                            val event = parse(type, data)
                            if (event != null) trySend(event)
                            if (type == "done" || type == "error") close()
                        }

                        override fun onClosed(eventSource: EventSource) {
                            close()
                        }

                        override fun onFailure(
                            eventSource: EventSource,
                            t: Throwable?,
                            response: Response?,
                        ) {
                            // 세션 캡 거부(사전-게이트 429) → 중립 QuotaExceeded. 그 외는 재시도형 Error.
                            if (response?.code == HTTP_TOO_MANY_REQUESTS) {
                                trySend(FeedbackDeepEvent.QuotaExceeded(remaining = 0))
                            } else {
                                trySend(FeedbackDeepEvent.Error(code = "network"))
                            }
                            close(t)
                        }
                    }

                val eventSource = factory.newEventSource(httpRequest, listener)
                awaitClose { eventSource.cancel() }
            }

        /** Map one SSE frame to a domain event, resolving the `object` frame's `data.section`. */
        @Suppress("ReturnCount")
        private fun parse(
            type: String?,
            data: String,
        ): FeedbackDeepEvent? =
            try {
                when (type) {
                    "object" -> {
                        val frame = json.decodeFromString(DeepObjectFrame.serializer(), data)
                        // Only feedbackDeepSection is this stream's concern; other object types are dropped.
                        if (frame.type != "feedbackDeepSection") {
                            null
                        } else {
                            parseSection(frame.data)
                        }
                    }
                    "done" -> FeedbackDeepEvent.Done(json.decodeFromString(DeepDoneFrame.serializer(), data).status)
                    "error" -> FeedbackDeepEvent.Error(json.decodeFromString(DeepErrorFrame.serializer(), data).code)
                    else -> null
                }
            } catch (e: kotlinx.serialization.SerializationException) {
                // A malformed frame must not crash the stream; drop it and let the watchdog/done decide.
                Log.w(TAG, "dropping unparseable SSE frame (type=$type)", e)
                null
            }

        /** Resolve the inner `data.section` discriminator into the matching typed section event. */
        @Suppress("ReturnCount")
        private fun parseSection(data: JsonElement): FeedbackDeepEvent? {
            val section = data.jsonObject["section"]?.jsonPrimitive?.content
            return when (section) {
                "conceptualBridge" ->
                    FeedbackDeepEvent.Section.ConceptualBridge(
                        json.decodeFromJsonElement(ConceptualBridgeDto.serializer(), data),
                    )
                "toneStyle" ->
                    FeedbackDeepEvent.Section.ToneStyle(
                        json.decodeFromJsonElement(ToneStyleDto.serializer(), data),
                    )
                "paraphrasing" ->
                    FeedbackDeepEvent.Section.Paraphrasing(
                        json.decodeFromJsonElement(ParaphrasingDto.serializer(), data),
                    )
                else -> null // unknown section discriminator — drop
            }
        }

        private companion object {
            const val TAG = "DeepFeedbackSseStream"

            // 사전-게이트 세션 캡 거부의 HTTP 상태(backend-functions.md §8, feedback CAP_EXCEEDED 429 선례).
            const val HTTP_TOO_MANY_REQUESTS = 429
        }
    }

// Wire DTOs for the typed envelope (backend-functions.md §4). Kept file-private to the parser;
// prefixed `Deep` to avoid a redeclaration clash with FeedbackSseStream's identical wire DTOs
// (same package).
@kotlinx.serialization.Serializable
private data class DeepObjectFrame(val type: String, val data: JsonElement)

@kotlinx.serialization.Serializable
private data class DeepDoneFrame(val status: String? = null)

@kotlinx.serialization.Serializable
private data class DeepErrorFrame(val code: String)

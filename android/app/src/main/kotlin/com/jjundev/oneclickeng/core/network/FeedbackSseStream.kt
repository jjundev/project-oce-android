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
 * okhttp-sse implementation of [FeedbackStream] (M1-07) — a structural twin of [DialogueSseStream].
 * Each named SSE frame (`object`/`done`/`error`) is parsed into a typed [FeedbackEvent] and emitted;
 * `done`/`error`/failure close the flow. Cancelling the collector runs `awaitClose` →
 * [EventSource.cancel], tearing down the socket (this is how the coordinator supersedes an in-flight
 * feedback request when the user moves on).
 *
 * The `object` frame's `type` is always `feedbackSection`; the inner `data.section` names WHICH of
 * the three fixed-order slim sections it carries (mirrors summaryCard.data.kind).
 *
 * 캡 거부 채널 (backend-functions.md §8): 세션 호출 캡 도달은 스트림이 열리기 전 사전-게이트 HTTP 429 로
 * 온다. feedback 는 일일 게이트가 없어 429 의 유일 원인이 세션 캡이므로 429 = [FeedbackEvent.QuotaExceeded]
 * 로 수렴하고(중립 상태), 그 외(네트워크·비-429 HTTP·mid-stream)는 재시도형 [FeedbackEvent.Error] 로 둔다
 * — dialogue 의 QuotaExceeded/Error 비대칭 선례와 정합.
 *
 * Timeout policy mirrors [DialogueSseStream]: the SSE call disables the socket read timeout and lets
 * the coordinator's idle watchdog be the authoritative bound.
 */
@Singleton
class FeedbackSseStream
    @Inject
    constructor(
        client: OkHttpClient,
        private val json: Json,
        retrofit: Retrofit,
    ) : FeedbackStream {
        // readTimeout(0): the idle watchdog (coordinator) — not the socket — bounds a live stream.
        private val factory =
            EventSources.createFactory(
                client.newBuilder().readTimeout(0, TimeUnit.SECONDS).build(),
            )

        // Full `/llm` endpoint, resolved off the shared Retrofit base so the origin never drifts.
        private val endpoint = retrofit.baseUrl().resolve("llm").toString()

        override fun events(request: FeedbackRequest): Flow<FeedbackEvent> =
            callbackFlow {
                val body =
                    json.encodeToString(FeedbackRequest.serializer(), request)
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
                                trySend(FeedbackEvent.QuotaExceeded(remaining = 0))
                            } else {
                                trySend(FeedbackEvent.Error(code = "network"))
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
        ): FeedbackEvent? =
            try {
                when (type) {
                    "object" -> {
                        val frame = json.decodeFromString(FeedbackObjectFrame.serializer(), data)
                        // Only feedbackSection is this stream's concern; other object types are dropped.
                        if (frame.type != "feedbackSection") {
                            null
                        } else {
                            parseSection(frame.data)
                        }
                    }
                    "done" -> FeedbackEvent.Done(json.decodeFromString(FeedbackDoneFrame.serializer(), data).status)
                    "error" -> FeedbackEvent.Error(json.decodeFromString(FeedbackErrorFrame.serializer(), data).code)
                    else -> null
                }
            } catch (e: kotlinx.serialization.SerializationException) {
                // A malformed frame must not crash the stream; drop it and let the watchdog/done decide.
                Log.w(TAG, "dropping unparseable SSE frame (type=$type)", e)
                null
            }

        /** Resolve the inner `data.section` discriminator into the matching typed section event. */
        @Suppress("ReturnCount")
        private fun parseSection(data: JsonElement): FeedbackEvent? {
            val section = data.jsonObject["section"]?.jsonPrimitive?.content
            return when (section) {
                "writingScore" ->
                    FeedbackEvent.Section.WritingScore(
                        json.decodeFromJsonElement(WritingScoreDto.serializer(), data),
                    )
                "grammar" ->
                    FeedbackEvent.Section.Grammar(
                        json.decodeFromJsonElement(GrammarDto.serializer(), data),
                    )
                "naturalExpression" ->
                    FeedbackEvent.Section.NaturalExpression(
                        json.decodeFromJsonElement(NaturalExpressionDto.serializer(), data),
                    )
                else -> null // unknown section discriminator — drop
            }
        }

        private companion object {
            const val TAG = "FeedbackSseStream"

            // 사전-게이트 세션 캡 거부의 HTTP 상태(backend-functions.md §8, speaking CAP_EXCEEDED 429 선례).
            const val HTTP_TOO_MANY_REQUESTS = 429
        }
    }

// Wire DTOs for the typed envelope (backend-functions.md §4). Kept file-private to the parser;
// prefixed to avoid a redeclaration clash with DialogueSseStream's identical wire DTOs (same package).
@kotlinx.serialization.Serializable
private data class FeedbackObjectFrame(val type: String, val data: JsonElement)

@kotlinx.serialization.Serializable
private data class FeedbackDoneFrame(val status: String? = null)

@kotlinx.serialization.Serializable
private data class FeedbackErrorFrame(val code: String)

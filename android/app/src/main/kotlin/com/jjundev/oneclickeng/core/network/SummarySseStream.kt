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
 * okhttp-sse implementation of [SummaryStream] (M2-02) — a structural twin of [FeedbackSseStream].
 * Each named SSE frame (`object`/`done`/`error`) is parsed into a typed [SummaryEvent] and emitted;
 * `done`/`error`/failure close the flow. Cancelling the collector runs `awaitClose` →
 * [EventSource.cancel], tearing down the socket (this is how the coordinator supersedes an in-flight
 * summary request on retry).
 *
 * The `object` frame's `type` is always `summaryCard`; the inner `data.kind ∈ {expression, word,
 * coaching}` names WHICH bundled section it carries (mirrors feedbackSection.data.section). Unlike
 * feedback the three cards arrive from one bundled multi-call, so the `done` frame carries a per-section
 * `ok|failed` map the client uses to distinguish empty vs failed (backend-functions.md §10).
 *
 * 캡 거부 채널 (backend-functions.md §8): 세션 호출 캡 도달은 스트림이 열리기 전 사전-게이트 HTTP 429 로
 * 온다. summary 는 일일 게이트가 없어 429 의 유일 원인이 세션 캡이므로 429 = [SummaryEvent.QuotaExceeded]
 * 로 수렴하고(중립 상태), 그 외(네트워크·비-429 HTTP·mid-stream)는 재시도형 [SummaryEvent.Error] 로 둔다.
 *
 * Timeout policy mirrors [FeedbackSseStream]: the SSE call disables the socket read timeout and lets
 * the coordinator's idle watchdog be the authoritative bound.
 */
@Singleton
class SummarySseStream
    @Inject
    constructor(
        client: OkHttpClient,
        private val json: Json,
        retrofit: Retrofit,
    ) : SummaryStream {
        // readTimeout(0): the idle watchdog (coordinator) — not the socket — bounds a live stream.
        private val factory =
            EventSources.createFactory(
                client.newBuilder().readTimeout(0, TimeUnit.SECONDS).build(),
            )

        // Full `/llm` endpoint, resolved off the shared Retrofit base so the origin never drifts.
        private val endpoint = retrofit.baseUrl().resolve("llm").toString()

        override fun events(request: SummaryRequest): Flow<SummaryEvent> =
            callbackFlow {
                val body =
                    json.encodeToString(SummaryRequest.serializer(), request)
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
                                trySend(SummaryEvent.QuotaExceeded(remaining = 0))
                            } else {
                                trySend(SummaryEvent.Error(code = "network"))
                            }
                            close(t)
                        }
                    }

                val eventSource = factory.newEventSource(httpRequest, listener)
                awaitClose { eventSource.cancel() }
            }

        /** Map one SSE frame to a domain event, resolving the `object` frame's `data.kind`. */
        @Suppress("ReturnCount")
        private fun parse(
            type: String?,
            data: String,
        ): SummaryEvent? =
            try {
                when (type) {
                    "object" -> {
                        val frame = json.decodeFromString(SummaryObjectFrame.serializer(), data)
                        // Only summaryCard is this stream's concern; other object types are dropped.
                        if (frame.type != "summaryCard") null else parseCard(frame.data)
                    }
                    "done" -> {
                        val frame = json.decodeFromString(SummaryDoneFrame.serializer(), data)
                        SummaryEvent.Done(
                            expressions = SectionOutcome.fromWire(frame.expressions),
                            words = SectionOutcome.fromWire(frame.words),
                            coaching = SectionOutcome.fromWire(frame.coaching),
                        )
                    }
                    "error" -> SummaryEvent.Error(json.decodeFromString(SummaryErrorFrame.serializer(), data).code)
                    else -> null
                }
            } catch (e: kotlinx.serialization.SerializationException) {
                // A malformed frame must not crash the stream; drop it and let the watchdog/done decide.
                Log.w(TAG, "dropping unparseable SSE frame (type=$type)", e)
                null
            }

        /** Resolve the inner `data.kind` discriminator into the matching typed card event. */
        @Suppress("ReturnCount")
        private fun parseCard(data: JsonElement): SummaryEvent? {
            val kind = data.jsonObject["kind"]?.jsonPrimitive?.content
            return when (kind) {
                "expression" ->
                    SummaryEvent.Card.Expression(
                        json.decodeFromJsonElement(SummaryExpressionSectionDto.serializer(), data).items,
                    )
                "word" ->
                    SummaryEvent.Card.Word(
                        json.decodeFromJsonElement(SummaryWordSectionDto.serializer(), data).items,
                    )
                "coaching" ->
                    SummaryEvent.Card.Coaching(
                        json.decodeFromJsonElement(CoachingDto.serializer(), data),
                    )
                else -> null // unknown kind discriminator — drop
            }
        }

        private companion object {
            const val TAG = "SummarySseStream"

            // 사전-게이트 세션 캡 거부의 HTTP 상태(backend-functions.md §8, speaking CAP_EXCEEDED 429 선례).
            const val HTTP_TOO_MANY_REQUESTS = 429
        }
    }

// Wire DTOs for the typed envelope (backend-functions.md §4·§10). Kept file-private to the parser;
// prefixed to avoid a redeclaration clash with the sibling SSE streams' identical wire DTOs (same package).
@kotlinx.serialization.Serializable
private data class SummaryObjectFrame(val type: String, val data: JsonElement)

// The expression/word `data` carries `{kind, items}`; ignoreUnknownKeys drops `kind` on decode.
@kotlinx.serialization.Serializable
private data class SummaryExpressionSectionDto(val items: List<ExpressionItemDto> = emptyList())

@kotlinx.serialization.Serializable
private data class SummaryWordSectionDto(val items: List<WordItemDto> = emptyList())

@kotlinx.serialization.Serializable
private data class SummaryDoneFrame(
    val status: String? = null,
    val expressions: String? = null,
    val words: String? = null,
    val coaching: String? = null,
)

@kotlinx.serialization.Serializable
private data class SummaryErrorFrame(val code: String)

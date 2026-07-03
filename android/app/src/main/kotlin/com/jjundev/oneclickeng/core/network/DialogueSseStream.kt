package com.jjundev.oneclickeng.core.network

import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
 * okhttp-sse implementation of [DialogueStream] (M1-01). Wraps an [EventSource] as a cold
 * [callbackFlow]: each named SSE frame (`meta`/`object`/`done`/`error`) is parsed into a typed
 * [DialogueEvent] and emitted; `done`/`error`/failure close the flow. Cancelling the collector runs
 * `awaitClose` → [EventSource.cancel], tearing down the socket (this is how the coordinator's
 * stale-guard supersedes an in-flight generation).
 *
 * Timeout policy: the shared [OkHttpClient]'s blanket read timeout would kill a legitimately-idle
 * multi-turn stream between server flushes, so the SSE call derives a client with `readTimeout(0)`
 * (disabled). The authoritative bound is the coordinator's inter-event idle watchdog — same "app
 * watchdog is the harder bound than the socket" stance as `NetworkModule`/`TtsPlaybackCoordinator`.
 */
@Singleton
class DialogueSseStream
    @Inject
    constructor(
        client: OkHttpClient,
        private val json: Json,
        retrofit: Retrofit,
    ) : DialogueStream {
        // readTimeout(0): the idle watchdog (coordinator) — not the socket — bounds a live stream.
        private val factory =
            EventSources.createFactory(
                client.newBuilder().readTimeout(0, TimeUnit.SECONDS).build(),
            )

        // Full `/llm` endpoint, resolved off the shared Retrofit base so the origin never drifts.
        private val endpoint = retrofit.baseUrl().resolve("llm").toString()

        override fun events(request: DialogueRequest): Flow<DialogueEvent> =
            callbackFlow {
                val body =
                    json.encodeToString(DialogueRequest.serializer(), request)
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
                            // 채널-탈결합 (i): 일일 한도 거부는 스트림이 열리기 전 사전-게이트에서 비200으로
                            // 온다(backend-functions.md §7 `{remaining:0}`). okhttp-sse 는 non-2xx 에
                            // non-null·미소비 Response 로 onFailure 를 부른다. dialogue-start 의 유일한 429
                            // 원인이 일일 한도이므로 429 = 한도 도달로 본다. 그 외(네트워크·비-429 HTTP)는
                            // 기존대로 재시도형 Error. remaining 은 거부 시 상수 0.
                            if (response?.code == HTTP_TOO_MANY_REQUESTS) {
                                trySend(DialogueEvent.QuotaExceeded(remaining = 0))
                            } else {
                                trySend(DialogueEvent.Error(code = "network"))
                            }
                            close(t)
                        }
                    }

                val eventSource = factory.newEventSource(httpRequest, listener)
                awaitClose { eventSource.cancel() }
            }

        /** Map one SSE frame to a domain event, resolving the `object` frame's `type` discriminator. */
        @Suppress("ReturnCount")
        private fun parse(
            type: String?,
            data: String,
        ): DialogueEvent? =
            try {
                when (type) {
                    "meta" -> {
                        val dto = json.decodeFromString(StartFrame.serializer(), data)
                        DialogueEvent.Start(dto.sessionId, dto.remaining)
                    }
                    "object" -> {
                        val frame = json.decodeFromString(ObjectFrame.serializer(), data)
                        when (frame.type) {
                            "dialogueMeta" ->
                                DialogueEvent.Meta(json.decodeFromJsonElement(DialogueMeta.serializer(), frame.data))
                            "turn" ->
                                DialogueEvent.Turn(json.decodeFromJsonElement(DialogueTurn.serializer(), frame.data))
                            else -> null // feedbackSection/summaryCard are not dialogue's concern (§12)
                        }
                    }
                    "done" -> DialogueEvent.Done(json.decodeFromString(DoneFrame.serializer(), data).status)
                    "error" -> {
                        // 채널-탈결합 (ii): M1-02 가 거부를 HTTP 비200 대신 열린 스트림의 `event:error` 로
                        // 실어 보내는 경우도 흡수한다(§4 `event:error → {code}` 정본 스키마 불변, remaining 은
                        // ErrorBody 에 없으므로 상수 0). 명시된 일일-한도 code 만 QuotaExceeded 로 수렴하고
                        // 미인식 code 는 기존 재시도형 Error 로 남긴다(의도된 비대칭 — 경로(i)은 dialogue-start
                        // 유일 429=한도라 상태만으로, 경로(ii)는 명시 code 만 신뢰).
                        val code = json.decodeFromString(ErrorFrame.serializer(), data).code
                        if (code == CODE_DAILY_LIMIT_EXCEEDED) {
                            DialogueEvent.QuotaExceeded(remaining = 0)
                        } else {
                            DialogueEvent.Error(code)
                        }
                    }
                    else -> null
                }
            } catch (e: kotlinx.serialization.SerializationException) {
                // A malformed frame must not crash the stream; drop it and let the watchdog/done decide.
                Log.w(TAG, "dropping unparseable SSE frame (type=$type)", e)
                null
            }

        private companion object {
            const val TAG = "DialogueSseStream"

            // 사전-게이트 일일 한도 거부의 HTTP 상태(backend-functions.md §7, speaking 캡 429 선례와 정합).
            const val HTTP_TOO_MANY_REQUESTS = 429

            // 일일 한도 거부용 신규 code(M1-02 에 `ErrorCode` 멤버 추가 요청). speaking 세션캡 `CAP_EXCEEDED`
            // 와 개념이 다르므로 별개 code 를 쓴다. SSE `event:error` 프레임 채널(ii)에서만 참조.
            const val CODE_DAILY_LIMIT_EXCEEDED = "DAILY_LIMIT_EXCEEDED"
        }
    }

// Wire DTOs for the typed envelope (backend-functions.md §4). Kept internal to the parser.
@kotlinx.serialization.Serializable
private data class StartFrame(val sessionId: String, val remaining: Int)

@kotlinx.serialization.Serializable
private data class ObjectFrame(val type: String, val data: JsonElement)

@kotlinx.serialization.Serializable
private data class DoneFrame(val status: String? = null)

@kotlinx.serialization.Serializable
private data class ErrorFrame(val code: String)

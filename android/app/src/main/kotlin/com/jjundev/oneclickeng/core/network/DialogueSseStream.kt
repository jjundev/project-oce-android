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
                            // Network/HTTP failure → typed error so the coordinator surfaces retry.
                            trySend(DialogueEvent.Error(code = "network"))
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
                    "error" -> DialogueEvent.Error(json.decodeFromString(ErrorFrame.serializer(), data).code)
                    else -> null
                }
            } catch (e: kotlinx.serialization.SerializationException) {
                // A malformed frame must not crash the stream; drop it and let the watchdog/done decide.
                Log.w(TAG, "dropping unparseable SSE frame (type=$type)", e)
                null
            }

        private companion object {
            const val TAG = "DialogueSseStream"
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

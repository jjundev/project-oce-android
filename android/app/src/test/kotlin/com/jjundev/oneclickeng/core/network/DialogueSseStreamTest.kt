package com.jjundev.oneclickeng.core.network

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * Golden test of the real okhttp-sse framing (NFR risk zone, backend-functions.md §4·§9): a mock
 * `text/event-stream` response is parsed into the exact typed [DialogueEvent] sequence, proving the
 * `event:`/`data:` frame → domain-event mapping (including the `object` `type` discriminator).
 */
class DialogueSseStreamTest {
    private lateinit var server: MockWebServer

    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun stream(): DialogueSseStream {
        val client = OkHttpClient.Builder().build()
        val retrofit = Retrofit.Builder().baseUrl(server.url("/")).build()
        return DialogueSseStream(client, json, retrofit)
    }

    private val request =
        DialogueRequest(
            idempotencyKey = "key-1",
            payload = DialoguePayload(level = "easy", topic = "coffee", length = 5, firstSession = true),
        )

    @Test
    fun `parses meta, object(dialogueMeta), object(turn), done frames in order`() =
        runBlocking {
            val body =
                buildString {
                    append("event: meta\n")
                    append("data: {\"sessionId\":\"s1\",\"remaining\":2}\n\n")
                    append("event: object\n")
                    append(
                        "data: {\"type\":\"dialogueMeta\",\"data\":" +
                            "{\"topic\":\"커피 주문\",\"opponentName\":\"John\",\"opponentGender\":\"male\"," +
                            "\"opponentRole\":\"Barista\"}}\n\n",
                    )
                    append("event: object\n")
                    append(
                        "data: {\"type\":\"turn\",\"data\":" +
                            "{\"ko\":\"안녕하세요\",\"en\":\"Hello\",\"role\":\"model\"}}\n\n",
                    )
                    append("event: done\n")
                    append("data: {\"status\":\"ok\"}\n\n")
                }
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(body),
            )

            val events = withTimeout(5_000) { stream().events(request).toList() }

            assertEquals(4, events.size)
            assertEquals(DialogueEvent.Start("s1", 2), events[0])
            assertEquals(DialogueEvent.Meta(DialogueMeta("커피 주문", "John", "male", "Barista")), events[1])
            assertEquals(DialogueEvent.Turn(DialogueTurn("안녕하세요", "Hello", "model")), events[2])
            assertEquals(DialogueEvent.Done("ok"), events[3])

            // The request body carried the dialogue envelope (task + idempotencyKey).
            val sent = server.takeRequest().body.readUtf8()
            assertTrue(sent.contains("\"task\":\"dialogue\""))
            assertTrue(sent.contains("\"idempotencyKey\":\"key-1\""))
        }

    @Test
    fun `error frame surfaces a typed Error and ends the stream`() =
        runBlocking {
            val body =
                buildString {
                    append("event: meta\n")
                    append("data: {\"sessionId\":\"s1\",\"remaining\":2}\n\n")
                    append("event: error\n")
                    append("data: {\"code\":\"gen_failed\"}\n\n")
                }
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(body),
            )

            val events = withTimeout(5_000) { stream().events(request).toList() }

            assertEquals(2, events.size)
            assertEquals(DialogueEvent.Start("s1", 2), events[0])
            assertEquals(DialogueEvent.Error("gen_failed"), events[1])
        }
}

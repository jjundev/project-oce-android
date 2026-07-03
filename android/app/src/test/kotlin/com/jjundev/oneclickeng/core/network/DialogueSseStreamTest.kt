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

    @Test
    fun `pre-stream HTTP 429 surfaces QuotaExceeded (daily-limit channel i)`() =
        runBlocking {
            // 사전-게이트 일일 한도 거부: 스트림이 열리기 전 비200(429)로 온다(backend-functions.md §7).
            server.enqueue(
                MockResponse()
                    .setResponseCode(429)
                    .setBody("{\"code\":\"DAILY_LIMIT_EXCEEDED\",\"remaining\":0}"),
            )

            val events = withTimeout(5_000) { stream().events(request).toList() }

            assertEquals(1, events.size)
            assertEquals(DialogueEvent.QuotaExceeded(0), events[0])
        }

    @Test
    fun `error frame with daily-limit code surfaces QuotaExceeded (channel ii)`() =
        runBlocking {
            // 채널 탈결합: M1-02 가 거부를 열린 스트림의 event:error 로 실어 보내도 QuotaExceeded 로 수렴.
            val body =
                buildString {
                    append("event: error\n")
                    append("data: {\"code\":\"DAILY_LIMIT_EXCEEDED\"}\n\n")
                }
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(body),
            )

            val events = withTimeout(5_000) { stream().events(request).toList() }

            assertEquals(1, events.size)
            assertEquals(DialogueEvent.QuotaExceeded(0), events[0])
        }

    @Test
    fun `non-429 HTTP failure stays a retryable Error, not QuotaExceeded`() =
        runBlocking {
            // 비-429(예: 500)는 기존 재시도형 Error("network") — 한도로 오분류하지 않는다(의도된 비대칭).
            server.enqueue(MockResponse().setResponseCode(500))

            val events = withTimeout(5_000) { stream().events(request).toList() }

            assertEquals(1, events.size)
            assertEquals(DialogueEvent.Error("network"), events[0])
        }
}

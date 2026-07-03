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
 * Golden test of the real okhttp-sse framing for feedback (M1-07): a mock `text/event-stream`
 * response is parsed into the exact typed [FeedbackEvent] sequence, proving the `feedbackSection`
 * `data.section` discriminator → domain-event mapping and the 429 cap channel. Mirrors
 * [DialogueSseStreamTest].
 */
class FeedbackSseStreamTest {
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

    private fun stream(): FeedbackSseStream {
        val client = OkHttpClient.Builder().build()
        val retrofit = Retrofit.Builder().baseUrl(server.url("/")).build()
        return FeedbackSseStream(client, json, retrofit)
    }

    private val request =
        FeedbackRequest(
            sessionId = "s1",
            payload =
                FeedbackPayload(
                    koreanPrompt = "커피 주세요",
                    userEnglish = "One coffee",
                    referenceEnglish = "Can I get a coffee?",
                    level = "normal",
                ),
        )

    @Test
    fun `parses the three feedbackSection frames in order, then done`() =
        runBlocking {
            val body =
                buildString {
                    append("event: object\n")
                    append(
                        "data: {\"type\":\"feedbackSection\",\"data\":" +
                            "{\"section\":\"writingScore\",\"score\":85,\"encouragementMessage\":\"잘했어요!\"}}\n\n",
                    )
                    append("event: object\n")
                    append(
                        "data: {\"type\":\"feedbackSection\",\"data\":{\"section\":\"grammar\"," +
                            "\"correctedSentence\":{\"segments\":[{\"text\":\"ok\",\"type\":\"normal\"}]}," +
                            "\"explanation\":\"좋아요.\"}}\n\n",
                    )
                    append("event: object\n")
                    append(
                        "data: {\"type\":\"feedbackSection\",\"data\":{\"section\":\"naturalExpression\"," +
                            "\"segments\":[{\"text\":\"x\",\"type\":\"normal\"}]," +
                            "\"reason\":{\"keyword\":\"k\",\"description\":\"d\"}}}\n\n",
                    )
                    append("event: done\n")
                    append("data: {\"status\":\"ok\"}\n\n")
                }
            server.enqueue(
                MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body),
            )

            val events = withTimeout(5_000) { stream().events(request).toList() }

            assertEquals(4, events.size)
            assertEquals(
                FeedbackEvent.Section.WritingScore(WritingScoreDto(85, "잘했어요!")),
                events[0],
            )
            assertTrue(events[1] is FeedbackEvent.Section.Grammar)
            assertEquals(
                "좋아요.",
                (events[1] as FeedbackEvent.Section.Grammar).value.explanation,
            )
            assertTrue(events[2] is FeedbackEvent.Section.NaturalExpression)
            assertEquals(
                ReasonDto("k", "d"),
                (events[2] as FeedbackEvent.Section.NaturalExpression).value.reason,
            )
            assertEquals(FeedbackEvent.Done("ok"), events[3])

            val sent = server.takeRequest().body.readUtf8()
            assertTrue(sent.contains("\"task\":\"feedback\""))
            assertTrue(sent.contains("\"sessionId\":\"s1\""))
        }

    @Test
    fun `error frame surfaces a typed Error and ends the stream`() =
        runBlocking {
            val body =
                buildString {
                    append("event: error\n")
                    append("data: {\"code\":\"INTERNAL\"}\n\n")
                }
            server.enqueue(
                MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body),
            )

            val events = withTimeout(5_000) { stream().events(request).toList() }

            assertEquals(1, events.size)
            assertEquals(FeedbackEvent.Error("INTERNAL"), events[0])
        }

    @Test
    fun `pre-gate HTTP 429 surfaces QuotaExceeded (session cap channel)`() =
        runBlocking {
            server.enqueue(
                MockResponse().setResponseCode(429).setBody("{\"code\":\"CAP_EXCEEDED\"}"),
            )

            val events = withTimeout(5_000) { stream().events(request).toList() }

            assertEquals(1, events.size)
            assertEquals(FeedbackEvent.QuotaExceeded(0), events[0])
        }

    @Test
    fun `non-429 HTTP failure stays a retryable Error`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(500))

            val events = withTimeout(5_000) { stream().events(request).toList() }

            assertEquals(1, events.size)
            assertEquals(FeedbackEvent.Error("network"), events[0])
        }

    @Test
    fun `unknown section discriminator is dropped`() =
        runBlocking {
            val body =
                buildString {
                    append("event: object\n")
                    append("data: {\"type\":\"feedbackSection\",\"data\":{\"section\":\"mystery\"}}\n\n")
                    append("event: done\n")
                    append("data: {\"status\":\"ok\"}\n\n")
                }
            server.enqueue(
                MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body),
            )

            val events = withTimeout(5_000) { stream().events(request).toList() }

            assertEquals(1, events.size)
            assertEquals(FeedbackEvent.Done("ok"), events[0])
        }
}

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
 * Golden test of the real okhttp-sse framing for summary (M2-02): a mock `text/event-stream` response
 * is parsed into the exact typed [SummaryEvent] sequence, proving the `summaryCard` `data.kind`
 * discriminator → domain-event mapping, the per-section `done{ok|failed}` map, and the 429 cap channel.
 * Mirrors [FeedbackSseStreamTest].
 */
class SummarySseStreamTest {
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

    private fun stream(): SummarySseStream {
        val client = OkHttpClient.Builder().build()
        val retrofit = Retrofit.Builder().baseUrl(server.url("/")).build()
        return SummarySseStream(client, json, retrofit)
    }

    private val request =
        SummaryRequest(
            sessionId = "s1",
            payload = SummaryPayload(totalScore = 85, turns = emptyList()),
        )

    @Test
    fun `parses expression, word and coaching cards by kind, then done with per-section outcomes`() =
        runBlocking {
            val body =
                buildString {
                    append("event: object\n")
                    append(
                        "data: {\"type\":\"summaryCard\",\"data\":{\"kind\":\"expression\",\"items\":" +
                            "[{\"type\":\"natural\",\"koreanPrompt\":\"커피 주세요\",\"before\":\"One coffee\"," +
                            "\"after\":\"Could I grab a coffee?\",\"explanation\":\"가벼워요.\"}]}}\n\n",
                    )
                    append("event: object\n")
                    append(
                        "data: {\"type\":\"summaryCard\",\"data\":{\"kind\":\"word\",\"items\":" +
                            "[{\"en\":\"grab\",\"ko\":\"잽싸게\",\"partOfSpeech\":\"verb\",\"level\":\"B1\"," +
                            "\"example\":{\"en\":\"Let me grab it.\",\"ko\":\"제가 가져올게요.\"}}]}}\n\n",
                    )
                    append("event: object\n")
                    append(
                        "data: {\"type\":\"summaryCard\",\"data\":{\"kind\":\"coaching\"," +
                            "\"futureSelfFeedback\":{\"positive\":\"끝까지 했어요.\",\"toImprove\":\"과거형을 노려봐요.\"}}}\n\n",
                    )
                    append("event: done\n")
                    append("data: {\"expressions\":\"ok\",\"words\":\"failed\",\"coaching\":\"ok\"}\n\n")
                }
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body))

            val events = withTimeout(5_000) { stream().events(request).toList() }

            assertEquals(4, events.size)
            assertTrue(events[0] is SummaryEvent.Card.Expression)
            assertEquals("Could I grab a coffee?", (events[0] as SummaryEvent.Card.Expression).items.single().after)
            assertTrue(events[1] is SummaryEvent.Card.Word)
            assertEquals("grab", (events[1] as SummaryEvent.Card.Word).items.single().en)
            assertTrue(events[2] is SummaryEvent.Card.Coaching)
            assertEquals("끝까지 했어요.", (events[2] as SummaryEvent.Card.Coaching).value.futureSelfFeedback.positive)
            assertEquals(
                SummaryEvent.Done(SectionOutcome.Ok, SectionOutcome.Failed, SectionOutcome.Ok),
                events[3],
            )

            val sent = server.takeRequest().body.readUtf8()
            assertTrue(sent.contains("\"task\":\"summary\""))
            assertTrue(sent.contains("\"sessionId\":\"s1\""))
        }

    @Test
    fun `error frame surfaces a typed Error`() =
        runBlocking {
            val body =
                buildString {
                    append("event: error\n")
                    append("data: {\"code\":\"INTERNAL\"}\n\n")
                }
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body))

            val events = withTimeout(5_000) { stream().events(request).toList() }

            assertEquals(1, events.size)
            assertEquals(SummaryEvent.Error("INTERNAL"), events[0])
        }

    @Test
    fun `pre-gate HTTP 429 surfaces QuotaExceeded (session cap channel)`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(429).setBody("{\"code\":\"CAP_EXCEEDED\"}"))

            val events = withTimeout(5_000) { stream().events(request).toList() }

            assertEquals(1, events.size)
            assertEquals(SummaryEvent.QuotaExceeded(0), events[0])
        }
}

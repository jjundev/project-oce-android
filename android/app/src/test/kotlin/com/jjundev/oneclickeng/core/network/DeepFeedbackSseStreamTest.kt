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
 * Golden test of the real okhttp-sse framing for deep feedback (M2-03): a mock `text/event-stream`
 * response is parsed into the exact typed [FeedbackDeepEvent] sequence, proving the
 * `feedbackDeepSection` `data.section` discriminator → domain-event mapping (including the ARRAY
 * `paraphrasing` section wrapped under `items`) and the 429 cap channel. Mirrors [FeedbackSseStreamTest].
 */
class DeepFeedbackSseStreamTest {
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

    private fun stream(): DeepFeedbackSseStream {
        val client = OkHttpClient.Builder().build()
        val retrofit = Retrofit.Builder().baseUrl(server.url("/")).build()
        return DeepFeedbackSseStream(client, json, retrofit)
    }

    private val request =
        FeedbackDeepRequest(
            sessionId = "s1",
            payload =
                FeedbackPayload(
                    koreanPrompt = "커피 주세요",
                    userEnglish = "One coffee",
                    referenceEnglish = "Can I get a coffee?",
                    level = "normal",
                ),
        )

    @Suppress("LongMethod") // golden SSE body: three fully-inlined deep-section frames + assertions.
    @Test
    fun `parses conceptualBridge, toneStyle, paraphrasing frames in order, then done`() =
        runBlocking {
            val body =
                buildString {
                    append("event: object\n")
                    append(
                        "data: {\"type\":\"feedbackDeepSection\",\"data\":{\"section\":\"conceptualBridge\"," +
                            "\"literalTranslation\":\"직역\",\"explanation\":\"설명\",\"venn\":{\"guide\":\"안내\"," +
                            "\"leftCircle\":{\"word\":\"get\",\"items\":[\"얻다\"]}," +
                            "\"rightCircle\":{\"word\":\"order\",\"items\":[\"주문하다\"]}," +
                            "\"intersection\":{\"items\":[\"받다\"]}}}}\n\n",
                    )
                    append("event: object\n")
                    append(
                        "data: {\"type\":\"feedbackDeepSection\",\"data\":{\"section\":\"toneStyle\"," +
                            "\"defaultLevel\":2,\"levels\":[" +
                            "{\"level\":0,\"sentence\":\"a\",\"sentenceTranslation\":\"가\"}," +
                            "{\"level\":1,\"sentence\":\"b\",\"sentenceTranslation\":\"나\"}," +
                            "{\"level\":2,\"sentence\":\"c\",\"sentenceTranslation\":\"다\"}," +
                            "{\"level\":3,\"sentence\":\"d\",\"sentenceTranslation\":\"라\"}," +
                            "{\"level\":4,\"sentence\":\"e\"," +
                            "\"sentenceTranslation\":\"마\"}]}}\n\n",
                    )
                    append("event: object\n")
                    append(
                        "data: {\"type\":\"feedbackDeepSection\",\"data\":{\"section\":\"paraphrasing\"," +
                            "\"items\":[" +
                            "{\"level\":1,\"label\":\"Beginner\"," +
                            "\"sentence\":\"p1\",\"sentenceTranslation\":\"번역1\"}," +
                            "{\"level\":2,\"label\":\"Intermediate\"," +
                            "\"sentence\":\"p2\",\"sentenceTranslation\":\"번역2\"}," +
                            "{\"level\":3,\"label\":\"Advanced\"," +
                            "\"sentence\":\"p3\",\"sentenceTranslation\":\"번역3\"}]}}\n\n",
                    )
                    append("event: done\n")
                    append("data: {\"status\":\"ok\"}\n\n")
                }
            server.enqueue(
                MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body),
            )

            val events = withTimeout(5_000) { stream().events(request).toList() }

            assertEquals(4, events.size)
            assertTrue(events[0] is FeedbackDeepEvent.Section.ConceptualBridge)
            assertEquals(
                "직역",
                (events[0] as FeedbackDeepEvent.Section.ConceptualBridge).value.literalTranslation,
            )
            assertEquals(
                "받다",
                (events[0] as FeedbackDeepEvent.Section.ConceptualBridge)
                    .value.venn.intersection.items.first(),
            )
            assertTrue(events[1] is FeedbackDeepEvent.Section.ToneStyle)
            assertEquals(5, (events[1] as FeedbackDeepEvent.Section.ToneStyle).value.levels.size)
            assertTrue(events[2] is FeedbackDeepEvent.Section.Paraphrasing)
            assertEquals(
                3,
                (events[2] as FeedbackDeepEvent.Section.Paraphrasing).value.items.size,
            )
            assertEquals(
                "Advanced",
                (events[2] as FeedbackDeepEvent.Section.Paraphrasing).value.items[2].label,
            )
            assertEquals(FeedbackDeepEvent.Done("ok"), events[3])

            val sent = server.takeRequest().body.readUtf8()
            assertTrue(sent.contains("\"task\":\"feedbackDeep\""))
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
            assertEquals(FeedbackDeepEvent.Error("INTERNAL"), events[0])
        }

    @Test
    fun `pre-gate HTTP 429 surfaces QuotaExceeded (shared session cap channel)`() =
        runBlocking {
            server.enqueue(
                MockResponse().setResponseCode(429).setBody("{\"code\":\"CAP_EXCEEDED\"}"),
            )

            val events = withTimeout(5_000) { stream().events(request).toList() }

            assertEquals(1, events.size)
            assertEquals(FeedbackDeepEvent.QuotaExceeded(0), events[0])
        }

    @Test
    fun `non-429 HTTP failure stays a retryable Error`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(500))

            val events = withTimeout(5_000) { stream().events(request).toList() }

            assertEquals(1, events.size)
            assertEquals(FeedbackDeepEvent.Error("network"), events[0])
        }

    @Test
    fun `unknown section discriminator is dropped`() =
        runBlocking {
            val body =
                buildString {
                    append("event: object\n")
                    append("data: {\"type\":\"feedbackDeepSection\",\"data\":{\"section\":\"mystery\"}}\n\n")
                    append("event: done\n")
                    append("data: {\"status\":\"ok\"}\n\n")
                }
            server.enqueue(
                MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body),
            )

            val events = withTimeout(5_000) { stream().events(request).toList() }

            assertEquals(1, events.size)
            assertEquals(FeedbackDeepEvent.Done("ok"), events[0])
        }

    @Test
    fun `a slim feedbackSection frame is not this stream's concern and is dropped`() =
        runBlocking {
            val body =
                buildString {
                    append("event: object\n")
                    append(
                        "data: {\"type\":\"feedbackSection\",\"data\":" +
                            "{\"section\":\"writingScore\",\"score\":85,\"encouragementMessage\":\"x\"}}\n\n",
                    )
                    append("event: done\n")
                    append("data: {\"status\":\"ok\"}\n\n")
                }
            server.enqueue(
                MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body),
            )

            val events = withTimeout(5_000) { stream().events(request).toList() }

            assertEquals(1, events.size)
            assertEquals(FeedbackDeepEvent.Done("ok"), events[0])
        }
}

package com.jjundev.oneclickeng.core.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the `task` discriminator on the wire. The shared Json (NetworkModule) uses
 * encodeDefaults=false, so a `task` default would be dropped and the server could not dispatch —
 * every request type pins `task` with @EncodeDefault(ALWAYS). This test mirrors that Json config.
 */
class LlmRequestSerializationTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Test
    fun `tts request keeps task on the wire despite the default`() {
        val body =
            json.encodeToString(
                TtsRequest.serializer(),
                TtsRequest(payload = TtsPayload(text = "hi", gender = "male", speechRate = 1.0f)),
            )
        assertTrue(body, body.contains("\"task\":\"tts\""))
    }

    @Test
    fun `speaking request keeps task and top-level sessionId on the wire`() {
        val body =
            json.encodeToString(
                SpeakingRequest.serializer(),
                SpeakingRequest(sessionId = "s1", payload = SpeakingPayload(audioBase64 = "abc")),
            )
        assertTrue(body, body.contains("\"task\":\"speaking\""))
        assertTrue(body, body.contains("\"sessionId\":\"s1\""))
    }

    @Test
    fun `dialogue request keeps task on the wire despite the default`() {
        val body =
            json.encodeToString(
                DialogueRequest.serializer(),
                DialogueRequest(
                    idempotencyKey = "k1",
                    payload = DialoguePayload(level = "easy", topic = "t", length = 5, firstSession = true),
                ),
            )
        assertTrue(body, body.contains("\"task\":\"dialogue\""))
        assertTrue(body, body.contains("\"idempotencyKey\":\"k1\""))
    }
}

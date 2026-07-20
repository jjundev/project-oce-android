package com.jjundev.oneclickeng.feature.session.analytics

import com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink
import com.jjundev.oneclickeng.feature.session.speaking.SpeakingAnalysisState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionFunnelAnalyticsDispatchTest {
    private val sink = RecordingAnalyticsSink()
    private val analytics = FirebaseSessionFunnelAnalytics(sink)

    @Test
    fun `first_session_generation_started carries idempotency_key_present`() {
        analytics.firstSessionGenerationStarted(idempotencyKeyPresent = true)
        assertEquals(
            RecordingAnalyticsSink.Event("first_session_generation_started", mapOf("idempotency_key_present" to true)),
            sink.events.single(),
        )
    }

    @Test
    fun `first_session_started carries topic length difficulty`() {
        analytics.firstSessionStarted(sessionId = "s1", topicId = "cafe", length = 5, difficulty = "easy")
        assertEquals(
            mapOf("session_id" to "s1", "topic_id" to "cafe", "length" to 5L, "difficulty" to "easy"),
            sink.events.single().params,
        )
        assertEquals("first_session_started", sink.events.single().name)
    }

    @Test
    fun `learning_session_started carries topic length level`() {
        analytics.learningSessionStarted(sessionId = "s2", topicId = "airport", length = 10, level = "normal")
        assertEquals(
            RecordingAnalyticsSink.Event(
                "learning_session_started",
                mapOf("session_id" to "s2", "topic_id" to "airport", "length" to 10L, "level" to "normal"),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `turn_started carries session_id and turn_index`() {
        analytics.turnStarted(sessionId = "s1", turnIndex = 2)
        assertEquals(
            RecordingAnalyticsSink.Event("turn_started", mapOf("session_id" to "s1", "turn_index" to 2L)),
            sink.events.single(),
        )
    }

    @Test
    fun `turn_completed carries input_mode and writing_score when present`() {
        analytics.turnCompleted(sessionId = "s1", turnIndex = 0, inputMode = "voice", writingScore = 82)
        assertEquals(
            mapOf("session_id" to "s1", "turn_index" to 0L, "input_mode" to "voice", "writing_score" to 82L),
            sink.events.single().params,
        )
    }

    @Test
    fun `turn_completed omits writing_score when null`() {
        analytics.turnCompleted(sessionId = "s1", turnIndex = 1, inputMode = "text", writingScore = null)
        assertEquals(
            mapOf("session_id" to "s1", "turn_index" to 1L, "input_mode" to "text"),
            sink.events.single().params,
        )
        assertNull(sink.events.single().params["writing_score"])
    }

    @Test
    fun `speaking_analyze_result carries the result label`() {
        analytics.speakingAnalyzeResult(sessionId = "s1", turnIndex = 0, result = "empty_transcript")
        assertEquals(
            RecordingAnalyticsSink.Event(
                "speaking_analyze_result",
                mapOf("session_id" to "s1", "turn_index" to 0L, "result" to "empty_transcript"),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `deep_feedback_opened carries session_id and turn_index`() {
        analytics.deepFeedbackOpened(sessionId = "s1", turnIndex = 3)
        assertEquals(
            RecordingAnalyticsSink.Event("deep_feedback_opened", mapOf("session_id" to "s1", "turn_index" to 3L)),
            sink.events.single(),
        )
    }

    @Test
    fun `session_complete carries turn_count and is_first`() {
        analytics.sessionComplete(sessionId = "s1", turnCount = 5, isFirst = true)
        assertEquals(
            RecordingAnalyticsSink.Event(
                "session_complete",
                mapOf("session_id" to "s1", "turn_count" to 5L, "is_first" to true),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `summary_partial_failure carries sections_failed count`() {
        analytics.summaryPartialFailure(sessionId = "s1", sectionsFailed = 2)
        assertEquals(
            RecordingAnalyticsSink.Event("summary_partial_failure", mapOf("session_id" to "s1", "sections_failed" to 2L)),
            sink.events.single(),
        )
    }

    @Test
    fun `speakingResultLabel maps each analysis state`() {
        assertEquals(
            "transcript_present",
            speakingResultLabel(SpeakingAnalysisState.Result(transcript = "hi", encouragement = "Nice job!")),
        )
        assertEquals("empty_transcript", speakingResultLabel(SpeakingAnalysisState.Empty))
        assertEquals("analyze_failed", speakingResultLabel(SpeakingAnalysisState.Failed))
        assertNull(speakingResultLabel(SpeakingAnalysisState.Analyzing))
        assertNull(speakingResultLabel(SpeakingAnalysisState.Idle))
    }
}

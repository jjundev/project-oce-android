package com.jjundev.oneclickeng.feature.session.analytics

import com.jjundev.oneclickeng.core.analytics.AnalyticsSink
import com.jjundev.oneclickeng.feature.session.speaking.SpeakingAnalysisState
import javax.inject.Inject

/**
 * Session-lifecycle funnel telemetry seam (M4-01b). One seam for the whole dialogue-session funnel —
 * generation start → session start → per-turn → session end. Ids/params per analytics-events.md §4/§5
 * (Event Decision Table in the plan). Dispatches through [AnalyticsSink] so the id/param contract is
 * unit-testable (repo convention = no mockk). PII boundary: enum/bool/count/id only.
 */
@Suppress("TooManyFunctions")
interface SessionFunnelAnalytics {
    fun firstSessionGenerationStarted(idempotencyKeyPresent: Boolean)

    fun firstSessionStarted(sessionId: String, topicId: String, length: Int, difficulty: String)

    fun learningSessionStarted(sessionId: String, topicId: String, length: Int, level: String)

    fun turnStarted(sessionId: String, turnIndex: Int)

    fun turnCompleted(sessionId: String, turnIndex: Int, inputMode: String, writingScore: Int?)

    fun speakingAnalyzeResult(sessionId: String, turnIndex: Int, result: String)

    fun deepFeedbackOpened(sessionId: String, turnIndex: Int)

    fun sessionComplete(sessionId: String, turnCount: Int, isFirst: Boolean)

    fun summaryPartialFailure(sessionId: String, sectionsFailed: Int)
}

/** GA4 `result` value for a speech-analysis outcome, or null when the state is not a terminal result. */
internal fun speakingResultLabel(state: SpeakingAnalysisState): String? =
    when (state) {
        is SpeakingAnalysisState.Result -> "transcript_present"
        SpeakingAnalysisState.Empty -> "empty_transcript"
        SpeakingAnalysisState.Failed -> "analyze_failed"
        SpeakingAnalysisState.Analyzing, SpeakingAnalysisState.Idle -> null
    }

/** Default no-op binding until DebugView verification; also the fallback in tests that don't assert analytics. */
@Suppress("TooManyFunctions")
class NoOpSessionFunnelAnalytics
    @Inject
    constructor() : SessionFunnelAnalytics {
        override fun firstSessionGenerationStarted(idempotencyKeyPresent: Boolean) = Unit

        override fun firstSessionStarted(sessionId: String, topicId: String, length: Int, difficulty: String) = Unit

        override fun learningSessionStarted(sessionId: String, topicId: String, length: Int, level: String) = Unit

        override fun turnStarted(sessionId: String, turnIndex: Int) = Unit

        override fun turnCompleted(sessionId: String, turnIndex: Int, inputMode: String, writingScore: Int?) = Unit

        override fun speakingAnalyzeResult(sessionId: String, turnIndex: Int, result: String) = Unit

        override fun deepFeedbackOpened(sessionId: String, turnIndex: Int) = Unit

        override fun sessionComplete(sessionId: String, turnCount: Int, isFirst: Boolean) = Unit

        override fun summaryPartialFailure(sessionId: String, sectionsFailed: Int) = Unit
    }

/** Firebase dispatch via the shared [AnalyticsSink] (M4-01a). */
@Suppress("TooManyFunctions")
class FirebaseSessionFunnelAnalytics
    @Inject
    constructor(
        private val sink: AnalyticsSink,
    ) : SessionFunnelAnalytics {
        override fun firstSessionGenerationStarted(idempotencyKeyPresent: Boolean) =
            sink.log("first_session_generation_started", mapOf("idempotency_key_present" to idempotencyKeyPresent))

        override fun firstSessionStarted(sessionId: String, topicId: String, length: Int, difficulty: String) =
            sink.log(
                "first_session_started",
                mapOf("session_id" to sessionId, "topic_id" to topicId, "length" to length.toLong(), "difficulty" to difficulty),
            )

        override fun learningSessionStarted(sessionId: String, topicId: String, length: Int, level: String) =
            sink.log(
                "learning_session_started",
                mapOf("session_id" to sessionId, "topic_id" to topicId, "length" to length.toLong(), "level" to level),
            )

        override fun turnStarted(sessionId: String, turnIndex: Int) =
            sink.log("turn_started", mapOf("session_id" to sessionId, "turn_index" to turnIndex.toLong()))

        override fun turnCompleted(sessionId: String, turnIndex: Int, inputMode: String, writingScore: Int?) =
            sink.log(
                "turn_completed",
                buildMap {
                    put("session_id", sessionId)
                    put("turn_index", turnIndex.toLong())
                    put("input_mode", inputMode)
                    writingScore?.let { put("writing_score", it.toLong()) }
                },
            )

        override fun speakingAnalyzeResult(sessionId: String, turnIndex: Int, result: String) =
            sink.log(
                "speaking_analyze_result",
                mapOf("session_id" to sessionId, "turn_index" to turnIndex.toLong(), "result" to result),
            )

        override fun deepFeedbackOpened(sessionId: String, turnIndex: Int) =
            sink.log("deep_feedback_opened", mapOf("session_id" to sessionId, "turn_index" to turnIndex.toLong()))

        override fun sessionComplete(sessionId: String, turnCount: Int, isFirst: Boolean) =
            sink.log(
                "session_complete",
                mapOf("session_id" to sessionId, "turn_count" to turnCount.toLong(), "is_first" to isFirst),
            )

        override fun summaryPartialFailure(sessionId: String, sectionsFailed: Int) =
            sink.log("summary_partial_failure", mapOf("session_id" to sessionId, "sections_failed" to sectionsFailed.toLong()))
    }

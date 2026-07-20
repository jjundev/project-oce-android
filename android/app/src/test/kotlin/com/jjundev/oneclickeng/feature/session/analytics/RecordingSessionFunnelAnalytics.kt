package com.jjundev.oneclickeng.feature.session.analytics

/** Records session-funnel calls for emit-site behavior tests (repo convention = fakes). */
@Suppress("TooManyFunctions")
class RecordingSessionFunnelAnalytics : SessionFunnelAnalytics {
    data class Call(val name: String, val args: Map<String, Any?>)

    val calls = mutableListOf<Call>()

    override fun firstSessionGenerationStarted(idempotencyKeyPresent: Boolean) {
        calls += Call("first_session_generation_started", mapOf("idempotency_key_present" to idempotencyKeyPresent))
    }

    override fun firstSessionStarted(sessionId: String, topicId: String, length: Int, difficulty: String) {
        calls += Call("first_session_started", mapOf("session_id" to sessionId, "topic_id" to topicId, "length" to length, "difficulty" to difficulty))
    }

    override fun learningSessionStarted(sessionId: String, topicId: String, length: Int, level: String) {
        calls += Call("learning_session_started", mapOf("session_id" to sessionId, "topic_id" to topicId, "length" to length, "level" to level))
    }

    override fun turnStarted(sessionId: String, turnIndex: Int) {
        calls += Call("turn_started", mapOf("session_id" to sessionId, "turn_index" to turnIndex))
    }

    override fun turnCompleted(sessionId: String, turnIndex: Int, inputMode: String, writingScore: Int?) {
        calls += Call("turn_completed", mapOf("session_id" to sessionId, "turn_index" to turnIndex, "input_mode" to inputMode, "writing_score" to writingScore))
    }

    override fun speakingAnalyzeResult(sessionId: String, turnIndex: Int, result: String) {
        calls += Call("speaking_analyze_result", mapOf("session_id" to sessionId, "turn_index" to turnIndex, "result" to result))
    }

    override fun deepFeedbackOpened(sessionId: String, turnIndex: Int) {
        calls += Call("deep_feedback_opened", mapOf("session_id" to sessionId, "turn_index" to turnIndex))
    }

    override fun sessionComplete(sessionId: String, turnCount: Int, isFirst: Boolean) {
        calls += Call("session_complete", mapOf("session_id" to sessionId, "turn_count" to turnCount, "is_first" to isFirst))
    }

    override fun summaryPartialFailure(sessionId: String, sectionsFailed: Int) {
        calls += Call("summary_partial_failure", mapOf("session_id" to sessionId, "sections_failed" to sectionsFailed))
    }
}

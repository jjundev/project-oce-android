package com.jjundev.oneclickeng.core.network

import kotlinx.coroutines.flow.Flow

/**
 * Source of the `feedbackDeep` SSE stream (M2-03). A cold [Flow] of parsed [FeedbackDeepEvent]s:
 * collecting opens the connection, cancelling the collector closes it — mirrors [FeedbackStream].
 * The real implementation is [DeepFeedbackSseStream]; tests substitute a fake so the coordinator's
 * ordering / cache / stale / failure logic is exercised without a socket.
 */
interface DeepFeedbackStream {
    fun events(request: FeedbackDeepRequest): Flow<FeedbackDeepEvent>
}

package com.jjundev.oneclickeng.core.network

import kotlinx.coroutines.flow.Flow

/**
 * Source of the `feedback` SSE stream (M1-07). A cold [Flow] of parsed [FeedbackEvent]s: collecting
 * opens the connection, cancelling the collector closes it — mirrors [DialogueStream]. The real
 * implementation is [FeedbackSseStream]; tests substitute a fake so the coordinator's ordering /
 * stale / failure logic is exercised without a socket.
 */
interface FeedbackStream {
    fun events(request: FeedbackRequest): Flow<FeedbackEvent>
}

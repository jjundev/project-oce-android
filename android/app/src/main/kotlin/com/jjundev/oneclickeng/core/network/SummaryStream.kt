package com.jjundev.oneclickeng.core.network

import kotlinx.coroutines.flow.Flow

/**
 * Source of the `summary` SSE stream (M2-02). A cold [Flow] of parsed [SummaryEvent]s: collecting
 * opens the connection, cancelling the collector closes it — mirrors [FeedbackStream]. The real
 * implementation is [SummarySseStream]; tests substitute a fake so the coordinator's bundle /
 * partial-failure / stale / retry logic is exercised without a socket.
 */
interface SummaryStream {
    fun events(request: SummaryRequest): Flow<SummaryEvent>
}

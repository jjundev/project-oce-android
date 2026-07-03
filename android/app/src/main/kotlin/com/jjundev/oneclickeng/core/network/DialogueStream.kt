package com.jjundev.oneclickeng.core.network

import kotlinx.coroutines.flow.Flow

/**
 * Source of the `dialogue` SSE stream (M1-01). A cold [Flow] of parsed [DialogueEvent]s: collecting
 * opens the connection, cancelling the collector closes it. The real implementation is
 * [DialogueSseStream]; tests substitute a fake so the coordinator's ordering/stale/failure logic is
 * exercised without a socket.
 */
interface DialogueStream {
    fun events(request: DialogueRequest): Flow<DialogueEvent>
}

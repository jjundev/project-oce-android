package com.jjundev.oneclickeng.feature.session.dialogue

import com.jjundev.oneclickeng.core.network.DialogueEvent
import com.jjundev.oneclickeng.core.network.DialogueRequest
import com.jjundev.oneclickeng.core.network.DialogueStream
import com.jjundev.oneclickeng.core.network.DialogueTurn
import com.jjundev.oneclickeng.core.time.FakeElapsedClock
import com.jjundev.oneclickeng.feature.session.analytics.LatencyAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.RecordingLatencyAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class LatencyFakeDialogueStream : DialogueStream {
    private val channels = mutableListOf<Channel<DialogueEvent>>()

    override fun events(request: DialogueRequest): Flow<DialogueEvent> {
        val channel = Channel<DialogueEvent>(Channel.UNLIMITED)
        channels += channel
        return channel.consumeAsFlow()
    }

    fun push(event: DialogueEvent) = channels.last().trySend(event)

    fun end() = channels.last().close()
}

@OptIn(ExperimentalCoroutinesApi::class)
class DialogueGenerationScriptGenLatencyTest {
    private fun TestScope.coordScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `first turn logs script_gen_latency_ms successful with exact elapsed ms`() =
        runTest {
            val stream = LatencyFakeDialogueStream()
            val clock = FakeElapsedClock(now = 1_000L)
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                DialogueGenerationCoordinator(stream, coordScope(), clock = clock, latencyAnalytics = latency)

            coordinator.start("easy", "coffee", 5, firstSession = true)
            runCurrent()
            clock.advance(750L)
            stream.push(DialogueEvent.Turn(DialogueTurn(ko = "안녕", en = "Hi", role = "model")))
            runCurrent()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("script_gen", LatencyAnalytics.OUTCOME_SUCCESSFUL, 750L)),
                latency.calls,
            )
        }

    @Test
    fun `second turn does not re-log latency`() =
        runTest {
            val stream = LatencyFakeDialogueStream()
            val clock = FakeElapsedClock()
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                DialogueGenerationCoordinator(stream, coordScope(), clock = clock, latencyAnalytics = latency)

            coordinator.start("easy", "coffee", 5, firstSession = true)
            runCurrent()
            stream.push(DialogueEvent.Turn(DialogueTurn(ko = "안녕", en = "Hi", role = "model")))
            runCurrent()
            stream.push(DialogueEvent.Turn(DialogueTurn(ko = "잘가", en = "Bye", role = "model")))
            runCurrent()

            assertEquals(1, latency.calls.size)
        }

    @Test
    fun `done before any turn logs script_gen_latency_ms failed`() =
        runTest {
            val stream = LatencyFakeDialogueStream()
            val clock = FakeElapsedClock(now = 500L)
            val latency = RecordingLatencyAnalytics()
            val coordinator =
                DialogueGenerationCoordinator(stream, coordScope(), clock = clock, latencyAnalytics = latency)

            coordinator.start("easy", "coffee", 5, firstSession = true)
            runCurrent()
            clock.advance(1_500L)
            stream.push(DialogueEvent.Done("ok"))
            runCurrent()

            assertEquals(
                listOf(RecordingLatencyAnalytics.Call("script_gen", LatencyAnalytics.OUTCOME_FAILED, 1_500L)),
                latency.calls,
            )
        }
}

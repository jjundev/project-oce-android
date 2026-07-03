package com.jjundev.oneclickeng.feature.session.dialogue

import com.jjundev.oneclickeng.core.network.DialogueEvent
import com.jjundev.oneclickeng.core.network.DialogueMeta
import com.jjundev.oneclickeng.core.network.DialogueRequest
import com.jjundev.oneclickeng.core.network.DialogueStream
import com.jjundev.oneclickeng.core.network.DialogueTurn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun turn(
    en: String,
    role: String = "model",
) = DialogueTurn(ko = "안녕", en = en, role = role)

private val META = DialogueMeta("커피 주문", "John", "male", "Barista")

/** Fake stream: each events() call yields a fresh channel-backed cold flow the test drives. */
private class FakeDialogueStream : DialogueStream {
    val requests = mutableListOf<DialogueRequest>()
    private val channels = mutableListOf<Channel<DialogueEvent>>()

    override fun events(request: DialogueRequest): Flow<DialogueEvent> {
        requests += request
        val channel = Channel<DialogueEvent>(Channel.UNLIMITED)
        channels += channel
        return channel.consumeAsFlow()
    }

    fun push(event: DialogueEvent) {
        channels.last().trySend(event)
    }

    /** Close the current stream (SSE connection ends). */
    fun end() {
        channels.last().close()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DialogueGenerationCoordinatorTest {
    @Test
    fun `turns render in arrival order and first turn flips Ready`() =
        runTest {
            val stream = FakeDialogueStream()
            val coordinator = DialogueGenerationCoordinator(stream, coordScope())

            coordinator.start("easy", "coffee", 5, firstSession = true)
            runCurrent()
            assertEquals(DialogueGenState.Generating, coordinator.state.value)

            stream.push(DialogueEvent.Start("s1", remaining = 2))
            runCurrent()
            // Start alone does NOT transition (no renderable content).
            assertEquals(DialogueGenState.Generating, coordinator.state.value)

            stream.push(DialogueEvent.Turn(turn("Hi")))
            stream.push(DialogueEvent.Turn(turn("How are you?", role = "user")))
            stream.push(DialogueEvent.Turn(turn("Good, thanks")))
            runCurrent()

            val state = coordinator.state.value
            assertTrue(state is DialogueGenState.Ready)
            state as DialogueGenState.Ready
            assertEquals(listOf("Hi", "How are you?", "Good, thanks"), state.turns.map { it.en })
            assertEquals("s1", state.sessionId)
            assertEquals(2, state.remaining)
            assertEquals("s1", coordinator.sessionId())
        }

    @Test
    fun `meta arriving before the first turn is retained in Ready`() =
        runTest {
            val stream = FakeDialogueStream()
            val coordinator = DialogueGenerationCoordinator(stream, coordScope())

            coordinator.start("easy", "coffee", 5, true)
            runCurrent()
            stream.push(DialogueEvent.Meta(META))
            stream.push(DialogueEvent.Turn(turn("Hi")))
            runCurrent()

            val state = coordinator.state.value as DialogueGenState.Ready
            assertEquals(META, state.meta)
        }

    @Test
    fun `done before any turn maps to Failed`() =
        runTest {
            val stream = FakeDialogueStream()
            val coordinator = DialogueGenerationCoordinator(stream, coordScope())

            coordinator.start("easy", "coffee", 5, true)
            runCurrent()
            stream.push(DialogueEvent.Done("ok"))
            runCurrent()

            assertEquals(DialogueGenState.Failed, coordinator.state.value)
        }

    @Test
    fun `stream closing before any turn maps to Failed`() =
        runTest {
            val stream = FakeDialogueStream()
            val coordinator = DialogueGenerationCoordinator(stream, coordScope())

            coordinator.start("easy", "coffee", 5, true)
            runCurrent()
            stream.end() // connection closed with no frames
            advanceUntilIdle()

            assertEquals(DialogueGenState.Failed, coordinator.state.value)
        }

    @Test
    fun `error before Ready maps to Failed`() =
        runTest {
            val stream = FakeDialogueStream()
            val coordinator = DialogueGenerationCoordinator(stream, coordScope())

            coordinator.start("easy", "coffee", 5, true)
            runCurrent()
            stream.push(DialogueEvent.Error("gen_failed"))
            runCurrent()

            assertEquals(DialogueGenState.Failed, coordinator.state.value)
        }

    @Test
    fun `error after Ready is ignored (Ready is sticky)`() =
        runTest {
            val stream = FakeDialogueStream()
            val coordinator = DialogueGenerationCoordinator(stream, coordScope())

            coordinator.start("easy", "coffee", 5, true)
            runCurrent()
            stream.push(DialogueEvent.Turn(turn("Hi")))
            runCurrent()
            stream.push(DialogueEvent.Error("late_error"))
            runCurrent()

            val state = coordinator.state.value
            assertTrue(state is DialogueGenState.Ready)
            assertEquals(listOf("Hi"), (state as DialogueGenState.Ready).turns.map { it.en })
        }

    @Test
    fun `idle before the first turn trips the watchdog to Failed`() =
        runTest {
            val stream = FakeDialogueStream()
            val coordinator = DialogueGenerationCoordinator(stream, coordScope())

            coordinator.start("easy", "coffee", 5, true)
            // No frames ever arrive; the inter-event idle watchdog (30s) must fire.
            advanceUntilIdle()

            assertEquals(DialogueGenState.Failed, coordinator.state.value)
        }

    @Test
    fun `retry reuses the same idempotencyKey`() =
        runTest {
            val stream = FakeDialogueStream()
            val coordinator = DialogueGenerationCoordinator(stream, coordScope())

            coordinator.start("easy", "coffee", 5, true)
            runCurrent()
            stream.push(DialogueEvent.Error("gen_failed"))
            runCurrent()
            assertEquals(DialogueGenState.Failed, coordinator.state.value)

            coordinator.retry()
            runCurrent()
            assertEquals(DialogueGenState.Generating, coordinator.state.value)

            assertEquals(2, stream.requests.size)
            assertEquals(stream.requests[0].idempotencyKey, stream.requests[1].idempotencyKey)
            // A brand-new start(), by contrast, mints a fresh key.
            coordinator.start("easy", "coffee", 5, true)
            runCurrent()
            assertTrue(stream.requests[2].idempotencyKey != stream.requests[0].idempotencyKey)
        }

    @Test
    fun `a superseding start resets state and drives the new attempt`() =
        runTest {
            val stream = FakeDialogueStream()
            val coordinator = DialogueGenerationCoordinator(stream, coordScope())

            coordinator.start("easy", "coffee", 5, true)
            runCurrent()
            stream.push(DialogueEvent.Turn(turn("first-attempt")))
            runCurrent()
            assertTrue(coordinator.state.value is DialogueGenState.Ready)

            // Supersede: a new generation must reset to Generating and ignore the old stream.
            coordinator.start("normal", "travel", 10, false)
            runCurrent()
            assertEquals(DialogueGenState.Generating, coordinator.state.value)

            stream.push(DialogueEvent.Turn(turn("second-attempt")))
            runCurrent()
            val state = coordinator.state.value as DialogueGenState.Ready
            assertEquals(listOf("second-attempt"), state.turns.map { it.en })
        }

    /** Coordinator scope on an unconfined dispatcher tied to the test scheduler (see speaking test). */
    private fun TestScope.coordScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
}

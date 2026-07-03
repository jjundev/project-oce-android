package com.jjundev.oneclickeng.feature.session.dialogue

import com.jjundev.oneclickeng.core.network.DialogueEvent
import com.jjundev.oneclickeng.core.network.DialogueRequest
import com.jjundev.oneclickeng.core.network.DialogueStream
import com.jjundev.oneclickeng.core.network.WaitQuizAnalytics
import com.jjundev.oneclickeng.feature.session.dialogue.quiz.QuizBank
import com.jjundev.oneclickeng.ui.component.QuizItem
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun item(id: String) =
    QuizItem(
        id = id,
        level = 1,
        prompt = "p",
        optionA = "a",
        optionB = "b",
        correctIndex = 0,
        revealCopyCorrect = "c",
        revealCopyWrong = "w",
    )

private class FakeStream : DialogueStream {
    private val channels = mutableListOf<Channel<DialogueEvent>>()

    override fun events(request: DialogueRequest): Flow<DialogueEvent> {
        val channel = Channel<DialogueEvent>(Channel.UNLIMITED)
        channels += channel
        return channel.consumeAsFlow()
    }

    fun push(event: DialogueEvent) {
        channels.last().trySend(event)
    }
}

private class FakeQuizBank(private val byTier: Map<String, List<QuizItem>>) : QuizBank {
    override fun forTier(tier: String): List<QuizItem> = byTier[tier].orEmpty()
}

private class RecordingAnalytics : WaitQuizAnalytics {
    data class Call(val sessionId: String?, val cardId: String, val correct: Boolean, val index: Int)

    val calls = mutableListOf<Call>()

    override fun cardAnswered(
        sessionId: String?,
        cardId: String,
        choseCorrect: Boolean,
        cardIndex: Int,
    ) {
        calls += Call(sessionId, cardId, choseCorrect, cardIndex)
    }
}

private class FakeConfig(override val loadingQuizEnabled: Boolean) : LoadingQuizConfig

@OptIn(ExperimentalCoroutinesApi::class)
class DialogueGenerationViewModelTest {
    private val bank =
        FakeQuizBank(mapOf("easy" to listOf(item("e1")), "hard" to listOf(item("h1"))))

    @Test
    fun `first session forces the easy tier regardless of requested level`() =
        runTest {
            val vm = viewModel(RecordingAnalytics(), FakeConfig(true))

            vm.start(level = "hard", topic = "t", length = 5, firstSession = true)

            assertEquals(listOf("e1"), vm.quizItems.value.map { it.id })
        }

    @Test
    fun `non-first session uses the requested level as the tier`() =
        runTest {
            val vm = viewModel(RecordingAnalytics(), FakeConfig(true))

            vm.start(level = "hard", topic = "t", length = 10, firstSession = false)

            assertEquals(listOf("h1"), vm.quizItems.value.map { it.id })
        }

    @Test
    fun `kill-switch off yields no quiz items and quizEnabled false`() =
        runTest {
            val vm = viewModel(RecordingAnalytics(), FakeConfig(false))

            vm.start(level = "easy", topic = "t", length = 5, firstSession = true)

            assertFalse(vm.quizEnabled)
            assertTrue(vm.quizItems.value.isEmpty())
        }

    @Test
    fun `quiz answers route to analytics with sessionId and a monotonic card index`() =
        runTest {
            val analytics = RecordingAnalytics()
            val stream = FakeStream()
            val vm = viewModel(analytics, FakeConfig(true), stream)

            vm.start(level = "easy", topic = "t", length = 5, firstSession = true)
            runCurrent()
            stream.push(DialogueEvent.Start(sessionId = "s9", remaining = 3))
            runCurrent()

            vm.onQuizAnswered(item("e1"), correct = true)
            vm.onQuizAnswered(item("e2"), correct = false)

            assertEquals(2, analytics.calls.size)
            assertEquals(
                RecordingAnalytics.Call(sessionId = "s9", cardId = "e1", correct = true, index = 0),
                analytics.calls[0],
            )
            assertEquals(
                RecordingAnalytics.Call(sessionId = "s9", cardId = "e2", correct = false, index = 1),
                analytics.calls[1],
            )
        }

    private fun TestScope.viewModel(
        analytics: RecordingAnalytics,
        config: FakeConfig,
        stream: FakeStream = FakeStream(),
    ): DialogueGenerationViewModel {
        val scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = DialogueGenerationCoordinator(stream, scope)
        return DialogueGenerationViewModel(coordinator, bank, analytics, config)
    }
}

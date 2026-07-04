package com.jjundev.oneclickeng.feature.session.dialogue

import com.jjundev.oneclickeng.core.connectivity.ConnectivityObserver
import com.jjundev.oneclickeng.core.connectivity.OfflineAnalytics
import com.jjundev.oneclickeng.core.network.DialogueEvent
import com.jjundev.oneclickeng.core.network.DialogueRequest
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.jjundev.oneclickeng.core.network.DialogueStream
import com.jjundev.oneclickeng.core.network.LimitAnalytics
import com.jjundev.oneclickeng.core.network.WaitQuizAnalytics
import com.jjundev.oneclickeng.feature.session.dialogue.quiz.QuizBank
import com.jjundev.oneclickeng.feature.session.resume.SessionSnapshotStore
import com.jjundev.oneclickeng.ui.component.QuizItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    /** How many times a stream was opened — 0 means generation never started (pre-flight offline gate). */
    val opened: Int get() = channels.size

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

private class RecordingLimitAnalytics : LimitAnalytics {
    data class Call(val remaining: Int, val surface: String)

    val calls = mutableListOf<Call>()

    override fun limitReached(
        remaining: Int,
        surface: String,
    ) {
        calls += Call(remaining, surface)
    }
}

private class FakeConfig(override val loadingQuizEnabled: Boolean) : LoadingQuizConfig

private class RecordingOfflineAnalytics : OfflineAnalytics {
    val blocked = mutableListOf<String>()
    val transitions = mutableListOf<Boolean>()

    override fun connectivityChanged(online: Boolean) {
        transitions += online
    }

    override fun offlineBlocked(surface: String) {
        blocked += surface
    }
}

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

    @Test
    fun `onLimitReached routes limit_reached to the analytics seam with the dialogue_start_gate surface`() =
        runTest {
            val limit = RecordingLimitAnalytics()
            val vm = viewModel(RecordingAnalytics(), FakeConfig(true), limitAnalytics = limit)

            vm.onLimitReached(remaining = 0)

            assertEquals(
                listOf(RecordingLimitAnalytics.Call(remaining = 0, surface = "dialogue_start_gate")),
                limit.calls,
            )
        }

    @Test
    fun `offline start gates OfflineBlocked without opening a stream and logs offline_blocked_action`() =
        runTest {
            val stream = FakeStream()
            val offline = RecordingOfflineAnalytics()
            val vm =
                viewModel(
                    RecordingAnalytics(),
                    FakeConfig(true),
                    stream,
                    connectivity = FakeConnectivity(offline = true),
                    offlineAnalytics = offline,
                )

            vm.start(level = "easy", topic = "t", length = 5, firstSession = true)
            runCurrent()

            assertEquals(DialogueGenState.OfflineBlocked, vm.state.value)
            assertTrue(vm.quizItems.value.isEmpty())
            assertEquals(0, stream.opened) // 스트림 미기동(전송 없음)
            assertEquals(listOf("dialogue_start_gate"), offline.blocked)
        }

    @Test
    fun `retry after a pre-flight offline gate re-checks connectivity and starts once online`() =
        runTest {
            val stream = FakeStream()
            val connectivity = SwitchableConnectivity(offline = true)
            val vm =
                viewModel(
                    RecordingAnalytics(),
                    FakeConfig(true),
                    stream,
                    connectivity = connectivity,
                    offlineAnalytics = RecordingOfflineAnalytics(),
                )

            vm.start(level = "easy", topic = "t", length = 5, firstSession = false)
            runCurrent()
            assertEquals(DialogueGenState.OfflineBlocked, vm.state.value)
            assertEquals(0, stream.opened)

            connectivity.online() // 연결 복귀
            vm.retry()
            runCurrent()

            assertEquals(DialogueGenState.Generating, vm.state.value)
            assertEquals(1, stream.opened) // 이제 스트림 기동
        }

    @Suppress("LongParameterList") // 테스트 팩토리 — seam 별 fake 를 명시 주입한다(운영 코드 아님).
    private fun TestScope.viewModel(
        analytics: RecordingAnalytics,
        config: FakeConfig,
        stream: FakeStream = FakeStream(),
        limitAnalytics: RecordingLimitAnalytics = RecordingLimitAnalytics(),
        connectivity: ConnectivityObserver = FakeConnectivity(offline = false),
        offlineAnalytics: OfflineAnalytics = RecordingOfflineAnalytics(),
    ): DialogueGenerationViewModel {
        val scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = DialogueGenerationCoordinator(stream, scope, connectivity)
        val snapshotStore = SessionSnapshotStore(inMemoryPrefsDataStore())
        return DialogueGenerationViewModel(
            coordinator,
            bank,
            analytics,
            limitAnalytics,
            snapshotStore,
            scope,
            offlineAnalytics,
            config,
        )
    }
}

/** 파일 I/O 없는 인메모리 [DataStore] — snapshotStore 주입용(테스트는 store 를 직접 검증하지 않는다). */
private fun inMemoryPrefsDataStore(): DataStore<Preferences> =
    object : DataStore<Preferences> {
        private val flow = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = flow

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(flow.value)
            flow.value = updated
            return updated
        }
    }

package com.jjundev.oneclickeng.feature.session.dialogue

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.jjundev.oneclickeng.core.audio.PcmPlayer
import com.jjundev.oneclickeng.core.connectivity.ConnectivityObserver
import com.jjundev.oneclickeng.core.connectivity.OfflineAnalytics
import com.jjundev.oneclickeng.core.network.DialogueEvent
import com.jjundev.oneclickeng.core.network.DialogueRequest
import com.jjundev.oneclickeng.core.network.DialogueStream
import com.jjundev.oneclickeng.core.network.DialogueTurn
import com.jjundev.oneclickeng.core.network.LimitAnalytics
import com.jjundev.oneclickeng.core.network.LlmApi
import com.jjundev.oneclickeng.core.network.SpeakingRequest
import com.jjundev.oneclickeng.core.network.SpeakingResponse
import com.jjundev.oneclickeng.core.network.TtsRequest
import com.jjundev.oneclickeng.core.network.TtsResponse
import com.jjundev.oneclickeng.core.network.WaitQuizAnalytics
import com.jjundev.oneclickeng.core.settings.TtsQuality
import com.jjundev.oneclickeng.core.settings.TtsSettings
import com.jjundev.oneclickeng.core.settings.TtsSettingsRepository
import com.jjundev.oneclickeng.feature.session.analytics.NoOpSessionFunnelAnalytics
import com.jjundev.oneclickeng.feature.session.dialogue.loading.LoadingMessageSource
import com.jjundev.oneclickeng.feature.session.dialogue.quiz.QuizBank
import com.jjundev.oneclickeng.feature.session.resume.SessionSnapshotStore
import com.jjundev.oneclickeng.feature.session.tts.DeviceTts
import com.jjundev.oneclickeng.feature.session.tts.DeviceTtsResult
import com.jjundev.oneclickeng.feature.session.tts.TtsPlaybackCoordinator
import com.jjundev.oneclickeng.ui.component.QuizItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

private class FakeLoadingMessageSource : LoadingMessageSource {
    val requests = mutableListOf<Boolean>()

    override fun forSession(isOnboarding: Boolean): String {
        requests += isOnboarding
        return if (isOnboarding) "onboarding-copy" else "returning-copy"
    }
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

    override fun waitQuizShown(
        sessionId: String?,
        surface: String,
        delayMsAtShow: Long,
    ) = Unit

    override fun waitQuizEnded(
        sessionId: String?,
        surface: String,
        reason: String,
        cardsAnswered: Int,
        dwellMs: Long,
    ) = Unit
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

private class CountingTtsApi : LlmApi {
    var callCount = 0
    var lastText: String? = null
    var lastGender: String? = null

    override suspend fun tts(body: TtsRequest): TtsResponse {
        callCount++
        lastText = body.payload.text
        lastGender = body.payload.gender
        return TtsResponse(pcmBase64 = "", sampleRate = 24000, mimeType = "audio/L16;rate=24000")
    }

    override suspend fun speaking(body: SpeakingRequest): SpeakingResponse = error("unused")
}

private class NoopPcmPlayer : PcmPlayer {
    override suspend fun play(
        pcm: ByteArray,
        sampleRateHz: Int,
        speed: Float,
    ) = Unit

    override fun stop() = Unit
}

private class NoopDeviceTts : DeviceTts {
    override suspend fun speak(
        text: String,
        gender: String?,
        speechRate: Float,
        onStart: () -> Unit,
    ): DeviceTtsResult = DeviceTtsResult.COMPLETED

    override fun stop() = Unit
}

private class ServerTtsSettings(private val quality: TtsQuality = TtsQuality.SERVER) : TtsSettingsRepository {
    private val value = TtsSettings(quality = quality)
    override val settings: Flow<TtsSettings> = flowOf(value)

    override suspend fun current(): TtsSettings = value // default quality = SERVER

    override suspend fun setQuality(quality: TtsQuality) = Unit

    override suspend fun setSpeechRate(rate: Float) = Unit

    override suspend fun setMuted(muted: Boolean) = Unit
}

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
    fun `onboarding start exposes the onboarding loading message`() =
        runTest {
            val source = FakeLoadingMessageSource()
            val vm = viewModel(RecordingAnalytics(), FakeConfig(true), loadingMessages = source)

            vm.start(level = "easy", topic = "t", length = 5, firstSession = true, isOnboarding = true)

            assertEquals("onboarding-copy", vm.loadingMessage.value)
            assertEquals(listOf(true), source.requests)
        }

    @Test
    fun `returning start exposes the returning loading message`() =
        runTest {
            val source = FakeLoadingMessageSource()
            val vm = viewModel(RecordingAnalytics(), FakeConfig(true), loadingMessages = source)

            vm.start(level = "hard", topic = "t", length = 10, firstSession = false, isOnboarding = false)

            assertEquals("returning-copy", vm.loadingMessage.value)
            assertEquals(listOf(false), source.requests)
        }

    @Test
    fun `retry keeps the selected message while a fresh start selects again`() =
        runTest {
            val source = FakeLoadingMessageSource()
            val vm = viewModel(RecordingAnalytics(), FakeConfig(true), loadingMessages = source)

            vm.start(level = "easy", topic = "t", length = 5, firstSession = false, isOnboarding = false)
            val selected = vm.loadingMessage.value
            vm.retry()
            assertEquals(listOf(false), source.requests)
            assertEquals(selected, vm.loadingMessage.value)

            vm.start(level = "easy", topic = "t2", length = 5, firstSession = false, isOnboarding = false)
            assertEquals(listOf(false, false), source.requests)
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

    @Test
    fun `a prior generation's sticky Ready does not leak into a newly created generation VM`() =
        runTest {
            val stream = FakeStream()
            val scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val coordinator =
                DialogueGenerationCoordinator(stream, scope, FakeConnectivity(offline = false))

            // A prior generation completed → the process-singleton coordinator holds a sticky Ready.
            coordinator.start("easy", "t", 5, firstSession = true)
            runCurrent()
            stream.push(DialogueEvent.Start(sessionId = "s1", remaining = 3))
            stream.push(DialogueEvent.Turn(DialogueTurn(ko = "안녕", en = "Hi", role = "model")))
            runCurrent()
            assertTrue(coordinator.state.value is DialogueGenState.Ready)

            // A new generating screen mounts → a fresh VM is created sharing that singleton coordinator.
            val tts =
                TtsPlaybackCoordinator(CountingTtsApi(), NoopPcmPlayer(), NoopDeviceTts(), ServerTtsSettings(), scope)
            val vm =
                DialogueGenerationViewModel(
                    coordinator,
                    tts,
                    bank,
                    FakeLoadingMessageSource(),
                    RecordingAnalytics(),
                    RecordingLimitAnalytics(),
                    SessionSnapshotStore(inMemoryPrefsDataStore()),
                    scope,
                    RecordingOfflineAnalytics(),
                    NoOpSessionFunnelAnalytics(),
                    FakeConfig(true),
                )

            // init must reset the leftover Ready so the generating screen sees Idle (→ quiz, not auto-skip).
            assertEquals(DialogueGenState.Idle, vm.state.value)
        }

    @Test
    fun `prepareFirstLine warms the first opponent line and marks it ready`() =
        runTest {
            val stream = FakeStream()
            val scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val coordinator = DialogueGenerationCoordinator(stream, scope, FakeConnectivity(offline = false))
            val ttsApi = CountingTtsApi()
            val tts = TtsPlaybackCoordinator(ttsApi, NoopPcmPlayer(), NoopDeviceTts(), ServerTtsSettings(), scope)

            val vm =
                DialogueGenerationViewModel(
                    coordinator,
                    tts,
                    bank,
                    FakeLoadingMessageSource(),
                    RecordingAnalytics(),
                    RecordingLimitAnalytics(),
                    SessionSnapshotStore(inMemoryPrefsDataStore()),
                    scope,
                    RecordingOfflineAnalytics(),
                    NoOpSessionFunnelAnalytics(),
                    FakeConfig(true),
                )

            // init resets the coordinator to Idle, so Ready must be established AFTER construction —
            // otherwise the ctor's reset() wipes the sticky Ready this test relies on.
            coordinator.start("easy", "t", 5, firstSession = true)
            runCurrent()
            stream.push(DialogueEvent.Start(sessionId = "s1", remaining = 3))
            stream.push(DialogueEvent.Turn(DialogueTurn(ko = "안녕", en = "Hello there", role = "model")))
            runCurrent()
            assertTrue(coordinator.state.value is DialogueGenState.Ready)
            assertFalse(vm.firstLineReady.value)

            vm.prepareFirstLine()
            advanceUntilIdle()

            assertEquals(1, ttsApi.callCount)
            assertEquals("Hello there", ttsApi.lastText) // the first opponent (model, index 0) line
            assertTrue(vm.firstLineReady.value) // loading gate may now release
        }

    @Test
    fun `prepareFirstLine marks ready without synthesis in DEVICE quality`() =
        runTest {
            val stream = FakeStream()
            val scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val coordinator = DialogueGenerationCoordinator(stream, scope, FakeConnectivity(offline = false))
            val ttsApi = CountingTtsApi()
            val tts =
                TtsPlaybackCoordinator(
                    ttsApi,
                    NoopPcmPlayer(),
                    NoopDeviceTts(),
                    ServerTtsSettings(TtsQuality.DEVICE),
                    scope,
                )

            val vm =
                DialogueGenerationViewModel(
                    coordinator,
                    tts,
                    bank,
                    FakeLoadingMessageSource(),
                    RecordingAnalytics(),
                    RecordingLimitAnalytics(),
                    SessionSnapshotStore(inMemoryPrefsDataStore()),
                    scope,
                    RecordingOfflineAnalytics(),
                    NoOpSessionFunnelAnalytics(),
                    FakeConfig(true),
                )

            coordinator.start("easy", "t", 5, firstSession = true)
            runCurrent()
            stream.push(DialogueEvent.Start(sessionId = "s1", remaining = 3))
            stream.push(DialogueEvent.Turn(DialogueTurn(ko = "안녕", en = "Hello there", role = "model")))
            runCurrent()

            vm.prepareFirstLine()
            advanceUntilIdle()

            assertEquals(0, ttsApi.callCount) // DEVICE quality → nothing to warm
            assertTrue(vm.firstLineReady.value) // but the gate still releases immediately
        }

    @Suppress("LongParameterList") // 테스트 팩토리 — seam 별 fake 를 명시 주입한다(운영 코드 아님).
    private fun TestScope.viewModel(
        analytics: RecordingAnalytics,
        config: FakeConfig,
        stream: FakeStream = FakeStream(),
        limitAnalytics: RecordingLimitAnalytics = RecordingLimitAnalytics(),
        connectivity: ConnectivityObserver = FakeConnectivity(offline = false),
        offlineAnalytics: OfflineAnalytics = RecordingOfflineAnalytics(),
        loadingMessages: LoadingMessageSource = FakeLoadingMessageSource(),
    ): DialogueGenerationViewModel {
        val scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = DialogueGenerationCoordinator(stream, scope, connectivity)
        val snapshotStore = SessionSnapshotStore(inMemoryPrefsDataStore())
        val tts = TtsPlaybackCoordinator(CountingTtsApi(), NoopPcmPlayer(), NoopDeviceTts(), ServerTtsSettings(), scope)
        return DialogueGenerationViewModel(
            coordinator,
            tts,
            bank,
            loadingMessages,
            analytics,
            limitAnalytics,
            snapshotStore,
            scope,
            offlineAnalytics,
            NoOpSessionFunnelAnalytics(),
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

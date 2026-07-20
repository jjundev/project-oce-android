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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Local fake [WaitQuizAnalytics] that records shown/ended calls (unique name — file-private, no clash). */
private class RecordingWaitQuizForShownEnded : WaitQuizAnalytics {
    data class Call(
        val name: String,
        val sessionId: String? = null,
        val surface: String? = null,
        val reason: String? = null,
        val delayMs: Long? = null,
        val dwellMs: Long? = null,
        val cardsAnswered: Int? = null,
    )

    val calls = mutableListOf<Call>()

    override fun cardAnswered(
        sessionId: String?,
        cardId: String,
        choseCorrect: Boolean,
        cardIndex: Int,
    ) = Unit

    override fun waitQuizShown(
        sessionId: String?,
        surface: String,
        delayMsAtShow: Long,
    ) {
        calls += Call(name = "wait_quiz_shown", sessionId = sessionId, surface = surface, delayMs = delayMsAtShow)
    }

    override fun waitQuizEnded(
        sessionId: String?,
        surface: String,
        reason: String,
        cardsAnswered: Int,
        dwellMs: Long,
    ) {
        calls +=
            Call(
                name = "wait_quiz_ended",
                sessionId = sessionId,
                surface = surface,
                reason = reason,
                dwellMs = dwellMs,
                cardsAnswered = cardsAnswered,
            )
    }
}

private class ShownEndedFakeQuizBank : QuizBank {
    override fun forTier(tier: String): List<QuizItem> = emptyList()
}

private class ShownEndedFakeLimitAnalytics : LimitAnalytics {
    override fun limitReached(remaining: Int, surface: String) = Unit
}

private class ShownEndedFakeConfig(override val loadingQuizEnabled: Boolean = true) : LoadingQuizConfig

private class ShownEndedNoopLlmApi : LlmApi {
    override suspend fun tts(body: TtsRequest): TtsResponse =
        TtsResponse(pcmBase64 = "", sampleRate = 24000, mimeType = "audio/L16;rate=24000")

    override suspend fun speaking(body: SpeakingRequest): SpeakingResponse = error("unused")
}

private class ShownEndedNoopPcmPlayer : PcmPlayer {
    override suspend fun play(pcm: ByteArray, sampleRateHz: Int, speed: Float) = Unit
    override fun stop() = Unit
}

private class ShownEndedNoopDeviceTts : DeviceTts {
    override suspend fun speak(
        text: String,
        gender: String?,
        speechRate: Float,
        onStart: () -> Unit,
    ): DeviceTtsResult = DeviceTtsResult.COMPLETED

    override fun stop() = Unit
}

private class ShownEndedServerTtsSettings : TtsSettingsRepository {
    private val value = TtsSettings(quality = TtsQuality.SERVER)
    override val settings: Flow<TtsSettings> = flowOf(value)
    override suspend fun current(): TtsSettings = value
    override suspend fun setQuality(quality: TtsQuality) = Unit
    override suspend fun setSpeechRate(rate: Float) = Unit
    override suspend fun setMuted(muted: Boolean) = Unit
}

private class ShownEndedRecordingOfflineAnalytics : OfflineAnalytics {
    override fun connectivityChanged(online: Boolean) = Unit
    override fun offlineBlocked(surface: String) = Unit
}

private class ShownEndedFakeStream : DialogueStream {
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

@OptIn(ExperimentalCoroutinesApi::class)
class WaitQuizShownEndedEmitTest {
    @Test
    fun `revisit generation logs wait_quiz_shown once then wait_quiz_ended ready once`() =
        runTest {
            val quiz = RecordingWaitQuizForShownEnded()
            val stream = ShownEndedFakeStream()
            val vm = newGenerationViewModel(waitQuiz = quiz, stream = stream)

            vm.start(level = "normal", topic = "cafe", length = 10, firstSession = false, isOnboarding = false)
            runCurrent()
            stream.push(DialogueEvent.Start(sessionId = "s1", remaining = 3))
            runCurrent()

            vm.onQuizShown()
            vm.onQuizShown() // idempotent
            vm.onQuizEnded("ready")
            vm.onQuizEnded("ready") // idempotent

            assertEquals(listOf("wait_quiz_shown", "wait_quiz_ended"), quiz.calls.map { it.name })
            val shown = quiz.calls.first { it.name == "wait_quiz_shown" }
            assertEquals("s1", shown.sessionId)
            assertEquals("home", shown.surface)
            assertTrue((shown.delayMs ?: -1L) >= 0L)
            val ended = quiz.calls.first { it.name == "wait_quiz_ended" }
            assertEquals("s1", ended.sessionId)
            assertEquals("home", ended.surface)
            assertEquals("ready", ended.reason)
            assertEquals(0, ended.cardsAnswered)
            assertTrue((ended.dwellMs ?: -1L) >= 0L)
        }

    @Test
    fun `onQuizEnded without a prior onQuizShown fires nothing`() =
        runTest {
            val quiz = RecordingWaitQuizForShownEnded()
            val vm = newGenerationViewModel(waitQuiz = quiz)

            vm.start(level = "normal", topic = "cafe", length = 10, firstSession = false, isOnboarding = false)
            vm.onQuizEnded("failed")

            assertEquals(emptyList<String>(), quiz.calls.map { it.name })
        }

    private fun TestScope.newGenerationViewModel(
        waitQuiz: WaitQuizAnalytics,
        stream: ShownEndedFakeStream = ShownEndedFakeStream(),
        connectivity: ConnectivityObserver = FakeConnectivity(offline = false),
    ): DialogueGenerationViewModel {
        val scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = DialogueGenerationCoordinator(stream, scope, connectivity)
        val snapshotStore = SessionSnapshotStore(inMemoryPrefsDataStore())
        val tts =
            TtsPlaybackCoordinator(
                ShownEndedNoopLlmApi(),
                ShownEndedNoopPcmPlayer(),
                ShownEndedNoopDeviceTts(),
                ShownEndedServerTtsSettings(),
                scope,
            )
        return DialogueGenerationViewModel(
            coordinator,
            tts,
            ShownEndedFakeQuizBank(),
            waitQuiz,
            ShownEndedFakeLimitAnalytics(),
            snapshotStore,
            scope,
            ShownEndedRecordingOfflineAnalytics(),
            NoOpSessionFunnelAnalytics(),
            ShownEndedFakeConfig(),
        )
    }
}

/** In-memory [DataStore] (no file I/O) for the injected snapshotStore — tests don't assert on it directly. */
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

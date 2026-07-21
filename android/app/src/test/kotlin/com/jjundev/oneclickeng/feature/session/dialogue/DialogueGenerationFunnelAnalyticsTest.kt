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
import com.jjundev.oneclickeng.feature.session.analytics.RecordingSessionFunnelAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.SessionFunnelAnalytics
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FunnelFakeQuizBank : QuizBank {
    override fun forTier(tier: String): List<QuizItem> = emptyList()
}

private class FunnelRecordingWaitQuizAnalytics : WaitQuizAnalytics {
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
    ) = Unit

    override fun waitQuizEnded(
        sessionId: String?,
        surface: String,
        reason: String,
        cardsAnswered: Int,
        dwellMs: Long,
    ) = Unit
}

private class FunnelRecordingLimitAnalytics : LimitAnalytics {
    override fun limitReached(
        remaining: Int,
        surface: String,
    ) = Unit
}

private class FunnelFakeConfig(override val loadingQuizEnabled: Boolean = true) : LoadingQuizConfig

private class FunnelNoopLlmApi : LlmApi {
    override suspend fun tts(body: TtsRequest): TtsResponse =
        TtsResponse(pcmBase64 = "", sampleRate = 24000, mimeType = "audio/L16;rate=24000")

    override suspend fun speaking(body: SpeakingRequest): SpeakingResponse = error("unused")
}

private class FunnelNoopPcmPlayer : PcmPlayer {
    override suspend fun play(
        pcm: ByteArray,
        sampleRateHz: Int,
        speed: Float,
    ) = Unit

    override fun stop() = Unit
}

private class FunnelNoopDeviceTts : DeviceTts {
    override suspend fun speak(
        text: String,
        gender: String?,
        speechRate: Float,
        onStart: () -> Unit,
    ): DeviceTtsResult = DeviceTtsResult.COMPLETED

    override fun stop() = Unit
}

private class FunnelServerTtsSettings : TtsSettingsRepository {
    private val value = TtsSettings(quality = TtsQuality.SERVER)
    override val settings: Flow<TtsSettings> = flowOf(value)

    override suspend fun current(): TtsSettings = value

    override suspend fun setQuality(quality: TtsQuality) = Unit

    override suspend fun setSpeechRate(rate: Float) = Unit

    override suspend fun setMuted(muted: Boolean) = Unit
}

private class FunnelRecordingOfflineAnalytics : OfflineAnalytics {
    override fun connectivityChanged(online: Boolean) = Unit

    override fun offlineBlocked(surface: String) = Unit
}

private class FunnelFakeStream : DialogueStream {
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
class DialogueGenerationFunnelAnalyticsTest {
    @Test
    fun `onboarding generation logs first_session_generation_started when started`() =
        runTest {
            val funnel = RecordingSessionFunnelAnalytics()
            val vm = viewModel(sessionFunnel = funnel)

            vm.start(level = "easy", topic = "cafe", length = 5, firstSession = true, isOnboarding = true)

            assertEquals(
                RecordingSessionFunnelAnalytics.Call(
                    "first_session_generation_started",
                    mapOf("idempotency_key_present" to true),
                ),
                funnel.calls.first(),
            )
        }

    @Test
    fun `onConversationStarted logs first_session_started once for onboarding`() =
        runTest {
            val funnel = RecordingSessionFunnelAnalytics()
            val stream = FunnelFakeStream()
            val vm = viewModel(sessionFunnel = funnel, stream = stream)

            vm.start(level = "easy", topic = "cafe", length = 5, firstSession = true, isOnboarding = true)
            runCurrent()
            stream.push(DialogueEvent.Start(sessionId = "s1", remaining = 3))
            runCurrent()

            vm.onConversationStarted()
            vm.onConversationStarted() // idempotent

            val started = funnel.calls.filter { it.name == "first_session_started" }
            assertEquals(1, started.size)
            assertEquals(
                mapOf("session_id" to "s1", "topic_id" to "cafe", "length" to 5, "difficulty" to "easy"),
                started.single().args,
            )
        }

    @Test
    fun `onConversationStarted logs learning_session_started for a revisit`() =
        runTest {
            val funnel = RecordingSessionFunnelAnalytics()
            val stream = FunnelFakeStream()
            val vm = viewModel(sessionFunnel = funnel, stream = stream)

            vm.start(level = "hard", topic = "gym", length = 8, firstSession = false, isOnboarding = false)
            runCurrent()
            stream.push(DialogueEvent.Start(sessionId = "s2", remaining = 3))
            runCurrent()

            vm.onConversationStarted()

            val started = funnel.calls.filter { it.name == "learning_session_started" }
            assertEquals(1, started.size)
            assertEquals(
                mapOf("session_id" to "s2", "topic_id" to "gym", "length" to 8, "level" to "hard"),
                started.single().args,
            )
        }

    private fun TestScope.viewModel(
        sessionFunnel: SessionFunnelAnalytics = NoOpSessionFunnelAnalytics(),
        stream: FunnelFakeStream = FunnelFakeStream(),
        connectivity: ConnectivityObserver = FakeConnectivity(offline = false),
    ): DialogueGenerationViewModel {
        val scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = DialogueGenerationCoordinator(stream, scope, connectivity)
        val snapshotStore = SessionSnapshotStore(inMemoryPrefsDataStore())
        val tts =
            TtsPlaybackCoordinator(
                FunnelNoopLlmApi(),
                FunnelNoopPcmPlayer(),
                FunnelNoopDeviceTts(),
                FunnelServerTtsSettings(),
                scope,
            )
        return DialogueGenerationViewModel(
            coordinator,
            tts,
            FunnelFakeQuizBank(),
            object : LoadingMessageSource {
                override fun forSession(isOnboarding: Boolean): String = "test-loading-copy"
            },
            FunnelRecordingWaitQuizAnalytics(),
            FunnelRecordingLimitAnalytics(),
            snapshotStore,
            scope,
            FunnelRecordingOfflineAnalytics(),
            sessionFunnel,
            FunnelFakeConfig(),
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

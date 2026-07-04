package com.jjundev.oneclickeng.feature.session.turn

import android.os.Bundle
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.os.bundleOf
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jjundev.oneclickeng.core.audio.AudioCaptureException
import com.jjundev.oneclickeng.core.audio.RecordingController
import com.jjundev.oneclickeng.core.audio.RecordingResult
import com.jjundev.oneclickeng.core.network.DialogueTurn as NetworkDialogueTurn
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenState
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenerationCoordinator
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueStreamStatus
import com.jjundev.oneclickeng.feature.session.feedback.SlimFeedbackCoordinator
import com.jjundev.oneclickeng.feature.session.speaking.SpeakingAnalysisCoordinator
import com.jjundev.oneclickeng.feature.session.speaking.SpeakingAnalysisState
import com.jjundev.oneclickeng.ui.audio.MicState
import com.jjundev.oneclickeng.ui.audio.MicTransientReason
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Generated dialogue route (M1-01 대본 → M1-08 마이크 루프). 라이브 SSE 누적을 M1-03 턴 렌더러에 얹고,
 * 96dp 4상태 마이크 도크(I1) + 채팅 대체 입력(FR-9)을 배선한다. 턴 진행·마이크 상태는 [GeneratedDialogueSessionViewModel]
 * 이 소유해 회전/프로세스킬을 `SavedStateHandle` 로 견딘다(FR-13).
 */
@Composable
fun GeneratedDialogueSessionRoute(
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotion(),
    onViewSummary: () -> Unit = {},
    viewModel: GeneratedDialogueSessionViewModel = hiltViewModel(),
) {
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val state = viewModel.turnState

    // 코디네이터 라이브 상태를 턴머신에 흘려보낸다. 프로세스킬 복원 시 코디네이터는 Idle(비Ready)라 accept 는
    // no-op 이고, 시드된 스냅샷이 정본으로 남는다(결정 #4).
    LaunchedEffect(generationState) { viewModel.onGenerationState(generationState) }

    // 상대역 자동 진행 지연(M1-03 유지). reduce-motion 이면 즉시.
    val effectiveDelay = if (reduceMotion) 0L else DEFAULT_OPPONENT_ADVANCE_DELAY_MS.toLong()
    LaunchedEffect(state.opponentTurnSerial) {
        if (state.turnPhase == TurnPhase.OpponentTurn && state.sessionPhase == SessionPhase.InTurn) {
            delay(effectiveDelay)
            state.completeOpponentTurn()
        }
    }

    GeneratedDialogueSessionContent(
        state = state,
        onViewSummary = onViewSummary,
        modifier = modifier,
        dock = { task ->
            MicSessionDock(
                task = task,
                viewModel = viewModel,
                reduceMotion = reduceMotion,
            )
        },
    )
}

/**
 * M1-08 세션 턴 루프 + 마이크 4상태의 상태 소유자. 기존 얇은 forwarder 를 확장해 [GeneratedDialogueState] 를
 * 직접 소유하고, 정착 [MicState] 루프(녹음→분석→완료)를 Singleton 코디네이터에 배선하며, 매 저장 시점
 * ([SavedStateHandle.setSavedStateProvider])에 [SessionTurnSnapshot] 을 lazy 직렬화한다(매-변이 쓰기 아님).
 *
 * MicState 는 정착 축만 필드로 들고([micState]), 과도 사유([transientReason])는 보존 대상이 아니다(§6.1).
 * 프로세스킬 복원 시 정착 Recording/Analyzing 은 [MicState.Ready] 로 강등된다 — fresh Singleton 이라 stale
 * 콜백이 없고(orphan 없음), 사용자에겐 재시도 힌트로 고지한다(무증명 아님, 결정 #12b).
 */
// 마이크 4상태 루프 + 텍스트 대체 + SavedState 결선이라 작은 전이 헬퍼가 많다(SlimFeedbackCoordinator 선례).
@Suppress("TooManyFunctions")
@HiltViewModel
class GeneratedDialogueSessionViewModel
    @Inject
    constructor(
        private val generation: DialogueGenerationCoordinator,
        private val recording: RecordingController,
        private val speaking: SpeakingAnalysisCoordinator,
        private val feedback: SlimFeedbackCoordinator,
        private val appScope: CoroutineScope,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val generationState = generation.state

        // 내부 턴머신 타입이라 internal(같은 모듈 Route/테스트만 접근). public 노출 금지.
        internal val turnState = GeneratedDialogueState()

        /** 실시간 파형(Recording 시 도크가 소비). */
        val waveform = recording.waveform

        var micState by mutableStateOf(MicState.Ready)
            private set

        var transientReason by mutableStateOf<MicTransientReason?>(null)
            private set

        /** TooQuiet/Empty/Failed 또는 프로세스킬 강등 시의 UI-local 힌트(정착 상태는 Ready). */
        var retryHint by mutableStateOf<String?>(null)
            private set

        var textMode by mutableStateOf(false)
            private set

        var textValue by mutableStateOf("")
            private set

        // L2 원본 버퍼-of-record. 정상 운영 시 코디네이터 Ready.turns 에서 갱신, 복원 시 스냅샷에서 seed.
        private var latestTurns: List<NetworkDialogueTurn> = emptyList()

        private val json = Json { ignoreUnknownKeys = true }

        init {
            val restored =
                savedStateHandle.get<Bundle>(PROVIDER_KEY)
                    ?.getString(BUNDLE_JSON)
                    ?.let { runCatching { json.decodeFromString<SessionTurnSnapshot>(it) }.getOrNull() }
                    ?.takeIf { it.schemaVersion == SessionTurnSnapshot.SCHEMA_VERSION }
            if (restored != null) {
                turnState.restoreFrom(restored)
                latestTurns = restored.turns.map { it.toDomain() }
                val settled = micStateFromName(restored.micState)
                // 진행 중 캡처/분석은 프로세스킬로 소멸 → Ready 강등 + 재시도 고지.
                if (settled == MicState.Recording || settled == MicState.Analyzing) {
                    micState = MicState.Ready
                    retryHint = HINT_RETRY
                } else {
                    micState = settled
                }
            }
            // onGenerationState 는 Route 의 collectAsStateWithLifecycle 가 구동한다(중복 collect 회피).
            viewModelScope.launch { speaking.state.collect(::onAnalysisState) }
            savedStateHandle.setSavedStateProvider(PROVIDER_KEY) {
                bundleOf(BUNDLE_JSON to json.encodeToString(turnState.toSnapshot(micState, latestTurns)))
            }
        }

        /** 코디네이터 상태를 턴머신에 반영(Route 가 라이프사이클 인지 collect 로 호출). */
        fun onGenerationState(state: DialogueGenState) {
            if (state is DialogueGenState.Ready) latestTurns = state.turns
            turnState.accept(state)
        }

        /** 도크 마이크 탭. 정착 상태별 분기(결정 #12). */
        fun onMicTap() {
            when (micState) {
                MicState.Ready -> startRecording()
                MicState.Recording -> stopRecording()
                MicState.Analyzing, MicState.Complete -> Unit // enabled=false 로도 막히나 방어적 no-op
            }
        }

        // AudioCaptureException 은 UI 힌트로만 흡수한다(원인 로깅은 오디오 레이어 소관).
        @Suppress("SwallowedException")
        private fun startRecording() {
            if (transientReason != null) return // recorderStarting 창 재탭 차단
            retryHint = null
            transientReason = MicTransientReason.RecorderStarting
            viewModelScope.launch {
                try {
                    recording.start()
                    micState = MicState.Recording
                } catch (e: AudioCaptureException) {
                    micState = MicState.Ready
                    retryHint = HINT_ERROR
                } finally {
                    transientReason = null
                }
            }
        }

        private fun stopRecording() {
            viewModelScope.launch {
                when (val result = recording.stop()) {
                    is RecordingResult.Captured -> {
                        val sid = generation.sessionId()
                        if (sid != null) {
                            micState = MicState.Analyzing
                            speaking.analyze(result, sid)
                        } else {
                            micState = MicState.Ready
                            retryHint = HINT_ERROR
                        }
                    }
                    RecordingResult.TooQuiet -> {
                        micState = MicState.Ready
                        retryHint = HINT_RETRY
                    }
                    is RecordingResult.Failed -> {
                        micState = MicState.Ready
                        retryHint = HINT_ERROR
                    }
                }
            }
        }

        // 우리 분석(micState=Analyzing)에만 반응 — Singleton 의 이전 세션 잔여 상태 오반응 차단.
        private fun onAnalysisState(state: SpeakingAnalysisState) {
            if (micState != MicState.Analyzing) return
            when (state) {
                is SpeakingAnalysisState.Result -> {
                    turnState.appendLearnerAnswer(state.transcript)
                    micState = MicState.Complete
                    triggerFeedback(state.transcript)
                }
                SpeakingAnalysisState.Empty -> {
                    micState = MicState.Ready
                    retryHint = HINT_RETRY
                }
                SpeakingAnalysisState.Failed -> {
                    micState = MicState.Ready
                    retryHint = HINT_ERROR
                }
                SpeakingAnalysisState.Analyzing, SpeakingAnalysisState.Idle -> Unit
            }
        }

        /** Complete 에서 다음 턴으로 전진(피드백 settled 게이트는 M1-07 시트 소관, 여기선 도크 "다음"). */
        fun onAdvance() {
            if (micState != MicState.Complete) return
            turnState.advanceTurn()
            micState = MicState.Ready
            retryHint = null
            speaking.reset()
            feedback.reset()
        }

        fun onToggleTextMode(on: Boolean) {
            textMode = on
            if (!on) textValue = ""
        }

        fun onTextChange(value: String) {
            textValue = value
        }

        /** FR-9 텍스트 제출 — 녹음·분석 우회, 같은 피드백 파이프라인으로 합류. */
        fun onSubmitText() {
            val text = textValue.trim()
            if (text.isEmpty() || micState == MicState.Analyzing) return
            turnState.appendLearnerAnswer(text)
            micState = MicState.Complete
            textMode = false
            textValue = ""
            retryHint = null
            triggerFeedback(text)
        }

        private fun triggerFeedback(userEnglish: String) {
            val sid = generation.sessionId() ?: return
            val level = generation.level() ?: return
            val task = turnState.currentTask?.koreanPrompt
            val ref = turnState.currentReferenceEnglish()
            if (task != null && ref != null) {
                feedback.start(sid, task, userEnglish, ref, level)
            }
        }

        override fun onCleared() {
            // 진행 중 캡처/분석을 화면 이탈 시 취소(결정 #13b). appScope 는 VM scope 소멸과 무관.
            appScope.launch { runCatching { recording.stop() } }
            speaking.reset()
        }

        private companion object {
            const val PROVIDER_KEY = "session_turn"
            const val BUNDLE_JSON = "json"
            const val HINT_RETRY = "다시 말해볼까요"
            const val HINT_ERROR = "문제가 생겼어요. 다시 시도해 주세요."
        }
    }

@Composable
internal fun GeneratedDialogueSessionContent(
    state: GeneratedDialogueState,
    onViewSummary: () -> Unit,
    modifier: Modifier = Modifier,
    dock: (@Composable (ScaffoldTask) -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }
    DialogueTurnContent(
        messages = state.messages,
        turnPhase = state.turnPhase,
        sessionPhase = state.sessionPhase,
        currentTask = state.currentTask,
        listState = listState,
        onSubmitStub = state::submitLearnerStub,
        onViewSummary = onViewSummary,
        modifier = modifier,
        dock = dock,
    )
}

// 턴머신 전이 헬퍼 + M1-08 SavedState export/seed 로 메서드가 많다(상태 머신 클래스, 의도적).
@Suppress("TooManyFunctions")
@Stable
internal class GeneratedDialogueState {
    var messages by mutableStateOf<List<DialogueMessage>>(emptyList())
        private set

    var turnPhase by mutableStateOf(TurnPhase.OpponentTurn)
        private set

    var sessionPhase by mutableStateOf(SessionPhase.InTurn)
        private set

    var currentTask by mutableStateOf<ScaffoldTask?>(null)
        private set

    var diagnostic by mutableStateOf<String?>(null)
        private set

    var opponentTurnSerial by mutableIntStateOf(0)
        private set

    private var consumedTurnCount = 0
    private var streamStatus = DialogueStreamStatus.Streaming
    private var pending = PendingOpponent()
    private val bufferedPending = ArrayDeque<PendingOpponent>()

    fun accept(state: DialogueGenState) {
        if (state !is DialogueGenState.Ready) return
        if (state.turns.size < consumedTurnCount) reset()
        streamStatus = state.streamStatus
        state.turns.drop(consumedTurnCount).forEachIndexed { offset, turn ->
            val index = consumedTurnCount + offset
            consume(index, turn)
        }
        consumedTurnCount = state.turns.size
        settleTerminalStatus()
    }

    fun completeOpponentTurn() {
        if (turnPhase != TurnPhase.OpponentTurn || sessionPhase != SessionPhase.InTurn) return
        val current = pending
        if (current.opponentEnglish == null) return
        current.opponentComplete = true
        when {
            current.task != null -> enterLearnerTurn(current)
            streamStatus == DialogueStreamStatus.Done -> sessionPhase = SessionPhase.Completed
            else -> Unit
        }
    }

    /** 학습자 턴 전진 스텁(임시, M1-03 스텁 라우트 전용). 목표 문장을 재생해 다음 턴으로. */
    fun submitLearnerStub() {
        if (turnPhase != TurnPhase.LearnerTurn) return
        pending.referenceEnglish?.let { messages = messages + DialogueMessage.Learner(it) }
        advanceTurn()
    }

    /**
     * 실 학습자 답변(전사/텍스트)을 학습자 말풍선으로 append 한다(M1-08). currentTask 는 유지(리캡) — 전진은
     * [advanceTurn] 이 담당한다(Complete → 피드백 → "다음").
     */
    fun appendLearnerAnswer(userEnglish: String) {
        if (turnPhase != TurnPhase.LearnerTurn) return
        messages = messages + DialogueMessage.Learner(userEnglish)
    }

    /** 현재 학습자 턴의 목표 영어(피드백 referenceEnglish 재사용). */
    fun currentReferenceEnglish(): String? = pending.referenceEnglish

    /** 학습자 턴을 마감하고 다음 상대역 턴/완료로 전진한다(스텁·실답변 공용 tail). */
    fun advanceTurn() {
        if (turnPhase != TurnPhase.LearnerTurn) return
        currentTask = null
        if (promoteBufferedOpponent()) return
        pending = PendingOpponent()
        turnPhase = TurnPhase.OpponentTurn
        sessionPhase =
            if (streamStatus == DialogueStreamStatus.Done) {
                SessionPhase.Completed
            } else {
                SessionPhase.AwaitingStreamDone
            }
    }

    private fun consume(
        index: Int,
        turn: NetworkDialogueTurn,
    ) {
        val expectedRole = if (index % 2 == 0) ROLE_MODEL else ROLE_USER
        if (turn.role != expectedRole) {
            diagnostic = "unexpected_role:$index:${turn.role}"
            return
        }
        if (turn.role == ROLE_MODEL) {
            val next = PendingOpponent(opponentEnglish = turn.en)
            if (pending.opponentEnglish == null) {
                displayOpponent(next)
            } else {
                bufferedPending.addLast(next)
            }
        } else {
            attachUserTarget(index, turn)
        }
    }

    private fun displayOpponent(next: PendingOpponent) {
        val english = next.opponentEnglish ?: return
        pending = next
        messages = messages + DialogueMessage.Opponent(english)
        currentTask = null
        turnPhase = TurnPhase.OpponentTurn
        sessionPhase = SessionPhase.InTurn
        opponentTurnSerial += 1
    }

    private fun attachUserTarget(
        index: Int,
        turn: NetworkDialogueTurn,
    ) {
        val target =
            bufferedPending.lastOrNull { it.task == null }
                ?: pending.takeIf { it.opponentEnglish != null && it.task == null }
        if (target == null) {
            diagnostic = "unexpected_user_without_model:$index"
            return
        }
        target.task = ScaffoldTask(turn.ko)
        target.referenceEnglish = turn.en
        if (target === pending && target.opponentComplete) enterLearnerTurn(target)
    }

    private fun promoteBufferedOpponent(): Boolean {
        val next = bufferedPending.removeFirstOrNull() ?: return false
        displayOpponent(next)
        return true
    }

    private fun enterLearnerTurn(current: PendingOpponent) {
        currentTask = current.task
        turnPhase = TurnPhase.LearnerTurn
        sessionPhase = SessionPhase.InTurn
    }

    private fun settleTerminalStatus() {
        when {
            streamStatus == DialogueStreamStatus.FailedAfterReady ->
                diagnostic = "stream_failed_after_ready"
            sessionPhase == SessionPhase.AwaitingStreamDone && streamStatus == DialogueStreamStatus.Done ->
                sessionPhase = SessionPhase.Completed
            turnPhase == TurnPhase.OpponentTurn &&
                pending.opponentEnglish != null &&
                pending.task == null &&
                pending.opponentComplete &&
                streamStatus == DialogueStreamStatus.Done ->
                sessionPhase = SessionPhase.Completed
        }
    }

    private fun reset() {
        messages = emptyList()
        turnPhase = TurnPhase.OpponentTurn
        sessionPhase = SessionPhase.InTurn
        currentTask = null
        diagnostic = null
        opponentTurnSerial = 0
        consumedTurnCount = 0
        streamStatus = DialogueStreamStatus.Streaming
        pending = PendingOpponent()
        bufferedPending.clear()
    }

    // --- M1-08 SavedState (L1 파생 상태 export/seed, replay 없음) ---

    /** 현 상태 + 앰비언트 micState/turns 를 [SessionTurnSnapshot] 으로 직렬화(L1+L2). */
    fun toSnapshot(
        micState: MicState,
        turns: List<NetworkDialogueTurn>,
    ): SessionTurnSnapshot =
        SessionTurnSnapshot(
            messages = messages.map { MessageData(it is DialogueMessage.Learner, it.english) },
            turnPhase = turnPhase.name,
            sessionPhase = sessionPhase.name,
            currentTaskKo = currentTask?.koreanPrompt,
            consumedTurnCount = consumedTurnCount,
            opponentTurnSerial = opponentTurnSerial,
            pending = pending.toData(),
            bufferedPending = bufferedPending.map { it.toData() },
            streamStatus = streamStatus.name,
            diagnostic = diagnostic,
            micState = micState.name,
            turns = turns.map { it.toData() },
        )

    /** L1 필드를 replay 없이 그대로 seed 한다(프로세스킬 복원). */
    fun restoreFrom(snapshot: SessionTurnSnapshot) {
        messages =
            snapshot.messages.map {
                if (it.isLearner) DialogueMessage.Learner(it.english) else DialogueMessage.Opponent(it.english)
            }
        turnPhase = runCatching { TurnPhase.valueOf(snapshot.turnPhase) }.getOrDefault(TurnPhase.OpponentTurn)
        sessionPhase = runCatching { SessionPhase.valueOf(snapshot.sessionPhase) }.getOrDefault(SessionPhase.InTurn)
        currentTask = snapshot.currentTaskKo?.let { ScaffoldTask(it) }
        consumedTurnCount = snapshot.consumedTurnCount
        opponentTurnSerial = snapshot.opponentTurnSerial
        pending = snapshot.pending.toPending()
        bufferedPending.clear()
        bufferedPending.addAll(snapshot.bufferedPending.map { it.toPending() })
        streamStatus =
            runCatching { DialogueStreamStatus.valueOf(snapshot.streamStatus) }
                .getOrDefault(DialogueStreamStatus.Streaming)
        diagnostic = snapshot.diagnostic
    }

    private fun PendingOpponent.toData(): PendingData =
        PendingData(
            opponentEnglish = opponentEnglish,
            taskKo = task?.koreanPrompt,
            referenceEnglish = referenceEnglish,
            opponentComplete = opponentComplete,
        )

    private fun PendingData.toPending(): PendingOpponent =
        PendingOpponent(
            opponentEnglish = opponentEnglish,
            task = taskKo?.let { ScaffoldTask(it) },
            referenceEnglish = referenceEnglish,
            opponentComplete = opponentComplete,
        )

    private data class PendingOpponent(
        var opponentEnglish: String? = null,
        var task: ScaffoldTask? = null,
        var referenceEnglish: String? = null,
        var opponentComplete: Boolean = false,
    )

    private companion object {
        const val ROLE_MODEL = "model"
        const val ROLE_USER = "user"
    }
}

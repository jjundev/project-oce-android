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
import com.jjundev.oneclickeng.feature.session.feedback.SectionState
import com.jjundev.oneclickeng.feature.session.feedback.SlimFeedbackCoordinator
import com.jjundev.oneclickeng.feature.session.feedback.SlimFeedbackState
import com.jjundev.oneclickeng.feature.session.resume.SessionSnapshotStore
import com.jjundev.oneclickeng.feature.session.speaking.SpeakingAnalysisCoordinator
import com.jjundev.oneclickeng.feature.session.speaking.SpeakingAnalysisState
import com.jjundev.oneclickeng.feature.session.summary.SessionTurnBufferStore
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
    onViewSummary: (sessionId: String) -> Unit = {},
    // 세션 정체성 헤더(주제 이모지·제목·레벨·진행 점) 재료. 시작 플로우(홈/온보딩)만 실값을 싣고,
    // 이어하기·프로세스킬 복원 재진입은 빈 값이라 아래에서 header=null 로 폴백한다(스냅샷 스키마 불변, 회귀 0).
    topicEmoji: String = "",
    topicTitle: String = "",
    level: String = "",
    totalTurns: Int = 5,
    viewModel: GeneratedDialogueSessionViewModel = hiltViewModel(),
) {
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val state = viewModel.turnState

    // 시작 플로우가 주제 제목을 실어왔을 때만 세션 헤더를 렌더한다. 이어하기/복원 재진입은 주제 메타가
    // 없어(빈 문자열) header=null → 헤더 미표시(M1-03 기존 동작 유지). 진행 점은 학습자 말풍선 수로 채운다.
    val header =
        if (topicTitle.isNotBlank()) {
            DialogueHeaderState(
                topicEmoji = topicEmoji,
                title = topicTitle,
                levelLabel = dialogueLevelLabel(level, totalTurns),
                totalTurns = totalTurns,
                completedTurns = state.messages.count { it is DialogueMessage.Learner },
            )
        } else {
            null
        }

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
        // 완료 CTA 탭 시점의 sessionId 를 상위 요약 라우팅에 전달(M3-02 대화→요약 배선). 완주 후에만
        // 도달하므로 sessionId 는 non-null 이나 방어적으로 orEmpty.
        onViewSummary = { onViewSummary(viewModel.sessionId().orEmpty()) },
        modifier = modifier,
        header = header,
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
// LongParameterList: 세션 루프 DI 허브(생성·녹음·발화·피드백 코디네이터 + durable 스냅샷 + SavedState).
@Suppress("TooManyFunctions", "LongParameterList")
@HiltViewModel
class GeneratedDialogueSessionViewModel
    @Inject
    constructor(
        private val generation: DialogueGenerationCoordinator,
        private val recording: RecordingController,
        private val speaking: SpeakingAnalysisCoordinator,
        private val feedback: SlimFeedbackCoordinator,
        private val turnBuffer: SessionTurnBufferStore,
        private val appScope: CoroutineScope,
        private val snapshotStore: SessionSnapshotStore,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val generationState = generation.state

        /** 서버 발급 sessionId(요약 라우팅용, M3-02). 대본 미도착이면 null → 요약 진입은 완주 후에만 일어나 non-null. */
        fun sessionId(): String? = generation.sessionId()

        // 크로스-프로세스 durable 복원 시 코디네이터는 Idle 이라 sessionId/level 을 잃는다. 스냅샷에서 복원한
        // 값을 폴백으로 들어 피드백/발화분석이 계속 동작하게 한다(M3-08 §2.5 내구 복귀).
        private var restoredSessionId: String? = null
        private var restoredLevel: String? = null

        private fun currentSessionId(): String? = generation.sessionId() ?: restoredSessionId

        private fun currentLevel(): String? = generation.level() ?: restoredLevel

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

        // 아직 요약 버퍼에 기록되지 않은 현재 턴의 과제·답변 echo. 피드백 정착([onFeedbackState]) 또는
        // "다음"([onAdvance]) 중 먼저 오는 쪽이 기록하고 null 로 비워 턴당 1회 기록을 보장한다.
        private var pendingTurn: PendingTurn? = null

        private val json = Json { ignoreUnknownKeys = true }

        init {
            val restored =
                savedStateHandle.get<Bundle>(PROVIDER_KEY)
                    ?.getString(BUNDLE_JSON)
                    ?.let { runCatching { json.decodeFromString<SessionTurnSnapshot>(it) }.getOrNull() }
                    ?.takeIf { it.schemaVersion == SessionTurnSnapshot.SCHEMA_VERSION }
            if (restored != null) {
                seedFrom(restored)
            } else if (generation.state.value !is DialogueGenState.Ready) {
                // SavedStateHandle 에 화면-체류 스냅샷이 없다 = 새 NavBackStackEntry(홈-복귀 재진입 등).
                // durable 스냅샷(§2.5)은 라이브 코디네이터가 세션을 들고 있지 **않을 때**(크로스-프로세스,
                // 코디네이터 non-Ready)만 정본이다. 같은 프로세스에서 코디네이터가 이미 Ready 면 Route 의
                // onGenerationState→accept() 가 정본이므로 durable read 를 아예 시작하지 않는다 — 이 동기
                // 분기로 async read 와 accept() 사이 순서 경쟁을 제거한다(둘 중 무엇이 정본인지 결정적).
                // messages.isEmpty() 는 그래도 남겨 belt-and-suspenders 로 이중 seed 를 막는다.
                viewModelScope.launch {
                    val durable = snapshotStore.read()
                    if (durable != null && turnState.messages.isEmpty()) seedFrom(durable)
                }
            }
            // onGenerationState 는 Route 의 collectAsStateWithLifecycle 가 구동한다(중복 collect 회피).
            viewModelScope.launch { speaking.state.collect(::onAnalysisState) }
            // 슬림 피드백이 정착(3섹션 모두 Loading 종료)하면 그 턴을 요약 버퍼에 기록한다(M2-02 handoff).
            // 시트(M1-07)가 아직 라이브에 없어 사용자는 정착 전에 "다음"을 누를 수 있으므로, 백그라운드
            // 정착 기록 + [onAdvance] best-effort 폴백의 이중 경로로 턴당 1회 기록을 보장한다(pendingTurn 가드).
            viewModelScope.launch { feedback.state.collect(::onFeedbackState) }
            savedStateHandle.setSavedStateProvider(PROVIDER_KEY) {
                bundleOf(BUNDLE_JSON to json.encodeToString(currentSnapshot()))
            }
        }

        /** L1 파생 상태 + 앰비언트 + 세션 식별을 스냅샷에서 복원한다(SavedStateHandle·durable 공용). */
        private fun seedFrom(snapshot: SessionTurnSnapshot) {
            turnState.restoreFrom(snapshot)
            latestTurns = snapshot.turns.map { it.toDomain() }
            restoredSessionId = snapshot.sessionId
            restoredLevel = snapshot.level
            val settled = micStateFromName(snapshot.micState)
            // 진행 중 캡처/분석은 프로세스킬/이탈로 소멸 → Ready 강등 + 재시도 고지.
            if (settled == MicState.Recording || settled == MicState.Analyzing) {
                micState = MicState.Ready
                retryHint = HINT_RETRY
            } else {
                micState = settled
            }
        }

        private fun currentSnapshot(): SessionTurnSnapshot =
            turnState.toSnapshot(micState, latestTurns, currentSessionId(), currentLevel())

        /**
         * durable 스냅샷 저장/폐기(§2.5). 완주(Completed)면 폐기(미완 아님 → 복귀 후보 아님), 아니면 현 상태를
         * 영속화한다. appScope 로 실행해 화면 이탈(onCleared)로 취소되지 않게 한다.
         */
        private fun persistResume() {
            val snapshot = currentSnapshot()
            val completed = turnState.sessionPhase == SessionPhase.Completed
            appScope.launch {
                if (completed) snapshotStore.clear() else snapshotStore.write(snapshot)
            }
        }

        /** 코디네이터 상태를 턴머신에 반영(Route 가 라이프사이클 인지 collect 로 호출). */
        fun onGenerationState(state: DialogueGenState) {
            if (state is DialogueGenState.Ready) latestTurns = state.turns
            turnState.accept(state)
            persistResume()
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
                        val sid = currentSessionId()
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
                    persistResume()
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
            // 정착 전에 "다음"을 누른 경우 최선 스냅샷으로 이 턴을 기록한 뒤 전진한다(feedback.reset 이
            // 진행 중 SSE 를 취소하므로 리셋 전에 기록해야 점수 유실을 막는다). 이미 정착 기록됐으면 no-op.
            pendingTurn?.let { recordTurn(it) }
            turnState.advanceTurn()
            micState = MicState.Ready
            retryHint = null
            speaking.reset()
            feedback.reset()
            persistResume()
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
            persistResume()
        }

        private fun triggerFeedback(userEnglish: String) {
            val sid = currentSessionId() ?: return
            val level = currentLevel() ?: return
            val task = turnState.currentTask?.koreanPrompt
            val ref = turnState.currentReferenceEnglish()
            if (task != null && ref != null) {
                // 세션 버퍼 시작(멱등, 첫 턴에만 실효) — 요약 점수·하이라이트·studytime 의 단일 소스.
                turnBuffer.startSession(sid)
                pendingTurn = PendingTurn(koreanPrompt = task, userText = userEnglish)
                feedback.start(sid, task, userEnglish, ref, level)
            }
        }

        /**
         * 피드백 상태 정착 시 현재 턴을 요약 버퍼에 기록한다. 정착 = 3섹션 모두 Loading 종료(Ready/Failed/
         * Skipped) 또는 캡 거부([QuotaBlocked]). 실패 섹션은 스냅샷에서 해당 키가 null 로 빠져 요약이 낮은
         * 신뢰도로 처리한다(§9.1). pendingTurn 가드로 턴당 1회만 기록한다.
         */
        private fun onFeedbackState(state: SlimFeedbackState) {
            val pending = pendingTurn ?: return
            val resolved =
                when (state) {
                    is SlimFeedbackState.Active ->
                        state.writingScore !is SectionState.Loading &&
                            state.grammar !is SectionState.Loading &&
                            state.natural !is SectionState.Loading
                    is SlimFeedbackState.QuotaBlocked -> true
                    SlimFeedbackState.Idle -> false
                }
            if (resolved) recordTurn(pending)
        }

        /** 요약 버퍼에 한 턴을 기록하고 pending 을 비운다(정착·"다음" 공용 진입). */
        private fun recordTurn(pending: PendingTurn) {
            turnBuffer.record(pending.koreanPrompt, pending.userText, feedback.bufferSnapshot())
            pendingTurn = null
        }

        override fun onCleared() {
            // 진행 중 캡처/분석을 화면 이탈 시 취소(결정 #13b). appScope 는 VM scope 소멸과 무관.
            appScope.launch { runCatching { recording.stop() } }
            speaking.reset()
        }

        /** 요약 버퍼 기록 대기 중인 턴의 과제·답변 echo(피드백 스냅샷과 함께 record 로 밀어넣는다). */
        private data class PendingTurn(val koreanPrompt: String, val userText: String)

        private companion object {
            const val PROVIDER_KEY = "session_turn"
            const val BUNDLE_JSON = "json"
            const val HINT_RETRY = "다시 말해볼까요? 채팅으로 입력해도 돼요."
            const val HINT_ERROR = "문제가 생겼어요. 다시 시도해 주세요."
        }
    }

@Composable
internal fun GeneratedDialogueSessionContent(
    state: GeneratedDialogueState,
    onViewSummary: () -> Unit,
    modifier: Modifier = Modifier,
    // 세션 정체성 헤더. 미주입(스텁·테스트)이면 헤더 없이 렌더(기존 스크린샷 계약 유지).
    header: DialogueHeaderState? = null,
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
        header = header,
        dock = dock,
    )
}

/**
 * 세션 헤더 레벨 라벨(홈 히어로 문구 정합) — `<레벨 한글> · <N>턴`. 레벨 문자열은 홈 설정과 동일 매핑
 * (easy=쉬움/normal=보통/hard=어려움), 미해소/빈 값이면 턴 수만 남긴다.
 */
private fun dialogueLevelLabel(
    level: String,
    totalTurns: Int,
): String {
    val levelKo =
        when (level) {
            "easy" -> "쉬움"
            "normal" -> "보통"
            "hard" -> "어려움"
            else -> null
        }
    return listOfNotNull(levelKo, "${totalTurns}턴").joinToString(" · ")
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

    /** 현 상태 + 앰비언트 micState/turns + 세션 식별(sessionId/level)을 [SessionTurnSnapshot] 으로 직렬화. */
    fun toSnapshot(
        micState: MicState,
        turns: List<NetworkDialogueTurn>,
        sessionId: String?,
        level: String?,
    ): SessionTurnSnapshot =
        SessionTurnSnapshot(
            sessionId = sessionId,
            level = level,
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

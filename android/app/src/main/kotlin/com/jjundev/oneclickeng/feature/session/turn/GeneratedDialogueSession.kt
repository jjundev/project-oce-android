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
import com.jjundev.oneclickeng.core.session.SessionLevel
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenState
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenerationCoordinator
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueStreamStatus
import com.jjundev.oneclickeng.feature.session.feedback.DeepFeedbackCoordinator
import com.jjundev.oneclickeng.feature.session.feedback.Paraphrase
import com.jjundev.oneclickeng.feature.session.feedback.SectionState
import com.jjundev.oneclickeng.feature.session.feedback.SlimFeedbackCoordinator
import com.jjundev.oneclickeng.feature.session.feedback.SlimFeedbackSheet
import com.jjundev.oneclickeng.feature.session.feedback.SlimFeedbackState
import com.jjundev.oneclickeng.feature.session.feedback.SlimSection
import com.jjundev.oneclickeng.feature.session.resume.SessionSnapshotStore
import com.jjundev.oneclickeng.feature.session.speaking.SpeakingAnalysisCoordinator
import com.jjundev.oneclickeng.feature.session.speaking.SpeakingAnalysisState
import com.jjundev.oneclickeng.feature.session.summary.SessionTurnBufferStore
import com.jjundev.oneclickeng.feature.session.tts.PlaybackState
import com.jjundev.oneclickeng.feature.session.tts.TtsPlaybackCoordinator
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
    // 대화 나가기(세션 그래프 → 홈/탭). 시스템 뒤로가기·헤더 뒤로가기·시트 dismiss 가 모두 이 출구로 수렴한다.
    onExit: () -> Unit = {},
    // 세션 정체성 헤더(주제 이모지·제목·레벨·진행 점) 재료. 시작 플로우(홈/온보딩)만 실값을 싣고,
    // 이어하기·프로세스킬 복원 재진입은 빈 값이라 VM 이 durable 스냅샷에서 복원한 정체성으로 폴백한다.
    topicEmoji: String = "",
    topicTitle: String = "",
    level: String = "",
    totalTurns: Int = 5,
    viewModel: GeneratedDialogueSessionViewModel = hiltViewModel(),
) {
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val state = viewModel.turnState

    // 라이브 턴 피드백 시트(M1-07 slim + M2-03 deep) 상태축. 답변 정착 후 코디네이터가 Active 로 밀면
    // 아래 [SlimFeedbackSheet] 가 모달로 올라오고, "다음"으로 전진하면 reset→Idle 이 되어 스스로 내려간다.
    val feedbackState by viewModel.feedbackState.collectAsStateWithLifecycle()
    val deepState by viewModel.deepState.collectAsStateWithLifecycle()
    val bookmarkedLevels by viewModel.bookmarkedLevels.collectAsStateWithLifecycle()

    // 시작 플로우가 실어온 주제 정체성. 실값이면 VM 에 기억시켜 durable 스냅샷에 실린다(이어하기/복원 시 헤더 유지).
    val navIdentity =
        if (topicTitle.isNotBlank()) {
            SessionHeaderIdentity(
                topicEmoji = topicEmoji,
                topicTitle = topicTitle,
                level = level,
                totalTurns = totalTurns,
            )
        } else {
            null
        }
    LaunchedEffect(navIdentity) { navIdentity?.let(viewModel::rememberHeaderIdentity) }

    // 헤더 재료: 시작 플로우 nav-arg 우선, 빈 재진입(이어하기/프로세스킬)은 VM 이 durable 스냅샷에서 복원한
    // 정체성으로 폴백한다(둘 다 없으면 header=null → 미표시). 진행 점/수치는 [totalTurns](상대+학습자
    // 합산 스크립트 길이)와 같은 단위로 맞춰 누적 말풍선 총수로 채운다(학습자 말풍선만 세면 완주해도
    // totalTurns 의 절반에서 멈춘다 — 상대·학습자가 교대로 한 줄씩 쌓이므로).
    val identity = navIdentity ?: viewModel.headerIdentity
    val header =
        identity?.let {
            DialogueHeaderState(
                topicEmoji = it.topicEmoji,
                title = it.topicTitle,
                levelLabel = dialogueLevelLabel(it.level, it.totalTurns),
                totalTurns = it.totalTurns,
                completedTurns = state.messages.size,
            )
        }

    // 코디네이터 라이브 상태를 턴머신에 흘려보낸다. 프로세스킬 복원 시 코디네이터는 Idle(비Ready)라 accept 는
    // no-op 이고, 시드된 스냅샷이 정본으로 남는다(결정 #4).
    LaunchedEffect(generationState) { viewModel.onGenerationState(generationState) }

    DialogueExitGuard(onExit = onExit) { onBackRequest ->
        GeneratedDialogueSessionContent(
            state = state,
            // 세션 완료(sessionPhase == Completed) 진입 시 콘텐츠가 자동 발화 — 완료 화면 없이 곧장 요약으로(M3-02
            // 대화→요약 배선). 완주 후에만 도달하므로 sessionId 는 non-null 이나 방어적으로 orEmpty.
            onViewSummary = { onViewSummary(viewModel.sessionId().orEmpty()) },
            modifier = modifier,
            header = header,
            // 헤더 뒤로가기 화살표는 시스템 back 과 동일하게 "대화 중단 시트"를 띄운다(가드가 소유).
            onBack = onBackRequest,
            dock = { task ->
                MicSessionDock(
                    task = task,
                    viewModel = viewModel,
                    reduceMotion = reduceMotion,
                )
            },
            onReplay = { text -> viewModel.replayOpponent(text) },
            // 상대 발화자 이름을 말풍선에 반영. 미배정(초기·sessionId 미도착)이면 "Emma" 폴백.
            opponentSpeaker = viewModel.opponentSpeaker?.name ?: "Emma",
            // 자기 녹음 재생: 어떤 학습자 말풍선에 버튼을 띄울지 + 탭 시 그 순번 클립 재생.
            learnerClipIndices = viewModel.learnerClipIndices,
            onPlayLearnerClip = { index -> viewModel.playLearnerClip(index) },
            onSpeakOpponent = { text -> viewModel.speakOpponent(text) },
        )

        // 턴 피드백 시트는 드래그 없는 고정 오버레이라 대화 콘텐츠의 형제로 얹는다. Idle 이면 스스로 아무것도
        // 렌더하지 않아(early return) 턴 사이엔 숨는다. 시트는 스와이프/탭으로 줄이거나 닫을 수 없고,
        // "다음"(onNext)으로 전진하거나 시스템 뒤로가기(가드 → 대화 중단 시트)로만 벗어난다.
        SlimFeedbackSheet(
            state = feedbackState,
            onRetry = viewModel::retryFeedback,
            onSkip = viewModel::skipFeedback,
            onNext = { viewModel.onAdvance() },
            deepState = deepState,
            deepExpanded = viewModel.deepExpanded,
            onExpandDeep = viewModel::expandDeep,
            onCollapseDeep = viewModel::collapseDeep,
            onRetryDeep = viewModel::retryDeep,
            bookmarkedLevels = bookmarkedLevels,
            onToggleBookmark = viewModel::toggleBookmark,
        )
    }
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
/** The English of the opponent (model) line at the given 0-based opponent ordinal, or
 *  null if that line is not yet available. Opponent lines occupy even indices (0,2,4,…)
 *  of the raw turn buffer per the wire contract (DialogueTurn.role ∈ {"model","user"});
 *  ordinal 0 = the first opponent line. Used to prefetch/​warm a line's TTS ahead of its
 *  turn. Defensive against malformed data (non-model / blank → null). */
internal fun nextOpponentEnglish(
    turns: List<NetworkDialogueTurn>,
    opponentOrdinal: Int,
): String? {
    val turn = turns.getOrNull(2 * opponentOrdinal) ?: return null
    return turn.en.takeIf { turn.role == "model" && it.isNotBlank() }
}

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
        private val deep: DeepFeedbackCoordinator,
        private val turnBuffer: SessionTurnBufferStore,
        private val appScope: CoroutineScope,
        private val snapshotStore: SessionSnapshotStore,
        private val tts: TtsPlaybackCoordinator,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val generationState = generation.state

        // 라이브 턴 피드백 시트(M1-07 slim + M2-03 deep) 배선용 상태축. Route 가 라이프사이클 인지 collect 해
        // [SlimFeedbackSheet] 에 흘려보낸다. slim/deep 는 코디네이터가 정본, 펼침 여부만 호스트 UI 상태다.
        val feedbackState = feedback.state
        val deepState = deep.state

        // 턴 내 ephemeral 북마크 레벨(1/2/3). 코디네이터가 toggle/reset/retry 전반에서 유지하는 정본을 그대로
        // 노출한다 — 호스트 미러(2차 소스)로 두면 retry() 의 비움 등과 어긋날 수 있어 단일 소스로 둔다.
        val bookmarkedLevels = deep.bookmarkedLevels

        // 세션 헤더 정체성(주제 이모지·제목·레벨·턴 수). 시작 플로우가 nav-arg 로 실어오면 Route 가
        // [rememberHeaderIdentity] 로 심고, durable 스냅샷에도 실어 이어하기/프로세스킬 재진입(빈 nav-arg)에서
        // 상단바를 되살린다(회귀: 재진입 시 헤더 소멸). Route 는 nav-arg 가 비면 이 값으로 폴백한다.
        var headerIdentity by mutableStateOf<SessionHeaderIdentity?>(null)
            private set

        /**
         * 이 세션의 상대 발화자(로컬 [SpeakerDirectory] 배정). sessionId 가 처음 알려질 때 1회 배정하고,
         * 배정은 sessionId 결정적이라 복원 시 [seedFrom] 이 동일 발화자를 재도출한다(영속 불필요).
         * Route 가 이름을 말풍선에, [speakOpponent]/[replayOpponent] 가 성별을 TTS 에 쓴다.
         */
        var opponentSpeaker by mutableStateOf<Speaker?>(null)
            private set

        /** "더 보기" 펼침 여부(호스트 소유 UI 상태). 코디네이터는 개시/캐시만 알고 펼침은 모른다(P3). */
        var deepExpanded by mutableStateOf(false)
            private set

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
        private val progress = SessionTurnProgress(turnState, ::persistResume)

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

        /**
         * 자기 녹음 재생용 세션 메모리 클립 저장소. 키 = 0-based 학습자 턴 순번([learnerOrdinalAt] 와 동일).
         * 영속하지 않는다(프로세스킬/복원 시 비어 시작 — 상대 replay 정합). VM 소멸과 함께 GC 된다.
         */
        private val learnerClips = mutableMapOf<Int, RecordingResult.Captured>()

        /** 아직 전사 대기 중인(= append 전) 방금 캡처된 클립. [onAnalysisState] 가 소비하고 비운다. */
        private var pendingClip: RecordingResult.Captured? = null

        /**
         * 자기 녹음이 있는 학습자 말풍선 순번 집합. 관찰 가능 상태라, 클립이 들어오는 순간 해당 말풍선이
         * 재구성돼 스피커 버튼이 나타난다. Route 가 [GeneratedDialogueSessionContent] 로 흘려보낸다.
         */
        var learnerClipIndices by mutableStateOf<Set<Int>>(emptySet())
            private set

        // L2 원본 버퍼-of-record. 정상 운영 시 코디네이터 Ready.turns 에서 갱신, 복원 시 스냅샷에서 seed.
        private var latestTurns: List<NetworkDialogueTurn> = emptyList()

        // 이미 프리페치를 발주한 상대 라인 서수(중복 발주·SSE 청크마다의 코루틴 런치 억제). 실제 발주 성공 시에만
        // 갱신하므로 turns 미도착으로 실패하면 다음 상태에서 재시도된다. 세션 복원 시 -1로 리셋.
        private var lastWarmedOrdinal = -1

        // 아직 요약 버퍼에 기록되지 않은 현재 턴의 과제·답변 echo. 피드백 정착([onFeedbackState]) 또는
        // "다음"([onAdvance]) 중 먼저 오는 쪽이 기록하고 null 로 비워 턴당 1회 기록을 보장한다.
        private var pendingTurn: PendingTurn? = null

        // deep("더 보기") start 파라미터 stash. 이제 슬림 정착 시 [onFeedbackState] 가 이거-프리페치로 개시하고,
        // [expandDeep] 는 fallback 재호출(no-op)로 공존한다. 턴 전환 시 [onAdvance] 가 비운다.
        private var deepParams: DeepParams? = null

        private val json = Json { ignoreUnknownKeys = true }

        // 첫 NavBackStackEntry 복원 전에는 생성 코디네이터의 Ready emission을 잠시 보류한다. 같은 프로세스의
        // 이어하기는 Singleton 코디네이터가 전체 대본을 들고 있어도 durable 스냅샷의 진행 위치가 정본이다.
        private var initialRestoreComplete = false
        private var deferredGenerationState: DialogueGenState? = null

        init {
            val restored =
                savedStateHandle.get<Bundle>(PROVIDER_KEY)
                    ?.getString(BUNDLE_JSON)
                    ?.let { runCatching { json.decodeFromString<SessionTurnSnapshot>(it) }.getOrNull() }
                    ?.takeIf { it.schemaVersion == SessionTurnSnapshot.SCHEMA_VERSION }
            if (restored != null) {
                seedFrom(restored)
            } else {
                // SavedStateHandle 에 화면-체류 스냅샷이 없다 = 새 NavBackStackEntry(홈-복귀 재진입 등).
                // durable read 가 끝날 때까지 Route 의 Ready emission 을 보류해, 같은 프로세스 이어하기에서
                // 코디네이터의 처음부터인 turns 가 durable 진행 위치를 덮어쓰지 않게 한다.
                viewModelScope.launch {
                    val durable = snapshotStore.read()
                    val liveState = generation.state.value
                    if (
                        durable != null &&
                            shouldRestoreDurableSnapshot(durable, liveState) &&
                            turnState.messages.isEmpty()
                    ) {
                        seedFrom(durable)
                    }
                    initialRestoreComplete = true
                    deferredGenerationState?.let {
                        deferredGenerationState = null
                        acceptGenerationState(it)
                    }
                }
            }
            if (restored != null) initialRestoreComplete = true
            // onGenerationState 는 Route 의 collectAsStateWithLifecycle 가 구동한다(중복 collect 회피).
            viewModelScope.launch { speaking.state.collect(::onAnalysisState) }
            // 슬림 피드백이 정착(3섹션 모두 Loading 종료)하면 그 턴을 요약 버퍼에 기록한다(M2-02 handoff).
            // 시트(M1-07)가 아직 라이브에 없어 사용자는 정착 전에 "다음"을 누를 수 있으므로, 백그라운드
            // 정착 기록 + [onAdvance] best-effort 폴백의 이중 경로로 턴당 1회 기록을 보장한다(pendingTurn 가드).
            viewModelScope.launch { feedback.state.collect(::onFeedbackState) }
            // 상대역 자동발화 완료(정상/실패/mute) → 현재 턴 마감(입력 독 상승). advanceOnDone=false 인 replay 는
            // completions 를 내지 않으므로 여기로 오지 않는다(자동발화만 전진 구동).
            viewModelScope.launch { tts.completions.collect { onOpponentTtsDone() } }
            // 음성 데이터 없음(ERROR_TEXT_ONLY)은 completions 대신 상태로만 표출된다(코디네이터 advance=false).
            // 이 상태는 단말 경로에서만(DEVICE 설정 또는 SERVER 합성 실패 후 단말 폴백) 영어 음성 데이터 미설치 시
            // 나온다. 더 내려갈 폴백이 없으므로 텍스트는 남긴 채 그냥 전진시켜 세션이 멈추지 않게 한다(결정 #14).
            // 주의: 이 수집기는 advanceOnDone 게이트가 없다 — replay(LearnerTurn 한정) 중 음성없음이 나도
            // completeOpponentTurn 의 OpponentTurn/InTurn 가드가 오전진을 흡수하는 데 의존한다. replay 를
            // OpponentTurn 중 허용하거나 그 가드를 완화하면 이 의존이 깨지니 함께 재검토할 것.
            viewModelScope.launch {
                tts.state.collect { if (it == PlaybackState.ERROR_TEXT_ONLY) onOpponentTtsDone() }
            }
            // 상대역 오디오가 실제 재생을 시작하는 순간(코디네이터 audioReady: 디바이스 엔진 onStart / 서버 PCM
            // 재생 시작) 말풍선을 표시한다. 그 전까지는 스켈레톤이 유지돼 첫 오디오 API 로딩(디바이스 엔진 init)
            // 동안 "표시된 대사 + 침묵"이 아니라 "타이핑 중"으로 보인다. OpponentTurn 가드는 progress 안에 있어
            // replay/자기녹음 재생(LearnerTurn)의 PLAYING 은 무시된다.
            viewModelScope.launch { tts.audioReady.collect { revealOnAudioReady() } }
            savedStateHandle.setSavedStateProvider(PROVIDER_KEY) {
                bundleOf(BUNDLE_JSON to json.encodeToString(currentSnapshot()))
            }
        }

        /** L1 파생 상태 + 앰비언트 + 세션 식별을 스냅샷에서 복원한다(SavedStateHandle·durable 공용). */
        private fun seedFrom(snapshot: SessionTurnSnapshot) {
            turnState.restoreFrom(snapshot)
            latestTurns = snapshot.turns.map { it.toDomain() }
            lastWarmedOrdinal = -1 // 복원된 위치에서 다시 워밍하도록 리셋
            restoredSessionId = snapshot.sessionId
            restoredLevel = snapshot.level
            assignSpeakerIfNeeded() // 결정적 매핑이라 복원 sessionId 로 동일 발화자 재도출
            restoreHeaderIdentity(snapshot)
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
                .copy(
                    // 헤더 정체성을 함께 실어 이어하기/복원 재진입에서 상단바를 되살린다(레벨은 위 currentLevel 재사용).
                    topicEmoji = headerIdentity?.topicEmoji,
                    topicTitle = headerIdentity?.topicTitle,
                    totalTurns = headerIdentity?.totalTurns,
                )

        /** 시작 플로우가 실어온 세션 헤더 정체성을 기억한다(빈 제목이면 무시 — durable 복원값을 덮지 않게). */
        fun rememberHeaderIdentity(identity: SessionHeaderIdentity) {
            if (identity.topicTitle.isBlank()) return
            headerIdentity = identity
        }

        /** durable/SavedState 스냅샷에서 헤더 정체성을 복원한다(이미 심겨 있으면 유지 — 시작 플로우 우선). */
        private fun restoreHeaderIdentity(snapshot: SessionTurnSnapshot) {
            val title = snapshot.topicTitle
            if (headerIdentity == null && !title.isNullOrBlank()) {
                headerIdentity =
                    SessionHeaderIdentity(
                        topicEmoji = snapshot.topicEmoji.orEmpty(),
                        topicTitle = title,
                        level = snapshot.level.orEmpty(),
                        totalTurns = snapshot.totalTurns ?: DEFAULT_TOTAL_TURNS,
                    )
            }
        }

        /**
         * durable 스냅샷 저장/폐기(§2.5). 완주(Completed)면 폐기(미완 아님 → 복귀 후보 아님), 아니면 현 상태를
         * 영속화한다. appScope 로 실행해 화면 이탈(onCleared)로 취소되지 않게 한다.
         */
        private fun persistResume() {
            val snapshot = currentSnapshot()
            appScope.launch { snapshotStore.persist(snapshot) }
        }

        fun revealOpponentTurn() = progress.revealOpponentTurn()

        fun revealOnAudioReady() = progress.revealOnAudioReady()

        fun completeOpponentTurn() = progress.completeOpponentTurn()

        /** 코디네이터 상태를 턴머신에 반영(Route 가 라이프사이클 인지 collect 로 호출). */
        fun onGenerationState(state: DialogueGenState) {
            if (!initialRestoreComplete) {
                deferredGenerationState = state
                return
            }
            acceptGenerationState(state)
        }

        private fun acceptGenerationState(state: DialogueGenState) {
            if (state is DialogueGenState.Ready) latestTurns = state.turns
            val ordinalBeforeAccept = turnState.opponentTurnSerial // accept()가 이번에 증가시킬 수 있어 그 전에 읽는다
            turnState.accept(state)
            reconcileLearnerClips() // turns 축소 리셋 시 사라진 순번의 stale 클립 파기
            assignSpeakerIfNeeded()
            prefetchOpponentLine(ordinalBeforeAccept) // 첫 라인 워밍(서수 0) + 스트리밍으로 늦게 온 라인 재시도
            persistResume()
        }

        /** sessionId 가 알려져 있고 아직 미배정이면 상대 발화자를 배정한다(멱등 — 결정적 매핑). */
        private fun assignSpeakerIfNeeded() {
            if (opponentSpeaker == null) {
                currentSessionId()?.let { opponentSpeaker = SpeakerDirectory.assign(it) }
            }
        }

        /** 상대역 대사 자동발화(Route 가 commitReveal 직후 호출). 음질 설정을 따른다 — SERVER 면 서버(Gemini)
         *  합성(8초 워치독 후 단말 폴백), DEVICE 면 단말 TTS. 완료 시 completions→자동진행. */
        fun speakOpponent(text: String) {
            tts.playTurn(text, gender = opponentSpeaker?.gender, advanceOnDone = true)
        }

        /** 주어진 상대 서수(ordinal, 0-기반)의 라인 오디오를 미리 서버 합성해 캐시에 채운다. 코디네이터가
         *  SERVER·비음소거 게이트와 중복요청 dedup을 처리하므로 발주 자체는 안전하다. lastWarmedOrdinal 로
         *  같은 서수 반복 발주만 억제한다. 라인 미도착(null)이면 lastWarmedOrdinal 을 갱신하지 않아 재시도된다. */
        private fun prefetchOpponentLine(ordinal: Int) {
            if (ordinal == lastWarmedOrdinal) return
            val text = nextOpponentEnglish(latestTurns, ordinal) ?: return // 아직 미도착 — 다음 상태에서 재시도
            lastWarmedOrdinal = ordinal
            tts.prefetch(text, opponentSpeaker?.gender)
        }

        /** 말풍선 "다시 듣기" 재발화. 자동발화 중(OpponentTurn)엔 no-op — 라이브 발화 취소·조기전진을 막는다.
         *  음질 설정을 따라 재합성한다(SERVER 면 서버 재합성 — 캐시 재사용 아님, 결정 A). advanceOnDone=false 라
         *  재발화 완료가 턴 전진을 구동하지 않는다(경쟁 봉인, 결정 #9). */
        fun replayOpponent(text: String) {
            if (turnState.turnPhase == TurnPhase.OpponentTurn) return
            tts.playTurn(text, gender = opponentSpeaker?.gender, advanceOnDone = false)
        }

        /**
         * 학습자 말풍선 스피커 탭 → 그 순번의 세션 메모리 클립을 재생(상대 발화와 단일 재생 권위 공유).
         * 상대 자동발화 중(OpponentTurn)엔 no-op — [replayOpponent] 와 **동일한 가드**다. [TtsPlaybackCoordinator.playClip]
         * 은 `startNewSession()` 으로 진행 중 재생을 취소하는데, 그때 취소되는 상대 자동발화(`playTurn`,
         * advanceOnDone=true)는 완료 신호(completions)를 못 내 [onOpponentTtsDone]→completeOpponentTurn 이
         * 영영 호출되지 않아 턴이 OpponentTurn 에 갇힌다. 이 가드가 그 교착을 봉인한다(회귀 방지).
         */
        fun playLearnerClip(index: Int) {
            if (turnState.turnPhase == TurnPhase.OpponentTurn) return
            learnerClips[index]?.let { tts.playClip(it.pcm, it.sampleRate) }
        }

        /**
         * turnState 리셋(생성 재시작으로 turns 축소, [GeneratedDialogueState] `reset`) 등으로 학습자 말풍선이
         * 줄면, 더 이상 존재하지 않는 순번의 세션 클립을 버린다(stale 오귀속 방지). 정상 운영 시엔 모든 클립
         * 순번이 현재 학습자 수 미만이라 no-op 이다.
         */
        private fun reconcileLearnerClips() {
            val learnerCount = turnState.messages.count { it is DialogueMessage.Learner }
            if (learnerClips.keys.any { it >= learnerCount }) {
                learnerClips.keys.retainAll { it < learnerCount }
                learnerClipIndices = learnerClips.keys.toSet()
            }
        }

        /** TTS 완료/음성없음 폴백 시 현재 상대역 턴 마감. 내부 가드로 OpponentTurn·InTurn 일 때만 실효. */
        private fun onOpponentTtsDone() {
            // 자동발화 완료가 턴을 마감한다. progress 경유(completeOpponentTurn())라야 마감 후 durable 스냅샷이
            // 갱신돼 master 의 "전진 후 영속" 계약이 유지된다(turnState 직접 호출은 persistResume 를 건너뜀).
            completeOpponentTurn()
            prefetchOpponentLine(turnState.opponentTurnSerial) // 학습자 턴 진입 → 다음 상대 라인 미리 합성
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
                            pendingClip = result // 전사 성공 시 append 되는 말풍선에 붙일 자기 녹음
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

        /**
         * 도크 "처음부터 말하기" 탭 — 이번 발화 시도를 통째로 버린다(녹음 중·분석 중 공용).
         *
         * - Recording: 캡처를 멈추고 결과를 버린다([stopRecording] 과 달리 Analyzing 으로 넘어가지 않는다).
         * - Analyzing: 진행 중인 전사/LLM 왕복을 [SpeakingAnalysisCoordinator.reset] 으로 취소한다. 취소된
         *   요청은 Result 를 내지 않으므로 [onAnalysisState] → [triggerFeedback] 이 돌지 않고, 따라서 취소한
         *   턴의 피드백 시트가 뒤늦게 떠오르지 않는다(시트는 [triggerFeedback] → feedback.start 로만 뜬다).
         *   [onAnalysisState] 의 `micState != Analyzing` 가드는 방어적 2중화다 — 현재 배선(코디네이터 scope 가
         *   Main.immediate, 토큰 검사와 `_state` 기록 사이에 중단점 없음)에서는 취소와 응답 기록이 겹칠 수
         *   없어 도달 불가지만, 그 scope 가 메인 밖으로 옮겨져도 안전하도록 남겨둔다.
         *
         * micState 를 launch 밖에서 **먼저** 뒤집는 건 재탭 창을 즉시 닫기 위함이다(안에서 뒤집으면 stop() 이
         * 끝나기 전 마이크 재탭이 [stopRecording] 을 타 취소한 녹음을 도로 제출한다). Ready 로 되돌리면
         * [MicButton] 이 이미 tappable(enabled=Ready||Recording)이라 재녹음은 추가 배선 없이 바로 가능하다.
         */
        fun onCancelSpeaking() {
            val phase = micState
            if (phase != MicState.Recording && phase != MicState.Analyzing) return
            micState = MicState.Ready
            pendingClip = null // 취소한 녹음은 다음 답변 말풍선에 붙지 않는다
            if (phase == MicState.Recording) {
                viewModelScope.launch { recording.stop() }
            } else {
                speaking.reset()
            }
        }

        // 우리 분석(micState=Analyzing)에만 반응 — Singleton 의 이전 세션 잔여 상태 오반응 차단.
        private fun onAnalysisState(state: SpeakingAnalysisState) {
            if (micState != MicState.Analyzing) return
            when (state) {
                is SpeakingAnalysisState.Result -> {
                    turnState.appendLearnerAnswer(state.transcript)
                    // 방금 append 된 학습자 말풍선의 0-based 순번에 이 턴의 녹음을 매핑(있으면).
                    pendingClip?.let { clip ->
                        // append 가 LearnerTurn 가드로 no-op 된 경우(예: 전사 중 turnState 리셋)엔 messages 가 비어
                        // ordinal 이 -1 이 된다 — 그런 유령 클립은 저장하지 않는다(영구 누수·오귀속 방지).
                        val ordinal = turnState.messages.count { it is DialogueMessage.Learner } - 1
                        if (ordinal >= 0) {
                            learnerClips[ordinal] = clip
                            learnerClipIndices = learnerClips.keys.toSet()
                        }
                    }
                    pendingClip = null
                    micState = MicState.Complete
                    triggerFeedback(state.transcript)
                    persistResume()
                }
                SpeakingAnalysisState.Empty -> {
                    pendingClip = null
                    micState = MicState.Ready
                    retryHint = HINT_RETRY
                }
                SpeakingAnalysisState.Failed -> {
                    pendingClip = null
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
            // deep 도 새 턴을 위해 Idle 로 되돌린다(캐시·ephemeral 북마크 파기). 펼침/stash 도 함께 비운다.
            deep.reset()
            deepExpanded = false
            deepParams = null
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
                // deep cardId 파생용 0-based 학습자 턴 인덱스. triggerFeedback 은 appendLearnerAnswer 직후라
                // 학습자 말풍선 수는 현재 턴을 이미 포함한다 → -1 로 0-based 로 만든다.
                val turnIndex = turnState.messages.count { it is DialogueMessage.Learner } - 1
                deepParams = DeepParams(sid, turnIndex, task, userEnglish, ref, level)
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
            if (resolved) {
                recordTurn(pending)
                // 딥 이거-프리페치: 슬림 3섹션이 종결되는 즉시 딥을 백그라운드로 개시해, 사용자가 바닥까지
                // 스크롤해 "더 보기"를 누를 때 대기 없이 즉시 펼쳐지게 한다(온디맨드→이거, 결정 #17/#19).
                // recordTurn 이 pendingTurn 을 비우므로 이 블록은 정착 emission 1회에만 실행된다.
                // 캡 거부(QuotaBlocked)면 딥도 동일 세션 캡에 걸리므로 개시하지 않는다(불필요 왕복 회피, 결정 #20).
                // start()는 Idle 이 아니면 no-op 이라 이후 [expandDeep]의 재호출과 안전하게 공존한다(P3).
                if (state is SlimFeedbackState.Active) {
                    deepParams?.let { p ->
                        deep.start(p.sessionId, p.turnIndex, p.koreanPrompt, p.userText, p.referenceEnglish, p.level)
                    }
                }
            }
        }

        /** 요약 버퍼에 한 턴을 기록하고 pending 을 비운다(정착·"다음" 공용 진입). */
        private fun recordTurn(pending: PendingTurn) {
            turnBuffer.record(pending.koreanPrompt, pending.userText, feedback.bufferSnapshot())
            pendingTurn = null
        }

        // --- 턴 피드백 시트 상호작용(M1-07 slim + M2-03 deep) — Route 가 [SlimFeedbackSheet] 콜백에 연결 ---

        /** 실패 슬림 섹션 재시도(코디네이터가 canRetry 게이트). */
        fun retryFeedback(section: SlimSection) = feedback.retry(section)

        /** 반복 실패 슬림 섹션 스킵(settled 로 간주돼 "다음"/"더 보기" 게이트 통과). */
        fun skipFeedback(section: SlimSection) = feedback.skip(section)

        /** "더 보기" 첫 확장 → stash 한 현재 턴 파라미터로 deep 개시(코디네이터가 턴당 1회 캐시). */
        fun expandDeep() {
            deepParams?.let { p ->
                deep.start(p.sessionId, p.turnIndex, p.koreanPrompt, p.userText, p.referenceEnglish, p.level)
            }
            deepExpanded = true
        }

        /** "접기" → 펼침만 내린다(재호출 없음, 캐시 유지 — P3). */
        fun collapseDeep() {
            deepExpanded = false
        }

        /** deep 영역 재시도(Error 에서만 실효, 코디네이터 게이트). */
        fun retryDeep() = deep.retry()

        /** 패러프레이즈 북마크 토글 → 코디네이터가 ephemeral 레벨·영속(M2-04)을 함께 갱신. */
        fun toggleBookmark(paraphrase: Paraphrase) {
            deep.toggleBookmark(paraphrase)
        }

        override fun onCleared() {
            // 진행 중 캡처/분석을 화면 이탈 시 취소(결정 #13b). appScope 는 VM scope 소멸과 무관.
            appScope.launch { runCatching { recording.stop() } }
            speaking.reset()
            tts.stop() // 잔여 발화 차단(nav-pop 시 이 훅이 커버 — 별도 onExit 훅 없음).
            tts.clearCache() // 프리페치/캐시 파기(화면 이탈 — 다음 세션에 stale 오디오가 새지 않게).
        }

        /** 요약 버퍼 기록 대기 중인 턴의 과제·답변 echo(피드백 스냅샷과 함께 record 로 밀어넣는다). */
        private data class PendingTurn(val koreanPrompt: String, val userText: String)

        /** deep 개시 지연을 위해 stash 하는 현재 턴의 [DeepFeedbackCoordinator.start] 파라미터. */
        private data class DeepParams(
            val sessionId: String,
            val turnIndex: Int,
            val koreanPrompt: String,
            val userText: String,
            val referenceEnglish: String,
            val level: String,
        )

        private companion object {
            const val PROVIDER_KEY = "session_turn"
            const val BUNDLE_JSON = "json"
            const val HINT_RETRY = "다시 말해볼까요? 채팅으로 입력해도 돼요."
            const val HINT_ERROR = "문제가 생겼어요. 다시 시도해 주세요."
            const val DEFAULT_TOTAL_TURNS = 5
        }
    }

/**
 * Selects the durable snapshot when a new session ViewModel sees a live generation state.
 * A Ready state with the same server session id is the same conversation at a later in-app
 * re-entry, so its durable cursor wins over the coordinator's full turn list. A different Ready
 * id is a genuinely new generation and must not be polluted by an older resume candidate; a
 * non-Ready state has no live conversation to prefer (the process-death restore path).
 */
internal fun shouldRestoreDurableSnapshot(
    snapshot: SessionTurnSnapshot,
    liveState: DialogueGenState,
): Boolean {
    val ready = liveState as? DialogueGenState.Ready ?: return true
    return snapshot.sessionId != null && snapshot.sessionId == ready.sessionId
}

/**
 * 세션 헤더 정체성(주제 이모지·제목·레벨·턴 수). 시작 플로우 nav-arg 로 실려오거나 durable 스냅샷에서 복원돼
 * [DialogueHeaderState] 로 렌더된다. 진행 점(completedTurns)은 라이브 말풍선 수라 여기 담지 않는다.
 */
data class SessionHeaderIdentity(
    val topicEmoji: String,
    val topicTitle: String,
    val level: String,
    val totalTurns: Int,
)

@Composable
internal fun GeneratedDialogueSessionContent(
    state: GeneratedDialogueState,
    onViewSummary: () -> Unit,
    modifier: Modifier = Modifier,
    // 세션 정체성 헤더. 미주입(스텁·테스트)이면 헤더 없이 렌더(기존 스크린샷 계약 유지).
    header: DialogueHeaderState? = null,
    // 헤더 뒤로가기 화살표 콜백(대화 나가기). 미주입이면 no-op(프리뷰·테스트 호환).
    onBack: () -> Unit = {},
    dock: (@Composable (ScaffoldTask) -> Unit)? = null,
    // 상대역 말풍선 "다시 듣기" 콜백(발화 텍스트 전달). 미주입이면 no-op(프리뷰·테스트 호환).
    onReplay: (String) -> Unit = {},
    // 상대역 화자명(로컬 SpeakerDirectory 배정). 미주입(프리뷰·테스트)이면 "Emma" 고정(스크린샷 계약 유지).
    opponentSpeaker: String = "Emma",
    // 자기 녹음이 있는 학습자 말풍선 순번 집합. 미주입(프리뷰·테스트)이면 빈 집합(버튼 없음, 스크린샷 계약 유지).
    learnerClipIndices: Set<Int> = emptySet(),
    // 학습자 말풍선 스피커 탭 콜백. 미주입이면 no-op.
    onPlayLearnerClip: (Int) -> Unit = {},
    // 상대역 대사 합성/발화 시작 콜백. Route 는 viewModel.speakOpponent 로 연결한다. 미주입(테스트)이면 no-op.
    onSpeakOpponent: (String) -> Unit = {},
    // 상대역 말풍선 reveal 전 최소 스켈레톤 노출 dwell(ms). 이 시간 경과 후에만 onSpeakOpponent 를 호출한다.
    minSkeletonMs: Long = DEFAULT_OPPONENT_SKELETON_FLOOR_MS,
) {
    val listState = rememberLazyListState()
    // 메시지 추가·타이핑 스켈레톤 등장 시 최신 아이템으로 자동 스크롤(스켈레톤은 메시지 뒤 마지막 아이템).
    LaunchedEffect(state.messages.size, state.opponentTyping) {
        val lastIndex = if (state.opponentTyping) state.messages.size else state.messages.lastIndex
        if (lastIndex >= 0) {
            listState.animateScrollToItem(lastIndex)
        }
    }
    // 상대역 턴 진입 → 최소 스켈레톤 dwell 후 대사 합성/발화 시작. 말풍선 표시는 VM 의 tts.audioReady 수집
    // (revealOnAudioReady)이 구동하므로, 스켈레톤은 dwell + 합성-로딩 시간만큼 노출돼 항상 최소 dwell 이상
    // 눈에 보인다. dwell 은 reduceMotion 과 무관하게 적용한다(페이싱 게이트). serial 이 재키잉되면 이전 dwell
    // 코루틴은 취소된다.
    LaunchedEffect(state.opponentTurnSerial) {
        if (state.turnPhase == TurnPhase.OpponentTurn && state.sessionPhase == SessionPhase.InTurn) {
            delay(minSkeletonMs)
            // 가드는 delay 전 1회만 평가한다. dwell 중 OpponentTurn/InTurn 을 벗어나지 않음에 의존한다 —
            // 이 조합은 completeOpponentTurn 으로만 벗어나고, 그건 speak 개시 후 TTS 신호(audioReady/completions/
            // ERROR_TEXT_ONLY) 하류에서만 발화하므로 speak 전 전이는 없다(새 displayOpponent 는 serial 재키잉→취소).
            // dwell 전에 전이를 유발하는 코드를 추가하면 이 불변식이 깨지니 함께 재검토할 것.
            state.pendingOpponentEnglish()?.let(onSpeakOpponent)
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
        onBack = onBack,
        dock = dock,
        opponentTyping = state.opponentTyping,
        onReplay = onReplay,
        opponentSpeaker = opponentSpeaker,
        learnerClipIndices = learnerClipIndices,
        onPlayLearnerClip = onPlayLearnerClip,
    )
}

/**
 * 세션 헤더 레벨 라벨(홈 히어로 문구 정합) — `<레벨 한글> · <N>턴`. 레벨 문자열은 [SessionLevel] SoT 로
 * 매핑(5티어: 매우 쉬움/쉬움/중간/어려움/매우 어려움), 미지 토큰은 SessionLevel.fromToken 폴백(NORMAL)을 따른다.
 */
private fun dialogueLevelLabel(
    level: String,
    totalTurns: Int,
): String = "${SessionLevel.fromToken(level).labelKo} · ${totalTurns}턴"

/** 테스트 전용 노출(순수 매핑 검증용). */
internal fun dialogueLevelLabelForTest(
    level: String,
    totalTurns: Int,
): String = dialogueLevelLabel(level, totalTurns)

/**
 * Couples timer-driven opponent-state mutations to their durable-state notification. Keeping this
 * separate from Compose lets a regression test drive the exact automatic completion path.
 */
internal class SessionTurnProgress(
    private val state: GeneratedDialogueState,
    private val onStateChanged: () -> Unit,
) {
    fun revealOpponentTurn() {
        state.commitReveal()
        onStateChanged()
    }

    /** 상대역 오디오가 실제 재생을 시작할 때(코디네이터 audioReady) 호출 — 표시 대기 중인 상대역 대사를
     *  표시한다. OpponentTurn 일 때만 실효해 replay·자기녹음 재생(LearnerTurn)의 재생 시작을 무시한다.
     *  commitReveal 은 멱등이라 이미 표시됐으면 append 는 no-op 다. */
    fun revealOnAudioReady() {
        if (state.turnPhase != TurnPhase.OpponentTurn) return
        state.commitReveal()
        onStateChanged()
    }

    fun completeOpponentTurn() {
        state.completeOpponentTurn()
        onStateChanged()
    }
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

    /**
     * 상대역 발화가 화면에 나타나기 **직전**의 "타이핑 중" 국면(프로토타입 oppSkeleton). 다음 상대역 대사를
     * 기다리는 동안(첫 턴 생성 대기·턴 전환 후 SSE 대기) true, [displayOpponent] 로 대사가 붙으면 false.
     * Ready 이후 스트림이 실패([DialogueStreamStatus.FailedAfterReady])하면 더 이상 대사가 오지 않으므로 즉시
     * false 로 내려가 무한 스켈레톤을 막는다. 파생 상태라 [recomputeTyping] 이 각 전이 끝에서 재계산한다
     * (스냅샷 필드 불필요 — 복원 후 재계산으로 정착).
     */
    var opponentTyping by mutableStateOf(false)
        private set

    private var consumedTurnCount = 0
    private var streamStatus = DialogueStreamStatus.Streaming
    private var pending = PendingOpponent()
    private val bufferedPending = ArrayDeque<PendingOpponent>()

    // 표시 대기 창: [displayOpponent] 가 상대역 대사를 [pending] 에 실었지만 아직 [messages] 에 append 하지
    // 않은 구간. 이 창에서 [opponentTyping]=true(스켈레톤)로, Route 가 스켈레톤 지연 경과 후 [commitReveal] 로
    // 실제 표시한다. 실기기는 대본이 미리 버퍼링돼 즉시 표시되던 것을(스켈레톤 창 0) 이 지연으로 되살린다.
    private var awaitingReveal = false

    init {
        recomputeTyping()
    }

    /**
     * 파생 typing 국면 재계산: `OpponentTurn` + 미완(`!Completed`) + 스트림이 Ready 이후 실패하지 않았고
     * (`streamStatus != FailedAfterReady`) + 아직 이번 턴 대사 미표시(`pending.opponentEnglish == null`)일
     * 때만 스켈레톤을 노출한다. 각 상태 전이 말미에서 호출한다. Ready 후 스트림이 실패해 상대역 대사가 더 이상
     * 오지 않는 경우 이 게이트가 없으면 스켈레톤이 영원히 남는다(회귀: 무한 "타이핑…").
     */
    private fun recomputeTyping() {
        opponentTyping =
            turnPhase == TurnPhase.OpponentTurn &&
            sessionPhase != SessionPhase.Completed &&
            streamStatus != DialogueStreamStatus.FailedAfterReady &&
            (pending.opponentEnglish == null || awaitingReveal)
    }

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
        commitReveal() // 표시 지연 중이면 먼저 확정(대사 유실 없이 진행).
        current.opponentComplete = true
        when {
            current.task != null -> enterLearnerTurn(current)
            streamStatus == DialogueStreamStatus.Done -> sessionPhase = SessionPhase.Completed
            else -> Unit
        }
        recomputeTyping()
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

    /** 방금 reveal 된(=messages 마지막) 상대역 영어. 발화 대상(Route TTS)으로 읽는다. 마지막이 학습자
     *  말풍선이거나 아직 아무 것도 append 안 됐으면 null. */
    fun lastOpponentEnglish(): String? = (messages.lastOrNull() as? DialogueMessage.Opponent)?.english

    /** 아직 표시 대기(awaitingReveal)인 상대역 대사 = 이번 턴 스켈레톤 뒤에서 합성/발화할 대상. Route 가
     *  오디오 준비 전 선(先)합성을 위해 읽는다(표시 전이라 messages.last 가 아니라 pending 에서 가져온다). */
    fun pendingOpponentEnglish(): String? = pending.opponentEnglish

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
        recomputeTyping()
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
            val next = PendingOpponent(opponentEnglish = turn.en, opponentKorean = turn.ko)
            if (pending.opponentEnglish == null) {
                displayOpponent(next)
            } else {
                bufferedPending.addLast(next)
            }
        } else {
            attachUserTarget(index, turn)
        }
    }

    // 상대역 대사를 **표시 대기**로 올린다(프로토타입 oppSkeleton 정합). 대사는 [pending] 에만 싣고 [messages]
    // 에는 아직 붙이지 않는다 — Route 가 스켈레톤 지연 경과 후 [commitReveal] 로 실제 표시한다. 직전 대사가 아직
    // 표시 대기였다면 먼저 확정해(유실 방지) 순서를 지킨다.
    private fun displayOpponent(next: PendingOpponent) {
        next.opponentEnglish ?: return
        commitReveal()
        pending = next
        currentTask = null
        turnPhase = TurnPhase.OpponentTurn
        sessionPhase = SessionPhase.InTurn
        awaitingReveal = true
        opponentTurnSerial += 1
        recomputeTyping()
    }

    /**
     * 스켈레톤 지연 경과 후 Route(또는 전이 초크포인트)가 호출. [displayOpponent] 로 대기 중이던 상대역 대사를
     * [messages] 에 append 하고 타이핑 창을 닫는다. 대기 중이 아니면 no-op(멱등) — completeOpponentTurn·
     * enterLearnerTurn 진입에서 방어적으로 불러 어떤 경로로도 대사 없이 학습자 턴으로 넘어가 유실되지 않게 한다.
     */
    fun commitReveal() {
        if (!awaitingReveal) return
        val english = pending.opponentEnglish
        awaitingReveal = false
        if (english != null) {
            messages = messages + DialogueMessage.Opponent(english, pending.opponentKorean.orEmpty())
        }
        recomputeTyping()
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
        commitReveal() // 학습자 턴 진입 전 상대역 대사가 반드시 표시되게(어떤 경로로도 유실 방지).
        currentTask = current.task
        turnPhase = TurnPhase.LearnerTurn
        sessionPhase = SessionPhase.InTurn
        recomputeTyping()
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
        recomputeTyping()
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
        awaitingReveal = false
        recomputeTyping()
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
            // messages는 실제로 렌더된 말풍선만 보존한다. 표시 대기 상대역 대사는 pending에 남겨 복원 시
            // 스켈레톤을 다시 거친다. 따라서 홈의 resumeInfo가 messages를 렌더 사실로 사용할 수 있다.
            messages =
                messages.map {
                    MessageData(
                        isLearner = it is DialogueMessage.Learner,
                        english = it.english,
                        korean = (it as? DialogueMessage.Opponent)?.korean.orEmpty(),
                    )
                },
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
                if (it.isLearner) {
                    DialogueMessage.Learner(it.english)
                } else {
                    DialogueMessage.Opponent(it.english, it.korean)
                }
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
        // v3에는 awaitingReveal 전용 필드가 없다. 상대역 차례에서 현재 pending 대사가 마지막 말풍선이면
        // 이미 표시된 상태이고, 아니면 pending에만 보존된 스켈레톤 상태다. 직전 턴은 학습자 말풍선으로 끝나므로
        // 이 마지막-메시지 비교는 현재 pending 대사의 표시 여부를 결정한다.
        val pendingOpponent = pending.opponentEnglish
        awaitingReveal =
            turnPhase == TurnPhase.OpponentTurn &&
                pendingOpponent != null &&
                (messages.lastOrNull() as? DialogueMessage.Opponent)?.english != pendingOpponent
        recomputeTyping()
    }

    private fun PendingOpponent.toData(): PendingData =
        PendingData(
            opponentEnglish = opponentEnglish,
            opponentKorean = opponentKorean,
            taskKo = task?.koreanPrompt,
            referenceEnglish = referenceEnglish,
            opponentComplete = opponentComplete,
        )

    private fun PendingData.toPending(): PendingOpponent =
        PendingOpponent(
            opponentEnglish = opponentEnglish,
            opponentKorean = opponentKorean,
            task = taskKo?.let { ScaffoldTask(it) },
            referenceEnglish = referenceEnglish,
            opponentComplete = opponentComplete,
        )

    private data class PendingOpponent(
        var opponentEnglish: String? = null,
        var opponentKorean: String? = null,
        var task: ScaffoldTask? = null,
        var referenceEnglish: String? = null,
        var opponentComplete: Boolean = false,
    )

    private companion object {
        const val ROLE_MODEL = "model"
        const val ROLE_USER = "user"
    }
}

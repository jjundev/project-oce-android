package com.jjundev.oneclickeng.feature.session.turn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import kotlinx.coroutines.delay

/** 현재 발화 주체(dialogue-learning-flow.md §2 `TurnPhase`). M1-03 은 이 두 값만 쓴다. */
enum class TurnPhase { OpponentTurn, LearnerTurn }

/**
 * 세션 앱바 상태(주제 아바타·제목·레벨·진행 점). 실 라우트가 주제/레벨/진행을 주입하기 전까지 seam.
 * [DialogueHeader] 가 소비한다.
 *
 * @param topicEmoji 주제 아바타 이모지(프로토타입 ☕ 정합 — 헤더 아바타는 이모지 그대로 렌더).
 * @param completedTurns 진행 점 채움 개수(0 = 첫 턴 진행 전, 전부 비움).
 */
data class DialogueHeaderState(
    val topicEmoji: String,
    val title: String,
    val levelLabel: String,
    val totalTurns: Int = 5,
    val completedTurns: Int = 0,
)

/**
 * 세션 전체 진행(dialogue-learning-flow.md §2 `SessionPhase`)의 M1-03 부분집합.
 * `Starting`/`GeneratingScript`/`SummaryPreparing` 등 나머지 값은 SSE·요약 배선 이슈에서 확장한다.
 */
enum class SessionPhase { InTurn, AwaitingStreamDone, Completed }

/** 학습자 응답 과제(한국어 발판). D1 발판 카드가 소비한다(04-screen-03-dialogue.md D1 rev2). */
data class ScaffoldTask(val koreanPrompt: String)

/** 화면 대화 기록에 렌더되는 말풍선 1개. 영어 콘텐츠에는 `LocaleList("en")`(A4) 이 적용된다. */
sealed interface DialogueMessage {
    val english: String

    /** 상대역 말풍선(좌측, `surface.card`). [korean] 은 `해석 보기` 토글용 한국어 번역(없으면 빈 문자열). */
    data class Opponent(override val english: String, val korean: String = "") : DialogueMessage

    /**
     * 학습자 말풍선(우측, `brand.primary`). M1-03 에서는 [DialogueTurn.referenceEnglish] 목표 문장을
     * 스텁 재생한 것이다(실제 사용자 입력 아님 — SampleDialogue.kt 부채 주석 참조).
     */
    data class Learner(override val english: String) : DialogueMessage
}

/** 상대역 말풍선 렌더 후 자동 진행까지의 잠정 지연(ms). */
const val DEFAULT_OPPONENT_ADVANCE_DELAY_MS: Int = 1200

/**
 * 상대역 발화가 나타나기 직전 "타이핑 스켈레톤"을 노출하는 잠정 지연(ms). 프로토타입 `oc-fade-up` 진입 감을
 * 재현하는 값으로 출처 없는 placeholder 다. reduceMotion·테스트/프리뷰는 [rememberDialogueState] 의 seam 으로
 * 0 을 주입해 스켈레톤 국면을 건너뛴다(자동 진행 결정성 유지).
 */
const val DEFAULT_OPPONENT_SKELETON_DELAY_MS: Int = 700

/**
 * M1-03 대화 턴 화면의 상태 홀더. **신규 도입** 화면 스코프 홀더로, 리포에 `remember*State` 홀더 선례는
 * 없다(루트 `AppViewModel` 만 존재). "ViewModel 은 콘텐츠 이슈에서 도입"(HomeScreen.kt:12) 규약을 따라
 * 실 데이터(SSE) 배선 전까지는 ViewModel 없이 이 홀더가 스텁 대본을 구동한다.
 *
 * **상태 전이(dialogue-learning-flow.md §5·§9):**
 * - 상대역 턴 진입 → 타이핑 스켈레톤 국면([opponentTyping]) + [TurnPhase.OpponentTurn]. 스켈레톤 지연 경과 후
 *   말풍선 append([revealOpponentTurn]).
 * - 상대역 자동 진행 지연 경과 → 학습자 과제 있으면 [TurnPhase.LearnerTurn], 없으면(마감 턴) [SessionPhase.Completed].
 * - 학습자 턴 이탈은 **[submitLearnerStub] 스텁 버튼 전용**(마이크·텍스트 입력은 M1-04/M1-06 범위 밖).
 *   목표 문장을 학습자 말풍선으로 append 후 다음 상대역 턴으로. 마지막 턴이면 [SessionPhase.Completed].
 *
 * 자동 진행 지연 자체는 [rememberDialogueState] 의 `LaunchedEffect` 가 구동한다(홀더는 순수 로직).
 */
@Stable
class DialogueState internal constructor(
    private val script: List<DialogueTurn>,
) {
    var messages by mutableStateOf<List<DialogueMessage>>(emptyList())
        private set

    var turnPhase by mutableStateOf(TurnPhase.OpponentTurn)
        private set

    var sessionPhase by mutableStateOf(SessionPhase.InTurn)
        private set

    var currentTask by mutableStateOf<ScaffoldTask?>(null)
        private set

    /**
     * 상대역 발화 append 직전의 "타이핑 중" 국면(프로토타입 스켈레톤 말풍선). [enterOpponentTurn] 이 true 로
     * 세우고, 스켈레톤 지연 경과 후 [revealOpponentTurn] 이 실제 말풍선을 append 하며 false 로 내린다. 무상태
     * `DialogueTurnContent`/프리뷰/스크린샷 테스트는 이 국면을 렌더하지 않는다(기본 false 고정 렌더).
     */
    var opponentTyping by mutableStateOf(false)
        private set

    /** 현재 대본 턴 인덱스. 자동 진행 `LaunchedEffect` 의 재시작 키로도 쓰인다. */
    var turnIndex by mutableStateOf(0)
        private set

    init {
        require(script.isNotEmpty()) { "대본은 최소 1턴 이상이어야 합니다." }
        enterOpponentTurn()
    }

    /** 자동 진행 지연 경과 후 호출. 상대역 턴을 마감하고 학습자 턴 또는 완료로 전이한다. */
    internal fun completeOpponentTurn() {
        if (turnPhase != TurnPhase.OpponentTurn || sessionPhase != SessionPhase.InTurn) return
        val task = script[turnIndex].learnerTask
        if (task != null) {
            currentTask = task
            turnPhase = TurnPhase.LearnerTurn
        } else {
            sessionPhase = SessionPhase.Completed
        }
    }

    /**
     * 학습자 턴 전진 스텁(임시). 실제 입력 독(M1-04/M1-08)으로 교체 대상. 목표 문장을 학습자 말풍선으로
     * append 하고 다음 상대역 턴 또는 완료로 넘어간다.
     */
    fun submitLearnerStub() {
        if (turnPhase != TurnPhase.LearnerTurn) return
        script[turnIndex].referenceEnglish?.let { messages = messages + DialogueMessage.Learner(it) }
        currentTask = null
        if (turnIndex + 1 < script.size) {
            turnIndex += 1
            enterOpponentTurn()
        } else {
            sessionPhase = SessionPhase.Completed
        }
    }

    /**
     * 상대역 턴 진입. 프로토타입 정합상 말풍선을 **즉시 append 하지 않고** 타이핑 스켈레톤 국면으로 들어간다.
     * 실제 말풍선은 스켈레톤 지연 경과 후 [revealOpponentTurn] 이 append 한다([rememberDialogueState] 의
     * `LaunchedEffect` 가 지연을 구동). reduceMotion·테스트는 skeleton delay=0 으로 스켈레톤을 사실상 건너뛴다.
     */
    private fun enterOpponentTurn() {
        turnPhase = TurnPhase.OpponentTurn
        opponentTyping = true
    }

    /** 스켈레톤 지연 경과 후 호출. 상대역 말풍선을 append 하고 타이핑 국면을 내린다. */
    internal fun revealOpponentTurn() {
        if (!opponentTyping || turnPhase != TurnPhase.OpponentTurn || sessionPhase != SessionPhase.InTurn) return
        messages = messages + DialogueMessage.Opponent(script[turnIndex].opponentEnglish)
        opponentTyping = false
    }
}

/**
 * 스텁 대본을 구동하는 상태 홀더를 생성/기억한다. 상대역 자동 진행 지연은 여기 `LaunchedEffect` 가 담당하며,
 * `LearnerTurn` 이탈에는 관여하지 않는다(스텁 버튼 전용).
 *
 * @param reduceMotion true 면 자동 진행을 지연 없이 즉시 처리(A7, 06-accessibility-impl.md). 테스트/프리뷰는
 *   이 seam 을 직접 주입해 반증가능하게 검증한다.
 * @param advanceDelayMs 상대역 말풍선 렌더 후 자동 진행까지 잠정 지연. 출처 없는 placeholder 로, M1-05 에서
 *   TTS 완료 게이팅(dialogue-learning-flow.md §5)으로 교체된다.
 * @param skeletonDelayMs 상대역 말풍선 append **직전** 타이핑 스켈레톤을 노출하는 잠정 지연. reduceMotion 이면
 *   0(스켈레톤 국면 즉시 통과). 테스트/프리뷰는 이 seam 을 0 으로 주입해 결정적으로 렌더한다.
 */
@Composable
fun rememberDialogueState(
    script: List<DialogueTurn>,
    reduceMotion: Boolean = rememberReduceMotion(),
    advanceDelayMs: Int = DEFAULT_OPPONENT_ADVANCE_DELAY_MS,
    skeletonDelayMs: Int = DEFAULT_OPPONENT_SKELETON_DELAY_MS,
): DialogueState {
    val state = remember(script) { DialogueState(script) }
    val effectiveAdvance = if (reduceMotion) 0L else advanceDelayMs.toLong()
    val effectiveSkeleton = if (reduceMotion) 0L else skeletonDelayMs.toLong()
    // turnIndex 키로 재시작하는 단일 결정적 시퀀스: 스켈레톤 지연 → 말풍선 reveal → 자동 진행 지연 → 턴 마감.
    // (skeleton delay=0 이면 append 는 즉시 일어나 기존 동작과 동치 — 스켈레톤 국면만 사실상 사라진다.)
    LaunchedEffect(state.turnIndex) {
        if (state.turnPhase == TurnPhase.OpponentTurn && state.sessionPhase == SessionPhase.InTurn) {
            delay(effectiveSkeleton)
            state.revealOpponentTurn()
            delay(effectiveAdvance)
            state.completeOpponentTurn()
        }
    }
    return state
}

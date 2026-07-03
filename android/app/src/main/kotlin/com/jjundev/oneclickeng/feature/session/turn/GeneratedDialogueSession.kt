package com.jjundev.oneclickeng.feature.session.turn

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jjundev.oneclickeng.core.network.DialogueTurn as NetworkDialogueTurn
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenState
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenerationCoordinator
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueStreamStatus
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Generated dialogue route (M1-01): adapts the live SSE accumulator into the M1-03 turn renderer without
 * recreating [DialogueState] from a changing script list. This keeps stream handoff append-only and makes
 * pending learner targets explicit instead of overloading `learnerTask = null`.
 */
@Composable
fun GeneratedDialogueSessionRoute(
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotion(),
    onViewSummary: () -> Unit = {},
    viewModel: GeneratedDialogueSessionViewModel = hiltViewModel(),
) {
    val generationState by viewModel.state.collectAsStateWithLifecycle()
    val generatedState = rememberGeneratedDialogueState(reduceMotion = reduceMotion)
    LaunchedEffect(generationState) {
        generatedState.accept(generationState)
    }
    GeneratedDialogueSessionContent(
        state = generatedState,
        onViewSummary = onViewSummary,
        modifier = modifier,
    )
}

@HiltViewModel
class GeneratedDialogueSessionViewModel
    @Inject
    constructor(
        coordinator: DialogueGenerationCoordinator,
    ) : ViewModel() {
        val state = coordinator.state
    }

@Composable
internal fun rememberGeneratedDialogueState(
    reduceMotion: Boolean = rememberReduceMotion(),
    advanceDelayMs: Int = DEFAULT_OPPONENT_ADVANCE_DELAY_MS,
): GeneratedDialogueState {
    val state = remember { GeneratedDialogueState() }
    val effectiveDelay = if (reduceMotion) 0L else advanceDelayMs.toLong()
    LaunchedEffect(state.opponentTurnSerial) {
        if (state.turnPhase == TurnPhase.OpponentTurn && state.sessionPhase == SessionPhase.InTurn) {
            delay(effectiveDelay)
            state.completeOpponentTurn()
        }
    }
    return state
}

@Composable
internal fun GeneratedDialogueSessionContent(
    state: GeneratedDialogueState,
    onViewSummary: () -> Unit,
    modifier: Modifier = Modifier,
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
    )
}

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

    fun submitLearnerStub() {
        if (turnPhase != TurnPhase.LearnerTurn) return
        val current = pending
        current.referenceEnglish?.let { messages = messages + DialogueMessage.Learner(it) }
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

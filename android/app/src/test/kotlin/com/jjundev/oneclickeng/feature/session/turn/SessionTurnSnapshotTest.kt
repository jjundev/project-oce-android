package com.jjundev.oneclickeng.feature.session.turn

import com.jjundev.oneclickeng.core.network.DialogueTurn as NetworkDialogueTurn
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenState
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueStreamStatus
import com.jjundev.oneclickeng.ui.audio.MicState
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun ready(
    turns: List<NetworkDialogueTurn>,
    status: DialogueStreamStatus = DialogueStreamStatus.Streaming,
) = DialogueGenState.Ready(
    sessionId = "s1",
    remaining = 2,
    meta = null,
    turns = turns,
    streamStatus = status,
)

private fun model(en: String) = NetworkDialogueTurn(ko = "상대역", en = en, role = "model")

private fun user(
    en: String,
    ko: String,
) = NetworkDialogueTurn(ko = ko, en = en, role = "user")

/** 2쌍 대본(둘째 상대역이 bufferedPending 에 쌓이는 시나리오). */
private val TWO_PAIRS =
    listOf(
        model("Hello"),
        user("A coffee, please.", "커피 주세요."),
        model("Anything else?"),
        user("No, thanks.", "괜찮아요."),
    )

class SessionTurnSnapshotTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `appendLearnerAnswer records transcript and keeps task for recap without advancing`() {
        val state = GeneratedDialogueState()
        state.accept(ready(TWO_PAIRS))
        state.completeOpponentTurn()

        state.appendLearnerAnswer("Coffee, my own words.")

        assertEquals(TurnPhase.LearnerTurn, state.turnPhase)
        assertEquals(ScaffoldTask("커피 주세요."), state.currentTask)
        assertEquals(DialogueMessage.Learner("Coffee, my own words."), state.messages.last())
        assertEquals("A coffee, please.", state.currentReferenceEnglish())
    }

    @Test
    fun `advanceTurn after real answer promotes the buffered opponent`() {
        val state = GeneratedDialogueState()
        state.accept(ready(TWO_PAIRS))
        state.completeOpponentTurn()
        state.appendLearnerAnswer("Coffee, my own words.")

        state.advanceTurn()
        state.commitReveal() // 버퍼된 다음 대사는 스켈레톤 지연 후 표시된다(표시 대기 계약).

        assertTrue(state.messages.any { it == DialogueMessage.Opponent("Anything else?") })
        assertEquals(TurnPhase.OpponentTurn, state.turnPhase)
    }

    @Test
    fun `snapshot json round-trip preserves messages phase task and buffered opponent`() {
        val original = GeneratedDialogueState()
        original.accept(ready(TWO_PAIRS))
        original.completeOpponentTurn()
        original.appendLearnerAnswer("Coffee, my own words.")

        val encoded = json.encodeToString(original.toSnapshot(MicState.Complete, TWO_PAIRS, "s1", "easy"))
        val decoded = json.decodeFromString<SessionTurnSnapshot>(encoded)
        val restored = GeneratedDialogueState().apply { restoreFrom(decoded) }

        // L1 파생 상태(실 사용자 전사 포함)가 replay 없이 그대로 복원된다.
        assertEquals(original.messages, restored.messages)
        assertEquals(original.turnPhase, restored.turnPhase)
        assertEquals(original.currentTask, restored.currentTask)
        assertEquals(MicState.Complete.name, decoded.micState)
        // v2: 세션 식별(sessionId/level)이 round-trip 으로 보존된다(크로스-프로세스 피드백 재부착).
        assertEquals("s1", decoded.sessionId)
        assertEquals("easy", decoded.level)

        // bufferedPending 생존: 전진→상대역 진행 시 버퍼된 "Anything else?" 가 살아나야 한다(D2 결함 방지).
        restored.advanceTurn()
        restored.commitReveal() // 버퍼된 다음 대사는 스켈레톤 지연 후 표시된다(표시 대기 계약).
        assertTrue(restored.messages.any { it == DialogueMessage.Opponent("Anything else?") })
        restored.completeOpponentTurn()
        assertEquals(TurnPhase.LearnerTurn, restored.turnPhase)
        assertEquals(ScaffoldTask("괜찮아요."), restored.currentTask)
    }

    @Test
    fun `L2 raw turns are retained in the snapshot as buffer of record`() {
        val state = GeneratedDialogueState()
        state.accept(ready(TWO_PAIRS))

        val snapshot = state.toSnapshot(MicState.Ready, TWO_PAIRS, sessionId = null, level = null)

        assertEquals(TWO_PAIRS.size, snapshot.turns.size)
        assertEquals("Hello", snapshot.turns.first().en)
        assertNotNull(snapshot.turns.first { it.role == "user" })
    }
}

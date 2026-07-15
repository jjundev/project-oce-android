package com.jjundev.oneclickeng.feature.session.turn

import com.jjundev.oneclickeng.core.network.DialogueTurn as NetworkDialogueTurn
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenState
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueStreamStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

private fun model(
    en: String,
    ko: String = "",
) = NetworkDialogueTurn(ko = ko, en = en, role = "model")

private fun user(
    en: String,
    ko: String = "학습자",
) = NetworkDialogueTurn(ko = ko, en = en, role = "user")

class GeneratedDialogueStateTest {
    @Test
    fun `model-only streaming state renders opponent and waits for user target`() {
        val state = GeneratedDialogueState()

        state.accept(ready(listOf(model("Hello"))))
        state.completeOpponentTurn()

        assertEquals(listOf(DialogueMessage.Opponent("Hello")), state.messages)
        assertEquals(TurnPhase.OpponentTurn, state.turnPhase)
        assertEquals(SessionPhase.InTurn, state.sessionPhase)
        assertNull(state.currentTask)
    }

    @Test
    fun `each opponent line shows a typing skeleton until it is revealed`() {
        val state = GeneratedDialogueState()
        // 세션 시작 — 첫 상대역 대사 대기 중이라 스켈레톤 노출.
        assertTrue(state.opponentTyping)

        // 현실: 백엔드가 전체 대본을 빠르게 스트리밍 → 턴들이 미리 버퍼링된다.
        state.accept(
            ready(
                listOf(
                    model("Hello"),
                    user("A coffee, please.", "커피 주세요."),
                    model("Sure, anything else?"),
                ),
            ),
        )
        // 첫 대사도 즉시 표시되지 않고 스켈레톤 창을 가진다(표시 대기 = messages 미append).
        assertTrue(state.opponentTyping)
        assertTrue(state.messages.isEmpty())

        // 스켈레톤 지연 경과(Route) → commitReveal 로 실제 표시.
        state.commitReveal()
        assertFalse(state.opponentTyping)
        assertEquals(DialogueMessage.Opponent("Hello"), state.messages.last())

        state.completeOpponentTurn()
        assertEquals(TurnPhase.LearnerTurn, state.turnPhase)
        assertFalse(state.opponentTyping)

        // 핵심(버그 수정): 다음 상대 대사가 이미 버퍼돼 있어도 즉시 표시되지 않고 스켈레톤 창을 가진다.
        state.submitLearnerStub()
        assertTrue(state.opponentTyping)
        assertEquals(DialogueMessage.Learner("A coffee, please."), state.messages.last())

        state.commitReveal()
        assertFalse(state.opponentTyping)
        assertEquals(DialogueMessage.Opponent("Sure, anything else?"), state.messages.last())
    }

    @Test
    fun `user target attaches to completed opponent and enables learner turn`() {
        val state = GeneratedDialogueState()

        state.accept(ready(listOf(model("Hello"))))
        state.completeOpponentTurn()
        state.accept(
            ready(listOf(model("Hello"), user("A coffee, please.", "커피 주세요."))),
        )

        assertEquals(TurnPhase.LearnerTurn, state.turnPhase)
        assertEquals(SessionPhase.InTurn, state.sessionPhase)
        assertEquals(ScaffoldTask("커피 주세요."), state.currentTask)
    }

    @Test
    fun `final paired user waits for Done before completing after learner submit`() {
        val state = GeneratedDialogueState()
        val turns = listOf(model("Hello"), user("A coffee, please."))

        state.accept(ready(turns))
        state.completeOpponentTurn()
        state.submitLearnerStub()

        assertEquals(SessionPhase.AwaitingStreamDone, state.sessionPhase)
        assertEquals(
            listOf(DialogueMessage.Opponent("Hello"), DialogueMessage.Learner("A coffee, please.")),
            state.messages,
        )

        state.accept(ready(turns, DialogueStreamStatus.Done))

        assertEquals(SessionPhase.Completed, state.sessionPhase)
    }

    @Test
    fun `unpaired final model only becomes closing turn after Done`() {
        val state = GeneratedDialogueState()
        val turns = listOf(model("Goodbye"))

        state.accept(ready(turns))
        state.completeOpponentTurn()
        assertEquals(SessionPhase.InTurn, state.sessionPhase)

        state.accept(ready(turns, DialogueStreamStatus.Done))

        assertEquals(SessionPhase.Completed, state.sessionPhase)
    }

    @Test
    fun `duplicate Ready snapshot does not duplicate messages`() {
        val state = GeneratedDialogueState()
        val snapshot = ready(listOf(model("Hello"), user("A coffee, please.")))

        state.accept(snapshot)
        state.accept(snapshot)

        state.commitReveal()
        assertEquals(listOf(DialogueMessage.Opponent("Hello")), state.messages)
    }

    @Test
    fun `shrinking Ready snapshot resets transcript and consumes from the beginning`() {
        val state = GeneratedDialogueState()

        state.accept(
            ready(
                listOf(
                    model("Old hello"),
                    user("Old coffee, please."),
                    model("Old anything else?"),
                ),
            ),
        )
        state.accept(ready(listOf(model("New hello"))))

        state.commitReveal()
        assertEquals(listOf(DialogueMessage.Opponent("New hello")), state.messages)
        assertEquals(TurnPhase.OpponentTurn, state.turnPhase)
        assertEquals(SessionPhase.InTurn, state.sessionPhase)
        assertNull(state.currentTask)
    }

    @Test
    fun `multiple model-user pairs buffer future opponent without losing learner target`() {
        val state = GeneratedDialogueState()
        val turns =
            listOf(
                model("Hello"),
                user("A coffee, please.", "커피 주세요."),
                model("Anything else?"),
                user("No, thanks.", "괜찮아요."),
            )

        state.accept(ready(turns))

        state.commitReveal()
        assertEquals(listOf(DialogueMessage.Opponent("Hello")), state.messages)

        state.completeOpponentTurn()

        assertEquals(TurnPhase.LearnerTurn, state.turnPhase)
        assertEquals(ScaffoldTask("커피 주세요."), state.currentTask)

        state.submitLearnerStub()
        // 전진 시 버퍼된 다음 대사는 표시 대기 → 스켈레톤 지연 경과(commitReveal) 후 표시된다.
        state.commitReveal()

        assertEquals(
            listOf(
                DialogueMessage.Opponent("Hello"),
                DialogueMessage.Learner("A coffee, please."),
                DialogueMessage.Opponent("Anything else?"),
            ),
            state.messages,
        )
        assertEquals(TurnPhase.OpponentTurn, state.turnPhase)
        assertNull(state.currentTask)

        state.completeOpponentTurn()

        assertEquals(TurnPhase.LearnerTurn, state.turnPhase)
        assertEquals(ScaffoldTask("괜찮아요."), state.currentTask)

        state.submitLearnerStub()

        assertEquals(
            listOf(
                DialogueMessage.Opponent("Hello"),
                DialogueMessage.Learner("A coffee, please."),
                DialogueMessage.Opponent("Anything else?"),
                DialogueMessage.Learner("No, thanks."),
            ),
            state.messages,
        )
        assertEquals(SessionPhase.AwaitingStreamDone, state.sessionPhase)

        state.accept(ready(turns, DialogueStreamStatus.Done))

        assertEquals(SessionPhase.Completed, state.sessionPhase)
    }

    @Test
    fun `unexpected role is diagnosed and ignored`() {
        val state = GeneratedDialogueState()

        state.accept(ready(listOf(user("I should not be first"))))

        assertTrue(state.messages.isEmpty())
        assertEquals("unexpected_role:0:user", state.diagnostic)
    }

    @Test
    fun `stream failure after ready clears the typing skeleton`() {
        val state = GeneratedDialogueState()
        assertTrue(state.opponentTyping) // 첫 상대역 대사 생성 대기 중

        // 대사가 오기 전에 스트림이 실패로 종료 → 무한 스켈레톤 방지(typing 해제).
        state.accept(ready(emptyList(), DialogueStreamStatus.FailedAfterReady))

        assertFalse(state.opponentTyping)
    }

    @Test
    fun `lastOpponentEnglish returns revealed opponent line and null after learner reply`() {
        val state = GeneratedDialogueState()
        state.accept(ready(listOf(model("Hello"), user("A coffee, please.", "커피 주세요."))))

        // reveal 전에는 아직 messages 에 없음.
        assertNull(state.lastOpponentEnglish())

        state.commitReveal()
        assertEquals("Hello", state.lastOpponentEnglish())

        // 학습자 답변이 마지막이면 상대역 라인이 아니므로 null.
        state.completeOpponentTurn()
        state.appendLearnerAnswer("A coffee, please.")
        assertNull(state.lastOpponentEnglish())
    }

    @Test
    fun `pendingOpponentEnglish exposes the staged line before and after reveal`() {
        val state = GeneratedDialogueState()
        state.accept(ready(listOf(model("Hello"))))

        assertTrue(state.opponentTyping) // typing skeleton, not revealed
        assertTrue(state.messages.isEmpty())
        assertEquals("Hello", state.pendingOpponentEnglish())

        state.commitReveal()
        assertEquals("Hello", state.pendingOpponentEnglish()) // pending is retained after reveal
    }

    @Test
    fun `revealOnAudioReady reveals the staged opponent line during an opponent turn`() {
        val state = GeneratedDialogueState()
        state.accept(ready(listOf(model("Hello"))))
        var changes = 0
        val progress = SessionTurnProgress(state) { changes++ }

        progress.revealOnAudioReady()

        assertFalse(state.opponentTyping)
        assertEquals(DialogueMessage.Opponent("Hello"), state.messages.last())
        assertEquals(1, changes)
    }

    @Test
    fun `revealOnAudioReady is a no-op during a learner turn`() {
        val state = GeneratedDialogueState()
        state.accept(ready(listOf(model("Hello"))))
        state.completeOpponentTurn()
        state.accept(ready(listOf(model("Hello"), user("A coffee, please.", "커피 주세요."))))
        assertEquals(TurnPhase.LearnerTurn, state.turnPhase)
        var changes = 0
        val progress = SessionTurnProgress(state) { changes++ }

        progress.revealOnAudioReady()

        assertEquals(0, changes) // guarded out — no reveal, no persist
    }

    @Test
    fun `opponent line carries its Korean translation into the revealed message`() {
        val state = GeneratedDialogueState()
        state.accept(ready(listOf(model("Hello", ko = "안녕하세요"))))
        state.commitReveal()

        assertEquals(
            DialogueMessage.Opponent("Hello", "안녕하세요"),
            state.messages.last(),
        )
    }
}

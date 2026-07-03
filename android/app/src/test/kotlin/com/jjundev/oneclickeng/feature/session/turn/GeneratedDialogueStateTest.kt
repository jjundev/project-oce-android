package com.jjundev.oneclickeng.feature.session.turn

import com.jjundev.oneclickeng.core.network.DialogueTurn as NetworkDialogueTurn
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenState
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueStreamStatus
import org.junit.Assert.assertEquals
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
    ko: String = "상대역",
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

        assertEquals(listOf(DialogueMessage.Opponent("Hello")), state.messages)

        state.completeOpponentTurn()

        assertEquals(TurnPhase.LearnerTurn, state.turnPhase)
        assertEquals(ScaffoldTask("커피 주세요."), state.currentTask)

        state.submitLearnerStub()

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
}

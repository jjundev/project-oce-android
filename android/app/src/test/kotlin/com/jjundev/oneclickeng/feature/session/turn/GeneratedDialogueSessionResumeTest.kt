package com.jjundev.oneclickeng.feature.session.turn

import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenState
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueStreamStatus
import com.jjundev.oneclickeng.core.network.DialogueTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedDialogueSessionResumeTest {
    private fun snapshot(sessionId: String) =
        SessionTurnSnapshot(
            sessionId = sessionId,
            level = "easy",
            messages =
                listOf(
                    MessageData(isLearner = false, english = "Opening"),
                    MessageData(isLearner = true, english = "My answer"),
                    MessageData(isLearner = false, english = "Next question"),
                ),
            turnPhase = TurnPhase.OpponentTurn.name,
            sessionPhase = SessionPhase.InTurn.name,
            currentTaskKo = null,
            consumedTurnCount = 3,
            opponentTurnSerial = 2,
            pending = PendingData(opponentEnglish = "Next question"),
            bufferedPending = emptyList(),
            streamStatus = DialogueStreamStatus.Streaming.name,
            diagnostic = null,
            micState = "Ready",
            turns = emptyList(),
        )

    @Test
    fun `same live Ready session restores durable progress instead of replaying`() {
        val live =
            DialogueGenState.Ready(
                sessionId = "session-1",
                remaining = 2,
                meta = null,
                turns = emptyList(),
                streamStatus = DialogueStreamStatus.Streaming,
            )

        assertTrue(shouldRestoreDurableSnapshot(snapshot("session-1"), live))
    }

    @Test
    fun `durable snapshot from another session does not override a fresh Ready generation`() {
        val live =
            DialogueGenState.Ready(
                sessionId = "fresh-session",
                remaining = 5,
                meta = null,
                turns = emptyList(),
                streamStatus = DialogueStreamStatus.Streaming,
            )

        assertFalse(shouldRestoreDurableSnapshot(snapshot("old-session"), live))
    }

    @Test
    fun `same-session restore keeps the third bubble instead of replaying from the first`() {
        val live =
            DialogueGenState.Ready(
                sessionId = "session-1",
                remaining = 2,
                meta = null,
                turns =
                    listOf(
                        DialogueTurn(ko = "첫 과제", en = "Opening", role = "model"),
                        DialogueTurn(ko = "첫 답변", en = "Reference", role = "user"),
                        DialogueTurn(ko = "다음 질문", en = "Next question", role = "model"),
                    ),
                streamStatus = DialogueStreamStatus.Streaming,
            )
        val restored = GeneratedDialogueState().apply { restoreFrom(snapshot("session-1")) }

        if (shouldRestoreDurableSnapshot(snapshot("session-1"), live)) {
            restored.accept(live)
        }

        assertEquals(
            listOf(
                DialogueMessage.Opponent("Opening"),
                DialogueMessage.Learner("My answer"),
                DialogueMessage.Opponent("Next question"),
            ),
            restored.messages,
        )
    }
}

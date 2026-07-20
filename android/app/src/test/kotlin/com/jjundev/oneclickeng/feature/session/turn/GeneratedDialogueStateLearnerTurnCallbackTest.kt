package com.jjundev.oneclickeng.feature.session.turn

import com.jjundev.oneclickeng.core.network.DialogueTurn as NetworkDialogueTurn
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenState
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueStreamStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class GeneratedDialogueStateLearnerTurnCallbackTest {
    private fun model(en: String, ko: String = "") = NetworkDialogueTurn(ko = ko, en = en, role = "model")
    private fun user(en: String, ko: String = "학습자") = NetworkDialogueTurn(ko = ko, en = en, role = "user")

    @Test
    fun `entering the learner turn invokes onEnterLearnerTurn exactly once`() {
        var count = 0
        val state = GeneratedDialogueState().apply { onEnterLearnerTurn = { count++ } }

        state.accept(
            DialogueGenState.Ready(
                sessionId = "s1",
                remaining = 2,
                meta = null,
                turns = listOf(model("Hello"), user("A coffee, please.", "커피 주세요."), model("Sure?")),
                streamStatus = DialogueStreamStatus.Streaming,
            ),
        )
        state.commitReveal()
        state.completeOpponentTurn() // OpponentTurn -> LearnerTurn

        assertEquals(TurnPhase.LearnerTurn, state.turnPhase)
        assertEquals(1, count)
    }
}

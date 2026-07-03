package com.jjundev.oneclickeng.feature.session.turn

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jjundev.oneclickeng.core.network.DialogueTurn as NetworkDialogueTurn
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenState
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueStreamStatus
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test

private fun ready(
    turns: List<NetworkDialogueTurn>,
) = DialogueGenState.Ready(
    sessionId = "generated-smoke",
    remaining = 1,
    meta = null,
    turns = turns,
    streamStatus = DialogueStreamStatus.Streaming,
)

private fun model(en: String) = NetworkDialogueTurn(ko = "상대역", en = en, role = "model")

private fun user(
    ko: String,
    en: String = "A coffee, please.",
) = NetworkDialogueTurn(ko = ko, en = en, role = "user")

class GeneratedDialogueSessionContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun generatedContentRendersOpponentAndLearnerTaskWithoutHilt() {
        val state =
            GeneratedDialogueState().apply {
                accept(
                    ready(
                        listOf(
                            model("Welcome in. What would you like today?"),
                            user("따뜻한 아메리카노 한 잔 주세요."),
                        ),
                    ),
                )
                completeOpponentTurn()
            }

        composeRule.setContent {
            OceTheme {
                GeneratedDialogueSessionContent(
                    state = state,
                    onViewSummary = {},
                )
            }
        }

        composeRule.onNodeWithText("Welcome in. What would you like today?").assertIsDisplayed()
        composeRule.onNodeWithText("따뜻한 아메리카노 한 잔 주세요.").assertIsDisplayed()
    }
}

package com.jjundev.oneclickeng.feature.session.turn

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import com.jjundev.oneclickeng.core.network.DialogueTurn as NetworkDialogueTurn
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenState
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueStreamStatus
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 상대역 말풍선은 스켈레톤을 최소 [DEFAULT_OPPONENT_SKELETON_FLOOR_MS] 노출한 뒤에만 대사 합성/발화를
 * 시작한다. 반증가능: dwell 경과 직전엔 발화 0회, 경과 시점에 정확히 1회.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class OpponentSkeletonFloorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `opponent speak is deferred until the skeleton floor elapses`() {
        composeRule.mainClock.autoAdvance = false
        var speakCount = 0
        val state =
            GeneratedDialogueState().apply {
                accept(
                    DialogueGenState.Ready(
                        sessionId = "s1",
                        remaining = 1,
                        meta = null,
                        turns = listOf(NetworkDialogueTurn(ko = "안녕", en = "Hello", role = "model")),
                        streamStatus = DialogueStreamStatus.Streaming,
                    ),
                )
            }

        composeRule.setContent {
            OceTheme {
                Surface {
                    GeneratedDialogueSessionContent(
                        state = state,
                        onViewSummary = {},
                        minSkeletonMs = DEFAULT_OPPONENT_SKELETON_FLOOR_MS,
                        onSpeakOpponent = { speakCount += 1 },
                    )
                }
            }
        }

        val base = composeRule.mainClock.currentTime
        advanceTo(base + DEFAULT_OPPONENT_SKELETON_FLOOR_MS - 1)
        composeRule.runOnIdle { assertEquals(0, speakCount) }

        advanceTo(base + DEFAULT_OPPONENT_SKELETON_FLOOR_MS)
        composeRule.runOnIdle { assertEquals(1, speakCount) }
    }

    private fun advanceTo(targetTimeMs: Long) {
        composeRule.mainClock.advanceTimeBy(
            targetTimeMs - composeRule.mainClock.currentTime,
            ignoreFrameDuration = true,
        )
    }
}

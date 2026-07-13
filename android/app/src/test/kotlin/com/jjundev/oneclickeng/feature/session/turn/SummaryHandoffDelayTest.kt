package com.jjundev.oneclickeng.feature.session.turn

import android.app.Application
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 세션 완료 → 요약 자동 이동은 완료 상태를 [SUMMARY_HANDOFF_DELAY_MS] 노출한 뒤에만 발화한다(1초 대기).
 * 반증가능: 대기 경과 직전엔 콜백 0회, 경과 시점에 정확히 1회.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class SummaryHandoffDelayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `completed handoff fires only after the delay`() {
        composeRule.mainClock.autoAdvance = false
        var summaryCount = 0
        composeRule.setContent {
            OceTheme {
                Surface {
                    DialogueTurnContent(
                        messages = emptyList(),
                        turnPhase = TurnPhase.OpponentTurn,
                        sessionPhase = SessionPhase.Completed,
                        currentTask = null,
                        listState = rememberLazyListState(),
                        onSubmitStub = {},
                        onViewSummary = { summaryCount += 1 },
                    )
                }
            }
        }

        val base = composeRule.mainClock.currentTime
        advanceTo(base + SUMMARY_HANDOFF_DELAY_MS - 1)
        composeRule.runOnIdle { assertEquals(0, summaryCount) }

        advanceTo(base + SUMMARY_HANDOFF_DELAY_MS)
        composeRule.runOnIdle { assertEquals(1, summaryCount) }
    }

    private fun advanceTo(targetTimeMs: Long) {
        composeRule.mainClock.advanceTimeBy(
            targetTimeMs - composeRule.mainClock.currentTime,
            ignoreFrameDuration = true,
        )
    }
}

package com.jjundev.oneclickeng.feature.session.turn

import android.app.Application
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 상대역 말풍선 `해석 보기` 토글: 탭하면 본문이 영문→한국어로 교체되고 라벨이 `원문 보기` 로 뒤집힌다
 * (프로토타입 정합 — 병기 아닌 교체). 다시 탭하면 원문으로 복귀.
 *
 * `GraphicsMode.NATIVE`(리포 관례 — RecordsDeleteDialogTest·AppExitGuardTest·DialogueExitGuardTest 동일):
 * `OpponentBubble` 말풍선 Row 의 `clip`+`background`+`border` 체인이 있는 상태에서 Robolectric 기본
 * LEGACY 그래픽스 모드로는 `performClick()` 이후 `LazyColumn` 아이템 재구성이 실제로 관측되지 않는다
 * (탭이 씹힘 — 실측: 이 조합을 격리한 최소 재현에서 NATIVE 없이는 라벨/본문이 갱신되지 않음).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class DialogueTranslationToggleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tapping the interpretation toggle swaps English to Korean and flips the label`() {
        composeRule.setContent {
            OceTheme {
                Surface {
                    DialogueTurnContent(
                        messages = listOf(DialogueMessage.Opponent("Hello there!", "안녕하세요!")),
                        turnPhase = TurnPhase.OpponentTurn,
                        sessionPhase = SessionPhase.InTurn,
                        currentTask = null,
                        listState = rememberLazyListState(),
                        onSubmitStub = {},
                        onViewSummary = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Hello there!").assertIsDisplayed()
        composeRule.onNodeWithText("해석 보기").assertIsDisplayed()

        composeRule.onNodeWithText("해석 보기").performClick()

        composeRule.onNodeWithText("안녕하세요!").assertIsDisplayed()
        composeRule.onNodeWithText("원문 보기").assertIsDisplayed()

        composeRule.onNodeWithText("원문 보기").performClick()
        composeRule.onNodeWithText("Hello there!").assertIsDisplayed()
    }
}

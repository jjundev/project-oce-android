package com.jjundev.oneclickeng.feature.session.turn

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 스피커 버튼(`ReplayButton`)의 "재생 중" 시각 상태: [OpponentTurn]/[LearnerTurn] 의 `isPlaying` 이 true 면
 * 아이콘·contentDescription 이 "다시 듣기"(VolumeUp)에서 "재생 중지"(GraphicEq)로 바뀐다(A2 비색 신호 —
 * 아이콘 모양 자체가 바뀌므로 색만으로 상태를 구분하지 않는다). 탭 동작 자체(onReplay 호출)는 이 버튼이
 * 아니라 ViewModel 이 토글하므로, 여기서는 재생 중이어도 탭하면 그대로 onReplay 가 불린다는 것만 검증한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ChatBubbleReplayButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `opponent bubble shows the replay label when idle`() {
        composeRule.setContent {
            OceTheme { OpponentTurn(text = "Hi there!", isPlaying = false, onReplay = {}) }
        }
        composeRule.onNodeWithContentDescription("다시 듣기").assertExists()
    }

    @Test
    fun `opponent bubble swaps to the stop label while playing`() {
        composeRule.setContent {
            OceTheme { OpponentTurn(text = "Hi there!", isPlaying = true, onReplay = {}) }
        }
        composeRule.onNodeWithContentDescription("재생 중지").assertExists()
    }

    @Test
    fun `tapping the opponent speaker button invokes onReplay regardless of playing state`() {
        var tapCount = 0
        composeRule.setContent {
            OceTheme { OpponentTurn(text = "Hi there!", isPlaying = true, onReplay = { tapCount++ }) }
        }
        composeRule.onNodeWithContentDescription("재생 중지").performClick()
        assertEquals(1, tapCount)
    }

    @Test
    fun `learner clip button shows the replay label when idle`() {
        composeRule.setContent {
            OceTheme {
                LearnerTurn(text = "My answer", hasAudio = true, isPlaying = false, onReplay = {})
            }
        }
        composeRule.onNodeWithContentDescription("다시 듣기").assertExists()
    }

    @Test
    fun `learner clip button swaps to the stop label while playing`() {
        composeRule.setContent {
            OceTheme {
                LearnerTurn(text = "My answer", hasAudio = true, isPlaying = true, onReplay = {})
            }
        }
        composeRule.onNodeWithContentDescription("재생 중지").assertExists()
    }
}

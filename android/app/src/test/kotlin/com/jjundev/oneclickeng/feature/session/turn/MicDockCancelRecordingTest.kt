package com.jjundev.oneclickeng.feature.session.turn

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.jjundev.oneclickeng.ui.audio.MicState
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 녹음 중("다시 말하기") 취소 어피던스 검증. Recording 상태에서는 하단 토글이 "채팅으로 입력하기" 대신
 * "다시 말하기"로 바뀌고, 탭하면 [onCancelRecording] 콜백이 호출돼야 한다(다른 상태는 기존 문구 유지).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class MicDockCancelRecordingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val task = ScaffoldTask("라떼 한 잔을 주문해보세요")
    private val waveform = MutableStateFlow(FloatArray(0))

    private fun setDock(micState: MicState, onCancelRecording: () -> Unit) {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                MicDock(
                    task = task,
                    micState = micState,
                    waveform = waveform,
                    textMode = false,
                    textValue = "",
                    retryHint = null,
                    permanentlyDenied = false,
                    reduceMotion = true,
                    onMicTap = {},
                    onAdvance = {},
                    onCancelRecording = onCancelRecording,
                    onToggleTextMode = {},
                    onTextChange = {},
                    onSubmitText = {},
                )
            }
        }
    }

    @Test
    fun recording_state_shows_cancel_label_instead_of_chat_toggle() {
        setDock(MicState.Recording, onCancelRecording = {})

        composeRule.onNodeWithText("다시 말하기", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("채팅으로 입력하기", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun tapping_cancel_label_invokes_callback() {
        var cancelCount = 0
        setDock(MicState.Recording, onCancelRecording = { cancelCount++ })

        composeRule.onNodeWithText("다시 말하기", useUnmergedTree = true).performClick()

        assertEquals(1, cancelCount)
    }

    @Test
    fun ready_state_keeps_chat_toggle() {
        setDock(MicState.Ready, onCancelRecording = {})

        composeRule.onNodeWithText("채팅으로 입력하기", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("다시 말하기", useUnmergedTree = true).assertDoesNotExist()
    }
}

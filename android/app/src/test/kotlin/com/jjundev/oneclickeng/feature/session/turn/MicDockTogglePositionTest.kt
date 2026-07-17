package com.jjundev.oneclickeng.feature.session.turn

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
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
 * "채팅으로 입력하기"(마이크 모드)와 "마이크로 말하기"(텍스트 모드) 토글이 화면 하단에서 동일한 위치에 오는지
 * 검증. 두 도크를 같은 높이(600dp)의 바텀-정착 박스에 나란히 렌더하면, 두 토글은 각 도크의 마지막 자식이므로
 * 스타일이 통일되면 root 기준 bottom Y 가 같아야 한다. 통일 전엔 마이크 모드 토글이 Top-정렬+48dp 라 위로 떠
 * 값이 어긋난다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class MicDockTogglePositionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val task = ScaffoldTask("라떼 한 잔을 주문해보세요")
    private val waveform = MutableStateFlow(FloatArray(0))

    @Test
    fun input_mode_toggles_share_bottom_offset() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(false, true).forEach { textMode ->
                        Box(
                            modifier = Modifier.weight(1f).height(600.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            MicDock(
                                task = task,
                                micState = MicState.Ready,
                                waveform = waveform,
                                textMode = textMode,
                                textValue = "",
                                retryHint = null,
                                permanentlyDenied = false,
                                reduceMotion = true,
                                onMicTap = {},
                                onAdvance = {},
                                onCancelRecording = {},
                                onToggleTextMode = {},
                                onTextChange = {},
                                onSubmitText = {},
                            )
                        }
                    }
                }
            }
        }

        // useUnmergedTree: clickable() 가 semantics 를 병합해 onNodeWithText 가 기본적으로 Row 전체(터치
        // 타깃) bounds 를 반환한다 — Row 자체는 두 모드 다 마지막 자식이라 항상 같은 위치라, 실제 육안으로
        // 보이는 "점프"(Row 내부에서 아이콘+텍스트가 상단/중앙 중 어디에 놓이는지)를 이 값으로는 잡지 못한다.
        // 텍스트 노드 자체의 위치를 봐야 하므로 병합 트리를 끄고 조회한다.
        val chatBottom =
            composeRule.onNodeWithText("채팅으로 입력하기", useUnmergedTree = true).getUnclippedBoundsInRoot().bottom
        val micBottom =
            composeRule.onNodeWithText("마이크로 말하기", useUnmergedTree = true).getUnclippedBoundsInRoot().bottom
        // 같은 높이·바텀 정착 박스의 마지막 자식 → 통일되면 두 토글의 root 기준 하단 Y 가 일치해야 한다.
        assertEquals(chatBottom.value, micBottom.value, 0.5f)
    }
}

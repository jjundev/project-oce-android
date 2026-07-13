package com.jjundev.oneclickeng.feature.settings

import android.app.Application
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 회귀: 신형 [ReminderTimeSheet] 는 드래그로 크기 조절·이동이 불가능하다.
 * 핵심 행위 테스트 = 비스크롤 헤더를 크게 아래로 스와이프해도 시트가 닫히거나 화면 밖으로 밀리지 않는다
 * (닫혔다면 "설정" 버튼이 뷰포트를 벗어나 assertIsDisplayed 실패). 보조 = 드래그 핸들이 부여하는
 * [SemanticsActions.Dismiss] 시맨틱 부재(dragHandle = null 적용됨).
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class ReminderTimeSheetDragHandleTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setSheet() {
        composeRule.setContent {
            OceTheme {
                ReminderTimeSheet(
                    initialHour = 20,
                    initialMinute = 0,
                    onConfirm = { _, _ -> },
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    // 핵심: 헤더를 아래로 크게 스와이프해도 드래그가 막혀 시트가 유지된다.
    @Test
    fun dragging_the_header_down_does_not_dismiss_or_move_the_sheet() {
        setSheet()
        composeRule.onNodeWithText("설정").assertIsDisplayed()

        // 헤더("리마인더 시간", 비스크롤 영역)에서 시작해 한참 아래로 스와이프.
        composeRule.onNodeWithText("리마인더 시간").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 1200f, durationMillis = 200)
        }
        composeRule.waitForIdle()

        // 드래그가 막혔으면 시트는 그대로 → 확인 버튼이 여전히 뷰포트에 보인다.
        composeRule.onNodeWithText("설정").assertIsDisplayed()
    }

    // 보조: 기능적 M3 드래그 핸들 부재 → Dismiss 시맨틱이 없다(상단 장식용 그래버는 시맨틱을 안 부여함).
    @Test
    fun sheet_has_no_functional_drag_handle_dismiss_action() {
        setSheet()
        composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .assertCountEquals(0)
    }

    // 상단 장식용 그래버 바가 표시된다(비기능적 — 드래그는 여전히 막힘).
    @Test
    fun sheet_shows_a_decorative_grabber_handle() {
        setSheet()
        composeRule.onNodeWithTag(GRABBER_TEST_TAG).assertIsDisplayed()
    }
}

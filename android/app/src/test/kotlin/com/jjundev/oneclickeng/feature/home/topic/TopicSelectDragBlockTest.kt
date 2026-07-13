package com.jjundev.oneclickeng.feature.home.topic

import android.app.Application
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 회귀: "상황 고르기"([TopicSelectSheet]) 는 드래그로 크기 조절·이동이 불가능하다.
 * 핵심 = 비스크롤 헤더를 크게 아래로 스와이프해도 시트가 닫히거나 밀리지 않는다(닫혔다면 "상황 고르기"
 * 헤더가 뷰포트를 벗어나 assertIsDisplayed 실패). 보조 = 드래그 핸들이 부여하는
 * [SemanticsActions.Dismiss] 시맨틱 부재(dragHandle = null 적용됨).
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class TopicSelectDragBlockTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setSheet() {
        composeRule.setContent {
            OceTheme {
                TopicSelectSheet(
                    onTopicChosen = { _, _ -> },
                    onDismiss = {},
                    topics = testTopics,
                )
            }
        }
        composeRule.waitForIdle()
    }

    // 핵심: 헤더를 아래로 크게 스와이프해도 드래그가 막혀 시트가 유지된다.
    @Test
    fun dragging_the_header_down_does_not_dismiss_or_move_the_sheet() {
        setSheet()
        composeRule.onNodeWithText("상황 고르기").assertIsDisplayed()

        composeRule.onNodeWithText("상황 고르기").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 1200f, durationMillis = 200)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("상황 고르기").assertIsDisplayed()
    }

    // 보조: 드래그 핸들 부재 → 핸들이 부여하는 Dismiss 시맨틱이 없다.
    @Test
    fun sheet_has_no_drag_handle_dismiss_action() {
        setSheet()
        composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .assertCountEquals(0)
    }
}

private val testTopics =
    listOf(
        Topic(
            id = "cafe-order",
            emoji = "☕",
            titleKo = "카페에서 주문하기",
            group = TopicGroup.Daily,
            beginnerFriendly = true,
            promptSeed = "ordering at a café",
            icon = OceIcon.LocalCafe,
        ),
    )

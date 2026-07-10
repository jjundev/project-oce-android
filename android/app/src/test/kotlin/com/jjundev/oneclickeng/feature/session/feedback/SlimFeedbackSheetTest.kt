package com.jjundev.oneclickeng.feature.session.feedback

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.jjundev.oneclickeng.ui.component.RichSegment
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 풋터 계약: `Active`·펼치기 전에는 `더 보기`와 `다음` 둘 다 고정 풋터에 함께 있어야 한다(항상 도달 가능).
 * 슬림 시트는 프로덕션에서 별도 윈도가 아니므로 [SlimFeedbackContent] 를 직접 렌더해 노드 트리를 검증한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class SlimFeedbackSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun active(): SlimFeedbackState.Active =
        SlimFeedbackState.Active(
            header = RecapHeader(koreanPrompt = "라떼 한 잔을 주문해보세요", userText = "Can I get a latte?"),
            writingScore = SectionState.Ready(WritingScore(score = 92, encouragement = "좋아요")),
            grammar = SectionState.Ready(Grammar(listOf(RichSegment.Normal("Can I get a latte?")), "정확해요")),
            natural =
                SectionState.Ready(
                    NaturalExpression(listOf(RichSegment.Normal("Can I get a latte?")), Reason("자연스러움", "이미 자연스러워요")),
                ),
        )

    @Test
    fun footer_holds_both_more_toggle_and_next() {
        composeRule.setContent {
            OceTheme { SlimFeedbackContent(state = active(), onRetry = {}, onSkip = {}, onNext = {}) }
        }

        // 두 어포던스 모두 컴포즈되고 표시됨.
        composeRule.onNodeWithText("더 보기").assertIsDisplayed()
        composeRule.onNodeWithText("다음").assertIsDisplayed()

        // 고정 풋터가 "더 보기"와 "다음"을 함께 담는다.
        composeRule.onNode(hasTestTag("slim_footer") and hasAnyDescendant(hasText("더 보기"))).assertExists()
        composeRule.onNode(hasTestTag("slim_footer") and hasAnyDescendant(hasText("다음"))).assertExists()
    }
}

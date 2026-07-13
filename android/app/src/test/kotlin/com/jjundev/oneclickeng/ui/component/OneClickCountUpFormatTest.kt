package com.jjundev.oneclickeng.ui.component

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [OneClickCountUp] 의 [format] 람다는 애니메이션 텍스트와 a11y 최종 라벨 모두에 적용된다.
 * reduceMotion 로 즉시 스냅시켜 최종값(135분 → "2시간 15분")이 contentDescription 으로 노출되는지 본다
 * (프리미티브가 clearAndSetSemantics 로 최종 라벨을 contentDescription 에 싣는다).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OneClickCountUpFormatTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun format_lambda_applies_to_final_label() {
        composeRule.setContent {
            OceTheme {
                OneClickCountUp(
                    target = 135,
                    format = { "${it / 60}시간 ${it % 60}분" },
                    reduceMotion = true,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("2시간 15분").assertIsDisplayed()
    }
}

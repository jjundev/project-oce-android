package com.jjundev.oneclickeng.feature.session.turn

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 회귀: totalTurns 가 MAX_PROGRESS_DOTS(8) 초과 시 점-그리드 대신 "n / N" 수치 표기로 전환된다.
 * (10 개 점은 48dp 고정 높이 헤더에서 넘침 — Task 11)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DialogueHeaderProgressTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `renders numeric progress when turns exceed dot cap`() {
        compose.setContent {
            OceTheme {
                DialogueHeader(
                    state = DialogueHeaderState(
                        topicEmoji = "☕", title = "카페", levelLabel = "중간 · 10턴",
                        totalTurns = 10, completedTurns = 3,
                    ),
                )
            }
        }
        compose.onNodeWithText("3 / 10").assertIsDisplayed()
    }
}

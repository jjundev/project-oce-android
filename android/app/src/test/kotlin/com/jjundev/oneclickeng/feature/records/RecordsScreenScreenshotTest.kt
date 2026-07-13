package com.jjundev.oneclickeng.feature.records

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.feature.session.saved.CardType
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 기록 탭 스크린샷 캡처(프로토타입 대조 파일럿). [RecordsContent] 를 VM 없이 고정 상태로 강제 렌더한다.
 * 상태 값은 프로토타입 "기록" 화면 참조와 맞춰 구성(평생통계 240XP·1시간8분·12일, 표현 카드).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class RecordsScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun records_light_expression() {
        capture(name = "records_light_expression", dark = false)
    }

    @Test
    fun records_dark_expression() {
        capture(name = "records_dark_expression", dark = true)
    }

    private fun capture(name: String, dark: Boolean) {
        val state =
            RecordsUiState(
                selected = CardType.EXPRESSION,
                cards =
                    listOf(
                        entry("1", "커피 한 잔 주문하기", "I want a coffee.", "Could I get a latte, please?"),
                        entry("2", "매장에서 먹기", "For here eat.", "For here, please."),
                        entry("3", "영수증 받기", "Give me receipt.", "Could I get the receipt?"),
                    ),
                loading = false,
                endReached = true,
                lifetime = LifetimeStats(xp = 240, studyMinutes = 68, studyDays = 12),
                animateCountUp = false,
            )
        composeRule.setContent {
            OceTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RecordsContent(
                        state = state,
                        onSelectTab = {},
                        onDelete = {},
                        onLoadMore = {},
                        reduceMotion = true,
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun entry(
        id: String,
        koreanPrompt: String,
        before: String,
        after: String,
    ): SavedCardEntry =
        SavedCardEntry(
            cardId = id,
            card =
                SavedCard.Expression(
                    type = "natural",
                    koreanPrompt = koreanPrompt,
                    before = before,
                    after = after,
                    explanation = "더 자연스러운 표현이에요.",
                ),
        )
}

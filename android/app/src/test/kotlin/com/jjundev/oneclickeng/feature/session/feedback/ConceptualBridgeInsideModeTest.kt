package com.jjundev.oneclickeng.feature.session.feedback

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 짧은 뜻은 원 안(INSIDE)에 그려지고 아래 레전드는 렌더되지 않아야 한다. INSIDE 아이템은 canvas drawText
 * (비-semantics)라 Text 노드가 아니다 → 짧은 뜻이 Text 노드로 존재하지 않으면 레전드 미표시(INSIDE) 확정.
 * (긴 뜻 → LEGEND 는 ConceptualBridgeLegendTest 가 커버.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class ConceptualBridgeInsideModeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun ready(): DeepFeedbackState.Ready =
        DeepFeedbackState.Ready(
            conceptualBridge =
                ConceptualBridge(
                    literalTranslation = "커피 하나요.",
                    explanation = "조금 더 공손하게 표현할 수 있어요.",
                    venn =
                        VennData(
                            guide = "두 단어의 차이를 볼까요?",
                            left = VennCircle(word = "get", items = listOf("얻다")),
                            right = VennCircle(word = "order", items = listOf("주문")),
                            intersectionItems = listOf("받다"),
                        ),
                ),
            toneStyle =
                ToneStyle(
                    defaultLevel = 0,
                    levels = listOf(ToneLevel(0, "Can I get a coffee?", "커피 한 잔 주세요.")),
                ),
            paraphrasing =
                Paraphrasing(items = listOf(Paraphrase(1, "Beginner", "A coffee, please.", "커피 한 잔이요."))),
        )

    @Test
    fun short_meanings_render_inside_not_as_legend_text() {
        composeRule.setContent {
            OceTheme {
                DeepFeedbackRegion(
                    state = ready(),
                    onRetry = {},
                    bookmarkedLevels = emptySet(),
                    onToggleBookmark = {},
                )
            }
        }
        // 짧은 뜻은 canvas(INSIDE)로만 그려지므로 legend Text 노드로 존재하지 않는다.
        composeRule.onNodeWithText("얻다", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("주문", substring = true).assertDoesNotExist()
    }
}

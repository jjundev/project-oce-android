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
 * 개념 브리지 회귀 가드: 실데이터의 긴 서술형 뜻은 원 안(canvas)이 아니라 다이어그램 아래 레전드에
 * **읽기 쉬운 Text 노드**로 렌더돼야 한다. canvas drawText 는 semantics Text 노드를 만들지 않으므로,
 * 아래 onNodeWithText 는 레전드가 실제 Composable 텍스트일 때만 통과한다(겹침 버그의 회귀 방지).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class ConceptualBridgeLegendTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val longLeft = "물건을 사고 받은 증명서를 건네줄 때 쓰는 표현"
    private val longRight = "어떤 것을 건네주며 상황을 전달할 때 쓰는 표현"

    private fun ready(): DeepFeedbackState.Ready =
        DeepFeedbackState.Ready(
            conceptualBridge =
                ConceptualBridge(
                    literalTranslation = "이 셔츠를 반품하고 싶어요.",
                    explanation = "의도는 맞지만 더 자연스럽게 말할 수 있어요.",
                    venn =
                        VennData(
                            guide = "두 표현의 차이를 볼까요?",
                            left = VennCircle(word = "receipt", items = listOf(longLeft)),
                            right = VennCircle(word = "here is", items = listOf(longRight)),
                            intersectionItems = listOf("상황 전달", "물건의 존재를 알림"),
                        ),
                ),
            toneStyle =
                ToneStyle(
                    defaultLevel = 0,
                    // "receipt" 를 피한다 — 레전드의 좌측 헤드워드(receipt)와 substring 이 겹치면
                    // onNodeWithText("receipt", substring = true) 가 다중 매치로 assertExists 에서 실패한다.
                    levels = listOf(ToneLevel(0, "Here is one for you.", "여기 있습니다.")),
                ),
            paraphrasing =
                Paraphrasing(items = listOf(Paraphrase(1, "Beginner", "Here's one for you.", "여기요."))),
        )

    @Test
    fun meanings_render_as_legible_text_nodes_below_the_diagram() {
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
        // 헤드워드 + 좌/우 긴 뜻 + 교집합 뜻이 모두 실제 Text 노드로 존재해야 한다.
        composeRule.onNodeWithText("receipt", substring = true).assertExists()
        composeRule.onNodeWithText("here is", substring = true).assertExists()
        composeRule.onNodeWithText(longLeft, substring = true).assertExists()
        composeRule.onNodeWithText(longRight, substring = true).assertExists()
        composeRule.onNodeWithText("상황 전달", substring = true).assertExists()
    }
}

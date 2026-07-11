package com.jjundev.oneclickeng.feature.session.feedback

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
 * [DeepFeedbackRegion] 렌더 계약: 스켈레톤은 [DeepFeedbackState.Loading] 에서만 보이고, 실패([Error]) 시엔
 * 재시도 메시지만 남긴다(무한 시머로 "로딩 중"처럼 보이던 버그 회귀 방지). 스켈레톤은 [DEEP_BLOCK_SKELETON_TAG]
 * 로 카운트한다. 시머는 rememberInfiniteTransition 이라 테스트 클럭 자동전진을 끄고(autoAdvance=false)
 * 노드 트리만 검증한다 — 아니면 idle 대기가 무한 애니메이션에 막힌다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class DeepFeedbackRegionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loading_shows_three_skeletons() {
        composeRule.mainClock.autoAdvance = false // 무한 시머 → idle 대기 회피
        composeRule.setContent {
            OceTheme {
                DeepFeedbackRegion(
                    state = DeepFeedbackState.Loading(),
                    onRetry = {},
                    bookmarkedLevels = emptySet(),
                    onToggleBookmark = {},
                )
            }
        }

        composeRule.onAllNodesWithTag(DEEP_BLOCK_SKELETON_TAG).assertCountEquals(3)
    }

    private fun sampleConceptualBridge() =
        ConceptualBridge(
            literalTranslation = "커피 하나요.",
            explanation = "조금 더 공손하게 표현할 수 있어요.",
            venn =
                VennData(
                    guide = "두 단어의 의미 차이를 볼까요?",
                    left = VennCircle("get", listOf("얻다")),
                    right = VennCircle("order", listOf("주문하다")),
                    intersectionItems = listOf("받다"),
                ),
        )

    @Test
    fun error_all_missing_shows_message_and_no_skeleton() {
        // Step 2 (pre-fix) still renders infinite-shimmer skeletons; setContent → waitForIdle would hang.
        // Harmless post-fix (Error renders no skeletons). See DialogueGeneratingScreenshotTest for the same guard.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            OceTheme {
                DeepFeedbackRegion(
                    state = DeepFeedbackState.Error(),
                    onRetry = {},
                    bookmarkedLevels = emptySet(),
                    onToggleBookmark = {},
                )
            }
        }

        composeRule.onNodeWithText("깊은 분석을 불러오지 못했어요. 다시 시도해볼까요?").assertIsDisplayed()
        composeRule.onNodeWithText("재시도").assertIsDisplayed()
        composeRule.onAllNodesWithTag(DEEP_BLOCK_SKELETON_TAG).assertCountEquals(0)
    }

    @Test
    fun error_partial_keeps_arrived_block_and_hides_skeletons() {
        // Same reason as above: pre-fix the missing tone/para blocks render infinite-shimmer skeletons.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            OceTheme {
                DeepFeedbackRegion(
                    state = DeepFeedbackState.Error(conceptualBridge = sampleConceptualBridge()),
                    onRetry = {},
                    bookmarkedLevels = emptySet(),
                    onToggleBookmark = {},
                )
            }
        }

        composeRule.onNodeWithText("개념 브리지").assertIsDisplayed() // 도착 블록은 sticky 유지
        composeRule.onNodeWithText("깊은 분석을 불러오지 못했어요. 다시 시도해볼까요?").assertIsDisplayed()
        composeRule.onAllNodesWithTag(DEEP_BLOCK_SKELETON_TAG).assertCountEquals(0) // 미도착 블록 스켈레톤 없음
    }
}

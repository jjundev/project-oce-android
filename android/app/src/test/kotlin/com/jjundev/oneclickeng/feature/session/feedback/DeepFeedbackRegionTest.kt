package com.jjundev.oneclickeng.feature.session.feedback

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
 * 재시도 메시지만 남긴다(무한 시머로 "로딩 중"처럼 보이던 버그 회귀 방지). 스켈레톤은 [DeepBlockSkeletonTag]
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

        composeRule.onAllNodesWithTag(DeepBlockSkeletonTag).assertCountEquals(3)
    }
}

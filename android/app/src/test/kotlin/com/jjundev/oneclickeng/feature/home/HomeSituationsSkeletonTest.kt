package com.jjundev.oneclickeng.feature.home

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 추천 상황 스켈레톤 플래시(프로토 `_flashRecSkel`)의 렌더 계약 검증 — 플래시 타이밍(780/300ms)은 stateful
 * [HomeScreen] 소관이라 여기서는 stateless [HomeContent] 에 `situationsSkeleton` 을 직접 주입해, 플래시 중엔
 * 실제 상황 카드가 시머 자리표시자로 대체되고 플래시가 없으면 카드가 보임을 반증가능하게 고정한다.
 *
 * reduce-motion=true 로 렌더한다 — [com.jjundev.oneclickeng.ui.component.OneClickSkeleton] 이 시머 대신 정적
 * 표면을 써서 무한 애니메이션 없이 `waitForIdle` 이 정착한다(A7).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HomeSituationsSkeletonTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val sampleSituations =
        listOf(
            HomeSituation("cafe", "카페에서 주문하기", OceIcon.LocalCafe),
            HomeSituation("intro", "처음 만나 자기소개하기", OceIcon.WavingHand),
            HomeSituation("appointment", "친구와 약속 잡기", OceIcon.Event),
            HomeSituation("hotel", "호텔 체크인하기", OceIcon.Hotel),
        )

    private fun renderHome(situationsSkeleton: Boolean) {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                HomeContent(
                    state =
                        HomeUiState(
                            isOnline = true,
                            level = "easy",
                            length = 5,
                            situations = sampleSituations,
                        ),
                    onStartLearning = {},
                    onResumeContinue = {},
                    onResumeStartNew = {},
                    onViewRecords = {},
                    onOfflineBlocked = {},
                    situationsSkeleton = situationsSkeleton,
                    reduceMotion = true,
                )
            }
        }
    }

    @Test
    fun cards_visible_when_not_flashing() {
        renderHome(situationsSkeleton = false)
        // 추천 카드는 LazyColumn 하단이라 뷰포트로 스크롤해 실제 카드가 렌더됨을 확인한다.
        composeRule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("호텔 체크인하기", substring = true))
        composeRule.onNodeWithText("호텔 체크인하기", substring = true).assertIsDisplayed()
    }

    @Test
    fun cards_replaced_by_skeleton_while_flashing() {
        renderHome(situationsSkeleton = true)
        // 플래시 중엔 실제 상황 라벨이 사라지고 시머 자리표시자가 그 자리를 차지한다.
        composeRule.onNodeWithText("호텔 체크인하기", substring = true).assertDoesNotExist()
    }
}

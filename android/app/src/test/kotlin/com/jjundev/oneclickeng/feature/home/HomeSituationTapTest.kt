package com.jjundev.oneclickeng.feature.home

import android.app.Application
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 계약 가드: 추천 상황 행 탭은 선택 콜백([onSituationSelected])만 호출하고, 학습 시작([onStartLearning])은
 * 호출하지 않는다(시작은 히어로 CTA 소유). 스테이트풀 래퍼(hiltViewModel)는 이 단위테스트 범위 밖이라,
 * 실제 "히어로 반영만" 배선은 앱 수동 검증(Step 5)으로 보강한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HomeSituationTapTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recommended_row_tap_selects_but_does_not_start() {
        var selected: HomeSituation? = null
        var started = false
        val row = HomeSituation(id = "cafe", labelKo = "카페에서 주문하기", promptSeed = "seed")
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                HomeContent(
                    state =
                        HomeUiState(
                            isOnline = true,
                            hasResume = false,
                            level = "easy",
                            length = 5,
                            selectedSituation = SelectedSituation("other", "날씨로 스몰토크", "s2"),
                            situations = listOf(row),
                        ),
                    onStartLearning = { started = true },
                    onResumeContinue = {},
                    onResumeStartNew = {},
                    onViewRecords = {},
                    onOfflineBlocked = {},
                    onSituationSelected = { selected = it },
                )
            }
        }
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("카페에서 주문하기"))
        composeRule.onNodeWithText("카페에서 주문하기").performClick()

        assertEquals(row, selected)
        assertFalse("추천 행 탭이 학습 시작을 트리거하면 안 된다", started)
    }
}

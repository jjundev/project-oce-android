package com.jjundev.oneclickeng.feature.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 히어로 리빌 트리거의 헤드리스 스모크 — 애니메이션 프레임 자체(비트맵)는 검증 대상이 아니고,
 * 주제 변경 시 히어로 메타 텍스트가 새 라벨로 스왑되며(트리거·AnimatedContent 배선) 크래시가 없는지만 본다.
 * reduce-motion on/off 두 경로 모두 새 라벨을 표시해야 한다(reduce-motion 은 애니메이션만 끄고 메타는 갱신).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HomeHeroRevealTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun stateWith(label: String) =
        HomeUiState(
            isOnline = true,
            hasResume = false,
            level = "easy",
            length = 5,
            selectedSituation = SelectedSituation("id-$label", label, "seed"),
            situations = emptyList(),
        )

    private fun assertMetaSwaps(reduceMotion: Boolean) {
        var state by mutableStateOf(stateWith("카페에서 주문하기"))
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                HomeContent(
                    state = state,
                    onStartLearning = {},
                    onResumeContinue = {},
                    onResumeStartNew = {},
                    onViewRecords = {},
                    onOfflineBlocked = {},
                    reduceMotion = reduceMotion,
                )
            }
        }
        composeRule.onNodeWithText("카페에서 주문하기", substring = true).assertExists()

        state = stateWith("날씨로 스몰토크")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("날씨로 스몰토크", substring = true).assertExists()
    }

    @Test
    fun meta_swaps_with_motion() = assertMetaSwaps(reduceMotion = false)

    @Test
    fun meta_swaps_reduced_motion() = assertMetaSwaps(reduceMotion = true)
}

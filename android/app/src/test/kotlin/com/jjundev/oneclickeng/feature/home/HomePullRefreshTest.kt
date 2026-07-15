package com.jjundev.oneclickeng.feature.home

import android.app.Application
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 계약 가드: 홈 최상단 당겨서-새로고침은 [HomeContent]의 기존 onRefreshSituations 콜백만 트리거한다
 * (추천 상황만 회전 — 오늘 N분/streak/hero 는 재로딩하지 않는다).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HomePullRefreshTest {
    @get:Rule val rule = createComposeRule()

    @Test fun pullDown_refreshesSituationsOnly() {
        var refreshSituationsCalls = 0
        val state = HomeUiState(
            // icon defaults to OceIcon.Hub — do NOT pass an emoji string (3rd arg is OceIcon, not String).
            situations = List(4) { HomeSituation(id = "id$it", labelKo = "상황 $it", promptSeed = "seed$it") },
            // header/stats/hero fields left default
        )
        rule.setContent {
            OceTheme {
                HomeContent(
                    state = state,
                    onStartLearning = {},
                    onResumeContinue = {},
                    onResumeStartNew = {},
                    onViewRecords = {},
                    onOfflineBlocked = {},
                    onRefreshSituations = { refreshSituationsCalls++ },
                    modifier = Modifier.testTag("home"),
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("home").performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertTrue("pull refreshed 추천 상황", refreshSituationsCalls >= 1)
    }
}

package com.jjundev.oneclickeng.ui.navigation

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import com.jjundev.oneclickeng.ui.foundation.OceBottomNav
import com.jjundev.oneclickeng.ui.root.AppRoot
import com.jjundev.oneclickeng.ui.root.MAIN_TABS_ROUTE
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test

/**
 * 3탭 네비 스모크(M0-09 검증). 탭 라벨과 화면 인라인 타이틀이 동일 문자열이라, 탭은 클릭 액션으로,
 * 타이틀은 heading 시맨틱으로 구분해 매칭한다.
 */
class AppNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** 탭(클릭 가능) 노드 매처. */
    private fun isTab(label: String): SemanticsMatcher = hasText(label) and hasClickAction()

    /** 화면 인라인 타이틀(heading, 비클릭) 노드 매처. */
    private fun isTitle(label: String): SemanticsMatcher {
        return hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
    }

    @Test
    fun threeTabsRenderAndSwitchContent() {
        // 부트 게이트(Firestore 왕복)를 우회해 3탭 셸을 결정적으로 부팅하려고 시작 목적지를 직접 주입한다
        // (M3-08 로 M1-09 하니스는 제거됨 — 홈이 유일 정본 진입).
        composeRule.setContent {
            OceTheme { AppRoot(startRoute = MAIN_TABS_ROUTE) }
        }

        // 3탭 모두 렌더.
        composeRule.onNode(isTab("학습")).assertIsDisplayed()
        composeRule.onNode(isTab("기록")).assertIsDisplayed()
        composeRule.onNode(isTab("설정")).assertIsDisplayed()

        // 시작 목적지 = 학습 화면 타이틀.
        composeRule.onNode(isTitle("학습")).assertIsDisplayed()

        // 기록 탭 전환.
        composeRule.onNode(isTab("기록")).performClick()
        composeRule.onNode(isTitle("기록")).assertIsDisplayed()

        // 설정 탭 전환.
        composeRule.onNode(isTab("설정")).performClick()
        composeRule.onNode(isTitle("설정")).assertIsDisplayed()
    }

    /**
     * reduce-motion 정적 대체 seam 검증(수용기준 #4). 시스템 설정 토글(WRITE_SECURE_SETTINGS 필요)
     * 대신 [OceNavHost] 의 reduceMotion 인자를 직접 주입해, 정적 전환 경로에서도 시작 화면이 정상
     * 렌더됨을 반증가능하게 확인한다.
     */
    @Test
    fun navHostRendersStartDestinationWithReduceMotion() {
        composeRule.setContent {
            OceTheme {
                val navController = rememberNavController()
                androidx.compose.foundation.layout.Column {
                    OceNavHost(
                        navController = navController,
                        onStartSession = { _, _, _, _, _ -> },
                        onResume = {},
                        reduceMotion = true,
                    )
                    OceBottomNav(navController)
                }
            }
        }

        composeRule.onNode(isTitle("학습")).assertIsDisplayed()
    }
}

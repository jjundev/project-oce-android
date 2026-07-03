package com.jjundev.oneclickeng.dev

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test

/**
 * 디버그 빌드 진입 스모크(M1-09 검증). 하니스 런처가 제목과 프리셋 버튼을 렌더함을 확인한다.
 * androidTest 는 debug 변이로 컴파일되므로 debug 전용 [DevHarnessLauncher] 에 접근 가능하다.
 * (AppRoot 전체 부팅 대신 런처를 직접 렌더해 Hilt/ViewModel 없이 진입 표면만 반증가능하게 검증.)
 */
class HarnessSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun launcherRendersTitleAndPresets() {
        composeRule.setContent {
            OceTheme { DevHarnessLauncher(rememberNavController()) }
        }

        composeRule.onNodeWithText(HARNESS_LAUNCHER_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText("쉬움 · 5턴 · 첫 세션 — 카페 주문").assertIsDisplayed()
    }
}

package com.jjundev.oneclickeng.ui.component

import android.app.Application
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 회귀: [OneClickBottomSheet] 는 콘텐츠-hug 시트라 기본 sheetState 로도 **완전 펼침**으로 열려야 한다.
 * M3 기본(skipPartiallyExpanded=false)이면 콘텐츠가 화면 50%를 넘길 때 절반 detent 에서 멈춰
 * "완전히 안 펼쳐지는" 버그가 났다("알림을 보내도 될까요?" 권한 시트, 긴 화면).
 *
 * 짧은 화면(h640dp)으로 실제 권한 시트 콘텐츠가 50%를 넘게 해 사용자가 겪은 조건을 재현하고,
 * **sheetState 를 넘기지 않아 프리미티브 기본 경로**를 검증한다. 절반 펼침이면 최하단 버튼("다음에")이
 * 화면 밖이라 미표시된다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h640dp-560dpi", application = Application::class)
class OneClickBottomSheetExpandTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun default_sheet_opens_fully_expanded_with_tall_content() {
        composeRule.setContent {
            OceTheme {
                OneClickBottomSheet(onDismissRequest = {}) {
                    OneClickPermissionPrimingSheetContent(
                        icon = OceIcon.Notifications,
                        rationale =
                            "다음 화면에서 허용을 눌러주세요.\n" +
                                "매일 정한 시각에 학습 리마인더만 보내드려요.\n" +
                                "광고나 다른 알림은 없어요.",
                        emphasis = "허용",
                        onRequest = {},
                        onLater = {},
                        title = "알림을 보내도 될까요?",
                        requestLabel = "계속",
                        laterLabel = "다음에",
                        assurance = "거부해도 학습에는 아무 영향이 없어요.",
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 완전 펼침이면 최하단 컨트롤이 화면에 보인다(절반 detent 면 미표시 → 회귀 감지).
        composeRule.onNodeWithText("다음에").assertIsDisplayed()
    }
}

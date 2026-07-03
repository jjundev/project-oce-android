package com.jjundev.oneclickeng.ui.component

import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * M0-05 코어 컴포넌트 불변식 계측 테스트 5종(WaveformCanvasTest 패턴). dp 고정·a11y stateDescription·
 * reduce-motion 정적·위험 확인 라벨·undo 액션을 화면 밖 회귀로 고정한다.
 */
class CoreComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** C4: 오프라인 배너는 fontScale 무관 28dp 고정(A7). */
    @Test
    fun offlineBannerHeightIsFixed28dp() {
        composeRule.setContent {
            OceTheme {
                OneClickOfflineBanner(
                    visible = true,
                    modifier = Modifier.testTag("banner"),
                )
            }
        }

        composeRule.onNodeWithTag("banner").assertHeightIsEqualTo(28.dp)
    }

    /** C7: Indeterminate 링은 "분석 중" contentDescription 을 노출한다(A3/A6). */
    @Test
    fun progressRingIndeterminateExposesAnalyzingDescription() {
        composeRule.setContent {
            OceTheme {
                OneClickProgressRing(mode = ProgressRingMode.Indeterminate)
            }
        }

        composeRule.onNodeWithContentDescription("분석 중").assertExists()
    }

    /** C6: reduce-motion 시 스켈레톤은 정적으로(시머 없이) 카드 높이를 유지한다. */
    @Test
    fun skeletonReduceMotionRendersStatic() {
        composeRule.setContent {
            OceTheme {
                OneClickSkeleton(
                    shape = SkeletonShape.Card,
                    reduceMotion = true,
                    modifier = Modifier.testTag("skeleton"),
                )
            }
        }

        composeRule.onNodeWithTag("skeleton").assertHeightIsEqualTo(96.dp)
    }

    /** C1: Destructive 다이얼로그는 명시 동사 확인 라벨을 노출하고 클릭 시 onConfirm 을 호출한다. */
    @Test
    fun destructiveDialogConfirmLabelInvokesCallback() {
        var confirmed = false
        composeRule.setContent {
            OceTheme {
                OneClickDialog(
                    title = "이 카드를 지울까요?",
                    body = "이 작업은 되돌릴 수 없어요.",
                    confirmLabel = "삭제",
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                    variant = OneClickDialogVariant.Destructive,
                )
            }
        }

        composeRule.onNodeWithText("삭제").performClick()

        assertTrue(confirmed)
    }

    /** C3: 스낵바 undo 액션이 노출되고 클릭 시 performAction 을 호출한다. */
    @Test
    fun snackbarUndoActionInvokesPerformAction() {
        var actioned = false
        val data =
            object : SnackbarData {
                override val visuals =
                    object : SnackbarVisuals {
                        override val message = "카드를 삭제했어요."
                        override val actionLabel = "실행취소"
                        override val duration = SnackbarDuration.Short
                        override val withDismissAction = false
                    }

                override fun performAction() {
                    actioned = true
                }

                override fun dismiss() = Unit
            }

        composeRule.setContent {
            OceTheme {
                OneClickSnackbar(data = data)
            }
        }

        composeRule.onNodeWithText("실행취소").performClick()

        assertTrue(actioned)
    }
}

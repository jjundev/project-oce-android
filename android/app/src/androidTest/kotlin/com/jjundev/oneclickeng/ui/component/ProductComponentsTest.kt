package com.jjundev.oneclickeng.ui.component

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * M0-06 제품특화 컴포넌트 불변식 계측 테스트. 각 컴포넌트의 외형·상태·a11y 계약(수용 기준)을 회귀로 고정한다.
 * 오버레이 시트(C13/C19)는 프리뷰가 담당하고, 여기서는 상태 전이·라벨·리빌·surface 분기에 집중한다.
 */
class ProductComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** 확인 버튼(텍스트 "삭제")만 매칭 — 동명 입력필드를 SetText 액션 유무로 배제한다(placeholder 미사용). */
    private val confirmButtonMatcher =
        hasText("삭제") and SemanticsMatcher.keyNotDefined(SemanticsActions.SetText)

    /** C2: "삭제" 정확 타이핑 전까지 확인 disabled, 일치 시 enabled + onConfirm 호출. */
    @Test
    fun dangerConfirmEnablesConfirmOnlyOnTypedMatch() {
        composeRule.setContent {
            OceTheme {
                OneClickDangerConfirm(
                    title = "계정을 삭제할까요?",
                    impactLines = listOf("모든 기록이 사라져요."),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        // (1) 영향 단계 → 계속 → (2) 타이핑 단계
        composeRule.onNodeWithText("계속").performClick()
        composeRule.onNode(confirmButtonMatcher).assertIsNotEnabled()

        // 잘못된 입력 → 여전히 disabled
        composeRule.onNode(hasSetTextAction()).performTextInput("삭재")
        composeRule.onNode(confirmButtonMatcher).assertIsNotEnabled()
    }

    /** C2: 정확 일치 시 확인이 enabled 되고 클릭이 onConfirm 을 호출한다. */
    @Test
    fun dangerConfirmFiresOnExactMatch() {
        var confirmed = false
        composeRule.setContent {
            OceTheme {
                OneClickDangerConfirm(
                    title = "계정을 삭제할까요?",
                    impactLines = listOf("모든 기록이 사라져요."),
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("계속").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput(" 삭제 ") // trim 무관
        composeRule.onNode(confirmButtonMatcher).assertIsEnabled().performClick()

        assertTrue(confirmed)
    }

    /** C8: 톤 이산 슬라이더는 선택 stop 의 EN 라벨을 노출한다(EN+KO 이중, A4). */
    @Test
    fun toneSliderShowsSelectedEnglishLabel() {
        composeRule.setContent {
            OceTheme {
                OneClickSlider(
                    value = 2f,
                    onValueChange = {},
                    mode = SliderMode.Discrete(labels = previewToneLabels()),
                )
            }
        }

        composeRule.onNodeWithText("That works for me.", substring = true).assertExists()
    }

    /** C11: Blocked 모드는 "건너뛰고 다음으로" 를 노출한다(누적 2회 후 소비처 전환). */
    @Test
    fun inlineErrorBlockedShowsSkip() {
        composeRule.setContent {
            OceTheme {
                OneClickInlineError(
                    mode = InlineErrorMode.Blocked,
                    message = "여러 번 실패했어요.",
                    onRetry = {},
                    onSkip = {},
                )
            }
        }

        composeRule.onNodeWithText("건너뛰고 다음으로").assertExists()
    }

    /** C14: streak 칩은 🔥 + "N 일" 텍스트 이중신호를 노출한다. */
    @Test
    fun streakChipShowsDays() {
        composeRule.setContent {
            OceTheme {
                OneClickStreakChip(days = 7)
            }
        }

        composeRule.onNodeWithText("7 일").assertExists()
    }

    /** C16: static/reduce-motion 카운트업은 최종값을 즉시 스냅하고 최종 라벨을 announce 한다(A6). */
    @Test
    fun countUpStaticAnnouncesFinalValue() {
        composeRule.setContent {
            OceTheme {
                OneClickCountUp(target = 120, unit = " XP", static = true)
            }
        }

        composeRule.onNodeWithContentDescription("120 XP").assertExists()
    }

    /** C18: DialogueStartGate surface 는 중립 문구 + 기록 보기 액션을 렌더한다. */
    @Test
    fun limitPanelGateSurfaceRenders() {
        composeRule.setContent {
            OceTheme {
                OneClickLimitReachedPanel(
                    surface = LimitSurface.DialogueStartGate,
                    streakDays = 5,
                    onViewRecords = {},
                )
            }
        }

        composeRule.onNodeWithText("오늘 무료 학습을 다 했어요").assertExists()
        composeRule.onNodeWithText("기록 보기").assertExists()
    }

    /** C18: Home surface 는 비숫자 보조 고지를 렌더한다(P7). */
    @Test
    fun limitPanelHomeSurfaceRenders() {
        composeRule.setContent {
            OceTheme {
                OneClickLimitReachedPanel(
                    surface = LimitSurface.Home,
                    streakDays = 0,
                    onViewRecords = {},
                )
            }
        }

        composeRule.onNodeWithText("오늘은 여기까지예요").assertExists()
    }

    /** C18: OnboardingFirstSession surface 는 기록 보기 액션 없이 대기 안내만 렌더한다. */
    @Test
    fun limitPanelOnboardingSurfaceHasNoRecordsAction() {
        composeRule.setContent {
            OceTheme {
                OneClickLimitReachedPanel(
                    surface = LimitSurface.OnboardingFirstSession,
                    streakDays = 0,
                    onViewRecords = {},
                )
            }
        }

        composeRule.onNodeWithText("오늘의 첫 대화를 마쳤어요").assertExists()
        composeRule.onNodeWithText("기록 보기").assertDoesNotExist()
    }

    /** C20: 옵션 탭 시 정답을 리빌(오답 비처벌)하고 다음 카드로 넘어갈 수 있다. */
    @Test
    fun waitQuizRevealsAnswerOnTap() {
        composeRule.setContent {
            OceTheme {
                OneClickWaitQuiz(items = previewWaitQuizItems(), reduceMotion = true)
            }
        }

        composeRule.onNodeWithText("I have a plan.", substring = true).performClick()
        composeRule.onNodeWithText("맞아요", substring = true).assertExists()
        composeRule.onNodeWithText("다음").assertExists()
    }
}

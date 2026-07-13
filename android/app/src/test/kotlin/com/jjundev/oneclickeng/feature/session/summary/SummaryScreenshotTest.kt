package com.jjundev.oneclickeng.feature.session.summary

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 세션 요약 스크린샷 캡처(프로토타입 `summary` 대조). [SummaryScreen] 은 이미 무상태라 고정 상태로 직접 렌더한다.
 * 상태는 파일 내 previewRich 픽스처와 동일 구성(점수 87·하이라이트·표현/단어/코칭 리치 번들).
 *
 * `summary_light` = 기본 뷰포트(상단만), `summary_full_light` = 세로 2600dp 뷰포트로 스크롤 없이 **전체 콘텐츠**를
 * 한 프레임에 렌더(프로토타입 스크롤 하단 섹션과 대조용).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class SummaryScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun summary_light() {
        capture("build/outputs/roborazzi/summary_light.png", dark = false)
    }

    @Test
    fun summary_dark() = capture("build/outputs/roborazzi/summary_dark.png", dark = true)

    /** 전체 콘텐츠 한 프레임 캡처 — 세로로 긴 뷰포트라 스크롤 없이 하단 섹션(단어/코칭/북마크)까지 렌더된다. */
    @Test
    @Config(qualifiers = "+h2600dp")
    fun summary_full_light() {
        capture("build/outputs/roborazzi/summary_full_light.png", dark = false)
    }

    @Test
    @Config(qualifiers = "+h2600dp")
    fun summary_full_dark() = capture("build/outputs/roborazzi/summary_full_dark.png", dark = true)

    /**
     * 로딩(BundleLoading) 스켈레톤 정합 — 하이라이트는 즉시 렌더, 표현/단어/코칭 섹션은 **제목 즉시 노출 + 본문만
     * 시머**([FeedbackLoadingSkeleton], 턴 피드백 시트 스켈레톤과 동일 anatomy).
     */
    @Test
    @Config(qualifiers = "+h2600dp")
    fun summary_loading_light() {
        capture("build/outputs/roborazzi/summary_loading_light.png", dark = false, state = loadingState())
    }

    private fun capture(
        path: String,
        dark: Boolean,
        state: SummaryState = richState(),
    ) {
        composeRule.setContent {
            OceTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SummaryScreen(
                        state = state,
                        onRetry = {},
                        onToggleSaveWord = {},
                        onToggleSaveExpression = {},
                        // 고정 "완료" 풋터가 항상 하단에 보이는지 캡처(프로토 flex:none 풋터 정합).
                        onDone = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage(path)
    }

    /** 로딩 상태 픽스처 — 로컬 즉시 블록(점수·하이라이트)은 채우고 SSE 번들은 [SectionBundle.BundleLoading]. */
    private fun loadingState() =
        SummaryState(
            totalScore = 87,
            highlight = HighlightTurn("커피 주세요", "Could I get a latte?", 92),
            bookmarks = emptyList(),
            accrual = AccrualStrip(streakDays = 1, xp = 20),
            bundle = SectionBundle.BundleLoading,
        )

    private fun richBundle(): SectionBundle.Sectioned =
        SectionBundle.Sectioned(
            expression =
                SummarySectionState.Ready(
                    listOf(
                        ExpressionCard(
                            type = ExpressionType.Natural,
                            koreanPrompt = "커피 주세요",
                            before = "One coffee",
                            after = "Could I grab a coffee?",
                            explanation = "가볍게 주문할 때 자연스러워요.",
                        ),
                        ExpressionCard(
                            type = ExpressionType.Accurate,
                            koreanPrompt = "길을 잃었어요",
                            before = "I lost",
                            after = "I got lost",
                            explanation = "get lost 가 '길을 잃다'예요.",
                        ),
                        ExpressionCard(
                            type = ExpressionType.Natural,
                            koreanPrompt = "가격 물어보기",
                            before = "How much this one?",
                            after = "How much is this one?",
                            explanation = "be동사 is 를 잊지 마세요.",
                        ),
                        ExpressionCard(
                            type = ExpressionType.Natural,
                            koreanPrompt = "영수증 받기",
                            before = "Give me receipt.",
                            after = "Can I have the receipt?",
                            explanation = "Can I have...가 훨씬 공손해요.",
                        ),
                    ),
                ),
            word =
                SummarySectionState.Ready(
                    listOf(
                        WordCard(
                            en = "grab",
                            ko = "잽싸게 가져오다",
                            partOfSpeech = "verb",
                            level = "B1",
                            exampleEn = "Let me grab a quick bite.",
                            exampleKo = "간단히 먹을게요.",
                        ),
                    ),
                ),
            coaching =
                SummarySectionState.Ready(
                    Coaching(positive = "끝까지 대화를 이어간 게 좋았어요.", toImprove = "다음엔 과거형을 한 번 노려볼까요?"),
                ),
        )

    private fun richState(): SummaryState =
        SummaryState(
            totalScore = 87,
            highlight =
                HighlightTurn(
                    koreanPrompt = "커피 주세요",
                    userText = "Could I get a latte, please?",
                    score = 92,
                    rationale = "정중하게 부탁하는 표현을 스스로 골라 말했어요.",
                ),
            bookmarks = listOf(BookmarkCard("I got lost on the way.", "오는 길에 길을 잃었어요.")),
            accrual =
                AccrualStrip(
                    streakDays = 1,
                    xp = 20,
                    todayStudySecondsBefore = 0,
                    todayStudySecondsAfter = 300,
                    streakStatic = false,
                    animate = false,
                ),
            bundle = richBundle(),
            savedWordIndices = setOf(0),
            isFirstSession = true,
        )
}

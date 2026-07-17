package com.jjundev.oneclickeng.feature.session.turn

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.feature.session.feedback.ConceptualBridge
import com.jjundev.oneclickeng.feature.session.feedback.DeepFeedbackState
import com.jjundev.oneclickeng.feature.session.feedback.Grammar
import com.jjundev.oneclickeng.feature.session.feedback.NaturalExpression
import com.jjundev.oneclickeng.feature.session.feedback.Paraphrase
import com.jjundev.oneclickeng.feature.session.feedback.Paraphrasing
import com.jjundev.oneclickeng.feature.session.feedback.Reason
import com.jjundev.oneclickeng.feature.session.feedback.RecapHeader
import com.jjundev.oneclickeng.feature.session.feedback.SectionState
import com.jjundev.oneclickeng.feature.session.feedback.SlimFeedbackContent
import com.jjundev.oneclickeng.feature.session.feedback.SlimFeedbackState
import com.jjundev.oneclickeng.feature.session.feedback.ToneLevel
import com.jjundev.oneclickeng.feature.session.feedback.ToneStyle
import com.jjundev.oneclickeng.feature.session.feedback.VennCircle
import com.jjundev.oneclickeng.feature.session.feedback.VennData
import com.jjundev.oneclickeng.feature.session.feedback.WritingScore
import com.jjundev.oneclickeng.ui.audio.MicState
import com.jjundev.oneclickeng.ui.component.RichSegment
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.sin

/**
 * 대화 학습 "클릭 이후" 상태 스크린샷(프로토타입 flow_* 대조): 마이크 녹음/분석, 오답(재시도 힌트), 심화 피드백.
 * 슬림 피드백 시트는 ModalBottomSheet(별도 윈도)라 onRoot 캡처가 어려워 심화 영역(DeepFeedbackRegion,
 * 인라인)만 캡처한다 — 슬림 3섹션은 [com.jjundev.oneclickeng.feature.session.feedback] 프리뷰로 검증된다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class SessionFlowScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val header =
        DialogueHeaderState(
            topicEmoji = "☕",
            title = "카페에서 주문하기",
            levelLabel = "easy(A2) · 5턴 균일",
        )
    private val opponent =
        listOf(DialogueMessage.Opponent("Hi! What can I get for you?", "안녕하세요! 무엇을 드릴까요?"))
    private val task = ScaffoldTask("라떼 한 잔을 주문해보세요")
    private val waveform =
        MutableStateFlow(FloatArray(48) { i -> 0.35f + 0.55f * abs(sin(i * 0.7f)) })

    private fun captureDock(name: String, micState: MicState, retryHint: String? = null) {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DialogueTurnContent(
                        messages = opponent,
                        turnPhase = TurnPhase.LearnerTurn,
                        sessionPhase = SessionPhase.InTurn,
                        currentTask = task,
                        listState = rememberLazyListState(),
                        onSubmitStub = {},
                        onViewSummary = {},
                        header = header,
                        dock = { t ->
                            MicDock(
                                task = t,
                                micState = micState,
                                waveform = waveform,
                                textMode = false,
                                textValue = "",
                                retryHint = retryHint,
                                permanentlyDenied = false,
                                reduceMotion = true,
                                onMicTap = {},
                                onAdvance = {},
                                onCancelRecording = {},
                                onToggleTextMode = {},
                                onTextChange = {},
                                onSubmitText = {},
                            )
                        },
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun flow_recording_light() = captureDock("flow_recording_light", MicState.Recording)

    @Test
    fun flow_analyzing_light() = captureDock("flow_analyzing_light", MicState.Analyzing)

    @Test
    fun flow_wrong_light() =
        captureDock("flow_wrong_light", MicState.Ready, retryHint = "다시 말해볼까요? 채팅으로 입력해도 돼요.")

    @Test
    fun flow_text_input_light() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DialogueTurnContent(
                        messages = opponent,
                        turnPhase = TurnPhase.LearnerTurn,
                        sessionPhase = SessionPhase.InTurn,
                        currentTask = task,
                        listState = rememberLazyListState(),
                        onSubmitStub = {},
                        onViewSummary = {},
                        header = header,
                        dock = { t ->
                            MicDock(
                                task = t,
                                micState = MicState.Ready,
                                waveform = waveform,
                                textMode = true,
                                textValue = "",
                                retryHint = null,
                                permanentlyDenied = false,
                                reduceMotion = true,
                                onMicTap = {},
                                onAdvance = {},
                                onCancelRecording = {},
                                onToggleTextMode = {},
                                onTextChange = {},
                                onSubmitText = {},
                            )
                        },
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/flow_text_input_light.png")
    }

    private val feedbackBehind =
        listOf(
            DialogueMessage.Opponent("Hi! What can I get for you?", "안녕하세요! 무엇을 드릴까요?"),
            DialogueMessage.Learner("Can I get a latte, please?"),
        )

    @Test
    fun flow_feedback_light() = captureFeedback(name = "flow_feedback_light", dark = false)

    @Test
    fun flow_feedback_dark() = captureFeedback(name = "flow_feedback_dark", dark = true)

    private fun captureFeedback(name: String, dark: Boolean) {
        composeRule.setContent {
            OceTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 뒤 대화(딤). 학습자 답변까지 노출, 도크 없음(OpponentTurn).
                        DialogueTurnContent(
                            messages = feedbackBehind,
                            turnPhase = TurnPhase.OpponentTurn,
                            sessionPhase = SessionPhase.InTurn,
                            currentTask = null,
                            listState = rememberLazyListState(),
                            onSubmitStub = {},
                            onViewSummary = {},
                            header = header,
                        )
                        Box(modifier = Modifier.fillMaxSize().background(OceTheme.colors.scrim))
                        // 적응형 시트(콘텐츠에 맞춰, 최대 70%) + 드래그 핸들 — 실제 SlimFeedbackSheet 셸과 동일.
                        Surface(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.7f).dp),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .width(32.dp)
                                                .height(4.dp)
                                                .clip(OceTheme.shapes.pill)
                                                .background(MaterialTheme.colorScheme.outlineVariant),
                                    )
                                }
                                SlimFeedbackContent(
                                    state = slimActive(),
                                    onRetry = {},
                                    onSkip = {},
                                    onNext = {},
                                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun slimActive(): SlimFeedbackState.Active =
        SlimFeedbackState.Active(
            header =
                RecapHeader(
                    koreanPrompt = "라떼 한 잔을 주문해보세요",
                    userText = "Can I get a latte, please?",
                ),
            writingScore =
                SectionState.Ready(
                    WritingScore(score = 94, encouragement = "정확하게 말했어요. 이대로 쭉 이어가면 돼요."),
                ),
            grammar =
                SectionState.Ready(
                    Grammar(
                        segments = listOf(RichSegment.Normal("Can I get a latte, please?")),
                        explanation = "고칠 곳이 없어요. 문장이 그대로 정확해요.",
                    ),
                ),
            natural =
                SectionState.Ready(
                    NaturalExpression(
                        segments = listOf(RichSegment.Normal("Can I get a latte, please?")),
                        reason = Reason(keyword = "자연스러움", description = "이미 자연스러워요."),
                    ),
                ),
        )

    /** "더 보기" 펼침 — 프로덕션과 동일하게 **흰 시트 안 인라인**으로 렌더(회색 배경 별도 화면 아님). */
    @Test
    fun flow_deep_light() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        DialogueTurnContent(
                            messages = feedbackBehind,
                            turnPhase = TurnPhase.OpponentTurn,
                            sessionPhase = SessionPhase.InTurn,
                            currentTask = null,
                            listState = rememberLazyListState(),
                            onSubmitStub = {},
                            onViewSummary = {},
                            header = header,
                        )
                        Box(modifier = Modifier.fillMaxSize().background(OceTheme.colors.scrim))
                        Surface(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.9f).dp),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .width(32.dp)
                                                .height(4.dp)
                                                .clip(OceTheme.shapes.pill)
                                                .background(MaterialTheme.colorScheme.outlineVariant),
                                    )
                                }
                                SlimFeedbackContent(
                                    state = slimActive(),
                                    onRetry = {},
                                    onSkip = {},
                                    onNext = {},
                                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                                    deepState = deepReady(),
                                    deepExpanded = true,
                                    bookmarkedLevels = setOf(2),
                                )
                            }
                        }
                    }
                }
            }
        }
        // 심화 영역이 슬림 3섹션 아래에 이어지는 것을 보이도록 시트 내부를 아래로 스크롤한 뒤 캡처.
        composeRule.onRoot().performTouchInput { swipeUp(startY = bottom * 0.85f, endY = top + 120f) }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/flow_deep_light.png")
    }

    private fun deepReady(): DeepFeedbackState.Ready =
        DeepFeedbackState.Ready(
            conceptualBridge =
                ConceptualBridge(
                    literalTranslation = "주문하다",
                    explanation = "한국어 '주문하다'는 order 와 get 둘 다 자연스러워요.",
                    venn =
                        VennData(
                            guide = "주문하다",
                            left = VennCircle(word = "order", items = listOf("격식 있는 주문", "메뉴 지정")),
                            right = VennCircle(word = "get", items = listOf("구어체", "가볍게")),
                            intersectionItems = listOf("주문하다"),
                        ),
                ),
            toneStyle =
                ToneStyle(
                    defaultLevel = 2,
                    levels =
                        listOf(
                            ToneLevel(0, "I would like to order a latte.", "라떼를 주문하고 싶습니다."),
                            ToneLevel(1, "Could I get a latte, please?", "라떼 한 잔 주시겠어요?"),
                            ToneLevel(2, "Can I get a latte, please?", "라떼 한 잔 주세요."),
                            ToneLevel(3, "I'll get a latte.", "라떼로 할게요."),
                            ToneLevel(4, "A latte, thanks!", "라떼요, 감사!"),
                        ),
                ),
            paraphrasing =
                Paraphrasing(
                    items =
                        listOf(
                            Paraphrase(1, "Beginner", "A latte, please.", "라떼 한 잔 주세요."),
                            Paraphrase(2, "Intermediate", "Can I get a latte, please?", "라떼 한 잔 주세요."),
                            Paraphrase(3, "Advanced", "Could I grab a latte, please?", "라떼 한 잔 부탁드려요."),
                        ),
                ),
        )
}

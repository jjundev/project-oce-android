package com.jjundev.oneclickeng.feature.session.turn

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * M1-03 대화 턴 화면 = 채팅 표면 + 한국어 발판 카드(정적 셸). 인메모리 스텁 대본([SampleDialogue])으로
 * 구동되며 실 SSE·입력·TTS·피드백은 전부 범위 밖(M1-01/04/05/06/07/08)이라 seam 으로만 남는다.
 *
 * **컨테이너 선택(리뷰 반영):** `TabScreenScaffold`(3개 상시탭 전용, TabScreenScaffold.kt) 는 여기 부적합해
 * bare [Scaffold] 를 쓴다. 이는 피처 스크린이 자체 Scaffold 를 소유하는 리포 첫 선례다(앱 루트 AppRoot.kt
 * 외). 시스템 백/상태바/실 라우트 진입 배선은 M1-01 로 이연한다(현 nav 는 3탭뿐, 세션 라우트 없음).
 *
 * @param onViewSummary 세션 완료 시 요약 이동 콜백. 완료 화면 없이 `sessionPhase == Completed` 진입 즉시 발화한다.
 */
@Composable
fun DialogueTurnScreen(
    modifier: Modifier = Modifier,
    script: List<DialogueTurn> = SampleDialogue.script,
    reduceMotion: Boolean = rememberReduceMotion(),
    onViewSummary: () -> Unit = {},
) {
    val state = rememberDialogueState(script = script, reduceMotion = reduceMotion)
    val listState = rememberLazyListState()

    // 신규 구현(리포 선례 없음): 메시지 추가·타이핑 스켈레톤 등장 시 최신 아이템으로 자동 스크롤.
    // 스켈레톤은 메시지 뒤 마지막 아이템이라 typing 중엔 인덱스 = messages.size, 아니면 messages.lastIndex.
    LaunchedEffect(state.messages.size, state.opponentTyping) {
        val lastIndex = if (state.opponentTyping) state.messages.size else state.messages.lastIndex
        if (lastIndex >= 0) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    DialogueTurnContent(
        messages = state.messages,
        turnPhase = state.turnPhase,
        sessionPhase = state.sessionPhase,
        currentTask = state.currentTask,
        listState = listState,
        onSubmitStub = state::submitLearnerStub,
        onViewSummary = onViewSummary,
        modifier = modifier,
        opponentTyping = state.opponentTyping,
    )
}

/**
 * 상태 무관 콘텐츠. 상태 홀더에서 분리해 [DialogueTurnScreen] 은 배선만, 프리뷰는 각 위상을 결정적으로
 * 렌더한다(자동 진행 타이머에 흔들리지 않음).
 */
@Composable
internal fun DialogueTurnContent(
    messages: List<DialogueMessage>,
    turnPhase: TurnPhase,
    sessionPhase: SessionPhase,
    currentTask: ScaffoldTask?,
    listState: LazyListState,
    onSubmitStub: () -> Unit,
    onViewSummary: () -> Unit,
    modifier: Modifier = Modifier,
    // 세션 정체성 헤더(주제·레벨·진행 점). 미주입이면 헤더 없이 렌더(M1-03 스텁·프리뷰 호환). 실 라우트 배선은 seam.
    header: DialogueHeaderState? = null,
    // 헤더 뒤로가기 화살표 콜백(대화 나가기). 미주입이면 no-op(스텁·프리뷰·테스트 호환).
    onBack: () -> Unit = {},
    // 상대역 말풍선 TTS(M1-05)·해석 토글 콜백. 현재는 시각 셸 seam 으로 기본 no-op.
    onReplay: () -> Unit = {},
    onToggleTranslation: () -> Unit = {},
    // 입력 독 slot(M1-08). 미주입(스텁 라우트·프리뷰)이면 기존 [ScaffoldDock] 로 폴백해 M1-03 화면을 유지한다.
    dock: (@Composable (ScaffoldTask) -> Unit)? = null,
    // 상대역 발화 append 직전 타이핑 스켈레톤 국면(프로토타입 정합). 기본 false 라 프리뷰·스크린샷 테스트·
    // 무상태 렌더는 스켈레톤을 그리지 않는다(결정성 유지). 상태 홀더만 실제 국면을 주입한다.
    opponentTyping: Boolean = false,
) {
    // reduceMotion 게이트(스켈레톤 진입 페이드·입력 독 슬라이드업). 무상태 렌더도 시스템 설정을 읽지만,
    // 슬라이드업은 초기 visible=true 시 애니메이션이 없고 스켈레톤은 opponentTyping=false 라 프리뷰/테스트는 정적.
    val reduceMotion = rememberReduceMotion()
    // 세션이 완료되면(마지막 턴 전진) 완료 화면 없이 곧장 요약으로 이동한다(완료 바텀시트 삭제 요구). 마지막 턴
    // 피드백 "다음"이 recordTurn→advanceTurn 순서라 요약이 읽을 턴 버퍼는 Completed 시점에 이미 정착돼 있다.
    // sessionPhase 전이는 단방향(Completed 도달 후 유지)이라 이 LaunchedEffect 는 정확히 한 번만 발화한다.
    LaunchedEffect(sessionPhase) {
        if (sessionPhase == SessionPhase.Completed) onViewSummary()
    }
    Scaffold(
        modifier = modifier,
        // 세션 앱바(뒤로가기·주제 아바타·제목/레벨·진행 점). 라이브리전 politeness 는 후속 진행률/TTS 배선의
        // 자동 진행 announce 를 위한 것으로, 지금은 헤더 정적 렌더만 감싼다.
        topBar = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                // 헤더는 상태바 아래로 인셋(엣지-투-엣지에서 상태바와 겹치지 않게). header=null 이면
                // 빈 topBar 라 인셋을 얹지 않는다(콘텐츠 자체 인셋과 이중 패딩 방지, 기존 동작 유지).
                if (header != null) {
                    DialogueHeader(state = header, modifier = Modifier.statusBarsPadding(), onBack = onBack)
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = OceTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages) { message ->
                    when (message) {
                        is DialogueMessage.Opponent ->
                            OpponentTurn(
                                text = message.english,
                                onReplay = onReplay,
                                onToggleTranslation = onToggleTranslation,
                            )
                        is DialogueMessage.Learner ->
                            ChatBubble(text = message.english, isLearner = true)
                    }
                }
                // 상대역 발화 직전 타이핑 스켈레톤 = 메시지 뒤 마지막 아이템(프로토타입 oppSkeleton).
                if (opponentTyping) {
                    item(key = "opponentTypingSkeleton") {
                        OpponentTypingSkeleton(reduceMotion = reduceMotion)
                    }
                }
            }

            // 완료 시엔 아무 것도 렌더하지 않는다 — 완료 화면(DialogueCompletion) 없이 위 LaunchedEffect 가 곧장
            // 요약으로 이동한다. 학습자 턴에서만 하단 입력 독을 올린다.
            //
            // 슬라이드업(프로토타입 정합): 학습자 턴 진입 시 패널이 화면 하단에서 위로 올라온다
            // (transform translateY(100%)→0, 0.34s ease-out). 초기 렌더가 이미 visible=true 인 경우(프리뷰·
            // 스크린샷 테스트: turnPhase=LearnerTurn 고정)엔 AnimatedVisibility 가 애니메이션 없이 즉시 표시하므로
            // 결정적으로 렌더된다. reduceMotion 이면 진입 트랜지션을 제거해 즉시 표시한다.
            val activeTask = currentTask
            val dockSlot = dock
            AnimatedVisibility(
                visible = turnPhase == TurnPhase.LearnerTurn && activeTask != null,
                enter =
                    if (reduceMotion) {
                        EnterTransition.None
                    } else {
                        slideInVertically(
                            animationSpec = tween(340, easing = OceTheme.motion.easingOut),
                        ) { fullHeight -> fullHeight } + fadeIn(tween(340))
                    },
                exit = ExitTransition.None,
            ) {
                if (activeTask != null) {
                    // 하단에서 올라오는 입력 독 패널: surface-card 배경 + 상단 헤어라인 + 상단만 radius18.
                    // 스레드(배경 회색) 위에 얹혀 "바"로 읽힌다.
                    SessionInputPanel {
                        if (dockSlot != null) {
                            dockSlot(activeTask)
                        } else {
                            ScaffoldDock(task = activeTask, onSubmitStub = onSubmitStub)
                        }
                    }
                }
            }
        }
    }
}

/** 하단 입력 독 패널 형태(상단만 radius18). 프로토타입 `sessionInputVisible` 바 정합. */
private val SessionDockShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)

/**
 * 하단에서 올라오는 입력 독 패널. `surface.card` 배경 + 상단 헤어라인 경계 + 상단만 라운드로, 스레드(배경)
 * 위에 얹힌 "바"로 읽힌다(프로토타입 정합). 실제 슬라이드-업 진입 애니메이션(transform 0.34s)은 후속 seam.
 */
@Composable
private fun SessionInputPanel(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(SessionDockShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SessionDockShape),
    ) {
        content()
    }
}

/** 타이핑 스켈레톤 말풍선 형태 = 상대역 말풍선과 동일(좌하단 4dp 꼬리). 프로토타입 oppSkeleton 정합. */
private val OpponentSkeletonShape =
    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)

/** 스켈레톤 말풍선 폭 = 스레드의 62%(프로토타입). 좌측 마진 38dp = 아바타 30 + 간격 8. */
private const val OPPONENT_SKELETON_WIDTH_FRACTION = 0.62f
private val OpponentSkeletonMarginStart = 38.dp

/** 시머 바 높이(프로토타입 11px). 상대너비 100%·70% 두 줄. */
private val SkeletonBarHeight = 11.dp

// 시머 스윕 상수(OneClickSkeleton.kt 로직 재사용 — 폭을 몰라도 도는 상수 트래블/밴드폭 근사).
private const val SKELETON_SHIMMER_TRAVEL_PX = 1400f
private const val SKELETON_SHIMMER_BAND_PX = 400f

/**
 * 상대역 "타이핑 중" 스켈레톤 말풍선(프로토타입 oppSkeleton). 실제 발화 append 직전 잠깐 노출된다.
 * 형태는 상대역 말풍선과 동일(좌하단 4dp 꼬리·헤어라인·surface), 좌측 38dp 마진 + 스레드 62% 폭.
 * 진입은 `oc-fade-up`(opacity 0→1, translateY 8dp→0, ~300ms) 근사. 내부는 11dp 시머 바 2개(100%·70%).
 *
 * 시머 로직은 [com.jjundev.oneclickeng.ui.component.OneClickSkeleton] 을 그대로 참고했다(base=surface,
 * highlight=outlineVariant, `motion.shimmerLoopMs`=1200, LinearEasing 무한). 단, 바가 **surface(흰색) 말풍선
 * 위에** 얹히므로 바 base 는 말풍선 배경(surface)이 아니라 프로토타입 `surface-background`(=스레드 회색,
 * [androidx.compose.material3.ColorScheme.background])를 써야 흰 말풍선 위에서 보인다. shimmer 하이라이트는
 * hairline(outlineVariant). reduceMotion 정적 대체도 같은 회색([barBase])으로 자리표시자를 노출한다.
 */
@Composable
private fun OpponentTypingSkeleton(
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val bubbleBg = MaterialTheme.colorScheme.surface // 말풍선 배경(흰색, surface-card)
    val barBase = MaterialTheme.colorScheme.background // 바 기본색(스레드 회색 = 프로토 surface-background)
    val highlight = MaterialTheme.colorScheme.outlineVariant // shimmer 하이라이트 = hairline

    val barBrush: Brush =
        if (reduceMotion) {
            SolidColor(barBase)
        } else {
            val transition = rememberInfiniteTransition(label = "oppSkeleton")
            val offset by transition.animateFloat(
                initialValue = 0f,
                targetValue = SKELETON_SHIMMER_TRAVEL_PX,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(OceTheme.motion.shimmerLoopMs, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "oppShimmer",
            )
            Brush.linearGradient(
                colors = listOf(barBase, highlight, barBase),
                start = Offset(offset - SKELETON_SHIMMER_BAND_PX, 0f),
                end = Offset(offset, 0f),
            )
        }

    // 진입 페이드-업(oc-fade-up). 첫 컴포지션 후 appeared=true 로 1회 전이. reduceMotion 이면 즉시 정착.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(300, easing = OceTheme.motion.easingOut),
        label = "oppFadeUp",
    )
    val enter = if (reduceMotion) 1f else progress

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val bubbleWidth = maxWidth * OPPONENT_SKELETON_WIDTH_FRACTION
        Column(
            modifier =
                Modifier
                    .padding(start = OpponentSkeletonMarginStart)
                    .width(bubbleWidth)
                    .graphicsLayer {
                        alpha = enter
                        translationY = (1f - enter) * 8.dp.toPx()
                    }
                    .clip(OpponentSkeletonShape)
                    .background(bubbleBg)
                    .border(1.dp, highlight, OpponentSkeletonShape)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            SkeletonBar(fraction = 1f, brush = barBrush)
            SkeletonBar(fraction = 0.7f, brush = barBrush)
        }
    }
}

/** 시머 바 1줄(높이 11dp, 상대너비 [fraction], radius4). */
@Composable
private fun SkeletonBar(
    fraction: Float,
    brush: Brush,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth(fraction)
                .height(SkeletonBarHeight)
                .clip(OceTheme.shapes.radius4)
                .background(brush),
    )
}

/**
 * D1 발판 도크(04-screen-03-dialogue.md:32) = 입력 독 위 고정 [OneClickCard]. `LearnerTurn` 위상에서만
 * 렌더된다(위상 게이트 — SoT D1 "항상 노출"을 D1 현황줄 "학습자 턴 상단" 스코프로 해석; OpponentTurn·
 * Completed 에는 미표시). 과제 ≠ 대화 시각 분리를 위해 ChatBubble 아닌 카드로 제시한다.
 *
 * 카드 하단 "다음 (스텁)" 버튼은 **임시 개발용 어피던스**로 학습자 턴을 전진시킨다. 마이크(M1-04/M1-08)·
 * 텍스트(M1-06) 실 입력 독으로 교체 대상이다.
 */
@Composable
private fun ScaffoldDock(
    task: ScaffoldTask,
    onSubmitStub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(OceTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
    ) {
        ScaffoldPromptCard(task = task)
        // 임시 스텁: 실 입력 독(M1-04/M1-08)으로 교체. 터치 타깃 ≥48dp(A 접근성).
        Button(
            onClick = onSubmitStub,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = OceTheme.shapes.radius12,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
        ) {
            Text(text = "다음 (스텁)", style = OceTheme.typography.sectionLabel)
        }
    }
}

// --- 프리뷰: 3상태 × 라이트/다크(수용기준 #14, 리포 관례 CoreComponentCatalog.kt:108-129). ---
// 상태 홀더의 자동 진행 타이머에 흔들리지 않도록 무상태 DialogueTurnContent 를 고정 데이터로 렌더한다.
// 위상 데이터는 각 @Preview 진입점에서 주입하고, 렌더 골격은 이 단일 헬퍼가 담당한다.

@Composable
private fun DialoguePreviewBody(
    darkTheme: Boolean,
    turnPhase: TurnPhase,
    sessionPhase: SessionPhase,
    currentTask: ScaffoldTask?,
    messages: List<DialogueMessage>,
) {
    OceTheme(darkTheme = darkTheme) {
        DialogueTurnContent(
            messages = messages,
            turnPhase = turnPhase,
            sessionPhase = sessionPhase,
            currentTask = currentTask,
            listState = rememberLazyListState(),
            onSubmitStub = {},
            onViewSummary = {},
        )
    }
}

private val previewOpponentMessages =
    listOf(DialogueMessage.Opponent("Hi! Welcome. What can I get for you?"))

private val previewCompletedMessages =
    listOf(
        DialogueMessage.Opponent("Great. Anything else?"),
        DialogueMessage.Learner("No, that's all. Thank you."),
        DialogueMessage.Opponent("Perfect. Have a nice day!"),
    )

@Suppress("UnusedPrivateMember")
@Preview(name = "OpponentTurn Light", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun DialogueOpponentTurnLightPreview() =
    DialoguePreviewBody(false, TurnPhase.OpponentTurn, SessionPhase.InTurn, null, previewOpponentMessages)

@Suppress("UnusedPrivateMember")
@Preview(
    name = "OpponentTurn Dark",
    showBackground = true,
    widthDp = 360,
    heightDp = 640,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DialogueOpponentTurnDarkPreview() =
    DialoguePreviewBody(true, TurnPhase.OpponentTurn, SessionPhase.InTurn, null, previewOpponentMessages)

@Suppress("UnusedPrivateMember")
@Preview(name = "LearnerTurn Light", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun DialogueLearnerTurnLightPreview() =
    DialoguePreviewBody(
        false,
        TurnPhase.LearnerTurn,
        SessionPhase.InTurn,
        ScaffoldTask("따뜻한 아메리카노 한 잔 주세요."),
        previewOpponentMessages,
    )

@Suppress("UnusedPrivateMember")
@Preview(
    name = "LearnerTurn Dark",
    showBackground = true,
    widthDp = 360,
    heightDp = 640,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DialogueLearnerTurnDarkPreview() =
    DialoguePreviewBody(
        true,
        TurnPhase.LearnerTurn,
        SessionPhase.InTurn,
        ScaffoldTask("따뜻한 아메리카노 한 잔 주세요."),
        previewOpponentMessages,
    )

@Suppress("UnusedPrivateMember")
@Preview(name = "Completed Light", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun DialogueCompletedLightPreview() =
    DialoguePreviewBody(false, TurnPhase.OpponentTurn, SessionPhase.Completed, null, previewCompletedMessages)

@Suppress("UnusedPrivateMember")
@Preview(
    name = "Completed Dark",
    showBackground = true,
    widthDp = 360,
    heightDp = 640,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DialogueCompletedDarkPreview() =
    DialoguePreviewBody(true, TurnPhase.OpponentTurn, SessionPhase.Completed, null, previewCompletedMessages)

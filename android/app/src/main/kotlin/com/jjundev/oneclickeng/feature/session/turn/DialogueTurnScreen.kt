package com.jjundev.oneclickeng.feature.session.turn

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
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
 * @param onViewSummary 완료 화면 `요약 보기` CTA. 기본 no-op(요약 M2-02 미배선) — [DialogueCompletion] 주석 참조.
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

    // 신규 구현(리포 선례 없음): 메시지 추가 시 최신 말풍선으로 자동 스크롤.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
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
) {
    Scaffold(
        modifier = modifier,
        // 진행률 헤더(`N of M`) 삽입 seam. 헤더 자체는 M1-03 범위 밖(수용기준 미포함)이라 자리만 예약한다.
        // 라이브리전 politeness 는 후속 진행률/TTS 배선의 자동 진행 announce 를 위한 것으로, 지금은 빈 seam.
        topBar = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
            ) {}
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(OceTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
            ) {
                items(messages) { message ->
                    ChatBubble(
                        text = message.english,
                        isLearner = message is DialogueMessage.Learner,
                    )
                }
            }

            when {
                sessionPhase == SessionPhase.Completed ->
                    DialogueCompletion(onViewSummary = onViewSummary)

                turnPhase == TurnPhase.LearnerTurn && currentTask != null ->
                    ScaffoldDock(task = currentTask, onSubmitStub = onSubmitStub)
            }
        }
    }
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
        OneClickCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(OceTheme.spacing.lg)) {
                Text(
                    text = "이 한국어를 영어로 말해보세요",
                    style = OceTheme.typography.helper,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = task.koreanPrompt,
                    style = OceTheme.typography.body,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = OceTheme.spacing.xs),
                )
            }
        }
        // 임시 스텁: 실 입력 독(M1-04/M1-08)으로 교체. 터치 타깃 ≥48dp(A 접근성).
        Button(
            onClick = onSubmitStub,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
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

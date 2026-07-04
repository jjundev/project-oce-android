package com.jjundev.oneclickeng.feature.session.dialogue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jjundev.oneclickeng.ui.component.BlockingGateSurface
import com.jjundev.oneclickeng.ui.component.InlineErrorMode
import com.jjundev.oneclickeng.ui.component.OneClickBlockingGate
import com.jjundev.oneclickeng.ui.component.OneClickInlineError
import com.jjundev.oneclickeng.ui.component.OneClickLimitReachedPanel
import com.jjundev.oneclickeng.ui.component.OneClickProgressRing
import com.jjundev.oneclickeng.ui.component.OneClickWaitQuiz
import com.jjundev.oneclickeng.ui.component.ProgressRingMode
import com.jjundev.oneclickeng.ui.component.QuizItem
import com.jjundev.oneclickeng.ui.component.previewWaitQuizItems
import com.jjundev.oneclickeng.ui.component.selectLimitSurface
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlinx.coroutines.delay

/** 1000ms 지연 게이트(loading-quiz-interstitial.md §5): 첫 완성 턴이 이 안에 오면 퀴즈 생략 직행. */
private const val QUIZ_DELAY_GATE_MS = 1_000L

/** 생성 실패 카피(loading-quiz-interstitial.md §6). */
private const val FAILURE_COPY = "불러오지 못했어요. 다시 시도해볼까요?"

/**
 * 대본 생성 대기 화면(M1-01). 1000ms 지연 게이트를 소유하고, 그 뒤 [OneClickWaitQuiz](C20)를 띄운다.
 * "첫 완성 상대역 턴 수신 = 준비 완료"([DialogueGenState.Ready]) 시 자동전이 없이 `대화 시작하기` CTA를
 * 노출하고, 게이트 이전(1s 내)에 준비되면 퀴즈를 건너뛰고 곧장 대화로 넘어간다.
 *
 * 실패는 예외로서 인터럽트를 허용한다(loading-quiz §6). 카드 리빌 in-flight 여부에 따른 "리빌 후/즉시"
 * 세분화는 [OneClickWaitQuiz]가 in-flight 상태를 외부로 노출하지 않으므로 이 화면에서는 실패 즉시 배너로
 * 표시한다(리빌-지연 세분화는 컴포넌트 확장 후속 항목).
 *
 * @param quizEnabled 로딩 퀴즈 kill-switch(default-on, loading-quiz-interstitial.md §7). off면 게이트 뒤에도
 *   퀴즈 대신 중립 로딩만 노출한다(준비 완료 CTA는 유지).
 * @param onStartConversation 준비 완료 후 CTA 탭(또는 <1s 준비 시 자동) — 누적 턴을 대화 화면(M1-03)에 인계.
 * @param onQuizAnswered 무채점 텔레메트리 훅 — ViewModel이 `WaitQuizAnalytics`로 라우팅(실 디스패치 M4-01).
 */
@Composable
fun DialogueGeneratingScreen(
    state: DialogueGenState,
    quizItems: List<QuizItem>,
    onStartConversation: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    quizEnabled: Boolean = true,
    isOnboarding: Boolean = false,
    onQuizAnswered: (item: QuizItem, selectedIndex: Int, correct: Boolean) -> Unit = { _, _, _ -> },
    onLimitReached: (remaining: Int) -> Unit = {},
    onViewRecords: () -> Unit = {},
    onExit: () -> Unit = {},
) {
    var gatePassed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(QUIZ_DELAY_GATE_MS)
        gatePassed = true
    }

    // 게이트(1s) 이전에 준비되면 퀴즈 생략 직행. 게이트 이후엔 CTA로 유저 탭을 기다린다(자동전이 없음).
    val readyBeforeGate = state is DialogueGenState.Ready && !gatePassed
    LaunchedEffect(readyBeforeGate) {
        if (readyBeforeGate) onStartConversation()
    }

    // 한도 도달은 대기 화면 전체를 차단형 한도 패널(C18, dialogue_start_gate)로 점유한다 — 재시도 없음.
    // 인라인 로딩/퀴즈용 중앙정렬 Column 밖에서 렌더해 패널이 전체화면 스캐폴드를 온전히 차지하게 한다.
    if (state is DialogueGenState.QuotaBlocked) {
        LaunchedEffect(Unit) { onLimitReached(state.remaining) }
        // 온보딩 첫 세션 게이트면 중립 온보딩 표면, 아니면 일반 시작 게이트(라이브 스냅샷 재개는 이 경로에
        // 도달하지 않으므로 hasLiveSnapshot=false). VM 의 limit_reached 분석 표면과 동일 셀렉터를 쓴다.
        // streakDays=0: M3-04 는 streak 넛지 제외 — 소스는 M3-05/06. 패널의 streak seam 은 유지(0 → 미렌더).
        OneClickLimitReachedPanel(
            surface = selectLimitSurface(isOnboarding = isOnboarding, hasLiveSnapshot = false),
            streakDays = 0,
            onViewRecords = onViewRecords,
            modifier = modifier,
        )
        return
    }

    // 오프라인 새 세션 차단 게이트[C](M4-04, exception-states.md 결정 #4·#5) — 전체화면 점유, 인라인 로딩
    // 밖에서 렌더. 다시 시도는 연결성 재확인(재시도 어포던스는 오프라인이 transient 라 유효), 홈으로 이탈 제공.
    if (state is DialogueGenState.OfflineBlocked) {
        OneClickBlockingGate(
            surface = BlockingGateSurface.Offline,
            onRetry = onRetry,
            onHome = onExit,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier.fillMaxSize().padding(OceTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GeneratingContent(
            state = state,
            quizItems = quizItems,
            gatePassed = gatePassed,
            quizEnabled = quizEnabled,
            onStartConversation = onStartConversation,
            onRetry = onRetry,
            onQuizAnswered = onQuizAnswered,
        )
    }
}

/**
 * 인라인(비게이트) 표면 렌더 — 전체화면 게이트(한도[C]/오프라인[C])는 상위에서 early-return 으로 처리되므로
 * 여기 도달하지 않는다(망라성 유지용 no-op 브랜치). 상위 [DialogueGeneratingScreen] 의 순환 복잡도를 낮추려
 * 분리한다.
 */
@Composable
private fun GeneratingContent(
    state: DialogueGenState,
    quizItems: List<QuizItem>,
    gatePassed: Boolean,
    quizEnabled: Boolean,
    onStartConversation: () -> Unit,
    onRetry: () -> Unit,
    onQuizAnswered: (item: QuizItem, selectedIndex: Int, correct: Boolean) -> Unit,
) {
    when (state) {
        DialogueGenState.Failed ->
            OneClickInlineError(
                mode = InlineErrorMode.Recoverable,
                message = FAILURE_COPY,
                onRetry = onRetry,
                onSkip = {},
            )

        is DialogueGenState.Ready ->
            if (gatePassed) {
                if (quizEnabled) OneClickWaitQuiz(items = quizItems, onAnswered = onQuizAnswered) else SlimLoading()
                StartConversationCta(onStartConversation)
            } else {
                SlimLoading() // <1s 준비: 위 LaunchedEffect가 자동 전이 처리
            }

        DialogueGenState.Generating ->
            if (gatePassed && quizEnabled) {
                OneClickWaitQuiz(items = quizItems, onAnswered = onQuizAnswered)
            } else {
                SlimLoading()
            }

        DialogueGenState.Idle -> SlimLoading()

        // 위에서 early-return 으로 전체화면 한도/오프라인 게이트를 렌더했다(도달 불가 — 망라성 유지용).
        is DialogueGenState.QuotaBlocked -> Unit
        DialogueGenState.OfflineBlocked -> Unit
    }
}

/**
 * Stateful entry point: drives [DialogueGenerationViewModel] and renders [DialogueGeneratingScreen].
 * The generation params (level/topic/length/firstSession) are supplied by the caller — the internal
 * dev harness (M1-09) or the user-facing home entry (M3-08) — and generation begins once on entry.
 */
@Composable
fun DialogueGeneratingRoute(
    level: String,
    topic: String,
    length: Int,
    firstSession: Boolean,
    onStartConversation: () -> Unit,
    modifier: Modifier = Modifier,
    isOnboarding: Boolean = false,
    onViewRecords: () -> Unit = {},
    onExit: () -> Unit = {},
    viewModel: DialogueGenerationViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.start(level, topic, length, firstSession, isOnboarding)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val quizItems by viewModel.quizItems.collectAsStateWithLifecycle()
    DialogueGeneratingScreen(
        state = state,
        quizItems = quizItems,
        onStartConversation = onStartConversation,
        onRetry = viewModel::retry,
        modifier = modifier,
        quizEnabled = viewModel.quizEnabled,
        isOnboarding = isOnboarding,
        // Drop the tapped-option index — analytics logs only chose_correct (PII boundary).
        onQuizAnswered = { answeredItem, _, wasCorrect -> viewModel.onQuizAnswered(answeredItem, wasCorrect) },
        onLimitReached = viewModel::onLimitReached,
        onViewRecords = onViewRecords,
        onExit = onExit,
    )
}

/** 96dp 링 + 안심 카피(지연 게이트 이전·Idle의 중립 로딩 표면). */
@Composable
private fun SlimLoading() {
    OneClickProgressRing(mode = ProgressRingMode.Indeterminate)
    Text(
        text = "첫 대화를 준비하고 있어요",
        style = OceTheme.typography.helper,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = OceTheme.spacing.md),
    )
}

@Composable
private fun StartConversationCta(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(top = OceTheme.spacing.lg),
    ) {
        Text(text = "대화 시작하기", style = OceTheme.typography.sectionLabel)
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun DialogueGeneratingReadyPreview() {
    OceTheme {
        DialogueGeneratingScreen(
            state =
                DialogueGenState.Ready(
                    sessionId = "s1",
                    remaining = 2,
                    meta = null,
                    turns = emptyList(),
                ),
            quizItems = previewWaitQuizItems(),
            onStartConversation = {},
            onRetry = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun DialogueGeneratingFailedPreview() {
    OceTheme {
        DialogueGeneratingScreen(
            state = DialogueGenState.Failed,
            quizItems = previewWaitQuizItems(),
            onStartConversation = {},
            onRetry = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun DialogueGeneratingQuotaBlockedPreview() {
    OceTheme {
        DialogueGeneratingScreen(
            state = DialogueGenState.QuotaBlocked(remaining = 0),
            quizItems = previewWaitQuizItems(),
            onStartConversation = {},
            onRetry = {},
        )
    }
}

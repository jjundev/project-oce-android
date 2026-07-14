package com.jjundev.oneclickeng.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jjundev.oneclickeng.feature.review.data.ReviewPhase
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 복습 플로우 화면(Task 11) — `ReviewViewModel` 을 구독해 상태 없는 [ReviewFlowContent] 로 위임하는 얇은
 * 어댑터. 실제 렌더/분기 로직은 스크린샷·동작 테스트 seam 인 [ReviewFlowContent] 가 전담한다.
 */
@Composable
fun ReviewFlowScreen(
    onClose: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReviewFlowContent(
        state = state,
        onReveal = viewModel::reveal,
        onGrade = viewModel::grade,
        onPick = viewModel::pick,
        onNext = viewModel::next,
        onSpeak = viewModel::playTts,
        onClose = onClose,
        onRestart = viewModel::restart,
    )
}

/**
 * 상태 없는 복습 플로우 콘텐츠 — 진행 바 + 닫기 헤더(Done 이 아닐 때만) + phase 라우팅.
 *
 * **분기 순서가 안전성 계약이다**: Done → Expression 퀴즈 → (else) 플래시카드. [ReviewFlashcard] 는
 * 내부에서 `card as SavedCard.Sentence` 캐스트를 수행해 `SavedCard.Expression` 을 넘기면
 * `ClassCastException` 이 난다 — 따라서 Expression 분기가 반드시 플래시카드 분기보다 먼저 와야 한다.
 */
@Composable
internal fun ReviewFlowContent(
    state: ReviewUiState,
    onReveal: () -> Unit,
    onGrade: (Boolean) -> Unit,
    onPick: (Int) -> Unit,
    onNext: () -> Unit,
    onSpeak: (String) -> Unit,
    onClose: () -> Unit,
    onRestart: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.phase != ReviewPhase.Done) {
                val progress = if (state.total == 0) 0f else state.index.toFloat() / state.total
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = OceTheme.spacing.xl, vertical = OceTheme.spacing.md)
                        .height(4.dp)
                        .clip(OceTheme.shapes.pill)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(progress).fillMaxHeight()
                            .clip(OceTheme.shapes.pill)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = OceTheme.spacing.xl),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(
                        modifier = Modifier.size(34.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        OneClickIcon(
                            icon = OceIcon.Close,
                            contentDescription = "닫기",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            size = OceIconSize.ListDisclosure,
                        )
                    }
                    Text(
                        text = "${state.index + 1} / ${state.total}",
                        style = OceTheme.typography.helper,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when {
                state.phase == ReviewPhase.Done ->
                    ReviewSummary(
                        total = state.total,
                        done = state.done,
                        again = state.again,
                        onRestart = onRestart,
                        onClose = onClose,
                    )
                state.current?.card is SavedCard.Expression ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(OceTheme.spacing.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        ReviewExpressionQuiz(
                            card = state.current!!.card as SavedCard.Expression,
                            counter = "${state.index + 1} / ${state.total}",
                            revealed = state.phase == ReviewPhase.Reveal,
                            pick = state.pick,
                            onPick = onPick,
                            onNext = onNext,
                        )
                    }
                state.current != null ->
                    ReviewFlashcard(
                        card = state.current!!.card,
                        revealed = state.phase == ReviewPhase.Back,
                        onReveal = onReveal,
                        onGrade = onGrade,
                        onSpeak = onSpeak,
                    )
            }
        }
    }
}

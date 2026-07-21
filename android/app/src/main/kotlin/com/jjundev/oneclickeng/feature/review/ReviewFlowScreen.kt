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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jjundev.oneclickeng.feature.review.data.ReviewPhase
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.component.OneClickEmptyState
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** "닫기" 버튼 높이 — 앱 전역 primary CTA 표준(52dp, ReviewSummary.ReviewButtonHeight 와 동일). */
private val ReviewEmptyStateButtonHeight = 52.dp

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
 * 상태 없는 복습 플로우 콘텐츠 — 진행 바 + 닫기 헤더(Done 이 아닐 때만) + phase 라우팅. 풀스크린 화면이며,
 * 하단 버튼 영역만 [ReviewButtonSheet] 로 감싸 시트처럼(핸들바 + 올라오는 애니메이션) 보이게 한다.
 *
 * **분기 순서가 안전성 계약이다**: (Done ∧ total==0) → (Done ∧ total>0) → Expression 퀴즈 → (else) 플래시카드.
 * `total`(=최초 로드된 풀 크기, 인덱스가 전진해도 불변)==0 은 "방금 다 풀어서 끝남"이 아니라 "애초에 로드된
 * 풀이 비어 있었음"을 뜻한다 — due·신규·"미리 복습"(srs 있는 카드를 당겨오는 폴백, [ReviewUiState.aheadOfSchedule])
 * 세 경로를 모두 시도해도 비었다는 뜻이라, 저장한 카드가 아예 없을 때만 도달한다. 둘 다 phase==Done 이라
 * `total` 로만 구분되며, 진짜 완료(총량 N/N)와 같은 "완료!" 링·색종이 화면을 재사용하면 오해를 준다.
 * [ReviewFlashcard] 는 내부에서 `card as SavedCard.Sentence` 캐스트를 수행해 `SavedCard.Expression` 을 넘기면
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
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            if (state.phase != ReviewPhase.Done) {
                val progress = if (state.total == 0) 0f else state.index.toFloat() / state.total
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = OceTheme.spacing.xl, vertical = OceTheme.spacing.md)
                            .height(4.dp)
                            .clip(OceTheme.shapes.pill)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth(progress).fillMaxHeight()
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
                        modifier =
                            Modifier.size(34.dp).clip(CircleShape)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
                    ) {
                        if (state.aheadOfSchedule) {
                            Text(
                                text = "미리 복습",
                                style = OceTheme.typography.accrualLabel.copy(fontWeight = FontWeight.SemiBold),
                                color = OceTheme.colors.textTertiary,
                                modifier =
                                    Modifier.clip(OceTheme.shapes.pill)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = OceTheme.spacing.sm, vertical = OceTheme.spacing.xs),
                            )
                        }
                        Text(
                            text = "${state.index + 1} / ${state.total}",
                            style = OceTheme.typography.helper,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            when {
                state.phase == ReviewPhase.Done && state.total == 0 ->
                    ReviewEmptyState(onClose = onClose)
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

/**
 * 로드된 복습 풀이 애초에 비어 있을 때 표시. [FirestoreReviewSource][com.jjundev.oneclickeng.feature.review.data.FirestoreReviewSource.pool]
 * 는 due → 신규 → (그래도 비면) srs 있는 카드를 당겨오는 "미리 복습" 폴백까지 두므로, 이 화면에 실제로
 * 도달하는 경우는 저장한 카드가 아예 하나도 없을 때뿐이다. "한 번 더 복습"은 제공하지 않는다 — 저장 카드가
 * 없는 한 다시 눌러도 똑같이 빈 풀이기 때문.
 */
@Composable
private fun ReviewEmptyState(onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(top = OceTheme.spacing.xl)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            OneClickEmptyState(
                icon = OceIcon.BookmarkBorder,
                title = "아직 저장한 카드가 없어요",
                subtitle = "학습하면서 마음에 드는 표현·단어·문장을 저장하면 여기서 복습할 수 있어요.",
            )
        }
        ReviewButtonSheet {
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().height(ReviewEmptyStateButtonHeight),
                shape = OceTheme.shapes.radius12,
            ) {
                Text(
                    text = "닫기",
                    style = OceTheme.typography.sectionLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

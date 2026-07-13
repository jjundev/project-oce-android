package com.jjundev.oneclickeng.feature.records

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jjundev.oneclickeng.R
import com.jjundev.oneclickeng.feature.session.saved.CardType
import com.jjundev.oneclickeng.ui.component.EmptyStateCtaStrength
import com.jjundev.oneclickeng.ui.component.OneClickDialog
import com.jjundev.oneclickeng.ui.component.OneClickDialogVariant
import com.jjundev.oneclickeng.ui.component.OneClickEmptyState
import com.jjundev.oneclickeng.ui.component.OneClickSegmentedControl
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.ScreenEntranceState
import com.jjundev.oneclickeng.ui.foundation.TabScreenScaffold
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.foundation.rememberScreenEntrance
import com.jjundev.oneclickeng.ui.foundation.staggerReveal
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 기록 탭(M2-05). 공유 [TabScreenScaffold] 골격을 유지하고 그 [LazyListScope] 안에 평생통계 헤더(item) →
 * 세그먼트(stickyHeader) → 카드 리스트(items) / 빈 상태를 채운다. 삭제는 카드 롱프레스 → [OneClickDialog]
 * (Destructive) 확인 후에만 실행된다(undo 없음 — 다이얼로그가 안전장치).
 */
@Composable
fun RecordsScreen(
    modifier: Modifier = Modifier,
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    RecordsContent(
        state = state,
        onSelectTab = viewModel::selectTab,
        onDelete = viewModel::deleteCard,
        onLoadMore = viewModel::loadMore,
        reduceMotion = rememberReduceMotion(),
        modifier = modifier,
    )
}

/**
 * 기록 콘텐츠(stateless) — VM 없이 [RecordsUiState] 로 렌더하는 스크린샷 seam. 롱프레스로 띄운
 * [OneClickDialog] 확인 시에만 [onDelete] 를 호출한다(`pendingDeleteId` 는 회전에도 살아남는 로컬 상태).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecordsContent(
    state: RecordsUiState,
    onSelectTab: (CardType) -> Unit,
    onDelete: (SavedCardEntry) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val entrance = rememberScreenEntrance(reduceMotion)

    Box(modifier = modifier.fillMaxSize()) {
        TabScreenScaffold(titleRes = R.string.tab_records) {
            item(key = "lifetime") {
                LifetimeStatsHeader(
                    lifetime = state.lifetime,
                    animate = state.animateCountUp,
                    modifier = Modifier.staggerReveal(0, entrance).padding(bottom = OceTheme.spacing.lg),
                )
            }
            stickyHeader(key = "segments") {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(bottom = OceTheme.spacing.md),
                ) {
                    OneClickSegmentedControl(
                        options = state.tabs,
                        selected = state.selected,
                        onSelect = onSelectTab,
                        label = ::tabLabel,
                    )
                }
            }
            if (state.cards.isNotEmpty()) {
                item(key = "count") {
                    Text(
                        text = "${state.cards.size}개 · 최신순",
                        style = OceTheme.typography.helper,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.staggerReveal(1, entrance).padding(bottom = OceTheme.spacing.sm),
                    )
                }
            }
            cardList(
                state = state,
                expandedId = expandedId,
                onToggleExpand = { id -> expandedId = if (expandedId == id) null else id },
                onRequestDelete = { entry -> pendingDeleteId = entry.cardId },
                onLoadMore = onLoadMore,
                entrance = entrance,
            )
        }
    }

    val pendingEntry = pendingDeleteId?.let { id -> state.cards.firstOrNull { it.cardId == id } }
    pendingEntry?.let { entry ->
        OneClickDialog(
            title = "저장한 카드를 삭제할까요?",
            body = "이 작업은 되돌릴 수 없어요.",
            confirmLabel = "삭제",
            onConfirm = {
                onDelete(entry)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
            variant = OneClickDialogVariant.Destructive,
        )
    }
}

@Suppress("LongParameterList")
private fun LazyListScope.cardList(
    state: RecordsUiState,
    expandedId: String?,
    onToggleExpand: (String) -> Unit,
    onRequestDelete: (SavedCardEntry) -> Unit,
    onLoadMore: () -> Unit,
    entrance: ScreenEntranceState,
) {
    if (state.cards.isEmpty()) {
        if (!state.loading) {
            item(key = "empty") {
                Box(modifier = Modifier.staggerReveal(1, entrance)) {
                    EmptyState(state.selected)
                }
            }
        }
        return
    }

    items(state.cards.size, key = { state.cards[it].cardId }) { index ->
        val entry = state.cards[index]
        SavedCardRow(
            entry = entry,
            expanded = expandedId == entry.cardId,
            onToggleExpand = { onToggleExpand(entry.cardId) },
            onLongPress = { onRequestDelete(entry) },
            modifier = Modifier.staggerReveal(2 + index, entrance).padding(bottom = OceTheme.spacing.md),
        )
    }

    if (!state.endReached) {
        item(key = "load_more") {
            LaunchedEffect(state.cards.size) { onLoadMore() }
        }
    }
}

@Composable
private fun EmptyState(cardType: CardType) {
    val (icon, title, subtitle) =
        when (cardType) {
            CardType.EXPRESSION ->
                Triple(OceIcon.EditNote, "아직 저장한 표현이 없어요", "다듬은 표현을 저장하면 여기에 모여요.")
            CardType.WORD ->
                Triple(OceIcon.MatchWord, "아직 저장한 단어가 없어요", "새로 만난 단어를 저장하면 여기에 모여요.")
            CardType.SENTENCE ->
                Triple(OceIcon.FormatQuote, "아직 저장한 문장이 없어요", "마음에 든 문장을 저장하면 여기에 모여요.")
        }
    OneClickEmptyState(
        icon = icon,
        title = title,
        subtitle = subtitle,
        ctaStrength = EmptyStateCtaStrength.None,
    )
}

private fun tabLabel(cardType: CardType): String =
    when (cardType) {
        CardType.EXPRESSION -> "표현"
        CardType.WORD -> "단어"
        CardType.SENTENCE -> "문장"
    }

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun RecordsSegmentPreview() {
    OceTheme {
        OneClickSegmentedControl(
            options = RecordsUiState.TABS,
            selected = CardType.EXPRESSION,
            onSelect = {},
            label = ::tabLabel,
            modifier = Modifier.padding(OceTheme.spacing.xl),
        )
    }
}

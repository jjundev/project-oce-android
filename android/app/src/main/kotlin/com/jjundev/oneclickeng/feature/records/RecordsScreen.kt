package com.jjundev.oneclickeng.feature.records

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jjundev.oneclickeng.R
import com.jjundev.oneclickeng.feature.session.saved.CardType
import com.jjundev.oneclickeng.ui.component.EmptyStateCtaStrength
import com.jjundev.oneclickeng.ui.component.OneClickEmptyState
import com.jjundev.oneclickeng.ui.component.OneClickSegmentedControl
import com.jjundev.oneclickeng.ui.component.OneClickSnackbarHost
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceBottomNavDefaults
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.foundation.ScreenEntranceState
import com.jjundev.oneclickeng.ui.foundation.TabScreenScaffold
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.foundation.rememberScreenEntrance
import com.jjundev.oneclickeng.ui.foundation.staggerReveal
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 기록 탭(M2-05). 공유 [TabScreenScaffold] 골격을 유지하고 그 [LazyListScope] 안에 평생통계 헤더(item) →
 * 세그먼트(stickyHeader) → 카드 리스트(items) / 빈 상태를 채운다. undo 스낵바는 스캐폴드를 감싸는 overlay
 * [Box] 에 [OneClickSnackbarHost] 로 얹는다. 플로팅 BottomNav 가 뷰포트를 덮으므로 스낵바는
 * [OceBottomNavDefaults.overlayContentBottomPadding] 만큼 위로 띄운다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordsScreen(
    modifier: Modifier = Modifier,
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 삭제 → undo 스낵바(6초, Indefinite 로 띄워 OneClickSnackbar 의 자동 소멸 타이머를 발동). 액션=undo,
    // 소멸=커밋. undoBar 가 갱신되면(연속 삭제) 이전 스낵바는 취소되고 최신 삭제만 되돌릴 수 있다.
    LaunchedEffect(state.undoBar) {
        if (state.undoBar == null) return@LaunchedEffect
        val result =
            snackbarHostState.showSnackbar(
                message = "카드를 삭제했어요.",
                actionLabel = "실행취소",
                duration = SnackbarDuration.Indefinite,
            )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.undoDelete()
            SnackbarResult.Dismissed -> viewModel.commitDelete()
        }
    }

    RecordsContent(
        state = state,
        onSelectTab = viewModel::selectTab,
        onDelete = viewModel::onSwipeDelete,
        onLoadMore = viewModel::loadMore,
        snackbarHostState = snackbarHostState,
        reduceMotion = rememberReduceMotion(),
        modifier = modifier,
    )
}

/**
 * 기록 콘텐츠(stateless) — VM 없이 [RecordsUiState] 로 렌더하는 스크린샷 seam. undo 스낵바 트리거
 * (LaunchedEffect(undoBar))는 상태 소유자([RecordsScreen])에 남고, 여기선 호스트만 얹는다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecordsContent(
    state: RecordsUiState,
    onSelectTab: (CardType) -> Unit,
    onDelete: (SavedCardEntry) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    reduceMotion: Boolean = false,
) {
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
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
                onDelete = onDelete,
                onLoadMore = onLoadMore,
                entrance = entrance,
            )
        }
        OneClickSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
            bottomInset = OceBottomNavDefaults.overlayContentBottomPadding,
        )
    }
}

@Suppress("LongParameterList")
private fun LazyListScope.cardList(
    state: RecordsUiState,
    expandedId: String?,
    onToggleExpand: (String) -> Unit,
    onDelete: (SavedCardEntry) -> Unit,
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
        SwipeableCard(
            entry = entry,
            expanded = expandedId == entry.cardId,
            onToggleExpand = { onToggleExpand(entry.cardId) },
            onDelete = { onDelete(entry) },
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
private fun SwipeableCard(
    entry: SavedCardEntry,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    onDelete()
                    true
                } else {
                    false
                }
            },
        )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = { DeleteBackground() },
        modifier =
            modifier.semantics {
                customActions = listOf(CustomAccessibilityAction("삭제") { onDelete(); true })
            },
    ) {
        SavedCardRow(entry = entry, expanded = expanded, onToggleExpand = onToggleExpand)
    }
}

@Composable
private fun DeleteBackground() {
    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.error, OceTheme.shapes.radius16)
                .padding(horizontal = OceTheme.spacing.xl),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OneClickIcon(
            icon = OceIcon.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onError,
        )
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

package com.jjundev.oneclickeng.feature.records

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jjundev.oneclickeng.R
import com.jjundev.oneclickeng.feature.review.ReviewBanner
import com.jjundev.oneclickeng.feature.session.saved.CardType
import com.jjundev.oneclickeng.ui.component.EmptyStateCtaStrength
import com.jjundev.oneclickeng.ui.component.OneClickDialog
import com.jjundev.oneclickeng.ui.component.OneClickDialogVariant
import com.jjundev.oneclickeng.ui.component.OneClickEmptyState
import com.jjundev.oneclickeng.ui.component.OneClickScrollFab
import com.jjundev.oneclickeng.ui.component.OneClickSegmentedControl
import com.jjundev.oneclickeng.ui.component.OneClickShimmerPiece
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.foundation.OceBottomNavDefaults
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.ScreenEntranceState
import com.jjundev.oneclickeng.ui.foundation.TabScreenScaffold
import com.jjundev.oneclickeng.ui.foundation.refresh.OverscrollRefreshBox
import com.jjundev.oneclickeng.ui.foundation.refresh.refreshWave
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.foundation.rememberScreenEntrance
import com.jjundev.oneclickeng.ui.foundation.staggerReveal
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 기록 탭(M2-05). 공유 [TabScreenScaffold] 골격을 유지하고 그 [LazyListScope] 안에 평생통계 헤더(item) →
 * 세그먼트(stickyHeader) → 카드 리스트(items) / 빈 상태를 채운다. 삭제는 카드 롱프레스 → [OneClickDialog]
 * (Destructive) 확인 후에만 실행된다(undo 없음 — 다이얼로그가 안전장치).
 */
@Composable
fun RecordsScreen(
    onEnterReview: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    RecordsResumeEffect(onResume = viewModel::refreshOnResume)

    RecordsContent(
        state = state,
        onSelectTab = viewModel::selectTab,
        onDelete = viewModel::deleteCard,
        onLoadMore = viewModel::loadMore,
        onRefresh = viewModel::refresh,
        onEnterReview = onEnterReview,
        reduceMotion = rememberReduceMotion(),
        modifier = modifier,
    )
}

@Composable
internal fun RecordsResumeEffect(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResume by rememberUpdatedState(onResume)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
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
    onRefresh: () -> Unit,
    onEnterReview: () -> Unit = {},
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val entrance = rememberScreenEntrance(reduceMotion)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 카드 스켈레톤 최소 노출 시간(핫픽스): Firestore 캐시 히트 등으로 재로딩이 매우 빨리 끝나도, 새로고침이
    // 시작된 순간(state.refreshing 이 true 로 전이)부터 최소 RECORDS_SKELETON_MIN_VISIBLE_MS 동안은 실제
    // 카드로 바로 넘어가지 않고 스켈레톤을 유지한다(프로토 홈 `flashSituationsSkeleton` 과 동일 패턴 — 데이터
    // 도착 여부와 무관하게 고정 시간 홀드). state.refreshing 을 트리거로 쓰면 당겨서-새로고침 박스의 내부
    // 제스처/스냅 애니메이션 타이밍과 결합되지 않고, "새로고침이 실제로 시작됨"이라는 안정적 상태 계약만 본다.
    var cardsSkeletonMinHold by remember { mutableStateOf(false) }
    var skeletonJob by remember { mutableStateOf<Job?>(null) }
    fun flashCardsSkeleton() {
        skeletonJob?.cancel()
        cardsSkeletonMinHold = true
        skeletonJob =
            scope.launch {
                delay(RECORDS_SKELETON_MIN_VISIBLE_MS)
                cardsSkeletonMinHold = false
            }
    }
    // rememberUpdatedState 필수: LaunchedEffect(Unit) 은 최초 컴포지션에서 딱 한 번만 코루틴을 시작하므로,
    // state 파라미터를 직접 클로저로 캡처하면 이후 재구성으로 갱신되는 값을 절대 못 본다(고정된 첫 값에
    // 박제됨). rememberUpdatedState 로 감싸야 매 재구성마다 최신 값을 가리키는 State 를 통해 읽는다.
    val currentState = rememberUpdatedState(state)
    LaunchedEffect(Unit) {
        snapshotFlow { currentState.value.refreshing }.collect { refreshing -> if (refreshing) flashCardsSkeleton() }
    }

    OverscrollRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        TabScreenScaffold(
            titleRes = R.string.tab_records,
            listState = listState,
            headerModifier = Modifier.refreshWave(0, soft = true),
        ) {
            item(key = "lifetime") {
                LifetimeStatsHeader(
                    lifetime = state.lifetime,
                    animate = state.animateCountUp,
                    modifier =
                        Modifier
                            .staggerReveal(0, entrance)
                            .padding(bottom = OceTheme.spacing.lg)
                            .refreshWave(1, soft = true),
                )
            }
            item(key = "review_banner") {
                ReviewBanner(
                    dueCount = state.dueCount,
                    onClick = onEnterReview,
                    modifier = Modifier.staggerReveal(1, entrance).padding(bottom = OceTheme.spacing.lg),
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
            if (state.cards.isNotEmpty() && !cardsSkeletonMinHold) {
                item(key = "count") {
                    Text(
                        text = "${state.cards.size}개 · 최신순",
                        style = OceTheme.typography.helper,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.staggerReveal(2, entrance).padding(bottom = OceTheme.spacing.sm),
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
                reduceMotion = reduceMotion,
                skeletonMinHold = cardsSkeletonMinHold,
            )
        }

        // 스크롤 보조 FAB(세션 요약 화면과 동일) — 스크롤 가능할 때만 뜨고, 끝 이전엔 아래(page-down),
        // 끝에선 위(맨 위로) chevron. 플로팅 하단 내비 바로 위에 띄운다.
        val canScroll by remember {
            derivedStateOf { listState.canScrollForward || listState.canScrollBackward }
        }
        val atEnd by remember { derivedStateOf { !listState.canScrollForward } }
        if (canScroll) {
            OneClickScrollFab(
                atEnd = atEnd,
                onClick = {
                    scope.launch {
                        if (atEnd) {
                            listState.animateScrollToItem(0)
                        } else {
                            val viewportHeight = listState.layoutInfo.viewportSize.height
                            listState.animateScrollBy(viewportHeight * RECORDS_FAB_PAGE_FRACTION)
                        }
                    }
                },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = OceBottomNavDefaults.overlayContentBottomPadding + RecordsFabBottomGap),
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
    reduceMotion: Boolean,
    skeletonMinHold: Boolean,
) {
    // 로딩 중(첫 진입 또는 당겨서 재로딩으로 cards 가 비워진 직후)이거나, 실제 데이터가 이미 도착했더라도
    // 최소 노출 타이머([skeletonMinHold])가 아직 안 끝났으면 빈 상태/실제 카드 대신 카드 모양 스켈레톤을
    // 채운다 — "아무것도 없다가 갑자기 나타나는" 깜빡임과 "너무 빨리 지나가는" 두 문제를 함께 없앤다
    // (홈 추천 상황 스켈레톤과 동일 패턴).
    if (skeletonMinHold || (state.cards.isEmpty() && state.loading)) {
        recordsSkeletonItems(reduceMotion)
        return
    }

    if (state.cards.isEmpty()) {
        item(key = "empty") {
            Box(modifier = Modifier.staggerReveal(2, entrance)) {
                EmptyState(state.selected)
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
            modifier =
                Modifier
                    .staggerReveal(3 + index, entrance)
                    .padding(bottom = OceTheme.spacing.md)
                    .refreshWave(index),
        )
    }

    if (!state.endReached) {
        item(key = "load_more") {
            LaunchedEffect(state.cards.size) { onLoadMore() }
        }
    }
}

/** 카드 로딩 자리표시자([RECORDS_SKELETON_COUNT]개, [SavedCardRow] 모양 미러) — 물결도 실제 카드와 동일하게 참여. */
private fun LazyListScope.recordsSkeletonItems(reduceMotion: Boolean) {
    val indices = List(RECORDS_SKELETON_COUNT) { it }
    itemsIndexed(indices, key = { i, _ -> "skel_$i" }) { index, _ ->
        RecordsCardSkeletonRow(
            reduceMotion = reduceMotion,
            modifier = Modifier.padding(bottom = OceTheme.spacing.md).refreshWave(index),
        )
    }
}

/** [RecordsCardSkeletonRow] 테스트 태그 — [FeedbackLoadingSkeleton] 의 `FEEDBACK_LOADING_CARD_TAG` 선례와 동일 패턴. */
internal const val RECORDS_CARD_SKELETON_TAG = "records_card_skeleton"

/** [SavedCardRow] 미러 — 유형칩 + 본문 2줄(넓은/좁은) 시머. 카드와 동일한 [OneClickCard] 컨테이너·패딩. */
@Composable
private fun RecordsCardSkeletonRow(
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    OneClickCard(modifier = modifier.fillMaxWidth().testTag(RECORDS_CARD_SKELETON_TAG)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(OceTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
        ) {
            OneClickShimmerPiece(
                shape = OceTheme.shapes.pill,
                modifier = Modifier.width(RECORDS_SKELETON_CHIP_WIDTH).height(RECORDS_SKELETON_CHIP_HEIGHT),
                reduceMotion = reduceMotion,
            )
            OneClickShimmerPiece(
                shape = RecordsSkeletonLineShape,
                modifier = Modifier.fillMaxWidth(RECORDS_SKELETON_LINE_WIDE).height(16.dp),
                reduceMotion = reduceMotion,
            )
            OneClickShimmerPiece(
                shape = RecordsSkeletonLineShape,
                modifier = Modifier.fillMaxWidth(RECORDS_SKELETON_LINE_NARROW).height(14.dp),
                reduceMotion = reduceMotion,
            )
        }
    }
}

private val RecordsSkeletonLineShape = RoundedCornerShape(6.dp)
private val RECORDS_SKELETON_CHIP_WIDTH = 72.dp
private val RECORDS_SKELETON_CHIP_HEIGHT = 22.dp
private const val RECORDS_SKELETON_LINE_WIDE = 0.78f
private const val RECORDS_SKELETON_LINE_NARROW = 0.5f
private const val RECORDS_SKELETON_COUNT = 3

/** 카드 스켈레톤 최소 노출 시간(핫픽스) — 홈 `SITUATIONS_REFRESH_SKELETON_MS` 와 동일 크기, 데이터 도착과 무관하게 고정 홀드. */
internal const val RECORDS_SKELETON_MIN_VISIBLE_MS = 780L

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

/** 스크롤 보조 FAB 와 플로팅 하단 내비 사이 간격(요약 화면 SummaryFabBottomGap 선례와 정합). */
private val RecordsFabBottomGap = 16.dp

/** FAB 한 번 탭 시 내려가는 뷰포트 비율(요약 화면 SUMMARY_FAB_PAGE_FRACTION 선례와 정합). */
private const val RECORDS_FAB_PAGE_FRACTION = 0.82f

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

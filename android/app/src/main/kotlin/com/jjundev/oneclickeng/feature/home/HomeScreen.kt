package com.jjundev.oneclickeng.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jjundev.oneclickeng.core.session.SessionLevel
import com.jjundev.oneclickeng.feature.home.topic.TopicCatalog
import com.jjundev.oneclickeng.feature.home.topic.TopicSelectSheet
import com.jjundev.oneclickeng.feature.reminder.ui.HomeReminderHost
import com.jjundev.oneclickeng.feature.reminder.ui.HomeReminderViewModel
import com.jjundev.oneclickeng.feature.settings.ReminderTimeSheet
import com.jjundev.oneclickeng.ui.component.OneClickAtLimitNotice
import com.jjundev.oneclickeng.ui.component.OneClickCountUp
import com.jjundev.oneclickeng.ui.component.OneClickReminderEnabledBanner
import com.jjundev.oneclickeng.ui.component.OneClickShimmerPiece
import com.jjundev.oneclickeng.ui.component.OneClickSlider
import com.jjundev.oneclickeng.ui.component.SliderMode
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OceBottomNavDefaults
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.foundation.ScreenEntranceState
import com.jjundev.oneclickeng.ui.foundation.refresh.OverscrollRefreshBox
import com.jjundev.oneclickeng.ui.foundation.refresh.refreshWave
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.foundation.rememberScreenEntrance
import com.jjundev.oneclickeng.ui.foundation.staggerReveal
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 히어로 CTA 최소 탭 타겟(오프라인 비활성 시에도 48dp 유지, H1/H7). */
private val HeroMinHeight = 96.dp
private const val DISABLED_ALPHA = 0.38f
private const val HERO_BADGE_ALPHA = 0.2f
private const val SITUATION_ICON_BG_ALPHA = 0.12f

/** 추천 상황 스켈레톤 플래시 지속(프로토 `_flashRecSkel`) — 새로고침 780ms · 그리드 전환 300ms(둘 다름). */
private const val SITUATIONS_REFRESH_SKELETON_MS = 780L
private const val SITUATIONS_GRID_SKELETON_MS = 300L

/** 스켈레톤 라벨 줄 모서리(프로토 `.oc-sk` 6px)와 폭 비율(그리드 82%/58%). */
private val SkeletonLineShape = RoundedCornerShape(6.dp)
private const val SKELETON_LINE_WIDE = 0.82f
private const val SKELETON_LINE_NARROW = 0.58f

/** NameDrop 리빌 지속(ms) — 좌→우 물결 스윕·메타 드롭인 공용. 느리게 강조(기존 700). */
private const val REVEAL_MS = 1200

/** 좌→우 물결 글로우의 피크 alpha(강도). 기존 0.30f 대비 상향. */
private const val WAVE_PEAK_ALPHA = 0.5f

/** 물결 글로우 반경 = 카드 폭의 이 비율. */
private const val WAVE_RADIUS_FRACTION = 0.6f

/**
 * 히어로 리빌 재생 여부. 최초 컴포지션이 아니고([primed]) 새 대화 모드([resumeTopic]==null)일 때만 재생한다.
 * 새 대화 모드 내 주제 변경과 "이어하기 → 새 대화" 전환("+ 새 대화 시작", resumeTopic non-null→null)이 모두
 * 이 경우로 수렴한다. 최초 컴포지션(진입 플래시 방지)·이어하기 히어로(resumeTopic!=null)에서는 재생하지 않는다.
 */
internal fun shouldPlayHeroReveal(
    primed: Boolean,
    resumeTopic: String?,
): Boolean = primed && resumeTopic == null

/**
 * 학습(홈) 탭 — "학습 시작 허브"(M3-08, 프로토 홈 허브 완전 정합). 맥락 H1 → 인라인 지표 → 히어로 CTA
 * (선택 상황·길이·레벨 메타 + mic/▶ 배지) → 설정 변경 인라인 패널(레벨·길이) → 추천 상황 리스트 →
 * 다른 상황 고르기(상황 시트) → at-limit 보조 고지.
 *
 * 프로토 플로우 정합: 히어로 탭 = **바로 대화 생성**([onStartSession] — 세션 설정 화면 없음), 추천 행 탭 =
 * 선택만 갱신해 히어로에 반영(시트 pickTopic 과 동일), 시트 = 선택만 하고 닫힘(홈 히어로 갱신).
 * 시작은 두 경로 모두 히어로 CTA 가 소유한다.
 * [HomeReminderHost] 는 M3-07 리마인더 opt-in 오버레이(스캐폴드 밖 최상위 합성).
 */
@Composable
fun HomeScreen(
    onStartSession: (promptSeed: String, topicLabel: String, topicEmoji: String, level: String, length: Int) -> Unit,
    onResume: () -> Unit,
    onViewRecords: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    reminderViewModel: HomeReminderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reminderState by reminderViewModel.uiState.collectAsStateWithLifecycle()

    HomeResumeEffect(onResume = viewModel::refreshOnResume)

    var topicSheetVisible by remember { mutableStateOf(false) }
    var timeSheetVisible by remember { mutableStateOf(false) }
    var gridMode by remember { mutableStateOf(false) }

    // 추천 상황 스켈레톤 플래시(프로토 `_flashRecSkel`): 새로고침·그리드 전환 시 지정 시간 동안 시머 자리표시자
    // 노출 후 실제 카드로 교체. 두 트리거의 지속이 다르다(새로고침 780ms / 그리드 300ms).
    val skeletonScope = rememberCoroutineScope()
    var situationsSkeleton by remember { mutableStateOf(false) }
    var skeletonJob by remember { mutableStateOf<Job?>(null) }
    fun flashSituationsSkeleton(durationMs: Long) {
        skeletonJob?.cancel()
        situationsSkeleton = true
        skeletonJob =
            skeletonScope.launch {
                delay(durationMs)
                situationsSkeleton = false
            }
    }

    fun startWithCurrentSetup(situation: SelectedSituation?) {
        val level = state.level ?: return // #6: profile.level 미해소 동안 시작 차단(easy 누출 방지).
        val target = situation ?: return
        viewModel.onCtaTap()
        // 세션 헤더 주제 아바타 이모지는 카탈로그에서 topicId 로 조회한다. 직접 입력(custom) 상황은
        // topicId==null → 이모지 없음("")이고, 헤더는 labelKo 를 제목으로만 쓴다.
        val emoji = target.topicId?.let { id -> TopicCatalog.ALL.firstOrNull { it.id == id }?.emoji }.orEmpty()
        onStartSession(target.promptSeed, target.labelKo, emoji, level, state.length)
    }

    HomeContent(
        state = state,
        onStartLearning = { startWithCurrentSetup(state.selectedSituation) },
        onResumeContinue = {
            viewModel.onResumeContinue()
            onResume()
        },
        onResumeStartNew = viewModel::onResumeStartNew,
        onViewRecords = onViewRecords,
        onOfflineBlocked = viewModel::onOfflineBlocked,
        modifier = modifier,
        onSituationSelected = { situation ->
            // 추천 행 탭 = 선택만 갱신해 히어로에 반영(시트 pickTopic 과 동일). 시작은 히어로 CTA 가 소유한다.
            viewModel.selectSituationById(situation.id)
        },
        onRefreshSituations = {
            flashSituationsSkeleton(SITUATIONS_REFRESH_SKELETON_MS)
            viewModel.refreshSituations()
        },
        onMoreSituations = { topicSheetVisible = true },
        onSetLevel = viewModel::setLevel,
        onSetLength = viewModel::setLength,
        gridMode = gridMode,
        onToggleLayout = {
            flashSituationsSkeleton(SITUATIONS_GRID_SKELETON_MS)
            gridMode = !gridMode
        },
        situationsSkeleton = situationsSkeleton,
        reduceMotion = rememberReduceMotion(),
        showReminderBanner = reminderState.showEnabledBanner,
        reminderHour = reminderState.hour,
        reminderMinute = reminderState.minute,
        onDismissReminderBanner = reminderViewModel::dismissEnabledBanner,
        onChangeReminderTime = { timeSheetVisible = true },
    )
    if (timeSheetVisible) {
        ReminderTimeSheet(
            initialHour = reminderState.hour,
            initialMinute = reminderState.minute,
            onConfirm = { h, m ->
                reminderViewModel.setReminderTime(h, m)
                timeSheetVisible = false
            },
            onDismiss = { timeSheetVisible = false },
        )
    }
    if (topicSheetVisible) {
        TopicSelectSheet(
            selectedTopicId = state.selectedSituation?.topicId,
            onTopicChosen = { promptSeed, topicId ->
                // 프로토 pickTopic/pickCustom — 선택만 갱신하고 닫는다(시작은 홈 히어로가 소유).
                topicSheetVisible = false
                if (topicId != null) {
                    viewModel.selectSituationById(topicId)
                } else {
                    viewModel.selectCustomSituation(promptSeed)
                }
            },
            onDismiss = { topicSheetVisible = false },
        )
    }
    HomeReminderHost()
}

/**
 * [RecordsResumeEffect] 선례와 동일 패턴 — 화면이 다시 보일 때([Lifecycle.Event.ON_RESUME]) [onResume] 을
 * 호출한다. `HomeViewModel.gamification` 은 init 1회성 suspend 읽기라, 이 훅 없이는 세션 완료 후 홈으로
 * 돌아와도 "오늘 N분"이 갱신되지 않는다(홈 VM 은 탭 백스택 엔트리에 스코프돼 재생성되지 않음).
 */
@Composable
internal fun HomeResumeEffect(onResume: () -> Unit) {
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
 * 홈 콘텐츠(stateless) — VM/내비 없이 [HomeUiState] 로 렌더하는 스크린샷 seam. 콜백은 기본 no-op
 * (리스트가 비면 추천 섹션 자체가 숨는다).
 */
@Composable
internal fun HomeContent(
    state: HomeUiState,
    onStartLearning: () -> Unit,
    onResumeContinue: () -> Unit,
    onResumeStartNew: () -> Unit,
    onViewRecords: () -> Unit,
    onOfflineBlocked: () -> Unit,
    modifier: Modifier = Modifier,
    onSituationSelected: (HomeSituation) -> Unit = {},
    onRefreshSituations: () -> Unit = {},
    onMoreSituations: () -> Unit = {},
    onSetLevel: (String) -> Unit = {},
    onSetLength: (Int) -> Unit = {},
    gridMode: Boolean = false,
    onToggleLayout: () -> Unit = {},
    situationsSkeleton: Boolean = false,
    reduceMotion: Boolean = false,
    showReminderBanner: Boolean = false,
    reminderHour: Int = 20,
    reminderMinute: Int = 0,
    onDismissReminderBanner: () -> Unit = {},
    onChangeReminderTime: () -> Unit = {},
) {
    val entrance = rememberScreenEntrance(reduceMotion)
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    // 추천 상황 탭 = 히어로에 선택만 반영(프로토 pickTopic). 추천 리스트는 히어로보다 아래라, 반영이 화면
    // 밖에서 일어나 "아무 일도 안 난 것"처럼 보인다 → 반영 직후 상단(히어로)으로 스크롤해 결과를 보이고
    // ▶ CTA 로 학습을 이어가게 한다. reduce-motion 이면 애니 없이 즉시 점프.
    val onSituationTap: (HomeSituation) -> Unit = { situation ->
        onSituationSelected(situation)
        scrollScope.launch {
            if (reduceMotion) listState.scrollToItem(0) else listState.animateScrollToItem(0)
        }
    }
    OverscrollRefreshBox(
        isRefreshing = false, // 추천 상황 회전은 동기/로컬 → 최소 표시 시간이 지배
        onRefresh = onRefreshSituations, // 오직 추천 상황만 새로고침(오늘 N분/streak/hero 불변)
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = OceTheme.spacing.xl),
            contentPadding =
                PaddingValues(bottom = OceBottomNavDefaults.overlayContentBottomPadding),
        ) {
            // 리마인더 켜짐 확인 배너(프로토 reminderBanner) — 홈 최상단 in-flow.
            if (showReminderBanner) {
                item(key = "reminder_banner") {
                    OneClickReminderEnabledBanner(
                        hour = reminderHour,
                        minute = reminderMinute,
                        onDismiss = onDismissReminderBanner,
                        onChangeTime = onChangeReminderTime,
                        modifier = Modifier.staggerReveal(0, entrance).padding(top = OceTheme.spacing.lg),
                    )
                }
            }
            // 프로토타입 홈 리듬(비균일): 섹션 사이는 넉넉히(12~24dp), 상황 카드끼리는 촘촘히(8dp).
            item(key = "header") {
                Column(
                    modifier =
                        Modifier
                            .staggerReveal(1, entrance)
                            .padding(top = OceTheme.spacing.xxl)
                            .refreshWave(0, soft = true),
                    verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
                ) {
                    Text(
                        text = if (state.hasResume) "이어서 말해볼까요?" else "오늘도 영어로 말해볼까요?",
                        style = OceTheme.typography.homeTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text =
                            if (state.hasResume) {
                                "이전 대화를 이어가거나 새로 시작할 수 있어요."
                            } else {
                                "5분만 가볍게 시작해요."
                            },
                        style = OceTheme.typography.helper.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "stats") {
                StatsStrip(
                    studyMinutes = state.studyMinutes,
                    streak = state.streak,
                    reduceMotion = reduceMotion,
                    modifier =
                        Modifier
                            .staggerReveal(2, entrance)
                            .padding(top = OceTheme.spacing.md)
                            .refreshWave(1, soft = true),
                )
            }

            item(key = "hero") {
                HeroCta(
                    online = state.isOnline,
                    resumeTopic = if (state.hasResume) state.resumeTopic else null,
                    resumeTurn = state.resumeTurn,
                    resumeTotalTurns = state.resumeTotalTurns,
                    situationLabel = state.selectedSituation?.labelKo,
                    level = state.level,
                    length = state.length,
                    onClick = if (state.hasResume) onResumeContinue else onStartLearning,
                    onDisabledClick = onOfflineBlocked,
                    reduceMotion = reduceMotion,
                    modifier = Modifier.staggerReveal(3, entrance).padding(top = OceTheme.spacing.xl),
                )
            }

            if (state.hasResume) {
                item(key = "new_chat") {
                    NewChatLink(
                        onClick = onResumeStartNew,
                        modifier = Modifier.staggerReveal(4, entrance).padding(top = OceTheme.spacing.md),
                    )
                }
            } else {
                item(key = "settings_inline") {
                    SettingsInline(
                        level = state.level,
                        length = state.length,
                        onSetLevel = onSetLevel,
                        onSetLength = onSetLength,
                        modifier = Modifier.staggerReveal(4, entrance).padding(top = OceTheme.spacing.md),
                    )
                }
            }

            if (state.situations.isNotEmpty()) {
                item(key = "situations_header") {
                    SituationsHeader(
                        gridMode = gridMode,
                        onToggleLayout = onToggleLayout,
                        onRefresh = onRefreshSituations,
                        modifier = Modifier.staggerReveal(5, entrance).padding(top = OceTheme.spacing.xxl),
                    )
                }
                if (situationsSkeleton) {
                    situationsSkeletonItems(state.situations.size, gridMode, reduceMotion)
                } else {
                    situationsCardItems(state.situations, gridMode, entrance, onSituationTap)
                }
                item(key = "more_situations") {
                    MoreSituationsButton(
                        onClick = onMoreSituations,
                        modifier = Modifier.staggerReveal(11, entrance).padding(top = OceTheme.spacing.xl),
                    )
                }
            }

            if (state.atLimit) {
                item(key = "atLimit") {
                    OneClickAtLimitNotice(
                        onViewRecords = onViewRecords,
                        modifier = Modifier.staggerReveal(11, entrance).padding(top = OceTheme.spacing.md),
                    )
                }
            }
        }
    }
}

/** 추천 상황 카드 목록(그리드 2열 / 리스트) — 실제 카드. 진입 stagger 유지. */
private fun LazyListScope.situationsCardItems(
    situations: List<HomeSituation>,
    gridMode: Boolean,
    entrance: ScreenEntranceState,
    onSituationSelected: (HomeSituation) -> Unit,
) {
    if (gridMode) {
        val rows = situations.chunked(2)
        itemsIndexed(rows, key = { _, pair -> "grid_" + pair.joinToString("_") { it.id } }) { index, pair ->
            Row(
                modifier =
                    Modifier
                        .staggerReveal(6 + index, entrance)
                        .fillMaxWidth()
                        .padding(top = if (index == 0) OceTheme.spacing.lg else OceTheme.spacing.sm)
                        .height(IntrinsicSize.Min)
                        .refreshWave(index),
                horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
            ) {
                pair.forEach { situation ->
                    SituationCell(
                        situation = situation,
                        onClick = { onSituationSelected(situation) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    } else {
        itemsIndexed(situations, key = { _, item -> item.id }) { index, situation ->
            SituationRow(
                situation = situation,
                onClick = { onSituationSelected(situation) },
                modifier =
                    Modifier.staggerReveal(6 + index, entrance).padding(
                        top = if (index == 0) OceTheme.spacing.lg else OceTheme.spacing.sm,
                    ).refreshWave(index),
            )
        }
    }
}

/**
 * 추천 상황 스켈레톤(프로토 `recSkel`) — 현재 레이아웃에 맞춰 [count]개 카드를 실제 카드 골격(아이콘 40dp +
 * 라벨 줄)으로 시머 렌더한다. 새로고침·그리드 전환 플래시 중에만 카드 대신 노출([reduceMotion] 이면 정적, A7).
 */
private fun LazyListScope.situationsSkeletonItems(
    count: Int,
    gridMode: Boolean,
    reduceMotion: Boolean,
) {
    val indices = List(count) { it }
    if (gridMode) {
        itemsIndexed(indices.chunked(2), key = { i, _ -> "skel_grid_$i" }) { index, row ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = if (index == 0) OceTheme.spacing.lg else OceTheme.spacing.sm)
                        .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
            ) {
                row.forEach { _ ->
                    SituationSkeletonCell(reduceMotion, Modifier.weight(1f).fillMaxHeight())
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    } else {
        itemsIndexed(indices, key = { i, _ -> "skel_list_$i" }) { index, _ ->
            val topPad = if (index == 0) OceTheme.spacing.lg else OceTheme.spacing.sm
            SituationSkeletonRow(reduceMotion, Modifier.padding(top = topPad))
        }
    }
}

/** 리스트 스켈레톤 1행 — [SituationRow] 미러(아이콘 40dp + 라벨 한 줄 시머). 프로토 `recSkel` notGrid. */
@Composable
private fun SituationSkeletonRow(
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    OneClickCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OceTheme.spacing.lg, vertical = OceTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
        ) {
            OneClickShimmerPiece(OceTheme.shapes.radius12, Modifier.size(40.dp), reduceMotion)
            OneClickShimmerPiece(SkeletonLineShape, Modifier.weight(1f).height(14.dp), reduceMotion)
        }
    }
}

/** 그리드 스켈레톤 셀 — [SituationCell] 미러(상단 아이콘 40dp + 라벨 2줄 시머). 프로토 `recSkel` isGrid. */
@Composable
private fun SituationSkeletonCell(
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    OneClickCard(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .padding(horizontal = OceTheme.spacing.lg, vertical = OceTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
        ) {
            OneClickShimmerPiece(OceTheme.shapes.radius12, Modifier.size(40.dp), reduceMotion)
            OneClickShimmerPiece(
                shape = SkeletonLineShape,
                modifier = Modifier.fillMaxWidth(SKELETON_LINE_WIDE).height(14.dp),
                reduceMotion = reduceMotion,
            )
            OneClickShimmerPiece(
                shape = SkeletonLineShape,
                modifier = Modifier.fillMaxWidth(SKELETON_LINE_NARROW).height(11.dp),
                reduceMotion = reduceMotion,
            )
        }
    }
}

/**
 * 메인 CTA hero(프로토 정합) — brand.gradient 카드(radius.24, 흰 텍스트) + 우측 배지. 이어하기([resumeTopic]
 * 비-null)면 "이어서 대화하기" + 주제·턴 요약 + ▶ 배지, 아니면 "바로 대화 시작하기" + 선택 상황·길이·레벨
 * 메타 + mic 배지. 오프라인이면 alpha 0.38 + `semantics{ disabled() }` + 인접 헬퍼(비색 신호, H7/P8),
 * onClick 은 계측 전용 no-op 가드.
 */
@Composable
private fun HeroCta(
    online: Boolean,
    resumeTopic: String?,
    resumeTurn: Int,
    resumeTotalTurns: Int,
    situationLabel: String?,
    level: String?,
    length: Int,
    onClick: () -> Unit,
    onDisabledClick: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val reveal = remember { Animatable(0f) }
    var primed by remember { mutableStateOf(false) }

    // 히어로 리빌: 주제(라벨) 변경뿐 아니라 "이어하기 → 새 대화" 전환("+ 새 대화 시작", resumeTopic non-null→null)
    // 에서도 1회 재생한다([shouldPlayHeroReveal]). 매 실행에서 primed 를 세우되 이번이 첫 실행이었는지(wasPrimed)를
    // 잡아, 최초 컴포지션(진입 플래시)·이어하기 히어로·reduce-motion 에서는 재생하지 않는다.
    LaunchedEffect(situationLabel, resumeTopic) {
        val wasPrimed = primed
        primed = true
        if (!shouldPlayHeroReveal(wasPrimed, resumeTopic)) return@LaunchedEffect
        if (reduceMotion) return@LaunchedEffect
        reveal.snapTo(0f)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        reveal.animateTo(1f, tween(REVEAL_MS, easing = FastOutSlowInEasing))
    }

    val p = reveal.value
    val revealActive = p > 0f && p < 1f
    val sparkleAlpha = if (revealActive) sin(p * PI).toFloat().coerceIn(0f, 1f) else 0f
    val subtitle =
        if (resumeTopic != null) {
            "$resumeTopic · $resumeTurn / ${resumeTotalTurns}턴"
        } else {
            listOfNotNull(situationLabel, level?.let { SessionLevel.fromToken(it).labelKo }, "${length}턴")
                .joinToString(" · ")
        }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = HeroMinHeight)
                        .clip(OceTheme.shapes.radius24)
                        .alpha(if (online) 1f else DISABLED_ALPHA)
                        .background(OceTheme.colors.brandGradient())
                        .nameDropHaze(progress = p, color = onPrimary)
                        .then(
                            if (online) {
                                Modifier.clickable(onClick = onClick)
                            } else {
                                Modifier
                                    .clickable(onClick = onDisabledClick)
                                    .semantics { disabled() }
                            },
                        )
                        .padding(OceTheme.spacing.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
                ) {
                    Text(
                        text = if (resumeTopic != null) "이어서 대화하기" else "바로 대화 시작하기",
                        style = OceTheme.typography.homeTitle.copy(fontSize = 23.sp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    HeroMeta(subtitle = subtitle, animate = !reduceMotion && resumeTopic == null)
                }
                HeroBadge(icon = if (resumeTopic != null) OceIcon.PlayArrow else OceIcon.Mic)
            }
            if (sparkleAlpha > 0f) {
                OneClickIcon(
                    icon = OceIcon.AutoAwesome,
                    contentDescription = null,
                    tint = onPrimary,
                    modifier =
                        Modifier
                            .align(BiasAlignment(horizontalBias = lerp(-1f, 1f, p), verticalBias = 0f))
                            .graphicsLayer {
                                alpha = sparkleAlpha
                                val s = 0.7f + 0.6f * sparkleAlpha
                                scaleX = s
                                scaleY = s
                            },
                )
            }
        }
        if (!online) {
            Text(
                text = "새 대화는 인터넷 연결이 필요해요.",
                style = OceTheme.typography.helper,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 히어로 부제(선택 상황 메타 또는 이어하기 요약). [animate]=true 면 주제 변경 시 드롭인(fade+슬라이드),
 * false 면 즉시 스왑한다(reduce-motion·이어하기 모드 — 메타는 항상 갱신, 정지하지 않는다).
 */
@Composable
private fun HeroMeta(
    subtitle: String,
    animate: Boolean,
) {
    val style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold)
    val color = MaterialTheme.colorScheme.onPrimary
    if (!animate) {
        Text(text = subtitle, style = style, color = color)
        return
    }
    AnimatedContent(
        targetState = subtitle,
        transitionSpec = {
            (
                fadeIn(tween(REVEAL_MS)) +
                    slideInVertically(tween(REVEAL_MS)) { it / 3 }
            ) togetherWith fadeOut(tween(REVEAL_MS / 2))
        },
        label = "heroMetaDrop",
    ) { text ->
        Text(text = text, style = style, color = color)
    }
}

/**
 * NameDrop 물결 — [progress] 0→1 동안 흰 글로우 블롭이 카드 **좌측 밖에서 우측 밖으로** 스윕한다(가법 합성 BlendMode.Plus).
 * 진입(좌)·이탈(우)에서 sin 엔벨로프로 부드럽게 페이드하고 중앙에서 가장 강하다. [progress] 가 0/1(비활성·완료)이면 원 콘텐츠만.
 */
private fun Modifier.nameDropHaze(
    progress: Float,
    color: Color,
): Modifier =
    drawWithContent {
        drawContent()
        if (progress <= 0f || progress >= 1f) return@drawWithContent
        val waveX = lerp(-0.15f * size.width, 1.15f * size.width, progress)
        val center = Offset(waveX, size.height / 2f)
        val radius = (size.width * WAVE_RADIUS_FRACTION).coerceAtLeast(1f)
        val envelope = sin(progress * PI).toFloat().coerceIn(0f, 1f)
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            color.copy(alpha = WAVE_PEAK_ALPHA * envelope),
                            Color.Transparent,
                        ),
                    center = center,
                    radius = radius,
                ),
            radius = radius,
            center = center,
            blendMode = BlendMode.Plus,
        )
    }

/** 히어로 CTA 우측 배지 — 반투명 흰 사각 + glyph(프로토 heroIcon: mic/play_arrow). */
@Composable
private fun HeroBadge(icon: OceIcon) {
    Box(
        modifier =
            Modifier
                .size(56.dp)
                .clip(OceTheme.shapes.radius18)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = HERO_BADGE_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        OneClickIcon(
            icon = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * 설정 변경 인라인(프로토 settingsOpenHome) — "⚙ 설정 변경 ⌄" 행 탭 시 홈 안에서 레벨·길이 슬라이더
 * 패널이 펼쳐진다(별도 화면 없음). 레벨은 5-스톱 슬라이더(라벨+설명 우측 정렬), 길이는 짝수 6..20
 * 슬라이더(기본 10턴)다. [level] null(=profile.level 미해소, #6) 동안은 요약을 로딩 문구로 대체하고
 * 펼침을 막는다.
 */
@Composable
internal fun SettingsInline(
    level: String?,
    length: Int,
    onSetLevel: (String) -> Unit,
    onSetLength: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronAngle by animateFloatAsState(if (expanded) 180f else 0f, label = "settingsChevron")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md)) {
        Row(
            modifier = Modifier.clickable(enabled = level != null) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
        ) {
            OneClickIcon(
                icon = OceIcon.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = OceIconSize.FeedbackInline,
            )
            Text(
                text = "설정 변경",
                style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OneClickIcon(
                icon = OceIcon.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = OceIconSize.FeedbackInline,
                modifier = Modifier.rotate(chevronAngle),
            )
        }
        AnimatedVisibility(
            visible = expanded && level != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            OneClickCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(OceTheme.spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.lg),
                ) {
                    // 레벨: 5-스톱 슬라이더(인덱스 0..4) + 선택 라벨/설명(우측 정렬, CEFR 미노출).
                    val current = SessionLevel.fromToken(level)
                    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            SettingLabel("난이도")
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
                            ) {
                                Text(
                                    text = current.labelKo,
                                    style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = current.descKo,
                                    style = OceTheme.typography.helper,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                        OneClickSlider(
                            value = current.ordinal.toFloat(),
                            onValueChange = { onSetLevel(SessionLevel.entries[it.roundToInt()].token) },
                            mode =
                                SliderMode.Stepped(
                                    range = 0..SessionLevel.entries.lastIndex,
                                    step = 1,
                                    labelFormatter = { SessionLevel.entries[it].labelKo },
                                ),
                            showValueLabel = false,
                        )
                    }
                    // 길이: 짝수 6..20 슬라이더 + "N턴"/설명(우측 정렬, 레벨 컨트롤과 동일 레이아웃).
                    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            SettingLabel("대화 길이")
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
                            ) {
                                Text(
                                    text = "${length}턴",
                                    style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = lengthDescKo(length),
                                    style = OceTheme.typography.helper,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                        OneClickSlider(
                            value = length.toFloat(),
                            onValueChange = { onSetLength(it.roundToInt()) },
                            mode =
                                SliderMode.Stepped(
                                    range = HomeViewModel.MIN_LENGTH..HomeViewModel.MAX_LENGTH,
                                    step = HomeViewModel.LENGTH_STEP,
                                    labelFormatter = { "${it}턴" },
                                ),
                            showValueLabel = false,
                        )
                    }
                }
            }
        }
    }
}

/** 펼친 설정의 소형 섹션 라벨(레벨/길이). */
@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        style = OceTheme.typography.helper.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 대화 길이(짝수 6..20) 구간별 짧은 설명 — 레벨 설명과 대칭되는 우측 정렬 보조 문구. */
private fun lengthDescKo(length: Int): String =
    when {
        length <= 8 -> "짧고 가볍게 대화해요"
        length <= 12 -> "적당한 길이로 대화해요"
        length <= 16 -> "여유 있게 대화해요"
        else -> "길고 깊이 있게 대화해요"
    }

/**
 * 게임화 요약 스트립(H2) — 인라인 `🕐 오늘 N분 · 🔥 N일 연속`(프로토타입 정합, pill 아님). streak 0 은 숨긴다.
 * 학습시간 미로딩(null)이고 streak 0 이면 스트립 자체를 렌더하지 않는다.
 */
@Composable
private fun StatsStrip(
    studyMinutes: Int?,
    streak: Int,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    if (studyMinutes == null && streak <= 0) return
    val statStyle = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
    ) {
        if (studyMinutes != null) {
            OneClickIcon(
                icon = OceIcon.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = OceIconSize.FeedbackInline,
            )
            // 프로토 "오늘 N분"(gamification-emphasis.md:131) — 슬롯머신 카운트업으로 0→오늘 분 롤업.
            OneClickCountUp(
                target = studyMinutes,
                format = { "오늘 ${it}분" },
                reduceMotion = reduceMotion,
                style = statStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (studyMinutes != null && streak > 0) {
            Text(
                text = "·",
                style = statStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (streak > 0) {
            OneClickIcon(
                icon = OceIcon.LocalFireDepartment,
                contentDescription = null,
                tint = OceTheme.colors.gameStreak,
                size = OceIconSize.FeedbackInline,
            )
            // 연속 학습일도 카운트업으로 0→N 롤업(프로토 "N일 연속").
            OneClickCountUp(
                target = streak,
                format = { "${it}일 연속" },
                reduceMotion = reduceMotion,
                style = statStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "+ 새 대화 시작" 링크(이어하기 CTA 하위 보조) — 프로토 discardSnapshot(스냅샷 폐기, 내비 없음). */
@Composable
private fun NewChatLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
    ) {
        Text(
            text = "+",
            style = OceTheme.typography.helper.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "새 대화 시작",
            style = OceTheme.typography.helper.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** "추천 상황" 섹션 헤더 — 라벨 + 레이아웃 토글(리스트↔그리드) + 새로고침. */
@Composable
private fun SituationsHeader(
    gridMode: Boolean,
    onToggleLayout: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
    ) {
        Text(
            text = "추천 상황",
            style = OceTheme.typography.sectionLabel.copy(fontWeight = FontWeight.ExtraBold, fontSize = 17.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier =
                Modifier
                    .clip(OceTheme.shapes.radius12)
                    .clickable(onClick = onToggleLayout)
                    .padding(OceTheme.spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            OneClickIcon(
                icon = if (gridMode) OceIcon.ViewAgenda else OceIcon.GridView,
                contentDescription = if (gridMode) "목록으로 보기" else "그리드로 보기",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = OceIconSize.ListDisclosure,
            )
        }
        Row(
            modifier = Modifier.clickable(onClick = onRefresh),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
        ) {
            OneClickIcon(
                icon = OceIcon.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                size = OceIconSize.ListDisclosure,
            )
            Text(
                text = "새로고침",
                style = OceTheme.typography.helper,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 추천 상황 1행 — 카드(선행 아이콘 + 라벨 + chevron). 탭 = 선택만 갱신해 히어로에 반영(시작은 히어로 CTA). */
@Composable
private fun SituationRow(
    situation: HomeSituation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OneClickCard(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OceTheme.spacing.lg, vertical = OceTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(OceTheme.shapes.radius12)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = SITUATION_ICON_BG_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                OneClickIcon(
                    icon = situation.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    size = OceIconSize.ListDisclosure,
                )
            }
            Text(
                text = situation.labelKo,
                style = OceTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            OneClickIcon(
                icon = OceIcon.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = OceIconSize.ListDisclosure,
            )
        }
    }
}

/** 그리드 셀 — 컴팩트 카드(상단 아이콘 박스 + 라벨 최대 2줄, chevron 없음). 탭 = 선택만 갱신해 히어로에 반영. */
@Composable
private fun SituationCell(
    situation: HomeSituation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OneClickCard(modifier = modifier.clickable(onClick = onClick)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .padding(horizontal = OceTheme.spacing.lg, vertical = OceTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(OceTheme.shapes.radius12)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = SITUATION_ICON_BG_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                OneClickIcon(
                    icon = situation.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    size = OceIconSize.ListDisclosure,
                )
            }
            Text(
                text = situation.labelKo,
                style = OceTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "다른 상황 고르기" — 추천 상황 리스트 하단, 흰 카드 + hairline 보더(프로토 정합). */
@Composable
private fun MoreSituationsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OneClickCard(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = OceTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OneClickIcon(
                icon = OceIcon.GridView,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = OceIconSize.ListDisclosure,
            )
            Text(
                text = "다른 상황 고르기",
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

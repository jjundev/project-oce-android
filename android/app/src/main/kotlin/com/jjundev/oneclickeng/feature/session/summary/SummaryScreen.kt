@file:Suppress("TooManyFunctions") // 화면 = 다수의 작은 private 섹션 컴포저블 합성(SlimFeedbackSheet 선례).

package com.jjundev.oneclickeng.feature.session.summary

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.feature.session.feedback.FeedbackLoadingSkeleton
import com.jjundev.oneclickeng.ui.component.InlineErrorMode
import com.jjundev.oneclickeng.ui.component.OneClickConfettiBurst
import com.jjundev.oneclickeng.ui.component.OneClickCountUp
import com.jjundev.oneclickeng.ui.component.OneClickEmptyState
import com.jjundev.oneclickeng.ui.component.OneClickInlineError
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * 화면 05 — 세션 요약(M2-02). 정본: 04-screen-05-summary.md · gamification-emphasis.md §4 ·
 * dialogue-learning-flow.md §9. 무상태 컴포넌트 — 상태는 [SummaryViewModel]/[SummaryCoordinator] 소유.
 *
 * 레이아웃(SM1): 종합 점수 hero(56sp) → `sectionGap`(24dp) → 적립 스트립 **별도 블록**(같은 행 금지) →
 * 5섹션. 로컬 즉시 블록(점수·하이라이트·북마크·스트립)은 스켈레톤 없이 즉시 렌더하고, 요약 SSE 3섹션
 * (표현/단어/코칭)은 **번들 단위 단일 스켈레톤**([SectionBundle.BundleLoading]) 하나로 로딩하다가 `done`
 * 이후 섹션별로 렌더/재시도한다(§9).
 *
 * **섹션 순서 note:** 표현·단어(SSE 상단부)는 번들 로딩(단일 스켈레톤)을 공유해 연속 배치하고, 로컬 북마크
 * 섹션을 그 뒤에 둔다. **코칭은 화면 최하단**([CoachingArea], 북마크 뒤)에 별도 배치하며, 잘한 점/다음엔
 * 이렇게를 **각각 별도 카드**로 분리한다(사용자 요청 정합). 로딩 단계엔 상단 단일 스켈레톤이 SSE 전체를
 * 대표하고, 코칭 카드는 `done`(Sectioned) 이후에만 하단에 렌더된다.
 */
internal const val GOOGLE_SAVE_PROMPT_DELAY_MS = 500L
internal const val SUMMARY_SCROLL_CONTENT_TAG = "summary_scroll_content"

@Composable
fun SummaryScreen(
    state: SummaryState,
    onRetry: (SummarySection) -> Unit,
    onToggleSaveWord: (Int) -> Unit,
    onToggleSaveExpression: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onDone: (() -> Unit)? = null,
    doneLabel: String = "완료",
    onScrollEndReached: (() -> Unit)? = null,
    // 진입 폭죽 발사 게이트 — 화면 전환 슬라이드가 끝난 뒤 true 로 넘어온다(요구). 기본 true(전환 없는 진입·프리뷰·테스트).
    startConfetti: Boolean = true,
) {
    // "더 보기" 접힘 상태(#15): 섹션별 독립, 초기 접힘. 기본 표시 [COLLAPSED_PREVIEW]개.
    val expanded = remember { mutableStateMapOf<SummarySection, Boolean>() }
    val scrollState = rememberScrollState()
    val currentOnScrollEndReached by rememberUpdatedState(onScrollEndReached)

    if (onScrollEndReached != null) {
        LaunchedEffect(scrollState) {
            snapshotFlow {
                when {
                    scrollState.maxValue <= 0 -> false
                    scrollState.value == scrollState.maxValue -> true
                    scrollState.value < scrollState.maxValue -> false
                    else -> null
                }
            }.filterNotNull()
                .distinctUntilChanged()
                .collectLatest { isAtBottom ->
                    if (isAtBottom) {
                        delay(GOOGLE_SAVE_PROMPT_DELAY_MS)
                        if (scrollState.maxValue > 0 && scrollState.value == scrollState.maxValue) {
                            currentOnScrollEndReached?.invoke()
                        }
                    }
                }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // 스크롤 영역 — weight 로 남은 높이를 채운다. 스크롤 콘텐츠 위에 스크롤 보조 FAB 를 오버레이하고,
            // 완료 풋터는 이 Box 아래 형제로 두어 FAB 가 풋터 바로 위에 뜨도록 한다(프로토 summaryFab 정합).
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .testTag(SUMMARY_SCROLL_CONTENT_TAG)
                            .padding(OceTheme.spacing.sheetPadding),
                    verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sectionGap),
                ) {
                    SummaryTitleBar()
                    ScoreHero(state.totalScore, state.isFirstSession)
                    AccrualCard(state.accrual)
                    StreakCaption(state.accrual.streakDays)
                    state.highlight?.let { HighlightSection(it) }
                    SseBundle(
                        bundle = state.bundle,
                        expanded = expanded,
                        onRetry = onRetry,
                        savedWordIndices = state.savedWordIndices,
                        savedExprIndices = state.savedExprIndices,
                        onToggleSaveWord = onToggleSaveWord,
                        onToggleSaveExpression = onToggleSaveExpression,
                    )
                    BookmarkSection(state.bookmarks)
                    CoachingArea(bundle = state.bundle, onRetry = onRetry)
                }
                // 스크롤 보조 FAB — 완료 풋터가 있을 때만(온보딩 GoogleSavePromptSheet 오버레이 케이스 제외).
                if (onDone != null) {
                    SummaryScrollFab(
                        scrollState = scrollState,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = SummaryFabBottomGap),
                    )
                }
            }
            // 완료 풋터 — 항상 화면 하단에 고정(스크롤과 무관, 프로토 정합). [onDone] null(온보딩 첫 세션의
            // GoogleSavePromptSheet 오버레이 케이스)이면 미표시.
            if (onDone != null) {
                SummaryDoneFooter(label = doneLabel, onDone = onDone)
            }
        }
        // 진입 폭죽(프로토 fireConfetti) — 점수 있을 때만, 장식 오버레이(입력 미차단·reduce-motion 미발사).
        if (state.totalScore != null) {
            OneClickConfettiBurst(modifier = Modifier.matchParentSize(), start = startConfetti)
        }
    }
}

/** 고정 완료 풋터(프로토 flex:none 하단 바) — 상단 hairline + 흰 배경 + primary 52dp 버튼. */
@Composable
private fun SummaryDoneFooter(
    label: String,
    onDone: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Button(
            onClick = onDone,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = OceTheme.spacing.xl,
                        vertical = OceTheme.spacing.lg,
                    )
                    .height(DoneButtonHeight),
            shape = OceTheme.shapes.radius12,
        ) {
            Text(text = label, style = OceTheme.typography.sectionLabel)
        }
    }
}

/**
 * 스크롤 보조 FAB(프로토 summaryFab) — 완료 풋터 바로 위에 떠 있는 원형 버튼. 끝에 닿기 전엔 아래 chevron
 * (탭 = 뷰포트 [SUMMARY_FAB_PAGE_FRACTION] 만큼 page-down), 끝에 닿으면 위 chevron(탭 = 맨 위로). 시각은
 * [MoreChevron] 원형 버튼(흰 서피스 + hairline)과 동일 규칙에 그림자만 더한다. 스크롤이 불가능하면
 * (내용이 뷰포트에 다 들어와 [ScrollState.maxValue] == 0) 죽은 어포던스가 되므로 렌더하지 않는다.
 */
@Composable
private fun SummaryScrollFab(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    if (scrollState.maxValue <= 0) return
    val scope = rememberCoroutineScope()
    val tolerancePx = with(LocalDensity.current) { SummaryFabAtEndTolerance.roundToPx() }
    val atEnd by remember(tolerancePx) {
        derivedStateOf { scrollState.value >= scrollState.maxValue - tolerancePx }
    }
    Box(
        modifier =
            modifier
                .size(SummaryFabSize)
                .shadow(SummaryFabElevation, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable {
                    scope.launch {
                        if (atEnd) {
                            scrollState.animateScrollTo(0)
                        } else {
                            scrollState.animateScrollBy(scrollState.viewportSize * SUMMARY_FAB_PAGE_FRACTION)
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        OneClickIcon(
            icon = OceIcon.ExpandMore,
            contentDescription = if (atEnd) "맨 위로" else "아래로 스크롤",
            tint = MaterialTheme.colorScheme.primary,
            size = SummaryFabIconSize,
            modifier = Modifier.rotate(if (atEnd) 180f else 0f),
        )
    }
}

/** ⓪ 상단 타이틀바 — 프로토타입 realization-SoT: 중앙 정렬 "세션 요약"(축하형 헤더). */
@Composable
private fun SummaryTitleBar() {
    Text(
        text = "세션 요약",
        // 프로토타입 700w 16sp 중앙 타이틀 = body(16sp) + Bold(700). 전용 토큰 없이 weight 변형으로 정확 일치.
        style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * ① 종합 점수 hero — 중앙 정렬 축하형(프로토타입 realization-SoT). 위계: 격려 헤드라인(`summaryHeadline`
 * 20sp) 우선 → 점수 `scoreDisplay` 56sp brand.primary + "작문 점수" 라벨 보조(ux-writing "격려 우선·점수
 * 보조"). 점수 없으면(전 턴 스킵) 중립 안내만 중앙 배치.
 */
@Composable
private fun ScoreHero(
    totalScore: Int?,
    isFirstSession: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
    ) {
        if (totalScore != null) {
            Text(
                // 프로토 summaryTitle: 온보딩(첫 세션) / 일반 변형(점수 비의존).
                text = if (isFirstSession) "영어로 첫 대화를 끝냈어요!" else "오늘도 해냈어요!",
                style = OceTheme.typography.summaryHeadline,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            // 가이드 부제(프로토 summarySub) — 첫 세션/일반 변형.
            Text(
                text =
                    if (isFirstSession) {
                        "짧아도 진짜 영어로 말한 거예요. 아래에서 오늘의 수확을 확인해보세요."
                    } else {
                        "탄탄한 문장이 많았어요. 아래에서 오늘의 수확을 확인해보세요."
                    },
                style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            // 프로토 scoreVal 카운트업(0→점수) — reduce-motion 스냅은 OneClickCountUp 내부(F4).
            OneClickCountUp(
                target = totalScore,
                from = 0,
                unit = "",
                static = false,
                style = OceTheme.typography.scoreDisplay,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = OceTheme.spacing.md),
            )
            Text(
                text = "작문 점수",
                style = OceTheme.typography.sectionLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text =
                    if (isFirstSession) {
                        "첫 대화를 끝까지 해냈어요. 그거면 충분해요. 다음엔 한 문장 더 말해볼까요?"
                    } else {
                        "이번 세션은 점수를 낼 수 없었어요. 다음엔 한 문장이라도 말해볼까요?"
                    },
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * ② 적립 카드 — 종합 점수와 **별도 블록**(같은 행 금지, gamification §4.2). 프로토타입 realization-SoT: 칩 나열이
 * 아니라 **3열 성취 카드**(아이콘+숫자+라벨, 세로 구분선). 순서 streak🔥 → 학습시간 → XP(§4.3). [AccrualStrip.animate]
 * 가 true(M3-05 실값 착지)면 세 지표를 슬롯머신 카운트업(I3, §4.4)으로 굴린다: XP 0→델타, 학습시간 오늘 누계
 * before→after, streak 0→N(단 same-day 2번째 세션은 정적). animate=false(주입 초기/EMPTY)면 정적 스냅.
 * reduce-motion 은 [OneClickCountUp] 내부에서 스냅(F4). 3열 그리드라 학습시간 열도 항상 노출한다(레이아웃 고정).
 */
@Composable
private fun AccrualCard(accrual: AccrualStrip) {
    val beforeMin = accrual.todayStudySecondsBefore?.div(SECONDS_PER_MINUTE)
    val afterMin = accrual.todayStudySecondsAfter / SECONDS_PER_MINUTE
    // before 불명(이관/롤오버=null) 또는 before·after 동일 분이면 학습시간 정적 스냅(§4.4 이관 예외·죽은 롤 방지).
    val studyStatic = beforeMin == null || beforeMin == afterMin || !accrual.animate
    OneClickCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = OceTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccrualMetric(
                icon = OceIcon.LocalFireDepartment,
                tint = OceTheme.colors.gameStreak,
                from = 0,
                target = accrual.streakDays,
                unit = "일",
                static = accrual.streakStatic || !accrual.animate,
                label = "연속 학습",
                modifier = Modifier.weight(1f),
            )
            MetricDivider()
            AccrualMetric(
                icon = OceIcon.Schedule,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                from = beforeMin ?: afterMin,
                target = afterMin,
                unit = "분",
                static = studyStatic,
                label = "학습 시간",
                modifier = Modifier.weight(1f),
            )
            MetricDivider()
            AccrualMetric(
                icon = OceIcon.Bolt,
                tint = MaterialTheme.colorScheme.primary,
                from = 0,
                target = accrual.xp,
                unit = " XP",
                static = !accrual.animate,
                label = "획득 경험치",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 적립 카드 1열 — 아이콘 → 숫자(카운트업) → 라벨 세로 스택. 숫자는 `homeTitle`(ExtraBold), 라벨은 `helper`. */
@Composable
private fun AccrualMetric(
    icon: OceIcon,
    tint: Color,
    from: Int,
    target: Int,
    unit: String,
    static: Boolean,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
    ) {
        OneClickIcon(icon = icon, contentDescription = null, tint = tint, size = AccrualIconSize)
        OneClickCountUp(
            target = target,
            from = from,
            unit = unit,
            static = static,
            style = OceTheme.typography.accrualValue,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = OceTheme.typography.accrualLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 스트릭 캡션 — 적립 카드 아래 중앙(프로토 streakLine). 🔥 아이콘 + "N일째 — {격려}". 이모지 미사용(P16)
 * 이라 [OceIcon.LocalFireDepartment] 벡터로 렌더. 격려 변형은 프로토 데모 2종(1일=좋은 시작 / 7일=일주일
 * 완성)을 스트릭 구간으로 일반화한다(그 외 구간 카피는 ux-writing 스펙 확정 대상).
 */
@Composable
private fun StreakCaption(streakDays: Int) {
    val cheer = if (streakDays >= WEEK_STREAK) "일주일 완성했어요!" else "좋은 시작이에요!"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OneClickIcon(
            icon = OceIcon.LocalFireDepartment,
            contentDescription = null,
            tint = OceTheme.colors.gameStreak,
            size = StreakCaptionIconSize,
        )
        Text(
            text = " ${streakDays}일째 — $cheer",
            style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 적립 카드 열 사이 세로 hairline 구분선. */
@Composable
private fun MetricDivider() {
    Box(
        modifier =
            Modifier
                .width(1.dp)
                .height(AccrualDividerHeight)
                .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * ③ 하이라이트(가장 잘한 순간, ≤1) — 로컬 즉시. 프로토타입 realization-SoT: 카드 안 "✨ 가장 잘한 순간" 배지 pill +
 * 사용자 발화(인용부호·강조) + 한국어 프롬프트. coaching 편승 보강은 M2-01 스키마 확정 후(#6).
 */
@Composable
private fun HighlightSection(highlight: HighlightTurn) {
    SectionScaffold(title = "하이라이트") {
        OneClickCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(OceTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
            ) {
                HighlightBadge()
                Text(
                    text = "“${highlight.userText}”",
                    style = OceTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // 설명줄 = rationale("왜 잘했는지", 프로토타입 정본). 미배선(null)이면 koreanPrompt 폴백(#6).
                Text(
                    text = highlight.rationale ?: highlight.koreanPrompt,
                    style = OceTheme.typography.helper,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "✨ 가장 잘한 순간" 배지 pill — brand 틴트 배경(alpha) + primary 텍스트. ✨는 이모지(사용자 요청). */
@Composable
private fun HighlightBadge() {
    Text(
        text = "✨ 가장 잘한 순간",
        style = OceTheme.typography.tabActive,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .clip(OceTheme.shapes.pill)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = HIGHLIGHT_BADGE_ALPHA))
                .padding(horizontal = OceTheme.spacing.md, vertical = OceTheme.spacing.xs),
    )
}

/**
 * SSE 번들 상단부(표현/단어). BundleLoading → 단일 스켈레톤(A6 polite). Sectioned → 섹션별 렌더.
 * QuotaBlocked → 중립 문구(재시도 없음). 코칭은 [CoachingArea] 로 화면 최하단에 별도 배치한다.
 */
@Composable
private fun SseBundle(
    bundle: SectionBundle,
    expanded: MutableMap<SummarySection, Boolean>,
    onRetry: (SummarySection) -> Unit,
    savedWordIndices: Set<Int>,
    savedExprIndices: Set<Int>,
    onToggleSaveWord: (Int) -> Unit,
    onToggleSaveExpression: (Int) -> Unit,
) {
    when (bundle) {
        is SectionBundle.BundleLoading ->
            // 턴 피드백 시트 스켈레톤과 동일: 섹션 제목은 즉시 노출하고 본문만 시머([FeedbackLoadingSkeleton]).
            Column(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sectionGap),
            ) {
                SectionScaffold(title = "자연스러운 표현") { FeedbackLoadingSkeleton() }
                SectionScaffold(title = "새 단어") { FeedbackLoadingSkeleton() }
            }

        is SectionBundle.QuotaBlocked ->
            Text(
                text = "오늘 준비할 수 있는 요약을 모두 사용했어요. 기록은 그대로 남아 있어요.",
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

        is SectionBundle.Sectioned ->
            Column(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sectionGap),
            ) {
                ExpressionSection(bundle.expression, expanded, onRetry, savedExprIndices, onToggleSaveExpression)
                WordSection(bundle.word, expanded, onRetry, savedWordIndices, onToggleSaveWord)
            }
    }
}

/**
 * 코칭 영역 — 화면 **최하단**(북마크 뒤) 배치. bundle 이 Sectioned 일 때만 코칭 섹션을 렌더한다(로딩/한도 단계는
 * 상단 [SseBundle] 스켈레톤·문구가 대표하므로 여기선 미표시).
 */
@Composable
private fun CoachingArea(
    bundle: SectionBundle,
    onRetry: (SummarySection) -> Unit,
) {
    when (bundle) {
        is SectionBundle.Sectioned -> CoachingSection(bundle.coaching, onRetry)
        // 로딩 중에도 코칭 제목은 즉시 노출 + 본문 시머(피드백 시트 정합). QuotaBlocked 는 상단 문구가 대표.
        is SectionBundle.BundleLoading -> SectionScaffold(title = "코칭") { FeedbackLoadingSkeleton() }
        is SectionBundle.QuotaBlocked -> Unit
    }
}

/**
 * ④ 표현 개선(≤8) — Ready 카드 + "더 보기", Failed 인라인 재시도, Loading(재시도 중) 스켈레톤. Ready 카드에만
 * 저장 토글이 붙는다(M2-04). 저장 어포던스가 Ready 에만 있어 retry(Failed 전용) 재생성과 인덱스가 충돌하지 않는다.
 */
@Composable
private fun ExpressionSection(
    stateOf: SummarySectionState<List<ExpressionCard>>,
    expanded: MutableMap<SummarySection, Boolean>,
    onRetry: (SummarySection) -> Unit,
    savedIndices: Set<Int>,
    onToggleSave: (Int) -> Unit,
) {
    SectionScaffold(title = "자연스러운 표현", count = sectionCount(stateOf)) {
        SectionBody(
            section = SummarySection.Expression,
            stateOf = stateOf,
            onRetry = onRetry,
            emptyTitle = "이번 세션엔 다듬을 표현이 없었어요",
            emptySubtitle = "다음 대화에서 새 표현을 만나볼까요?",
        ) { items ->
            ExpandableCards(SummarySection.Expression, items, expanded) { index, card ->
                SavableCardRow(saved = index in savedIndices, onToggle = { onToggleSave(index) }) {
                    ExpressionCardBody(card)
                }
            }
        }
    }
}

/** ⑤ 신규 단어(≤12) — 동일 패턴(저장 토글 포함). */
@Composable
private fun WordSection(
    stateOf: SummarySectionState<List<WordCard>>,
    expanded: MutableMap<SummarySection, Boolean>,
    onRetry: (SummarySection) -> Unit,
    savedIndices: Set<Int>,
    onToggleSave: (Int) -> Unit,
) {
    SectionScaffold(title = "새 단어", count = sectionCount(stateOf)) {
        SectionBody(
            section = SummarySection.Word,
            stateOf = stateOf,
            onRetry = onRetry,
            emptyTitle = "이번 세션엔 새 단어가 없었어요",
            emptySubtitle = "조금 더 어려운 주제도 시도해볼 수 있어요.",
        ) { items ->
            ExpandableCards(SummarySection.Word, items, expanded) { index, card ->
                SavableCardRow(saved = index in savedIndices, onToggle = { onToggleSave(index) }) {
                    WordCardBody(card)
                }
            }
        }
    }
}

/**
 * ⑥ 코칭(잘한 점/다음에 다듬을 점) — 프로토 정합: **흰 카드 1장** 안에 두 블록(✨잘한 점 → hairline 구분선 →
 * 다듬을 점)을 담는다. 각 블록은 아이콘+색상 라벨 + 본문 줄들(개행 분리). 빈 문자열 블록은 숨김(Rule 4).
 * Failed 재시도.
 */
@Composable
private fun CoachingSection(
    stateOf: SummarySectionState<Coaching>,
    onRetry: (SummarySection) -> Unit,
) {
    SectionScaffold(title = "코칭") {
        when (stateOf) {
            is SummarySectionState.Loading -> FeedbackLoadingSkeleton()
            is SummarySectionState.Failed ->
                RetryRow(SummarySection.Coaching, stateOf.canRetry, onRetry)
            is SummarySectionState.Ready ->
                OneClickCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(OceTheme.spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
                    ) {
                        if (!stateOf.value.hasPositive && !stateOf.value.hasToImprove) {
                            CoachingBlock(
                                icon = OceIcon.AutoAwesome,
                                label = "잘 마쳤어요",
                                accent = OceTheme.colors.feedbackNaturalAccent,
                                body = "이번 세션은 코칭할 거리가 많지 않았어요. 잘 마쳤어요!",
                            )
                        } else {
                            if (stateOf.value.hasPositive) {
                                CoachingBlock(
                                    icon = OceIcon.AutoAwesome,
                                    label = "잘한 점",
                                    accent = OceTheme.colors.feedbackNaturalAccent,
                                    body = stateOf.value.positive,
                                )
                            }
                            if (stateOf.value.hasPositive && stateOf.value.hasToImprove) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant),
                                )
                            }
                            if (stateOf.value.hasToImprove) {
                                CoachingBlock(
                                    icon = OceIcon.Spellcheck,
                                    label = "다음에 다듬을 점",
                                    accent = OceTheme.colors.feedbackCorrectAccent,
                                    body = stateOf.value.toImprove,
                                )
                            }
                        }
                    }
                }
        }
    }
}

/** ⑦ 북마크 문장(≤8, 최신순) — 로컬 즉시. 빈 리스트면 빈 상태(M2-04 착지 전 기본). 저장 토글 표시 전용. */
@Composable
private fun BookmarkSection(bookmarks: List<BookmarkCard>) {
    val count = bookmarks.size.takeIf { it > 0 }?.let { "${it}개 · 최신순" }
    SectionScaffold(title = "북마크 문장", count = count) {
        if (bookmarks.isEmpty()) {
            OneClickEmptyState(
                icon = OceIcon.BookmarkBorder,
                title = "아직 저장한 문장이 없어요",
                subtitle = "대화 중 마음에 드는 표현을 북마크해보세요.",
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
                bookmarks.forEach { card ->
                    OneClickCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(OceTheme.spacing.lg),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
                            ) {
                                SentenceBadge()
                                Text(
                                    text = card.english,
                                    style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = card.korean,
                                    style = OceTheme.typography.helper,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // 저장됨 표식(표시 전용) — 프로토타입 정합 골드(gameSaveGold 토큰).
                            OneClickIcon(
                                icon = OceIcon.Bookmark,
                                contentDescription = null,
                                tint = OceTheme.colors.gameSaveGold,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- 섹션 공통 헬퍼 ----

/** 섹션 제목(+선택 개수) + 본문 슬롯. 제목은 `summarySectionTitle`(800/16), 개수는 baseline 정렬 보조 라벨. */
@Composable
private fun SectionScaffold(
    title: String,
    count: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs)) {
            Text(
                text = title,
                style = OceTheme.typography.summarySectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alignByBaseline(),
            )
            if (count != null) {
                Text(
                    text = count,
                    style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        content()
    }
}

/** 섹션 제목 옆 개수 라벨 — Ready 이고 비어있지 않을 때만 "N개"(그 외 미표시). */
private fun <T> sectionCount(stateOf: SummarySectionState<List<T>>): String? =
    (stateOf as? SummarySectionState.Ready)?.value?.size?.takeIf { it > 0 }?.let { "${it}개" }

/** SSE 카드 섹션 본문 분기: Loading→스켈레톤, Failed→재시도, Ready(비었으면 빈 상태 / 아니면 [ready]). */
@Composable
private fun <T> SectionBody(
    section: SummarySection,
    stateOf: SummarySectionState<List<T>>,
    onRetry: (SummarySection) -> Unit,
    emptyTitle: String,
    emptySubtitle: String,
    ready: @Composable (List<T>) -> Unit,
) {
    when (stateOf) {
        is SummarySectionState.Loading -> FeedbackLoadingSkeleton()
        is SummarySectionState.Failed -> RetryRow(section, stateOf.canRetry, onRetry)
        is SummarySectionState.Ready ->
            if (stateOf.value.isEmpty()) {
                OneClickEmptyState(
                    icon = OceIcon.Notes,
                    title = emptyTitle,
                    subtitle = emptySubtitle,
                )
            } else {
                ready(stateOf.value)
            }
    }
}

/** Failed 섹션 인라인 재시도(canRetry→재시도 / 소진→비활성 안내). onSkip 은 요약에 없어 no-op(#17). */
@Composable
private fun RetryRow(
    section: SummarySection,
    canRetry: Boolean,
    onRetry: (SummarySection) -> Unit,
) {
    if (canRetry) {
        OneClickInlineError(
            mode = InlineErrorMode.Recoverable,
            message = "이 부분을 불러오지 못했어요.",
            onRetry = { onRetry(section) },
            onSkip = {},
        )
    } else {
        Text(
            text = "이 부분은 지금 불러올 수 없어요. 잠시 후 다시 확인해주세요.",
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * "더 보기" 접힘 카드 목록(#15): 초기 [COLLAPSED_PREVIEW]개 → 전개 시 전체(상한은 코디네이터가 이미 컷).
 * [card] 에 넘기는 index 는 원본 [items] 의 0-기반 순번(=cardId sourceIndex) — `take`/전체 모두 front prefix 라
 * 표시 인덱스가 원본 인덱스와 일치한다.
 */
@Composable
private fun <T> ExpandableCards(
    section: SummarySection,
    items: List<T>,
    expanded: MutableMap<SummarySection, Boolean>,
    card: @Composable (Int, T) -> Unit,
) {
    val isExpanded = expanded[section] == true
    val visible = if (isExpanded) items else items.take(COLLAPSED_PREVIEW)
    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
        visible.forEachIndexed { index, item ->
            OneClickCard { card(index, item) }
        }
        if (items.size > COLLAPSED_PREVIEW) {
            MoreChevron(expanded = isExpanded, onToggle = { expanded[section] = !isExpanded })
        }
    }
}

/**
 * "더 보기/접기" 어포던스 — 프로토타입 정합: 카드 스택 아래 중앙의 **원형 chevron 버튼**(흰 서피스 + hairline).
 * [OceIcon.ExpandMore](아래 chevron) 를 접힘=정방향, 전개=180° 뒤집어 위 chevron 으로 표시. 프로토는 카드 위로
 * 살짝 겹치나(오버랩) 여기선 중앙 배치로 근사(위치 잔차 note).
 */
@Composable
private fun MoreChevron(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .size(MoreChevronSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            OneClickIcon(
                icon = OceIcon.ExpandMore,
                contentDescription = if (expanded) "접기" else "더 보기",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }
    }
}

/**
 * 저장 토글이 붙은 카드 행(M2-04) — 본문(weight 1f) + 우상단 북마크 IconToggleButton(deep `ParaphraseCard`
 * 미러). 저장=채운 북마크 + primary tint, 미저장=빈 북마크. 낙관적: 탭 즉시 표시, 영속은 코디네이터가 배경 처리.
 */
@Composable
private fun SavableCardRow(
    saved: Boolean,
    onToggle: () -> Unit,
    body: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = Modifier.weight(1f)) { body() }
        // 저장=골드(프로토타입 정합, gameSaveGold), 미저장=중립 보조색.
        val tint =
            if (saved) OceTheme.colors.gameSaveGold else MaterialTheme.colorScheme.onSurfaceVariant
        IconToggleButton(checked = saved, onCheckedChange = { onToggle() }) {
            OneClickIcon(
                icon = if (saved) OceIcon.Bookmark else OceIcon.BookmarkBorder,
                contentDescription = if (saved) "저장 해제" else "저장",
                tint = tint,
            )
        }
    }
}

/**
 * 표현 개선 카드 본문 — 프로토타입/기록 탭 정합: 유형 pill 배지 + before(취소선) + 초록 → + after(볼드) + 설명.
 * 스타일은 기록 [com.jjundev.oneclickeng.feature.records] SavedCardRow 의 CategoryBadge/StrikeHelperText/AfterLine
 * 와 동일 규칙을 미러(패키지 private 라 재구현).
 */
@Composable
private fun ExpressionCardBody(card: ExpressionCard) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(OceTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
    ) {
        ExpressionBadge(card.type)
        if (card.before.isNotBlank()) {
            Text(
                text = card.before,
                style = OceTheme.typography.helper.copy(textDecoration = TextDecoration.LineThrough),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ImprovedLine(card.after)
        if (card.explanation.isNotBlank()) {
            Text(
                text = card.explanation,
                style = OceTheme.typography.helper,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 표현 pill 배지 — 프로토 정합: 유형 무관 단일 "표현" 칩(correct 핑크 토큰). 유형(자연/정확)은 데이터로
 * 유지되나 요약 카드 칩은 구분하지 않는다(기록 탭 CategoryBadge 는 유형 구분 유지).
 */
@Suppress("UnusedParameter")
@Composable
private fun ExpressionBadge(type: ExpressionType) {
    Text(
        text = "표현",
        style = OceTheme.typography.helper,
        color = OceTheme.colors.feedbackCorrectAccent,
        modifier =
            Modifier
                .clip(OceTheme.shapes.pill)
                .background(OceTheme.colors.feedbackCorrectBg)
                .padding(horizontal = OceTheme.spacing.sm, vertical = OceTheme.spacing.xs),
    )
}

/** 개선 표현 라인 = 초록 → + 굵은 결과(기록 카드 AfterLine 정합). */
@Composable
private fun ImprovedLine(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
    ) {
        Text(
            text = "→",
            style = OceTheme.typography.body,
            color = OceTheme.colors.feedbackNaturalAccent,
        )
        Text(
            text = text,
            style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 북마크 문장 배지 — 프로토타입 초록 "문장" pill(natural 초록 토큰). */
@Composable
private fun SentenceBadge() {
    Text(
        text = "문장",
        style = OceTheme.typography.helper,
        color = OceTheme.colors.feedbackNaturalAccent,
        modifier =
            Modifier
                .clip(OceTheme.shapes.pill)
                .background(OceTheme.colors.feedbackNaturalBg)
                .padding(horizontal = OceTheme.spacing.sm, vertical = OceTheme.spacing.xs),
    )
}

/**
 * 새 단어 카드 본문 — 프로토타입 단어 카드 정합(단순화): 단어(볼드)+뜻(보조색) + 예문 1줄(이탤릭·인용부호).
 * 품사·레벨·예문 한글 번역은 프로토가 노출하지 않아 생략(하단 전체 정합 결정, 스펙 확정 시 복원 seam).
 */
@Composable
private fun WordCardBody(card: WordCard) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(OceTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs)) {
            Text(
                text = card.en,
                style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = card.ko,
                style = OceTheme.typography.helper,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
        if (card.exampleEn.isNotBlank()) {
            Text(
                text = "“${card.exampleEn}”",
                style = OceTheme.typography.helper.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 코칭 블록 — 벡터 아이콘+색상 라벨(프로토: ✨=AutoAwesome/잘한 점, spellcheck/다음에 다듬을 점) + 본문
 * 줄들(개행 분리, 프로토는 항목당 한 줄). 이모지 미사용(P16) — 프로토도 ms 아이콘.
 */
@Composable
private fun CoachingBlock(
    icon: OceIcon,
    label: String,
    accent: Color,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
        ) {
            OneClickIcon(
                icon = icon,
                contentDescription = null,
                tint = accent,
                size = CoachingIconSize,
            )
            Text(
                text = label,
                style = OceTheme.typography.helper.copy(fontWeight = FontWeight.Bold),
                color = accent,
            )
        }
        body.lines().filter { it.isNotBlank() }.forEach { line ->
            Text(
                text = line,
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = CoachingBodyIndent),
            )
        }
    }
}

private const val COLLAPSED_PREVIEW = 3
private const val SECONDS_PER_MINUTE = 60

/** 스트릭 캡션 "일주일 완성" 격려로 승격되는 구간(프로토 streakLine 데모 정합). */
private const val WEEK_STREAK = 7

/** 완료 풋터 버튼 높이(프로토 Button primary 52px). */
private val DoneButtonHeight = 52.dp

/** 코칭 블록 라벨 아이콘 크기(프로토 17px). */
private val CoachingIconSize = 17.dp

/** 코칭 본문 들여쓰기(프로토 padding-left 23px — 아이콘 17 + gap 6). */
private val CoachingBodyIndent = 23.dp

/** 적립 카드 지표 아이콘 크기(프로토타입 ~22sp). */
private val AccrualIconSize = 22.dp

/** 적립 카드 열 구분선 높이. */
private val AccrualDividerHeight = 44.dp

/** 스트릭 캡션 🔥 아이콘 크기. */
private val StreakCaptionIconSize = 16.dp

/** "더 보기" 원형 chevron 버튼 크기. */
private val MoreChevronSize = 40.dp

/** 스크롤 보조 FAB 지름(프로토 summaryFab 48px 원형). */
private val SummaryFabSize = 48.dp

/** 스크롤 보조 FAB chevron 아이콘 크기(프로토 26px). */
private val SummaryFabIconSize = 26.dp

/** 스크롤 보조 FAB 그림자 높이(프로토 soft box-shadow 근사). */
private val SummaryFabElevation = 6.dp

/** 스크롤 보조 FAB 와 완료 풋터 사이 간격(프로토 FAB bottom:104 ≈ 풋터 높이 + 16). */
private val SummaryFabBottomGap = 16.dp

/** "끝 도달" 판정 허용 오차(프로토 scrollHeight − 8). */
private val SummaryFabAtEndTolerance = 8.dp

/** FAB 한 번 탭 시 내려가는 뷰포트 비율(프로토 clientHeight * 0.82). */
private const val SUMMARY_FAB_PAGE_FRACTION = 0.82f

/** 하이라이트 배지 pill 배경 브랜드색 알파(연한 톤 — OceBottomNav 활성 pill 선례와 정합). */
private const val HIGHLIGHT_BADGE_ALPHA = 0.12f

// ---- Previews (프로토타입 summary/summaryRich 대조용) ----

private val previewAccrual =
    AccrualStrip(
        streakDays = 7,
        xp = 120,
        todayStudySecondsBefore = 0,
        todayStudySecondsAfter = 720,
        streakStatic = false,
        animate = true,
    )

private val previewRichBundle =
    SectionBundle.Sectioned(
        expression =
            SummarySectionState.Ready(
                listOf(
                    ExpressionCard(
                        type = ExpressionType.Natural,
                        koreanPrompt = "커피 주세요",
                        before = "One coffee",
                        after = "Could I grab a coffee?",
                        explanation = "가볍게 주문할 때 자연스러워요.",
                    ),
                    ExpressionCard(
                        type = ExpressionType.Accurate,
                        koreanPrompt = "길을 잃었어요",
                        before = "I lost",
                        after = "I got lost",
                        explanation = "get lost 가 '길을 잃다'예요.",
                    ),
                ),
            ),
        word =
            SummarySectionState.Ready(
                listOf(
                    WordCard(
                        en = "grab",
                        ko = "잽싸게 가져오다",
                        partOfSpeech = "verb",
                        level = "B1",
                        exampleEn = "Let me grab a quick bite.",
                        exampleKo = "간단히 먹을게요.",
                    ),
                ),
            ),
        coaching =
            SummarySectionState.Ready(
                Coaching(positive = "끝까지 대화를 이어간 게 좋았어요.", toImprove = "다음엔 과거형을 한 번 노려볼까요?"),
            ),
    )

@Suppress("UnusedPrivateMember")
@Preview(name = "accrualCard", showBackground = true, widthDp = 360)
@Composable
private fun AccrualCardPreview() {
    OceTheme {
        Column(
            modifier = Modifier.padding(OceTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.lg),
        ) {
            // 첫 세션(롤업): streak 0→7, 학습시간 0→12분, XP 0→120.
            AccrualCard(previewAccrual)
            // same-day 2번째(streak 정적, 학습시간·XP 롤): 12분→18분.
            AccrualCard(
                AccrualStrip(
                    streakDays = 7,
                    xp = 120,
                    todayStudySecondsBefore = 720,
                    todayStudySecondsAfter = 1080,
                    streakStatic = true,
                    animate = true,
                ),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "summaryRich", showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun SummaryRichPreview() {
    OceTheme {
        SummaryScreen(
            state =
                SummaryState(
                    totalScore = 85,
                    highlight =
                        HighlightTurn(
                            koreanPrompt = "커피 주세요",
                            userText = "Could I grab a coffee?",
                            score = 92,
                            rationale = "정중하게 부탁하는 표현을 스스로 골라 말했어요.",
                        ),
                    bookmarks = listOf(BookmarkCard("I got lost on the way.", "오는 길에 길을 잃었어요.")),
                    accrual = previewAccrual,
                    bundle = previewRichBundle,
                    savedWordIndices = setOf(0),
                ),
            onRetry = {},
            onToggleSaveWord = {},
            onToggleSaveExpression = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "summary", showBackground = true, widthDp = 360, heightDp = 700)
@Composable
private fun SummaryLoadingPreview() {
    OceTheme {
        SummaryScreen(
            state =
                SummaryState(
                    totalScore = 85,
                    highlight =
                        HighlightTurn(
                            koreanPrompt = "커피 주세요",
                            userText = "Could I grab a coffee?",
                            score = 92,
                            rationale = "정중하게 부탁하는 표현을 스스로 골라 말했어요.",
                        ),
                    bookmarks = emptyList(),
                    accrual = previewAccrual,
                    bundle = SectionBundle.BundleLoading,
                ),
            onRetry = {},
            onToggleSaveWord = {},
            onToggleSaveExpression = {},
        )
    }
}

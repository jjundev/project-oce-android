@file:Suppress("TooManyFunctions") // 화면 = 다수의 작은 private 섹션 컴포저블 합성(SlimFeedbackSheet 선례).

package com.jjundev.oneclickeng.feature.session.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.jjundev.oneclickeng.ui.component.InlineErrorMode
import com.jjundev.oneclickeng.ui.component.OneClickCountUp
import com.jjundev.oneclickeng.ui.component.OneClickEmptyState
import com.jjundev.oneclickeng.ui.component.OneClickInlineError
import com.jjundev.oneclickeng.ui.component.OneClickSkeleton
import com.jjundev.oneclickeng.ui.component.OneClickStreakChip
import com.jjundev.oneclickeng.ui.component.OneClickXpChip
import com.jjundev.oneclickeng.ui.component.SkeletonShape
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 화면 05 — 세션 요약(M2-02). 정본: 04-screen-05-summary.md · gamification-emphasis.md §4 ·
 * dialogue-learning-flow.md §9. 무상태 컴포넌트 — 상태는 [SummaryViewModel]/[SummaryCoordinator] 소유.
 *
 * 레이아웃(SM1): 종합 점수 hero(56sp) → `sectionGap`(24dp) → 적립 스트립 **별도 블록**(같은 행 금지) →
 * 5섹션. 로컬 즉시 블록(점수·하이라이트·북마크·스트립)은 스켈레톤 없이 즉시 렌더하고, 요약 SSE 3섹션
 * (표현/단어/코칭)은 **번들 단위 단일 스켈레톤**([SectionBundle.BundleLoading]) 하나로 로딩하다가 `done`
 * 이후 섹션별로 렌더/재시도한다(§9).
 *
 * **섹션 순서 note:** SSE 3섹션은 번들 로딩(단일 스켈레톤)을 공유하므로 표현·단어·코칭을 **연속** 배치하고
 * 로컬 북마크 섹션을 그 뒤에 둔다 — doc 인벤토리의 북마크↔코칭 순서를 SSE 그룹핑을 위해 조정한 의도적
 * 결정(단일 스켈레톤 모델과 정합, SoT 재결정 필요 시 조정 지점).
 */
@Composable
fun SummaryScreen(
    state: SummaryState,
    onRetry: (SummarySection) -> Unit,
    onToggleSaveWord: (Int) -> Unit,
    onToggleSaveExpression: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // "더 보기" 접힘 상태(#15): 섹션별 독립, 초기 접힘. 기본 표시 [COLLAPSED_PREVIEW]개.
    val expanded = remember { mutableStateMapOf<SummarySection, Boolean>() }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(OceTheme.spacing.sheetPadding),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sectionGap),
    ) {
        ScoreHero(state.totalScore, state.isFirstSession)
        AccrualStripBlock(state.accrual)
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
    }
}

/** ① 종합 점수 hero — 56sp `scoreDisplay` brand.primary + 격려 1차. 점수 없으면(전 턴 스킵) 중립 안내. */
@Composable
private fun ScoreHero(
    totalScore: Int?,
    isFirstSession: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs)) {
        if (totalScore != null) {
            Text(
                text = totalScore.toString(),
                style = OceTheme.typography.scoreDisplay,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = encouragement(totalScore, isFirstSession),
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurface,
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
            )
        }
    }
}

/**
 * 격려 1차 카피(위계: 격려 우선, 점수 보조 — ux-writing). 비난 없음, 해요체. 온보딩 첫 세션([isFirstSession])은
 * 일반 세션보다 더 따뜻한 변형을 쓴다(01-onboarding §8 "첫 세션 피드백은 일반 세션보다 더 따뜻하게").
 */
private fun encouragement(
    score: Int,
    isFirstSession: Boolean,
): String =
    if (isFirstSession) {
        when {
            score >= HIGH_SCORE -> "첫 대화부터 정말 잘했어요! 영어로 말하는 게 이렇게 되네요."
            score >= MID_SCORE -> "첫 대화를 멋지게 해냈어요. 시작이 반이에요, 잘하고 있어요."
            else -> "첫 영어 대화를 끝까지 해냈어요. 이게 가장 큰 한 걸음이에요."
        }
    } else {
        when {
            score >= HIGH_SCORE -> "정말 잘했어요! 오늘 표현이 자연스러웠어요."
            score >= MID_SCORE -> "좋아요, 꾸준히 늘고 있어요."
            else -> "끝까지 해낸 게 가장 중요해요. 계속 가봐요."
        }
    }

/**
 * ② 적립 스트립 — 종합 점수와 **별도 블록**(같은 행 금지, gamification §4.2). 순서 streak🔥 → 학습시간 →
 * XP(§4.3). [AccrualStrip.animate] 가 true(M3-05 실값 착지)면 세 지표를 슬롯머신 카운트업(I3, §4.4)으로
 * 굴린다: XP 0→델타, 학습시간 오늘 누계 before→after, streak 0→N(단 same-day 2번째 세션은 정적). 주입 초기/
 * EMPTY(animate=false)는 정적. reduce-motion 은 [OneClickCountUp] 내부에서 스냅(F4).
 */
@Composable
private fun AccrualStripBlock(accrual: AccrualStrip) {
    val beforeMin = accrual.todayStudySecondsBefore?.div(SECONDS_PER_MINUTE)
    val afterMin = accrual.todayStudySecondsAfter / SECONDS_PER_MINUTE
    // before 불명(이관/롤오버=null) 또는 before·after 동일 분이면 학습시간 정적 스냅(§4.4 이관 예외·죽은 롤 방지).
    val studyStatic = beforeMin == null || beforeMin == afterMin || !accrual.animate
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
    ) {
        OneClickStreakChip(
            days = accrual.streakDays,
            static = accrual.streakStatic || !accrual.animate,
        )
        // 학습시간 슬롯은 실값 착지(animate) 후에만 노출한다(주입 초기 "오늘 0분" 잔상 방지).
        if (accrual.animate) {
            StudyTimeSlot(from = beforeMin ?: afterMin, target = afterMin, static = studyStatic)
        }
        OneClickXpChip(xp = accrual.xp, static = !accrual.animate)
    }
}

/** `오늘 N분` — "오늘 " 접두는 정적, 분(N)만 [OneClickCountUp] 으로 [from]→[target] 롤업(§4.4). */
@Composable
private fun StudyTimeSlot(
    from: Int,
    target: Int,
    static: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "오늘 ",
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OneClickCountUp(
            target = target,
            from = from,
            unit = "분",
            static = static,
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** ③ 하이라이트(가장 잘한 순간, ≤1) — 로컬 즉시. coaching 편승 보강은 M2-01 스키마 확정 후(#6). */
@Composable
private fun HighlightSection(highlight: HighlightTurn) {
    SectionScaffold(title = "가장 잘한 순간") {
        OneClickCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(OceTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
            ) {
                Text(
                    text = highlight.koreanPrompt,
                    style = OceTheme.typography.helper,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = highlight.userText,
                    style = OceTheme.typography.body,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * SSE 번들 영역(표현/단어/코칭). BundleLoading → 단일 스켈레톤(A6 polite). Sectioned → 섹션별 렌더.
 * QuotaBlocked → 중립 문구(재시도 없음).
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
            Column(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
            ) {
                Text(
                    text = "요약을 준비하고 있어요",
                    style = OceTheme.typography.sectionLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OneClickSkeleton(shape = SkeletonShape.Section)
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
                CoachingSection(bundle.coaching, onRetry)
            }
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
    SectionScaffold(title = "표현 개선") {
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
    SectionScaffold(title = "새로 만난 단어") {
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

/** ⑥ 코칭(잘한 점/개선점) — 빈 문자열 블록은 숨김(Rule 4). Failed 재시도. */
@Composable
private fun CoachingSection(
    stateOf: SummarySectionState<Coaching>,
    onRetry: (SummarySection) -> Unit,
) {
    SectionScaffold(title = "코칭") {
        when (stateOf) {
            is SummarySectionState.Loading -> OneClickSkeleton(shape = SkeletonShape.Section)
            is SummarySectionState.Failed ->
                RetryRow(SummarySection.Coaching, stateOf.canRetry, onRetry)
            is SummarySectionState.Ready ->
                if (!stateOf.value.hasPositive && !stateOf.value.hasToImprove) {
                    Text(
                        text = "이번 세션은 코칭할 거리가 많지 않았어요. 잘 마쳤어요!",
                        style = OceTheme.typography.helper,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md)) {
                        if (stateOf.value.hasPositive) {
                            CoachingBlock(label = "잘한 점", body = stateOf.value.positive)
                        }
                        if (stateOf.value.hasToImprove) {
                            CoachingBlock(label = "다음엔 이렇게", body = stateOf.value.toImprove)
                        }
                    }
                }
        }
    }
}

/** ⑦ 북마크 문장(≤8, 최신순) — 로컬 즉시. 빈 리스트면 빈 상태(M2-04 착지 전 기본). 저장 토글 표시 전용. */
@Composable
private fun BookmarkSection(bookmarks: List<BookmarkCard>) {
    SectionScaffold(title = "북마크한 문장") {
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
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(OceTheme.spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
                        ) {
                            Text(
                                text = card.english,
                                style = OceTheme.typography.body,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = card.korean,
                                style = OceTheme.typography.helper,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- 섹션 공통 헬퍼 ----

/** 섹션 제목 + 본문 슬롯. */
@Composable
private fun SectionScaffold(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
        Text(
            text = title,
            style = OceTheme.typography.sectionLabel,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

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
        is SummarySectionState.Loading -> OneClickSkeleton(shape = SkeletonShape.Section)
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
            TextButton(onClick = { expanded[section] = !isExpanded }) {
                Text(
                    text = if (isExpanded) "접기" else "더 보기 (${items.size - COLLAPSED_PREVIEW})",
                    style = OceTheme.typography.sectionLabel,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
        val tint =
            if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        IconToggleButton(checked = saved, onCheckedChange = { onToggle() }) {
            OneClickIcon(
                icon = if (saved) OceIcon.Bookmark else OceIcon.BookmarkBorder,
                contentDescription = if (saved) "저장 해제" else "저장",
                tint = tint,
            )
        }
    }
}

@Composable
private fun ExpressionCardBody(card: ExpressionCard) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(OceTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
    ) {
        Text(
            text = if (card.type == ExpressionType.Accurate) "정확한 표현" else "자연스러운 표현",
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = card.before,
            style = OceTheme.typography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = card.after,
            style = OceTheme.typography.body,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = card.explanation,
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WordCardBody(card: WordCard) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(OceTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
    ) {
        Text(
            text = "${card.en} · ${card.ko}",
            style = OceTheme.typography.body,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "${card.partOfSpeech} · ${card.level}",
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${card.exampleEn}\n${card.exampleKo}",
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CoachingBlock(
    label: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs)) {
        Text(
            text = label,
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = body,
            style = OceTheme.typography.body,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private const val HIGH_SCORE = 80
private const val MID_SCORE = 50
private const val COLLAPSED_PREVIEW = 3
private const val SECONDS_PER_MINUTE = 60

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
@Preview(name = "accrualStrip", showBackground = true, widthDp = 360)
@Composable
private fun AccrualStripPreview() {
    OceTheme {
        Column(
            modifier = Modifier.padding(OceTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.lg),
        ) {
            // 첫 세션(롤업): streak 0→7, 학습시간 0→12분, XP 0→120.
            AccrualStripBlock(previewAccrual)
            // same-day 2번째(streak 정적, 학습시간·XP 롤): 12분→18분.
            AccrualStripBlock(
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
                    highlight = HighlightTurn("커피 주세요", "Could I grab a coffee?", 92),
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
                    highlight = HighlightTurn("커피 주세요", "Could I grab a coffee?", 92),
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

package com.jjundev.oneclickeng.feature.session.feedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.component.InlineErrorMode
import com.jjundev.oneclickeng.ui.component.OneClickDualExposureBlock
import com.jjundev.oneclickeng.ui.component.OneClickInlineError
import com.jjundev.oneclickeng.ui.component.OneClickRichText
import com.jjundev.oneclickeng.ui.component.RichSegment
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** 턴 피드백 시트 높이 상한(화면 대비). 시트는 콘텐츠에 맞춰 커지되 이 비율을 넘지 않는다(넘으면 내부 스크롤). */
private const val SHEET_HEIGHT_FRACTION = 0.7f

/** 정적 드래그 핸들 바 불투명도(M3 DragHandle 정합 — 장식용, 시트는 드래그 불가). */
private const val HANDLE_ALPHA = 0.4f

/**
 * 화면 04 — 턴 피드백 시트(M1-07 slim + M2-03 deep). 드래그 없는 고정 하단 오버레이에 ⓪ 리캡 헤더(즉시)+
 * slim 3섹션(시머 점진 렌더)+ [더 보기/접기]·(deep 영역)·[다음] 을 싣는다. 정본: turn-feedback-ia.md
 * §2·§3·§4 · 04-screen-04-feedback-sheet.md.
 *
 * 섹션 렌더는 [SectionState] 로 분기: Loading → 시머 스켈레톤(C6) · Ready → 실데이터 · Failed → 인라인 에러
 * (canRetry 에 따라 재시도/스킵) · Skipped → 무음 건너뜀 표시. "다음" 은 3섹션이 모두 settled 일 때만 활성
 * (점수 gate 없음, §7).
 *
 * **deep "더 보기"(M2-03):** slim 3섹션이 모두 settled([SlimFeedbackState.Active.nextEnabled] 재사용 —
 * "다음"과 동일 게이트)면 활성화된다. 탭 시 [onExpandDeep] 으로 deep 을 개시/펼치고, 토글 자체는 사라진다
 * (`!deepExpanded` 에서만 렌더 — 접는 동작 없음). 펼친 뒤로는 slim 섹션 아래·"다음" 위에
 * [DeepFeedbackRegion] 이 인라인으로 계속 남는다. deep 상태([deepState])·재시도·북마크 토글은 호스트가
 * [DeepFeedbackCoordinator] 로 구동한다(라이브 배선은 통합 소관 — M1-08).
 *
 * @param onRetry 실패 섹션 재시도(코디네이터 [SlimFeedbackCoordinator.retry]).
 * @param onSkip 반복 실패 섹션 스킵([SlimFeedbackCoordinator.skip]).
 * @param onNext 다음 턴 진행(활성 조건은 시트가 게이팅).
 * @param deepState deep 상태축([DeepFeedbackCoordinator.state]).
 * @param deepExpanded "더 보기" 펼침 여부(호스트 소유 UI 상태).
 * @param onExpandDeep "더 보기" 첫 탭 → deep 개시/펼침([DeepFeedbackCoordinator.start]).
 * @param onCollapseDeep 접기 seam(현재 UI 에 접기 버튼 없음 — 재도입 대비 보존, P3).
 * @param onRetryDeep deep 영역 재시도([DeepFeedbackCoordinator.retry]).
 * @param bookmarkedLevels 턴 내 ephemeral 북마크 레벨.
 * @param onToggleBookmark 패러프레이즈 저장 토글 seam(M2-04 영속).
 */
@Composable
fun SlimFeedbackSheet(
    state: SlimFeedbackState,
    onRetry: (SlimSection) -> Unit,
    onSkip: (SlimSection) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    deepState: DeepFeedbackState = DeepFeedbackState.Idle,
    deepExpanded: Boolean = false,
    onExpandDeep: () -> Unit = {},
    onCollapseDeep: () -> Unit = {},
    onRetryDeep: () -> Unit = {},
    bookmarkedLevels: Set<Int> = emptySet(),
    onToggleBookmark: (Paraphrase) -> Unit = {},
) {
    if (state is SlimFeedbackState.Idle) return

    // 턴 피드백 시트는 콘텐츠에 맞춰 하단 정착하되 화면의 SHEET_HEIGHT_FRACTION 을 상한으로 둔다(짧은
    // 피드백은 그만큼만, 길면 상한에서 내부 스크롤). ModalBottomSheet 대신 **드래그 없는 고정 오버레이**로
    // 렌더한다 — 드래그가 되면 스크롤 중 시트가 실수로 줄어드는 불쾌한 경험이 생긴다(사용자 리포트). 시트는
    // 스와이프/탭으로 줄이거나 닫을 수 없고, "다음"(onNext) 또는 시스템 뒤로가기(호스트가 Route BackHandler 로
    // "대화 나가기"에 연결)로만 벗어난다. 드래그는 불가지만 프로토 핸들 바는 장식으로 복원한다.
    val sheetHeight = (LocalConfiguration.current.screenHeightDp * SHEET_HEIGHT_FRACTION).dp
    val reduceMotion = rememberReduceMotion()
    // 진입 시 1회 슬라이드-업 + 페이드-인(reduce-motion 이면 즉시). false→true 로 시작해 최초 컴포지션에서
    // 애니메이트한다. 나가기(다음/나가기)는 컴포저블이 즉시 제거돼 exit 는 실질적으로 재생되지 않는다.
    val visibleState = remember { MutableTransitionState(false) }.apply { targetState = true }
    Box(modifier = modifier.fillMaxSize()) {
        // 스크림(페이드-인): 뒤 대화를 어둡게. 탭은 소비만 하고(시트는 못 닫음) 뒤 콘텐츠로 새지 않게 막는다.
        AnimatedVisibility(
            visibleState = visibleState,
            enter = if (reduceMotion) EnterTransition.None else fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(OceTheme.colors.scrim)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
            )
        }
        // 하단 정착 고정 패널(슬라이드-업). 상단만 radius24 · surface · 그림자.
        AnimatedVisibility(
            visibleState = visibleState,
            enter = if (reduceMotion) EnterTransition.None else slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                // 내용에 맞춰 높이를 잡되 화면의 SHEET_HEIGHT_FRACTION 을 상한으로 둔다 — 짧은 피드백
                // (예: "이미 자연스러워요")에서 콘텐츠와 버튼 사이 과도한 빈 여백을 없애고, 길면 상한에서
                // 내부 스크롤한다(고정 70% → 적응형 상한).
                modifier = Modifier.fillMaxWidth().heightIn(max = sheetHeight),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 정적 드래그 핸들 바(프로토 정합). 시트는 드래그 불가라 순전히 장식이다.
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .width(32.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = HANDLE_ALPHA),
                                    ),
                        )
                    }
                    SlimFeedbackContent(
                        state = state,
                        onRetry = onRetry,
                        onSkip = onSkip,
                        onNext = onNext,
                        // fill = false: 콘텐츠가 짧으면 감싸고(빈 여백 없음), 상한에 닿으면 그 안에서 스크롤한다.
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        deepState = deepState,
                        deepExpanded = deepExpanded,
                        onExpandDeep = onExpandDeep,
                        onCollapseDeep = onCollapseDeep,
                        onRetryDeep = onRetryDeep,
                        bookmarkedLevels = bookmarkedLevels,
                        onToggleBookmark = onToggleBookmark,
                    )
                }
            }
        }
    }
}

/**
 * 시트 무관 콘텐츠(무상태 seam). [SlimFeedbackSheet] 는 모달 래핑만 하고 렌더는 여기 위임한다 — 프로토타입
 * 대조 스크린샷이 ModalBottomSheet(별도 윈도) 캡처 제약 없이 시트 내용을 고정 상태로 렌더할 수 있게 한다.
 */
// onCollapseDeep 은 "접기" 버튼 제거(펼친 뒤 토글 gone) 이후 UI 소비처가 없다 — API/프리뷰 호환을 위해
// seam 파라미터로 남겨 두되 미사용을 명시 억제한다.
@Suppress("UnusedParameter")
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SlimFeedbackContent(
    state: SlimFeedbackState,
    onRetry: (SlimSection) -> Unit,
    onSkip: (SlimSection) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    deepState: DeepFeedbackState = DeepFeedbackState.Idle,
    deepExpanded: Boolean = false,
    onExpandDeep: () -> Unit = {},
    onCollapseDeep: () -> Unit = {},
    onRetryDeep: () -> Unit = {},
    bookmarkedLevels: Set<Int> = emptySet(),
    onToggleBookmark: (Paraphrase) -> Unit = {},
) {
    // "더 보기" 탭 시 토글은 사라지고 심화 영역이 그 자리에 인라인으로 붙는다. 스크롤을 그 위치로 옮기지
    // 않으면 새로 붙은 영역이 화면 밖에 남아 아무 반응도 없는 것처럼 보인다(사용자 리포트). verticalScroll
    // 부모가 대상이다.
    val deepReveal = remember { BringIntoViewRequester() }
    LaunchedEffect(deepExpanded, deepState) {
        if (deepExpanded && deepState !is DeepFeedbackState.Idle) deepReveal.bringIntoView()
    }
    // 스크롤 콘텐츠(위)는 weight(fill=false)로 콘텐츠만큼만 차지하고(짧으면 빈 여백 없이 감싼다) 상한에
    // 닿으면 그 안에서 스크롤한다. 버튼 풋터는 그 아래 고정된다. 심화("더 보기")는 턴 피드백의 연장이라
    // 같은 스크롤 영역에 슬림 섹션 아래로 이어 붙는다.
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 6.dp, bottom = OceTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.lg),
        ) {
            when (state) {
                is SlimFeedbackState.Active -> {
                    Text(
                        text = "턴 피드백",
                        style = OceTheme.typography.summaryHeadline,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    RecapHeaderBlock(state.header)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // 섹션 간 간격을 넓혀(sectionGap=24) 작문·문법·자연을 뚜렷이 구분한다.
                    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sectionGap)) {
                        SlimSectionBlock(OceIcon.EditNote, "작문 점수", MaterialTheme.colorScheme.primary) {
                            SectionSlot(SlimSection.WritingScore, state.writingScore, onRetry, onSkip) {
                                WritingScoreContent(it)
                            }
                        }
                        SlimSectionBlock(OceIcon.Spellcheck, "문법", MaterialTheme.colorScheme.onSurfaceVariant) {
                            SectionSlot(SlimSection.Grammar, state.grammar, onRetry, onSkip) { GrammarContent(it) }
                        }
                        SlimSectionBlock(OceIcon.AutoAwesome, "자연스러운 표현", OceTheme.colors.feedbackNaturalAccent) {
                            SectionSlot(SlimSection.NaturalExpression, state.natural, onRetry, onSkip) {
                                NaturalContent(it)
                            }
                        }
                        // 심화(더 보기)는 턴 피드백의 연장 — 같은 섹션 리스트에 이어 붙어 슬림과 동일한 섹션 간격(24)을
                        // 공유한다(별도 구분선 없음). 딥 블록 내부 간격/헤더도 슬림 섹션과 같은 디자인 시스템을 쓴다.
                        if (deepExpanded && deepState !is DeepFeedbackState.Idle) {
                            DeepFeedbackRegion(
                                state = deepState,
                                onRetry = onRetryDeep,
                                bookmarkedLevels = bookmarkedLevels,
                                onToggleBookmark = onToggleBookmark,
                                modifier = Modifier.bringIntoViewRequester(deepReveal),
                            )
                        }
                    }
                }
                is SlimFeedbackState.QuotaBlocked -> {
                    RecapHeaderBlock(state.header)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "오늘 받을 수 있는 피드백을 모두 사용했어요. 그대로 다음으로 이어가요.",
                        style = OceTheme.typography.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is SlimFeedbackState.Idle -> Unit // unreachable (early return)
            }
        }
        // 하단 고정 버튼 풋터 — "더 보기"(펼치기 전)와 "다음"을 함께 고정 노출한다. "더 보기"는 딥이 슬림
        // 정착 시 이거-프리페치돼 있어 탭 시 즉시 펼쳐지고, 펼친 뒤에는 토글을 감춰 "다음"만 남긴다.
        when (state) {
            is SlimFeedbackState.Active ->
                SlimFooter {
                    if (!deepExpanded) {
                        MoreToggleButton(
                            expanded = false,
                            enabled = state.nextEnabled,
                            onClick = onExpandDeep,
                        )
                    }
                    NextButton(enabled = state.nextEnabled, onNext = onNext)
                }
            is SlimFeedbackState.QuotaBlocked ->
                SlimFooter { NextButton(enabled = true, onNext = onNext) } // 캡 거부 → "다음"만
            is SlimFeedbackState.Idle -> Unit
        }
    }
}

/** 시트 최하단 고정 버튼 풋터. */
@Composable
private fun SlimFooter(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("slim_footer")
                // 시스템 내비게이션 바 인셋만큼 하단을 비워 "다음" 버튼이 제스처 바/버튼 바에 잘리지 않게 한다.
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
        content = content,
    )
}

/**
 * 섹션 블록 = 아이콘+라벨 헤더(accent) + 콘텐츠. 헤더는 본문과 뚜렷이 구분되도록 ExtraBold 16(제목 타이포)로
 * 키우고 아이콘도 20dp 로 키운다 — 섹션별 accent 색 + 서로 다른 글리프로 위계를 준다.
 */
@Composable
private fun SlimSectionBlock(
    icon: OceIcon,
    label: String,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
        ) {
            OneClickIcon(icon = icon, contentDescription = null, tint = accent, size = 20.dp)
            Text(text = label, style = OceTheme.typography.summarySectionTitle, color = accent)
        }
        content()
    }
}

/** ⓪ 과제 리캡 헤더 — 라벨(좌) + 문장(우) 같은 줄(프로토타입 정합). 과제=강조, 내 답변=평문. */
@Composable
private fun RecapHeaderBlock(header: RecapHeader) {
    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
        RecapLine(label = "과제", value = header.koreanPrompt, emphasizeValue = true)
        RecapLine(label = "내 답변", value = header.userText, emphasizeValue = false)
    }
}

@Composable
private fun RecapLine(
    label: String,
    value: String,
    emphasizeValue: Boolean,
) {
    val valueColor =
        if (emphasizeValue) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md)) {
        Text(
            text = label,
            style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp).alignByBaseline(),
        )
        Text(
            text = value,
            style =
                OceTheme.typography.body.copy(
                    fontWeight = if (emphasizeValue) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 14.sp,
                ),
            color = valueColor,
            modifier = Modifier.weight(1f).alignByBaseline(),
        )
    }
}

/**
 * 한 섹션의 상태 분기. Loading → 스켈레톤(§3 스코프), Failed → 인라인 에러(canRetry→재시도 vs 스킵),
 * Skipped → 무음 건너뜀, Ready → [ready] 델리게이트. 섹션은 정답/실패에도 절대 숨기지 않는다(3섹션 불변, §1).
 */
@Composable
private fun <T> SectionSlot(
    section: SlimSection,
    state: SectionState<T>,
    onRetry: (SlimSection) -> Unit,
    onSkip: (SlimSection) -> Unit,
    ready: @Composable (T) -> Unit,
) {
    when (state) {
        is SectionState.Loading -> FeedbackLoadingSkeleton()
        is SectionState.Ready -> ready(state.value)
        is SectionState.Failed ->
            OneClickInlineError(
                mode = if (state.canRetry) InlineErrorMode.Recoverable else InlineErrorMode.Blocked,
                message = "피드백을 불러오지 못했어요.",
                onRetry = { onRetry(section) },
                onSkip = { onSkip(section) },
            )
        is SectionState.Skipped ->
            Text(
                text = "이 섹션은 건너뛰었어요.",
                style = OceTheme.typography.helper,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

/** ① 작문 점수 — 32sp 파생색 점수 + "점 · 점수는 정보예요" 접미 + 격려문(프로토타입 정합). 음성 점수 없음(§3.1). */
@Composable
private fun WritingScoreContent(value: WritingScore) {
    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs)) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
        ) {
            Text(
                text = value.score.toString(),
                style = OceTheme.typography.turnScore.copy(fontWeight = FontWeight.ExtraBold, fontSize = 32.sp),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "점 · 점수는 정보예요",
                style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Text(
            text = value.encouragement,
            style = OceTheme.typography.body.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** ② 문법 교정 — C15 세그먼트 + (초록 check + 설명). 정답(all-normal)이면 강조 없는 원문 + 축하 explanation(§3.2). */
@Composable
private fun GrammarContent(value: Grammar) {
    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs)) {
        OneClickRichText(segments = value.segments)
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
        ) {
            OneClickIcon(
                icon = OceIcon.CheckCircle,
                contentDescription = null,
                tint = OceTheme.colors.feedbackNaturalAccent,
                size = 17.dp,
            )
            Text(
                text = value.explanation,
                style = OceTheme.typography.helper.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * ③ 자연 표현 — EN(위)+reason(아래). 이미 자연스러우면(all-normal) 대체 diff 대신 긍정 상태 + reason 숨김
 * (§3.3, Rule 3). reason 은 항상 도착하지만 이 분기에서만 표시하지 않는다.
 */
@Composable
private fun NaturalContent(value: NaturalExpression) {
    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs)) {
        if (value.isAlreadyNatural) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
            ) {
                // 헤더(auto_awesome sparkle)와 겹치지 않도록 콘텐츠는 초록 check 로 확인 신호(문법 정답과 동일 관례).
                OneClickIcon(
                    icon = OceIcon.CheckCircle,
                    contentDescription = null,
                    tint = OceTheme.colors.feedbackNaturalAccent,
                    size = 17.dp,
                )
                Text(
                    text = "이미 자연스러워요.",
                    style = OceTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            OneClickDualExposureBlock(english = value.segments)
            Text(
                text = "${value.reason.keyword} · ${value.reason.description}",
                style = OceTheme.typography.helper,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * [더 보기/접기] 토글(M2-03). slim 3섹션이 모두 settled([enabled])이면 활성 — deep 를 개시/펼치거나 접는다.
 * 스킵된 슬림 섹션이 있으면(settled=true) 활성이 유지된다(nextEnabled 재사용, "다음"과 동일 게이트).
 */
/**
 * [더 보기/접기] — 프로토타입 정합: surface-background(회색) 채움 + 헤어라인 + radius12, 브랜드 텍스트(700/14) +
 * expand_more 셰브런(펼침 시 180° 회전). M3 OutlinedButton(stadium·투명) 대신 커스텀으로 형태를 맞춘다.
 */
@Composable
private fun MoreToggleButton(
    expanded: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val content =
        if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(OceTheme.shapes.radius12)
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, OceTheme.shapes.radius12)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) "접기" else "더 보기",
            style = OceTheme.typography.sectionLabel,
            color = content,
        )
        OneClickIcon(
            icon = OceIcon.ExpandMore,
            contentDescription = null,
            tint = content,
            size = 20.dp,
            modifier = Modifier.rotate(if (expanded) 180f else 0f),
        )
    }
}

/** [다음(settled 시 활성, §7 — 점수 gate 없음)]. 프로토타입: primary·full-width·52dp·radius12·텍스트 700/16. */
@Composable
private fun NextButton(
    enabled: Boolean,
    onNext: () -> Unit,
) {
    Button(
        onClick = onNext,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = OceTheme.shapes.radius12,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        Text(text = "다음", style = OceTheme.typography.sectionLabel.copy(fontSize = 16.sp))
    }
}

// ---- Previews (프로토타입 대조용) ----

private val previewHeader = RecapHeader(koreanPrompt = "커피 한 잔 주세요", userText = "One coffee please")

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SlimFeedbackReadyPreview() {
    OceTheme {
        Column(modifier = Modifier.clearAndSetSemantics {}) {
            RecapHeaderBlock(previewHeader)
            WritingScoreContent(WritingScore(score = 85, encouragement = "정말 잘했어요!"))
            GrammarContent(
                Grammar(
                    segments =
                        listOf(
                            RichSegment.Normal("Can I get "),
                            RichSegment.Incorrect("a coffee please"),
                            RichSegment.Correction("a coffee, please?"),
                        ),
                    explanation = "요청은 물음표로 부드럽게 끝내면 더 자연스러워요.",
                ),
            )
            NaturalContent(
                NaturalExpression(
                    segments =
                        listOf(
                            RichSegment.Normal("Could I "),
                            RichSegment.Highlight("grab a coffee"),
                            RichSegment.Normal("?"),
                        ),
                    reason = Reason(keyword = "구어체", description = "가볍게 주문할 때 자연스러운 표현이에요."),
                ),
            )
            // 이미 자연스러운 경우(all-normal → reason 숨김, §3.3).
            NaturalContent(
                NaturalExpression(
                    segments = listOf(RichSegment.Normal("Could I grab a coffee?")),
                    reason = Reason(keyword = "자연스러움", description = "이미 자연스러워요."),
                ),
            )
        }
    }
}

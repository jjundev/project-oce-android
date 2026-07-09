package com.jjundev.oneclickeng.feature.session.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.component.InlineErrorMode
import com.jjundev.oneclickeng.ui.component.OneClickDualExposureBlock
import com.jjundev.oneclickeng.ui.component.OneClickInlineError
import com.jjundev.oneclickeng.ui.component.OneClickRichText
import com.jjundev.oneclickeng.ui.component.OneClickSkeleton
import com.jjundev.oneclickeng.ui.component.RichSegment
import com.jjundev.oneclickeng.ui.component.SkeletonShape
import com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** 턴 피드백 시트 높이(화면 대비). 프로토타입 "70%만 올라오는" 시트 정합. */
private const val SHEET_HEIGHT_FRACTION = 0.7f

/**
 * 화면 04 — 턴 피드백 시트(M1-07 slim + M2-03 deep). 단일 [OneClickBottomSheet] 에 ⓪ 리캡 헤더(즉시)+
 * slim 3섹션(시머 점진 렌더)+ [더 보기/접기]·(deep 영역)·[다음] 을 싣는다. 정본: turn-feedback-ia.md
 * §2·§3·§4 · 04-screen-04-feedback-sheet.md.
 *
 * 섹션 렌더는 [SectionState] 로 분기: Loading → 시머 스켈레톤(C6) · Ready → 실데이터 · Failed → 인라인 에러
 * (canRetry 에 따라 재시도/스킵) · Skipped → 무음 건너뜀 표시. "다음" 은 3섹션이 모두 settled 일 때만 활성
 * (점수 gate 없음, §7).
 *
 * **deep "더 보기"(M2-03):** slim 3섹션이 모두 settled([SlimFeedbackState.Active.nextEnabled] 재사용 —
 * "다음"과 동일 게이트)면 활성화된다. 탭 시 [onExpandDeep]/[onCollapseDeep] 로 접기↔펴기하고, 펼침 상태에서
 * slim 섹션 아래·"다음" 위에 [DeepFeedbackRegion] 을 인라인 렌더한다. deep 상태([deepState])·재시도·북마크
 * 토글은 호스트가 [DeepFeedbackCoordinator] 로 구동한다(라이브 배선은 통합 소관 — M1-08).
 *
 * @param onRetry 실패 섹션 재시도(코디네이터 [SlimFeedbackCoordinator.retry]).
 * @param onSkip 반복 실패 섹션 스킵([SlimFeedbackCoordinator.skip]).
 * @param onNext 다음 턴 진행(활성 조건은 시트가 게이팅).
 * @param onDismiss 시트 dismiss.
 * @param deepState deep 상태축([DeepFeedbackCoordinator.state]).
 * @param deepExpanded "더 보기" 펼침 여부(호스트 소유 UI 상태).
 * @param onExpandDeep "더 보기" 첫 탭 → deep 개시/펼침([DeepFeedbackCoordinator.start]).
 * @param onCollapseDeep "접기" 탭 → 접기(재호출 없음, 캐시 유지, P3).
 * @param onRetryDeep deep 영역 재시도([DeepFeedbackCoordinator.retry]).
 * @param bookmarkedLevels 턴 내 ephemeral 북마크 레벨.
 * @param onToggleBookmark 패러프레이즈 저장 토글 seam(M2-04 영속).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlimFeedbackSheet(
    state: SlimFeedbackState,
    onRetry: (SlimSection) -> Unit,
    onSkip: (SlimSection) -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
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

    // 턴 피드백 시트는 화면의 70%까지만 올라온다(내부 스크롤). 프로토타입 정합.
    OneClickBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.fillMaxHeight(SHEET_HEIGHT_FRACTION),
    ) {
        SlimFeedbackContent(
            state = state,
            onRetry = onRetry,
            onSkip = onSkip,
            onNext = onNext,
            modifier = Modifier.fillMaxSize(),
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

/**
 * 시트 무관 콘텐츠(무상태 seam). [SlimFeedbackSheet] 는 모달 래핑만 하고 렌더는 여기 위임한다 — 프로토타입
 * 대조 스크린샷이 ModalBottomSheet(별도 윈도) 캡처 제약 없이 시트 내용을 고정 상태로 렌더할 수 있게 한다.
 */
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
    // 스크롤 콘텐츠(위)를 weight 로 채우고 버튼 풋터는 시트 최하단에 고정한다(요청). 심화("더 보기")는 턴
    // 피드백의 연장이라 같은 스크롤 영역에 슬림 섹션 아래로 이어 붙는다 — 버튼은 그대로 하단 고정.
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
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
        // 하단 고정 버튼 풋터.
        when (state) {
            is SlimFeedbackState.Active ->
                SlimFooter {
                    // 더 보기 게이트 = nextEnabled 재사용(모두 settled) — "다음"과 동일 술어(A4/A5).
                    MoreToggleButton(
                        expanded = deepExpanded,
                        enabled = state.nextEnabled,
                        onClick = { if (deepExpanded) onCollapseDeep() else onExpandDeep() },
                    )
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
        is SectionState.Loading -> OneClickSkeleton(shape = SkeletonShape.Section)
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

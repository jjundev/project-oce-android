package com.jjundev.oneclickeng.feature.session.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.component.InlineErrorMode
import com.jjundev.oneclickeng.ui.component.OneClickDualExposureBlock
import com.jjundev.oneclickeng.ui.component.OneClickInlineError
import com.jjundev.oneclickeng.ui.component.OneClickRichText
import com.jjundev.oneclickeng.ui.component.OneClickSkeleton
import com.jjundev.oneclickeng.ui.component.RichSegment
import com.jjundev.oneclickeng.ui.component.SkeletonShape
import com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet
import com.jjundev.oneclickeng.ui.theme.OceTheme

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

    OneClickBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(OceTheme.spacing.sheetPadding),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.lg),
        ) {
            when (state) {
                is SlimFeedbackState.Active -> {
                    RecapHeaderBlock(state.header)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SectionSlot(
                        section = SlimSection.WritingScore,
                        state = state.writingScore,
                        onRetry = onRetry,
                        onSkip = onSkip,
                    ) { WritingScoreContent(it) }
                    SectionSlot(
                        section = SlimSection.Grammar,
                        state = state.grammar,
                        onRetry = onRetry,
                        onSkip = onSkip,
                    ) { GrammarContent(it) }
                    SectionSlot(
                        section = SlimSection.NaturalExpression,
                        state = state.natural,
                        onRetry = onRetry,
                        onSkip = onSkip,
                    ) { NaturalContent(it) }
                    // 더 보기 게이트 = nextEnabled 재사용(모두 settled) — "다음"과 동일 술어(A4/A5).
                    MoreToggleButton(
                        expanded = deepExpanded,
                        enabled = state.nextEnabled,
                        onClick = { if (deepExpanded) onCollapseDeep() else onExpandDeep() },
                    )
                    if (deepExpanded && deepState !is DeepFeedbackState.Idle) {
                        DeepFeedbackRegion(
                            state = deepState,
                            onRetry = onRetryDeep,
                            bookmarkedLevels = bookmarkedLevels,
                            onToggleBookmark = onToggleBookmark,
                        )
                    }
                    NextButton(enabled = state.nextEnabled, onNext = onNext)
                }
                is SlimFeedbackState.QuotaBlocked -> {
                    RecapHeaderBlock(state.header)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "오늘 받을 수 있는 피드백을 모두 사용했어요. 그대로 다음으로 이어가요.",
                        style = OceTheme.typography.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // 슬림 캡 거부 → Ready 섹션이 없으므로 "더 보기" 미노출, "다음"만.
                    NextButton(enabled = true, onNext = onNext)
                }
                is SlimFeedbackState.Idle -> Unit // unreachable (early return)
            }
        }
    }
}

/** ⓪ 과제 리캡 헤더 — 과제(koreanPrompt) + 내 답변(userText), 무채색 plain, 즉시(§2.1). */
@Composable
private fun RecapHeaderBlock(header: RecapHeader) {
    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs)) {
        RecapLine(label = "과제", value = header.koreanPrompt)
        RecapLine(label = "내 답변", value = header.userText)
    }
}

@Composable
private fun RecapLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = OceTheme.typography.body,
            color = MaterialTheme.colorScheme.onSurface,
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

/** ① 작문 점수 — 28sp turnScore 파생색 + 격려문. 음성 점수 없음(§3.1). */
@Composable
private fun WritingScoreContent(value: WritingScore) {
    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs)) {
        Text(
            text = value.score.toString(),
            style = OceTheme.typography.turnScore,
            color = scoreColor(value.score),
        )
        Text(
            text = value.encouragement,
            style = OceTheme.typography.body,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** ② 문법 교정 — C15 세그먼트 + 설명. 정답(all-normal)이면 강조 없는 원문 + 축하 explanation(§3.2). */
@Composable
private fun GrammarContent(value: Grammar) {
    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs)) {
        OneClickRichText(segments = value.segments)
        Text(
            text = value.explanation,
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            Text(
                text = "이미 자연스러워요.",
                style = OceTheme.typography.body,
                color = OceTheme.colors.feedbackNaturalAccent,
            )
            OneClickRichText(segments = value.segments)
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
@Composable
private fun MoreToggleButton(
    expanded: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (expanded) "접기" else "더 보기",
            style = OceTheme.typography.sectionLabel,
            textDecoration = TextDecoration.None,
        )
    }
}

/** [다음(settled 시 활성, §7 — 점수 gate 없음)]. */
@Composable
private fun NextButton(
    enabled: Boolean,
    onNext: () -> Unit,
) {
    Button(
        onClick = onNext,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        Text(text = "다음", style = OceTheme.typography.sectionLabel)
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

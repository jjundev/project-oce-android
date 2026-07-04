package com.jjundev.oneclickeng.feature.session.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.component.InlineErrorMode
import com.jjundev.oneclickeng.ui.component.OneClickDualExposureBlock
import com.jjundev.oneclickeng.ui.component.OneClickInlineError
import com.jjundev.oneclickeng.ui.component.OneClickSkeleton
import com.jjundev.oneclickeng.ui.component.OneClickSlider
import com.jjundev.oneclickeng.ui.component.RichSegment
import com.jjundev.oneclickeng.ui.component.SkeletonShape
import com.jjundev.oneclickeng.ui.component.SliderMode
import com.jjundev.oneclickeng.ui.component.ToneLabel
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.component.venn.VennDiagramCanvas
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlin.math.roundToInt

/**
 * "더 보기" deep 영역(M2-03) — 단일 시트 하단에 conceptualBridge → toneStyle → paraphrasing 을 고정 순서로
 * 펼친다(turn-feedback-ia.md §4). 요청-레벨 상태([DeepFeedbackState])를 분기:
 * - [DeepFeedbackState.Loading]: 도착 블록은 실데이터, 미도착은 시머 스켈레톤(블록별 점진 렌더).
 * - [DeepFeedbackState.Ready]: 세 블록 전체.
 * - [DeepFeedbackState.Error]: 이미 도착한 블록은 보존(sticky) + 영역 1개 인라인 재시도(§9.2).
 * - [DeepFeedbackState.QuotaBlocked]: 중립 문구(재시도 아님).
 * - [DeepFeedbackState.Idle]·[DeepFeedbackState.Canceled]: 아무것도 렌더하지 않음.
 *
 * @param onRetry deep 전체 재호출([DeepFeedbackCoordinator.retry]).
 * @param bookmarkedLevels 턴 내 ephemeral 북마크 레벨(채워짐 표시).
 * @param onToggleBookmark 패러프레이즈 저장 토글 seam — 호스트가 M2-04 영속으로 연결(M2-03 는 seam 만).
 */
@Composable
fun DeepFeedbackRegion(
    state: DeepFeedbackState,
    onRetry: () -> Unit,
    bookmarkedLevels: Set<Int>,
    onToggleBookmark: (Paraphrase) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is DeepFeedbackState.Idle, is DeepFeedbackState.Canceled -> Unit
        is DeepFeedbackState.Loading ->
            DeepBlocks(
                modifier,
                state.conceptualBridge,
                state.toneStyle,
                state.paraphrasing,
                bookmarkedLevels,
                onToggleBookmark,
            )
        is DeepFeedbackState.Ready ->
            DeepBlocks(
                modifier,
                state.conceptualBridge,
                state.toneStyle,
                state.paraphrasing,
                bookmarkedLevels,
                onToggleBookmark,
            )
        is DeepFeedbackState.Error ->
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.lg),
            ) {
                DeepBlocks(
                    Modifier,
                    state.conceptualBridge,
                    state.toneStyle,
                    state.paraphrasing,
                    bookmarkedLevels,
                    onToggleBookmark,
                )
                OneClickInlineError(
                    mode = InlineErrorMode.Recoverable,
                    message = "깊은 분석을 불러오지 못했어요.",
                    onRetry = onRetry,
                    onSkip = {}, // deep 은 섹션별 스킵이 없다(영역 재시도만, §9.2)
                )
            }
        is DeepFeedbackState.QuotaBlocked ->
            Text(
                text = "지금은 깊은 분석을 더 볼 수 없어요. 그대로 다음으로 이어가요.",
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier,
            )
    }
}

/** 세 블록을 고정 순서로 렌더. null 블록은 스켈레톤(점진 렌더). */
@Composable
private fun DeepBlocks(
    modifier: Modifier,
    conceptualBridge: ConceptualBridge?,
    toneStyle: ToneStyle?,
    paraphrasing: Paraphrasing?,
    bookmarkedLevels: Set<Int>,
    onToggleBookmark: (Paraphrase) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.lg),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (conceptualBridge != null) ConceptualBridgeBlock(conceptualBridge) else BlockSkeleton()
        if (toneStyle != null) ToneStyleBlock(toneStyle) else BlockSkeleton()
        if (paraphrasing != null) {
            ParaphrasingBlock(paraphrasing, bookmarkedLevels, onToggleBookmark)
        } else {
            BlockSkeleton()
        }
    }
}

@Composable
private fun BlockSkeleton() {
    OneClickSkeleton(shape = SkeletonShape.Section)
}

/** ④ 개념 브릿지 — 직역 + 간극 설명 + 벤. guide·공통 의미를 텍스트로 병기(A2 색 단독 금지). */
@Composable
private fun ConceptualBridgeBlock(value: ConceptualBridge) {
    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
        Text(
            text = value.literalTranslation,
            style = OceTheme.typography.body,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value.explanation,
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (value.venn.guide.isNotBlank()) {
            Text(
                text = value.venn.guide,
                style = OceTheme.typography.helper,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        VennDiagramCanvas(venn = value.venn, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            text = "공통: ${value.venn.intersectionItems.joinToString(", ")}",
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** ⑤ 톤 스펙트럼 — C8 이산 슬라이더 5단계, 초기 선택 defaultLevel(로컬 rememberSaveable). */
@Composable
private fun ToneStyleBlock(value: ToneStyle) {
    val labels =
        remember(value) {
            value.levels
                .sortedBy { it.level }
                .map { ToneLabel(level = it.level, en = it.sentence, ko = it.sentenceTranslation) }
        }
    var selected by rememberSaveable(value) {
        mutableStateOf(value.defaultLevel.coerceIn(0, labels.lastIndex.coerceAtLeast(0)).toFloat())
    }
    OneClickSlider(
        value = selected,
        onValueChange = { selected = it.roundToInt().toFloat() },
        mode = SliderMode.Discrete(labels = labels),
    )
}

/** ⑥ 패러프레이징 — 3카드(Beginner/Intermediate/Advanced), 각 EN+KO 이중노출 + 북마크 토글. */
@Composable
private fun ParaphrasingBlock(
    value: Paraphrasing,
    bookmarkedLevels: Set<Int>,
    onToggleBookmark: (Paraphrase) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
        value.items.forEach { item ->
            ParaphraseCard(
                item = item,
                bookmarked = item.level in bookmarkedLevels,
                onToggle = { onToggleBookmark(item) },
            )
        }
    }
}

@Composable
private fun ParaphraseCard(
    item: Paraphrase,
    bookmarked: Boolean,
    onToggle: () -> Unit,
) {
    OneClickCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(OceTheme.spacing.md),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.label,
                    style = OceTheme.typography.helper,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OneClickDualExposureBlock(
                    english = listOf(RichSegment.Normal(item.sentence)),
                    korean = item.sentenceTranslation,
                )
            }
            val bookmarkTint =
                if (bookmarked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            IconToggleButton(checked = bookmarked, onCheckedChange = { onToggle() }) {
                OneClickIcon(
                    icon = if (bookmarked) OceIcon.Bookmark else OceIcon.BookmarkBorder,
                    contentDescription = if (bookmarked) "북마크 해제" else "북마크 저장",
                    tint = bookmarkTint,
                )
            }
        }
    }
}

// ---- Previews (프로토타입 대조용) ----

private fun previewReady() =
    DeepFeedbackState.Ready(
        conceptualBridge =
            ConceptualBridge(
                literalTranslation = "커피 하나요.",
                explanation = "주문 의도는 맞지만 조금 더 공손하게 표현할 수 있어요.",
                venn =
                    VennData(
                        guide = "두 단어의 의미 차이를 볼까요?",
                        left = VennCircle("get", listOf("얻다", "받다")),
                        right = VennCircle("order", listOf("주문하다")),
                        intersectionItems = listOf("받다"),
                    ),
            ),
        toneStyle =
            ToneStyle(
                defaultLevel = 2,
                levels =
                    listOf(
                        ToneLevel(0, "May I have a coffee, please?", "커피 한 잔 주시겠어요?"),
                        ToneLevel(1, "Could I get a coffee?", "커피 한 잔 주실 수 있나요?"),
                        ToneLevel(2, "Can I get a coffee?", "커피 한 잔 주세요."),
                        ToneLevel(3, "I'll grab a coffee.", "커피 하나 할게요."),
                        ToneLevel(4, "Lemme get a coffee.", "커피 하나요."),
                    ),
            ),
        paraphrasing =
            Paraphrasing(
                items =
                    listOf(
                        Paraphrase(1, "Beginner", "Can I get a coffee?", "커피 한 잔 주세요."),
                        Paraphrase(2, "Intermediate", "Could I get a coffee, please?", "커피 한 잔 주실 수 있을까요?"),
                        Paraphrase(3, "Advanced", "I'd love a coffee if you don't mind.", "괜찮으시면 커피 한 잔 부탁드려요."),
                    ),
            ),
    )

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DeepFeedbackRegionReadyPreview() {
    OceTheme {
        DeepFeedbackRegion(
            state = previewReady(),
            onRetry = {},
            bookmarkedLevels = setOf(2),
            onToggleBookmark = {},
            modifier = Modifier.padding(OceTheme.spacing.md),
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DeepFeedbackRegionLoadingPreview() {
    val ready = previewReady()
    OceTheme {
        DeepFeedbackRegion(
            state = DeepFeedbackState.Loading(conceptualBridge = ready.conceptualBridge),
            onRetry = {},
            bookmarkedLevels = emptySet(),
            onToggleBookmark = {},
            modifier = Modifier.padding(OceTheme.spacing.md),
        )
    }
}

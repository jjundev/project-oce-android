package com.jjundev.oneclickeng.feature.records

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 저장 카드 1행 = [OneClickCard] + 탭 시 인라인 펼침(타입별 여분 필드) + 펼침 상태 화살표. 펼침/접힘은 화면
 * 로컬 상태([expanded])가 구동하며, 우상단 [ExpandChevron] 이 접힘(⌄)/펼침(⌃)을 표시한다.
 *
 * 타입별 collapsed/expanded(R3·§4): WORD 굵은 영단어+보조색 뜻→+예문, EXPRESSION `koreanPrompt/before→after`→
 * +설명, SENTENCE 굵은 영문→+한글 번역.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedCardRow(
    entry: SavedCardEntry,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OneClickCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onToggleExpand, onLongClick = onLongPress)
                    .semantics {
                        customActions = listOf(CustomAccessibilityAction("삭제") { onLongPress(); true })
                    }
                    .padding(OceTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
                ) {
                    Collapsed(entry.card)
                }
                ExpandChevron(expanded = expanded)
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs)) {
                    Expanded(entry.card)
                }
            }
        }
    }
}

@Composable
private fun Collapsed(card: SavedCard) {
    when (card) {
        is SavedCard.Word -> {
            WordTermLine(english = card.english, korean = card.korean)
        }
        is SavedCard.Expression -> {
            CategoryBadge(card)
            if (card.koreanPrompt.isNotBlank()) PrimaryText(card.koreanPrompt)
            if (card.before.isNotBlank()) StrikeHelperText(card.before)
            AfterLine(card.after)
        }
        is SavedCard.Sentence -> {
            PrimaryText(card.english, bold = true)
        }
    }
}

@Composable
private fun Expanded(card: SavedCard) {
    when (card) {
        is SavedCard.Word -> {
            if (card.exampleEnglish.isNotBlank() || card.exampleKorean.isNotBlank()) {
                HelperText("${card.exampleEnglish}\n${card.exampleKorean}")
            }
        }
        is SavedCard.Expression -> {
            if (card.explanation.isNotBlank()) HelperText(card.explanation)
        }
        is SavedCard.Sentence -> {
            if (card.korean.isNotBlank()) HelperText(card.korean)
        }
    }
}

/** 펼침 어포던스 = 접힘 시 아래(⌄), 펼침 시 위(⌃)로 180° 회전. 카드 자체가 탭 토글을 소유하므로 비대화형 인디케이터. */
@Composable
private fun ExpandChevron(expanded: Boolean) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron-rotation",
    )
    OneClickIcon(
        icon = OceIcon.ExpandMore,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        size = OceIconSize.ListDisclosure,
        modifier = Modifier.rotate(rotation),
    )
}

/** 개선 표현 라인 = 초록 `→` + 굵은 결과 표현(프로토타입 기록 카드 정합). */
@Composable
private fun AfterLine(text: String) {
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

@Composable
private fun PrimaryText(text: String, bold: Boolean = false) {
    Text(
        text = text,
        style = if (bold) OceTheme.typography.body.copy(fontWeight = FontWeight.Bold) else OceTheme.typography.body,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** 단어 접힘 = 굵은 영단어(강조) + 보조색 한글 뜻, baseline 정렬(가운뎃점 없음, 프로토타입 정합). */
@Composable
private fun WordTermLine(english: String, korean: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
        Text(
            text = english,
            style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.alignByBaseline(),
        )
        if (korean.isNotBlank()) {
            Text(
                text = korean,
                style = OceTheme.typography.helper,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

@Composable
private fun HelperText(text: String) {
    Text(text = text, style = OceTheme.typography.helper, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** before(교정 전) 표현 = 취소선 헬퍼(프로토타입 기록 카드 정합). */
@Composable
private fun StrikeHelperText(text: String) {
    Text(
        text = text,
        style = OceTheme.typography.helper.copy(textDecoration = TextDecoration.LineThrough),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 표현 유형 pill 배지(자연/정확) — 피드백 색 토큰 tint. */
@Composable
private fun CategoryBadge(card: SavedCard.Expression) {
    val accurate = card.type == "accurate"
    val bg = if (accurate) OceTheme.colors.feedbackCorrectBg else OceTheme.colors.feedbackNaturalBg
    val fg = if (accurate) OceTheme.colors.feedbackCorrectAccent else OceTheme.colors.feedbackNaturalAccent
    Text(
        text = if (accurate) "정확한 표현" else "자연스러운 표현",
        style = OceTheme.typography.helper,
        color = fg,
        modifier =
            Modifier
                .clip(OceTheme.shapes.pill)
                .background(bg)
                .padding(horizontal = OceTheme.spacing.sm, vertical = OceTheme.spacing.xs),
    )
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SavedCardRowPreview() {
    OceTheme {
        SavedCardRow(
            entry =
                SavedCardEntry(
                    cardId = "s1",
                    card = SavedCard.Sentence(english = "I couldn't agree more.", korean = "전적으로 동의해요."),
                ),
            expanded = true,
            onToggleExpand = {},
            onLongPress = {},
            modifier = Modifier.padding(OceTheme.spacing.xl),
        )
    }
}

package com.jjundev.oneclickeng.feature.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 복습 Expression 카드 — before/after 2지선다(채점 O). `OneClickWaitQuiz` 레이아웃(⚡배지 + `N / M` →
 * 17sp bold 한글 질문 → 1.5dp/radius16 흰 옵션카드 → 리빌) 정합이나, 대기 퀴즈와 달리 오답을
 * correct-red 틴트 + X로 표시한다(비처벌 규칙 해제, ADR-0008 — 이 퀴즈는 복습 점수에 반영됨).
 *
 * 옵션 A=[SavedCard.Expression.before](index 0) / B=[SavedCard.Expression.after](index 1).
 * 정답은 항상 B([EXPRESSION_CORRECT_INDEX]).
 *
 * @param revealed true면 정답/오답 표시 + 설명 + "다음" 노출, 옵션은 비활성.
 * @param pick 사용자가 고른 인덱스(0/1) 또는 미선택 null.
 */
@Composable
fun ReviewExpressionQuiz(
    card: SavedCard.Expression,
    counter: String,
    revealed: Boolean,
    pick: Int?,
    onPick: (Int) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(OceTheme.shapes.radius22)
                .background(MaterialTheme.colorScheme.surface)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), OceTheme.shapes.radius22)
                .padding(horizontal = OceTheme.spacing.xxl, vertical = OceTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier =
                    Modifier
                        .clip(OceTheme.shapes.pill)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = BADGE_BG_ALPHA))
                        .padding(horizontal = OceTheme.spacing.md, vertical = OceTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
            ) {
                OneClickIcon(
                    icon = OceIcon.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    size = OceIconSize.InputInline,
                )
                Text(
                    text = "표현 복습",
                    style = OceTheme.typography.accrualLabel.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = counter,
                style = OceTheme.typography.accrualLabel.copy(fontWeight = FontWeight.SemiBold),
                color = OceTheme.colors.textTertiary,
            )
        }
        Text(
            text = card.koreanPrompt,
            style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        QuizOption(label = card.before, index = 0, revealed = revealed, pick = pick, onPick = onPick)
        QuizOption(
            label = card.after,
            index = EXPRESSION_CORRECT_INDEX,
            revealed = revealed,
            pick = pick,
            onPick = onPick,
        )
        if (revealed) {
            Text(
                text = card.explanation,
                style = OceTheme.typography.helper,
                color = OceTheme.colors.feedbackNaturalAccent,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(OceTheme.shapes.radius12)
                        .background(OceTheme.colors.feedbackNaturalBg)
                        .padding(OceTheme.spacing.md),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onNext) {
                    Text(
                        text = "다음",
                        style = OceTheme.typography.sectionLabel,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OneClickIcon(
                        icon = OceIcon.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        size = OceIconSize.ListDisclosure,
                    )
                }
            }
        }
    }
}

/** 배지 브랜드 틴트(프로토 oc-tint-brand 근사, OneClickWaitQuiz 정합). */
private const val BADGE_BG_ALPHA = 0.10f

/** 옵션 리빌 시각 상태(배경·보더·텍스트색) — 분기를 한 곳에 모아 [QuizOption]의 순환복잡도를 낮춘다. */
private data class OptionVisual(
    val background: Color,
    val borderColor: Color,
    val textColor: Color,
)

@Composable
private fun optionVisual(
    revealed: Boolean,
    isCorrect: Boolean,
    isWrongPick: Boolean,
): OptionVisual =
    when {
        revealed && isCorrect ->
            OptionVisual(
                background = OceTheme.colors.feedbackNaturalBg,
                borderColor = OceTheme.colors.feedbackNaturalAccent,
                textColor = MaterialTheme.colorScheme.onSurface,
            )
        isWrongPick ->
            OptionVisual(
                background = OceTheme.colors.feedbackCorrectBg,
                borderColor = OceTheme.colors.feedbackCorrectAccent,
                textColor = MaterialTheme.colorScheme.onSurface,
            )
        revealed ->
            OptionVisual(
                background = MaterialTheme.colorScheme.surface,
                borderColor = MaterialTheme.colorScheme.outlineVariant,
                textColor = OceTheme.colors.textTertiary,
            )
        else ->
            OptionVisual(
                background = MaterialTheme.colorScheme.surface,
                borderColor = OceTheme.colors.borderStrong,
                textColor = MaterialTheme.colorScheme.onSurface,
            )
    }

/**
 * 2지선다 옵션 — 흰 카드 + 1.5dp 보더(radius16). 리빌 후: 정답=natural 틴트 + accent 보더 + 700 + 체크,
 * 오답 선택지=correct-red 틴트 + accent 보더 + X(비처벌 규칙 해제, ADR-0008).
 */
@Composable
private fun QuizOption(
    label: String,
    index: Int,
    revealed: Boolean,
    pick: Int?,
    onPick: (Int) -> Unit,
) {
    val isCorrect = index == EXPRESSION_CORRECT_INDEX
    val isWrongPick = revealed && pick == index && !isCorrect
    val visual = optionVisual(revealed = revealed, isCorrect = isCorrect, isWrongPick = isWrongPick)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(OceTheme.shapes.radius16)
                .background(visual.background)
                .border(BorderStroke(1.5.dp, visual.borderColor), OceTheme.shapes.radius16)
                .clickable(enabled = !revealed) { onPick(index) }
                .padding(horizontal = OceTheme.spacing.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
    ) {
        Text(
            text = label,
            style =
                OceTheme.typography.body.copy(
                    fontWeight = if (revealed && isCorrect) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 15.5.sp,
                ),
            color = visual.textColor,
            modifier = Modifier.weight(1f),
        )
        OptionResultIcon(revealed = revealed, isCorrect = isCorrect, isWrongPick = isWrongPick)
    }
}

/** 리빌 후 정답=체크 / 오답 선택=X, 그 외엔 아무것도 그리지 않는다. */
@Composable
private fun OptionResultIcon(
    revealed: Boolean,
    isCorrect: Boolean,
    isWrongPick: Boolean,
) {
    when {
        revealed && isCorrect ->
            OneClickIcon(
                icon = OceIcon.CheckCircle,
                contentDescription = "정답",
                tint = OceTheme.colors.feedbackNaturalAccent,
                size = OceIconSize.ListDisclosure,
            )
        isWrongPick ->
            OneClickIcon(
                icon = OceIcon.Close,
                contentDescription = "오답",
                tint = OceTheme.colors.feedbackCorrectAccent,
                size = OceIconSize.ListDisclosure,
            )
    }
}

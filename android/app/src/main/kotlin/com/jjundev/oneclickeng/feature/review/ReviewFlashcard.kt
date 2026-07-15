package com.jjundev.oneclickeng.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** 카드 하단 버튼 높이 — 앱 전역 primary CTA 표준(52dp, DialogueGeneratingScreen.PrimaryCtaHeight 등과 동일). */
private val ReviewButtonHeight = 52.dp

/**
 * 복습 플립 카드(Word/Sentence 공통, Task 9). 앞면=한국어 프롬프트+"정답 보기", 뒷면=영어 정답+TTS+
 * (Word 전용)예문+[다시]/[완료] 채점 버튼. flip 상태([revealed])는 호출부(ReviewViewModel)가 소유한다 —
 * 이 컴포저블은 순수 렌더 + 콜백 위임(onReveal/onGrade/onSpeak)만 담당한다.
 */
@Composable
fun ReviewFlashcard(
    card: SavedCard,
    revealed: Boolean,
    onReveal: () -> Unit,
    onGrade: (Boolean) -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val korean = if (card is SavedCard.Word) card.korean else (card as SavedCard.Sentence).korean
    val english = if (card is SavedCard.Word) card.english else (card as SavedCard.Sentence).english

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(OceTheme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (!revealed) {
                Text(
                    text = if (card is SavedCard.Word) "이 뜻의 영어 단어는?" else "이 문장을 영어로?",
                    style = OceTheme.typography.helper,
                    color = OceTheme.colors.textTertiary,
                )
                Spacer(Modifier.size(OceTheme.spacing.md))
                Text(
                    text = korean,
                    style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
                ) {
                    Text(
                        text = english,
                        style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    SpeakButton(onClick = { onSpeak(english) })
                }
                Spacer(Modifier.size(OceTheme.spacing.sm))
                Text(
                    text = korean,
                    style = OceTheme.typography.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (card is SavedCard.Word && card.exampleEnglish.isNotBlank()) {
                    Spacer(Modifier.size(OceTheme.spacing.lg))
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(OceTheme.shapes.radius12)
                            .background(MaterialTheme.colorScheme.surface).padding(OceTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
                    ) {
                        Text(
                            text = card.exampleEnglish,
                            style = OceTheme.typography.helper,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (card.exampleKorean.isNotBlank()) {
                            Text(
                                text = card.exampleKorean,
                                style = OceTheme.typography.helper,
                                color = OceTheme.colors.textTertiary,
                            )
                        }
                    }
                }
            }
        }
        if (!revealed) {
            ReviewButtonSheet {
                Button(
                    onClick = onReveal,
                    modifier = Modifier.fillMaxWidth().height(ReviewButtonHeight),
                    shape = OceTheme.shapes.radius12,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) { Text(text = "정답 보기", style = OceTheme.typography.sectionLabel) }
            }
        } else {
            ReviewButtonSheet {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
                ) {
                    Button(
                        onClick = { onGrade(false) },
                        modifier = Modifier.weight(1f).height(ReviewButtonHeight),
                        shape = OceTheme.shapes.radius12,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OceTheme.colors.feedbackCorrectBg,
                            contentColor = OceTheme.colors.feedbackCorrectAccent,
                        ),
                    ) { Text(text = "다시", style = OceTheme.typography.sectionLabel) }
                    Button(
                        onClick = { onGrade(true) },
                        modifier = Modifier.weight(1f).height(ReviewButtonHeight),
                        shape = OceTheme.shapes.radius12,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OceTheme.colors.feedbackNaturalAccent,
                            contentColor = Color.White,
                        ),
                    ) { Text(text = "완료", style = OceTheme.typography.sectionLabel) }
                }
            }
        }
    }
}

@Composable
private fun SpeakButton(onClick: () -> Unit) {
    Column(
        modifier = Modifier.size(38.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OneClickIcon(
            icon = OceIcon.VolumeUp,
            contentDescription = "발음 듣기",
            tint = MaterialTheme.colorScheme.primary,
            size = OceIconSize.ListDisclosure,
        )
    }
}

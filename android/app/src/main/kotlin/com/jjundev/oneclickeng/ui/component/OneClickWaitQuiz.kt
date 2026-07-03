package com.jjundev.oneclickeng.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * C20 WaitQuiz = scratch 무상태·무채점 로딩 인터스티셜. 정본: 02-shared-components.md:140 · ADR-0005.
 *
 * 점수·streak·저장 영속 **0**. 대본 생성 대기 중 이탈 방지용으로, 옵션(버튼 semantics)을 탭하면 정답을
 * 리빌(오답 비처벌)하고 `다음`으로 넘어간다. 96dp 프로그레스 링 + 안심 카피는 **카드 아래**(O3 rev3)에 둔다.
 * 노출 타이밍(1000ms 지연 게이트)과 `visible` 게이팅은 소비처(로딩 화면)가 소유한다.
 *
 * 리빌 전환은 [reduceMotion] 시 즉시(Crossfade 없음). **비-reduce-motion 리빌 전환의 정밀 duration/easing 은
 * SoT 가 "신규 정의"로 남긴 잔여 open 항목**(02-shared-components.md:139-140)으로, 여기서는 기본 Crossfade 로
 * 두고 M3-06 튜닝(또는 소비 이슈)에서 확정한다. EN 콘텐츠(prompt/옵션)에는 `LocaleList("en")`(A4).
 *
 * @param onAnswered (문항, 선택 인덱스, 정답여부) — 채점 아닌 텔레메트리 훅.
 */
@Composable
fun OneClickWaitQuiz(
    items: List<QuizItem>,
    modifier: Modifier = Modifier,
    reassuranceCopy: String = "대화를 준비하고 있어요. 잠깐 퀴즈 어때요?",
    onAnswered: (item: QuizItem, selectedIndex: Int, correct: Boolean) -> Unit = { _, _, _ -> },
    reduceMotion: Boolean = rememberReduceMotion(),
) {
    var index by remember(items) { mutableIntStateOf(0) }
    var revealed by remember(items) { mutableStateOf<Int?>(null) }
    val item = items.getOrNull(index)

    Column(
        modifier = modifier.fillMaxWidth().padding(OceTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xl),
    ) {
        if (item != null) {
            OneClickCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(OceTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
                ) {
                    Text(
                        text = enText(item.prompt),
                        style = OceTheme.typography.body,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    QuizOption(
                        label = item.optionA,
                        highlighted = revealed != null && item.correctIndex == 0,
                        enabled = revealed == null,
                        onClick = {
                            revealed = 0
                            onAnswered(item, 0, item.correctIndex == 0)
                        },
                    )
                    QuizOption(
                        label = item.optionB,
                        highlighted = revealed != null && item.correctIndex == 1,
                        enabled = revealed == null,
                        onClick = {
                            revealed = 1
                            onAnswered(item, 1, item.correctIndex == 1)
                        },
                    )
                    val selection = revealed
                    Crossfade(
                        targetState = selection,
                        animationSpec =
                            if (reduceMotion) {
                                snap()
                            } else {
                                tween(OceTheme.motion.durationBaseMs)
                            },
                        label = "quiz-reveal",
                    ) { sel ->
                        if (sel != null) {
                            val revealCopy =
                                if (sel == item.correctIndex) item.revealCopyCorrect else item.revealCopyWrong
                            Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
                                Text(
                                    text = revealCopy,
                                    style = OceTheme.typography.body,
                                    color = OceTheme.colors.feedbackCorrectAccent,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                )
                                TextButton(
                                    onClick = {
                                        index = if (items.isEmpty()) 0 else (index + 1) % items.size
                                        revealed = null
                                    },
                                ) {
                                    Text(
                                        text = "다음",
                                        style = OceTheme.typography.sectionLabel,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 안심 슬롯: 96dp 링 + 카피는 카드 아래(O3 rev3).
        OneClickProgressRing(mode = ProgressRingMode.Indeterminate)
        Text(
            text = reassuranceCopy,
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuizOption(
    label: String,
    highlighted: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor =
        if (highlighted) OceTheme.colors.feedbackCorrectAccent else MaterialTheme.colorScheme.outlineVariant
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled || highlighted,
        shape = OceTheme.shapes.radius12,
        border = BorderStroke(width = if (highlighted) 2.dp else 1.dp, color = borderColor),
    ) {
        Text(
            text = enText(label),
            style = OceTheme.typography.body,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** EN 학습 콘텐츠에 `LocaleList("en")` 스팬을 붙인다(A4). */
private fun enText(text: String): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(localeList = LocaleList("en"))) { append(text) }
    }

/** 프리뷰/테스트용 샘플 문항(실 번들은 소비 이슈가 주입). */
internal fun previewWaitQuizItems(): List<QuizItem> =
    listOf(
        QuizItem(
            id = "sample-1",
            level = 1,
            prompt = "Which one sounds more natural?",
            optionA = "I have a plan.",
            optionB = "I have plan.",
            correctIndex = 0,
            revealCopyCorrect = "맞아요! 관사를 잘 챙겼어요.",
            revealCopyWrong = "괜찮아요. \"a plan\" 처럼 관사를 붙여요.",
        ),
        QuizItem(
            id = "sample-2",
            level = 2,
            prompt = "Pick the more polite request.",
            optionA = "Give me the report.",
            optionB = "Could you send me the report?",
            correctIndex = 1,
            revealCopyCorrect = "좋아요! 훨씬 부드러운 표현이에요.",
            revealCopyWrong = "괜찮아요. \"Could you...\" 가 더 정중해요.",
        ),
    )

/**
 * C20 WaitQuiz 문항. 정본: 02-shared-components.md:140 · loading-quiz-interstitial.md.
 *
 * 무채점 로딩 퀴즈의 2지선다 카드. [correctIndex] 0=A / 1=B. [revealCopyCorrect]/[revealCopyWrong] 는
 * 리빌 시 안심 문구(오답 비처벌). 실제 문항 번들(레벨 3티어 × ~25)은 소비 이슈(M1-01)가 빌드타임 오프라인
 * LLM+검수로 저작해 주입한다 — 이 컴포넌트는 번들 저작을 포함하지 않는다.
 */
data class QuizItem(
    val id: String,
    val level: Int,
    val prompt: String,
    val optionA: String,
    val optionB: String,
    val correctIndex: Int,
    val revealCopyCorrect: String,
    val revealCopyWrong: String,
)

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 520)
@Composable
private fun OneClickWaitQuizPreview() {
    OceTheme {
        OneClickWaitQuiz(items = previewWaitQuizItems(), reduceMotion = true)
    }
}

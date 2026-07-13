package com.jjundev.oneclickeng.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlin.math.sqrt

/**
 * C20 WaitQuiz = scratch 무상태·무채점 로딩 인터스티셜. 정본: 02-shared-components.md:140 · ADR-0005.
 *
 * 점수·streak·저장 영속 **0**. 대본 생성 대기 중 이탈 방지용으로, 옵션(버튼 semantics)을 탭하면 정답을
 * 리빌(오답 비처벌)하고 `다음`으로 넘어간다. 카드 구성(프로토 정합): 헤더(⚡"기다리는 동안 가볍게" 배지 +
 * `N / M` 카운터) → 질문(700/17) → 2지선다 → 리빌 → "점수·기록에 반영되지 않아요" 풋터. 링·안심 카피는
 * 소비처(로딩 화면)의 프리-퀴즈 로딩 표면이 소유한다. 노출 타이밍(1000ms 지연 게이트)도 소비처 소유.
 *
 * 리빌 전환은 [reduceMotion] 시 즉시(Crossfade 없음). **비-reduce-motion 리빌 전환의 정밀 duration/easing 은
 * SoT 가 "신규 정의"로 남긴 잔여 open 항목**(02-shared-components.md:139-140)으로, 여기서는 기본 Crossfade 로
 * 두고 M3-06 튜닝(또는 소비 이슈)에서 확정한다. EN 콘텐츠(prompt/옵션)에는 `LocaleList("en")`(A4).
 *
 * @param loading 생성 in-flight면 테두리 링이 회전한다(무한 전이 구독). 준비/실패 등 정지 상태는 `false`.
 *   `true` + 비-reduceMotion은 무한 전이를 구독하므로, 이 컴포넌트를 렌더하는 스크린샷 테스트는
 *   `reduceMotion=true`를 주입하거나 `loading=false`로 캡처하고 `waitForIdle()`을 피해야 한다(행 방지).
 * @param onAnswered (문항, 선택 인덱스, 정답여부) — 채점 아닌 텔레메트리 훅.
 */
@Composable
fun OneClickWaitQuiz(
    items: List<QuizItem>,
    modifier: Modifier = Modifier,
    onAnswered: (item: QuizItem, selectedIndex: Int, correct: Boolean) -> Unit = { _, _, _ -> },
    loading: Boolean = true,
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
            Column(
                // 링 래퍼(radius24 외곽) + 내부 surface(radius22) + 기존 카드 내부 padding.
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .quizLoadingRing(loading = loading, reduceMotion = reduceMotion)
                        .padding(
                            horizontal = OceTheme.spacing.xxl,
                            vertical = OceTheme.spacing.xl,
                        ),
                verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
            ) {
                QuizCardHeader(counter = "${index + 1} / ${items.size}")
                Text(
                    // 프로토 정합: 질문은 한국어(로케일 스팬 없음), 선택지는 EN(enText).
                    text = item.prompt,
                    style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                QuizOption(
                    label = item.optionA,
                    answered = revealed != null,
                    isCorrect = item.correctIndex == 0,
                    onClick = {
                        revealed = 0
                        onAnswered(item, 0, item.correctIndex == 0)
                    },
                )
                QuizOption(
                    label = item.optionB,
                    answered = revealed != null,
                    isCorrect = item.correctIndex == 1,
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
                        // 프로토 정합: 리빌 노트(좌, text-secondary=onSurfaceVariant 중립)와 "다음"(우)을 한 줄
                        // Row(space-between)로. 정답/오답 구분은 색이 아니라 카피 톤으로만(비처벌) — 오답도
                        // correct-accent 초록으로 칠하지 않는다(loading-quiz-interstitial.md, ADR-0005).
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = REVEAL_ROW_TOP_PADDING),
                            horizontalArrangement = Arrangement.spacedBy(REVEAL_ROW_GAP),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = revealCopy,
                                style =
                                    OceTheme.typography.helper.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                    ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            TextButton(
                                onClick = {
                                    index = if (items.isEmpty()) 0 else (index + 1) % items.size
                                    revealed = null
                                },
                                contentPadding =
                                    PaddingValues(
                                        horizontal = OceTheme.spacing.xs,
                                        vertical = OceTheme.spacing.xs,
                                    ),
                            ) {
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
                Text(
                    text = "점수·기록에 반영되지 않아요. 편하게 풀어보세요.",
                    style = OceTheme.typography.accrualLabel,
                    color = OceTheme.colors.textTertiary,
                )
            }
        }
    }
}

/** 카드 헤더 — ⚡"기다리는 동안 가볍게" 브랜드 틴트 배지 + `N / M` 카운터(프로토 정합). */
@Composable
private fun QuizCardHeader(counter: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .clip(OceTheme.shapes.pill)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = BADGE_BG_ALPHA))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = BADGE_BORDER_ALPHA),
                        shape = OceTheme.shapes.pill,
                    )
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
                text = "기다리는 동안 가볍게",
                style = OceTheme.typography.accrualLabel.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = counter,
            style = OceTheme.typography.accrualLabel.copy(fontWeight = FontWeight.SemiBold),
            color = OceTheme.colors.textTertiary,
        )
    }
}

/** 배지 브랜드 틴트(프로토 oc-tint-brand 근사). */
private const val BADGE_BG_ALPHA = 0.10f
private const val BADGE_BORDER_ALPHA = 0.25f

/**
 * 2지선다 옵션(프로토 정합) — 흰 카드 + 1.5dp 보더(radius16) + **왼쪽 정렬** EN 텍스트(600/15.5). 리빌 후:
 * 정답=natural 틴트 배경 + accent 보더 + 700 + 우측 체크, 비정답=hairline 보더 + tertiary 텍스트.
 */
@Composable
private fun QuizOption(
    label: String,
    answered: Boolean,
    isCorrect: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val revealCorrect = answered && isCorrect
    val background = if (revealCorrect) OceTheme.colors.feedbackNaturalBg else MaterialTheme.colorScheme.surface
    // 프로토 옵션 보더: 기본 border-strong → 전용 토큰(outline 은 이 테마에서 더 진함).
    val borderColor =
        when {
            revealCorrect -> OceTheme.colors.feedbackNaturalAccent
            answered -> MaterialTheme.colorScheme.outlineVariant
            else -> OceTheme.colors.borderStrong
        }
    val textColor =
        if (answered && !isCorrect) OceTheme.colors.textTertiary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(OceTheme.shapes.radius16)
                .background(background)
                .border(BorderStroke(OPTION_BORDER_WIDTH, borderColor), OceTheme.shapes.radius16)
                .clickable(enabled = !answered, onClick = onClick)
                .padding(horizontal = OceTheme.spacing.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
    ) {
        Text(
            text = enText(label),
            style =
                OceTheme.typography.body.copy(
                    fontWeight = if (revealCorrect) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 15.5.sp,
                ),
            color = textColor,
            modifier = Modifier.weight(1f),
        )
        if (revealCorrect) {
            OneClickIcon(
                icon = OceIcon.CheckCircle,
                contentDescription = "정답",
                tint = OceTheme.colors.feedbackNaturalAccent,
                size = OceIconSize.ListDisclosure,
            )
        }
    }
}

/** 옵션 보더 굵기(프로토 1.5px). */
private val OPTION_BORDER_WIDTH = 1.5.dp

/** EN 학습 콘텐츠에 `LocaleList("en")` 스팬을 붙인다(A4). */
private fun enText(text: String): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(localeList = LocaleList("en"))) { append(text) }
    }

/** 대기 퀴즈 링 폭·회전 주기·스윕 피크(프로토 2px·oc-rot 1.1s·70°/150° 정합). */
private val RING_WIDTH = 2.dp
private const val RING_ROTATION_MS = 1_100
private const val RING_PEAK_START = 70f / 360f
private const val RING_PEAK_END = 150f / 360f

/** 리빌 행(프로토): 노트↔"다음" 간격 gap:12px · 상단 여백 padding-top:2px. */
private val REVEAL_ROW_GAP = 12.dp
private val REVEAL_ROW_TOP_PADDING = 2.dp

/**
 * 회전 그라디언트 테두리 링(프로토 quizRingBg 정합). 외곽 radius24 clip → drawBehind(오버사이즈 스윕 필을
 * 각도만큼 회전, 대각선 크기라 회전 각도 무관 모서리 간극 없음) → 2dp padding → 내부 radius22 surface 마스크.
 * 2dp 간극에 드러난 회전 필이 "도는 혜성" 링. 정적 케이스(reduceMotion || !loading)는 sweep 대신 균일
 * hairline(outlineVariant) 스트로크 — 무한 전이를 **구독하지 않아** 테스트 idle·성능 안전(MicButton 관용구).
 */
@Composable
private fun Modifier.quizLoadingRing(
    loading: Boolean,
    reduceMotion: Boolean,
): Modifier {
    val rotating = loading && !reduceMotion
    val transition = rememberInfiniteTransition(label = "quiz-ring")
    val angle by if (rotating) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(RING_ROTATION_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "quiz-ring-angle",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val peakColor = MaterialTheme.colorScheme.primary
    val hairlineColor = MaterialTheme.colorScheme.outlineVariant
    val innerSurface = MaterialTheme.colorScheme.surface
    return this
        .clip(OceTheme.shapes.radius24)
        .drawBehind {
            if (rotating) {
                val diagonal = sqrt(size.width * size.width + size.height * size.height)
                rotate(angle) {
                    drawRect(
                        brush =
                            Brush.sweepGradient(
                                0f to Color.Transparent,
                                RING_PEAK_START to peakColor,
                                RING_PEAK_END to Color.Transparent,
                                1f to Color.Transparent,
                                center = center,
                            ),
                        topLeft = Offset(center.x - diagonal / 2f, center.y - diagonal / 2f),
                        size = Size(diagonal, diagonal),
                    )
                }
            } else {
                drawRect(color = hairlineColor)
            }
        }
        .padding(RING_WIDTH)
        .clip(OceTheme.shapes.radius22)
        .background(innerSurface)
}

/** 프리뷰/테스트용 샘플 문항(실 번들은 소비 이슈가 주입). */
internal fun previewWaitQuizItems(): List<QuizItem> =
    listOf(
        QuizItem(
            id = "sample-1",
            level = 1,
            prompt = "계획이 있다고 말할 때, 더 자연스러운 표현은?",
            optionA = "I have a plan.",
            optionB = "I have plan.",
            correctIndex = 0,
            revealCopyCorrect = "맞아요! 관사를 잘 챙겼어요.",
            revealCopyWrong = "괜찮아요. \"a plan\" 처럼 관사를 붙여요.",
        ),
        QuizItem(
            id = "sample-2",
            level = 2,
            prompt = "정중하게 부탁할 때, 더 알맞은 표현은?",
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

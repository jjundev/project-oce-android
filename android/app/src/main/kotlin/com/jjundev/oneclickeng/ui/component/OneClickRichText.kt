package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * C15 EN+KO 이중노출 블록 렌더러 = [AnnotatedString] 스팬 렌더러. 정본: 02-shared-components.md:115.
 *
 * [segments] 를 색+형태 이중신호로 렌더한다. [localeEnglish] 가 true 면 전 구간에 `LocaleList("en")` 을
 * 부여해(A4, 06-accessibility-impl.md:67 — 안정 Compose 에 로케일 시맨틱 노드 없음 → 텍스트 레벨 로케일)
 * 한국어 TalkBack 이 영어 발음으로 읽게 한다. KO 보조 라인은 [localeEnglish] = false 로 쓴다.
 *
 * 렌더러 자체는 무상태이며, 상위 [OneClickDualExposureBlock] 이 EN(위)+KO(아래)로 합성한다.
 */
@Composable
fun OneClickRichText(
    segments: List<RichSegment>,
    modifier: Modifier = Modifier,
    localeEnglish: Boolean = true,
) {
    Text(
        text = segments.toAnnotatedString(localeEnglish),
        style = OceTheme.typography.body,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

@Composable
private fun List<RichSegment>.toAnnotatedString(localeEnglish: Boolean): AnnotatedString {
    val errorColor = MaterialTheme.colorScheme.error
    val correctionColor = OceTheme.colors.feedbackCorrectAccent
    val highlightColor = OceTheme.colors.feedbackNaturalAccent
    val highlightBg = OceTheme.colors.feedbackNaturalBg
    val localeList = if (localeEnglish) LocaleList("en") else LocaleList.current

    return buildAnnotatedString {
        this@toAnnotatedString.forEach { segment ->
            val span =
                when (segment) {
                    is RichSegment.Normal ->
                        SpanStyle(localeList = localeList)

                    is RichSegment.Incorrect ->
                        SpanStyle(
                            color = errorColor,
                            textDecoration = TextDecoration.LineThrough,
                            localeList = localeList,
                        )

                    is RichSegment.Correction ->
                        SpanStyle(
                            color = correctionColor,
                            textDecoration = TextDecoration.Underline,
                            localeList = localeList,
                        )

                    is RichSegment.Highlight ->
                        SpanStyle(
                            color = highlightColor,
                            background = highlightBg,
                            textDecoration = TextDecoration.Underline,
                            localeList = localeList,
                        )
                }
            withStyle(span) { append(segment.text) }
        }
    }
}

/**
 * C15 EN+KO 이중노출 렌더러의 세그먼트 모델. 정본: 02-shared-components.md:115.
 *
 * grammar 세그먼트(`normal`/`incorrect`/`correction`)·naturalExpression(`highlight`)를 표현한다.
 * **형태 신호 필수(A2 색단독 금지):** [Incorrect] 는 취소선, [Correction]·[Highlight] 는 밑줄을
 * 색과 **함께** 얹어, 색을 못 봐도 의미가 전달되게 한다.
 */
sealed interface RichSegment {
    val text: String

    /** 평문 — 강조 없음. */
    data class Normal(override val text: String) : RichSegment

    /** 오류 구간 — 취소선 + `state.error` 색(원문 오류 표시). */
    data class Incorrect(override val text: String) : RichSegment

    /** 교정 구간 — 밑줄 + `feedback.correct.accent`(코랄) 색. */
    data class Correction(override val text: String) : RichSegment

    /** 자연스러운 표현 강조 — 밑줄 + `feedback.natural.accent` 색 + 배경. */
    data class Highlight(override val text: String) : RichSegment
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun OneClickRichTextPreview() {
    OceTheme {
        Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
            OneClickRichText(
                segments =
                    listOf(
                        RichSegment.Normal("I "),
                        RichSegment.Incorrect("goed"),
                        RichSegment.Correction("went"),
                        RichSegment.Normal(" to school and "),
                        RichSegment.Highlight("grabbed a bite"),
                        RichSegment.Normal("."),
                    ),
            )
        }
    }
}

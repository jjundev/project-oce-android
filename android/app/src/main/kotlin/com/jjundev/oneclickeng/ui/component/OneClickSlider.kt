package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlin.math.roundToInt

/** 프로토 정합 원형 thumb 지름(M3 1.3 기본 막대형 thumb 대체). */
private val SLIDER_THUMB_DIAMETER = 20.dp

/**
 * C8 톤 슬라이더의 이산 단계 라벨. 정본: 02-shared-components.md:80 · turn-feedback-ia.md(5단계 EN+KO).
 *
 * 각 stop 은 [level](0-기반 인덱스)과 EN(영어 문장)·KO(한국어 번역)을 가진다. EN 은 렌더 시
 * `LocaleList("en")`(A4) 이 붙는다.
 */
data class ToneLabel(
    val level: Int,
    val en: String,
    val ko: String,
)

/**
 * C8 슬라이더 표현 모드. 하나의 [OneClickSlider] 가 두 모드를 실현한다(단일 컴포넌트 2모드).
 */
sealed interface SliderMode {
    /** 연속값 — 말하기 속도 0.5x~1.5x(기본 1.0). */
    data class Continuous(
        val range: ClosedFloatingPointRange<Float> = 0.5f..1.5f,
    ) : SliderMode

    /**
     * 이산값 — 톤 5단계. M3 `Slider(steps=3)` ⇒ 총 5 stop(규칙 `stops = steps + 2`).
     * [labels] 는 stop 당 EN+KO 라벨(size 5 기대, 기본 선택 lv2).
     */
    data class Discrete(
        val labels: List<ToneLabel>,
    ) : SliderMode
}

/**
 * C8 슬라이더 = M3 [Slider] 래핑, 단일 컴포넌트 2모드. 정본: 02-shared-components.md:80.
 *
 * [SliderMode.Continuous] 는 속도(0.5–1.5x, thumb 값 = 배속), [SliderMode.Discrete] 는 톤 5단계
 * (thumb 값 = stop 인덱스 0..4, `steps=3` 스냅)로 동작한다. 트랙 색은 활성 `brand.primary` /
 * 비활성 `border.hairline`. 값 변경은 M3 내장 setProgress 시맨틱에 위임하고, 현재값을
 * `stateDescription`(배속/현재 톤 라벨)으로 announce 한다(A2·A6).
 *
 * @param value Continuous = 배속 실수, Discrete = stop 인덱스(0..labels.lastIndex, 정수 실수).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneClickSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    mode: SliderMode,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    showValueLabel: Boolean = true,
) {
    val valueRange =
        when (mode) {
            is SliderMode.Continuous -> mode.range
            is SliderMode.Discrete -> 0f..(mode.labels.lastIndex.coerceAtLeast(0)).toFloat()
        }
    val steps =
        when (mode) {
            is SliderMode.Continuous -> 0
            // stops = steps + 2 ⇒ steps = labels 수 - 2 (5 stop → steps 3).
            is SliderMode.Discrete -> (mode.labels.size - 2).coerceAtLeast(0)
        }
    val state =
        when (mode) {
            is SliderMode.Continuous -> "${"%.1f".format(value)}x"
            is SliderMode.Discrete -> mode.labels.getOrNull(value.roundToInt())?.let { "${it.en} / ${it.ko}" } ?: ""
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
    ) {
        val interaction = remember { MutableInteractionSource() }
        val sliderColors =
            SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
            )
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { stateDescription = state },
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = interaction,
            colors = sliderColors,
            // 프로토 정합: 막대형 thumb·끝점 stop-indicator·트랙 갭 제거 → 원형 thumb + 매끈한 트랙.
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interaction,
                    colors = sliderColors,
                    thumbSize = DpSize(SLIDER_THUMB_DIAMETER, SLIDER_THUMB_DIAMETER),
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    colors = sliderColors,
                    thumbTrackGapSize = 0.dp,
                    drawStopIndicator = null,
                    trackInsideCornerSize = 0.dp,
                )
            },
        )
        when (mode) {
            is SliderMode.Continuous ->
                if (showValueLabel) {
                    Text(
                        text = "${"%.1f".format(value)}x",
                        style = OceTheme.typography.body,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

            is SliderMode.Discrete ->
                mode.labels.getOrNull(value.roundToInt())?.let { label ->
                    Text(
                        text = label.toDualLine(),
                        style = OceTheme.typography.body,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
        }
    }
}

/** stop 라벨을 EN(로케일 en) + KO 두 줄로 표기한다(A4). */
private fun ToneLabel.toDualLine(): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(localeList = LocaleList("en"))) { append(en) }
        append("\n")
        append(ko)
    }

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 320)
@Composable
private fun OneClickSliderContinuousPreview() {
    OceTheme {
        OneClickSlider(
            value = 1.0f,
            onValueChange = {},
            mode = SliderMode.Continuous(),
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 320)
@Composable
private fun OneClickSliderDiscretePreview() {
    OceTheme {
        OneClickSlider(
            value = 2f,
            onValueChange = {},
            mode = SliderMode.Discrete(labels = previewToneLabels()),
        )
    }
}

/** 프리뷰/카탈로그용 톤 5단계 샘플(실 콘텐츠는 소비 화면이 주입). */
internal fun previewToneLabels(): List<ToneLabel> =
    listOf(
        ToneLabel(0, "No worries.", "괜찮아요."),
        ToneLabel(1, "It's all good.", "다 괜찮아요."),
        ToneLabel(2, "That works for me.", "저는 그걸로 좋아요."),
        ToneLabel(3, "That would be acceptable.", "그 정도면 받아들일 만해요."),
        ToneLabel(4, "That is entirely satisfactory.", "전적으로 만족스럽습니다."),
    )

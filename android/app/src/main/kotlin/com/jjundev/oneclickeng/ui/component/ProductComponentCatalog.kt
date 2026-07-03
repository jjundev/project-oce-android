package com.jjundev.oneclickeng.ui.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * M0-06 제품특화 컴포넌트 카탈로그 프리뷰(수용 기준: 라이트/다크 대조 + 프로토타입 대조). 컴포넌트당 파일 1개
 * 규칙의 **명시적 예외** — 카탈로그는 검증 진입점이라 `ui/component/` 최상위 단일 파일로 둔다(코어 카탈로그와 동일).
 *
 * 인라인 렌더 가능한 컴포넌트만 싣는다. 오버레이(C2 다이얼로그·C10 타임피커·C12 게이트·C13/C19 시트·C18 패널)와
 * C9(SegmentedControl 재사용 — 스코프 철회, 신규 빌드 아님)는 각 파일의 개별 프리뷰가 담당한다.
 */
@Composable
private fun ProductComponentCatalog() {
    var speed by remember { mutableFloatStateOf(1.0f) }
    var tone by remember { mutableFloatStateOf(2f) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(OceTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.lg),
        ) {
            CatalogLabel("C8 슬라이더 — 속도(연속)")
            OneClickSlider(value = speed, onValueChange = { speed = it }, mode = SliderMode.Continuous())

            CatalogLabel("C8 슬라이더 — 톤 5단계(이산)")
            OneClickSlider(
                value = tone,
                onValueChange = { tone = it },
                mode = SliderMode.Discrete(labels = previewToneLabels()),
            )

            CatalogLabel("C11 인라인 재시도 에러 [A]")
            OneClickInlineError(
                mode = InlineErrorMode.Recoverable,
                message = "이 부분을 불러오지 못했어요.",
                onRetry = {},
                onSkip = {},
            )
            OneClickInlineError(
                mode = InlineErrorMode.Blocked,
                message = "여러 번 실패했어요.",
                onRetry = {},
                onSkip = {},
            )

            CatalogLabel("C14 streak 칩 + XP 카운터")
            OneClickStreakChip(days = 7)
            OneClickXpChip(xp = 120, static = true)

            CatalogLabel("C15 EN+KO 이중노출")
            OneClickDualExposureBlock(
                english =
                    listOf(
                        RichSegment.Normal("I "),
                        RichSegment.Incorrect("goed"),
                        RichSegment.Correction("went"),
                        RichSegment.Normal(" and "),
                        RichSegment.Highlight("grabbed a bite"),
                        RichSegment.Normal("."),
                    ),
                korean = "나는 가서 간단히 먹었어요.",
            )

            CatalogLabel("C16 슬롯머신 카운트업(정적)")
            OneClickCountUp(target = 120, unit = " XP", static = true)

            CatalogLabel("C17 이어하기 프롬프트")
            OneClickResumePrompt(onResume = {}, onStartNew = {})
        }
    }
}

@Composable
private fun CatalogLabel(text: String) {
    Text(
        text = text,
        style = OceTheme.typography.sectionLabel,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Product Catalog Light", showBackground = true, widthDp = 360, heightDp = 1600)
@Composable
private fun ProductComponentCatalogLightPreview() {
    OceTheme(darkTheme = false) {
        ProductComponentCatalog()
    }
}

@Suppress("UnusedPrivateMember")
@Preview(
    name = "Product Catalog Dark",
    showBackground = true,
    widthDp = 360,
    heightDp = 1600,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProductComponentCatalogDarkPreview() {
    OceTheme(darkTheme = true) {
        ProductComponentCatalog()
    }
}

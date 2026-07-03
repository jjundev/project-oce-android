package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * C15 EN+KO 이중노출 블록 = [OneClickRichText] 2개(EN 위 + KO 아래 보조) 합성. 정본: 02-shared-components.md:115.
 *
 * 학습 콘텐츠는 영어를 1차로, 한국어를 보조로 노출한다. EN 라인은 스팬 강조(취소선/밑줄/배경)를
 * 담을 수 있어 [English] 세그먼트로 받고 `LocaleList("en")`(A4) 이 적용된다. KO 라인은 평문 보조 문구.
 *
 * @param english EN 강조 세그먼트(grammar/naturalExpression 렌더).
 * @param korean KO 보조 번역(없으면 EN 만 노출).
 */
@Composable
fun OneClickDualExposureBlock(
    english: List<RichSegment>,
    modifier: Modifier = Modifier,
    korean: String? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
    ) {
        OneClickRichText(segments = english, localeEnglish = true)
        if (korean != null) {
            Text(
                text = korean,
                style = OceTheme.typography.helper,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun OneClickDualExposureBlockPreview() {
    OceTheme {
        OneClickDualExposureBlock(
            english =
                listOf(
                    RichSegment.Normal("Could you "),
                    RichSegment.Highlight("give me a hand"),
                    RichSegment.Normal(" with this?"),
                ),
            korean = "이것 좀 도와줄 수 있어요?",
        )
    }
}

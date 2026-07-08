package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** 선택 세그먼트 알약 hairline 보더 폭 — 컴포넌트 로컬 dp(디자인 토큰 값 아님). 선례: OneClickOfflineBanner.kt. */
private val SegmentedSelectedBorder = 1.dp

/**
 * C9 / DS-19 세그먼트 컨트롤 = pill 트랙 안 N개 세그먼트 중 단일 선택(정본: 04-screen-06-history.md:32 R2,
 * ProductComponentCatalog.kt:27 "재사용"). 기록 탭 3종 필터(표현/단어/문장)와 설정 음질 2지선다가 공유하는
 * 단일 프리미티브 — 화면 로컬 원오프를 새로 만들지 않는다.
 *
 * 실현 정본(realization-SoT) 번들 `SegmentedControl.jsx`: 트랙 `surface.background`(= colorScheme.background)
 * + `radius.pill`, 선택 세그먼트는 `surface.card`(흰색) 채움 + `text.primary` 글자 + hairline 보더 알약이다.
 * 번들의 미세 그림자는 앱 elevation 정본(OceElevation "그림자 금지, 깊이=surface+hairline")에 맞춰 hairline
 * 보더로 대체한다. 비선택 글자는 `text.tertiary`. 접근성은 [selectableGroup] + 세그먼트별 [Role.RadioButton]
 * [selectable] 로 라디오 그룹으로 노출한다(탭 라벨은 [label] 이 문자열화).
 */
@Composable
fun <T> OneClickSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(OceTheme.shapes.pill)
                .background(MaterialTheme.colorScheme.background)
                .padding(OceTheme.spacing.xs)
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Text(
                text = label(option),
                style = if (isSelected) OceTheme.typography.tabActive else OceTheme.typography.helper,
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        OceTheme.colors.textTertiary
                    },
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(OceTheme.shapes.pill)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = SegmentedSelectedBorder,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = OceTheme.shapes.pill,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(option) },
                        )
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        )
                        .padding(vertical = OceTheme.spacing.sm),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun OneClickSegmentedControlPreview() {
    OceTheme {
        OneClickSegmentedControl(
            options = listOf("표현", "단어", "문장"),
            selected = "표현",
            onSelect = {},
            label = { it },
            modifier = Modifier.padding(OceTheme.spacing.xl),
        )
    }
}

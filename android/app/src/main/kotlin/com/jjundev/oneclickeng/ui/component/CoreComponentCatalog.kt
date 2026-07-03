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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.jjundev.oneclickeng.ui.component.primitive.OneClickBadge
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.component.primitive.OneClickInput
import com.jjundev.oneclickeng.ui.component.primitive.OneClickListRow
import com.jjundev.oneclickeng.ui.component.primitive.OneClickSwitch
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 코어 컴포넌트 카탈로그 프리뷰(수용 기준: 라이트/다크 대조 + 프로토타입 대조). 컴포넌트당 파일 1개 규칙의
 * **명시적 예외** — 카탈로그는 컴포넌트가 아니라 검증 진입점이라 `ui/component/` 최상위에 단일 파일로 둔다.
 *
 * 인라인 렌더 가능한 컴포넌트만 싣는다. 모달 오버레이(C1 다이얼로그·BottomSheet)와 스낵바는 각 파일의
 * 개별 프리뷰가 담당한다.
 */
@Composable
private fun CoreComponentCatalog() {
    var inputValue by remember { mutableStateOf("") }
    var switchOn by remember { mutableStateOf(true) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(OceTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.lg),
        ) {
            CatalogLabel("C4 오프라인 배너")
            OneClickOfflineBanner(visible = true)

            CatalogLabel("C5 빈 상태")
            OneClickEmptyState(
                icon = OceIcon.Notes,
                title = "아직 저장한 표현이 없어요",
                subtitle = "대화하면서 마음에 드는 표현을 저장해보세요.",
                ctaStrength = EmptyStateCtaStrength.Ghost,
                ctaLabel = "대화 시작하기",
                onCta = {},
            )

            CatalogLabel("C6 스켈레톤(Line/Card/Section)")
            OneClickSkeleton(shape = SkeletonShape.Line)
            OneClickSkeleton(shape = SkeletonShape.Card)
            OneClickSkeleton(shape = SkeletonShape.Section)

            CatalogLabel("C7 프로그레스 링")
            OneClickProgressRing(mode = ProgressRingMode.Determinate(fraction = 0.6f))
            OneClickProgressRing(mode = ProgressRingMode.Indeterminate)

            CatalogLabel("프리미티브 — Card / ListRow")
            OneClickCard {
                Text(
                    text = "카드 콘텐츠",
                    style = OceTheme.typography.body,
                    modifier = Modifier.padding(OceTheme.spacing.lg),
                )
            }
            OneClickListRow(
                headline = "말하기 속도",
                supporting = "1.0x",
                onClick = {},
            )

            CatalogLabel("프리미티브 — Input / Switch / Badge")
            OneClickInput(
                value = inputValue,
                onValueChange = { inputValue = it },
                label = "닉네임",
                modifier = Modifier.fillMaxWidth(),
            )
            OneClickSwitch(checked = switchOn, onCheckedChange = { switchOn = it })
            OneClickBadge(text = "3")
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
@Preview(name = "Catalog Light", showBackground = true, widthDp = 360, heightDp = 1400)
@Composable
private fun CoreComponentCatalogLightPreview() {
    OceTheme(darkTheme = false) {
        CoreComponentCatalog()
    }
}

@Suppress("UnusedPrivateMember")
@Preview(
    name = "Catalog Dark",
    showBackground = true,
    widthDp = 360,
    heightDp = 1400,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun CoreComponentCatalogDarkPreview() {
    OceTheme(darkTheme = true) {
        CoreComponentCatalog()
    }
}

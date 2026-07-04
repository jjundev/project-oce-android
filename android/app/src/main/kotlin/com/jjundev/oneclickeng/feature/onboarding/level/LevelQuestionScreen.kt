package com.jjundev.oneclickeng.feature.onboarding.level

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.jjundev.oneclickeng.feature.onboarding.OnboardingViewModel
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 온보딩 첫 문항 = 레벨 3지선다(M3-02, O1 rev2). 세로 스택 [OneClickCard] 3장, 탭 즉시 다음. SegmentedControl
 * 미채택 — 세그먼트는 "채점/평가"로 읽혀 "평가처럼 안 보이게" 제약과 충돌한다. 카피는 평가가 아니라 취향
 * 선택처럼 읽히게 쓴다(부제로 각 난이도를 안심 문구로 설명).
 *
 * 저장·분석은 [OnboardingViewModel] 이 소유한다: 탭 시 `profile.level` 을 fire-and-forget 저장(내비 비차단)하고
 * `level_selected` 를 남긴다. 첫 세션은 무엇을 고르든 `easy` 로 강제되며, 저장된 레벨은 세션 #2 부터 반영된다
 * (소비는 M3-08). [onLevelSelected] 는 상위 그래프의 내비 액션 — 선택 값을 실어 상황 문항으로 넘긴다.
 */
@Composable
fun LevelQuestionScreen(
    onLevelSelected: (level: String) -> Unit,
    modifier: Modifier = Modifier,
    isReturning: Boolean = false,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.onOnboardingStarted(isReturning) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(OceTheme.spacing.sheetPadding),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
    ) {
        Text(
            text = "먼저, 오늘 연습을 맞춰볼게요",
            style = OceTheme.typography.screenTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "첫 대화는 쉽게 시작하고, 선택한 난이도는 다음 대화부터 반영돼요.",
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = OceTheme.spacing.md),
        )
        LEVEL_OPTIONS.forEach { option ->
            LevelCard(
                option = option,
                onClick = {
                    viewModel.onLevelSelected(option.value)
                    onLevelSelected(option.value)
                },
            )
        }
    }
}

/** 레벨 선택카드 1장. 제목(난이도) + 안심 부제. 탭 전체가 클릭 타깃. */
@Composable
private fun LevelCard(
    option: LevelOption,
    onClick: () -> Unit,
) {
    OneClickCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(OceTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
        ) {
            Text(
                text = option.titleKo,
                style = OceTheme.typography.sectionLabel,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = option.subtitleKo,
                style = OceTheme.typography.helper,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 레벨 선택지. [value] = `profile.level` 저장 값(easy/normal/hard). */
private data class LevelOption(
    val value: String,
    val titleKo: String,
    val subtitleKo: String,
)

private val LEVEL_OPTIONS =
    listOf(
        LevelOption(value = "easy", titleKo = "쉬움", subtitleKo = "천천히, 쉬운 표현부터 시작해요"),
        LevelOption(value = "normal", titleKo = "보통", subtitleKo = "일상 대화를 자연스럽게 이어가요"),
        LevelOption(value = "hard", titleKo = "어려움", subtitleKo = "조금 더 길고 깊은 대화까지 해봐요"),
    )

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun LevelQuestionPreview() {
    OceTheme {
        Column {
            LEVEL_OPTIONS.forEach { LevelCard(option = it, onClick = {}) }
        }
    }
}

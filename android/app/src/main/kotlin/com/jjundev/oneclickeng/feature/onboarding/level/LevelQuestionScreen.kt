package com.jjundev.oneclickeng.feature.onboarding.level

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jjundev.oneclickeng.feature.onboarding.OnboardingStepBar
import com.jjundev.oneclickeng.feature.onboarding.OnboardingViewModel
import com.jjundev.oneclickeng.feature.onboarding.google.GoogleReauthPromptSheet
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.foundation.rememberScreenEntrance
import com.jjundev.oneclickeng.ui.foundation.staggerReveal
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
    var showReauthSheet by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        LevelQuestionContent(
            onLevelSelected = { value ->
                viewModel.onLevelSelected(value)
                onLevelSelected(value)
            },
            onReauthTapped = { showReauthSheet = true },
            reduceMotion = rememberReduceMotion(),
        )
        if (showReauthSheet) {
            GoogleReauthPromptSheet(onDismiss = { showReauthSheet = false })
        }
    }
}

/**
 * 레벨 문항 콘텐츠(stateless) — VM/분석 없이 렌더하는 스크린샷 seam. `onOnboardingStarted` 계측은 상태
 * 소유자([LevelQuestionScreen])에 남는다. [onLevelSelected] 는 저장 값(easy/normal/hard)을 싣는다.
 */
@Composable
internal fun LevelQuestionContent(
    onLevelSelected: (level: String) -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    onReauthTapped: () -> Unit = {},
) {
    val entrance = rememberScreenEntrance(reduceMotion)
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(OceTheme.spacing.sheetPadding),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
    ) {
        OnboardingStepBar(step = 1, total = 2, modifier = Modifier.staggerReveal(0, entrance))
        Text(
            text = "먼저, 오늘 연습을 맞춰볼게요",
            // 온보딩 H1 은 프로토 정합상 ExtraBold·24sp → homeTitle(800·25sp) 재사용(±1sp, 공용 screenTitle 과 구분).
            style = OceTheme.typography.homeTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.staggerReveal(1, entrance).semantics { heading() },
        )
        Text(
            text = "첫 대화는 쉽게 시작하고, 선택한 난이도는 다음 대화부터 반영돼요.",
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.staggerReveal(2, entrance).padding(bottom = OceTheme.spacing.md),
        )
        LEVEL_OPTIONS.forEachIndexed { index, option ->
            Box(modifier = Modifier.staggerReveal(index + LEVEL_CARD_STAGGER_OFFSET, entrance)) {
                LevelCard(
                    option = option,
                    onClick = { onLevelSelected(option.value) },
                )
            }
        }
        ExistingAccountEntry(
            onClick = onReauthTapped,
            modifier = Modifier.staggerReveal(LEVEL_OPTIONS.size + LEVEL_CARD_STAGGER_OFFSET, entrance),
        )
    }
}

/** Column 직계 자식 중 스텝바/제목/부제(0~2) 다음, LevelCard 스태거 인덱스 시작 오프셋. */
private const val LEVEL_CARD_STAGGER_OFFSET = 3

@Composable
private fun ExistingAccountEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "이미 계정이 있나요?",
        style = OceTheme.typography.helper,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = OceTheme.spacing.sm),
    )
}

/**
 * 레벨 선택카드 1장(프로토타입 온보딩·레벨 정합) — 좌측 표정 아이콘 + 제목/부제 + 우측 chevron. 추천 카드
 * ([LevelOption.recommended])는 "처음이라면 추천" 배지 + 브랜드 테두리 하이라이트를 얹는다. 탭 전체가 클릭 타깃.
 */
@Composable
private fun LevelCard(
    option: LevelOption,
    onClick: () -> Unit,
) {
    OneClickCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (option.recommended) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = OceTheme.shapes.radius16,
                        )
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OceTheme.spacing.lg, vertical = OceTheme.spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(OceTheme.shapes.radius12)
                        .background(
                            if (option.recommended) {
                                MaterialTheme.colorScheme.primary.copy(alpha = ICON_BG_ALPHA)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = NEUTRAL_ICON_BG_ALPHA)
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                OneClickIcon(
                    icon = option.icon,
                    contentDescription = null,
                    tint =
                        if (option.recommended) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
                ) {
                    Text(
                        text = option.titleKo,
                        // 레벨 선택 카드 제목은 프로토 정합상 Bold·17sp(sectionLabel 14sp 대비 +3sp) — 볼드 유지, 크기 상향.
                        style = OceTheme.typography.sectionLabel.copy(fontSize = 17.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (option.recommended) RecommendedBadge()
                }
                Text(
                    text = option.subtitleKo,
                    style = OceTheme.typography.helper,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OneClickIcon(
                icon = OceIcon.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = OceIconSize.ListDisclosure,
            )
        }
    }
}

/** "처음이라면 추천" pill 배지 — 브랜드 배경 + 흰 텍스트. */
@Composable
private fun RecommendedBadge() {
    Text(
        text = "처음이라면 추천",
        // 배지 문구는 프로토 정합상 Bold·11sp(기존 helper 13sp 대비 축소 + 볼드) — "너무 크던" 배지 축소.
        style = OceTheme.typography.sectionLabel.copy(fontSize = 11.sp),
        color = MaterialTheme.colorScheme.onPrimary,
        modifier =
            Modifier
                .clip(OceTheme.shapes.pill)
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = OceTheme.spacing.sm, vertical = OceTheme.spacing.xs),
    )
}

private const val ICON_BG_ALPHA = 0.12f
private const val NEUTRAL_ICON_BG_ALPHA = 0.06f

/** 레벨 선택지. [value] = `profile.level` 저장 값(easy/normal/hard). */
private data class LevelOption(
    val value: String,
    val titleKo: String,
    val subtitleKo: String,
    val icon: OceIcon,
    val recommended: Boolean = false,
)

private val LEVEL_OPTIONS =
    listOf(
        LevelOption(
            value = "easy",
            titleKo = "쉬움",
            subtitleKo = "천천히, 쉬운 표현부터 시작해요",
            icon = OceIcon.SentimentSatisfied,
            recommended = true,
        ),
        LevelOption(
            value = "normal",
            titleKo = "보통",
            subtitleKo = "일상 대화를 자연스럽게 이어가요",
            icon = OceIcon.SentimentNeutral,
        ),
        LevelOption(
            value = "hard",
            titleKo = "어려움",
            subtitleKo = "조금 더 길고 깊은 대화까지 해봐요",
            icon = OceIcon.SentimentVeryDissatisfied,
        ),
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

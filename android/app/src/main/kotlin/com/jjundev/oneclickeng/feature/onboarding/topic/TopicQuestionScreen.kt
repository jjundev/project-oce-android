package com.jjundev.oneclickeng.feature.onboarding.topic

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.foundation.rememberScreenEntrance
import com.jjundev.oneclickeng.ui.foundation.staggerReveal
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 온보딩 둘째 문항 = 상황 선택(M3-02, O2 rev2). 상단 진행 바(뒤로가기 + 2/2) + 제목·부제 + 단일 컬럼 6-카드
 * 리스트(그리드 아님 — 입문 친화·큰 터치타깃). 각 카드는 좌측 상황 아이콘(틴트 원) + titleKo + 우측 chevron
 * 이며(프로토타입 온보딩·상황 정합), 탭 즉시 생성 전이(확인 화면 없음). 후보는 [ONBOARDING_TOPICS]
 * (beginnerFriendly 6개). 이모지 미사용(P16) — 표시는 Material 심볼 벡터([OnboardingTopic.icon]).
 *
 * [onTopicSelected] 는 상위 그래프의 내비 액션 — 선택 상황(promptSeed)을 실어 생성 화면으로 넘긴다.
 * [onBack] 은 레벨 문항으로 되돌아가는 내비(2/2 단계라 뒤로가기 노출). 분석(`topic_selected`)은
 * [OnboardingViewModel] 이 남긴다.
 */
@Composable
fun TopicQuestionScreen(
    onTopicSelected: (topic: OnboardingTopic) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    TopicQuestionContent(
        onTopicSelected = { topic ->
            viewModel.onTopicSelected(topic.id)
            onTopicSelected(topic)
        },
        onBack = onBack,
        modifier = modifier,
        reduceMotion = rememberReduceMotion(),
    )
}

/** VM/분석 없는 렌더 심(seam) — 프로토타입 대조 스크린샷·프리뷰용. 프로덕션 진입점은 [TopicQuestionScreen]. */
@Composable
internal fun TopicQuestionContent(
    onTopicSelected: (topic: OnboardingTopic) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    reduceMotion: Boolean = false,
) {
    val entrance = rememberScreenEntrance(reduceMotion)
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(OceTheme.spacing.sheetPadding),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
    ) {
        OnboardingStepBar(
            step = 2,
            total = 2,
            modifier = Modifier.staggerReveal(0, entrance),
            onBack = onBack,
        )
        Text(
            text = "어떤 상황에서 말해볼까요?",
            // 온보딩 H1 은 프로토 정합상 ExtraBold·24sp → homeTitle(800·25sp) 재사용(±1sp, 공용 screenTitle 과 구분).
            style = OceTheme.typography.homeTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.staggerReveal(1, entrance).semantics { heading() },
        )
        Text(
            text = "익숙한 상황부터 골라보세요. 대화 중에도 언제든 바꿀 수 있어요.",
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.staggerReveal(2, entrance).padding(bottom = OceTheme.spacing.md),
        )
        ONBOARDING_TOPICS.forEachIndexed { index, topic ->
            Box(modifier = Modifier.staggerReveal(index + TOPIC_CARD_STAGGER_OFFSET, entrance)) {
                TopicCard(
                    topic = topic,
                    onClick = { onTopicSelected(topic) },
                )
            }
        }
    }
}

/** Column 직계 자식 중 스텝바/제목/부제(0~2) 다음, TopicCard 스태거 인덱스 시작 오프셋. */
private const val TOPIC_CARD_STAGGER_OFFSET = 3

/**
 * 상황 카드 1장(프로토타입 온보딩·상황 정합) — 좌측 상황 아이콘(브랜드 틴트 원) + titleKo + 우측 chevron.
 * 배지·부제·기본선택 강조 없음(O2). 탭 전체가 클릭 타깃.
 */
@Composable
private fun TopicCard(
    topic: OnboardingTopic,
    onClick: () -> Unit,
) {
    OneClickCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(OceTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(OceTheme.shapes.radius12)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = ICON_BG_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                OneClickIcon(
                    icon = topic.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = topic.titleKo,
                // 상황 카드 제목은 프로토 정합상 15sp(sectionLabel 14sp 대비 +1sp) — 볼드 유지, 크기만 상향.
                style = OceTheme.typography.sectionLabel.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            OneClickIcon(
                icon = OceIcon.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = OceIconSize.ListDisclosure,
            )
        }
    }
}

private const val ICON_BG_ALPHA = 0.12f

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun TopicQuestionPreview() {
    OceTheme {
        TopicQuestionContent(onTopicSelected = {}, onBack = {})
    }
}

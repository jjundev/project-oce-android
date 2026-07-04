package com.jjundev.oneclickeng.feature.onboarding.topic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.jjundev.oneclickeng.feature.onboarding.OnboardingViewModel
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 온보딩 둘째 문항 = 상황 선택(M3-02, O2 rev2). 단일 컬럼 6-카드 리스트(그리드 아님 — 입문 친화·큰 터치타깃).
 * 이모지 미사용(P16), 첫 카드(`카페에서 주문하기`)에 추천 배지·기본선택 강조 없음, 탭 즉시 생성 전이(확인
 * 화면 없음). 후보는 [ONBOARDING_TOPICS](beginnerFriendly 6개).
 *
 * [onTopicSelected] 는 상위 그래프의 내비 액션 — 선택 상황(promptSeed)을 실어 생성 화면으로 넘긴다. 분석
 * (`topic_selected`)은 [OnboardingViewModel] 이 남긴다.
 */
@Composable
fun TopicQuestionScreen(
    onTopicSelected: (topic: OnboardingTopic) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(OceTheme.spacing.sheetPadding),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
    ) {
        Text(
            text = "어떤 상황에서 말해볼까요?",
            style = OceTheme.typography.screenTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .padding(bottom = OceTheme.spacing.md)
                    .semantics { heading() },
        )
        ONBOARDING_TOPICS.forEach { topic ->
            TopicCard(
                topic = topic,
                onClick = {
                    viewModel.onTopicSelected(topic.id)
                    onTopicSelected(topic)
                },
            )
        }
    }
}

/** 상황 카드 1장 — titleKo 만(이모지·배지 없음). 탭 전체가 클릭 타깃. */
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
        Text(
            text = topic.titleKo,
            style = OceTheme.typography.sectionLabel,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(OceTheme.spacing.lg),
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun TopicQuestionPreview() {
    OceTheme {
        Column {
            ONBOARDING_TOPICS.forEach { TopicCard(topic = it, onClick = {}) }
        }
    }
}

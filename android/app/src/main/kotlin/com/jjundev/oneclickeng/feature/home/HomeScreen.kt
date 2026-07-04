package com.jjundev.oneclickeng.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jjundev.oneclickeng.R
import com.jjundev.oneclickeng.feature.reminder.ui.HomeReminderHost
import com.jjundev.oneclickeng.ui.component.OneClickAtLimitNotice
import com.jjundev.oneclickeng.ui.component.OneClickResumePrompt
import com.jjundev.oneclickeng.ui.component.OneClickStreakChip
import com.jjundev.oneclickeng.ui.foundation.TabScreenScaffold
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** 히어로 CTA 최소 탭 타겟(오프라인 비활성 시에도 48dp 유지, H1/H7). */
private val HeroMinHeight = 96.dp

/**
 * 학습(홈) 탭 — 대시보드가 아닌 "학습 시작 허브"(M3-08, home-learning-entry.md). 위계: 미완 이어하기(상단,
 * 있을 때만) → hero CTA `오늘 5분 말하기` → 게임화 2지표 스트립(학습시간·streak) → at-limit 보조 고지(fresh
 * remaining==0 일 때만). XP 는 홈 미표시(H2). 빈 상태는 CTA + 초대 카피가 대체(§5.4).
 *
 * [onStartLearning]/[onResume]/[onViewRecords] 는 outer/inner 내비를 소유하는 소비처가 주입한다 — 홈은
 * 판단(계측·상태)만 하고 목적지 전이는 람다에 위임한다.
 *
 * [HomeReminderHost] 는 M3-07 리마인더 opt-in 오버레이 — 홈 콘텐츠와 독립 합성(스캐폴드 밖 최상위).
 */
@Composable
fun HomeScreen(
    onStartLearning: () -> Unit,
    onResume: () -> Unit,
    onViewRecords: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TabScreenScaffold(titleRes = R.string.tab_home, modifier = modifier) {
        if (state.hasResume) {
            item(key = "resume") {
                OneClickResumePrompt(
                    onResume = {
                        viewModel.onResumeContinue()
                        onResume()
                    },
                    onStartNew = {
                        viewModel.onResumeStartNew()
                        onStartLearning()
                    },
                    modifier = Modifier.padding(bottom = OceTheme.spacing.lg),
                )
            }
        }

        item(key = "hero") {
            HeroCta(
                online = state.isOnline,
                onClick = {
                    viewModel.onCtaTap()
                    onStartLearning()
                },
                onDisabledClick = viewModel::onOfflineBlocked,
            )
        }

        item(key = "gamification") {
            GamificationStrip(
                studyTimeLabel = state.studyTimeLabel,
                streak = state.streak,
            )
        }

        if (state.atLimit) {
            item(key = "atLimit") {
                OneClickAtLimitNotice(
                    onViewRecords = onViewRecords,
                    modifier = Modifier.padding(top = OceTheme.spacing.lg),
                )
            }
        }
    }
    HomeReminderHost()
}

/**
 * 메인 CTA hero(H1 rev2) — brand.gradient 카드 블록(radius.24, 흰 텍스트). 항상 우선순위 #1 위계. 오프라인이면
 * alpha 0.38 + `semantics{ disabled() }` + 인접 헬퍼(비색 신호, H7/P8); onClick 미부여(no-op 가드), 48dp 유지.
 */
@Composable
private fun HeroCta(
    online: Boolean,
    onClick: () -> Unit,
    onDisabledClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = HeroMinHeight)
                    .clip(OceTheme.shapes.radius24)
                    .alpha(if (online) 1f else DISABLED_ALPHA)
                    .background(OceTheme.colors.brandGradient())
                    .then(
                        if (online) {
                            Modifier.clickable(onClick = onClick)
                        } else {
                            // 오프라인: 클릭 미부여(계측만) + disabled 시맨틱.
                            Modifier
                                .clickable(onClick = onDisabledClick)
                                .semantics { disabled() }
                        },
                    )
                    .padding(OceTheme.spacing.xl),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "오늘 5분 말하기",
                style = OceTheme.typography.screenTitle,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = "오늘은 어떤 상황을 연습해볼까요?",
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        if (!online) {
            Text(
                text = "새 대화는 인터넷 연결이 필요해요.",
                style = OceTheme.typography.helper,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 게임화 요약 스트립(H2) — CTA 아래 낮은 위계 정적 스트립. `오늘 N분`(중심) + `🔥 N일`. XP 는 홈 미표시.
 * streak 0 은 숨긴다(초대 카피 대체). 학습시간 미로딩(null) 이면 스트립 자체를 렌더하지 않는다.
 */
@Composable
private fun GamificationStrip(
    studyTimeLabel: String?,
    streak: Int,
) {
    if (studyTimeLabel == null && streak <= 0) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = OceTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
    ) {
        if (studyTimeLabel != null) {
            Text(
                text = studyTimeLabel,
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (streak > 0) {
            OneClickStreakChip(days = streak)
        }
    }
}

private const val DISABLED_ALPHA = 0.38f

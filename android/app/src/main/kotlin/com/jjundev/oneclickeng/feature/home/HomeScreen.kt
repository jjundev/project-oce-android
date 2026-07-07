package com.jjundev.oneclickeng.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jjundev.oneclickeng.feature.reminder.ui.HomeReminderHost
import com.jjundev.oneclickeng.ui.component.OneClickAtLimitNotice
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** 히어로 CTA 최소 탭 타겟(오프라인 비활성 시에도 48dp 유지, H1/H7). */
private val HeroMinHeight = 96.dp
private const val DISABLED_ALPHA = 0.38f
private const val PLAY_BADGE_ALPHA = 0.2f
private const val SITUATION_ICON_BG_ALPHA = 0.12f
private const val MORE_BTN_BG_ALPHA = 0.05f

/**
 * 학습(홈) 탭 — "학습 시작 허브"(M3-08). 프로토타입 홈(허브) 정합: 맥락 H1 → 인라인 지표 → 히어로 CTA(이어하기면
 * 주제·턴·▶ 통합) → 새 대화 링크(이어하기 시) → 추천 상황 리스트 → at-limit 보조 고지.
 *
 * [onStartLearning]/[onResume]/[onViewRecords] 는 소비처가 주입한다 — 홈은 계측·상태만 하고 전이는 위임한다.
 * [HomeReminderHost] 는 M3-07 리마인더 opt-in 오버레이(스캐폴드 밖 최상위 합성).
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

    HomeContent(
        state = state,
        onStartLearning = {
            viewModel.onCtaTap()
            onStartLearning()
        },
        onResumeContinue = {
            viewModel.onResumeContinue()
            onResume()
        },
        onResumeStartNew = {
            viewModel.onResumeStartNew()
            onStartLearning()
        },
        onViewRecords = onViewRecords,
        onOfflineBlocked = viewModel::onOfflineBlocked,
        modifier = modifier,
    )
    HomeReminderHost()
}

/**
 * 홈 콘텐츠(stateless) — VM/내비 없이 [HomeUiState] 로 렌더하는 스크린샷 seam. 추천 상황 콜백은 데이터 배선 전
 * 기본 no-op(리스트가 비면 섹션 자체가 숨는다).
 */
@Composable
internal fun HomeContent(
    state: HomeUiState,
    onStartLearning: () -> Unit,
    onResumeContinue: () -> Unit,
    onResumeStartNew: () -> Unit,
    onViewRecords: () -> Unit,
    onOfflineBlocked: () -> Unit,
    modifier: Modifier = Modifier,
    onSituationSelected: (String) -> Unit = {},
    onRefreshSituations: () -> Unit = {},
    onMoreSituations: () -> Unit = {},
) {
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = OceTheme.spacing.xl),
    ) {
        // 프로토타입 홈 리듬(비균일): 섹션 사이는 넉넉히(12~24dp), 상황 카드끼리는 촘촘히(8dp).
        item(key = "header") {
            Column(
                modifier = Modifier.padding(top = OceTheme.spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
            ) {
                Text(
                    text = if (state.hasResume) "이어서 말해볼까요?" else "오늘, 5분만 말해볼까요?",
                    style = OceTheme.typography.homeTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text =
                        if (state.hasResume) {
                            "이전 대화를 이어가거나 새로 시작할 수 있어요."
                        } else {
                            "오늘은 어떤 상황을 연습해볼까요?"
                        },
                    style = OceTheme.typography.helper.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "stats") {
            StatsStrip(
                studyTimeLabel = state.studyTimeLabel,
                streak = state.streak,
                modifier = Modifier.padding(top = OceTheme.spacing.md),
            )
        }

        item(key = "hero") {
            HeroCta(
                online = state.isOnline,
                resumeTopic = if (state.hasResume) state.resumeTopic else null,
                resumeTurn = state.resumeTurn,
                resumeTotalTurns = state.resumeTotalTurns,
                onClick = if (state.hasResume) onResumeContinue else onStartLearning,
                onDisabledClick = onOfflineBlocked,
                modifier = Modifier.padding(top = OceTheme.spacing.xl),
            )
        }

        if (state.hasResume) {
            item(key = "new_chat") {
                NewChatLink(
                    onClick = onResumeStartNew,
                    modifier = Modifier.padding(top = OceTheme.spacing.md),
                )
            }
        }

        if (state.situations.isNotEmpty()) {
            item(key = "situations_header") {
                SituationsHeader(
                    onRefresh = onRefreshSituations,
                    modifier = Modifier.padding(top = OceTheme.spacing.xxl),
                )
            }
            itemsIndexed(state.situations, key = { _, item -> item.id }) { index, situation ->
                SituationRow(
                    situation = situation,
                    onClick = { onSituationSelected(situation.id) },
                    modifier =
                        Modifier.padding(
                            top = if (index == 0) OceTheme.spacing.lg else OceTheme.spacing.sm,
                        ),
                )
            }
            item(key = "more_situations") {
                MoreSituationsButton(
                    onClick = onMoreSituations,
                    modifier = Modifier.padding(top = OceTheme.spacing.xl),
                )
            }
        }

        if (state.atLimit) {
            item(key = "atLimit") {
                OneClickAtLimitNotice(
                    onViewRecords = onViewRecords,
                    modifier = Modifier.padding(top = OceTheme.spacing.md),
                )
            }
        }
    }
}

/**
 * 메인 CTA hero(H1 rev2) — brand.gradient 카드(radius.24, 흰 텍스트). 이어하기([resumeTopic] 비-null)면 주제·턴
 * 요약 + ▶ 배지를 실어 "이어서 대화하기"로, 아니면 범용 "오늘 5분 말하기"로 렌더한다. 오프라인이면 alpha 0.38 +
 * `semantics{ disabled() }` + 인접 헬퍼(비색 신호, H7/P8), onClick 은 계측 전용 no-op 가드.
 */
@Composable
private fun HeroCta(
    online: Boolean,
    resumeTopic: String?,
    resumeTurn: Int,
    resumeTotalTurns: Int,
    onClick: () -> Unit,
    onDisabledClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
        Row(
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
                            Modifier
                                .clickable(onClick = onDisabledClick)
                                .semantics { disabled() }
                        },
                    )
                    .padding(OceTheme.spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
            ) {
                Text(
                    text = if (resumeTopic != null) "이어서 대화하기" else "오늘 5분 말하기",
                    style = OceTheme.typography.homeTitle.copy(fontSize = 23.sp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text =
                        if (resumeTopic != null) {
                            "$resumeTopic · $resumeTurn / ${resumeTotalTurns}턴"
                        } else {
                            "오늘은 어떤 상황을 연습해볼까요?"
                        },
                    style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            if (resumeTopic != null) {
                PlayBadge()
            }
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

/** 이어하기 CTA 우측 ▶ 배지 — 반투명 흰 사각 + play glyph. */
@Composable
private fun PlayBadge() {
    Box(
        modifier =
            Modifier
                .size(56.dp)
                .clip(OceTheme.shapes.radius18)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = PLAY_BADGE_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        OneClickIcon(
            icon = OceIcon.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * 게임화 요약 스트립(H2) — 인라인 `🕐 오늘 N분 · 🔥 N일 연속`(프로토타입 정합, pill 아님). streak 0 은 숨긴다.
 * 학습시간 미로딩(null)이고 streak 0 이면 스트립 자체를 렌더하지 않는다.
 */
@Composable
private fun StatsStrip(
    studyTimeLabel: String?,
    streak: Int,
    modifier: Modifier = Modifier,
) {
    if (studyTimeLabel == null && streak <= 0) return
    val statStyle = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
    ) {
        if (studyTimeLabel != null) {
            OneClickIcon(
                icon = OceIcon.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = OceIconSize.FeedbackInline,
            )
            Text(
                text = studyTimeLabel,
                style = statStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (studyTimeLabel != null && streak > 0) {
            Text(
                text = "·",
                style = statStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (streak > 0) {
            OneClickIcon(
                icon = OceIcon.LocalFireDepartment,
                contentDescription = null,
                tint = OceTheme.colors.gameStreak,
                size = OceIconSize.FeedbackInline,
            )
            Text(
                text = "${streak}일 연속",
                style = statStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "+ 새 대화 시작" 링크(이어하기 CTA 하위 보조). */
@Composable
private fun NewChatLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
    ) {
        Text(
            text = "+",
            style = OceTheme.typography.helper.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "새 대화 시작",
            style = OceTheme.typography.helper.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** "추천 상황" 섹션 헤더 — 라벨 + 그리드 토글 + 새로고침. */
@Composable
private fun SituationsHeader(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
    ) {
        Text(
            text = "추천 상황",
            style = OceTheme.typography.sectionLabel.copy(fontWeight = FontWeight.ExtraBold, fontSize = 17.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        OneClickIcon(
            icon = OceIcon.GridView,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = OceIconSize.ListDisclosure,
        )
        Row(
            modifier = Modifier.clickable(onClick = onRefresh),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
        ) {
            OneClickIcon(
                icon = OceIcon.Autorenew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                size = OceIconSize.ListDisclosure,
            )
            Text(
                text = "새로고침",
                style = OceTheme.typography.helper,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 추천 상황 1행 — 카드(선행 아이콘 + 라벨 + chevron). 선행 아이콘은 상황별 매핑 전까지 공통 glyph(후속 폴리시). */
@Composable
private fun SituationRow(
    situation: HomeSituation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OneClickCard(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OceTheme.spacing.lg, vertical = OceTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(OceTheme.shapes.radius12)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = SITUATION_ICON_BG_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                OneClickIcon(
                    icon = situation.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    size = OceIconSize.ListDisclosure,
                )
            }
            Text(
                text = situation.labelKo,
                style = OceTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
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

/** "다른 상황 고르기" — 추천 상황 리스트 하단의 tonal 버튼(프로토타입 정합). */
@Composable
private fun MoreSituationsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(OceTheme.shapes.radius16)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = MORE_BTN_BG_ALPHA))
                .clickable(onClick = onClick)
                .padding(vertical = OceTheme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OneClickIcon(
            icon = OceIcon.GridView,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = OceIconSize.ListDisclosure,
        )
        Text(
            text = "다른 상황 고르기",
            style = OceTheme.typography.body,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

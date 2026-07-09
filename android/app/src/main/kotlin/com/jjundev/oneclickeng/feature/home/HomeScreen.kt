package com.jjundev.oneclickeng.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jjundev.oneclickeng.feature.home.topic.TopicSelectSheet
import com.jjundev.oneclickeng.feature.reminder.ui.HomeReminderHost
import com.jjundev.oneclickeng.feature.reminder.ui.HomeReminderViewModel
import com.jjundev.oneclickeng.ui.component.OneClickAtLimitNotice
import com.jjundev.oneclickeng.ui.component.OneClickReminderEnabledBanner
import com.jjundev.oneclickeng.ui.component.OneClickSegmentedControl
import com.jjundev.oneclickeng.ui.component.OneClickTimePickerDialog
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** 히어로 CTA 최소 탭 타겟(오프라인 비활성 시에도 48dp 유지, H1/H7). */
private val HeroMinHeight = 96.dp
private const val DISABLED_ALPHA = 0.38f
private const val HERO_BADGE_ALPHA = 0.2f
private const val SITUATION_ICON_BG_ALPHA = 0.12f

/** 세션 설정 옵션(프로토 인라인 패널 levelOptions/lengthOptions 정합, 저장값 = profile.level 계약). */
private val LEVEL_OPTIONS = listOf("easy", "normal", "hard")
private val LENGTH_OPTIONS = listOf(5, 10)

private fun levelLabel(level: String): String =
    when (level) {
        "easy" -> "쉬움"
        "normal" -> "보통"
        "hard" -> "어려움"
        else -> "쉬움"
    }

/**
 * 학습(홈) 탭 — "학습 시작 허브"(M3-08, 프로토 홈 허브 완전 정합). 맥락 H1 → 인라인 지표 → 히어로 CTA
 * (선택 상황·길이·레벨 메타 + mic/▶ 배지) → 설정 변경 인라인 패널(레벨·길이) → 추천 상황 리스트 →
 * 다른 상황 고르기(상황 시트) → at-limit 보조 고지.
 *
 * 프로토 플로우 정합: 히어로 탭 = **바로 대화 생성**([onStartSession] — 세션 설정 화면 없음), 추천 행 탭 =
 * 선택 갱신 + 즉시 시작(startTopic), 시트 = 선택만 하고 닫힘(pickTopic — 홈 히어로 갱신).
 * [HomeReminderHost] 는 M3-07 리마인더 opt-in 오버레이(스캐폴드 밖 최상위 합성).
 */
@Composable
fun HomeScreen(
    onStartSession: (promptSeed: String, level: String, length: Int) -> Unit,
    onResume: () -> Unit,
    onViewRecords: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    reminderViewModel: HomeReminderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reminderState by reminderViewModel.uiState.collectAsStateWithLifecycle()
    var topicSheetVisible by remember { mutableStateOf(false) }
    var timePickerVisible by remember { mutableStateOf(false) }

    fun startWithCurrentSetup(situation: SelectedSituation?) {
        val level = state.level ?: return // #6: profile.level 미해소 동안 시작 차단(easy 누출 방지).
        val target = situation ?: return
        viewModel.onCtaTap()
        onStartSession(target.promptSeed, level, state.length)
    }

    HomeContent(
        state = state,
        onStartLearning = { startWithCurrentSetup(state.selectedSituation) },
        onResumeContinue = {
            viewModel.onResumeContinue()
            onResume()
        },
        onResumeStartNew = viewModel::onResumeStartNew,
        onViewRecords = onViewRecords,
        onOfflineBlocked = viewModel::onOfflineBlocked,
        modifier = modifier,
        onSituationSelected = { situation ->
            // 프로토 startTopic — 선택 갱신 + 즉시 시작.
            viewModel.selectSituationById(situation.id)
            startWithCurrentSetup(
                SelectedSituation(situation.id, situation.labelKo, situation.promptSeed),
            )
        },
        onRefreshSituations = viewModel::refreshSituations,
        onMoreSituations = { topicSheetVisible = true },
        onSetLevel = viewModel::setLevel,
        onSetLength = viewModel::setLength,
        showReminderBanner = reminderState.showEnabledBanner,
        reminderHour = reminderState.hour,
        reminderMinute = reminderState.minute,
        onDismissReminderBanner = reminderViewModel::dismissEnabledBanner,
        onChangeReminderTime = { timePickerVisible = true },
    )
    if (timePickerVisible) {
        OneClickTimePickerDialog(
            initialHour = reminderState.hour,
            initialMinute = reminderState.minute,
            onConfirm = { h, m ->
                reminderViewModel.setReminderTime(h, m)
                timePickerVisible = false
            },
            onDismiss = { timePickerVisible = false },
        )
    }
    if (topicSheetVisible) {
        TopicSelectSheet(
            selectedTopicId = state.selectedSituation?.topicId,
            onTopicChosen = { promptSeed, topicId ->
                // 프로토 pickTopic/pickCustom — 선택만 갱신하고 닫는다(시작은 홈 히어로가 소유).
                topicSheetVisible = false
                if (topicId != null) {
                    viewModel.selectSituationById(topicId)
                } else {
                    viewModel.selectCustomSituation(promptSeed)
                }
            },
            onDismiss = { topicSheetVisible = false },
        )
    }
    HomeReminderHost()
}

/**
 * 홈 콘텐츠(stateless) — VM/내비 없이 [HomeUiState] 로 렌더하는 스크린샷 seam. 콜백은 기본 no-op
 * (리스트가 비면 추천 섹션 자체가 숨는다).
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
    onSituationSelected: (HomeSituation) -> Unit = {},
    onRefreshSituations: () -> Unit = {},
    onMoreSituations: () -> Unit = {},
    onSetLevel: (String) -> Unit = {},
    onSetLength: (Int) -> Unit = {},
    showReminderBanner: Boolean = false,
    reminderHour: Int = 20,
    reminderMinute: Int = 0,
    onDismissReminderBanner: () -> Unit = {},
    onChangeReminderTime: () -> Unit = {},
) {
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = OceTheme.spacing.xl),
    ) {
        // 리마인더 켜짐 확인 배너(프로토 reminderBanner) — 홈 최상단 in-flow.
        if (showReminderBanner) {
            item(key = "reminder_banner") {
                OneClickReminderEnabledBanner(
                    hour = reminderHour,
                    minute = reminderMinute,
                    onDismiss = onDismissReminderBanner,
                    onChangeTime = onChangeReminderTime,
                    modifier = Modifier.padding(top = OceTheme.spacing.lg),
                )
            }
        }
        // 프로토타입 홈 리듬(비균일): 섹션 사이는 넉넉히(12~24dp), 상황 카드끼리는 촘촘히(8dp).
        item(key = "header") {
            Column(
                modifier = Modifier.padding(top = OceTheme.spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
            ) {
                Text(
                    text = if (state.hasResume) "이어서 말해볼까요?" else "오늘도 영어로 말해볼까요?",
                    style = OceTheme.typography.homeTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text =
                        if (state.hasResume) {
                            "이전 대화를 이어가거나 새로 시작할 수 있어요."
                        } else {
                            "5분만 가볍게 시작해요."
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
                situationLabel = state.selectedSituation?.labelKo,
                level = state.level,
                length = state.length,
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
        } else {
            item(key = "settings_inline") {
                SettingsInline(
                    level = state.level,
                    length = state.length,
                    onSetLevel = onSetLevel,
                    onSetLength = onSetLength,
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
                    onClick = { onSituationSelected(situation) },
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
 * 메인 CTA hero(프로토 정합) — brand.gradient 카드(radius.24, 흰 텍스트) + 우측 배지. 이어하기([resumeTopic]
 * 비-null)면 "이어서 대화하기" + 주제·턴 요약 + ▶ 배지, 아니면 "바로 대화 시작하기" + 선택 상황·길이·레벨
 * 메타 + mic 배지. 오프라인이면 alpha 0.38 + `semantics{ disabled() }` + 인접 헬퍼(비색 신호, H7/P8),
 * onClick 은 계측 전용 no-op 가드.
 */
@Composable
private fun HeroCta(
    online: Boolean,
    resumeTopic: String?,
    resumeTurn: Int,
    resumeTotalTurns: Int,
    situationLabel: String?,
    level: String?,
    length: Int,
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
                    text = if (resumeTopic != null) "이어서 대화하기" else "바로 대화 시작하기",
                    style = OceTheme.typography.homeTitle.copy(fontSize = 23.sp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text =
                        if (resumeTopic != null) {
                            "$resumeTopic · $resumeTurn / ${resumeTotalTurns}턴"
                        } else {
                            listOfNotNull(
                                situationLabel,
                                "${length}턴",
                                level?.let(::levelLabel),
                            ).joinToString(" · ")
                        },
                    style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            HeroBadge(icon = if (resumeTopic != null) OceIcon.PlayArrow else OceIcon.Mic)
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

/** 히어로 CTA 우측 배지 — 반투명 흰 사각 + glyph(프로토 heroIcon: mic/play_arrow). */
@Composable
private fun HeroBadge(icon: OceIcon) {
    Box(
        modifier =
            Modifier
                .size(56.dp)
                .clip(OceTheme.shapes.radius18)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = HERO_BADGE_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        OneClickIcon(
            icon = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * 설정 변경 인라인(프로토 settingsOpenHome) — "⚙ 설정 변경 · 쉬움 · 5턴 ⌄" 행 탭 시 홈 안에서 레벨·길이
 * 세그먼트 패널이 펼쳐진다(별도 화면 없음). [level] null(=profile.level 미해소, #6) 동안은 요약을 로딩
 * 문구로 대체하고 펼침을 막는다.
 */
@Composable
private fun SettingsInline(
    level: String?,
    length: Int,
    onSetLevel: (String) -> Unit,
    onSetLength: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronAngle by animateFloatAsState(if (expanded) 180f else 0f, label = "settingsChevron")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md)) {
        Row(
            modifier = Modifier.clickable(enabled = level != null) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
        ) {
            OneClickIcon(
                icon = OceIcon.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = OceIconSize.FeedbackInline,
            )
            Text(
                text =
                    level?.let { "설정 변경 · ${levelLabel(it)} · ${length}턴" }
                        ?: "설정 변경 · 불러오는 중",
                style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OneClickIcon(
                icon = OceIcon.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = OceIconSize.FeedbackInline,
                modifier = Modifier.rotate(chevronAngle),
            )
        }
        AnimatedVisibility(
            visible = expanded && level != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            OneClickCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(OceTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.lg),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
                        SettingLabel("레벨")
                        OneClickSegmentedControl(
                            options = LEVEL_OPTIONS,
                            selected = level ?: LEVEL_OPTIONS.first(),
                            onSelect = onSetLevel,
                            label = ::levelLabel,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
                        SettingLabel("길이")
                        OneClickSegmentedControl(
                            options = LENGTH_OPTIONS,
                            selected = length,
                            onSelect = onSetLength,
                            label = { "${it}턴" },
                        )
                    }
                }
            }
        }
    }
}

/** 펼친 설정의 소형 섹션 라벨(레벨/길이). */
@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        style = OceTheme.typography.helper.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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

/** "+ 새 대화 시작" 링크(이어하기 CTA 하위 보조) — 프로토 discardSnapshot(스냅샷 폐기, 내비 없음). */
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
                icon = OceIcon.Refresh,
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

/** 추천 상황 1행 — 카드(선행 아이콘 + 라벨 + chevron). 탭 = 선택 갱신 + 즉시 시작(프로토 startTopic). */
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

/** "다른 상황 고르기" — 추천 상황 리스트 하단, 흰 카드 + hairline 보더(프로토 정합). */
@Composable
private fun MoreSituationsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OneClickCard(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = OceTheme.spacing.lg),
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
}

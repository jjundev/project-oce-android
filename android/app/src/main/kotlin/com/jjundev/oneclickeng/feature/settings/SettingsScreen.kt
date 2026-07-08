package com.jjundev.oneclickeng.feature.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jjundev.oneclickeng.R
import com.jjundev.oneclickeng.core.auth.GoogleCredentialProvider
import com.jjundev.oneclickeng.core.settings.TtsQuality
import com.jjundev.oneclickeng.feature.onboarding.google.GoogleLinkViewModel
import com.jjundev.oneclickeng.feature.onboarding.google.LinkUiState
import com.jjundev.oneclickeng.feature.reminder.ReminderPermissionLogic
import com.jjundev.oneclickeng.feature.settings.data.PurgeScope
import com.jjundev.oneclickeng.ui.component.OneClickDangerConfirm
import com.jjundev.oneclickeng.ui.component.OneClickDialog
import com.jjundev.oneclickeng.ui.component.OneClickDialogVariant
import com.jjundev.oneclickeng.ui.component.OneClickSegmentedControl
import com.jjundev.oneclickeng.ui.component.OneClickSlider
import com.jjundev.oneclickeng.ui.component.OneClickSnackbarHost
import com.jjundev.oneclickeng.ui.component.ReminderSettingRow
import com.jjundev.oneclickeng.ui.component.SliderMode
import com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.component.primitive.OneClickInput
import com.jjundev.oneclickeng.ui.component.primitive.OneClickListRow
import com.jjundev.oneclickeng.ui.component.primitive.OneClickSwitch
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.foundation.TabScreenScaffold
import com.jjundev.oneclickeng.ui.theme.OceTheme
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 설정 탭(M3-09). 단일 스크롤 6섹션(프로필·음성·알림·데이터 관리·계정·정보). 위험 동작은 확인 다이얼로그로 마찰
 * 차등한다(초기화·정리=C1 단일 / 계정삭제=C2 2단계). 상태·동작은 [SettingsViewModel] 이 소유하고, 여기선 렌더링과
 * 권한/자격증명 등 Activity 컨텍스트가 필요한 조각 + 오버레이만 다룬다. 스크롤 리스트 본문은 stateless
 * [SettingsContent] 로 위임한다(스크린샷 seam).
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException", "CyclomaticComplexMethod")
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    linkViewModel: GoogleLinkViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val linkState by linkViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showPurgeSheet by rememberSaveable { mutableStateOf(false) }

    // Google 연결 성공 → 계정 분기 갱신(게스트 CTA → 로그아웃/삭제).
    LaunchedEffect(linkState) { if (linkState is LinkUiState.Success) viewModel.refreshAccount() }

    // 결과 메시지 → 스낵바.
    val linkFailedMsg = stringResource(R.string.settings_msg_link_failed)
    LaunchedEffect(linkState) {
        if (linkState is LinkUiState.Error) snackbarHostState.showSnackbar(linkFailedMsg)
    }
    val messageText = state.message?.let { settingsMessageText(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    // 알림 권한(13+) 런처: 허용→활성, 영구거부→시스템 설정 딥링크.
    val notifPermLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.markNotificationPermissionAsked()
            if (granted) {
                viewModel.enableReminder()
            } else {
                val activity = context as? Activity
                val rationale =
                    activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) ?: false
                if (ReminderPermissionLogic.deniedStep(rationale) ==
                    ReminderPermissionLogic.DeniedStep.PERMANENTLY_DENIED
                ) {
                    openAppNotificationSettings(context)
                }
            }
        }
    val onReminderToggle: (Boolean) -> Unit = { wantEnabled ->
        if (wantEnabled) {
            val hasPermission =
                Build.VERSION.SDK_INT < ReminderPermissionLogic.RUNTIME_PERMISSION_SDK ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            when (ReminderPermissionLogic.initialStep(Build.VERSION.SDK_INT, hasPermission)) {
                ReminderPermissionLogic.InitialStep.ENABLE_NO_RUNTIME,
                ReminderPermissionLogic.InitialStep.GRANT_NOW,
                -> viewModel.enableReminder()
                ReminderPermissionLogic.InitialStep.SHOW_PRIMING ->
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            viewModel.disableReminder()
        }
    }

    val onGoogleSave = {
        scope.launch {
            linkViewModel.onCredentialFlowStarted()
            val token =
                try {
                    GoogleCredentialProvider.getGoogleIdToken(context)
                } catch (e: GetCredentialCancellationException) {
                    linkViewModel.onCredentialCancelled()
                    return@launch
                } catch (e: Exception) {
                    linkViewModel.onCredentialFailed(LINK_SESSION_ID)
                    return@launch
                }
            linkViewModel.linkGoogle(token, LINK_SESSION_ID)
        }
        Unit
    }

    Box(modifier = modifier.fillMaxSize()) {
        SettingsContent(
            state = state,
            versionLabel = appVersionLabel(context),
            onNicknameChange = viewModel::onNicknameChange,
            onQualityChange = viewModel::onQualityChange,
            onSpeedChange = viewModel::onSpeedChange,
            onMuteChange = viewModel::onMuteChange,
            onReminderToggle = onReminderToggle,
            onReminderTimeChange = viewModel::onReminderTimeChange,
            onPurgeClick = { showPurgeSheet = true },
            onResetClick = { showResetDialog = true },
            onGoogleSave = { onGoogleSave() },
            onLogoutClick = { showLogoutDialog = true },
            onDeleteClick = { showDeleteDialog = true },
            onRetryMerge = { linkViewModel.retryMerge(LINK_SESSION_ID) },
            onPrivacy = { openUrl(context, SettingsUrls.PRIVACY) },
            onTerms = { openUrl(context, SettingsUrls.TERMS) },
        )

        // ----- 오버레이(다이얼로그·시트·스낵바) -----
        if (showPurgeSheet) {
            CardPurgeSheet(
                onDismiss = { showPurgeSheet = false },
                onSelect = { scopeSel ->
                    showPurgeSheet = false
                    viewModel.selectPurgeScope(scopeSel)
                },
            )
        }
        state.purgeConfirm?.let { confirm ->
            OneClickDialog(
                title = stringResource(R.string.settings_purge_confirm_title),
                body = stringResource(R.string.settings_purge_confirm_body, confirm.count),
                confirmLabel = stringResource(R.string.settings_purge_confirm_action),
                variant = OneClickDialogVariant.Destructive,
                onConfirm = viewModel::confirmPurge,
                onDismiss = viewModel::dismissPurgeConfirm,
            )
        }
        if (showResetDialog) {
            OneClickDialog(
                title = stringResource(R.string.settings_reset_title),
                body = stringResource(R.string.settings_reset_body),
                confirmLabel = stringResource(R.string.settings_reset_action),
                variant = OneClickDialogVariant.Destructive,
                onConfirm = {
                    showResetDialog = false
                    viewModel.resetMetrics()
                },
                onDismiss = { showResetDialog = false },
            )
        }
        if (showLogoutDialog) {
            OneClickDialog(
                title = stringResource(R.string.settings_logout_title),
                body = stringResource(R.string.settings_logout_body),
                confirmLabel = stringResource(R.string.settings_account_logout),
                onConfirm = {
                    showLogoutDialog = false
                    viewModel.logout()
                },
                onDismiss = { showLogoutDialog = false },
            )
        }
        if (showDeleteDialog) {
            OneClickDangerConfirm(
                title = stringResource(R.string.settings_delete_title),
                impactLines =
                    listOf(
                        stringResource(R.string.settings_delete_impact_cards),
                        stringResource(R.string.settings_delete_impact_progress),
                        stringResource(R.string.settings_delete_impact_account),
                    ),
                confirmationWord = stringResource(R.string.settings_delete_confirm_word),
                onConfirm = {
                    showDeleteDialog = false
                    viewModel.deleteAccount()
                },
                onDismiss = { showDeleteDialog = false },
            )
        }

        OneClickSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * 설정 콘텐츠(stateless) — VM/Activity 없이 [SettingsUiState] + 콜백으로 렌더하는 스크린샷 seam. 6섹션 스크롤
 * 리스트. 위험 동작 행은 콜백으로만 노출하고(다이얼로그 트리거는 상태 소유자가 소유), 앱 버전은 이미 해석된
 * [versionLabel] 로 받는다(Context 미의존).
 */
@Suppress("LongMethod", "LongParameterList")
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    versionLabel: String,
    onNicknameChange: (String) -> Unit,
    onQualityChange: (TtsQuality) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onMuteChange: (Boolean) -> Unit,
    onReminderToggle: (Boolean) -> Unit,
    onReminderTimeChange: (Int, Int) -> Unit,
    onPurgeClick: () -> Unit,
    onResetClick: () -> Unit,
    onGoogleSave: () -> Unit,
    onLogoutClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRetryMerge: () -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TabScreenScaffold(titleRes = R.string.tab_settings, modifier = modifier) {
        // ----- 프로필 -----
        sectionHeader(R.string.settings_section_profile)
        item(key = "profile_card") {
            OneClickCard(modifier = Modifier.fillMaxWidth()) {
                ProfileRow(nickname = state.nickname, onNicknameChange = onNicknameChange)
            }
        }

        // ----- 음성 -----
        sectionHeader(R.string.settings_section_voice)
        item(key = "voice_card") {
            val enabled = state.voiceControlsEnabled
            // Resolve labels outside the segmented control's (non-@Composable) label lambda.
            val serverLabel = stringResource(R.string.settings_voice_quality_server)
            val deviceLabel = stringResource(R.string.settings_voice_quality_device)
            var speed by remember(state.speechRate) { mutableStateOf(state.speechRate) }
            OneClickCard(modifier = Modifier.fillMaxWidth()) {
                // 보조 문구는 선택된 음질을 설명(동적). 순서는 프로토 정합 [빠른 발음 | 자연스러운 발음].
                val qualityDesc =
                    if (state.ttsQuality == TtsQuality.DEVICE) {
                        stringResource(R.string.settings_voice_quality_desc_device)
                    } else {
                        stringResource(R.string.settings_voice_quality_desc_server)
                    }
                SettingsRow(
                    icon = OceIcon.GraphicEq,
                    title = stringResource(R.string.settings_voice_quality_label),
                    desc = qualityDesc,
                    below = {
                        OneClickSegmentedControl(
                            options = listOf(TtsQuality.DEVICE, TtsQuality.SERVER),
                            selected = state.ttsQuality,
                            onSelect = { if (enabled) onQualityChange(it) },
                            label = { quality -> if (quality == TtsQuality.SERVER) serverLabel else deviceLabel },
                            modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
                        )
                    },
                )
                SettingsDivider()
                SettingsRow(
                    icon = OceIcon.Speed,
                    title = stringResource(R.string.settings_voice_speed_label),
                    desc = stringResource(R.string.settings_voice_speed_desc),
                    trailing = {
                        Text(
                            text = speedLabel(speed),
                            style = OceTheme.typography.body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    below = {
                        OneClickSlider(
                            value = speed,
                            onValueChange = { if (enabled) speed = it },
                            mode = SliderMode.Continuous(),
                            onValueChangeFinished = { if (enabled) onSpeedChange(speed) },
                            modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
                            showValueLabel = false,
                        )
                        SpeedTicks(modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA))
                    },
                )
                SettingsDivider()
                SettingsRow(
                    icon = OceIcon.VolumeUp,
                    title = stringResource(R.string.settings_voice_mute_label),
                    desc = stringResource(R.string.settings_voice_mute_desc),
                    trailing = { OneClickSwitch(checked = state.ttsMuted, onCheckedChange = onMuteChange) },
                )
            }
        }

        // ----- 알림 -----
        sectionHeader(R.string.settings_section_notify)
        item(key = "notify_card") {
            OneClickCard(modifier = Modifier.fillMaxWidth()) {
                ReminderSettingRow(
                    enabled = state.reminderEnabled,
                    onEnabledChange = onReminderToggle,
                    hour = state.reminderHour,
                    minute = state.reminderMinute,
                    onTimeChange = onReminderTimeChange,
                    leadingIcon = OceIcon.Notifications,
                    supporting = stringResource(R.string.settings_reminder_desc),
                )
            }
        }

        // ----- 데이터 관리 -----
        sectionHeader(R.string.settings_section_data)
        item(key = "data_card") {
            OneClickCard(modifier = Modifier.fillMaxWidth()) {
                OneClickListRow(
                    headline = stringResource(R.string.settings_data_purge),
                    onClick = onPurgeClick,
                )
                SettingsDivider()
                OneClickListRow(
                    headline = stringResource(R.string.settings_data_reset),
                    onClick = onResetClick,
                )
            }
        }

        // ----- 계정 (적응형) -----
        sectionHeader(R.string.settings_section_account)
        item(key = "account_card") {
            OneClickCard(modifier = Modifier.fillMaxWidth()) {
                if (state.isGuest) {
                    OneClickListRow(
                        headline = stringResource(R.string.settings_account_google_save),
                        onClick = onGoogleSave,
                    )
                } else {
                    OneClickListRow(
                        headline = stringResource(R.string.settings_account_logout),
                        onClick = onLogoutClick,
                    )
                    SettingsDivider()
                    OneClickListRow(
                        headline = stringResource(R.string.settings_account_delete),
                        onClick = onDeleteClick,
                    )
                }
                if (state.showRetryMerge) {
                    SettingsDivider()
                    OneClickListRow(
                        headline = stringResource(R.string.settings_account_retry_merge),
                        onClick = onRetryMerge,
                    )
                }
            }
        }

        // ----- 정보 -----
        sectionHeader(R.string.settings_section_info)
        item(key = "info_card") {
            OneClickCard(modifier = Modifier.fillMaxWidth()) {
                SettingValueRow(
                    label = stringResource(R.string.settings_info_version),
                    value = versionLabel,
                )
                SettingsDivider()
                OneClickListRow(
                    headline = stringResource(R.string.settings_info_privacy),
                    onClick = onPrivacy,
                )
                SettingsDivider()
                OneClickListRow(
                    headline = stringResource(R.string.settings_info_terms),
                    onClick = onTerms,
                )
            }
        }
    }
}

/** 카드 정리 범위 선택 바텀시트(30/90일·전체). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardPurgeSheet(
    onDismiss: () -> Unit,
    onSelect: (PurgeScope) -> Unit,
) {
    OneClickBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(OceTheme.spacing.xl)) {
            Text(
                text = stringResource(R.string.settings_purge_sheet_title),
                style = OceTheme.typography.dialogHeader,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = OceTheme.spacing.sm),
            )
            OneClickListRow(
                headline = stringResource(R.string.settings_purge_30),
                onClick = { onSelect(PurgeScope.LAST_30_DAYS) },
            )
            OneClickListRow(
                headline = stringResource(R.string.settings_purge_90),
                onClick = { onSelect(PurgeScope.LAST_90_DAYS) },
            )
            OneClickListRow(
                headline = stringResource(R.string.settings_purge_all),
                onClick = { onSelect(PurgeScope.ALL) },
            )
        }
    }
}

/** 섹션 헤더 item(14sp Bold, text.tertiary). */
private fun LazyListScope.sectionHeader(
    @StringRes titleRes: Int,
) {
    item(key = "header_$titleRes") {
        Text(
            text = stringResource(titleRes),
            style = OceTheme.typography.sectionLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = OceTheme.spacing.lg, bottom = OceTheme.spacing.sm),
        )
    }
}

/**
 * 설정 카드 행(프로토 정합) — 선행 tinted 아이콘 원 + (제목 + 보조 문구) + 우측 [trailing] + 하단 [below] 컨트롤.
 * 음질/속도/음소거처럼 컨트롤이 제목 아래로 오면 [below], 토글/값처럼 우측이면 [trailing] 을 쓴다.
 */
@Composable
private fun SettingsRow(
    icon: OceIcon,
    title: String,
    modifier: Modifier = Modifier,
    desc: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = OceTheme.spacing.lg, vertical = OceTheme.spacing.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
        ) {
            SettingsIcon(icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style =
                        OceTheme.typography.body.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            lineHeightStyle = TrimmedLineHeight,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (desc != null) {
                    Text(
                        text = desc,
                        style = OceTheme.typography.helper.copy(lineHeightStyle = TrimmedLineHeight),
                        color = OceTheme.colors.textTertiary,
                        modifier = Modifier.padding(top = LABEL_TITLE_DESC_GAP),
                    )
                }
            }
            trailing?.invoke()
        }
        if (below != null) {
            Spacer(modifier = Modifier.height(OceTheme.spacing.sm))
            below()
        }
    }
}

/** 설정 행 선행 아이콘 — 프로토 정합 tinted 회색 원(radius.12) 안 24-grid glyph. */
@Composable
private fun SettingsIcon(icon: OceIcon) {
    Box(
        modifier =
            Modifier
                .size(SETTINGS_ICON_BOX)
                .clip(OceTheme.shapes.radius12)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = SETTINGS_ICON_BG_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        OneClickIcon(
            icon = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = OceIconSize.ListDisclosure,
        )
    }
}

/** 카드 내부 행 구분선 — hairline(outline.variant), 선행 아이콘 폭만큼 좌측 인셋(프로토 정합). */
@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = SETTINGS_DIVIDER_INSET),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** 속도 슬라이더 눈금 라벨(0.5x·1.0x·1.5x). */
@Composable
private fun SpeedTicks(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        listOf("0.5x", "1.0x", "1.5x").forEach { tick ->
            Text(
                text = tick,
                style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                color = OceTheme.colors.textTertiary,
            )
        }
    }
}

/** 프로필 행 — 닉네임 표시 + "변경하기" 버튼 → 편집 다이얼로그(1~[NICKNAME_MAX_LEN]자, 빈값 허용). */
@Composable
private fun ProfileRow(
    nickname: String,
    onNicknameChange: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    SettingsRow(
        icon = OceIcon.AccountCircle,
        title = stringResource(R.string.settings_nickname_label),
        desc = nickname.ifBlank { stringResource(R.string.settings_nickname_placeholder) },
        trailing = { ChangeButton(onClick = { editing = true }) },
    )
    if (editing) {
        NicknameEditDialog(
            initial = nickname,
            onConfirm = {
                onNicknameChange(it)
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
}

/** "변경하기" 알약 버튼(hairline 보더 · text.primary). */
@Composable
private fun ChangeButton(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .clip(OceTheme.shapes.pill)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, OceTheme.shapes.pill)
                .clickable(onClick = onClick)
                .padding(horizontal = OceTheme.spacing.md, vertical = OceTheme.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.settings_nickname_change),
            style = OceTheme.typography.tabActive,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 닉네임 편집 다이얼로그 — 텍스트 필드 + 저장/취소. 저장은 [onConfirm] 으로 값 전달(1~[NICKNAME_MAX_LEN]자). */
@Composable
private fun NicknameEditDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = OceTheme.shapes.radius24,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(R.string.settings_nickname_edit_title),
                style = OceTheme.typography.dialogHeader,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            OneClickInput(
                value = text,
                onValueChange = { if (it.length <= NICKNAME_MAX_LEN) text = it },
                label = stringResource(R.string.settings_nickname_label),
                placeholder = stringResource(R.string.settings_nickname_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(
                    text = stringResource(R.string.settings_nickname_edit_save),
                    style = OceTheme.typography.sectionLabel,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.settings_dialog_cancel),
                    style = OceTheme.typography.sectionLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/** 비인터랙티브 라벨 + 우측 값 행(앱 버전). */
@Composable
private fun SettingValueRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = OceTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = OceTheme.typography.body,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun settingsMessageText(message: SettingsMessage): String =
    when (message) {
        is SettingsMessage.CardsPurged -> stringResource(R.string.settings_msg_cards_purged, message.count)
        SettingsMessage.PurgeFailed -> stringResource(R.string.settings_msg_purge_failed)
        SettingsMessage.MetricsResetFailed -> stringResource(R.string.settings_msg_reset_failed)
        SettingsMessage.DeleteFailed -> stringResource(R.string.settings_msg_delete_failed)
        SettingsMessage.LogoutFailed -> stringResource(R.string.settings_msg_logout_failed)
    }

private fun appVersionLabel(context: Context): String =
    runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        "${info.versionName} ($code)"
    }.getOrDefault("")

private fun openUrl(
    context: Context,
    url: String,
) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun openAppNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private const val DISABLED_ALPHA = 0.38f

/** 설정 카드 행 시각 상수(프로토 정합). */
private val SETTINGS_ICON_BOX = 40.dp
private const val SETTINGS_ICON_BG_ALPHA = 0.10f
private val SETTINGS_DIVIDER_INSET = 68.dp
private const val NICKNAME_MAX_LEN = 20

/** 제목↔보조 문구 세로 간격(프로토 실측 2~3dp). lineHeight leading 은 [TrimmedLineHeight] 로 제거 후 명시. */
private val LABEL_TITLE_DESC_GAP = 2.dp

/** 스택된 라벨의 상/하단 lineHeight leading 제거(제목↔설명 간격을 명시값으로 통제). */
private val TrimmedLineHeight =
    LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)

/** 배속 실수 → "1.0x" 라벨(로케일 고정). */
private fun speedLabel(speed: Float): String = String.format(Locale.US, "%.1fx", speed)

/** 설정 경로의 Google 연결 계측 sessionId(온보딩 세션 아님). */
private const val LINK_SESSION_ID = "settings"

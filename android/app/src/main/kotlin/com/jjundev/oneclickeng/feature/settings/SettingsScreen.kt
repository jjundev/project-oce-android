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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.jjundev.oneclickeng.ui.component.primitive.OneClickInput
import com.jjundev.oneclickeng.ui.component.primitive.OneClickListRow
import com.jjundev.oneclickeng.ui.component.primitive.OneClickSwitch
import com.jjundev.oneclickeng.ui.foundation.TabScreenScaffold
import com.jjundev.oneclickeng.ui.theme.OceTheme
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
        item(key = "profile_nickname") {
            OneClickInput(
                value = state.nickname,
                onValueChange = onNicknameChange,
                label = stringResource(R.string.settings_nickname_label),
                placeholder = stringResource(R.string.settings_nickname_placeholder),
                modifier = Modifier.fillMaxWidth().padding(vertical = OceTheme.spacing.sm),
            )
        }

        // ----- 음성 -----
        sectionHeader(R.string.settings_section_voice)
        item(key = "voice_quality") {
            val enabled = state.voiceControlsEnabled
            // Resolve labels outside the segmented control's (non-@Composable) label lambda.
            val serverLabel = stringResource(R.string.settings_voice_quality_server)
            val deviceLabel = stringResource(R.string.settings_voice_quality_device)
            Column(modifier = Modifier.padding(vertical = OceTheme.spacing.sm)) {
                Text(
                    text = stringResource(R.string.settings_voice_quality_label),
                    style = OceTheme.typography.body,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = OceTheme.spacing.sm),
                )
                OneClickSegmentedControl(
                    options = listOf(TtsQuality.SERVER, TtsQuality.DEVICE),
                    selected = state.ttsQuality,
                    onSelect = { if (enabled) onQualityChange(it) },
                    label = { quality -> if (quality == TtsQuality.SERVER) serverLabel else deviceLabel },
                    modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
                )
            }
        }
        item(key = "voice_speed") {
            val enabled = state.voiceControlsEnabled
            var speed by remember(state.speechRate) { mutableStateOf(state.speechRate) }
            Column(modifier = Modifier.padding(vertical = OceTheme.spacing.sm)) {
                Text(
                    text = stringResource(R.string.settings_voice_speed_label),
                    style = OceTheme.typography.body,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                OneClickSlider(
                    value = speed,
                    onValueChange = { if (enabled) speed = it },
                    mode = SliderMode.Continuous(),
                    onValueChangeFinished = { if (enabled) onSpeedChange(speed) },
                    modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
                )
            }
        }
        item(key = "voice_mute") {
            SettingToggleRow(
                label = stringResource(R.string.settings_voice_mute_label),
                checked = state.ttsMuted,
                onCheckedChange = onMuteChange,
            )
        }

        // ----- 알림 -----
        sectionHeader(R.string.settings_section_notify)
        item(key = "notify_reminder") {
            ReminderSettingRow(
                enabled = state.reminderEnabled,
                onEnabledChange = onReminderToggle,
                hour = state.reminderHour,
                minute = state.reminderMinute,
                onTimeChange = onReminderTimeChange,
            )
        }

        // ----- 데이터 관리 -----
        sectionHeader(R.string.settings_section_data)
        item(key = "data_purge") {
            OneClickListRow(
                headline = stringResource(R.string.settings_data_purge),
                onClick = onPurgeClick,
            )
        }
        item(key = "data_reset") {
            OneClickListRow(
                headline = stringResource(R.string.settings_data_reset),
                onClick = onResetClick,
            )
        }

        // ----- 계정 (적응형) -----
        sectionHeader(R.string.settings_section_account)
        if (state.isGuest) {
            item(key = "account_google_save") {
                OneClickListRow(
                    headline = stringResource(R.string.settings_account_google_save),
                    onClick = onGoogleSave,
                )
            }
        } else {
            item(key = "account_logout") {
                OneClickListRow(
                    headline = stringResource(R.string.settings_account_logout),
                    onClick = onLogoutClick,
                )
            }
            item(key = "account_delete") {
                OneClickListRow(
                    headline = stringResource(R.string.settings_account_delete),
                    onClick = onDeleteClick,
                )
            }
        }
        if (state.showRetryMerge) {
            item(key = "account_retry_merge") {
                OneClickListRow(
                    headline = stringResource(R.string.settings_account_retry_merge),
                    onClick = onRetryMerge,
                )
            }
        }

        // ----- 정보 -----
        sectionHeader(R.string.settings_section_info)
        item(key = "info_version") {
            SettingValueRow(
                label = stringResource(R.string.settings_info_version),
                value = versionLabel,
            )
        }
        item(key = "info_privacy") {
            OneClickListRow(
                headline = stringResource(R.string.settings_info_privacy),
                onClick = onPrivacy,
            )
        }
        item(key = "info_terms") {
            OneClickListRow(
                headline = stringResource(R.string.settings_info_terms),
                onClick = onTerms,
            )
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
            modifier = Modifier.padding(top = OceTheme.spacing.lg, bottom = OceTheme.spacing.xs),
        )
    }
}

/** 라벨 + 우측 토글 행(음소거 등). */
@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = OceTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = OceTheme.typography.body,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        OneClickSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
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

/** 설정 경로의 Google 연결 계측 sessionId(온보딩 세션 아님). */
private const val LINK_SESSION_ID = "settings"

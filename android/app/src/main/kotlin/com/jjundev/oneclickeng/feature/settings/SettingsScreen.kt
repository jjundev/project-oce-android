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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jjundev.oneclickeng.R
import com.jjundev.oneclickeng.core.auth.GoogleCredentialProvider
import com.jjundev.oneclickeng.core.settings.TtsQuality
import com.jjundev.oneclickeng.feature.onboarding.google.GoogleLinkViewModel
import com.jjundev.oneclickeng.feature.onboarding.google.LinkUiState
import com.jjundev.oneclickeng.feature.reminder.ReminderPermissionLogic
import com.jjundev.oneclickeng.feature.settings.data.PurgeScope
import com.jjundev.oneclickeng.ui.component.OneClickSegmentedControl
import com.jjundev.oneclickeng.ui.component.OneClickSlider
import com.jjundev.oneclickeng.ui.component.OneClickSnackbarHost
import com.jjundev.oneclickeng.ui.component.SliderMode
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.component.primitive.OneClickSwitch
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OceBottomNavDefaults
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.foundation.PinnedTabHeader
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.foundation.rememberScreenEntrance
import com.jjundev.oneclickeng.ui.foundation.staggerReveal
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
    var showTimeSheet by rememberSaveable { mutableStateOf(false) }

    // 시스템 알림 on/off 는 화면 재개마다 재확인(설정 앱에서 끄고 돌아온 경우 반영).
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationsBlocked = !notificationsEnabled

    // Google 연결 성공 → 계정 분기 갱신(게스트 CTA → 로그아웃/삭제).
    LaunchedEffect(linkState) { if (linkState is LinkUiState.Success) viewModel.refreshAccount() }

    // 결과 메시지 → 스낵바.
    val linkFailedMsg = stringResource(R.string.settings_msg_link_failed)
    LaunchedEffect(linkState) {
        if (linkState is LinkUiState.Error) snackbarHostState.showSnackbar(linkFailedMsg)
    }
    val messageText = state.message?.let { settingsMessageText(it) }
    SettingsMessageEffect(
        messageText = messageText,
        showSnackbar = snackbarHostState::showSnackbar,
        consumeMessage = viewModel::consumeMessage,
    )

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
            notificationsBlocked = notificationsBlocked,
            onNicknameChange = viewModel::onNicknameChange,
            onQualityChange = viewModel::onQualityChange,
            onSpeedChange = viewModel::onSpeedChange,
            onMuteChange = viewModel::onMuteChange,
            onReminderToggle = onReminderToggle,
            onReminderTimeClick = { showTimeSheet = true },
            onOpenNotificationSettings = { openAppNotificationSettings(context) },
            onPurgeClick = {
                viewModel.loadPurgeCounts()
                showPurgeSheet = true
            },
            onResetClick = { showResetDialog = true },
            onGoogleSave = { onGoogleSave() },
            onLogoutClick = { showLogoutDialog = true },
            onDeleteClick = { showDeleteDialog = true },
            onRetryMerge = { linkViewModel.retryMerge(LINK_SESSION_ID) },
            onPrivacy = { openUrl(context, SettingsUrls.PRIVACY) },
            onTerms = { openUrl(context, SettingsUrls.TERMS) },
            reduceMotion = rememberReduceMotion(),
        )

        // ----- 오버레이(다이얼로그·시트·스낵바) -----
        if (showPurgeSheet) {
            CardPurgeSheet(
                counts = state.purgeCounts,
                onDismiss = { showPurgeSheet = false },
                onSelect = { scopeSel ->
                    showPurgeSheet = false
                    viewModel.selectPurgeScope(scopeSel)
                },
            )
        }
        if (showTimeSheet) {
            ReminderTimeSheet(
                initialHour = state.reminderHour,
                initialMinute = state.reminderMinute,
                onConfirm = { h, m ->
                    viewModel.onReminderTimeChange(h, m)
                    showTimeSheet = false
                },
                onDismiss = { showTimeSheet = false },
            )
        }
        state.purgeConfirm?.let { confirm ->
            val title =
                if (confirm.scope == PurgeScope.ALL) {
                    stringResource(R.string.settings_purge_confirm_title_all, confirm.count)
                } else {
                    stringResource(R.string.settings_purge_confirm_title_scoped, confirm.count)
                }
            SettingsConfirmDialog(
                title = title,
                body = stringResource(R.string.settings_purge_confirm_body_short),
                confirmLabel = stringResource(R.string.settings_purge_confirm_action),
                confirmColor = MaterialTheme.colorScheme.error,
                onConfirm = viewModel::confirmPurge,
                onDismiss = viewModel::dismissPurgeConfirm,
            )
        }
        if (showResetDialog) {
            SettingsConfirmDialog(
                title = stringResource(R.string.settings_reset_title),
                body = stringResource(R.string.settings_reset_body),
                confirmLabel = stringResource(R.string.settings_reset_action),
                confirmColor = MaterialTheme.colorScheme.error,
                onConfirm = { showResetDialog = false; viewModel.resetMetrics() },
                onDismiss = { showResetDialog = false },
            )
        }
        if (showLogoutDialog) {
            SettingsConfirmDialog(
                title = stringResource(R.string.settings_logout_title),
                body = stringResource(R.string.settings_logout_body),
                confirmLabel = stringResource(R.string.settings_account_logout),
                confirmColor = MaterialTheme.colorScheme.primary,
                onConfirm = { showLogoutDialog = false; viewModel.logout() },
                onDismiss = { showLogoutDialog = false },
            )
        }
        if (showDeleteDialog) {
            DeleteAccountDialog(
                onConfirm = { showDeleteDialog = false; viewModel.deleteAccount() },
                onDismiss = { showDeleteDialog = false },
            )
        }

        OneClickSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
            bottomInset = OceBottomNavDefaults.overlayContentBottomPadding,
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
    notificationsBlocked: Boolean,
    onNicknameChange: (String) -> Unit,
    onQualityChange: (TtsQuality) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onMuteChange: (Boolean) -> Unit,
    onReminderToggle: (Boolean) -> Unit,
    onReminderTimeClick: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onPurgeClick: () -> Unit,
    onResetClick: () -> Unit,
    onGoogleSave: () -> Unit,
    onLogoutClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRetryMerge: () -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    Column(modifier = modifier.fillMaxSize()) {
        val entrance = rememberScreenEntrance(reduceMotion)
        PinnedTabHeader(titleRes = R.string.tab_settings)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding =
                PaddingValues(
                    top = 8.dp,
                    bottom = OceBottomNavDefaults.overlayContentBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            // 프로토 order:-1 = 게스트는 계정 카드(Google 저장)를 최상단으로 승격. LazyListScope 엔 CSS order 가
            // 없으므로 방출 위치를 분기해 동일 순서를 만든다(게스트=계정 먼저 / 회원=데이터 다음).
            if (state.isGuest) {
                item(key = "account") {
                    AccountSection(
                        state = state,
                        onGoogleSave = onGoogleSave,
                        onRetryMerge = onRetryMerge,
                        onLogoutClick = onLogoutClick,
                        onDeleteClick = onDeleteClick,
                        modifier = Modifier.staggerReveal(0, entrance),
                    )
                }
            }

            // ----- 프로필 -----
            item(key = "profile") {
                SettingsSection(
                    titleRes = R.string.settings_section_profile,
                    modifier = Modifier.staggerReveal(1, entrance),
                ) {
                    ProfileRow(nickname = state.nickname, onNicknameChange = onNicknameChange)
                }
            }

            // ----- 음성 -----
            item(key = "voice") {
                SettingsSection(
                    titleRes = R.string.settings_section_voice,
                    modifier = Modifier.staggerReveal(2, entrance),
                ) {
                    VoiceCardBody(
                        state = state,
                        onQualityChange = onQualityChange,
                        onSpeedChange = onSpeedChange,
                        onMuteChange = onMuteChange,
                    )
                }
            }

            // ----- 알림 -----
            item(key = "notify") {
                SettingsSection(
                    titleRes = R.string.settings_section_notify,
                    modifier = Modifier.staggerReveal(3, entrance),
                ) {
                    SettingsNavRow(
                        icon = OceIcon.Notifications,
                        title = stringResource(R.string.settings_reminder_title),
                        desc = stringResource(R.string.settings_reminder_desc),
                        trailing = {
                            OneClickSwitch(checked = state.reminderEnabled, onCheckedChange = onReminderToggle)
                        },
                    )
                    if (notificationsBlocked) {
                        SettingsCardDivider()
                        NotificationBlockedBanner(onOpenSettings = onOpenNotificationSettings)
                    }
                    if (state.reminderEnabled) {
                        SettingsCardDivider()
                        SettingsNavRow(
                            icon = OceIcon.Schedule,
                            title = stringResource(R.string.settings_reminder_time_label),
                            desc = stringResource(R.string.settings_reminder_time_desc),
                            onClick = onReminderTimeClick,
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = reminderTimeLabel(state.reminderHour, state.reminderMinute),
                                        style = OceTheme.typography.sectionLabel.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(OceTheme.spacing.xs))
                                    OneClickIcon(
                                        OceIcon.ChevronRight,
                                        null,
                                        tint = OceTheme.colors.textTertiary,
                                        size = OceIconSize.ListDisclosure,
                                    )
                                }
                            },
                        )
                    }
                }
            }

            // ----- 데이터 관리 -----
            item(key = "data") {
                SettingsSection(
                    titleRes = R.string.settings_section_data,
                    modifier = Modifier.staggerReveal(4, entrance),
                ) {
                    SettingsNavRow(
                        icon = OceIcon.CleaningServices,
                        title = stringResource(R.string.settings_data_purge),
                        desc = stringResource(R.string.settings_data_purge_desc),
                        onClick = onPurgeClick,
                    )
                    SettingsCardDivider()
                    SettingsNavRow(
                        icon = OceIcon.RestartAlt,
                        title = stringResource(R.string.settings_data_reset),
                        desc = stringResource(R.string.settings_data_reset_desc),
                        onClick = onResetClick,
                    )
                }
            }

            // ----- 계정 (회원은 데이터 다음 정상 위치; 게스트는 위에서 이미 최상단 승격) -----
            if (!state.isGuest) {
                item(key = "account") {
                    AccountSection(
                        state = state,
                        onGoogleSave = onGoogleSave,
                        onRetryMerge = onRetryMerge,
                        onLogoutClick = onLogoutClick,
                        onDeleteClick = onDeleteClick,
                        modifier = Modifier.staggerReveal(5, entrance),
                    )
                }
            }

            // ----- 정보 -----
            item(key = "info") {
                SettingsSection(
                    titleRes = R.string.settings_section_info,
                    modifier = Modifier.staggerReveal(6, entrance),
                ) {
                    SettingsNavRow(
                        icon = OceIcon.Info,
                        title = stringResource(R.string.settings_info_version),
                        onClick = null,
                        trailing = {
                            Text(
                                text = versionLabel,
                                style = OceTheme.typography.helper.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                ),
                                color = OceTheme.colors.textTertiary,
                            )
                        },
                    )
                    SettingsCardDivider()
                    SettingsNavRow(
                        icon = OceIcon.Shield,
                        title = stringResource(R.string.settings_info_privacy),
                        onClick = onPrivacy,
                        trailing = {
                            OneClickIcon(
                                OceIcon.OpenInNew,
                                null,
                                tint = OceTheme.colors.textTertiary,
                                size = OceIconSize.ListDisclosure,
                            )
                        },
                    )
                    SettingsCardDivider()
                    SettingsNavRow(
                        icon = OceIcon.Description,
                        title = stringResource(R.string.settings_info_terms),
                        onClick = onTerms,
                        trailing = {
                            OneClickIcon(
                                OceIcon.OpenInNew,
                                null,
                                tint = OceTheme.colors.textTertiary,
                                size = OceIconSize.ListDisclosure,
                            )
                        },
                    )
                }
            }
        }
    }
}

/** 섹션 = 헤더(10dp gap) + radius24 카드. */
@Composable
private fun SettingsSection(
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsSectionHeader(title = stringResource(titleRes))
        OneClickCard(modifier = Modifier.fillMaxWidth(), shape = OceTheme.shapes.radius24, content = content)
    }
}

/** 프로필 행 — Person 아이콘 + 닉네임 + brand "변경하기" 버튼(프로토 정합). */
@Composable
private fun ProfileRow(
    nickname: String,
    onNicknameChange: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    SettingsNavRow(
        icon = OceIcon.Person,
        title = stringResource(R.string.settings_nickname_label),
        desc = nickname.ifBlank { stringResource(R.string.settings_nickname_placeholder) },
        onClick = null,
        trailing = { ChangeButton(onClick = { editing = true }) },
    )
    if (editing) {
        NicknameEditDialog(
            initial = nickname,
            onConfirm = { onNicknameChange(it); editing = false },
            onDismiss = { editing = false },
        )
    }
}

/** "변경하기" 알약 버튼 — hairline 보더 · brand.primary 텍스트(프로토 정합). */
@Composable
private fun ChangeButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(OceTheme.shapes.pill)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, OceTheme.shapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = OceTheme.spacing.lg, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.settings_nickname_change),
            style = OceTheme.typography.tabActive,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 음성 카드 본문 — 음질 세그먼트 / 속도 슬라이더 / 음소거 스위치(기존 SettingsRow 재사용, 아이콘박스는 solid). */
@Composable
private fun ColumnScope.VoiceCardBody(
    state: SettingsUiState,
    onQualityChange: (TtsQuality) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onMuteChange: (Boolean) -> Unit,
) {
    val enabled = state.voiceControlsEnabled
    val serverLabel = stringResource(R.string.settings_voice_quality_server)
    val deviceLabel = stringResource(R.string.settings_voice_quality_device)
    var speed by remember(state.speechRate) { mutableStateOf(state.speechRate) }
    val qualityDesc =
        if (state.ttsQuality == TtsQuality.DEVICE) stringResource(R.string.settings_voice_quality_desc_device)
        else stringResource(R.string.settings_voice_quality_desc_server)
    SettingsRow(
        icon = OceIcon.GraphicEq,
        title = stringResource(R.string.settings_voice_quality_label),
        desc = qualityDesc,
        below = {
            OneClickSegmentedControl(
                options = listOf(TtsQuality.DEVICE, TtsQuality.SERVER),
                selected = state.ttsQuality,
                onSelect = { if (enabled) onQualityChange(it) },
                label = { q -> if (q == TtsQuality.SERVER) serverLabel else deviceLabel },
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

/** 계정 섹션(적응형) — 헤더 + 배지, 게스트는 Google 저장(+선택 이관 재시도) tinted 행, 회원은 로그아웃/삭제 행. */
@Composable
private fun AccountSection(
    state: SettingsUiState,
    onGoogleSave: () -> Unit,
    onRetryMerge: () -> Unit,
    onLogoutClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
            modifier = Modifier.padding(start = 4.dp),
        ) {
            SettingsSectionHeader(
                title = stringResource(R.string.settings_section_account),
                modifier = Modifier.padding(start = 0.dp),
            )
            SettingsAccountBadge(isGuest = state.isGuest)
        }
        OneClickCard(modifier = Modifier.fillMaxWidth(), shape = OceTheme.shapes.radius24) {
            if (state.isGuest) {
                SettingsNavRow(
                    icon = OceIcon.CloudSync,
                    title = stringResource(R.string.settings_account_google_save),
                    desc = stringResource(R.string.settings_account_google_save_desc),
                    titleColor = MaterialTheme.colorScheme.primary,
                    iconTint = MaterialTheme.colorScheme.primary,
                    iconBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    onClick = onGoogleSave,
                )
                if (state.showRetryMerge) {
                    SettingsCardDivider()
                    SettingsNavRow(
                        icon = OceIcon.SyncProblem,
                        title = stringResource(R.string.settings_account_retry_merge),
                        desc = stringResource(R.string.settings_account_retry_merge_desc),
                        iconTint = OceTheme.colors.gameSaveGold,
                        iconBg = OceTheme.colors.gameSaveGold.copy(alpha = 0.12f),
                        onClick = onRetryMerge,
                    )
                }
            } else {
                SettingsNavRow(
                    icon = OceIcon.Logout,
                    title = stringResource(R.string.settings_account_logout),
                    onClick = onLogoutClick,
                )
                SettingsCardDivider()
                SettingsNavRow(
                    icon = OceIcon.DeleteForever,
                    title = stringResource(R.string.settings_account_delete),
                    desc = stringResource(R.string.settings_account_delete_desc),
                    titleColor = MaterialTheme.colorScheme.error,
                    iconTint = MaterialTheme.colorScheme.error,
                    iconBg = OceTheme.colors.feedbackCorrectBg,
                    onClick = onDeleteClick,
                )
            }
        }
        if (state.isGuest) {
            Text(
                text = stringResource(R.string.settings_account_guest_footnote),
                style = OceTheme.typography.helper.copy(fontSize = 12.sp),
                color = OceTheme.colors.textTertiary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * 설정 카드 행(레거시, 음성 카드 전용) — 선행 tinted 아이콘 원 + (제목 + 보조 문구) + 우측 [trailing] + 하단
 * [below] 컨트롤. 음질/속도/음소거처럼 컨트롤이 제목 아래로 오면 [below], 토글/값처럼 우측이면 [trailing] 을 쓴다.
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

/** 설정 행 선행 아이콘 — 프로토 정합 solid 회색 원(radius.12) 안 24-grid glyph. */
@Composable
private fun SettingsIcon(icon: OceIcon) {
    Box(
        modifier =
            Modifier
                .size(SETTINGS_ICON_BOX)
                .clip(OceTheme.shapes.radius12)
                .background(MaterialTheme.colorScheme.background),
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

@Composable
private fun settingsMessageText(message: SettingsMessage): String =
    when (message) {
        is SettingsMessage.CardsPurged -> stringResource(R.string.settings_msg_cards_purged, message.count)
        SettingsMessage.NoCardsToPurge -> stringResource(R.string.settings_msg_no_cards_to_purge)
        SettingsMessage.PurgeFailed -> stringResource(R.string.settings_msg_purge_failed)
        SettingsMessage.MetricsResetFailed -> stringResource(R.string.settings_msg_reset_failed)
        SettingsMessage.DeleteFailed -> stringResource(R.string.settings_msg_delete_failed)
        SettingsMessage.LogoutFailed -> stringResource(R.string.settings_msg_logout_failed)
    }

@Composable
internal fun SettingsMessageEffect(
    messageText: String?,
    showSnackbar: suspend (String) -> SnackbarResult,
    consumeMessage: () -> Unit,
) {
    LaunchedEffect(messageText) {
        if (messageText != null) {
            try {
                showSnackbar(messageText)
            } finally {
                consumeMessage()
            }
        }
    }
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
private val SETTINGS_DIVIDER_INSET = 68.dp

/** 제목↔보조 문구 세로 간격(프로토 실측 2~3dp). lineHeight leading 은 [TrimmedLineHeight] 로 제거 후 명시. */
private val LABEL_TITLE_DESC_GAP = 2.dp

/** 스택된 라벨의 상/하단 lineHeight leading 제거(제목↔설명 간격을 명시값으로 통제). */
private val TrimmedLineHeight =
    LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)

/** 배속 실수 → "1.0x" 라벨(로케일 고정). */
private fun speedLabel(speed: Float): String = String.format(Locale.US, "%.1fx", speed)

/** 설정 경로의 Google 연결 계측 sessionId(온보딩 세션 아님). */
private const val LINK_SESSION_ID = "settings"

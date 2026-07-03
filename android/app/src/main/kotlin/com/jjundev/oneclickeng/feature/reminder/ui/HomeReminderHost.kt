package com.jjundev.oneclickeng.feature.reminder.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jjundev.oneclickeng.feature.reminder.ReminderPermissionLogic
import com.jjundev.oneclickeng.ui.component.OneClickPermissionDeniedHint
import com.jjundev.oneclickeng.ui.component.OneClickPermissionPrimingSheet
import com.jjundev.oneclickeng.ui.component.OneClickReminderOptInSheet
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import kotlinx.coroutines.launch

/**
 * 홈에 얹는 리마인더 opt-in 오버레이(notification-reminder.md §2·§3, 결정 #9). 홈 진입 시 1회 평가해
 * [OneClickReminderOptInSheet] 를 띄우고, `[알림 받기]` 시 POST_NOTIFICATIONS 권한 상태기계(§3)를 태운다.
 * 저장·계측은 [HomeReminderViewModel] 이, 시스템 권한 API(런처·rationale)는 이 컴포저블이 소유한다.
 *
 * M0-06 무상태 셸([OneClickReminderOptInSheet]/[OneClickPermissionPrimingSheet]/
 * [OneClickPermissionDeniedHint])을 재사용하고 상태·콜백만 배선한다(신규 제작 아님).
 */
@Composable
fun HomeReminderHost(viewModel: HomeReminderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showPriming by remember { mutableStateOf(false) }
    var deniedStep by remember { mutableStateOf<ReminderPermissionLogic.DeniedStep?>(null) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                deniedStep = null
                viewModel.enableReminder()
            } else {
                viewModel.markPermissionAsked()
                val activity = context.findActivity()
                val rationale =
                    activity?.let {
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            it,
                            Manifest.permission.POST_NOTIFICATIONS,
                        )
                    } ?: false
                deniedStep = ReminderPermissionLogic.deniedStep(rationale)
            }
        }

    // 홈 진입 1회 평가(결정 #9). !resolved 상태 게이트라 재진입은 안전한 no-op.
    LaunchedEffect(Unit) { viewModel.evaluatePrompt() }

    fun startEnableFlow() {
        when (ReminderPermissionLogic.initialStep(Build.VERSION.SDK_INT, hasPostPermission(context))) {
            ReminderPermissionLogic.InitialStep.ENABLE_NO_RUNTIME,
            ReminderPermissionLogic.InitialStep.GRANT_NOW,
            -> viewModel.enableReminder()
            ReminderPermissionLogic.InitialStep.SHOW_PRIMING -> showPriming = true
        }
    }

    if (state.showOptInSheet) {
        OneClickReminderOptInSheet(
            onOptIn = {
                viewModel.acceptOptIn()
                startEnableFlow()
            },
            onLater = { viewModel.dismissOptIn() },
        )
    }

    if (showPriming) {
        OneClickPermissionPrimingSheet(
            icon = OceIcon.Schedule,
            rationale = "정한 시각에 오늘 학습을 살짝 알려드려요. 언제든 설정에서 끌 수 있어요.",
            onRequest = {
                showPriming = false
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onLater = { showPriming = false },
            title = "알림을 허용할까요?",
        )
    }

    deniedStep?.let { step ->
        val message =
            when (step) {
                ReminderPermissionLogic.DeniedStep.SHOW_RATIONALE_INLINE ->
                    "알림이 꺼져 있어요. 다시 시도하면 리마인더를 받을 수 있어요."
                ReminderPermissionLogic.DeniedStep.PERMANENTLY_DENIED ->
                    "알림이 꺼져 있어요. 설정에서 켜면 리마인더를 받을 수 있어요."
            }
        OneClickPermissionDeniedHint(message = message)
    }
}

private fun hasPostPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

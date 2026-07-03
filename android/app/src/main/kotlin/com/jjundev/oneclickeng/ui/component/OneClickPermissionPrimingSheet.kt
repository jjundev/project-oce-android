package com.jjundev.oneclickeng.ui.component

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * C13 권한 요청 프라이밍 시트 = [OneClickBottomSheet] 재사용. 정본: 02-shared-components.md:105 ·
 * exception-states.md #21.
 *
 * 아이콘 + 설명(왜 필요한지) + 2버튼(요청/나중에)만 렌더한다. **실제 권한 요청 API 호출·
 * `shouldShowRequestPermissionRationale`·영구거부 1비트 저장은 소비처(마이크→대화학습, 알림→M3-07)가
 * 소유**한다 — 이 컴포넌트는 프라이밍 UI 와 콜백만 제공하는 무상태 시트다.
 *
 * @param icon 맥락 아이콘(마이크=[OceIcon.Mic], 알림 등은 소비처가 지정).
 * @param rationale 왜 이 권한이 필요한지 설명.
 * @param onRequest "허용하기" — 소비처가 실제 시스템 권한 요청을 띄운다.
 * @param onLater "나중에" — 시트만 닫는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneClickPermissionPrimingSheet(
    icon: OceIcon,
    rationale: String,
    onRequest: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "권한이 필요해요",
    requestLabel: String = "허용하기",
    laterLabel: String = "나중에",
) {
    val headerFocus = remember { FocusRequester() }
    OneClickBottomSheet(onDismissRequest = onLater, modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(OceTheme.spacing.sheetPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
        ) {
            OneClickIcon(
                icon = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                size = OceIconSize.EmptyState,
            )
            Text(
                text = title,
                style = OceTheme.typography.dialogHeader,
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .focusRequester(headerFocus)
                        .focusable(),
            )
            Text(
                text = rationale,
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text(text = requestLabel, style = OceTheme.typography.sectionLabel)
            }
            TextButton(onClick = onLater) {
                Text(
                    text = laterLabel,
                    style = OceTheme.typography.sectionLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // A5: 진입 시 헤더로 포커스 이동(스크린리더가 시트 콘텐츠부터 announce). 닫힘 복귀는 M3 모달 스코프.
    LaunchedEffect(Unit) { headerFocus.requestFocus() }
}

/**
 * C13 영구거부 인라인 힌트 = 1회성 안내 + 앱 설정 딥링크. 정본: 02-shared-components.md:105.
 *
 * 영구거부 감지·"1회만 노출"·넛지 반복 금지 정책은 소비처(로컬 1비트)가 소유하고, 여기서는 문구 +
 * `설정 열기`(딥링크)만 렌더한다.
 */
@Composable
fun OneClickPermissionDeniedHint(
    message: String,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = rememberOpenAppSettingsAction(),
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
    ) {
        Text(
            text = message,
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenSettings) {
            Text(
                text = "설정 열기",
                style = OceTheme.typography.sectionLabel,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * 앱 설정 화면 딥링크 액션. `ACTION_APPLICATION_DETAILS_SETTINGS` 로 이 앱의 설정을 연다(영구거부 시
 * 사용자가 권한을 직접 켜는 경로). 컴포저블 밖에서 부수효과를 실행하므로 [remember] 로 안정화한다.
 */
@Composable
fun rememberOpenAppSettingsAction(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            val intent =
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun OneClickPermissionDeniedHintPreview() {
    OceTheme {
        OneClickPermissionDeniedHint(
            message = "마이크 권한이 꺼져 있어요. 설정에서 켜면 말하기를 쓸 수 있어요.",
            onOpenSettings = {},
        )
    }
}

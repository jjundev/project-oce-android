package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet
import com.jjundev.oneclickeng.ui.component.primitive.OneClickSwitch
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** C19 리마인더 기본 시각(05-open-decisions P11). */
const val REMINDER_DEFAULT_HOUR = 20
const val REMINDER_DEFAULT_MINUTE = 0

/** 설정 카드 안 리마인더 행 선행 아이콘 시각 상수(SettingsScreen 정합). */
private val REMINDER_ICON_BOX = 40.dp
private const val REMINDER_ICON_BG_ALPHA = 0.10f
private val REMINDER_ROW_INSET = 68.dp

/** 제목↔보조 문구 세로 간격(프로토 실측 2~3dp) + lineHeight leading 제거(SettingsScreen 정합). */
private val REMINDER_LABEL_GAP = 2.dp
private val ReminderTrimmedLineHeight =
    LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)

/**
 * C19 리마인더 opt-in 시트 = [OneClickBottomSheet] 재사용. 정본: 02-shared-components.md:135 ·
 * notification-reminder.md §2.
 *
 * 2번째 세션 완주 후 홈에서 **1회** 노출(노출 정책은 소비처 소유). 아이콘 + 카피 + `알림 받기`(primary)/
 * `다음에`(ghost). opt-in 시 실제 권한 priming 은 C13 연계로 소비처가 잇는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneClickReminderOptInSheet(
    onOptIn: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
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
                icon = OceIcon.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                size = OceIconSize.EmptyState,
            )
            Text(
                text = "매일 학습 알림을 받을까요?",
                style = OceTheme.typography.dialogHeader,
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .focusRequester(headerFocus)
                        .focusable(),
            )
            Text(
                text = "정한 시각에 살짝 알려드릴게요. 언제든 설정에서 끌 수 있어요.",
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onOptIn,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text(text = "알림 받기", style = OceTheme.typography.sectionLabel)
            }
            TextButton(onClick = onLater) {
                Text(
                    text = "다음에",
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
 * C19 리마인더 설정 행 = [OneClickSwitch] + 조건부 [OneClickTimePickerDialog](C10). 정본:
 * 02-shared-components.md:135 · notification-reminder.md §6.
 *
 * 토글 ON일 때만 시각 행을 노출한다. 기본 [REMINDER_DEFAULT_HOUR]:[REMINDER_DEFAULT_MINUTE](20:00).
 * 켜짐/시각 값은 소비처(설정 저장소)가 소유하고, 이 행은 표시 + 변경 콜백만 제공한다.
 */
@Composable
fun ReminderSettingRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    hour: Int,
    minute: Int,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: OceIcon? = null,
    supporting: String? = null,
) {
    var showPicker by remember { mutableStateOf(false) }
    // 설정 카드 안에서 쓰면 선행 아이콘 폭만큼 좌측 인셋 정렬(SettingsScreen 정합), 시트 등 아이콘 없으면 인셋 0.
    val rowInset = if (leadingIcon != null) REMINDER_ROW_INSET else 0.dp

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .padding(
                        horizontal = if (leadingIcon != null) OceTheme.spacing.lg else 0.dp,
                        vertical = OceTheme.spacing.sm,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
        ) {
            if (leadingIcon != null) {
                Box(
                    modifier =
                        Modifier
                            .size(REMINDER_ICON_BOX)
                            .clip(OceTheme.shapes.radius12)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = REMINDER_ICON_BG_ALPHA)),
                    contentAlignment = Alignment.Center,
                ) {
                    OneClickIcon(
                        icon = leadingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = OceIconSize.ListDisclosure,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "학습 리마인더",
                    style =
                        if (leadingIcon != null) {
                            OceTheme.typography.body.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                lineHeightStyle = ReminderTrimmedLineHeight,
                            )
                        } else {
                            OceTheme.typography.body
                        },
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = OceTheme.typography.helper.copy(lineHeightStyle = ReminderTrimmedLineHeight),
                        color = OceTheme.colors.textTertiary,
                        modifier = Modifier.padding(top = REMINDER_LABEL_GAP),
                    )
                }
            }
            OneClickSwitch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        if (enabled) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clickable { showPicker = true }
                        .padding(
                            start = rowInset,
                            end = if (leadingIcon != null) OceTheme.spacing.lg else 0.dp,
                            top = OceTheme.spacing.sm,
                            bottom = OceTheme.spacing.sm,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "알림 시각",
                    style = OceTheme.typography.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "%02d:%02d".format(hour, minute),
                    style = OceTheme.typography.body,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (showPicker) {
        OneClickTimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onConfirm = { h, m ->
                onTimeChange(h, m)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ReminderSettingRowPreview() {
    OceTheme {
        ReminderSettingRow(
            enabled = true,
            onEnabledChange = {},
            hour = REMINDER_DEFAULT_HOUR,
            minute = REMINDER_DEFAULT_MINUTE,
            onTimeChange = { _, _ -> },
            modifier = Modifier.padding(OceTheme.spacing.lg),
        )
    }
}

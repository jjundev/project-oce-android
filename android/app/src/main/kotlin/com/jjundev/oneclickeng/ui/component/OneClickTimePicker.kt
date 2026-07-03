package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * C10 시간 선택기 = M3 [TimePicker] 채택(분 단위) + 토큰 컬러. 정본: 02-shared-components.md:90 ·
 * notification-reminder.md §6.
 *
 * 리마인더 토글 ON일 때만 노출된다(C19 [ReminderSettingRow] 연계). 확정 다이얼로그 형태로,
 * 선택 시각(hour 0-23, minute)을 [onConfirm] 으로 돌려준다. 색만 토큰(`brand.primary`·`surface.card`)으로
 * 덮고 anatomy 는 M3 기본을 따른다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneClickTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state =
        rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true,
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = OceTheme.shapes.radius24,
        containerColor = MaterialTheme.colorScheme.surface,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(
                    text = "확인",
                    style = OceTheme.typography.sectionLabel,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "취소",
                    style = OceTheme.typography.sectionLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Box(
                modifier = Modifier.padding(top = OceTheme.spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                TimePicker(
                    state = state,
                    colors =
                        TimePickerDefaults.colors(
                            selectorColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
            }
        },
    )
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun OneClickTimePickerDialogPreview() {
    OceTheme {
        OneClickTimePickerDialog(
            initialHour = 20,
            initialMinute = 0,
            onConfirm = { _, _ -> },
            onDismiss = {},
        )
    }
}

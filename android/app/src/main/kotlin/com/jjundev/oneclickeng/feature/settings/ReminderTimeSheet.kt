package com.jjundev.oneclickeng.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.R
import com.jjundev.oneclickeng.ui.component.SheetPrimaryHeight
import com.jjundev.oneclickeng.ui.component.OneClickSegmentedControl
import com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet
import com.jjundev.oneclickeng.ui.theme.OceTheme

private enum class Period { AM, PM }

private val HOURS = (1..12).toList()
private val MINUTES = (0..55 step 5).toList()

/** 24h → (period, 1-12 hour). */
private fun split24(hour: Int): Pair<Period, Int> {
    val period = if (hour < 12) Period.AM else Period.PM
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return period to h12
}

/** (period, 1-12 hour) → 24h. */
private fun to24(period: Period, h12: Int): Int =
    when {
        period == Period.AM && h12 == 12 -> 0
        period == Period.AM -> h12
        h12 == 12 -> 12
        else -> h12 + 12
    }

/**
 * C19 리마인더 시간 피커(프로토 정합) — 오전/오후 세그먼트 + 시(1-12)·분(5단위) 휠 + 라이브 라벨 + "설정". M3 시계
 * 다이얼(OneClickTimePickerDialog) 대체. 임시 선택은 시트 내부 상태로만 소유하고 [onConfirm] 에서 24h로 환산한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimeSheet(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OneClickBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        ReminderTimeSheetContent(initialHour = initialHour, initialMinute = initialMinute, onConfirm = onConfirm)
    }
}

/** 시트 콘텐츠(stateless seam) — ModalBottomSheet 래핑 없이 렌더(스크린샷·프리뷰). */
@Composable
internal fun ReminderTimeSheetContent(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (initPeriod, initH12) = remember(initialHour) { split24(initialHour) }
    var period by remember { mutableStateOfPeriod(initPeriod) }
    var h12 by remember { mutableIntStateOf(initH12) }
    var minute by remember { mutableIntStateOf(initialMinute) }
    val amLabel = stringResource(R.string.settings_time_period_am)
    val pmLabel = stringResource(R.string.settings_time_period_pm)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.settings_reminder_time_label),
                style = OceTheme.typography.dialogHeader.copy(fontWeight = FontWeight.ExtraBold, fontSize = 19.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = reminderTimeLabel(to24(period, h12), minute),
                style = OceTheme.typography.dialogHeader.copy(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.Center) {
            OneClickSegmentedControl(
                options = listOf(Period.AM, Period.PM),
                selected = period,
                onSelect = { period = it },
                label = { if (it == Period.AM) amLabel else pmLabel },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(168.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WheelColumn(
                items = HOURS,
                selected = h12,
                onSelect = { h12 = it },
                label = { "${it}시" },
                modifier = Modifier.weight(1f),
            )
            WheelColumn(
                items = MINUTES,
                selected = minute,
                onSelect = { minute = it },
                label = { "${it}분" },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = { onConfirm(to24(period, h12), minute) },
            modifier = Modifier.fillMaxWidth().height(SheetPrimaryHeight),
            shape = OceTheme.shapes.radius12,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(text = stringResource(R.string.settings_reminder_time_confirm), style = OceTheme.typography.sectionLabel)
        }
    }
}

@Composable
private fun WheelColumn(
    items: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    label: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .clip(OceTheme.shapes.radius16)
            .background(MaterialTheme.colorScheme.background)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items) { value ->
            val isSel = value == selected
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(OceTheme.shapes.radius12)
                    .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(value),
                    fontSize = 16.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// mutableStateOf helper typed to the private enum (avoids importing setValue delegate ambiguity).
private fun mutableStateOfPeriod(initial: Period) = androidx.compose.runtime.mutableStateOf(initial)

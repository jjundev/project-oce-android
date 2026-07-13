package com.jjundev.oneclickeng.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.R
import com.jjundev.oneclickeng.ui.component.SheetPrimaryHeight
import com.jjundev.oneclickeng.ui.component.OneClickSegmentedControl
import com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet
import com.jjundev.oneclickeng.ui.component.primitive.blockSheetDrag
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
 * C19 리마인더 시간 피커(프로토 정합) — 오전/오후 세그먼트 + 시(1-12)·분(5단위) 휠 + 라이브 라벨 + "설정".
 * 구형 M3 시계 다이얼 대체. 임시 선택은 시트 내부 상태로만 소유하고 [onConfirm] 에서 24h로 환산한다.
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
    OneClickBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        // M3 드래그 핸들 슬롯은 비운다(그건 시트 Surface 의 드래그 어포던스라 드래그를 되살린다). 대신 아래
        // 드래그 차단 컨테이너 안에 장식용 그래버를 둬, 보이되 잡아끌 수는 없게 한다.
        dragHandle = null,
        // 여백을 시트 내부로 이관 → 드래그 차단 모디파이어가 좌우/상하 여백까지 덮는다.
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    // 드래그로 시트를 줄이거나 늘리는 것을 막는다(그래버·본문·휠 오버스크롤 모두 차단).
                    .blockSheetDrag()
                    .padding(
                        start = OceTheme.spacing.sheetPadding,
                        end = OceTheme.spacing.sheetPadding,
                        // 상단 여백은 그래버(top 12 / bottom 16)가 제공한다.
                        top = 0.dp,
                        bottom = OceTheme.spacing.sheetContentBottom,
                    ),
        ) {
            SheetGrabber()
            ReminderTimeSheetContent(initialHour = initialHour, initialMinute = initialMinute, onConfirm = onConfirm)
        }
    }
}

/** 그래버 바 노드 태그(테스트에서 존재 검증). */
internal const val GRABBER_TEST_TAG = "reminderTimeSheetGrabber"

/**
 * 시트 상단 장식용 그래버 바(프로토 36×4 pill). 순수 시각 어포던스이며 실제 드래그는 [blockSheetDrag] 로
 * 막혀 있다 — M3 `dragHandle` 슬롯(시트 Surface 밖)과 달리 드래그 차단 컨테이너 안에 있어 잡아끌 수 없다.
 */
@Composable
private fun SheetGrabber(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 16.dp)
                .testTag(GRABBER_TEST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(OceTheme.shapes.pill)
                    // 프로토 --border-strong 정확 매핑(outlineVariant 는 hairline 이라 더 옅음).
                    .background(OceTheme.colors.borderStrong),
        )
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
            Text(
                text = stringResource(R.string.settings_reminder_time_confirm),
                style = OceTheme.typography.sectionLabel,
            )
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
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = items.indexOf(selected).coerceAtLeast(0))
    LazyColumn(
        modifier = modifier
            .clip(OceTheme.shapes.radius16)
            .background(MaterialTheme.colorScheme.background)
            .padding(4.dp),
        state = listState,
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

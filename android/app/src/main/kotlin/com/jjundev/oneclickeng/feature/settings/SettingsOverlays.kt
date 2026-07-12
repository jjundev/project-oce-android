package com.jjundev.oneclickeng.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jjundev.oneclickeng.R
import com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet
import com.jjundev.oneclickeng.ui.theme.OceTheme
import com.jjundev.oneclickeng.feature.settings.data.PurgeScope

/**
 * 프로토 정합 확인 다이얼로그 셸 — radius16 카드 · ExtraBold 18sp 제목 · 14sp 본문 · 하단 풀폭 2버튼
 * (취소=hairline outline / 실행=[confirmColor] 채움, 48dp·radius12). purge-confirm/reset/logout 공유.
 */
@Composable
internal fun SettingsConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    confirmColor: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .clip(OceTheme.shapes.radius16)
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
        ) {
            Text(
                text = title,
                style = OceTheme.typography.dialogHeader.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = OceTheme.typography.body.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DialogButtonRow(
                confirmLabel = confirmLabel,
                confirmColor = confirmColor,
                confirmEnabled = true,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )
        }
    }
}

/** 하단 풀폭 취소/실행 버튼 행(48dp · radius12). 프로토 전 다이얼로그 공유. */
@Composable
internal fun DialogButtonRow(
    confirmLabel: String,
    confirmColor: Color,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = OceTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(OceTheme.shapes.radius12)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, OceTheme.shapes.radius12)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.settings_dialog_cancel),
                style = OceTheme.typography.sectionLabel.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val actionAlpha = if (confirmEnabled) 1f else DISABLED_ALPHA
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(OceTheme.shapes.radius12)
                .background(confirmColor.copy(alpha = actionAlpha))
                .clickable(enabled = confirmEnabled, onClick = onConfirm),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = confirmLabel,
                style = OceTheme.typography.sectionLabel.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                color = Color.White,
            )
        }
    }
}

/** 카드 정리 시트(프로토 정합) — 제목 + 설명 + 3개 옵션 카드(라벨/서브/카운트 배지, 전체=error색). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardPurgeSheet(
    counts: Map<PurgeScope, Int>?,
    onDismiss: () -> Unit,
    onSelect: (PurgeScope) -> Unit,
) {
    OneClickBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.settings_purge_sheet_title),
            style = OceTheme.typography.dialogHeader.copy(fontWeight = FontWeight.ExtraBold, fontSize = 19.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.settings_purge_sheet_desc),
            style = OceTheme.typography.body.copy(fontSize = 13.5f.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = OceTheme.spacing.lg),
        )
        Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
            PurgeOption(
                PurgeScope.LAST_30_DAYS,
                R.string.settings_purge_30,
                R.string.settings_purge_30_sub,
                counts,
                isAll = false,
                onSelect,
            )
            PurgeOption(
                PurgeScope.LAST_90_DAYS,
                R.string.settings_purge_90,
                R.string.settings_purge_90_sub,
                counts,
                isAll = false,
                onSelect,
            )
            PurgeOption(
                PurgeScope.ALL,
                R.string.settings_purge_all,
                R.string.settings_purge_all_sub,
                counts,
                isAll = true,
                onSelect,
            )
        }
    }
}

@Composable
private fun PurgeOption(
    scope: PurgeScope,
    labelRes: Int,
    subRes: Int,
    counts: Map<PurgeScope, Int>?,
    isAll: Boolean,
    onSelect: (PurgeScope) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OceTheme.shapes.radius16)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, OceTheme.shapes.radius16)
            .clickable { onSelect(scope) }
            .padding(horizontal = OceTheme.spacing.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(labelRes),
                style = OceTheme.typography.sectionLabel.copy(fontWeight = FontWeight.Bold, fontSize = 14.5f.sp),
                color = if (isAll) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(subRes),
                style = OceTheme.typography.helper.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
                color = OceTheme.colors.textTertiary,
            )
        }
        val n = counts?.get(scope)
        if (n != null) {
            Box(
                modifier = Modifier
                    .clip(OceTheme.shapes.pill)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = OceTheme.spacing.md, vertical = 5.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_purge_count_badge, n),
                    style = OceTheme.typography.tabActive,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.38f

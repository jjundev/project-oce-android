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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.jjundev.oneclickeng.ui.component.primitive.OneClickInput
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

/** 닉네임 편집 다이얼로그(프로토 정합) — 부제 + n/20 카운터 + placeholder "닉네임 (선택)". */
@Composable
internal fun NicknameEditDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .clip(OceTheme.shapes.radius16)
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
        ) {
            Text(
                text = stringResource(R.string.settings_nickname_edit_title),
                style = OceTheme.typography.dialogHeader.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.settings_nickname_edit_subtitle),
                style = OceTheme.typography.body.copy(fontSize = 13.5f.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OneClickInput(
                value = text,
                onValueChange = { if (it.length <= NICKNAME_MAX_LEN) text = it },
                placeholder = stringResource(R.string.settings_nickname_input_placeholder),
                helper = stringResource(R.string.settings_nickname_counter, text.length),
                modifier = Modifier.fillMaxWidth(),
            )
            DialogButtonRow(
                confirmLabel = stringResource(R.string.settings_nickname_edit_save),
                confirmColor = MaterialTheme.colorScheme.primary,
                confirmEnabled = true,
                onConfirm = { onConfirm(text) },
                onDismiss = onDismiss,
            )
        }
    }
}

private enum class DeleteStep { Warn, Confirm }

/** 계정 삭제 2단계(프로토 정합) — 1/2 경고(계속) → 2/2 "삭제" 타이핑 확인(정확 일치 전 disabled). */
@Composable
internal fun DeleteAccountDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(DeleteStep.Warn) }
    var typed by remember { mutableStateOf("") }
    val confirmWord = stringResource(R.string.settings_delete_confirm_word)
    val matched = typed.trim().equals(confirmWord.trim(), ignoreCase = true)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .clip(OceTheme.shapes.radius16)
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
        ) {
            val badgeRes = if (step == DeleteStep.Warn) R.string.settings_delete_step1_badge else R.string.settings_delete_step2_badge
            Box(
                modifier = Modifier
                    .clip(OceTheme.shapes.pill)
                    .background(OceTheme.colors.feedbackCorrectBg)
                    .padding(horizontal = OceTheme.spacing.md, vertical = 5.dp),
            ) {
                Text(
                    text = stringResource(badgeRes),
                    style = OceTheme.typography.tabActive.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            when (step) {
                DeleteStep.Warn -> {
                    Text(
                        text = stringResource(R.string.settings_delete_step1_title),
                        style = OceTheme.typography.dialogHeader.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_delete_step1_body),
                        style = OceTheme.typography.body.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DialogButtonRow(
                        confirmLabel = stringResource(R.string.settings_delete_step1_continue),
                        confirmColor = MaterialTheme.colorScheme.error,
                        confirmEnabled = true,
                        onConfirm = { step = DeleteStep.Confirm },
                        onDismiss = onDismiss,
                    )
                }
                DeleteStep.Confirm -> {
                    Text(
                        text = stringResource(R.string.settings_delete_step2_title),
                        style = OceTheme.typography.dialogHeader.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_delete_step2_body),
                        style = OceTheme.typography.body.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OneClickInput(
                        value = typed,
                        onValueChange = { typed = it },
                        placeholder = confirmWord,
                        isError = typed.isNotEmpty() && !matched,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DialogButtonRow(
                        confirmLabel = stringResource(R.string.settings_account_delete),
                        confirmColor = MaterialTheme.colorScheme.error,
                        confirmEnabled = matched,
                        onConfirm = onConfirm,
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.38f
private const val NICKNAME_MAX_LEN = 20

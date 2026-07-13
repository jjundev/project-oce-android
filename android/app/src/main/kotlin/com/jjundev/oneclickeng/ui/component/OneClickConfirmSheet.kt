package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** 제목→본문 간격(리마인더 opt-in 시트와 동일 4dp 리듬). */
private val ConfirmLabelGap = 4.dp

/**
 * 재사용 확인 시트(비드래그 [OneClickBottomSheet] 재사용). 제목 + 선택 본문 + primary(머무르기)/ghost(떠나기)
 * 2버튼. 버튼 역할은 프로토 대화 중단 시트 정합: primary = 안전한 "머무르기"([stayLabel]/[onStay]),
 * ghost = "떠나기"([leaveLabel]/[onLeave]). 스크림 탭·시트 뒤로가기(onDismissRequest)도 [onStay]로 수렴한다.
 *
 * 콜러: 앱 종료 시트([AppExitGuard]) · 대화 중단 시트([DialogueExitGuard]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneClickConfirmSheet(
    title: String,
    stayLabel: String,
    leaveLabel: String,
    onStay: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    OneClickBottomSheet(
        onDismissRequest = onStay,
        modifier = modifier,
        draggable = false,
    ) {
        OneClickConfirmSheetContent(
            title = title,
            message = message,
            stayLabel = stayLabel,
            leaveLabel = leaveLabel,
            onStay = onStay,
            onLeave = onLeave,
        )
    }
}

/** 시트 콘텐츠(stateless) — ModalBottomSheet 래핑 없이 렌더하는 스크린샷·테스트 seam. */
@Composable
internal fun OneClickConfirmSheetContent(
    title: String,
    message: String?,
    stayLabel: String,
    leaveLabel: String,
    onStay: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Text(
            text = title,
            style = OceTheme.typography.dialogHeader,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (message != null) {
            Text(
                text = message,
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = ConfirmLabelGap),
            )
        }
        Spacer(modifier = Modifier.height(OceTheme.spacing.xl))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
        ) {
            Button(
                onClick = onStay,
                modifier = Modifier.fillMaxWidth().height(SheetPrimaryHeight),
                shape = OceTheme.shapes.radius12,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text(text = stayLabel, style = OceTheme.typography.sectionLabel)
            }
            TextButton(
                onClick = onLeave,
                modifier = Modifier.fillMaxWidth().height(SheetGhostHeight),
            ) {
                Text(
                    text = leaveLabel,
                    style = OceTheme.typography.sectionLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

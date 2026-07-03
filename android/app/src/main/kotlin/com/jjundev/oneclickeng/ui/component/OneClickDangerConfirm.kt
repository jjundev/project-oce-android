package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import com.jjundev.oneclickeng.ui.component.primitive.OneClickInput
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * C2 2단계 위험 확인 = C1 [OneClickDialog] destructive 확장 + [OneClickInput]. 정본: 02-shared-components.md:50.
 *
 * **(1)** 영향 명시 리스트 다이얼로그 → **(2)** [confirmationWord]("삭제") 타이핑 확인. 정확 일치(trim,
 * 대소문자 무관) 전까지 확인 버튼 disabled(M3 `enabled=false` → alpha 0.38 + disabled 시맨틱). 마찰 차등:
 * 초기화=C1 단일 / 계정삭제=C2 2단계. 타이핑 입력값은 컴포넌트 내부의 임시 뷰 상태로만 소유한다(앱 상태 아님).
 *
 * @param impactLines (1)단계에 나열할 영향 문구.
 * @param confirmationWord (2)단계 타이핑 매칭 문자열(기본 "삭제").
 */
@Composable
fun OneClickDangerConfirm(
    title: String,
    impactLines: List<String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmationWord: String = "삭제",
) {
    var step by remember { mutableStateOf(DangerConfirmStep.Impact) }
    var typed by remember { mutableStateOf("") }
    val matched = typed.trim().equals(confirmationWord.trim(), ignoreCase = true)
    val headerFocus = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = OceTheme.shapes.radius24,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = title,
                style = OceTheme.typography.dialogHeader,
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .focusRequester(headerFocus)
                        .focusable(),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
                when (step) {
                    DangerConfirmStep.Impact ->
                        impactLines.forEach { line ->
                            Text(
                                text = "• $line",
                                style = OceTheme.typography.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                    DangerConfirmStep.Typing -> {
                        Text(
                            text = "계속하려면 \"$confirmationWord\" 을(를) 입력해 주세요.",
                            style = OceTheme.typography.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OneClickInput(
                            value = typed,
                            onValueChange = { typed = it },
                            isError = typed.isNotEmpty() && !matched,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                DangerConfirmStep.Impact ->
                    TextButton(onClick = { step = DangerConfirmStep.Typing }) {
                        Text(
                            text = "계속",
                            style = OceTheme.typography.sectionLabel,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                DangerConfirmStep.Typing ->
                    TextButton(onClick = onConfirm, enabled = matched) {
                        Text(
                            text = confirmationWord,
                            style = OceTheme.typography.sectionLabel,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
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
    )

    LaunchedEffect(Unit) { headerFocus.requestFocus() }
}

/** C2 진행 단계. Impact(영향 명시) → Typing(단어 타이핑 확인). */
private enum class DangerConfirmStep {
    Impact,
    Typing,
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun OneClickDangerConfirmPreview() {
    OceTheme {
        OneClickDangerConfirm(
            title = "계정을 삭제할까요?",
            impactLines =
                listOf(
                    "모든 학습 기록이 사라져요.",
                    "저장한 표현과 streak 이 사라져요.",
                    "이 작업은 되돌릴 수 없어요.",
                ),
            onConfirm = {},
            onDismiss = {},
        )
    }
}

package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.focusable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * C1 확인 다이얼로그/알럿 = M3 [AlertDialog] 래핑. 정본: 02-shared-components.md:45.
 *
 * 헤더 `dialogHeader`(22sp) + 본문 `body`/`text.secondary` + 우측 액션행(취소=ghost, 확인=primary).
 * [OneClickDialogVariant.Destructive] 는 확인 라벨을 `state.error` 색으로 칠하되, **비색 신호(A2)** 를 위해
 * 호출부는 [confirmLabel] 에 색 단독이 아닌 **명시 동사**("삭제"/"초기화")를 넘겨야 한다.
 * 규약: 제네릭 라벨("확인"/"예") 금지 — 위험 액션의 결과를 라벨이 스스로 말해야 한다.
 *
 * 진입 시 헤더에 포커스(A5), 닫힘은 M3 기본대로 호출부로 복귀한다.
 */
@Composable
fun OneClickDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    variant: OneClickDialogVariant = OneClickDialogVariant.Default,
    dismissLabel: String = "취소",
) {
    val headerFocus = remember { FocusRequester() }
    val confirmColor =
        when (variant) {
            OneClickDialogVariant.Default -> MaterialTheme.colorScheme.primary
            OneClickDialogVariant.Destructive -> MaterialTheme.colorScheme.error
        }

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
            // AlertDialog 의 title 슬롯은 다이얼로그 자체의 별도 윈도우(서브컴포지션)에서 컴포즈된다. 이 effect 를
            // 바깥(호출부) 컴포지션에 두면 그 윈도우가 focusRequester 노드를 붙이기 전에 실행되어
            // "FocusRequester is not initialized" 로 죽을 수 있다(레이스) — 같은 서브컴포지션에 둬서 순서를 보장.
            LaunchedEffect(Unit) { headerFocus.requestFocus() }
        },
        text = {
            Text(
                text = body,
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    style = OceTheme.typography.sectionLabel,
                    color = confirmColor,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = dismissLabel,
                    style = OceTheme.typography.sectionLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/** C1 확인 다이얼로그 변형 축. Destructive 는 위험 액션(삭제·초기화·중단)에만 쓴다. */
enum class OneClickDialogVariant {
    Default,
    Destructive,
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun OneClickDialogDefaultPreview() {
    OceTheme {
        OneClickDialog(
            title = "로그아웃할까요?",
            body = "다시 로그인하면 이어서 학습할 수 있어요.",
            confirmLabel = "로그아웃",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun OneClickDialogDestructivePreview() {
    OceTheme {
        OneClickDialog(
            title = "저장한 카드를 삭제할까요?",
            body = "이 작업은 되돌릴 수 없어요.",
            confirmLabel = "삭제",
            onConfirm = {},
            onDismiss = {},
            variant = OneClickDialogVariant.Destructive,
        )
    }
}

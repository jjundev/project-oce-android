package com.jjundev.oneclickeng.feature.session.turn

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.jjundev.oneclickeng.ui.component.OneClickConfirmSheet

/** 프로토 대화 중단 시트 [C] 카피(변경 금지 — realization-SoT `prototype/Prototype Flow` 정합). */
private const val ABORT_TITLE = "대화를 그만할까요?"
private const val ABORT_MESSAGE = "지금 나가면 이 대화는 저장되지 않아요. 상황은 다시 고를 수 있어요."
private const val ABORT_STAY = "계속 이어하기"
private const val ABORT_LEAVE = "그만하기"

/**
 * 대화 화면 나가기 가드. 시스템 뒤로가기와 헤더 뒤로가기 화살표([content]에 넘기는 `onBackRequest`)를 모두
 * 가로채 곧장 나가지 않고 프로토 "대화를 그만할까요?" 경고 시트를 띄운다. `계속 이어하기`(primary)는 시트만
 * 닫고, `그만하기`(ghost)만 실제 나가기([onExit])를 수행한다.
 *
 * 시트가 떠 있는 동안 이 가드의 [BackHandler]는 비활성(`enabled = !abortVisible`)이라, 뒤로가기는
 * [OneClickConfirmSheet](ModalBottomSheet) 자체의 back(→ onStay)이 처리해 시트를 닫는다.
 */
@Composable
internal fun DialogueExitGuard(
    onExit: () -> Unit,
    content: @Composable (onBackRequest: () -> Unit) -> Unit,
) {
    var abortVisible by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = !abortVisible) { abortVisible = true }

    content { abortVisible = true }

    if (abortVisible) {
        OneClickConfirmSheet(
            title = ABORT_TITLE,
            message = ABORT_MESSAGE,
            stayLabel = ABORT_STAY,
            leaveLabel = ABORT_LEAVE,
            onStay = { abortVisible = false },
            onLeave = {
                abortVisible = false
                onExit()
            },
        )
    }
}

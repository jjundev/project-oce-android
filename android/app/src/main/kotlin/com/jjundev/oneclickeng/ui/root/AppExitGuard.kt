package com.jjundev.oneclickeng.ui.root

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.jjundev.oneclickeng.ui.component.OneClickConfirmSheet

/** 앱 종료 확인 시트 카피(프로토에 없는 신규 — 05-open-decisions 정합값). */
private const val EXIT_TITLE = "앱을 종료할까요?"
private const val EXIT_MESSAGE = "학습 기록은 저장돼요. 다음에 이어서 시작할 수 있어요."
private const val EXIT_STAY = "계속 사용하기"
private const val EXIT_LEAVE = "종료"

/**
 * 3탭 셸 종료 가드. [enabled]=true(=홈 탭 = 시작 목적지)일 때만 시스템 뒤로가기를 가로채 "앱을 종료할까요?"
 * 시트를 띄운다. `계속 사용하기`(primary)는 시트만 닫고, `종료`(ghost)만 [onExitApp]으로 실제 종료한다.
 *
 * [enabled]=false(기록/설정 탭)면 [BackHandler]가 비활성이라 뒤로가기가 기존 NavHost 기본 동작(홈 탭 복귀)으로
 * 그대로 흐른다. 시트가 떠 있는 동안엔 가드 핸들러가 비활성이라 뒤로가기는 시트 자체 back(→ onStay)이 닫는다.
 */
@Composable
internal fun AppExitGuard(
    enabled: Boolean,
    onExitApp: () -> Unit,
    content: @Composable () -> Unit,
) {
    var exitVisible by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = enabled && !exitVisible) { exitVisible = true }

    content()

    if (exitVisible) {
        OneClickConfirmSheet(
            title = EXIT_TITLE,
            message = EXIT_MESSAGE,
            stayLabel = EXIT_STAY,
            leaveLabel = EXIT_LEAVE,
            onStay = { exitVisible = false },
            onLeave = {
                exitVisible = false
                onExitApp()
            },
        )
    }
}

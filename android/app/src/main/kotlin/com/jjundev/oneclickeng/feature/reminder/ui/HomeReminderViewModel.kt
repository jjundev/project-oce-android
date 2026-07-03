package com.jjundev.oneclickeng.feature.reminder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjundev.oneclickeng.feature.reminder.ReminderOrchestrator
import com.jjundev.oneclickeng.feature.reminder.ReminderPromptDecision
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [HomeReminderHost] 가 관측하는 opt-in 시트 노출 상태. */
data class HomeReminderUiState(
    val showOptInSheet: Boolean = false,
)

/**
 * 홈 리마인더 opt-in UI 상태 adapter(notification-reminder.md §2). 저장소/스케줄러/계측 정책은
 * [ReminderOrchestrator] 가 소유하고, 권한 시스템 API 상호작용은 [HomeReminderHost] 컴포저블이 담당한다.
 *
 * 앵커(결정 #9): [evaluatePrompt] 는 홈 진입 시 1회 호출되어 `shouldPromptOptIn`(2번째 완주 && 미해소)
 * 게이트로 시트를 띄운다. 상태 기반 게이트라 탭 재선택 등 부가 재진입은 안전한 no-op 이다.
 */
@HiltViewModel
class HomeReminderViewModel
    @Inject
    constructor(
        private val reminderOrchestrator: ReminderOrchestrator,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HomeReminderUiState())
        val uiState: StateFlow<HomeReminderUiState> = _uiState.asStateFlow()

        /** 홈 진입 1회 평가. 조건 충족 시 시트 노출 + `reminder_prompt_shown`. */
        fun evaluatePrompt() {
            viewModelScope.launch {
                if (reminderOrchestrator.evaluateOptInPrompt() is ReminderPromptDecision.ShowPrompt) {
                    _uiState.update { it.copy(showOptInSheet = true) }
                }
            }
        }

        /** `[알림 받기]` — 멱등 해소 + 시트 닫기. 실제 권한 플로우는 호스트가 잇는다. */
        fun acceptOptIn() {
            _uiState.update { it.copy(showOptInSheet = false) }
            viewModelScope.launch { reminderOrchestrator.acceptOptIn() }
        }

        /** `[다음에]`/닫기 — 멱등 해소(재제안 종료, D13) + 미참여 계측. */
        fun dismissOptIn() {
            _uiState.update { it.copy(showOptInSheet = false) }
            viewModelScope.launch {
                reminderOrchestrator.dismissOptIn()
            }
        }

        /** 권한 확보 후(또는 <33 즉시) 리마인더 켜기 + 예약 + 참여 계측. */
        fun enableReminder() {
            viewModelScope.launch {
                reminderOrchestrator.enableReminder()
            }
        }

        fun disableReminder() {
            viewModelScope.launch { reminderOrchestrator.disableReminder() }
        }

        fun setReminderTime(
            hour: Int,
            minute: Int,
        ) {
            viewModelScope.launch { reminderOrchestrator.setReminderTime(hour, minute) }
        }

        fun markPermissionAsked() {
            viewModelScope.launch { reminderOrchestrator.markPermissionAsked() }
        }
    }

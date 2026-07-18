package com.jjundev.oneclickeng.ui.root

import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjundev.oneclickeng.core.update.AppUpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 강제 업데이트 게이트(archive `MainActivity` 재구현). [AppRoot] 최상단에서 마운트돼 [AppViewModel]
 * 부트 게이트보다 먼저 확정된다 — 업데이트가 필요하면 로그인/온보딩/메인 어느 것도 컴포즈되지 않는다.
 * [AppViewModel] 은 건드리지 않는다(관심사 분리, 기존 부트 로직 위험 최소화).
 */
@HiltViewModel
class UpdateGateViewModel
    @Inject
    constructor(
        private val updateChecker: AppUpdateChecker,
    ) : ViewModel() {
        private val _state = MutableStateFlow<UpdateGateState>(UpdateGateState.Checking)
        val state: StateFlow<UpdateGateState> = _state.asStateFlow()
        private var launchJob: Job? = null

        init {
            viewModelScope.launch { check() }
        }

        private suspend fun check() {
            _state.value =
                if (runCatching { updateChecker.isImmediateUpdateRequired() }.getOrDefault(false)) {
                    UpdateGateState.Required
                } else {
                    UpdateGateState.NotRequired
                }
        }

        /**
         * [AppRoot] 의 ON_RESUME 훅(archive `onResume` 이식). 최초 판정이 끝나기 전(=[UpdateGateState.Checking])
         * 이면 아직 결정할 게 없으니 no-op. 이미 진행 중인 업데이트가 있으면 다시 [UpdateGateState.Required]
         * 로 세팅해 [AppRoot] 의 재개 트리거를 유도한다.
         */
        fun onResumeCheck() {
            if (_state.value == UpdateGateState.Checking) return
            viewModelScope.launch {
                if (runCatching { updateChecker.isUpdateInProgress() }.getOrDefault(false)) {
                    _state.value = UpdateGateState.Required
                }
            }
        }

        /**
         * [AppRoot] 는 이 함수를 두 곳에서 부를 수 있다: [UpdateGateState.Required] 진입 시 자동 트리거
         * (`LaunchedEffect`)와 [com.jjundev.oneclickeng.ui.component.OneClickUpdateGate] 의 수동 버튼.
         * 이미 진행 중인 [launchJob] 이 있으면 무시해 Play Core 플로우가 중복 시작되지 않게 한다.
         */
        fun launchUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
            if (launchJob?.isActive == true) return
            launchJob =
                viewModelScope.launch {
                    runCatching { updateChecker.launchImmediateUpdate(launcher) }
                        .onFailure { Log.w(TAG, "Failed to start immediate update flow", it) }
                }
        }

        private companion object {
            const val TAG = "UpdateGateViewModel"
        }
    }

/**
 * 강제 업데이트 게이트 상태(archive `MainActivity` 재구현). [Checking] 이 확정되기 전까지 [AppRoot] 는
 * splash 만 보여준다(기존 [BootState.Loading] 과 동일 자리, 별도 게이트라 합치지 않는다).
 */
sealed interface UpdateGateState {
    /** 최초 조회 중 — [AppRoot] 는 splash 만 보여준다. */
    data object Checking : UpdateGateState

    /** 업데이트 불필요 — [AppRoot] 는 기존 [AppViewModel] 부트 게이트로 진행한다. */
    data object NotRequired : UpdateGateState

    /** IMMEDIATE 업데이트 필요 — [AppRoot] 는 [com.jjundev.oneclickeng.ui.component.OneClickUpdateGate] 로 앱을 가로막는다. */
    data object Required : UpdateGateState
}

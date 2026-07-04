package com.jjundev.oneclickeng.feature.session.summary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * 요약 화면(M2-02)의 상태 adapter. 상태 머신·SSE·로컬 합성은 [SummaryCoordinator] 가 소유하고, 이 VM 은
 * 진입 시 1회 [start] 를 잇고 [retry] 를 위임한다. VM 소멸 시 코디네이터를 리셋해 in-flight SSE 를 닫는다.
 */
@HiltViewModel
class SummaryViewModel
    @Inject
    constructor(
        private val coordinator: SummaryCoordinator,
    ) : ViewModel() {
        val state: StateFlow<SummaryState> = coordinator.state

        private var started = false

        /** 요약 진입 1회 시작(멱등 — 재구성/탭 재진입 시 재시작 방지). */
        fun start(
            sessionId: String,
            difficulty: String,
            modeId: String,
            accrual: AccrualStrip,
        ) {
            if (started) return
            started = true
            coordinator.start(sessionId = sessionId, difficulty = difficulty, modeId = modeId, accrual = accrual)
        }

        fun retry(section: SummarySection) = coordinator.retry(section)

        override fun onCleared() {
            coordinator.reset()
        }
    }

/**
 * 요약 라우트 호스트. 주어진 `sessionId` 로 VM 을 시작하고 [SummaryScreen] 을 렌더한다.
 *
 * **통합 seam(#22):** 대화화면(DialogueTurnScreen)→완료(DialogueCompletion)→요약의 상위 nav 배선과 실제
 * `difficulty`/`modeId`/[accrual] 값 전달은 M1 nav 통합 의존이다(현재 대화 그래프 미배선). 이 호스트는
 * 그 값들을 인자로 받는 진입점만 확정한다 — [accrual] 정적 값의 실제 소스는 M3-05, 카운트업은 M3-06.
 */
@Composable
fun SummaryRoute(
    sessionId: String,
    difficulty: String,
    modeId: String,
    accrual: AccrualStrip,
    modifier: Modifier = Modifier,
    viewModel: SummaryViewModel = hiltViewModel(),
) {
    LaunchedEffect(sessionId) {
        viewModel.start(sessionId = sessionId, difficulty = difficulty, modeId = modeId, accrual = accrual)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SummaryScreen(state = state, onRetry = viewModel::retry, modifier = modifier)
}

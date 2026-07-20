package com.jjundev.oneclickeng.feature.onboarding.google

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjundev.oneclickeng.core.analytics.AnalyticsSink
import com.jjundev.oneclickeng.core.auth.AccountResetBus
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.core.auth.GoogleAccountLinker
import com.jjundev.oneclickeng.core.auth.LinkOutcome
import com.jjundev.oneclickeng.feature.onboarding.OnboardingAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Google 연결 시트의 링크 진행 상태(M3-03). FR-3a/3b 모두 성공은 [Success] 로 수렴한다. */
sealed interface LinkUiState {
    data object Idle : LinkUiState

    /** 자격증명 취득 또는 링크/이관 진행 중 — primary 버튼 로딩·비활성. */
    data object Linking : LinkUiState

    /** 연결 완료(승격 또는 이관) → 홈으로 이동. */
    data object Success : LinkUiState

    /**
     * 실패. [afterSignIn]=true 면 이미 target 계정으로 로그인된 상태(merge 만 실패)라 **이관만 재시도**해야 하고,
     * false 면 여전히 게스트라 전체 흐름을 다시 탈 수 있다(결정 B5).
     */
    data class Error(val afterSignIn: Boolean) : LinkUiState
}

/**
 * Google 저장 제안 시트의 링크 상태 소유자(M3-03). `OnboardingViewModel` 의 무상태 불변식을 지키려고 링크 상태는
 * 이 **전용 VM** 에 둔다(결정 A5). Activity 는 여기에 들어오지 않는다 — 컴포저블이 [GoogleCredentialProvider]
 * 로 토큰 문자열만 얻어 [linkGoogle] 로 넘긴다(결정 B3).
 */
@HiltViewModel
class GoogleLinkViewModel
    @Inject
    constructor(
        private val linker: GoogleAccountLinker,
        private val analytics: OnboardingAnalytics,
        private val accountResetBus: AccountResetBus,
        private val analyticsSink: AnalyticsSink,
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<LinkUiState>(LinkUiState.Idle)
        val uiState: StateFlow<LinkUiState> = _uiState.asStateFlow()

        /** §3b cohort stitching — after a link/merge resolves, re-point the analytics identity to the
         *  now-linked uid so D1/D7 cohorts stay continuous across the anon→linked boundary. */
        private fun stitchLinkedIdentity() {
            analyticsSink.setUserId(authRepository.currentUid)
            analyticsSink.setUserProperty("auth_state", "linked")
        }

        /** 자격증명 시스템 시트가 뜨는 동안 primary 버튼을 로딩으로 전환. */
        fun onCredentialFlowStarted() {
            _uiState.value = LinkUiState.Linking
        }

        /** 사용자가 Google 피커를 취소 — 조용히 Idle 로 되돌린다(에러 아님, 결정 B4·15). */
        fun onCredentialCancelled() {
            _uiState.value = LinkUiState.Idle
        }

        /** 자격증명 취득 자체 실패(취소 제외) — signIn 이전이므로 게스트 유지. */
        fun onCredentialFailed(sessionId: String) {
            analytics.googleLinkFailed(sessionId)
            _uiState.value = LinkUiState.Error(afterSignIn = false)
        }

        /** raw Google ID 토큰으로 FR-3a/3b 를 수행한다. */
        fun linkGoogle(
            googleIdToken: String,
            sessionId: String,
        ) {
            _uiState.value = LinkUiState.Linking
            viewModelScope.launch {
                _uiState.value =
                    when (linker.linkGuest(googleIdToken)) {
                        LinkOutcome.Promoted -> {
                            analytics.googleLinkSucceeded(sessionId)
                            stitchLinkedIdentity()
                            LinkUiState.Success
                        }
                        LinkOutcome.Merged -> {
                            analytics.googleLinkConflictMerged(sessionId)
                            stitchLinkedIdentity()
                            LinkUiState.Success
                        }
                        LinkOutcome.FailedAsGuest -> {
                            analytics.googleLinkFailed(sessionId)
                            LinkUiState.Error(afterSignIn = false)
                        }
                        LinkOutcome.FailedAfterSignIn -> {
                            analytics.googleLinkFailed(sessionId)
                            LinkUiState.Error(afterSignIn = true)
                        }
                    }
            }
        }

        /** signIn 성공 후 merge 실패한 상태에서의 in-session 이관 재시도(mergeGuestData 만). */
        fun retryMerge(sessionId: String) {
            _uiState.value = LinkUiState.Linking
            viewModelScope.launch {
                _uiState.value =
                    when (linker.retryPendingMerge()) {
                        LinkOutcome.Merged -> {
                            analytics.googleLinkConflictMerged(sessionId)
                            stitchLinkedIdentity()
                            LinkUiState.Success
                        }
                        else -> {
                            analytics.googleLinkFailed(sessionId)
                            LinkUiState.Error(afterSignIn = true)
                        }
                    }
            }
        }

        /**
         * 자격증명 취득 자체가 실패한 재인증 흐름(취소 제외) — signIn 이전이므로 게스트 유지. sessionId 없는
         * 재인증 문맥 버전([onCredentialFailed] 은 세션 완주 흐름 전용).
         */
        fun onCredentialFailedForReauth() {
            analytics.reauthLinkFailed()
            _uiState.value = LinkUiState.Error(afterSignIn = false)
        }

        /**
         * raw Google ID 토큰으로 재인증(로그아웃 후 복귀) 흐름을 수행한다. 성공([LinkOutcome.Promoted]/
         * [LinkOutcome.Merged]) 시 [AccountResetBus.signal] 로 앱 전역 부트 게이트를 재평가시킨다 — 새로
         * 로그인된 UID 의 `profile.level` 유무에 따라 [com.jjundev.oneclickeng.ui.root.AppViewModel] 이
         * 온보딩 계속/홈 진입을 스스로 가른다(이 뷰모델은 어느 쪽인지 알 필요가 없다).
         */
        fun linkGoogleForReauth(googleIdToken: String) {
            _uiState.value = LinkUiState.Linking
            viewModelScope.launch {
                when (linker.linkGuest(googleIdToken)) {
                    LinkOutcome.Promoted -> {
                        analytics.reauthLinkSucceeded()
                        _uiState.value = LinkUiState.Success
                        accountResetBus.signal()
                    }
                    LinkOutcome.Merged -> {
                        analytics.reauthLinkConflictMerged()
                        _uiState.value = LinkUiState.Success
                        accountResetBus.signal()
                    }
                    LinkOutcome.FailedAsGuest -> {
                        analytics.reauthLinkFailed()
                        _uiState.value = LinkUiState.Error(afterSignIn = false)
                    }
                    LinkOutcome.FailedAfterSignIn -> {
                        analytics.reauthLinkFailed()
                        _uiState.value = LinkUiState.Error(afterSignIn = true)
                    }
                }
            }
        }

        /** signIn 후 merge 실패 상태에서의 재시도(재인증 문맥, sessionId 없음). 성공 시 부트 게이트 재평가. */
        fun retryMergeForReauth() {
            _uiState.value = LinkUiState.Linking
            viewModelScope.launch {
                when (linker.retryPendingMerge()) {
                    LinkOutcome.Merged -> {
                        analytics.reauthLinkConflictMerged()
                        _uiState.value = LinkUiState.Success
                        accountResetBus.signal()
                    }
                    else -> {
                        analytics.reauthLinkFailed()
                        _uiState.value = LinkUiState.Error(afterSignIn = true)
                    }
                }
            }
        }
    }

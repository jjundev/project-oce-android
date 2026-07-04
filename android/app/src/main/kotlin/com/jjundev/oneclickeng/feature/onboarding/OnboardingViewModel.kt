package com.jjundev.oneclickeng.feature.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.core.auth.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 온보딩 화면(레벨/상황/GoogleSave)의 부수효과 어댑터(M3-02). 상태를 들지 않는다 — 화면 간 흐름은 nav-arg 로
 * 전달되고(프로세스킬 안전), 이 VM 은 레벨 저장(seam)과 분석 라우팅만 위임한다. 화면별로 독립 인스턴스여도
 * 무상태라 안전하다.
 */
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val profileRepository: ProfileRepository,
        private val analytics: OnboardingAnalytics,
    ) : ViewModel() {
        /** 레벨 화면 최초 컴포지션 1회 — 비동기 게이트와 분리해 여기서 발화(01a §? · 결정 15). */
        fun onOnboardingStarted(isReturning: Boolean) = analytics.onboardingStarted(isReturning)

        /**
         * 레벨 탭(결정 4·8). 분석을 남기고 `profile.level` 을 **fire-and-forget** 저장한다 — 내비를 막지 않는다.
         * "폐기 안 함"은 await 가 아니라 Firestore 오프라인 쓰기 큐(디스크 영속·자동 재시도)가 보장한다. 부트가
         * 이미 sign-in 을 마쳤으므로 `currentUid` 는 보통 non-null 이나, 방어적으로 없으면 sign-in 을 보장한다.
         */
        fun onLevelSelected(level: String) {
            analytics.levelSelected(level)
            viewModelScope.launch {
                runCatching {
                    val uid = authRepository.currentUid ?: authRepository.ensureSignedIn()
                    profileRepository.saveLevel(uid, level)
                }.onFailure {
                    Log.w(TAG, "saveLevel failed — Firestore offline queue will retry", it)
                }
            }
        }

        /** 상황 탭(결정 9). 온보딩 후보라 beginnerFriendly 는 항상 true. */
        fun onTopicSelected(topicId: String) = analytics.topicSelected(topicId, beginnerFriendly = true)

        /** Google 저장 제안 시트 노출(첫 완주 후 1회). */
        fun onGoogleSavePromptShown(sessionId: String) = analytics.googleSavePromptShown(sessionId)

        /** 저장 제안 스킵/보류. */
        fun onGoogleLinkSkipped(sessionId: String) = analytics.googleLinkSkipped(sessionId)

        private companion object {
            const val TAG = "OnboardingViewModel"
        }
    }

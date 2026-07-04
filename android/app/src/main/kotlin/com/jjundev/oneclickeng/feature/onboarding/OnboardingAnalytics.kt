package com.jjundev.oneclickeng.feature.onboarding

import javax.inject.Inject

/**
 * 온보딩 첫 세션 퍼널의 텔레메트리 seam(M3-02, 01-onboarding §9). 필드 이름·타입은 그 계약과 일치한다 —
 * 자유 로그 라인이 아니다. `WaitQuizAnalytics`/`LimitAnalytics` 와 같은 패턴: M3-02 는 seam 과
 * [NoOpOnboardingAnalytics] 기본 바인딩만 싣고, 실제 Firebase 디스패치는 분석 계측 마일스톤(M4-01)이 소유한다.
 * PII 경계: enum/bool/id 만 — 사용자 입력 텍스트는 절대 싣지 않는다.
 */
interface OnboardingAnalytics {
    /** 온보딩 진입(레벨 화면 최초 컴포지션). [isReturning] = 레벨 없이 재진입한 보정 온보딩 여부. */
    fun onboardingStarted(isReturning: Boolean)

    /** 레벨 문항 선택. [level] ∈ {easy, normal, hard}. */
    fun levelSelected(level: String)

    /** 상황 선택. [topicId] = seed id, [beginnerFriendly] 는 온보딩 후보라 항상 true. */
    fun topicSelected(
        topicId: String,
        beginnerFriendly: Boolean,
    )

    /** Google 저장 제안 시트 노출(첫 완주 후 1회). */
    fun googleSavePromptShown(sessionId: String)

    /** 저장 제안 스킵/보류(`나중에 할게요` 또는 시트 dismiss). */
    fun googleLinkSkipped(sessionId: String)
}

/** M4-01 이 실제 디스패치를 배선하기 전까지의 기본 no-op 바인딩. */
class NoOpOnboardingAnalytics
    @Inject
    constructor() : OnboardingAnalytics {
        override fun onboardingStarted(isReturning: Boolean) = Unit

        override fun levelSelected(level: String) = Unit

        override fun topicSelected(
            topicId: String,
            beginnerFriendly: Boolean,
        ) = Unit

        override fun googleSavePromptShown(sessionId: String) = Unit

        override fun googleLinkSkipped(sessionId: String) = Unit
    }

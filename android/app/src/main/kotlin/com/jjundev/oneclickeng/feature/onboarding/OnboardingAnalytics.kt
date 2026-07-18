package com.jjundev.oneclickeng.feature.onboarding

import javax.inject.Inject

/**
 * 온보딩 첫 세션 퍼널의 텔레메트리 seam(M3-02, 01-onboarding §9). 필드 이름·타입은 그 계약과 일치한다 —
 * 자유 로그 라인이 아니다. `WaitQuizAnalytics`/`LimitAnalytics` 와 같은 패턴: M3-02 는 seam 과
 * [NoOpOnboardingAnalytics] 기본 바인딩만 싣고, 실제 Firebase 디스패치는 분석 계측 마일스톤(M4-01)이 소유한다.
 * PII 경계: enum/bool/id 만 — 사용자 입력 텍스트는 절대 싣지 않는다.
 */
@Suppress("TooManyFunctions")
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

    /** FR-3a 인플레이스 승격 성공(신규 신원). */
    fun googleLinkSucceeded(sessionId: String)

    /** FR-3b 충돌 후 mergeGuestData 이관 성공(복귀 사용자). */
    fun googleLinkConflictMerged(sessionId: String)

    /** 연결/이관 실패(취소 제외 — 네트워크·머지 오류). */
    fun googleLinkFailed(sessionId: String)

    /** 재인증(로그아웃 후 복귀) 흐름 — FR-3a 인플레이스 승격 성공. 세션 문맥이 없어 sessionId 를 받지 않는다. */
    fun reauthLinkSucceeded()

    /** 재인증 흐름 — FR-3b 충돌 후 mergeGuestData 이관 성공. */
    fun reauthLinkConflictMerged()

    /** 재인증 흐름 — 연결/이관 실패(취소 제외). */
    fun reauthLinkFailed()
}

/** M4-01 이 실제 디스패치를 배선하기 전까지의 기본 no-op 바인딩. */
@Suppress("TooManyFunctions")
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

        override fun googleLinkSucceeded(sessionId: String) = Unit

        override fun googleLinkConflictMerged(sessionId: String) = Unit

        override fun googleLinkFailed(sessionId: String) = Unit

        override fun reauthLinkSucceeded() = Unit

        override fun reauthLinkConflictMerged() = Unit

        override fun reauthLinkFailed() = Unit
    }

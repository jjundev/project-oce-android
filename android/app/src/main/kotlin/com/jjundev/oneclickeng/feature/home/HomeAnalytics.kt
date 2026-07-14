package com.jjundev.oneclickeng.feature.home

import javax.inject.Inject

/**
 * 홈/학습 진입 보조 퍼널 텔레메트리 seam(M3-08, home-learning-entry.md §10). 이벤트명은 **제안명** —
 * 최종 event id 는 Analytics 설계(M4-01)에서 확정한다. `OnboardingAnalytics`/`LimitAnalytics` 와 같은
 * 패턴: M3-08 은 seam 과 [NoOpHomeAnalytics] 기본 바인딩만 싣고 실제 Firebase 디스패치는 M4-01 소유.
 * PII 경계: id/enum/bool 만 — 직접 입력 주제 텍스트는 절대 싣지 않는다(customTopic 은 bool 로만).
 */
interface HomeAnalytics {
    /** 홈 노출(홈 VM 최초 컴포지션). */
    fun homeView()

    /** 메인 CTA 탭(주제 선택으로 진입). */
    fun homeCtaTap()

    /** 미완 세션 이어하기 탭. */
    fun resumeContinue()

    /** 미완 세션 새로 시작 탭. */
    fun resumeStartNew()

    /** 주제 선택. [custom]=직접 입력 여부(텍스트는 싣지 않음). [topicId]=큐레이션이면 seed id, 직접 입력이면 null. */
    fun topicSelected(
        topicId: String?,
        custom: Boolean,
    )

    /** 접힌 세션 설정 변경. [level] ∈ {starter,easy,normal,hard,expert}, [length] ∈ 짝수 6..20. */
    fun sessionSettingChanged(
        level: String,
        length: Int,
    )

    /** 오프라인으로 새 학습 CTA 비활성(탭 불가) 상태 관측. */
    fun offlineBlocked()
}

/** M4-01 이 실제 디스패치를 배선하기 전까지의 기본 no-op 바인딩. */
class NoOpHomeAnalytics
    @Inject
    constructor() : HomeAnalytics {
        override fun homeView() = Unit

        override fun homeCtaTap() = Unit

        override fun resumeContinue() = Unit

        override fun resumeStartNew() = Unit

        override fun topicSelected(
            topicId: String?,
            custom: Boolean,
        ) = Unit

        override fun sessionSettingChanged(
            level: String,
            length: Int,
        ) = Unit

        override fun offlineBlocked() = Unit
    }

package com.jjundev.oneclickeng.core.network

import com.jjundev.oneclickeng.core.analytics.AnalyticsSink
import javax.inject.Inject

/**
 * 일일 한도 도달 텔레메트리 seam(M3-04). 정본 단일 이벤트 `limit_reached {remaining, surface}`
 * (daily-limit-ux.md §9 · analytics-events.md). `surface` 는 `LimitSurface.value` snake_case 문자열
 * (`home`/`dialogue_start_gate`/`onboarding_first_session`) — 차단형 표면 진입 시 1회 발화한다. 선행
 * 게이트(`quota_blocked` → `limit_reached` 정규화)는 이미 문서에서 충족됨.
 *
 * [WaitQuizAnalytics] 와 동일하게 M3-04 는 seam 과 [NoOpLimitAnalytics] 기본 바인딩만 싣고, 실제
 * Firebase 디스패치는 애널리틱스 계측 마일스톤(M4-01)이 소유한다. PII 경계: enum/count 만, 자유 문자열
 * 로그 없음.
 */
interface LimitAnalytics {
    fun limitReached(
        remaining: Int,
        surface: String,
    )
}

/** Default no-op binding until M4-01 wires real dispatch. */
class NoOpLimitAnalytics
    @Inject
    constructor() : LimitAnalytics {
        override fun limitReached(
            remaining: Int,
            surface: String,
        ) = Unit
    }

/** Firebase dispatch (M4-01). `limit_reached {remaining, surface}` — analytics-events.md §4/§6.5. */
class FirebaseLimitAnalytics
    @Inject
    constructor(
        private val sink: AnalyticsSink,
    ) : LimitAnalytics {
        override fun limitReached(
            remaining: Int,
            surface: String,
        ) = sink.log("limit_reached", mapOf("remaining" to remaining.toLong(), "surface" to surface))
    }

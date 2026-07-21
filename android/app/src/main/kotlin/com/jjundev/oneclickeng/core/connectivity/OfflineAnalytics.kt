package com.jjundev.oneclickeng.core.connectivity

import com.jjundev.oneclickeng.core.analytics.AnalyticsSink
import javax.inject.Inject

/**
 * 오프라인 텔레메트리 seam(M4-04, exception-states.md §9). 두 이벤트:
 * - `connectivity_changed {online}` — online/offline 전이(전이 시에만 발화).
 * - `offline_blocked_action {surface}` — 오프라인으로 차단된 액션 진입(예: 새 세션 시작 게이트).
 *
 * [com.jjundev.oneclickeng.core.network.LimitAnalytics] 와 동일 패턴: M4-04 는 seam + [NoOpOfflineAnalytics]
 * 기본 바인딩만 싣고 실제 Firebase 디스패치는 계측 마일스톤(M4-01)이 소유한다. PII 경계: enum/bool/surface
 * 문자열만, 자유 로그 없음.
 */
interface OfflineAnalytics {
    fun connectivityChanged(online: Boolean)

    fun offlineBlocked(surface: String)
}

/** M4-01 실 디스패치 배선 전 기본 no-op 바인딩. */
class NoOpOfflineAnalytics
    @Inject
    constructor() : OfflineAnalytics {
        override fun connectivityChanged(online: Boolean) = Unit

        override fun offlineBlocked(surface: String) = Unit
    }

/** Firebase dispatch (M4-01). `connectivity_changed` + `offline_blocked_action` — exception-states.md §9. */
class FirebaseOfflineAnalytics
    @Inject
    constructor(
        private val sink: AnalyticsSink,
    ) : OfflineAnalytics {
        override fun connectivityChanged(online: Boolean) =
            sink.log("connectivity_changed", mapOf("online" to online))

        override fun offlineBlocked(surface: String) =
            sink.log("offline_blocked_action", mapOf("surface" to surface))
    }

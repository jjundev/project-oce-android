package com.jjundev.oneclickeng.core.connectivity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 앱 전역 인터넷 도달성(exception-states.md §4). `Offline` = OS 레벨 인터넷 미도달(활성 네트워크 없음 또는
 * 미검증). `/llm` 요청 실패는 별개 신호이며 이 상태로 판정하지 않는다(exception-states.md:56 신호 분리).
 */
enum class Connectivity { Online, Offline }

/**
 * 앱 전역 리액티브 연결성 소스(M4-04, ADR-0002 · exception-states.md §4). "연결성 감지는 구현 가정"이라
 * 문서는 UX 결과만 규정하고 감지 메커니즘은 이 seam 이 소유한다(exception-states.md:52).
 *
 * **소유 경계:** M4-04 는 이 상태를 노출만 한다. 오프라인 배너[D]를 화면에 배치·바인딩하는 것은 M4-03
 * 소관이다(`issues/M4-03-error-empty-loading.md:25` · `issues/M4-04-offline-support.md:26`). 소비처:
 * 배너(M4-03), 핵심 루프 오프라인 게이트([com.jjundev.oneclickeng.feature.session.dialogue] pre-flight/in-flight),
 * studytime write-ahead 재접속 드레인([com.jjundev.oneclickeng.ui.root.AppViewModel]).
 */
interface ConnectivityObserver {
    /** 최신 도달성. 초기값은 구독 시점의 활성 네트워크에서 동기 산출된다(구현 참조). */
    val state: StateFlow<Connectivity>

    /** pre-flight 게이트용 동기 편의 — 새 세션 시작 진입 전 즉시 판정(exception-states.md §4 진입 전 게이트). */
    fun isOffline(): Boolean = state.value == Connectivity.Offline
}

/**
 * 항상 `Online` 인 stub. 연결성이 무관한 단위 테스트·프리뷰의 안전 기본값이며, 연결성 인지 없이 생성되는
 * 코디네이터 등의 기본 인자로 쓴다(감지 실패 시 거짓 오프라인보다 거짓 온라인이 안전 — exception-states.md:59).
 */
object OnlineConnectivityObserver : ConnectivityObserver {
    override val state: StateFlow<Connectivity> = MutableStateFlow(Connectivity.Online)
}

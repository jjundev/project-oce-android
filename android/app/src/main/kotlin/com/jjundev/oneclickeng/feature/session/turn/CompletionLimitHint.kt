package com.jjundev.oneclickeng.feature.session.turn

/**
 * 완주 화면의 한도 힌트 상태(M3-04, daily-limit-ux.md §4). 완주 축하가 **1차**, 한도 안내는 그 하단
 * **보조 인라인 1줄**이라는 위계(§4·gamification-emphasis.md §35·§49)를 코드로 표현한다. 잔여 수는
 * 노출하지 않으므로(§0 금지) 비숫자 어포던스만 쓴다. 어떤 힌트를 보일지(remaining 판정)는 호출부(요약
 * 라우트, M2-02)가 서버 값으로 결정한다 — 완주 화면은 카운트를 신뢰하지 않는다(FR-27).
 *  - [None] : remaining ≥ 2 — 힌트 없음.
 *  - [PreLimit] : 완주로 remaining→1 그 순간 1회 — `오늘 한 번 더 할 수 있어요`.
 *  - [AtLimit] : 마지막 무료 세션 완주(완주+도달 동시, remaining==0, P6) — 도달 문구 보조.
 */
enum class CompletionLimitHint {
    None,
    PreLimit,
    AtLimit,
}

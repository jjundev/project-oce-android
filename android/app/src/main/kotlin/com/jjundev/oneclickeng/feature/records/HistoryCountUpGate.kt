package com.jjundev.oneclickeng.feature.records

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 평생 통계 헤더 카운트업의 "세션당 1회" 게이트(I3, 04-screen-06-history.md R1). 앱 프로세스 수명 동안 최초
 * 기록 탭 진입에서만 `true` 를 한 번 내주고 이후엔 `false`(정적 스냅). `@Singleton` 이라 탭 재진입·회전·탭 전환에
 * 걸쳐 상태를 공유한다.
 *
 * 스텁 통계(M3-05 배선 전)에서는 [RecordsViewModel] 이 이 게이트와 무관하게 정적으로 강등하므로, 게이트는
 * 실데이터가 붙는 M3-05 이후에 실효한다.
 */
@Singleton
class HistoryCountUpGate
    @Inject
    constructor() {
        private val consumed = AtomicBoolean(false)

        /** 최초 호출만 `true`. 멱등하지 않음(소비형) — 진입 1회 판정에 쓴다. */
        fun consumeFirstEntry(): Boolean = consumed.compareAndSet(false, true)
    }

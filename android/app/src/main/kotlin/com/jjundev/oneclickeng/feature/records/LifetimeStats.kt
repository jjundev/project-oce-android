package com.jjundev.oneclickeng.feature.records

import com.jjundev.oneclickeng.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 기록 탭 누적 통계 3지표(gamification §8·04-screen-06-history.md R1). `누적 N XP · 총 N시간 N분 · N일 학습`.
 */
data class LifetimeStats(
    val xp: Int,
    val studyMinutes: Int,
    val studyDays: Int,
)

/**
 * 누적 통계 소스 seam. **게임화 통계 배선은 M2-05 범위 밖(M3-05)**이라 v1 은 스텁이 `null` 을 반환하고,
 * 헤더는 정적 0 스냅으로 렌더한다(카운트업 연출도 정적 — 0→0 죽은 애니메이션 방지). M3-05 가 이 seam 을
 * 실데이터 구현으로 교체하면 헤더 값·카운트업이 자동으로 살아난다.
 */
interface LifetimeStatsSource {
    /** 배선 전이면 `null`(=스텁, 헤더 정적 0). 배선 후엔 실제 누적 통계. */
    suspend fun lifetime(): LifetimeStats?
}

/**
 * M3-05 배선 전 스텁. **release = 항상 `null`**(헤더 정적 0). **debug 빌드에서는** 슬롯머신 카운트업을 눈으로
 * 검증할 수 있도록 샘플 통계([DEBUG_SAMPLE])를 시드한다(`BuildConfig.DEBUG` 게이트) — 실데이터 배선(M3-05)과
 * 무관한 개발 확인용이며, M3-05 가 이 seam 을 교체하면 제거 대상이다.
 */
@Singleton
class StubLifetimeStatsSource
    @Inject
    constructor() : LifetimeStatsSource {
        override suspend fun lifetime(): LifetimeStats? =
            if (BuildConfig.DEBUG) DEBUG_SAMPLE else null

        private companion object {
            val DEBUG_SAMPLE = LifetimeStats(xp = 1240, studyMinutes = 135, studyDays = 12)
        }
    }

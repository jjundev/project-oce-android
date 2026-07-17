package com.jjundev.oneclickeng.feature.records

/**
 * 기록 탭 누적 통계 3지표(gamification §8·04-screen-06-history.md R1). `누적 N XP · 총 N시간 N분 · N일 학습`.
 */
data class LifetimeStats(
    val xp: Int,
    val studyMinutes: Int,
    val studyDays: Int,
)

/**
 * 누적 통계 소스 seam. 실데이터는 [FirestoreLifetimeStatsSource]가 공급한다 — 서버 `gamification/progress`의
 * xp/studyDays(Functions 전용 권위) + 로컬 [com.jjundev.oneclickeng.feature.gamification.data.StudytimeStore]의
 * 학습시간(클라 권위)을 합성한다.
 */
interface LifetimeStatsSource {
    /** 오프라인이고 Firestore 캐시도 없는 등 데이터를 읽을 수 없으면 `null`(헤더 정적 0 스냅). 그 외엔 실제 누적 통계. */
    suspend fun lifetime(): LifetimeStats?
}

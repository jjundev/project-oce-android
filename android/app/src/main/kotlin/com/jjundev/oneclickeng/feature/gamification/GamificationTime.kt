package com.jjundev.oneclickeng.feature.gamification

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Pure client-side gamification helpers (M3-05). No DataStore, no Firestore — unit-testable alone.
 *
 * The server (`onLedgerCreate`, firestore-schema.md §5) is the AUTHORITY for `gamification/progress`
 * (xp/streak/studyDays). These helpers only produce the CLIENT's optimistic, display-facing values
 * (study-time label + a local streak estimate) shown at summary entry before the server trigger's
 * eventual reconciliation. [XP_BY_DIFFICULTY] mirrors the server constant so the accrual strip can
 * show XP synchronously; the server remains the single source of truth.
 */
object GamificationTime {
    /** KST — the calendar zone all day-keys and the streak recurrence key on (firestore-schema.md §5). */
    val KST: ZoneId = ZoneId.of("Asia/Seoul")

    /** Client copy of the server XP map (firestore-schema.md:198). Server is authoritative; this is display-only. */
    val XP_BY_DIFFICULTY: Map<String, Int> =
        mapOf(
            "starter" to 5,
            "easy" to 10,
            "normal" to 20,
            "hard" to 35,
            "expert" to 55,
        )

    /** Per-session study-time cap in seconds — user-confirmed product cap (30 min), guards absurd elapsed values. */
    const val STUDYTIME_SESSION_CAP_SECONDS: Long = 1800

    /** epoch millis → `yyyy-MM-dd` KST calendar day-key (matches the server's lastStudyDate format). */
    fun kstDayKey(nowMs: Long): String = kstLocalDate(nowMs).toString()

    /** epoch millis → KST epochDay(Long) — 홈 추천 상황의 결정적 일일 회전 키(TopicCatalog.recommended). */
    fun kstEpochDay(nowMs: Long): Long = kstLocalDate(nowMs).toEpochDay()

    private fun kstLocalDate(nowMs: Long): LocalDate = Instant.ofEpochMilli(nowMs).atZone(KST).toLocalDate()

    /**
     * Wall-clock study seconds for a completed session, clamped to [0, cap]. A null start (session
     * never anchored a start time) yields 0 — no accrual rather than a bogus duration.
     */
    fun elapsedStudySeconds(
        startMillis: Long?,
        nowMillis: Long,
        capSeconds: Long = STUDYTIME_SESSION_CAP_SECONDS,
    ): Long {
        if (startMillis == null) return 0
        val seconds = (nowMillis - startMillis) / 1000
        return seconds.coerceIn(0, capSeconds)
    }

    /**
     * Optimistic streak recurrence — the same O(1) rule the server applies (firestore-schema.md:186-190),
     * run client-side over the local mirror: first study → 1; consecutive day → +1; one-day gap → held
     * flat (grace); a same-day repeat → unchanged; a ≥2-day gap → reset to 1. All day-keys are `yyyy-MM-dd`.
     */
    fun advanceStreak(
        prevStreak: Int,
        prevLastStudyDate: String?,
        dayKey: String,
    ): Int =
        when {
            prevLastStudyDate == null -> 1
            prevLastStudyDate == dayKey -> prevStreak
            else ->
                when (ChronoUnit.DAYS.between(LocalDate.parse(prevLastStudyDate), LocalDate.parse(dayKey))) {
                    1L -> prevStreak + 1 // consecutive day
                    2L -> prevStreak // one-day grace: held flat
                    else -> 1 // ≥2-day gap: reset
                }
        }
}

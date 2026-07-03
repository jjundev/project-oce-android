package com.jjundev.oneclickeng.feature.reminder

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * M3-07 로컬 리마인더의 **순수 결정 로직**. 안드로이드/코루틴 의존 없이 값→값으로만 계산해
 * JVM 단위 테스트로 전건 반증가능하게 한다(notification-reminder.md §4·§4.1·§5.1).
 *
 * 시계 분리(결정 #6): 스케줄 예약([computeInitialDelay])은 **기기 로컬 wall-clock** 을,
 * "오늘 학습했나" 스킵 판정([decideFire])은 **고정 KST 캘린더일**([KST])을 쓴다.
 */
object ReminderLogic {
    /** "오늘" 판정 기준 시간대. lastStudyDate 미러가 KST 로 저장되므로 비교도 KST 로 한다(§4.1). */
    val KST: ZoneId = ZoneId.of("Asia/Seoul")

    /**
     * opt-in 시트 노출 게이트(§2.1·§2.2). 2번째 완주 후 1회, 미해소일 때만.
     * 상태 기반이라 홈 재진입 경로(탭 재선택 등)와 무관하게 정확히 1회만 참이 된다(grill-review #9 동등성).
     */
    fun shouldPrompt(
        completedSessionCount: Int,
        optInResolved: Boolean,
    ): Boolean = completedSessionCount == 2 && !optInResolved

    /** 발화 여부 결정(§4.1). 캐시만 보고 오프라인 안전하게 분기한다. */
    enum class FireDecision {
        /** 오늘 아직 학습 안 함 → 발화. */
        FIRE,

        /** 오늘 이미 학습 → 침묵(그래도 다음날 재예약). */
        SKIP_STUDIED_TODAY,

        /** 캐시 부재(신규/재설치) → 무음 실패보다 발화가 안전. */
        FIRE_CACHE_MISS,
    }

    fun decideFire(
        lastStudyDate: LocalDate?,
        today: LocalDate,
    ): FireDecision =
        when (lastStudyDate) {
            null -> FireDecision.FIRE_CACHE_MISS
            today -> FireDecision.SKIP_STUDIED_TODAY
            else -> FireDecision.FIRE
        }

    /** 알림 콘텐츠(§5.1). 미래형 초대만 — 손실 프레이밍 금지. */
    data class ReminderContent(
        val title: String,
        val body: String,
    )

    private const val TITLE = "딸깍영어"

    /**
     * body 카피 분기(§5.1). skip-if-studied 로 인해 "오늘 아직 학습 안 한" 사용자에게만 호출된다.
     * - streak 0 또는 캐시 부재 → 중립 시작 초대.
     * - gap==1(어제 학습, 정상 연속) → `${N+1}` 미래 숫자(§5.1: 이때만 정확).
     * - gap>=2(유예/리셋 임박) → 숫자 없는 중립 초대(오늘 학습해도 평탄 유지라 +1 이 거짓).
     */
    fun buildContent(
        streak: Int?,
        lastStudyDate: LocalDate?,
        today: LocalDate,
    ): ReminderContent {
        val body =
            when {
                streak == null || streak == 0 || lastStudyDate == null -> "오늘 시작하면 1일째예요"
                ChronoUnit.DAYS.between(lastStudyDate, today) == 1L ->
                    "🔥 ${streak}일째 — 오늘 이어가면 ${streak + 1}일째예요"
                else -> "🔥 오늘 5분 이어가볼까요?"
            }
        return ReminderContent(title = TITLE, body = body)
    }

    /**
     * 다음 발화까지의 지연(기기 로컬 wall-clock 기준, 결정 #6). 오늘 시각이 이미 지났으면 내일로 넘긴다.
     * WorkManager `setInitialDelay` 에 그대로 넣는다.
     */
    fun computeInitialDelay(
        now: ZonedDateTime,
        hour: Int,
        minute: Int,
    ): Duration {
        var next =
            now.withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next)
    }
}

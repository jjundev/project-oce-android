package com.jjundev.oneclickeng.feature.reminder

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.random.Random

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

    /** 완주 화면(gamification-emphasis.md §5)과 동일한 스트릭 임계값. 도달 다음날 리마인더가 축하 문구로 변주된다. */
    val MILESTONE_THRESHOLDS = setOf(1, 3, 7, 14, 30)

    /**
     * body 카피 분기(§5.1 확장). skip-if-studied 로 인해 "오늘 아직 학습 안 한" 사용자에게만 호출된다.
     * - [milestoneStreak] 이 있으면 다른 모든 입력을 무시하고 축하 문구로 즉시 override 한다(희소·1회성이라
     *   랜덤 풀에 섞지 않는다 — 매번 나올 확률로 등장하면 특별함이 옅어진다).
     * - 그 외엔 스트릭 상태로 3분기 base 문구 풀을 고르고, [lastSavedReviewText]/[recommendedSituationTitle]
     *   가 있으면 각각 1개 변형을 풀 끝에 덧붙인 뒤 [pickVariant] 로 하나를 고른다.
     * - [pickVariant] 는 프로덕션에서 `Random.nextInt`, 테스트에서 고정 인덱스를 주입하는 seam이다
     *   (`ReminderScheduler.nowProvider` 와 동일한 스타일).
     */
    @Suppress("LongParameterList")
    fun buildContent(
        streak: Int?,
        lastStudyDate: LocalDate?,
        today: LocalDate,
        milestoneStreak: Int? = null,
        lastSavedReviewText: String? = null,
        recommendedSituationTitle: String? = null,
        pickVariant: (Int) -> Int = { Random.nextInt(it) },
    ): ReminderContent {
        if (milestoneStreak != null) {
            return ReminderContent(title = TITLE, body = milestoneBody(milestoneStreak))
        }
        val branchVariants =
            when {
                streak == null || streak == 0 || lastStudyDate == null ->
                    listOf(
                        "오늘 시작하면 1일째예요",
                        "오늘 5분, 첫 대화 시작해볼까요?",
                        "가볍게 한마디, 오늘 어때요?",
                    )
                ChronoUnit.DAYS.between(lastStudyDate, today) == 1L ->
                    listOf(
                        "🔥 ${streak}일째 — 오늘 이어가면 ${streak + 1}일째예요",
                        "🔥 어제 이어서, 오늘도 5분 가볼까요?",
                        "🔥 ${streak}일째 기록, 오늘도 이어가볼까요?",
                    )
                else ->
                    listOf(
                        "🔥 오늘 5분 이어가볼까요?",
                        "🔥 가볍게 다시 시작해볼까요?",
                        "🔥 오늘 5분이면 충분해요",
                    )
            }
        val pool =
            branchVariants +
                listOfNotNull(
                    lastSavedReviewText?.let { "저장한 표현 '$it', 오늘 한 번 더 써볼까요?" },
                    recommendedSituationTitle?.let { "오늘은 '$it' 어때요?" },
                )
        return ReminderContent(title = TITLE, body = pool[pickVariant(pool.size)])
    }

    /**
     * 마일스톤 축하(gamification-emphasis.md §5 톤 재사용, 결정 #18 임계값). 리마인더는 정의상 "다음날 아직
     * 미방문" 상태에서만 발화하므로, 완주 화면의 즉시 축하와 달리 재참여를 부드럽게 초대하는 톤으로 다시 쓴다.
     */
    private fun milestoneBody(streak: Int): String =
        when (streak) {
            1 -> "🔥 어제 1일째를 시작했어요 — 오늘 이어가볼까요?"
            3 -> "🔥 3일째까지 왔어요 — 오늘도 이어가볼까요?"
            7 -> "🔥 일주일을 채웠어요 — 오늘도 이어가볼까요?"
            14 -> "🔥 2주 연속, 대단해요 — 오늘도 가볼까요?"
            30 -> "🔥 한 달을 채웠어요 — 오늘도 이어가볼까요?"
            else -> "🔥 ${streak}일째, 대단해요 — 오늘도 이어가볼까요?"
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

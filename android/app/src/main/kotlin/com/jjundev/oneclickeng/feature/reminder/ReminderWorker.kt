package com.jjundev.oneclickeng.feature.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jjundev.oneclickeng.feature.reminder.data.ReminderRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

/**
 * 매일 리마인더 발화 worker(notification-reminder.md §4). 캐시만 읽어(오프라인 안전) 스킵/발화를 정하고,
 * **양 분기 모두에서 다음날로 자기재예약**한 뒤 항상 [Result.success] 를 반환한다(실패 반환 시 OneTime 은
 * 재시도 안 함 → 체인 끊김, 결정 #11).
 *
 * 자기교체 footgun 회피: 재예약([ReminderScheduler.schedule] → REPLACE)은 발화/계측을 마친 뒤 마지막
 * 동작으로 수행한다. 실행 중 같은 고유작업을 REPLACE 하면 자신을 취소할 수 있으나, 여기서는 곧 success
 * 로 완료되고 새 인스턴스는 내일 예약이라 안전하며, 앱 시작 헬스체크가 끊긴 체인을 추가로 복구한다.
 */
@HiltWorker
class ReminderWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val repository: ReminderRepository,
        private val notifier: ReminderNotifier,
        private val scheduler: ReminderScheduler,
        private val analytics: ReminderAnalytics,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val config = repository.currentConfig()
            // 토글이 꺼졌으면 발화·재예약 없이 조용히 종료(체인 자연 소멸).
            if (!config.enabled) return Result.success()

            val cache = repository.cacheSnapshot()
            val today = LocalDate.now(ReminderLogic.KST)

            when (ReminderLogic.decideFire(cache.lastStudyDate, today)) {
                ReminderLogic.FireDecision.SKIP_STUDIED_TODAY ->
                    analytics.fireSkipped(REASON_STUDIED_TODAY)
                ReminderLogic.FireDecision.FIRE_CACHE_MISS -> {
                    analytics.fireSkipped(REASON_CACHE_MISS)
                    notifier.post(cache, today)
                }
                ReminderLogic.FireDecision.FIRE ->
                    notifier.post(cache, today)
            }

            // 마지막 동작: 다음날 재예약(발화·스킵 양 분기 공통).
            scheduler.schedule(config.hour, config.minute)
            return Result.success()
        }

        companion object {
            const val REASON_STUDIED_TODAY = "studied_today"
            const val REASON_CACHE_MISS = "cache_miss"
        }
    }

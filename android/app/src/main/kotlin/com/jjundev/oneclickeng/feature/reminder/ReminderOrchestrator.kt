package com.jjundev.oneclickeng.feature.reminder

import com.jjundev.oneclickeng.feature.reminder.data.ReminderCache
import com.jjundev.oneclickeng.feature.reminder.data.ReminderConfig
import com.jjundev.oneclickeng.feature.reminder.data.ReminderStore
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Product seam for local learning reminders. Lifecycle adapters call this module; DataStore,
 * WorkManager, notification posting, and analytics stay behind its implementation.
 */
@Suppress("TooManyFunctions")
interface ReminderOrchestrator {
    val config: Flow<ReminderConfig>

    suspend fun evaluateOptInPrompt(): ReminderPromptDecision

    suspend fun acceptOptIn()

    suspend fun dismissOptIn()

    suspend fun enableReminder()

    suspend fun disableReminder()

    suspend fun setReminderTime(
        hour: Int,
        minute: Int,
    )

    suspend fun markPermissionAsked()

    suspend fun repairSchedule()

    suspend fun handleTimezoneChanged()

    suspend fun runDueReminder(): ReminderRunResult

    suspend fun recordSessionCompleted(
        streak: Int,
        lastStudyDate: LocalDate,
    )

    /** B1: 자동 저장된 표현/단어 텍스트를 리마인더 body 후보로 넘긴다. */
    suspend fun recordSavedReviewText(text: String)

    /** 누적 기록 초기화(M3-09) 시 streak/lastStudyDate 캐시 미러를 비운다. */
    suspend fun clearProgressCache()
}

sealed interface ReminderPromptDecision {
    data object DoNotShow : ReminderPromptDecision

    data class ShowPrompt(val completedSessionCount: Int = PROMPT_SESSION_COUNT) : ReminderPromptDecision

    companion object {
        const val PROMPT_SESSION_COUNT = 2
    }
}

sealed interface ReminderRunResult {
    data object DisabledNoOp : ReminderRunResult

    data object SkippedStudiedToday : ReminderRunResult

    data object FiredCacheMiss : ReminderRunResult

    data object Fired : ReminderRunResult
}

interface ReminderSchedule {
    fun schedule(
        hour: Int,
        minute: Int,
    )

    fun cancel()
}

interface ReminderNotificationSink {
    fun post(
        cache: ReminderCache,
        today: LocalDate,
    )
}

internal object ReminderSkipReason {
    const val STUDIED_TODAY = "studied_today"
    const val CACHE_MISS = "cache_miss"
}

@Singleton
@Suppress("TooManyFunctions")
class DefaultReminderOrchestrator
    @Inject
    constructor(
        private val store: ReminderStore,
        private val schedule: ReminderSchedule,
        private val notificationSink: ReminderNotificationSink,
        private val analytics: ReminderAnalytics,
    ) : ReminderOrchestrator {
        override val config: Flow<ReminderConfig> = store.config

        override suspend fun evaluateOptInPrompt(): ReminderPromptDecision {
            if (!store.shouldPromptOptIn()) return ReminderPromptDecision.DoNotShow
            analytics.promptShown(ReminderPromptDecision.PROMPT_SESSION_COUNT)
            return ReminderPromptDecision.ShowPrompt()
        }

        override suspend fun acceptOptIn() {
            store.markOptInResolved()
        }

        override suspend fun dismissOptIn() {
            store.markOptInResolved()
            analytics.optInResult(enabled = false, referencedStreak = store.cacheSnapshot().streak ?: 0)
        }

        override suspend fun enableReminder() {
            store.markOptInResolved()
            store.setEnabled(true)
            val current = store.currentConfig()
            schedule.schedule(current.hour, current.minute)
            analytics.optInResult(enabled = true, referencedStreak = store.cacheSnapshot().streak ?: 0)
        }

        override suspend fun disableReminder() {
            store.setEnabled(false)
            schedule.cancel()
        }

        override suspend fun setReminderTime(
            hour: Int,
            minute: Int,
        ) {
            store.setTime(hour, minute)
            analytics.timeSet(hour, minute)
            val current = store.currentConfig()
            if (current.enabled) schedule.schedule(current.hour, current.minute)
        }

        override suspend fun markPermissionAsked() {
            store.markPermissionAsked()
        }

        override suspend fun repairSchedule() {
            val current = store.currentConfig()
            if (current.enabled) schedule.schedule(current.hour, current.minute)
        }

        override suspend fun handleTimezoneChanged() {
            repairSchedule()
        }

        override suspend fun runDueReminder(): ReminderRunResult {
            val current = store.currentConfig()
            if (!current.enabled) return ReminderRunResult.DisabledNoOp

            val cache = store.cacheSnapshot()
            val today = LocalDate.now(ReminderLogic.KST)
            val result =
                when (ReminderLogic.decideFire(cache.lastStudyDate, today)) {
                    ReminderLogic.FireDecision.SKIP_STUDIED_TODAY -> {
                        analytics.fireSkipped(ReminderSkipReason.STUDIED_TODAY)
                        ReminderRunResult.SkippedStudiedToday
                    }
                    ReminderLogic.FireDecision.FIRE_CACHE_MISS -> {
                        analytics.fireSkipped(ReminderSkipReason.CACHE_MISS)
                        notificationSink.post(cache, today)
                        if (cache.milestoneStreak != null) store.clearMilestone()
                        ReminderRunResult.FiredCacheMiss
                    }
                    ReminderLogic.FireDecision.FIRE -> {
                        notificationSink.post(cache, today)
                        if (cache.milestoneStreak != null) store.clearMilestone()
                        ReminderRunResult.Fired
                    }
                }
            schedule.schedule(current.hour, current.minute)
            return result
        }

        override suspend fun recordSessionCompleted(
            streak: Int,
            lastStudyDate: LocalDate,
        ) {
            store.recordSessionCompleted(streak, lastStudyDate)
        }

        override suspend fun recordSavedReviewText(text: String) {
            store.recordSavedReviewText(text)
        }

        override suspend fun clearProgressCache() {
            store.resetProgressCache()
        }
    }

package com.jjundev.oneclickeng.feature.reminder.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jjundev.oneclickeng.feature.reminder.di.ReminderPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** 리마인더 on/off + 발화 시각(notification-reminder.md §7). 기본 20:00(P11). */
data class ReminderConfig(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
) {
    companion object {
        const val DEFAULT_HOUR = 20
        const val DEFAULT_MINUTE = 0
        val DISABLED = ReminderConfig(enabled = false, hour = DEFAULT_HOUR, minute = DEFAULT_MINUTE)
    }
}

/** ReminderWorker 가 오프라인에서 읽는 캐시 스냅샷(§4.1). Firestore 미독 — 이 미러만 신뢰. */
data class ReminderCache(
    val lastStudyDate: LocalDate?,
    val streak: Int?,
)

/**
 * M3-07 로컬 리마인더의 로컬 저장소(v1 DataStore-only, Firestore 동기화는 v1.1 — §7).
 *
 * **디커플 경계(결정 #16·#19):** 세션 완료 신호는 이 저장소가 소유하지 않는다. 세션을 확정하는 흐름
 * (M2-02 요약 확정 경로)이 [recordSessionCompleted] 를 호출하고, streak/lastStudyDate 값은 M3-05
 * 게임화가 산출해 넘긴다. M3-07 은 이 계약만 노출하므로 호출자(M2-02)가 아직 없어도 self-contained 하게
 * 머지·검증된다.
 *
 * 이 seam 은 [com.jjundev.oneclickeng.feature.reminder.ReminderOrchestrator] 내부 저장소 adapter 이다.
 * UI/Worker/Application 같은 lifecycle adapter 는 이 저장소가 아니라 product seam 인 orchestrator 를 주입한다.
 */
interface ReminderStore {
    /** 설정 행/스케줄러가 관측하는 라이브 설정 스트림. */
    val config: Flow<ReminderConfig>

    /** 단발 스냅샷(앱 시작 헬스체크 등). */
    suspend fun currentConfig(): ReminderConfig

    /**
     * 세션 완주 1회당 호출(호출자=M2-02 요약 확정 경로). 누적 완주수 +1, 그리고 스킵/카피 분기용
     * 캐시(streak·lastStudyDate)를 KST 미러링한다. streak/lastStudyDate 는 M3-05 산출값.
     */
    suspend fun recordSessionCompleted(
        streak: Int,
        lastStudyDate: LocalDate,
    )

    /** opt-in 시트를 지금 노출해야 하나(2번째 완주 && 미해소). 1회 평가용 스냅샷. */
    suspend fun shouldPromptOptIn(): Boolean

    /** opt-in 멱등 플래그 set(시트에서 켜기/닫기/설정에서 켜기 — 어느 경로든 재제안 종료, §2.2 D13). */
    suspend fun markOptInResolved()

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setTime(
        hour: Int,
        minute: Int,
    )

    /** 영구거부 판정 보조 플래그(§3.4). 시스템 다이얼로그를 한 번이라도 띄웠으면 true. */
    suspend fun wasPermissionAsked(): Boolean

    suspend fun markPermissionAsked()

    /** Worker 가 읽는 캐시 스냅샷. */
    suspend fun cacheSnapshot(): ReminderCache

    /**
     * 누적 기록 초기화(M3-09, FR-22) 시 streak/lastStudyDate 캐시 미러를 비운다. 초기화로 streak=0 이 되므로
     * 리마인더 카피가 stale streak 을 참조하지 않게 한다. on/off·시각 설정은 건드리지 않는다.
     */
    suspend fun resetProgressCache()
}

/** DataStore 구현. 누락 키는 기본값으로 폴백(리마인더 off·20:00·캐시 부재). */
@Suppress("TooManyFunctions")
@Singleton
class DataStoreReminderRepository
    @Inject
    constructor(
        @ReminderPrefs private val dataStore: DataStore<Preferences>,
    ) : ReminderStore {
        override val config: Flow<ReminderConfig> = dataStore.data.map(::toConfig)

        override suspend fun currentConfig(): ReminderConfig = toConfig(dataStore.data.first())

        override suspend fun recordSessionCompleted(
            streak: Int,
            lastStudyDate: LocalDate,
        ) {
            dataStore.edit { prefs ->
                val count = prefs[KEY_COMPLETED_SESSIONS] ?: 0
                prefs[KEY_COMPLETED_SESSIONS] = count + 1
                prefs[KEY_STREAK_CACHE] = streak
                prefs[KEY_LAST_STUDY_DATE_CACHE] = lastStudyDate.toString()
            }
        }

        override suspend fun shouldPromptOptIn(): Boolean {
            val prefs = dataStore.data.first()
            val count = prefs[KEY_COMPLETED_SESSIONS] ?: 0
            val resolved = prefs[KEY_OPT_IN_RESOLVED] ?: false
            return count == 2 && !resolved
        }

        override suspend fun markOptInResolved() {
            dataStore.edit { it[KEY_OPT_IN_RESOLVED] = true }
        }

        override suspend fun setEnabled(enabled: Boolean) {
            dataStore.edit { it[KEY_ENABLED] = enabled }
        }

        override suspend fun setTime(
            hour: Int,
            minute: Int,
        ) {
            dataStore.edit {
                it[KEY_HOUR] = hour
                it[KEY_MINUTE] = minute
            }
        }

        override suspend fun wasPermissionAsked(): Boolean = dataStore.data.first()[KEY_PERMISSION_ASKED] ?: false

        override suspend fun markPermissionAsked() {
            dataStore.edit { it[KEY_PERMISSION_ASKED] = true }
        }

        override suspend fun cacheSnapshot(): ReminderCache {
            val prefs = dataStore.data.first()
            return ReminderCache(
                lastStudyDate =
                    prefs[KEY_LAST_STUDY_DATE_CACHE]?.let { iso ->
                        runCatching { LocalDate.parse(iso) }.getOrNull()
                    },
                streak = prefs[KEY_STREAK_CACHE],
            )
        }

        override suspend fun resetProgressCache() {
            dataStore.edit { prefs ->
                prefs.remove(KEY_STREAK_CACHE)
                prefs.remove(KEY_LAST_STUDY_DATE_CACHE)
            }
        }

        private fun toConfig(prefs: Preferences): ReminderConfig =
            ReminderConfig(
                enabled = prefs[KEY_ENABLED] ?: false,
                hour = prefs[KEY_HOUR] ?: ReminderConfig.DEFAULT_HOUR,
                minute = prefs[KEY_MINUTE] ?: ReminderConfig.DEFAULT_MINUTE,
            )

        companion object {
            val KEY_ENABLED = booleanPreferencesKey("reminder_enabled")
            val KEY_HOUR = intPreferencesKey("reminder_hour")
            val KEY_MINUTE = intPreferencesKey("reminder_minute")
            val KEY_LAST_STUDY_DATE_CACHE = stringPreferencesKey("reminder_last_study_date_cache")
            val KEY_STREAK_CACHE = intPreferencesKey("reminder_streak_cache")
            val KEY_COMPLETED_SESSIONS = intPreferencesKey("reminder_completed_session_count")
            val KEY_OPT_IN_RESOLVED = booleanPreferencesKey("reminder_opt_in_resolved")
            val KEY_PERMISSION_ASKED = booleanPreferencesKey("reminder_notif_permission_asked")
        }
    }

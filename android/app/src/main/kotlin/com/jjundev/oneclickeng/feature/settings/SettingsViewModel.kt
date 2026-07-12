package com.jjundev.oneclickeng.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjundev.oneclickeng.core.auth.AccountRepository
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.core.auth.PendingMergeStore
import com.jjundev.oneclickeng.core.auth.ProfileRepository
import com.jjundev.oneclickeng.core.settings.TtsQuality
import com.jjundev.oneclickeng.core.settings.TtsSettingsRepository
import com.jjundev.oneclickeng.feature.gamification.StudytimeRepository
import com.jjundev.oneclickeng.feature.reminder.ReminderOrchestrator
import com.jjundev.oneclickeng.feature.settings.data.CardPurgeRepository
import com.jjundev.oneclickeng.feature.settings.data.PurgeScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 설정 탭 오케스트레이터(M3-09). 음성/알림/데이터/계정 동작을 기존 저장소 seam 으로 위임하고 화면 상태를 노출한다
 * (RecordsViewModel 관례). 위험 동작(초기화·정리·삭제)은 실행만 담당하고, 확인 UI(C1/C2)·권한 요청은 Composable
 * 이 소유한다. Google 연결/이관 재시도는 별도 GoogleLinkViewModel 을 재사용하므로 여기 없다.
 */
@Suppress("TooManyFunctions", "LongParameterList")
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val profileRepository: ProfileRepository,
        private val ttsSettings: TtsSettingsRepository,
        private val reminderOrchestrator: ReminderOrchestrator,
        private val studytimeRepository: StudytimeRepository,
        private val cardPurgeRepository: CardPurgeRepository,
        private val accountRepository: AccountRepository,
        private val pendingMergeStore: PendingMergeStore,
        private val analytics: SettingsAnalytics,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SettingsUiState())
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        private var nicknameSaveJob: Job? = null

        init {
            // 음성·알림은 라이브 Flow. 첫 방출 시 loading=false.
            viewModelScope.launch {
                combine(ttsSettings.settings, reminderOrchestrator.config) { tts, reminder -> tts to reminder }
                    .collect { (tts, reminder) ->
                        _uiState.update {
                            it.copy(
                                ttsQuality = tts.quality,
                                speechRate = tts.speechRate,
                                ttsMuted = tts.muted,
                                reminderEnabled = reminder.enabled,
                                reminderHour = reminder.hour,
                                reminderMinute = reminder.minute,
                                loading = false,
                            )
                        }
                    }
            }
            loadNickname()
            refreshAccount()
        }

        /** 화면 재진입(Google 연결/이관 후) 시 계정 분기·이관 재시도 행을 다시 읽는다. */
        fun refreshAccount() {
            viewModelScope.launch {
                val guest = accountRepository.isGuest()
                val pending = runCatching { pendingMergeStore.get() != null }.getOrDefault(false)
                _uiState.update { it.copy(isGuest = guest, showRetryMerge = pending) }
            }
        }

        private fun loadNickname() {
            viewModelScope.launch {
                val uid = authRepository.currentUid ?: return@launch
                val nick = runCatching { profileRepository.readNickname(uid) }.getOrNull().orEmpty()
                _uiState.update { it.copy(nickname = nick) }
            }
        }

        // ----- 프로필 -----

        /** 닉네임 입력(1~20자 clamp, 빈값 허용). 낙관적 로컬 갱신 후 디바운스 큐 write(오프라인 안전). */
        fun onNicknameChange(raw: String) {
            val value = raw.take(MAX_NICKNAME)
            _uiState.update { it.copy(nickname = value) }
            nicknameSaveJob?.cancel()
            nicknameSaveJob =
                viewModelScope.launch {
                    delay(NICKNAME_SAVE_DEBOUNCE_MS)
                    val uid = authRepository.currentUid ?: return@launch
                    runCatching { profileRepository.saveNickname(uid, value.trim()) }
                }
        }

        // ----- 음성 -----

        fun onQualityChange(quality: TtsQuality) {
            viewModelScope.launch { ttsSettings.setQuality(quality) }
            analytics.ttsQualityChanged(quality.name)
        }

        /** 슬라이더 드래그 종료 시 1회 호출(드래그 중은 Composable 로컬 상태). */
        fun onSpeedChange(rate: Float) {
            viewModelScope.launch { ttsSettings.setSpeechRate(rate) }
            analytics.ttsSpeedChanged(rate)
        }

        fun onMuteChange(muted: Boolean) {
            viewModelScope.launch { ttsSettings.setMuted(muted) }
            analytics.muteToggled(muted)
        }

        // ----- 알림 -----

        /** POST_NOTIFICATIONS 허용 후(또는 12 이하) 호출. 스케줄·계측은 orchestrator 소유. */
        fun enableReminder() {
            viewModelScope.launch { reminderOrchestrator.enableReminder() }
        }

        fun disableReminder() {
            viewModelScope.launch { reminderOrchestrator.disableReminder() }
        }

        /** 시스템 권한 다이얼로그를 띄웠음을 기록(영구거부 판정 보조). */
        fun markNotificationPermissionAsked() {
            viewModelScope.launch { reminderOrchestrator.markPermissionAsked() }
        }

        fun onReminderTimeChange(
            hour: Int,
            minute: Int,
        ) {
            viewModelScope.launch { reminderOrchestrator.setReminderTime(hour, minute) }
        }

        // ----- 데이터 관리 -----

        /** 정리 범위 선택(바텀시트) → 실행 직전 재쿼리한 건수로 확인 다이얼로그를 띄운다. */
        /** 정리 시트 오픈 시 3범위 카운트를 선로딩(배지 표기용). */
        fun loadPurgeCounts() {
            viewModelScope.launch {
                val counts = collectPurgeCounts(cardPurgeRepository)
                _uiState.update { it.copy(purgeCounts = counts) }
            }
        }

        fun selectPurgeScope(scope: PurgeScope) {
            viewModelScope.launch {
                val count = cardPurgeRepository.count(scope)
                _uiState.update { it.copy(purgeConfirm = PurgeConfirm(scope, count)) }
            }
        }

        fun dismissPurgeConfirm() {
            _uiState.update { it.copy(purgeConfirm = null) }
        }

        /** 확인 다이얼로그 확정 → 일괄 톰스톤. */
        fun confirmPurge() {
            val scope = _uiState.value.purgeConfirm?.scope ?: return
            _uiState.update { it.copy(purgeConfirm = null, purgeInFlight = true) }
            viewModelScope.launch {
                val result = runCatching { cardPurgeRepository.purge(scope) }
                _uiState.update {
                    it.copy(
                        purgeInFlight = false,
                        message =
                            result.fold(
                                onSuccess = { count ->
                                    analytics.cardsPurged(scope.name, count)
                                    SettingsMessage.CardsPurged(count)
                                },
                                onFailure = { SettingsMessage.PurgeFailed },
                            ),
                    )
                }
            }
        }

        /** 누적 기록 초기화(단일 확인 후). 로컬-우선 리셋 + resetMetrics 콜러블. */
        fun resetMetrics() {
            _uiState.update { it.copy(metricsResetInFlight = true) }
            viewModelScope.launch {
                val result = runCatching { studytimeRepository.resetMetrics() }
                result.onSuccess { analytics.metricsReset() }
                _uiState.update {
                    it.copy(
                        metricsResetInFlight = false,
                        message = if (result.isFailure) SettingsMessage.MetricsResetFailed else it.message,
                    )
                }
            }
        }

        // ----- 계정 -----

        /** 로그아웃(확인 후). 성공 시 AccountResetBus 가 앱을 재부트한다(화면 이탈). */
        fun logout() {
            analytics.logout()
            viewModelScope.launch {
                runCatching { accountRepository.signOut() }
                    .onFailure { _uiState.update { s -> s.copy(message = SettingsMessage.LogoutFailed) } }
            }
        }

        /** 계정 삭제(2단계 확인 후). 성공 시 AccountResetBus 가 신규 게스트로 재부트한다. */
        fun deleteAccount() {
            _uiState.update { it.copy(deleteInFlight = true) }
            viewModelScope.launch {
                runCatching { accountRepository.deleteAccount() }
                    .onSuccess { analytics.accountDeleted() }
                    .onFailure {
                        _uiState.update { s -> s.copy(deleteInFlight = false, message = SettingsMessage.DeleteFailed) }
                    }
            }
        }

        fun consumeMessage() {
            _uiState.update { it.copy(message = null) }
        }

        private companion object {
            const val MAX_NICKNAME = 20
            const val NICKNAME_SAVE_DEBOUNCE_MS = 500L
        }
    }

/** 3범위 카운트 수집(정리 시트 배지용). 각 범위 실패는 0으로 강등(오프라인/권한 안전). */
suspend fun collectPurgeCounts(repo: CardPurgeRepository): Map<PurgeScope, Int> =
    PurgeScope.entries.associateWith { scope ->
        runCatching { repo.count(scope) }.getOrDefault(0)
    }

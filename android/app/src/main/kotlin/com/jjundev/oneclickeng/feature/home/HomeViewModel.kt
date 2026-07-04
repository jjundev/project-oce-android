package com.jjundev.oneclickeng.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjundev.oneclickeng.core.network.ConnectivityMonitor
import com.jjundev.oneclickeng.feature.gamification.GamificationTime
import com.jjundev.oneclickeng.feature.gamification.data.StudytimeStore
import com.jjundev.oneclickeng.feature.session.resume.SessionLimitHolder
import com.jjundev.oneclickeng.feature.session.resume.SessionSnapshotStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 홈 탭 상태 소유자(M3-08). 학습 시작 허브의 낮은 비중 보조들을 조립한다:
 * - 게임화 스트립: [StudytimeStore.snapshot] (suspend) → 오늘 학습시간 라벨 + streak.
 * - 오프라인: [ConnectivityMonitor] (CTA 비활성 + 글로벌 배너).
 * - 미완 복귀: [SessionSnapshotStore.recoverable] (durable, §2.5).
 * - at-limit: [SessionLimitHolder.freshRemaining] (fresh==0 일 때만 고지, unknown→억제).
 *
 * 접힌 세션 설정의 기본 레벨은 홈이 아니라 설정 화면([com.jjundev.oneclickeng.feature.home.settings.SessionSettingsViewModel])이
 * 직접 `profile.level` 을 해소한다(#6) — 홈 CTA 는 레벨을 실어 보내지 않아 미해소 중 누출이 없다.
 *
 * [limitHolder] 를 주입하는 것만으로 그 Singleton 이 인스턴스화돼 코디네이터 상태 관측을 시작한다 — 홈은
 * 부트 시작 목적지라 어떤 생성 시도보다 먼저 살아있다.
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val studytimeStore: StudytimeStore,
        connectivityMonitor: ConnectivityMonitor,
        snapshotStore: SessionSnapshotStore,
        limitHolder: SessionLimitHolder,
        private val analytics: HomeAnalytics,
    ) : ViewModel() {
        /** 게임화 스냅샷(라벨+streak). null=미로딩. suspend 읽기라 flow 로 승격해 combine 에 넣는다. */
        private val gamification = MutableStateFlow<Gamification?>(null)

        val uiState: StateFlow<HomeUiState> =
            combine(
                connectivityMonitor.isOnline,
                snapshotStore.recoverable,
                limitHolder.freshRemaining,
                gamification,
            ) { online, resume, remaining, gami ->
                HomeUiState(
                    studyTimeLabel = gami?.studyTimeLabel,
                    streak = gami?.streak ?: 0,
                    isOnline = online,
                    hasResume = resume,
                    // fresh remaining 이 관측됐고(non-null) 그 값이 0 일 때만 at-limit(H6, unknown→억제).
                    atLimit = remaining == 0,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

        init {
            analytics.homeView()
            viewModelScope.launch {
                val snapshot = studytimeStore.snapshot()
                gamification.value =
                    Gamification(
                        studyTimeLabel = GamificationTime.studyTimeLabel(snapshot.todaySeconds),
                        streak = snapshot.streak,
                    )
            }
        }

        /** CTA 탭 계측(내비는 소비처 람다가 소유). */
        fun onCtaTap() = analytics.homeCtaTap()

        fun onResumeContinue() = analytics.resumeContinue()

        fun onResumeStartNew() = analytics.resumeStartNew()

        fun onOfflineBlocked() = analytics.offlineBlocked()

        private data class Gamification(
            val studyTimeLabel: String,
            val streak: Int,
        )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

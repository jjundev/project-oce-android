package com.jjundev.oneclickeng.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.core.auth.ProfileRepository
import com.jjundev.oneclickeng.core.connectivity.Connectivity
import com.jjundev.oneclickeng.core.connectivity.ConnectivityObserver
import com.jjundev.oneclickeng.core.session.SessionLevel
import com.jjundev.oneclickeng.feature.gamification.GamificationTime
import com.jjundev.oneclickeng.feature.gamification.data.StudytimeStore
import com.jjundev.oneclickeng.feature.home.topic.Topic
import com.jjundev.oneclickeng.feature.home.topic.TopicCatalog
import com.jjundev.oneclickeng.feature.session.resume.SessionLimitHolder
import com.jjundev.oneclickeng.feature.session.resume.SessionSnapshotStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 홈 탭 상태 소유자(M3-08 → 프로토 홈 허브 정합). 학습 시작 허브의 상태를 조립한다:
 * - 게임화 스트립: [StudytimeStore.snapshot] (suspend) → 오늘 학습시간 라벨 + streak.
 * - 오프라인: [ConnectivityObserver] (M4-04 단일 연결성 소스) → Boolean 파생(CTA 비활성 + 글로벌 배너).
 * - 미완 복귀: [SessionSnapshotStore.resumeInfo] (durable, §2.5).
 * - at-limit: [SessionLimitHolder.freshRemaining] (fresh==0 일 때만 고지, unknown→억제).
 * - 세션 설정(프로토 인라인 패널): 레벨은 `profile.level` 을 **홈 VM 이 직접** 해소한다(#6 이관 —
 *   세션 설정 화면 폐기로 해소 주체가 홈으로 왔다. null 동안 시작 차단 → easy 누출 없음). 길이 기본 5턴.
 * - 선택 상황(프로토 selectedTopic): 히어로에 실리는 상황. 기본=오늘 추천 1순위. 시트/추천 행이 갱신.
 * - 추천 상황: [TopicCatalog.recommended] 결정적 순환(KST epochDay + 새로고침 카운트) 5개 중 선택 상황을
 *   제외한 4개(프로토: 히어로=선택 상황, 리스트=나머지).
 *
 * [limitHolder] 를 주입하는 것만으로 그 Singleton 이 인스턴스화돼 코디네이터 상태 관측을 시작한다 — 홈은
 * 부트 시작 목적지라 어떤 생성 시도보다 먼저 살아있다.
 */
// LongParameterList: DI 허브 상태 소스 5종 + profile.level 해소(#6 이관, 세션 설정 화면 폐기) 조립.
// TooManyFunctions: 허브가 소유한 액션 표면(CTA·resume·레벨/길이·상황 선택·새로고침)이 각각 얇은 위임.
@Suppress("LongParameterList", "TooManyFunctions")
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val studytimeStore: StudytimeStore,
        connectivity: ConnectivityObserver,
        private val snapshotStore: SessionSnapshotStore,
        limitHolder: SessionLimitHolder,
        private val authRepository: AuthRepository,
        private val profileRepository: ProfileRepository,
        private val analytics: HomeAnalytics,
    ) : ViewModel() {
        /** 게임화 스냅샷(라벨+streak). null=미로딩. suspend 읽기라 flow 로 승격해 combine 에 넣는다. */
        private val gamification = MutableStateFlow<Gamification?>(null)

        /** profile.level 해소값(#6). null=미해소(시작 차단), 사용자가 세그먼트로 바꾸면 override 가 우선. */
        private val defaultLevel = MutableStateFlow<String?>(null)
        private val levelOverride = MutableStateFlow<String?>(null)
        private val length = MutableStateFlow(DEFAULT_LENGTH)

        /** 오늘의 추천 회전 키(KST) — 같은 날 같은 창, 새로고침이 창을 전진시킨다(TopicCatalog.recommended). */
        private val dayIndex: Long = GamificationTime.kstEpochDay(System.currentTimeMillis())
        private val refreshCount = MutableStateFlow(0)

        /** 선택 상황(프로토 selectedTopic). 기본=오늘 추천 1순위. */
        private val selected =
            MutableStateFlow(TopicCatalog.recommended(dayIndex, 0, RECOMMEND_POOL).first().toSelected())

        private val sessionSetup =
            combine(defaultLevel, levelOverride, length, refreshCount, selected) {
                default, override, len, refresh, sel ->
                SessionSetup(
                    level = override ?: default,
                    length = len,
                    selected = sel,
                    situations =
                        TopicCatalog
                            .recommended(dayIndex, refresh, RECOMMEND_POOL)
                            .filter { it.id != sel.topicId }
                            .take(RECOMMEND_VISIBLE)
                            .map { HomeSituation(it.id, it.titleKo, it.icon, it.promptSeed) },
                )
            }

        val uiState: StateFlow<HomeUiState> =
            combine(
                connectivity.state.map { it == Connectivity.Online },
                snapshotStore.resumeInfo,
                limitHolder.freshRemaining,
                gamification,
                sessionSetup,
            ) { online, resume, remaining, gami, setup ->
                HomeUiState(
                    studyMinutes = gami?.studyMinutes,
                    streak = gami?.streak ?: 0,
                    isOnline = online,
                    hasResume = resume != null,
                    resumeTopic = resume?.topicTitle,
                    resumeTurn = resume?.doneTurns ?: 0,
                    resumeTotalTurns = resume?.totalTurns ?: 0,
                    // fresh remaining 이 관측됐고(non-null) 그 값이 0 일 때만 at-limit(H6, unknown→억제).
                    atLimit = remaining == 0,
                    level = setup.level,
                    length = setup.length,
                    selectedSituation = setup.selected,
                    situations = setup.situations,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

        init {
            analytics.homeView()
            refreshOnResume()
            viewModelScope.launch {
                val level =
                    runCatching { authRepository.currentUid?.let { profileRepository.readLevel(it) } }
                        .onFailure { Log.d(TAG, "readLevel failed — defaulting normal: ${it.message}") }
                        .getOrNull()
                defaultLevel.value = level ?: FALLBACK_LEVEL
            }
        }

        /**
         * 화면 재개마다 다시 부른다([RecordsResumeEffect] 선례). [gamification] 은 init 1회성 suspend 읽기라,
         * 이 훅이 없으면 세션 완료 직후 홈으로 돌아와도 stale 한 "오늘 0분"이 남는다(HomeViewModel 은
         * 백스택 엔트리에 스코프돼 탭 왕복으로 재생성되지 않음).
         */
        fun refreshOnResume() {
            viewModelScope.launch {
                val snapshot = studytimeStore.snapshot()
                gamification.value =
                    Gamification(
                        studyMinutes = (snapshot.todaySeconds / 60).toInt(),
                        streak = snapshot.streak,
                    )
            }
        }

        /** CTA 탭 계측(내비는 소비처 람다가 소유). */
        fun onCtaTap() = analytics.homeCtaTap()

        fun onResumeContinue() = analytics.resumeContinue()

        /** "+ 새 대화 시작"(프로토 discardSnapshot) — 스냅샷 폐기로 홈을 새 대화 모드로 되돌린다(내비 없음). */
        fun onResumeStartNew() {
            analytics.resumeStartNew()
            viewModelScope.launch { snapshotStore.clear() }
        }

        fun onOfflineBlocked() = analytics.offlineBlocked()

        fun setLevel(level: String) {
            levelOverride.value = level
        }

        fun setLength(turns: Int) {
            length.value = clampLength(turns)
        }

        /** 슬라이더 드래그 커밋(onValueChangeFinished) — 틱마다가 아닌 정착당 1회 session_setting_changed. */
        fun onSessionSettingCommitted() {
            analytics.sessionSettingChanged(
                level = levelOverride.value ?: defaultLevel.value ?: FALLBACK_LEVEL,
                length = length.value,
            )
        }

        /** 시트/추천 행에서 카탈로그 상황 선택(프로토 pickTopic·startTopic 공용 선택 갱신). */
        fun selectSituation(topic: Topic) {
            selected.value = topic.toSelected()
            analytics.topicSelected(topicId = topic.id, custom = false)
        }

        /** 카탈로그 id 로 상황 선택 — 시트/추천 행 콜백용(미지 id 는 무시). */
        fun selectSituationById(id: String) {
            TopicCatalog.ALL.firstOrNull { it.id == id }?.let {
                selected.value = it.toSelected()
                analytics.topicSelected(topicId = it.id, custom = false)
            }
        }

        /** 직접 입력 상황 선택(프로토 pickCustom) — 입력 원문이 라벨이자 promptSeed. */
        fun selectCustomSituation(text: String) {
            selected.value = SelectedSituation(topicId = null, labelKo = text, promptSeed = text)
            analytics.topicSelected(topicId = null, custom = true)
        }

        /** 추천 새로고침(프로토 refreshRecs) — 결정적 창 전진. */
        fun refreshSituations() {
            refreshCount.value += 1
        }

        private data class Gamification(
            val studyMinutes: Int,
            val streak: Int,
        )

        private data class SessionSetup(
            val level: String?,
            val length: Int,
            val selected: SelectedSituation,
            val situations: List<HomeSituation>,
        )

        companion object {
            const val TAG = "HomeViewModel"
            const val STOP_TIMEOUT_MS = 5_000L
            const val MIN_LENGTH = 6
            const val MAX_LENGTH = 20
            const val LENGTH_STEP = 2
            const val DEFAULT_LENGTH = 10
            val FALLBACK_LEVEL = SessionLevel.NORMAL.token

            /** 추천 풀 5개 → 선택 상황 제외 후 4개 노출(프로토 홈 리스트 행 수). */
            const val RECOMMEND_POOL = 5
            const val RECOMMEND_VISIBLE = 4

            /** 임의 정수를 짝수 6..20 로 스냅(슬라이더 밖 입력·구버전 값 방어). */
            fun clampLength(turns: Int): Int {
                val clamped = turns.coerceIn(MIN_LENGTH, MAX_LENGTH)
                return clamped - ((clamped - MIN_LENGTH) % LENGTH_STEP)
            }

            fun Topic.toSelected() = SelectedSituation(topicId = id, labelKo = titleKo, promptSeed = promptSeed)
        }
    }

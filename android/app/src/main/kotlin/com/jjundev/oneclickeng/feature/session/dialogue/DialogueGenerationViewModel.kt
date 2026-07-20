package com.jjundev.oneclickeng.feature.session.dialogue

import androidx.lifecycle.ViewModel
import com.jjundev.oneclickeng.core.connectivity.OfflineAnalytics
import com.jjundev.oneclickeng.core.network.LimitAnalytics
import com.jjundev.oneclickeng.core.network.WaitQuizAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.SessionFunnelAnalytics
import com.jjundev.oneclickeng.feature.session.dialogue.quiz.QuizBank
import com.jjundev.oneclickeng.feature.session.resume.SessionSnapshotStore
import com.jjundev.oneclickeng.feature.session.tts.TtsPlaybackCoordinator
import com.jjundev.oneclickeng.feature.session.turn.SpeakerDirectory
import com.jjundev.oneclickeng.feature.session.turn.nextOpponentEnglish
import com.jjundev.oneclickeng.ui.component.QuizItem
import com.jjundev.oneclickeng.ui.component.selectLimitSurface
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Wires the M1-01 dialogue-generation surface: bridges [DialogueGenerationCoordinator]'s state to the
 * screen, selects the WaitQuiz tier from the generation level (first session forced to `easy`,
 * loading-quiz-interstitial.md §9), routes quiz answers to the [WaitQuizAnalytics] seam, and exposes
 * the local kill-switch ([LoadingQuizConfig]). Without this bridge the screen, coordinator, analytics,
 * and quiz bank are unconnected parts — this is the connective layer M1-01 owns.
 *
 * Scoped to the caller's ViewModelStore, so an in-flight generation and its accumulated turns survive
 * configuration changes (process-death survival remains M1-08's concern).
 */
@Suppress("LongParameterList") // DI: SSE 브리지 + 퀴즈/분석 seam + durable 스냅샷 폐기(appScope) 조립.
@HiltViewModel
class DialogueGenerationViewModel
    @Inject
    constructor(
        private val coordinator: DialogueGenerationCoordinator,
        private val tts: TtsPlaybackCoordinator,
        private val quizBank: QuizBank,
        private val analytics: WaitQuizAnalytics,
        private val limitAnalytics: LimitAnalytics,
        private val snapshotStore: SessionSnapshotStore,
        private val appScope: CoroutineScope,
        private val offlineAnalytics: OfflineAnalytics,
        private val sessionFunnel: SessionFunnelAnalytics,
        loadingQuizConfig: LoadingQuizConfig,
    ) : ViewModel() {
        val state: StateFlow<DialogueGenState> = coordinator.state

        /** WaitQuiz kill-switch (default-on). Consumed by the screen to gate the quiz surface. */
        val quizEnabled: Boolean = loadingQuizConfig.loadingQuizEnabled

        private val _quizItems = MutableStateFlow<List<QuizItem>>(emptyList())
        val quizItems: StateFlow<List<QuizItem>> = _quizItems.asStateFlow()

        // 첫 상대 대사 음성이 재생 가능(캐시됨)해졌는지. 로딩 화면이 이 값이 true 가 될 때까지 "준비 중"을
        // 유지해 첫 대사가 채팅 진입 즉시 재생되게 한다(생성 완료만으론 부족 — TTS 합성 시간이 지배적).
        // 워밍할 게 없으면(DEVICE·음소거) 즉시 true 라 추가 대기 없음.
        private val _firstLineReady = MutableStateFlow(false)
        val firstLineReady: StateFlow<Boolean> = _firstLineReady.asStateFlow()

        // Monotonic position of the answered card within this generation, for `card_index`
        // (analytics-events.md §6.6) — the WaitQuiz callback does not carry the card position.
        private var answeredCount = 0

        // Whether this generation is the onboarding first-session gate (M3-02). Drives the limit
        // surface for BOTH the panel render (via the screen) and the `limit_reached` analytics event
        // (via [onLimitReached]) so the two never disagree.
        private var isOnboarding = false

        // 마지막 start 파라미터 + pre-flight 오프라인 게이트 여부(M4-04). pre-flight 로 막혔으면 재시도는
        // 스트림 재개가 아니라 새 start(연결성 재확인)로 가야 한다 — 아무것도 전송하지 않았으므로 usage 중복 없음.
        private var lastStart: StartParams? = null
        private var preflightBlocked = false

        // onConversationStarted() 는 auto-start 와 CTA 탭 두 경로 모두에서 불릴 수 있어(Route 가 콜백을
        // 감싸는 위치가 두 진입점을 모두 통과) 한 번만 계측되게 이 가드로 막는다.
        private var conversationStartedLogged = false

        init {
            // 이 코디네이터는 process @Singleton 이라 직전 세션의 sticky Ready 가 남는다. 새 생성 VM 이 뜰 때
            // 그 잔여 상태를 Idle 로 되돌려, 생성 화면이 stale Ready 를 읽고 대기 퀴즈를 건너뛰는 걸 막는다
            // (온보딩=첫 생성이라 원래 Idle → 정상, 2번째+ 생성만 문제였음). start() 는 곧이어 Generating 으로
            // 전이하므로 정상 <1s fast-ready 자동 스킵은 그대로 보존된다.
            coordinator.reset()
        }

        /** Begin generation and load the tier's quiz items (first session → easy). */
        fun start(
            level: String,
            topic: String,
            length: Int,
            firstSession: Boolean,
            isOnboarding: Boolean = false,
        ) {
            this.isOnboarding = isOnboarding
            lastStart = StartParams(level, topic, length, firstSession)
            // pre-flight 게이트(M4-04, exception-states.md 결정 #4): 연결성 소유는 코디네이터 하나다. 오프라인
            // 이면 [StartOutcome.OfflineGated] 를 받아 퀴즈를 스킵하고 `offline_blocked_action` 만 계측한다
            // (핵심 루프=온라인 필수, 로그인된 캐시보유 사용자에만 도달).
            val outcome = coordinator.start(level, topic, length, firstSession)
            // 온보딩 첫 세션 생성 퍼널의 진입점(M4-01b §4). idempotencyKeyPresent = 실제로 전송이 시작됐는지
            // (Started) — 오프라인 게이트로 막힌 시도는 present=false 로 구분된다.
            if (isOnboarding) sessionFunnel.firstSessionGenerationStarted(outcome == StartOutcome.Started)
            when (outcome) {
                StartOutcome.OfflineGated -> {
                    preflightBlocked = true
                    _quizItems.value = emptyList()
                    offlineAnalytics.offlineBlocked(OFFLINE_GATE_SURFACE)
                }
                StartOutcome.Started -> {
                    preflightBlocked = false
                    val tier = if (firstSession) FIRST_SESSION_TIER else level
                    _quizItems.value = if (quizEnabled) quizBank.forTier(tier) else emptyList()
                    answeredCount = 0
                    // 세션이 **실제로 시작**됐을 때만 직전 미완 세션 durable 스냅샷 폐기(§2.5 "새 세션 시작
                    // 시에만 폐기"). 오프라인 게이트로 막혔으면 아무 세션도 시작 안 됐으므로 이전 스냅샷을
                    // 보존한다(온라인 복귀 후 이어하기 가능). appScope 로 실행해 화면 이탈로 취소되지 않게 한다.
                    appScope.launch { snapshotStore.clear() }
                }
            }
        }

        /** 첫 상대 대사 오디오를 서버 합성해 캐시에 채우고, 준비되면 [firstLineReady] 를 true 로 올린다(Route 가
         *  Ready 도착 시 호출, suspend). 로딩 화면이 이 신호까지 "준비 중"을 유지하므로 채팅 진입 즉시 첫 대사가
         *  재생된다. TtsPlaybackCoordinator 는 @Singleton 이라 이 VM 이 파괴돼도(생성→채팅 nav pop) 캐시가 살아
         *  있어 채팅의 speakOpponent 가 같은 라인을 즉시 재생한다(같은 sessionId→같은 gender→같은 캐시 키).
         *  [awaitWarm] 은 DEVICE·음소거면 즉시 반환하고, SERVER 면 합성 완료까지 대기한다 — 상한은 코디네이터의
         *  [TtsPlaybackCoordinator.SYNTH_WATCHDOG_MS](16s)라 콜드 첫 호출이면 로딩 화면이 최대 그만큼 유지된다
         *  (8s 가 아니다 — 8s 는 라이브 재생의 단말 폴백 상한). 합성 실패여도 게이트는 열어 진입을 막지 않는다.
         *  멱등(이미 준비됐으면 no-op).
         *
         *  전제: 이 await 중에 [TtsPlaybackCoordinator.clearCache] 가 불리면 in-flight deferred 가 취소되며
         *  CancellationException 이 여기로 전파돼 [firstLineReady] 가 영영 false 로 남는다(로딩 화면 고착).
         *  현재 clearCache 호출자는 채팅 VM 의 onCleared 뿐이고 생성→채팅 pop 이후라 도달 불가하다. 생성 측에
         *  clearCache 호출자를 추가한다면 이 await 를 `catch (e: CancellationException) { coroutineContext.ensureActive() }`
         *  로 감싸 deferred 취소와 본 코루틴 취소를 구분해야 한다. */
        suspend fun prepareFirstLine() {
            if (_firstLineReady.value) return
            val ready = coordinator.state.value as? DialogueGenState.Ready ?: return // 아직 미도착 — 재호출 시 재시도
            // 첫 라인이 있으면 캐시될 때까지 대기(DEVICE·음소거면 즉시); 없으면 대기 없이 곧바로 게이트 해제.
            val text = nextOpponentEnglish(ready.turns, 0)
            if (text != null) {
                val gender = ready.sessionId?.let { SpeakerDirectory.assign(it).gender }
                tts.awaitWarm(text, gender) // 결과와 무관하게 진입 허용(라이브 재생이 자체 폴백)
            }
            _firstLineReady.value = true
        }

        /**
         * 재시도. pre-flight 오프라인으로 막혔던 경우 새 [start] 로 연결성을 재확인한다(전송 이력 없음 →
         * 새 idempotencyKey 무해). in-flight 실패였으면 [DialogueGenerationCoordinator.retry] 로 같은
         * idempotencyKey 를 재사용한다(backend-functions.md §7 — 서버가 transient/terminal 판별).
         */
        fun retry() {
            val params = lastStart
            if (preflightBlocked && params != null) {
                start(params.level, params.topic, params.length, params.firstSession, isOnboarding)
            } else {
                coordinator.retry()
            }
        }

        /**
         * 대기 화면이 한도 도달 패널에 진입할 때 1회 호출 — 정본 `limit_reached` 이벤트를 [LimitAnalytics]
         * seam 으로 라우팅한다(daily-limit-ux.md §9). remaining 은 거부 시 0. surface 는 패널 렌더와 동일한
         * [selectLimitSurface] 로 산출해(온보딩이면 `onboarding_first_session`) 두 표면이 어긋나지 않게 한다.
         * 라이브 스냅샷 재개는 시작 게이트를 거치지 않으므로 이 경로에선 항상 `hasLiveSnapshot=false`.
         */
        fun onLimitReached(remaining: Int) {
            val surface = selectLimitSurface(isOnboarding = isOnboarding, hasLiveSnapshot = false)
            limitAnalytics.limitReached(remaining, surface.value)
        }

        /**
         * Route a (unscored) quiz answer to the analytics seam (`wait_quiz_card_answered`). The tapped
         * option index is intentionally NOT forwarded — the contract logs only `chose_correct`
         * (analytics-events.md §6.6, PII boundary), so the Route drops it before calling this.
         */
        fun onQuizAnswered(
            item: QuizItem,
            correct: Boolean,
        ) {
            analytics.cardAnswered(
                sessionId = coordinator.sessionId(),
                cardId = item.id,
                choseCorrect = correct,
                cardIndex = answeredCount++,
            )
        }

        /**
         * Fired by the generating Route when the user commits to the conversation (auto-start once ready,
         * or the CTA tap) — logs `first_session_started` (onboarding) or `learning_session_started`
         * (revisit) exactly once per generation (M4-01b §4). No-ops if [start] never landed a session id.
         */
        fun onConversationStarted() {
            if (conversationStartedLogged) return
            val params = lastStart
            val sid = coordinator.sessionId()
            if (params == null || sid == null) return
            conversationStartedLogged = true
            if (isOnboarding) {
                sessionFunnel.firstSessionStarted(sid, params.topic, params.length, params.level)
            } else {
                sessionFunnel.learningSessionStarted(sid, params.topic, params.length, params.level)
            }
        }

        /** Retained start params, so a pre-flight-offline retry can re-attempt with the same inputs. */
        private data class StartParams(
            val level: String,
            val topic: String,
            val length: Int,
            val firstSession: Boolean,
        )

        private companion object {
            val FIRST_SESSION_TIER = com.jjundev.oneclickeng.core.session.SessionLevel.EASY.token

            // `offline_blocked_action` surface(exception-states.md §9). LimitSurface.DialogueStartGate 와 동일
            // 표면 문자열을 재사용해 한도 게이트와 오프라인 게이트가 같은 표면으로 계측된다.
            const val OFFLINE_GATE_SURFACE = "dialogue_start_gate"
        }
    }

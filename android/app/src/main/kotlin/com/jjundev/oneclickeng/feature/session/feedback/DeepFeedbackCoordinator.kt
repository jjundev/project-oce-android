package com.jjundev.oneclickeng.feature.session.feedback

import com.jjundev.oneclickeng.core.network.DeepFeedbackStream
import com.jjundev.oneclickeng.core.network.FeedbackDeepEvent
import com.jjundev.oneclickeng.core.network.FeedbackDeepRequest
import com.jjundev.oneclickeng.core.network.FeedbackPayload
import com.jjundev.oneclickeng.core.time.ElapsedClock
import com.jjundev.oneclickeng.core.time.NoOpElapsedClock
import com.jjundev.oneclickeng.feature.session.analytics.LatencyAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.NoOpLatencyAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.SavedCardAnalytics
import com.jjundev.oneclickeng.feature.session.saved.CardType
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.feature.session.saved.SavedCardId
import com.jjundev.oneclickeng.feature.session.saved.SavedCardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 온디맨드 깊은 분석("더 보기", M2-03)을 오케스트레이션한다: `feedbackDeep` SSE([DeepFeedbackStream])를
 * 소비해 세 블록(conceptualBridge → toneStyle → paraphrasing)을 [DeepFeedbackState] 로 점진 노출하는 코루틴
 * 상태 머신 — [SlimFeedbackCoordinator] 를 미러하되 **요청-레벨** 축(섹션별 재시도 없음)이라는 점만 다르다.
 *
 * **턴당 1회 캐시(P3):** [start] 는 상태가 [DeepFeedbackState.Idle] 일 때만 호출을 개시한다 — 접기/펴기로
 * 재호출되지 않는다(호스트가 매 확장마다 start 를 호출해도 no-op). 새 턴은 [reset] 으로 Idle 로 되돌린 뒤
 * 다시 첫 확장에서만 개시된다.
 *
 * **Stale-guard:** 각 개시/[retry] 는 [sessionToken] 을 bump 하고 이전 collect Job 을 취소한다 — 늦은 이벤트는
 * 토큰 불일치로 드롭된다(collect 취소는 스트림 `awaitClose` 로 소켓을 닫는다).
 *
 * **워치독:** 어떤 블록이든 아직 미도착인 동안 인터-이벤트 idle 타임아웃을 건다. 발화 시 currentJob 을 취소하고
 * 도착 블록은 보존한 채 [DeepFeedbackState.Error] 로 만든다(sticky). 캡 거부는 [DeepFeedbackState.QuotaBlocked]
 * 로 분기한다(재시도 아님).
 *
 * **북마크:** 패러프레이즈 저장 토글은 턴 내 ephemeral([bookmarkedLevels]) 이면서, M2-04 부터 [toggleBookmark]
 * 가 [SavedCardRepository] 로 SENTENCE 카드를 영속화한다(cardId=`{sessionId}__SENTENCE__{turnIndex}__{level}`).
 * 반환하는 [ParaphraseBookmark] 는 계측/호스트용 seam 으로 유지한다.
 */
// 상태 머신 코디네이터라 작은 전이 헬퍼가 많다(SlimFeedbackCoordinator 선례와 동일 판단).
@Suppress("TooManyFunctions")
@Singleton
class DeepFeedbackCoordinator
    @Inject
    constructor(
        private val stream: DeepFeedbackStream,
        private val savedCardRepository: SavedCardRepository,
        private val scope: CoroutineScope,
        private val savedCardAnalytics: SavedCardAnalytics,
        private val clock: ElapsedClock = NoOpElapsedClock,
        private val latencyAnalytics: LatencyAnalytics = NoOpLatencyAnalytics(),
    ) {
        private val _state = MutableStateFlow<DeepFeedbackState>(DeepFeedbackState.Idle)
        val state: StateFlow<DeepFeedbackState> = _state.asStateFlow()

        // 턴 내 ephemeral 북마크 레벨(1/2/3). 영속은 M2-04.
        private val _bookmarkedLevels = MutableStateFlow<Set<Int>>(emptySet())
        val bookmarkedLevels: StateFlow<Set<Int>> = _bookmarkedLevels.asStateFlow()

        @Volatile
        private var sessionToken = 0L
        private var currentJob: Job? = null
        private var watchdogJob: Job? = null

        // Retained so retry() re-runs the same deep call.
        private var lastRequest: FeedbackDeepRequest? = null
        private var turnIndex = 0

        // Per-block accumulators, snapshotted into Loading/Ready/Error on every change.
        private var cb: ConceptualBridge? = null
        private var tone: ToneStyle? = null
        private var para: Paraphrasing? = null

        // Per-attempt latency bookkeeping (M4-01f) — reset on every beginAttempt() (start/retry).
        private var attemptStartMs = 0L
        private var deepLatencyLogged = false

        /**
         * "더 보기" 첫 확장에서 deep 를 개시한다. 상태가 [DeepFeedbackState.Idle] 이 아니면 no-op — 접기/펴기
         * 재확장은 캐시된 결과를 재사용하고 재호출하지 않는다(턴당 1회, P3). 실패 재시도는 [retry] 로 한다.
         */
        @Suppress("LongParameterList") // 슬림 start() 동형 + turnIndex(cardId 파생) — 페이로드 분해가 오히려 불투명.
        fun start(
            sessionId: String,
            turnIndex: Int,
            koreanPrompt: String,
            userEnglish: String,
            referenceEnglish: String,
            level: String,
        ) {
            if (_state.value !is DeepFeedbackState.Idle) return // 턴당 1회 캐시
            this.turnIndex = turnIndex
            lastRequest =
                FeedbackDeepRequest(
                    sessionId = sessionId,
                    payload =
                        FeedbackPayload(
                            koreanPrompt = koreanPrompt,
                            userEnglish = userEnglish,
                            referenceEnglish = referenceEnglish,
                            level = level,
                        ),
                )
            beginAttempt()
        }

        /**
         * 실패 영역 재시도(전체 재호출, [DeepFeedbackState.Error] 에서만). deep 는 섹션별 재시도가 없다 —
         * 영역 1개를 다시 스트리밍한다(§9.2). 재호출은 문장을 재생성하므로 ephemeral 북마크를 비운다(A10).
         */
        fun retry() {
            if (_state.value !is DeepFeedbackState.Error || lastRequest == null) return
            _bookmarkedLevels.value = emptySet()
            beginAttempt()
        }

        /**
         * 다음 턴으로 넘어가며 진행 중 deep 를 취소한다(늦은 응답 무시). [reset] 과 달리 [DeepFeedbackState.Canceled]
         * 로 남겨 "넘어감"을 표현한다. 새 시트/턴 진입은 [reset] 이 Idle 로 되돌린다.
         */
        fun cancel() {
            val wasInFlight = _state.value is DeepFeedbackState.Loading
            sessionToken++
            currentJob?.cancel()
            watchdogJob?.cancel()
            currentJob = null
            clearAccumulators()
            _bookmarkedLevels.value = emptySet()
            _state.value = DeepFeedbackState.Canceled
            if (wasInFlight) logDeepLatency(LatencyAnalytics.OUTCOME_CANCELED)
        }

        /** 진행 중 요청을 취소하고 Idle 로 리셋한다(새 턴/시트 dismiss). 캐시·북마크 파기. */
        fun reset() {
            sessionToken++
            currentJob?.cancel()
            watchdogJob?.cancel()
            currentJob = null
            lastRequest = null
            clearAccumulators()
            _bookmarkedLevels.value = emptySet()
            _state.value = DeepFeedbackState.Idle
        }

        /**
         * 패러프레이즈 북마크 토글. 턴 내 ephemeral 레벨을 [bookmarkedLevels] 에서 토글하고, 결정적 cardId 로
         * SENTENCE 카드를 영속화한다(add→save / remove→톰스톤 setDeleted, fire-and-forget). [ParaphraseBookmark]
         * 를 반환해 호스트 계측 seam 을 유지한다.
         *
         * **불변식:** 토글은 `para != null`(Ready/Loading/Error, DeepFeedbackSections)에서만 도달 가능하고 그
         * 상태는 `beginAttempt()` 실행 = `lastRequest != null` 을 함의한다. 방어적으로 [lastRequest] 가 null 이면
         * 영속을 건너뛴다(ephemeral 토글만 반영).
         */
        fun toggleBookmark(paraphrase: Paraphrase): ParaphraseBookmark {
            val next = _bookmarkedLevels.value.toMutableSet()
            val added = next.add(paraphrase.level)
            if (!added) next.remove(paraphrase.level)
            _bookmarkedLevels.value = next

            lastRequest?.sessionId?.let { sessionId ->
                val cardId = SavedCardId.forSentence(sessionId, turnIndex, paraphrase.level)
                if (added) {
                    savedCardRepository.save(
                        cardId,
                        SavedCard.Sentence(
                            english = paraphrase.sentence,
                            korean = paraphrase.sentenceTranslation,
                        ),
                    )
                    savedCardAnalytics.savedCardCreate(
                        sessionId,
                        SavedCardAnalytics.SURFACE_DEEP_FEEDBACK,
                        CardType.SENTENCE,
                    )
                } else {
                    savedCardRepository.setDeleted(cardId, CardType.SENTENCE, deleted = true)
                }
            }

            return ParaphraseBookmark(
                turnIndex = turnIndex,
                level = paraphrase.level,
                sentence = paraphrase.sentence,
                sentenceTranslation = paraphrase.sentenceTranslation,
            )
        }

        private fun beginAttempt() {
            val request = lastRequest ?: return
            clearAccumulators()
            _state.value = DeepFeedbackState.Loading()
            val token = ++sessionToken
            attemptStartMs = clock.nowMillis()
            deepLatencyLogged = false
            currentJob?.cancel()
            armWatchdog(token)
            currentJob =
                scope.launch {
                    stream.events(request).collect { event ->
                        if (token != sessionToken) return@collect
                        onEvent(token, event)
                    }
                    // Stream closed. If not every block arrived, this attempt failed.
                    if (token == sessionToken) settleOnClose(token)
                }
        }

        private fun onEvent(
            token: Long,
            event: FeedbackDeepEvent,
        ) {
            when (event) {
                is FeedbackDeepEvent.Section.ConceptualBridge -> {
                    cb = event.value.toDomain()
                    afterSection(token)
                }
                is FeedbackDeepEvent.Section.ToneStyle -> {
                    tone = event.value.toDomain()
                    afterSection(token)
                }
                is FeedbackDeepEvent.Section.Paraphrasing -> {
                    para = event.value.toDomain()
                    afterSection(token)
                }
                is FeedbackDeepEvent.Done -> settleOnClose(token)
                is FeedbackDeepEvent.Error -> settleOnClose(token)
                is FeedbackDeepEvent.QuotaExceeded -> {
                    // 캡 거부: 요청-레벨 중립 상태로 분기(§8). 워치독 중단.
                    watchdogJob?.cancel()
                    if (token == sessionToken) _state.value = DeepFeedbackState.QuotaBlocked
                }
            }
        }

        /** Snapshot after a block arrival; promote to Ready when all three land, else keep loading. */
        private fun afterSection(token: Long) {
            if (token != sessionToken) return
            if (allArrived) {
                watchdogJob?.cancel()
                _state.value = readyState()
                logDeepLatency(LatencyAnalytics.OUTCOME_SUCCESSFUL)
            } else {
                _state.value = DeepFeedbackState.Loading(cb, tone, para)
                armWatchdog(token)
            }
        }

        /**
         * Stream closed / done / error / watchdog: all-arrived → Ready (idempotent), else Error with the
         * blocks that did arrive preserved (sticky). Never clobbers a terminal QuotaBlocked/Canceled.
         */
        private fun settleOnClose(token: Long) {
            if (token != sessionToken) return
            watchdogJob?.cancel()
            if (isTerminalNeutral) return
            if (allArrived) {
                _state.value = readyState()
                logDeepLatency(LatencyAnalytics.OUTCOME_SUCCESSFUL)
            } else {
                _state.value = DeepFeedbackState.Error(cb, tone, para)
                logDeepLatency(LatencyAnalytics.OUTCOME_FAILED)
            }
        }

        private fun armWatchdog(token: Long) {
            watchdogJob?.cancel()
            watchdogJob =
                scope.launch {
                    delay(IDLE_WATCHDOG_MS)
                    if (token == sessionToken && !allArrived && !isTerminalNeutral) {
                        currentJob?.cancel() // closes the SSE socket via awaitClose
                        settleOnClose(token)
                    }
                }
        }

        private val allArrived: Boolean
            get() = cb != null && tone != null && para != null

        private val isTerminalNeutral: Boolean
            get() =
                _state.value is DeepFeedbackState.QuotaBlocked ||
                    _state.value is DeepFeedbackState.Canceled

        /** Precondition: [allArrived]. Non-null asserted blocks into the terminal Ready snapshot. */
        private fun readyState(): DeepFeedbackState.Ready =
            DeepFeedbackState.Ready(cb!!, tone!!, para!!)

        /**
         * Fire `deep_latency_ms` once per attempt. Guarded by [deepLatencyLogged] because both
         * [afterSection] (last block arrives) and [settleOnClose] (stream close/watchdog) can reach a
         * terminal transition for the same attempt — without the guard, the routine sequence of
         * afterSection setting Ready followed by the stream's collect loop finishing (which calls
         * settleOnClose) would double-fire.
         */
        private fun logDeepLatency(outcome: String) {
            if (deepLatencyLogged) return
            deepLatencyLogged = true
            latencyAnalytics.latency(LatencyAnalytics.OPERATION_DEEP, outcome, clock.nowMillis() - attemptStartMs)
        }

        private fun clearAccumulators() {
            cb = null
            tone = null
            para = null
        }

        companion object {
            // Inter-event idle bound while any block is still missing. Mirrors
            // SlimFeedbackCoordinator.IDLE_WATCHDOG_MS; authoritative over the SSE socket (readTimeout 0).
            const val IDLE_WATCHDOG_MS = 20_000L
        }
    }

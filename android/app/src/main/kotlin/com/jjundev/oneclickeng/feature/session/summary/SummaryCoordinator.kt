package com.jjundev.oneclickeng.feature.session.summary

import com.jjundev.oneclickeng.core.network.SectionOutcome
import com.jjundev.oneclickeng.core.network.SummaryEvent
import com.jjundev.oneclickeng.core.network.SummaryPayload
import com.jjundev.oneclickeng.core.network.SummaryRequest
import com.jjundev.oneclickeng.core.network.SummaryStream
import com.jjundev.oneclickeng.core.settings.SummarySaveSettingsRepository
import com.jjundev.oneclickeng.feature.gamification.GamificationTime
import com.jjundev.oneclickeng.feature.gamification.StudytimeRepository
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
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 재시도 필터가 지정하는 요약 SSE 섹션. [sectionKey] 는 백엔드 PLURAL 키(`done.sections`/`payload.sections`). */
enum class SummarySection(val sectionKey: String) {
    Expression("expressions"),
    Word("words"),
    Coaching("coaching"),
}

/**
 * 세션 요약(M2-02)을 오케스트레이션한다: 로컬 즉시 데이터([SessionTurnBufferStore]·[BookmarkSource]·주입
 * accrual)와 요약 SSE 번들([SummaryStream])을 합성해 [SummaryState] 로 노출하는 코루틴 상태 머신 —
 * [com.jjundev.oneclickeng.feature.session.feedback.SlimFeedbackCoordinator] 의 구조적 쌍둥이(주입 scope +
 * 단조 stale 토큰 + idle 워치독)다. 클라는 완성 카드만 렌더하고 원시 JSON 을 파싱하지 않는다(FR-6).
 *
 * **로딩 모델(#3/#4):** SSE 3섹션(표현/단어/코칭)은 stream:no 인 3개 one-shot 호출이 단일 SSE 로 묶여
 * 오므로, slim 식 섹션별 점진 스켈레톤이 아니라 **초기 단일 번들 스켈레톤**([SectionBundle.BundleLoading])
 * 하나를 두고, `event:done` 수신 시 [SectionBundle.Sectioned] 로 전이해 섹션별로 렌더/재시도한다
 * (dialogue-learning-flow.md §9). 로컬 4블록은 어느 상태에서도 즉시 렌더된다(스켈레톤 없음).
 *
 * **부분 실패/재시도(#9/#10):** `done{expressions,words,coaching: ok|failed}` 로 섹션별 성패를 가른다.
 * [retry] 는 실패 섹션(들)을 [SummaryPayload.sections](백엔드 PLURAL 키)에 실어 재요청한다(백엔드가 성공
 * 섹션 캐시 재사용 — backend-functions.md:117). 재요청 중 추가 실패 섹션 재시도 탭은 pending sections(=현재
 * Loading 섹션 집합)에 병합된다 — 단일 공유 SSE 라 동시 다중 호출을 만들지 않는다.
 *
 * **Stale-guard/워치독:** 각 [start]/[retry] 는 [sessionToken] 을 bump 하고 이전 collect Job 을 취소한다
 * (늦은 이벤트는 토큰 불일치로 드롭). 워치독은 번들이 아직 확정되지 않았거나(초기) 재시도 섹션이 Loading 인
 * 동안 인터-이벤트 idle 타임아웃을 걸어, 발화 시 소켓을 닫고 미도착 섹션을 Failed 로 만든다.
 *
 * **완주 적립(#20·M3-05):** [start] 는 요약 진입 시점에 [CompletionLedger.recordCompletion] 으로
 * point_ledger create(XP 원장, 멱등)를, [StudytimeRepository.recordSession] 으로 studytime 누적(멱등)을
 * 시도한다. XP/streak 서버 집계는 `onLedgerCreate` 트리거 소관이며, 적립 스트립([accrual])은 로컬 낙관
 * 값으로 갱신되어 서버가 사후 정합한다.
 */
// 상태 머신 코디네이터라 작은 전이 헬퍼가 많다(SlimFeedbackCoordinator 선례와 동일 판단). 주입 seam 이
// 많아(스트림·버퍼·북마크·원장·저장카드·studytime·scope) LongParameterList 를 억제한다 — 페이로드 분해가
// 오히려 불투명(DeepFeedbackCoordinator 선례와 동일 판단).
@Suppress("TooManyFunctions", "LongParameterList")
@Singleton
class SummaryCoordinator
    @Inject
    constructor(
        private val stream: SummaryStream,
        private val turnBuffer: SessionTurnBufferStore,
        private val bookmarkSource: BookmarkSource,
        private val ledger: CompletionLedger,
        private val savedCardRepository: SavedCardRepository,
        private val saveSettings: SummarySaveSettingsRepository,
        private val studytime: StudytimeRepository,
        private val scope: CoroutineScope,
    ) {
        private val _state = MutableStateFlow(EMPTY)
        val state: StateFlow<SummaryState> = _state.asStateFlow()

        @Volatile
        private var sessionToken = 0L
        private var currentJob: Job? = null
        private var watchdogJob: Job? = null

        // The request inputs of the current summary, retained so retry() re-issues the same call.
        private var sessionId: String? = null
        private var basePayload: SummaryPayload = SummaryPayload(totalScore = 0)

        // Local-immediate blocks (set once at start; bookmarks fill async).
        private var totalScore: Int? = null
        private var highlight: HighlightTurn? = null
        private var accrual: AccrualStrip = AccrualStrip(streakDays = 0, xp = 0)
        private var bookmarks: List<BookmarkCard> = emptyList()
        private var isFirstSession = false

        // 저장 카드 낙관적 UI 축(M2-04). sourceIndex=표시 인덱스. start()/reset() 에서만 초기화된다.
        private var savedWordIndices = emptySet<Int>()
        private var savedExprIndices = emptySet<Int>()
        private var unsavedBookmarkIds = emptySet<String>()

        // 저장 기본값(설정 화면). start() 가 [beginAttempt] 로 SSE 오픈을 이 값 확정 뒤로 미뤄, 카드 도착
        // 시점과의 레이스를 없앤다.
        private var saveByDefault = false

        // Per-section accumulators. Before the first `done`, arrived cards set Ready but the bundle
        // still shows BundleLoading (single skeleton) until [sectioned] flips.
        private var expression: SummarySectionState<List<ExpressionCard>> = SummarySectionState.Loading
        private var word: SummarySectionState<List<WordCard>> = SummarySectionState.Loading
        private var coaching: SummarySectionState<Coaching> = SummarySectionState.Loading
        private var sectioned = false
        private var quota = false

        // Cumulative failure counts per section (persist across retries — #17 누적 임계).
        private var attemptsExpr = 0
        private var attemptsWord = 0
        private var attemptsCoaching = 0

        /**
         * Begin the session summary. Composes local-immediate blocks from [turnBuffer] + injected
         * [accrual], attempts the completion ledger create, then opens the summary SSE with the whole
         * session's turn buffer. `difficulty`/`modeId` are session facts supplied by the caller (the
         * summary route); full plumbing from the dialogue params is the M1 nav integration seam.
         */
        fun start(
            sessionId: String,
            difficulty: String,
            modeId: String,
            accrual: AccrualStrip,
            isFirstSession: Boolean = false,
        ) {
            this.sessionId = sessionId
            this.accrual = accrual
            this.isFirstSession = isFirstSession
            totalScore = turnBuffer.totalScore()
            highlight = turnBuffer.highlightBase()
            bookmarks = emptyList()
            savedWordIndices = emptySet()
            savedExprIndices = emptySet()
            unsavedBookmarkIds = emptySet()
            basePayload = SummaryPayloadProjector.project(turnBuffer.bufferedTurns(), totalScore ?: 0)
            expression = SummarySectionState.Loading
            word = SummarySectionState.Loading
            coaching = SummarySectionState.Loading
            sectioned = false
            quota = false
            attemptsExpr = 0
            attemptsWord = 0
            attemptsCoaching = 0

            // stale-guard: neutralize any live prior stream synchronously. Before [beginAttempt]
            // existed this bump/cancel happened inside launchAttempt, called synchronously from here —
            // airtight the instant start() returned. launchAttempt is now deferred behind a settings
            // read, so without this, a card from the PRIOR session could arrive and pass the token
            // guard while the new sessionId is already live, persisting it under the wrong cardId.
            sessionToken++
            currentJob?.cancel()

            // 완주 적립 시도(요약 진입 시점, #20). fire-and-forget · 멱등.
            ledger.recordCompletion(sessionId = sessionId, difficulty = difficulty, modeId = modeId)
            // studytime 적립 + 적립 스트립 산출(M3-05). 비동기 — 완료 시 accrual 갱신.
            recordAccrual(sessionId, difficulty)
            // 북마크 비동기 로드(M2-04 착지 전엔 빈 리스트). 도착 시 로컬 블록만 갱신.
            loadBookmarks(sessionId)

            emit()
            beginAttempt(sessionId)
        }

        /**
         * 저장 기본값 설정을 1회 읽은 뒤 SSE 를 연다. 표현/단어 카드가 [onEvent] 로 도착하는 시점에
         * [saveByDefault] 가 이미 확정돼 있어야 자동 저장 여부를 놓치지 않는다 — 네트워크(SSE)보다 로컬
         * DataStore 읽기가 사실상 항상 먼저 끝나지만, 순서를 코드로 보장해 레이스를 없앤다.
         *
         * **가드는 sessionId 가 아니라 `sessionToken` 스냅샷이다.** [loadBookmarks]/[recordAccrual] 은
         * sessionId 동일성만 가드해도 충분하다 — 그쪽이 지키는 일은 멱등 상태 대입이라, 같은 sessionId 로
         * 다시 [start] 된 경우 최신 데이터로 한 번 더 덮어써도 무해하다. 반면 여기서 가드하는 일은 SSE
         * 스트림을 여는 부수효과라, 같은 sessionId 로 `start→reset→start` 가 인터리브되면(예: 요약 화면
         * leave→re-enter) sessionId 비교만으로는 두 번째 대기 중인 읽기도 통과시켜 스트림을 두 번 열어버린다
         * (취소되는 쪽도 백엔드에는 이미 유료 Gemini 생성 요청을 하나 태운 뒤다). `sessionToken` 은 [start]
         * 가 호출될 때마다 bump 되므로, [beginAttempt] 진입 시점에 캡처해두면 각 호출은 자신을 낳은 그
         * [start] 에만 유효하다 — sessionId 비교는 부가 방어로 남긴다.
         *
         * 설정 읽기는 SSE 오픈의 하드 프리컨디션이 아니다: DataStore 읽기가 IOException(-계열인
         * CorruptionException 포함)으로 실패해도 기본값 "저장"(true)으로 폴백해 그대로 진행한다 —
         * [start] 에서 [launchAttempt] 까지 아무것도 던지지 않던 이전 불변을 유지한다. 단, **멈추지 않는다는
         * 것까지는 보장하지 않는다**: `saveSettings.current()` 가 예외 없이 영영 반환하지 않으면
         * [launchAttempt] 도 워치독도 기동하지 않아 화면이 스켈레톤에 멈춘다 — DataStore 읽기는 실전에서
         * 사실상 항상 끝나므로 현재는 이 경우를 별도로 방어하지 않는다.
         */
        private fun beginAttempt(sessionId: String) {
            val token = sessionToken // captured synchronously, right after start()'s bump.
            scope.launch {
                val resolved =
                    try {
                        saveSettings.current().saveByDefault
                    } catch (_: IOException) {
                        true
                    }
                if (token == sessionToken && sessionId == this@SummaryCoordinator.sessionId) {
                    saveByDefault = resolved
                    launchAttempt(sections = null)
                }
            }
        }

        /**
         * Retry one failed SSE section (#10). Sets it back to Loading and re-issues the call naming all
         * currently-Loading sections in [SummaryPayload.sections] (plural backend keys; merges a
         * concurrent retry). No-op unless the bundle is [SectionBundle.Sectioned], the section is
         * [SummarySectionState.Failed] and retries remain.
         */
        @Suppress("ReturnCount") // 세 개의 조기 no-op 가드(미확정/캡/재시도 불가)는 평평하게 읽는 게 낫다.
        fun retry(section: SummarySection) {
            if (!sectioned || quota) return
            val failed = sectionState(section) as? SummarySectionState.Failed ?: return
            if (!failed.canRetry) return
            setSection(section, SummarySectionState.Loading)
            emit()
            launchAttempt(sections = loadingSectionKeys())
        }

        /** Cancel any in-flight request and reset to the empty state (e.g. screen left). */
        fun reset() {
            sessionToken++
            currentJob?.cancel()
            watchdogJob?.cancel()
            currentJob = null
            sessionId = null
            savedWordIndices = emptySet()
            savedExprIndices = emptySet()
            unsavedBookmarkIds = emptySet()
            saveByDefault = false
            _state.value = EMPTY
        }

        /**
         * 단어 카드 저장 토글(M2-04, sourceIndex=[index]). Ready 섹션에서만 호출된다(호출부 게이팅). 낙관적으로
         * [savedWordIndices] 를 토글하고 결정적 cardId 로 영속화한다(add→save / remove→톰스톤). 세션·해당 인덱스
         * 카드가 없으면 no-op.
         */
        fun toggleSaveWord(index: Int) {
            val id = sessionId ?: return
            val card = (word as? SummarySectionState.Ready)?.value?.getOrNull(index) ?: return
            val added = index !in savedWordIndices
            savedWordIndices = if (added) savedWordIndices + index else savedWordIndices - index
            val cardId = SavedCardId.forSummary(id, CardType.WORD, index)
            if (added) {
                savedCardRepository.save(cardId, card.toSavedCard())
            } else {
                savedCardRepository.setDeleted(cardId, CardType.WORD, deleted = true)
            }
            emit()
        }

        /** 표현 카드 저장 토글(M2-04, sourceIndex=[index]). [toggleSaveWord] 와 동형(EXPRESSION 타입). */
        fun toggleSaveExpression(index: Int) {
            val id = sessionId ?: return
            val card = (expression as? SummarySectionState.Ready)?.value?.getOrNull(index) ?: return
            val added = index !in savedExprIndices
            savedExprIndices = if (added) savedExprIndices + index else savedExprIndices - index
            val cardId = SavedCardId.forSummary(id, CardType.EXPRESSION, index)
            if (added) {
                savedCardRepository.save(cardId, card.toSavedCard())
            } else {
                savedCardRepository.setDeleted(cardId, CardType.EXPRESSION, deleted = true)
            }
            emit()
        }

        /** 현재 요약의 SENTENCE 카드 저장 상태를 토글한다. 카드는 목록에 유지해 재저장할 수 있다. */
        fun toggleSaveBookmark(cardId: String) {
            if (sessionId == null) return
            val current = bookmarks.firstOrNull { it.cardId == cardId } ?: return
            val added = cardId in unsavedBookmarkIds
            unsavedBookmarkIds =
                if (added) unsavedBookmarkIds - cardId else unsavedBookmarkIds + cardId
            if (added) {
                savedCardRepository.save(
                    cardId,
                    SavedCard.Sentence(english = current.english, korean = current.korean),
                )
            } else {
                savedCardRepository.setDeleted(cardId, CardType.SENTENCE, deleted = true)
            }
            emit()
        }

        /**
         * 저장 기본값 ON 일 때 표현 카드 도착 즉시 전 항목을 저장한다. [toggleSaveExpression] 과 같은
         * cardId 규칙([SavedCardId.forSummary])을 써서 이후 수동 토글(해제)이 정확히 같은 문서를 가리킨다.
         */
        private fun autoSaveExpressions(items: List<ExpressionCard>) {
            val id = sessionId ?: return
            savedExprIndices = items.indices.toSet()
            items.forEachIndexed { index, card ->
                savedCardRepository.save(SavedCardId.forSummary(id, CardType.EXPRESSION, index), card.toSavedCard())
            }
        }

        /** [autoSaveExpressions] 와 동형(WORD). */
        private fun autoSaveWords(items: List<WordCard>) {
            val id = sessionId ?: return
            savedWordIndices = items.indices.toSet()
            items.forEachIndexed { index, card ->
                savedCardRepository.save(SavedCardId.forSummary(id, CardType.WORD, index), card.toSavedCard())
            }
        }

        private fun loadBookmarks(sessionId: String) {
            scope.launch {
                val loaded = bookmarkSource.latestSentences(sessionId, BOOKMARK_LIMIT)
                // Apply only if still the current session (a reset/new start supersedes this load).
                if (sessionId == this@SummaryCoordinator.sessionId) {
                    bookmarks = loaded
                    emit()
                }
            }
        }

        /**
         * studytime 적립(M3-05). 세션 시작 벽시계([SessionTurnBufferStore.sessionStartMillis])에서 완주까지
         * 경과 학습시간을 산출해 [StudytimeRepository.recordSession] 으로 로컬 누적(멱등)·서버 push 하고, 반환된
         * 오늘 학습시간/streak 로 적립 스트립을 갱신한다. XP 는 난이도의 순수 함수(서버 권위 미러). 비동기라 진입
         * 시엔 주입된 초기 [accrual] 이 보이고, 완료 시 실제 값으로 교체된다(로컬 블록 async 패턴, 북마크와 동일).
         */
        private fun recordAccrual(
            sessionId: String,
            difficulty: String,
        ) {
            scope.launch {
                val nowMs = System.currentTimeMillis()
                val dayKey = GamificationTime.kstDayKey(nowMs)
                val elapsed = GamificationTime.elapsedStudySeconds(turnBuffer.sessionStartMillis(), nowMs)
                val snap = studytime.recordSession(sessionId, elapsed, dayKey)
                // Apply only if still the current session (a reset/new start supersedes this).
                if (sessionId == this@SummaryCoordinator.sessionId) {
                    // 실데이터 착지 → animate=true 로 카운트업 대상(M3-06). 학습시간은 오늘 누계 before→after
                    // 롤업, streak 는 same-day 반복이면 정적(§4.4).
                    accrual =
                        AccrualStrip(
                            streakDays = snap.streak,
                            xp = GamificationTime.XP_BY_DIFFICULTY[difficulty] ?: 0,
                            todayStudySecondsBefore = snap.todaySecondsBefore.toInt(),
                            todayStudySecondsAfter = snap.todaySeconds.toInt(),
                            streakStatic = snap.streakStatic,
                            animate = true,
                        )
                    emit()
                }
            }
        }

        private fun launchAttempt(sections: List<String>?) {
            val id = sessionId ?: return
            val token = ++sessionToken
            currentJob?.cancel()
            val request =
                SummaryRequest(
                    sessionId = id,
                    payload = basePayload.copy(sections = sections),
                )
            armWatchdog(token)
            currentJob =
                scope.launch {
                    stream.events(request).collect { event ->
                        if (token != sessionToken) return@collect
                        onEvent(token, event)
                    }
                    // Stream closed without a clean `done` — fail whatever is still Loading.
                    if (token == sessionToken) failLoadingSections(token)
                }
        }

        private fun onEvent(
            token: Long,
            event: SummaryEvent,
        ) {
            when (event) {
                is SummaryEvent.Card.Expression -> {
                    val items = event.items.take(MAX_EXPRESSIONS).map { it.toDomain() }
                    expression = SummarySectionState.Ready(items)
                    if (saveByDefault) autoSaveExpressions(items)
                    afterCard(token)
                }
                is SummaryEvent.Card.Word -> {
                    val items = event.items.take(MAX_WORDS).map { it.toDomain() }
                    word = SummarySectionState.Ready(items)
                    if (saveByDefault) autoSaveWords(items)
                    afterCard(token)
                }
                is SummaryEvent.Card.Coaching -> {
                    coaching = SummarySectionState.Ready(event.value.toDomain())
                    afterCard(token)
                }
                is SummaryEvent.Done -> applyDone(token, event)
                is SummaryEvent.Error -> failLoadingSections(token)
                is SummaryEvent.QuotaExceeded -> onQuotaExceeded(token)
            }
        }

        /**
         * 세션 캡 거부(#16). 두 경로:
         * - **사전-게이트(아무 카드도 안 온 상태):** SSE 영역을 top-level 배타 [SectionBundle.QuotaBlocked] 로
         *   — 중립 문구, 재시도 없음.
         * - **mid-stream(이미 일부 카드 도착):** 이미 도착한 섹션은 유지하고(sticky Ready) 미도착 섹션만
         *   `Failed(canRetry=false)`(=[SummarySectionState.MAX_ATTEMPTS])로 종결한다 — 캡 하에선 재시도 불가라
         *   재시도 버튼 없이 비활성 안내만 뜬다. 로컬 블록은 두 경로 모두 유지된다.
         */
        private fun onQuotaExceeded(token: Long) {
            watchdogJob?.cancel()
            if (token != sessionToken) return
            if (anyArrived) {
                SummarySection.entries.forEach { section ->
                    if (sectionState(section) is SummarySectionState.Loading) {
                        setSection(section, SummarySectionState.Failed(SummarySectionState.MAX_ATTEMPTS))
                    }
                }
                sectioned = true
            } else {
                quota = true
            }
            emit()
        }

        /** A card arrived: re-arm the watchdog while the bundle is still unsettled or a retry is loading. */
        private fun afterCard(token: Long) {
            emit()
            if (!sectioned || anyLoading) armWatchdog(token) else watchdogJob?.cancel()
        }

        /**
         * `event:done` — resolve every still-Loading section from its per-section outcome: `ok` with no
         * card ⇒ Ready(empty) (§10 "비어있음"), `failed` ⇒ Failed(attempts++). Arrived cards already sit
         * Ready and stay sticky. Flips [sectioned] so the bundle renders per-section.
         */
        private fun applyDone(
            token: Long,
            done: SummaryEvent.Done,
        ) {
            if (token != sessionToken || quota) return
            watchdogJob?.cancel()
            resolveSection(SummarySection.Expression, done.expressions)
            resolveSection(SummarySection.Word, done.words)
            resolveSection(SummarySection.Coaching, done.coaching)
            sectioned = true
            emit()
        }

        private fun resolveSection(
            section: SummarySection,
            outcome: SectionOutcome,
        ) {
            if (sectionState(section) !is SummarySectionState.Loading) return // arrived card is sticky
            when (outcome) {
                // `ok` with no card ⇒ the section really was empty (§10 "비어있음" vs "실패" 구분).
                SectionOutcome.Ok -> setReadyEmpty(section)
                SectionOutcome.Failed -> failSection(section)
            }
        }

        private fun setReadyEmpty(section: SummarySection) {
            when (section) {
                SummarySection.Expression -> expression = SummarySectionState.Ready(emptyList())
                SummarySection.Word -> word = SummarySectionState.Ready(emptyList())
                SummarySection.Coaching -> coaching = SummarySectionState.Ready(Coaching(positive = "", toImprove = ""))
            }
        }

        /** Convert every still-Loading section into Failed (attempts++) — stream close / error / timeout. */
        private fun failLoadingSections(token: Long) {
            if (token != sessionToken || quota) return
            watchdogJob?.cancel()
            SummarySection.entries.forEach { section ->
                if (sectionState(section) is SummarySectionState.Loading) failSection(section)
            }
            sectioned = true
            emit()
        }

        private fun failSection(section: SummarySection) {
            val attempts = bumpAttempts(section)
            setSection(section, SummarySectionState.Failed(attempts))
        }

        private fun armWatchdog(token: Long) {
            watchdogJob?.cancel()
            watchdogJob =
                scope.launch {
                    delay(SUMMARY_WATCHDOG_MS)
                    if (token == sessionToken && (!sectioned || anyLoading)) {
                        currentJob?.cancel() // closes the SSE socket via awaitClose
                        failLoadingSections(token)
                    }
                }
        }

        private val anyLoading: Boolean
            get() =
                expression is SummarySectionState.Loading ||
                    word is SummarySectionState.Loading ||
                    coaching is SummarySectionState.Loading

        /** 하나라도 카드가 도착(Ready)했는지 — mid-stream 캡 처리(#16)가 도착분 보존을 결정하는 데 쓴다. */
        private val anyArrived: Boolean
            get() =
                expression is SummarySectionState.Ready ||
                    word is SummarySectionState.Ready ||
                    coaching is SummarySectionState.Ready

        private fun loadingSectionKeys(): List<String> =
            SummarySection.entries
                .filter { sectionState(it) is SummarySectionState.Loading }
                .map { it.sectionKey }

        /** Re-snapshot into [SummaryState]. Bundle = QuotaBlocked > (sectioned ? Sectioned : BundleLoading). */
        private fun emit() {
            val bundle =
                when {
                    quota -> SectionBundle.QuotaBlocked
                    sectioned -> SectionBundle.Sectioned(expression, word, coaching)
                    else -> SectionBundle.BundleLoading
                }
            _state.value =
                SummaryState(
                    totalScore = totalScore,
                    highlight = highlight,
                    bookmarks = bookmarks,
                    accrual = accrual,
                    bundle = bundle,
                    savedWordIndices = savedWordIndices,
                    savedExprIndices = savedExprIndices,
                    unsavedBookmarkIds = unsavedBookmarkIds,
                    isFirstSession = isFirstSession,
                )
        }

        // --- per-section field accessors (the three sections have distinct value types) ---

        private fun sectionState(section: SummarySection): SummarySectionState<*> =
            when (section) {
                SummarySection.Expression -> expression
                SummarySection.Word -> word
                SummarySection.Coaching -> coaching
            }

        /** Set a section to a value-less state (Loading or Failed). */
        private fun setSection(
            section: SummarySection,
            value: SummarySectionState<Nothing>,
        ) {
            when (section) {
                SummarySection.Expression -> expression = value
                SummarySection.Word -> word = value
                SummarySection.Coaching -> coaching = value
            }
        }

        private fun bumpAttempts(section: SummarySection): Int =
            when (section) {
                SummarySection.Expression -> ++attemptsExpr
                SummarySection.Word -> ++attemptsWord
                SummarySection.Coaching -> ++attemptsCoaching
            }

        companion object {
            // Inter-event idle bound while the bundle is unsettled or a retry section is Loading. Summary
            // is 3 sequential one-shot Gemini calls bundled into one SSE, so it runs longer than slim
            // feedback — a wider bound than SlimFeedbackCoordinator.IDLE_WATCHDOG_MS. Authoritative over
            // the SSE socket, whose read timeout is disabled (SummarySseStream).
            const val SUMMARY_WATCHDOG_MS = 30_000L

            // 표시 상한(04-screen-05-summary.md:14-16). 서버가 이미 dedupe 하지만 클라도 방어적으로 자른다.
            const val MAX_EXPRESSIONS = 8
            const val MAX_WORDS = 12
            const val BOOKMARK_LIMIT = 8

            private val EMPTY =
                SummaryState(
                    totalScore = null,
                    highlight = null,
                    bookmarks = emptyList(),
                    accrual = AccrualStrip(streakDays = 0, xp = 0),
                    bundle = SectionBundle.BundleLoading,
                )
        }
    }

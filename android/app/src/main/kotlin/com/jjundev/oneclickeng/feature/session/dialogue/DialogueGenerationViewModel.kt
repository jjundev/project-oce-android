package com.jjundev.oneclickeng.feature.session.dialogue

import androidx.lifecycle.ViewModel
import com.jjundev.oneclickeng.core.network.LimitAnalytics
import com.jjundev.oneclickeng.core.network.WaitQuizAnalytics
import com.jjundev.oneclickeng.feature.session.dialogue.quiz.QuizBank
import com.jjundev.oneclickeng.ui.component.LimitSurface
import com.jjundev.oneclickeng.ui.component.QuizItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
@HiltViewModel
class DialogueGenerationViewModel
    @Inject
    constructor(
        private val coordinator: DialogueGenerationCoordinator,
        private val quizBank: QuizBank,
        private val analytics: WaitQuizAnalytics,
        private val limitAnalytics: LimitAnalytics,
        loadingQuizConfig: LoadingQuizConfig,
    ) : ViewModel() {
        val state: StateFlow<DialogueGenState> = coordinator.state

        /** WaitQuiz kill-switch (default-on). Consumed by the screen to gate the quiz surface. */
        val quizEnabled: Boolean = loadingQuizConfig.loadingQuizEnabled

        private val _quizItems = MutableStateFlow<List<QuizItem>>(emptyList())
        val quizItems: StateFlow<List<QuizItem>> = _quizItems.asStateFlow()

        // Monotonic position of the answered card within this generation, for `card_index`
        // (analytics-events.md §6.6) — the WaitQuiz callback does not carry the card position.
        private var answeredCount = 0

        /** Begin generation and load the tier's quiz items (first session → easy). */
        fun start(
            level: String,
            topic: String,
            length: Int,
            firstSession: Boolean,
        ) {
            val tier = if (firstSession) FIRST_SESSION_TIER else level
            _quizItems.value = if (quizEnabled) quizBank.forTier(tier) else emptyList()
            answeredCount = 0
            coordinator.start(level, topic, length, firstSession)
        }

        /** Retry the current attempt, reusing its idempotencyKey (backend-functions.md §7). */
        fun retry() = coordinator.retry()

        /**
         * 대기 화면이 한도 도달(dialogue_start_gate) 패널에 진입할 때 1회 호출 — 정본 `limit_reached`
         * 이벤트를 [LimitAnalytics] seam 으로 라우팅한다(daily-limit-ux.md §9). remaining 은 거부 시 0.
         */
        fun onLimitReached(remaining: Int) {
            limitAnalytics.limitReached(remaining, LimitSurface.DialogueStartGate.value)
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

        private companion object {
            const val FIRST_SESSION_TIER = "easy"
        }
    }

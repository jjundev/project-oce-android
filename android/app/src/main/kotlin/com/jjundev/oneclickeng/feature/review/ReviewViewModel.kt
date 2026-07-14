package com.jjundev.oneclickeng.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjundev.oneclickeng.feature.review.data.LeitnerLogic
import com.jjundev.oneclickeng.feature.review.data.ReviewClock
import com.jjundev.oneclickeng.feature.review.data.ReviewItem
import com.jjundev.oneclickeng.feature.review.data.ReviewPhase
import com.jjundev.oneclickeng.feature.review.data.ReviewSource
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.feature.session.saved.SavedCardRepository
import com.jjundev.oneclickeng.feature.session.tts.TtsPlaybackCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 복습 세션 상태 머신(Task 7). `TtsPlaybackCoordinator` 는 인터페이스가 아니라 구상 `@Singleton class`라
 * 서브클래싱으로 테스트 페이크를 만들 수 없다 — 대신 Hilt 주입 생성자가 이를 `speak: (String) -> Unit` 람다로
 * 래핑해 내부 primary constructor(테스트 전용, `internal`)에 넘긴다. 테스트는 `speak = {}` no-op 람다를 직접
 * 주입해 실제 TTS 스택 없이 상태 전이만 검증한다.
 *
 * Flashcard(Front/Back): [reveal] 이 Front→Back, [grade] 가 srs 기록 + 카운터 갱신 + 다음 카드로 전진.
 * Quiz(Ask/Reveal, Expression 전용): [pick] 이 srs 기록 + 카운터 갱신 + Ask→Reveal(전진하지 않음),
 * 이어서 [next] 가 전진한다. 인덱스가 끝을 넘으면 Done.
 */
@HiltViewModel
class ReviewViewModel
    internal constructor(
        private val reviewSource: ReviewSource,
        private val clock: ReviewClock,
        private val savedCardRepository: SavedCardRepository,
        private val speak: (String) -> Unit,
    ) : ViewModel() {
        @Inject
        constructor(
            reviewSource: ReviewSource,
            clock: ReviewClock,
            savedCardRepository: SavedCardRepository,
            tts: TtsPlaybackCoordinator,
        ) : this(
            reviewSource,
            clock,
            savedCardRepository,
            speak = { text -> tts.playTurn(text = text, gender = null, advanceOnDone = false) },
        )

        private val _uiState = MutableStateFlow(ReviewUiState())
        val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

        init { load() }

        fun restart() = load()

        fun reveal() {
            val s = _uiState.value
            if (s.phase == ReviewPhase.Front) _uiState.value = s.copy(phase = ReviewPhase.Back)
        }

        fun grade(correct: Boolean) {
            val s = _uiState.value
            val item = s.current ?: return
            record(item, correct)
            val scored = s.copy(done = s.done + if (correct) 1 else 0, again = s.again + if (correct) 0 else 1)
            advanceFrom(scored)
        }

        @Suppress("ReturnCount")
        fun pick(choice: Int) {
            val s = _uiState.value
            if (s.phase != ReviewPhase.Ask) return
            val item = s.current ?: return
            if (item.card !is SavedCard.Expression) return
            val correct = choice == EXPRESSION_CORRECT_INDEX
            record(item, correct)
            _uiState.value =
                s.copy(
                    phase = ReviewPhase.Reveal,
                    pick = choice,
                    done = s.done + if (correct) 1 else 0,
                    again = s.again + if (correct) 0 else 1,
                )
        }

        fun next() = advanceFrom(_uiState.value)

        fun playTts(text: String) = speak(text)

        private fun record(item: ReviewItem, correct: Boolean) {
            val next = LeitnerLogic.onGrade(item.review, correct, clock.nowMs())
            savedCardRepository.updateSrs(
                cardId = item.cardId,
                cardType = item.card.cardType,
                box = next.box,
                nextReviewAt = next.nextReviewAt,
                lastReviewedAt = next.lastReviewedAt,
                reps = next.reps,
                lapses = next.lapses,
            )
        }

        private fun advanceFrom(s: ReviewUiState) {
            val ni = s.index + 1
            _uiState.value =
                if (ni >= s.items.size) {
                    s.copy(finished = true, phase = ReviewPhase.Done)
                } else {
                    s.copy(index = ni, phase = phaseFor(s.items[ni]), pick = null)
                }
        }

        private fun load() {
            _uiState.value = ReviewUiState(loading = true)
            viewModelScope.launch {
                val items = reviewSource.pool(clock.nowMs())
                _uiState.value =
                    ReviewUiState(
                        loading = false,
                        items = items,
                        index = 0,
                        phase = if (items.isEmpty()) ReviewPhase.Done else phaseFor(items[0]),
                        finished = items.isEmpty(),
                    )
            }
        }

        private fun phaseFor(item: ReviewItem): ReviewPhase =
            if (item.card is SavedCard.Expression) ReviewPhase.Ask else ReviewPhase.Front
    }

package com.jjundev.oneclickeng.feature.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.jjundev.oneclickeng.feature.session.saved.CardType
import com.jjundev.oneclickeng.feature.session.saved.SavedCardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 기록 탭 상태 소유자(M2-05). 읽기는 [SavedCardQuerySource] 커서 페이지네이션(탭별 누적 유지), 삭제는
 * M2-04 [SavedCardRepository.setDeleted] 톰스톤 위에 **명시적 낙관 로컬 변이**를 얹는다:
 *  - [deleteCard](확인 다이얼로그 이후 호출) → 즉시 `setDeleted(true)`(톰스톤) + 인메모리 리스트에서 제거.
 *  undo 는 없다 — 다이얼로그가 안전장치이므로 되돌릴 필요가 없다.
 *
 * 읽기가 1회성 get 이라 리스너 재전달에 의존하지 않으므로, 삭제 후 리스트 반영은 이 낙관 변이가 단일하게 소유한다.
 */
@Suppress("LongParameterList") // DI: 기존 읽기/삭제/통계 seam 5종 + 복습 배너용 ReviewSource/ReviewClock(Task 8).
@HiltViewModel
class RecordsViewModel
    @Inject
    constructor(
        private val querySource: SavedCardQuerySource,
        private val savedCardRepository: SavedCardRepository,
        private val lifetimeStatsSource: LifetimeStatsSource,
        private val analytics: HistoryAnalytics,
        private val countUpGate: HistoryCountUpGate,
        private val reviewSource: com.jjundev.oneclickeng.feature.review.data.ReviewSource,
        private val reviewClock: com.jjundev.oneclickeng.feature.review.data.ReviewClock,
    ) : ViewModel() {
        private data class TypeState(
            val cards: List<SavedCardEntry> = emptyList(),
            val cursor: DocumentSnapshot? = null,
            val endReached: Boolean = false,
            val loading: Boolean = false,
            val loaded: Boolean = false,
        )

        private val typeStates = RecordsUiState.TABS.associateWith { TypeState() }.toMutableMap()
        private var selected: CardType = RecordsUiState.TABS.first()
        private var lifetime: LifetimeStats? = null
        private var animateCountUp: Boolean = false
        private var dueCount: Int = 0

        private val _uiState = MutableStateFlow(RecordsUiState())
        val uiState: StateFlow<RecordsUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                lifetime = lifetimeStatsSource.lifetime()
                // 카운트업: 실데이터가 있고(스텁 아님) 세션 최초 진입일 때만 애니메이션.
                animateCountUp = lifetime != null && countUpGate.consumeFirstEntry()
                dueCount = reviewSource.dueCount(reviewClock.nowMs())
                publish()
            }
            analytics.tabView(selected)
            loadFirstPage(selected)
        }

        /** 세그먼트 선택. 같은 탭이면 no-op. 미로드 탭이면 첫 페이지를 로드한다. */
        fun selectTab(cardType: CardType) {
            if (cardType == selected) return
            selected = cardType
            analytics.tabSwitch(cardType)
            if (!typeStates.getValue(cardType).loaded) {
                loadFirstPage(cardType)
            } else {
                publish()
            }
        }

        /** 현재 탭 다음 페이지(스크롤 끝 트리거). 로딩 중·종단이면 무시. */
        fun loadMore() {
            val state = typeStates.getValue(selected)
            if (state.loading || state.endReached || !state.loaded) return
            loadPage(selected, after = state.cursor)
        }

        /** 삭제(확인 다이얼로그 이후) = 톰스톤 + 낙관 제거. undo 없음(다이얼로그가 안전장치). */
        fun deleteCard(entry: SavedCardEntry) {
            val state = typeStates.getValue(selected)
            if (state.cards.none { it.cardId == entry.cardId }) return
            savedCardRepository.setDeleted(entry.cardId, entry.card.cardType, deleted = true)
            typeStates[selected] = state.copy(cards = state.cards.filterNot { it.cardId == entry.cardId })
            analytics.deleteCard(entry.card.cardType, undone = false)
            publish()
        }

        private fun loadFirstPage(cardType: CardType) {
            typeStates[cardType] = typeStates.getValue(cardType).copy(loaded = true)
            loadPage(cardType, after = null)
        }

        private fun loadPage(
            cardType: CardType,
            after: DocumentSnapshot?,
        ) {
            typeStates[cardType] = typeStates.getValue(cardType).copy(loading = true)
            publish()
            viewModelScope.launch {
                val page = querySource.page(cardType, after)
                val state = typeStates.getValue(cardType)
                typeStates[cardType] =
                    state.copy(
                        cards = if (after == null) page.entries else state.cards + page.entries,
                        cursor = page.cursor ?: state.cursor,
                        endReached = page.endReached,
                        loading = false,
                        loaded = true,
                    )
                publish()
            }
        }

        private fun publish() {
            val state = typeStates.getValue(selected)
            _uiState.value =
                RecordsUiState(
                    selected = selected,
                    cards = state.cards,
                    loading = state.loading,
                    endReached = state.endReached,
                    lifetime = lifetime,
                    animateCountUp = animateCountUp,
                    dueCount = dueCount,
                )
        }
    }

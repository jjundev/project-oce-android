package com.jjundev.oneclickeng.feature.records

import com.jjundev.oneclickeng.feature.session.saved.CardType

/**
 * 기록 탭 화면 상태. 탭은 3종 고정 순서(표현/단어/문장 = EXPRESSION/WORD/SENTENCE, R2), [selected] 탭의
 * 누적 [cards] 를 커서 페이지네이션으로 채운다.
 */
data class RecordsUiState(
    val selected: CardType = CardType.EXPRESSION,
    val cards: List<SavedCardEntry> = emptyList(),
    val loading: Boolean = true,
    val endReached: Boolean = false,
    val lifetime: LifetimeStats? = null,
    /** 헤더 카운트업 애니메이션 여부. 스텁이거나 세션 최초 진입이 아니면 false(정적 스냅). */
    val animateCountUp: Boolean = false,
) {
    /** 기록 탭 3종 세그먼트 순서(R2). */
    val tabs: List<CardType> get() = TABS

    companion object {
        val TABS = listOf(CardType.EXPRESSION, CardType.WORD, CardType.SENTENCE)
    }
}

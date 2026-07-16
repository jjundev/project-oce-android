package com.jjundev.oneclickeng.feature.records

import com.google.firebase.firestore.DocumentSnapshot
import com.jjundev.oneclickeng.feature.review.FakeReviewSource
import com.jjundev.oneclickeng.feature.review.data.ReviewClock
import com.jjundev.oneclickeng.feature.session.saved.CardType
import com.jjundev.oneclickeng.feature.session.saved.FakeSavedCardRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsRefreshingStateTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    // 2번째(refresh) page() 호출을 gate 로 막아 refreshing==true 를 관측한다.
    private class BlockingQuerySource(private val gate: CompletableDeferred<Unit>) : SavedCardQuerySource {
        private var firstDone = false
        override suspend fun page(cardType: CardType, after: DocumentSnapshot?, limit: Int): SavedCardPage {
            if (firstDone) gate.await()
            firstDone = true
            return SavedCardPage(entries = emptyList(), cursor = null, endReached = true)
        }
    }

    // 특정 탭의 2번째 이후 page() 호출(=refresh 재조회)만 gate 로 막는다. 다른 탭의 최초 로드는 막지 않아,
    // "3개 탭 모두 loaded=true" 인 전제를 세팅한 뒤 특정 탭만 느린 재조회 상태로 만들 수 있다.
    private class BlockOnRefreshQuerySource(
        private val blockCardType: CardType,
        private val gate: CompletableDeferred<Unit>,
    ) : SavedCardQuerySource {
        private val callCounts = mutableMapOf<CardType, Int>()
        override suspend fun page(cardType: CardType, after: DocumentSnapshot?, limit: Int): SavedCardPage {
            val count = (callCounts[cardType] ?: 0) + 1
            callCounts[cardType] = count
            if (cardType == blockCardType && count > 1) gate.await()
            return SavedCardPage(entries = emptyList(), cursor = null, endReached = true)
        }
    }

    private fun vm(query: SavedCardQuerySource) = RecordsViewModel(
        querySource = query,
        savedCardRepository = FakeSavedCardRepository(),
        lifetimeStatsSource = object : LifetimeStatsSource { override suspend fun lifetime(): LifetimeStats? = null },
        analytics = object : HistoryAnalytics {
            override fun tabView(cardType: CardType) = Unit
            override fun tabSwitch(cardType: CardType) = Unit
            override fun deleteCard(cardType: CardType, undone: Boolean) = Unit
        },
        countUpGate = HistoryCountUpGate(),
        reviewSource = FakeReviewSource(),
        reviewClock = object : ReviewClock { override fun nowMs() = 0L },
    )

    @Test fun refresh_setsRefreshingTrueThenFalseOnCompletion() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val viewModel = vm(BlockingQuerySource(gate))
        advanceUntilIdle()
        assertFalse("not refreshing after initial load", viewModel.uiState.value.refreshing)

        viewModel.refresh()
        advanceUntilIdle()
        assertTrue("refreshing while reload in flight", viewModel.uiState.value.refreshing)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse("refreshing cleared on completion", viewModel.uiState.value.refreshing)
    }

    // 회귀: 표현 탭에서 당겨서-새로고침이 진행 중일 때(느린 Firestore 재조회) 단어 탭으로 전환하면,
    // 표현 탭의 완료 가드(cardType == selected)가 영원히 false 로 남아 refreshing 이 절대 안 풀리던 버그.
    // selectTab 이 refreshing=false 를 직접 던지지 않으면 이 테스트는 실패한다(구버전 재현 확인됨).
    @Test fun tabSwitch_duringRefresh_clearsRefreshingImmediately() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val viewModel = vm(BlockOnRefreshQuerySource(CardType.EXPRESSION, gate))
        advanceUntilIdle() // init 이 기본 선택 탭(EXPRESSION)을 최초 로드.

        // 나머지 탭도 한 번씩 방문시켜 3개 탭 모두 loaded=true 로 만든다.
        viewModel.selectTab(CardType.WORD)
        advanceUntilIdle()
        viewModel.selectTab(CardType.SENTENCE)
        advanceUntilIdle()
        viewModel.selectTab(CardType.EXPRESSION) // 이미 loaded=true → publish() 만, page() 호출 없음.
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()
        assertTrue("refreshing while reload in flight", viewModel.uiState.value.refreshing)

        // 표현 탭의 refresh() 재조회가 gate 에 막혀 있는 채로 단어 탭으로 전환한다.
        viewModel.selectTab(CardType.WORD)
        advanceUntilIdle()
        assertFalse(
            "tab switch must abandon the stuck refresh gesture",
            viewModel.uiState.value.refreshing,
        )

        // 막혀 있던 표현 탭 로드가 뒤늦게 끝나도(가드가 no-op) 크래시 없이 정상 완료된다.
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse("still not refreshing after stale load completes", viewModel.uiState.value.refreshing)
    }
}

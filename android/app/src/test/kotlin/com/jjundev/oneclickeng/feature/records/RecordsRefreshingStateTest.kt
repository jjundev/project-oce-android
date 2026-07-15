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
}

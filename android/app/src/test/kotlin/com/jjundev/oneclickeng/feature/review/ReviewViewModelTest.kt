package com.jjundev.oneclickeng.feature.review

import android.app.Application
import com.jjundev.oneclickeng.feature.review.data.ReviewClock
import com.jjundev.oneclickeng.feature.review.data.ReviewItem
import com.jjundev.oneclickeng.feature.review.data.ReviewPhase
import com.jjundev.oneclickeng.feature.session.saved.FakeSavedCardRepository
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class ReviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val fixedNow = 1_000_000_000_000L
    private val day = 86_400_000L
    private val clock = object : ReviewClock { override fun nowMs() = fixedNow }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val expr =
        ReviewItem("e1", SavedCard.Expression("natural", "질문?", "before x", "after y", "설명"), review = null)
    private val word = ReviewItem("w1", SavedCard.Word("grasp", "이해하다", "ex", "예"), review = null)

    private fun vm(items: List<ReviewItem>, repo: FakeSavedCardRepository = FakeSavedCardRepository()) =
        ReviewViewModel(
            reviewSource = FakeReviewSource(items),
            clock = clock,
            savedCardRepository = repo,
            speak = {},
        ) to repo

    @Test
    fun `loads pool and starts on first card with type-specific phase`() = runTest(dispatcher) {
        val (viewModel, _) = vm(listOf(expr, word))
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.items.size)
        assertEquals(ReviewPhase.Ask, viewModel.uiState.value.phase)
    }

    @Test
    fun `quiz correct pick records srs box 1 and reveals`() = runTest(dispatcher) {
        val (viewModel, repo) = vm(listOf(expr))
        advanceUntilIdle()
        viewModel.pick(EXPRESSION_CORRECT_INDEX)
        assertEquals(ReviewPhase.Reveal, viewModel.uiState.value.phase)
        assertEquals(1, viewModel.uiState.value.done)
        assertEquals(1, repo.srsUpdates.size)
        assertEquals(1, repo.srsUpdates.first().box)
        assertEquals(fixedNow + 1 * day, repo.srsUpdates.first().nextReviewAt)
    }

    @Test
    fun `flashcard reveal then done advances and finishes at end`() = runTest(dispatcher) {
        val (viewModel, repo) = vm(listOf(word))
        advanceUntilIdle()
        assertEquals(ReviewPhase.Front, viewModel.uiState.value.phase)
        viewModel.reveal()
        assertEquals(ReviewPhase.Back, viewModel.uiState.value.phase)
        viewModel.grade(correct = true)
        assertEquals(true, viewModel.uiState.value.finished)
        assertEquals(1, viewModel.uiState.value.done)
        assertEquals(1, repo.srsUpdates.size)
    }

    @Test
    fun `quiz wrong pick counts again and records box 1`() = runTest(dispatcher) {
        val (viewModel, repo) = vm(listOf(expr))
        advanceUntilIdle()
        viewModel.pick(0)
        assertEquals(1, viewModel.uiState.value.again)
        assertEquals(1, repo.srsUpdates.first().box)
        assertEquals(1, repo.srsUpdates.first().lapses)
    }
}

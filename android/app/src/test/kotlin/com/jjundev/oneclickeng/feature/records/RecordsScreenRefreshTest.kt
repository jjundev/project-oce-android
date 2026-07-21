package com.jjundev.oneclickeng.feature.records

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.firestore.DocumentSnapshot
import com.jjundev.oneclickeng.feature.review.FakeReviewSource
import com.jjundev.oneclickeng.feature.review.data.ReviewClock
import com.jjundev.oneclickeng.feature.session.saved.CardType
import com.jjundev.oneclickeng.feature.session.saved.FakeSavedCardRepository
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class RecordsScreenRefreshTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `screen refreshes cards when records destination reenters`() {
        val query = RecordingQuerySource()
        val viewModel = recordsViewModel(query)
        val owner = TestLifecycleOwner()
        val screenVisible = mutableStateOf(true)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        composeRule.setContent {
            if (screenVisible.value) {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    OceTheme { RecordsScreen(viewModel = viewModel) }
                }
            }
        }

        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        composeRule.waitForIdle()
        assertEquals(1, query.requests.size)
        assertEquals(listOf("old"), viewModel.uiState.value.cards.map { it.cardId })

        query.entries = listOf(expression("new"), expression("old"))
        composeRule.runOnIdle { screenVisible.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { screenVisible.value = true }
        composeRule.waitForIdle()

        assertEquals(2, query.requests.size)
        assertEquals(listOf("new", "old"), viewModel.uiState.value.cards.map { it.cardId })
    }

    private fun recordsViewModel(query: SavedCardQuerySource) =
        RecordsViewModel(
            querySource = query,
            savedCardRepository = FakeSavedCardRepository(),
            lifetimeStatsSource =
                object : LifetimeStatsSource {
                    override suspend fun lifetime(): LifetimeStats? = null
                },
            analytics =
                object : HistoryAnalytics {
                    override fun tabView(cardType: CardType) = Unit

                    override fun tabSwitch(cardType: CardType) = Unit

                    override fun deleteCard(
                        cardType: CardType,
                        undone: Boolean,
                    ) = Unit
                },
            countUpGate = HistoryCountUpGate(),
            reviewSource = FakeReviewSource(),
            reviewClock =
                object : ReviewClock {
                    override fun nowMs(): Long = 0L
                },
        )

    private fun expression(id: String) =
        SavedCardEntry(
            cardId = id,
            card =
                SavedCard.Expression(
                    type = "natural",
                    koreanPrompt = "",
                    before = "",
                    after = "after-$id",
                    explanation = "",
                ),
        )

    private inner class RecordingQuerySource : SavedCardQuerySource {
        var entries = listOf(expression("old"))
        val requests = mutableListOf<DocumentSnapshot?>()

        override suspend fun page(
            cardType: CardType,
            after: DocumentSnapshot?,
            limit: Int,
        ): SavedCardPage {
            requests += after
            return SavedCardPage(entries = entries, cursor = null, endReached = true)
        }
    }

    private class TestLifecycleOwner : LifecycleOwner {
        override val lifecycle: LifecycleRegistry = LifecycleRegistry(this)
    }
}

package com.jjundev.oneclickeng.feature.records

import android.app.Application
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 7: 기록 탭 pull-to-refresh 배선. [RecordsScreenRefreshTest] 의 하네스(fake VM 구성)를 그대로
 * 재사용하고, 하단 resume 트리거 대신 상단 origin 스와이프가 [RecordsViewModel.refresh] 를 태우는지만 본다.
 * 제스처 기반(스와이프)이 [OverscrollRefreshBoxTest] 선례와 정합돼 결정적이라 우선한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RecordsScreenPullRefreshTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `pull down at top triggers a reload`() {
        val query = CountingQuerySource(cardCount = 3)
        val viewModel = recordsViewModel(query)

        composeRule.setContent {
            OceTheme {
                RecordsScreen(viewModel = viewModel, modifier = Modifier.testTag("records"))
            }
        }
        composeRule.waitForIdle()

        val before = query.calls
        composeRule.onNodeWithTag("records").performTouchInput {
            swipeDown(startY = 100f, endY = 900f, durationMillis = 300)
        }
        composeRule.waitForIdle()

        assertTrue("pull triggered a reload page() call", query.calls > before)
    }

    private fun recordsViewModel(query: SavedCardQuerySource) =
        RecordsViewModel(
            querySource = query,
            savedCardRepository = FakeSavedCardRepository(),
            lifetimeStatsSource = object : LifetimeStatsSource {
                override suspend fun lifetime(): LifetimeStats? = null
            },
            analytics = object : HistoryAnalytics {
                override fun tabView(cardType: CardType) = Unit
                override fun tabSwitch(cardType: CardType) = Unit
                override fun deleteCard(cardType: CardType, undone: Boolean) = Unit
            },
            countUpGate = HistoryCountUpGate(),
            reviewSource = FakeReviewSource(),
            reviewClock = object : ReviewClock {
                override fun nowMs(): Long = 0L
            },
        )

    private fun expression(id: String) =
        SavedCardEntry(
            cardId = id,
            card = SavedCard.Expression(
                type = "natural",
                koreanPrompt = "",
                before = "",
                after = "after-$id",
                explanation = "",
            ),
        )

    /** [SavedCardQuerySource.page] 호출 횟수를 세는 페이크 — 매 호출마다 같은 [cardCount] 장의 페이지를 반환한다. */
    private inner class CountingQuerySource(private val cardCount: Int) : SavedCardQuerySource {
        var calls = 0
            private set

        override suspend fun page(
            cardType: CardType,
            after: DocumentSnapshot?,
            limit: Int,
        ): SavedCardPage {
            calls += 1
            val entries = (1..cardCount).map { expression("card-$it") }
            return SavedCardPage(entries = entries, cursor = null, endReached = true)
        }
    }
}

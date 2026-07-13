package com.jjundev.oneclickeng.feature.records

import android.app.Application
import com.google.firebase.firestore.DocumentSnapshot
import com.jjundev.oneclickeng.feature.session.saved.CardType
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [RecordsViewModel] 삭제(톰스톤 write + 낙관 로컬 제거) + 탭 전환 계측 검증(M2-05). Firestore 없이 fake 읽기/쓰기
 * seam 으로 deleteCard 호출 시 카드 목록에서 즉시 사라지는 낙관 변이와 톰스톤 write, 삭제 계측 호출을
 * 반증가능하게 고정한다(undo 없음 — 롱프레스→확인 다이얼로그로 확정된 삭제만 다룬다).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class RecordsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // 탭↔카드 타입 정합: EXPRESSION 탭엔 Expression 카드, WORD 탭엔 Word 카드를 넣는다(운영에선 항상 일치 —
    // ViewModel 은 카드의 cardType 으로 삭제 계측 타깃을 정하므로 fixture 도 정합해야 한다).
    private fun expr(id: String) =
        SavedCardEntry(
            id,
            SavedCard.Expression(
                type = "natural",
                koreanPrompt = "",
                before = "",
                after = "after-$id",
                explanation = "",
            ),
        )

    private fun word(id: String) =
        SavedCardEntry(
            id,
            SavedCard.Word(english = "en-$id", korean = "ko-$id", exampleEnglish = "", exampleKorean = ""),
        )

    private fun vm(
        query: FakeQuerySource = FakeQuerySource(),
        repo: FakeSavedCardRepository = FakeSavedCardRepository(),
        analytics: RecordingHistoryAnalytics = RecordingHistoryAnalytics(),
        lifetime: LifetimeStats? = null,
    ) = RecordsViewModel(query, repo, FakeLifetimeStatsSource(lifetime), analytics, HistoryCountUpGate())

    @Test
    fun `init loads first page of default tab and logs tab_view`() =
        runTest(dispatcher) {
            val query = FakeQuerySource(mapOf(CardType.EXPRESSION to emptyList()))
            val analytics = RecordingHistoryAnalytics()
            val viewModel = vm(query = query, analytics = analytics)

            advanceUntilIdle()

            assertEquals(CardType.EXPRESSION, viewModel.uiState.value.selected)
            assertEquals(listOf(CardType.EXPRESSION), analytics.views)
            assertFalse(viewModel.uiState.value.loading)
        }

    @Test
    fun `deleteCard tombstones, optimistically removes, and logs delete`() =
        runTest(dispatcher) {
            val query = FakeQuerySource(mapOf(CardType.EXPRESSION to listOf(expr("a"), expr("b"), expr("c"))))
            val repo = FakeSavedCardRepository()
            val analytics = RecordingHistoryAnalytics()
            val viewModel = vm(query = query, repo = repo, analytics = analytics)
            advanceUntilIdle()

            viewModel.deleteCard(expr("b"))

            assertEquals(listOf("a", "c"), viewModel.uiState.value.cards.map { it.cardId })
            assertEquals(1, repo.deletes.size)
            assertEquals("b" to true, repo.deletes.first().cardId to repo.deletes.first().deleted)
            assertEquals(listOf(CardType.EXPRESSION to false), analytics.deletes)
        }

    @Test
    fun `selecting a new tab logs tab_switch and loads that tab`() =
        runTest(dispatcher) {
            val query =
                FakeQuerySource(
                    mapOf(
                        CardType.EXPRESSION to listOf(expr("e1")),
                        CardType.WORD to listOf(word("w1"), word("w2")),
                    ),
                )
            val analytics = RecordingHistoryAnalytics()
            val viewModel = vm(query = query, analytics = analytics)
            advanceUntilIdle()

            viewModel.selectTab(CardType.WORD)
            advanceUntilIdle()

            assertEquals(CardType.WORD, viewModel.uiState.value.selected)
            assertEquals(listOf(CardType.WORD), analytics.switches)
            assertEquals(listOf("w1", "w2"), viewModel.uiState.value.cards.map { it.cardId })
        }

    @Test
    fun `reselecting the current tab is a no-op`() =
        runTest(dispatcher) {
            val analytics = RecordingHistoryAnalytics()
            val viewModel = vm(analytics = analytics)
            advanceUntilIdle()

            viewModel.selectTab(CardType.EXPRESSION)

            assertTrue(analytics.switches.isEmpty())
        }

    @Test
    fun `stub lifetime keeps count-up static`() =
        runTest(dispatcher) {
            val viewModel = vm(lifetime = null)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.lifetime)
            assertFalse(viewModel.uiState.value.animateCountUp)
        }

    @Test
    fun `real lifetime on first entry animates count-up`() =
        runTest(dispatcher) {
            val viewModel = vm(lifetime = LifetimeStats(xp = 100, studyMinutes = 30, studyDays = 3))
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.animateCountUp)
        }
}

private class FakeQuerySource(
    private val byType: Map<CardType, List<SavedCardEntry>> = emptyMap(),
) : SavedCardQuerySource {
    override suspend fun page(
        cardType: CardType,
        after: DocumentSnapshot?,
        limit: Int,
    ): SavedCardPage = SavedCardPage(entries = byType[cardType].orEmpty(), cursor = null, endReached = true)
}

private class FakeLifetimeStatsSource(private val value: LifetimeStats?) : LifetimeStatsSource {
    override suspend fun lifetime(): LifetimeStats? = value
}

private class RecordingHistoryAnalytics : HistoryAnalytics {
    val views = mutableListOf<CardType>()
    val switches = mutableListOf<CardType>()
    val deletes = mutableListOf<Pair<CardType, Boolean>>()

    override fun tabView(cardType: CardType) {
        views += cardType
    }

    override fun tabSwitch(cardType: CardType) {
        switches += cardType
    }

    override fun deleteCard(
        cardType: CardType,
        undone: Boolean,
    ) {
        deletes += cardType to undone
    }
}

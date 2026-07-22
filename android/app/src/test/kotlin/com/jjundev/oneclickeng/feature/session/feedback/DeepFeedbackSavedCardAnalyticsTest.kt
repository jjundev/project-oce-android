package com.jjundev.oneclickeng.feature.session.feedback

import com.jjundev.oneclickeng.core.network.ConceptualBridgeDto
import com.jjundev.oneclickeng.core.network.DeepFeedbackStream
import com.jjundev.oneclickeng.core.network.FeedbackDeepEvent
import com.jjundev.oneclickeng.core.network.FeedbackDeepRequest
import com.jjundev.oneclickeng.core.network.ParaphraseItemDto
import com.jjundev.oneclickeng.core.network.ParaphrasingDto
import com.jjundev.oneclickeng.core.network.ToneLevelDto
import com.jjundev.oneclickeng.core.network.ToneStyleDto
import com.jjundev.oneclickeng.core.network.VennCircleDto
import com.jjundev.oneclickeng.core.network.VennDto
import com.jjundev.oneclickeng.core.network.VennIntersectionDto
import com.jjundev.oneclickeng.feature.session.analytics.RecordingSavedCardAnalytics
import com.jjundev.oneclickeng.feature.session.analytics.SavedCardAnalytics
import com.jjundev.oneclickeng.feature.session.saved.CardType
import com.jjundev.oneclickeng.feature.session.saved.FakeSavedCardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// Prefixed `SavedCardAnalyticsDeep*` — top-level `private` classes collide by name across files in the
// same package in Kotlin (DeepFeedbackCoordinatorTest.kt already owns `FakeDeepStream`; see
// SummarySavedCardAnalyticsTest.kt for the sibling-package precedent of this same constraint).

/** Fake stream: each events() call yields a fresh channel-backed cold flow the test drives. */
private class SavedCardAnalyticsDeepStream : DeepFeedbackStream {
    private val channels = mutableListOf<Channel<FeedbackDeepEvent>>()

    override fun events(request: FeedbackDeepRequest): Flow<FeedbackDeepEvent> {
        val channel = Channel<FeedbackDeepEvent>(Channel.UNLIMITED)
        channels += channel
        return channel.consumeAsFlow()
    }

    fun push(event: FeedbackDeepEvent) = channels.last().trySend(event)
}

@OptIn(ExperimentalCoroutinesApi::class)
class DeepFeedbackSavedCardAnalyticsTest {
    private lateinit var stream: SavedCardAnalyticsDeepStream

    /** Mirrors `DeepFeedbackCoordinatorTest`'s construction, threading [savedCardAnalytics] into the
     * (now +1-arg) constructor. Ties the coordinator's internal scope to this [TestScope]'s scheduler so
     * [runCurrent] here actually advances it (mirrors `DeepFeedbackCoordinatorTest.coordScope()`). */
    private fun TestScope.newCoordinator(saved: SavedCardAnalytics): DeepFeedbackCoordinator {
        stream = SavedCardAnalyticsDeepStream()
        val scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        return DeepFeedbackCoordinator(stream, FakeSavedCardRepository(), scope, saved)
    }

    /** Drives [coordinator] to a Ready state carrying paraphrases for [sessionId] (mirrors
     * `DeepFeedbackCoordinatorTest`'s `begin()` + section pushes), returning the level-2 paraphrase. */
    private fun TestScope.driveToDeepReadyWithParaphrase(
        coordinator: DeepFeedbackCoordinator,
        sessionId: String,
    ): Paraphrase {
        coordinator.start(
            sessionId = sessionId,
            turnIndex = 4,
            koreanPrompt = "커피 주세요",
            userEnglish = "One coffee",
            referenceEnglish = "Can I get a coffee?",
            level = "normal",
        )
        runCurrent()
        stream.push(
            FeedbackDeepEvent.Section.ConceptualBridge(
                ConceptualBridgeDto(
                    literalTranslation = "직역",
                    explanation = "설명",
                    venn =
                        VennDto(
                            guide = "안내",
                            leftCircle = VennCircleDto("get", listOf("얻다")),
                            rightCircle = VennCircleDto("order", listOf("주문하다")),
                            intersection = VennIntersectionDto(listOf("받다")),
                        ),
                ),
            ),
        )
        runCurrent()
        stream.push(
            FeedbackDeepEvent.Section.ToneStyle(
                ToneStyleDto(defaultLevel = 2, levels = (0..4).map { ToneLevelDto(it, "s$it", "번역$it") }),
            ),
        )
        stream.push(
            FeedbackDeepEvent.Section.Paraphrasing(
                ParaphrasingDto(
                    items =
                        listOf(
                            ParaphraseItemDto(1, "Beginner", "p1", "번역1"),
                            ParaphraseItemDto(2, "Intermediate", "p2", "번역2"),
                            ParaphraseItemDto(3, "Advanced", "p3", "번역3"),
                        ),
                ),
            ),
        )
        runCurrent()
        return (coordinator.state.value as DeepFeedbackState.Ready).paraphrasing.items[1]
    }

    @Test
    fun `bookmarking a paraphrase logs one saved_card_create deep_feedback SENTENCE, unbookmark logs nothing`() =
        runTest {
            val saved = RecordingSavedCardAnalytics()
            val coordinator = newCoordinator(saved = saved)
            val paraphrase = driveToDeepReadyWithParaphrase(coordinator, sessionId = "s1")
            coordinator.toggleBookmark(paraphrase) // added -> logs
            coordinator.toggleBookmark(paraphrase) // remove -> no log

            assertEquals(
                listOf(
                    RecordingSavedCardAnalytics.Call("s1", SavedCardAnalytics.SURFACE_DEEP_FEEDBACK, CardType.SENTENCE),
                ),
                saved.calls,
            )
        }
}

package com.jjundev.oneclickeng.feature.onboarding.google

import android.app.Application
import com.jjundev.oneclickeng.core.auth.GoogleAccountLinker
import com.jjundev.oneclickeng.core.auth.LinkOutcome
import com.jjundev.oneclickeng.feature.onboarding.OnboardingAnalytics
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

/**
 * [GoogleLinkViewModel] outcome→state 매핑(M3-03, 결정 B5·17). FR-3a(Promoted)/FR-3b(Merged) 는 모두 Success,
 * signIn 전 실패는 Error(afterSignIn=false), signIn 후 merge 실패는 Error(afterSignIn=true) 로 갈라져 in-session
 * 이관 재시도를 구분한다. Firebase 없이 fake linker 로 반증가능하게 고정(레포 관례 = mockk 미사용).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class GoogleLinkViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        linker: GoogleAccountLinker,
        analytics: OnboardingAnalytics = RecordingAnalytics(),
    ) = GoogleLinkViewModel(linker, analytics)

    @Test
    fun `FR-3a promotion maps to Success and logs succeeded`() =
        runTest(dispatcher) {
            val analytics = RecordingAnalytics()
            val model = vm(FakeLinker(LinkOutcome.Promoted), analytics)

            model.linkGoogle("token", "s1")
            advanceUntilIdle()

            assertEquals(LinkUiState.Success, model.uiState.value)
            assertEquals(listOf("succeeded:s1"), analytics.events)
        }

    @Test
    fun `FR-3b merge maps to Success and logs conflict_merged`() =
        runTest(dispatcher) {
            val analytics = RecordingAnalytics()
            val model = vm(FakeLinker(LinkOutcome.Merged), analytics)

            model.linkGoogle("token", "s1")
            advanceUntilIdle()

            assertEquals(LinkUiState.Success, model.uiState.value)
            assertEquals(listOf("merged:s1"), analytics.events)
        }

    @Test
    fun `failure before sign-in is a retryable guest error (afterSignIn false)`() =
        runTest(dispatcher) {
            val model = vm(FakeLinker(LinkOutcome.FailedAsGuest))

            model.linkGoogle("token", "s1")
            advanceUntilIdle()

            assertEquals(LinkUiState.Error(afterSignIn = false), model.uiState.value)
        }

    @Test
    fun `merge failure after sign-in is a post-sign-in error (afterSignIn true)`() =
        runTest(dispatcher) {
            val model = vm(FakeLinker(LinkOutcome.FailedAfterSignIn))

            model.linkGoogle("token", "s1")
            advanceUntilIdle()

            assertEquals(LinkUiState.Error(afterSignIn = true), model.uiState.value)
        }

    @Test
    fun `retryMerge success maps to Success`() =
        runTest(dispatcher) {
            val model = vm(FakeLinker(retry = LinkOutcome.Merged))

            model.retryMerge("s1")
            advanceUntilIdle()

            assertEquals(LinkUiState.Success, model.uiState.value)
        }

    @Test
    fun `retryMerge still-failing stays post-sign-in error`() =
        runTest(dispatcher) {
            val model = vm(FakeLinker(retry = LinkOutcome.FailedAfterSignIn))

            model.retryMerge("s1")
            advanceUntilIdle()

            assertEquals(LinkUiState.Error(afterSignIn = true), model.uiState.value)
        }

    @Test
    fun `credential cancel returns to Idle without an error`() {
        val model = vm(FakeLinker(LinkOutcome.Promoted))

        model.onCredentialFlowStarted()
        assertEquals(LinkUiState.Linking, model.uiState.value)
        model.onCredentialCancelled()

        assertEquals(LinkUiState.Idle, model.uiState.value)
    }

    // --- fakes -------------------------------------------------------------

    private class FakeLinker(
        private val link: LinkOutcome = LinkOutcome.Promoted,
        private val retry: LinkOutcome = LinkOutcome.Merged,
    ) : GoogleAccountLinker {
        override suspend fun linkGuest(googleIdToken: String): LinkOutcome = link

        override suspend fun retryPendingMerge(): LinkOutcome = retry
    }

    private class RecordingAnalytics : OnboardingAnalytics {
        val events = mutableListOf<String>()

        override fun onboardingStarted(isReturning: Boolean) = Unit

        override fun levelSelected(level: String) = Unit

        override fun topicSelected(
            topicId: String,
            beginnerFriendly: Boolean,
        ) = Unit

        override fun googleSavePromptShown(sessionId: String) = Unit

        override fun googleLinkSkipped(sessionId: String) = Unit

        override fun googleLinkSucceeded(sessionId: String) {
            events += "succeeded:$sessionId"
        }

        override fun googleLinkConflictMerged(sessionId: String) {
            events += "merged:$sessionId"
        }

        override fun googleLinkFailed(sessionId: String) {
            events += "failed:$sessionId"
        }
    }
}

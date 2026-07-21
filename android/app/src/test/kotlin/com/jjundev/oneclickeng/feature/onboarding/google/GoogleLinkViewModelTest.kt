package com.jjundev.oneclickeng.feature.onboarding.google

import android.app.Application
import com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink
import com.jjundev.oneclickeng.core.auth.AccountResetBus
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.core.auth.GoogleAccountLinker
import com.jjundev.oneclickeng.core.auth.LinkOutcome
import com.jjundev.oneclickeng.feature.onboarding.OnboardingAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        resetBus: AccountResetBus = AccountResetBus(),
        analyticsSink: RecordingAnalyticsSink = RecordingAnalyticsSink(),
        authRepository: AuthRepository = FakeLinkAuthRepository(),
    ) = GoogleLinkViewModel(linker, analytics, resetBus, analyticsSink, authRepository)

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
    fun `FR-3a promotion stitches the linked identity into analytics`() =
        runTest(dispatcher) {
            val sink = RecordingAnalyticsSink()
            val model =
                vm(
                    linker = FakeLinker(LinkOutcome.Promoted),
                    analyticsSink = sink,
                    authRepository = FakeLinkAuthRepository(uid = "linked-uid"),
                )

            model.linkGoogle("token", "s1")
            advanceUntilIdle()

            assertEquals("linked-uid", sink.userId)
            assertEquals("linked", sink.userProperties["auth_state"])
        }

    @Test
    fun `FR-3b merge stitches the linked identity into analytics`() =
        runTest(dispatcher) {
            val sink = RecordingAnalyticsSink()
            val model =
                vm(
                    linker = FakeLinker(LinkOutcome.Merged),
                    analyticsSink = sink,
                    authRepository = FakeLinkAuthRepository(uid = "linked-uid"),
                )

            model.linkGoogle("token", "s1")
            advanceUntilIdle()

            assertEquals("linked-uid", sink.userId)
            assertEquals("linked", sink.userProperties["auth_state"])
        }

    @Test
    fun `retryMerge success stitches the linked identity into analytics`() =
        runTest(dispatcher) {
            val sink = RecordingAnalyticsSink()
            val model =
                vm(
                    linker = FakeLinker(retry = LinkOutcome.Merged),
                    analyticsSink = sink,
                    authRepository = FakeLinkAuthRepository(uid = "linked-uid"),
                )

            model.retryMerge("s1")
            advanceUntilIdle()

            assertEquals("linked-uid", sink.userId)
            assertEquals("linked", sink.userProperties["auth_state"])
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
    fun `reauth link promotion sets Success, logs reauth_succeeded, and signals account reset`() =
        runTest(dispatcher) {
            val analytics = RecordingAnalytics()
            val resetBus = AccountResetBus()
            var signaled = false
            val collector = launch { resetBus.events.collect { signaled = true } }
            val model = vm(FakeLinker(LinkOutcome.Promoted), analytics, resetBus)

            model.linkGoogleForReauth("token")
            advanceUntilIdle()

            assertEquals(LinkUiState.Success, model.uiState.value)
            assertEquals(listOf("reauth_succeeded"), analytics.events)
            assertTrue(signaled)
            collector.cancel()
        }

    @Test
    fun `reauth link merge sets Success, logs reauth_merged, and signals account reset`() =
        runTest(dispatcher) {
            val analytics = RecordingAnalytics()
            val resetBus = AccountResetBus()
            var signaled = false
            val collector = launch { resetBus.events.collect { signaled = true } }
            val model = vm(FakeLinker(LinkOutcome.Merged), analytics, resetBus)

            model.linkGoogleForReauth("token")
            advanceUntilIdle()

            assertEquals(LinkUiState.Success, model.uiState.value)
            assertEquals(listOf("reauth_merged"), analytics.events)
            assertTrue(signaled)
            collector.cancel()
        }

    @Test
    fun `reauth link failure before sign-in is a retryable guest error and does not signal reset`() =
        runTest(dispatcher) {
            val analytics = RecordingAnalytics()
            val resetBus = AccountResetBus()
            var signaled = false
            val collector = launch { resetBus.events.collect { signaled = true } }
            val model = vm(FakeLinker(LinkOutcome.FailedAsGuest), analytics, resetBus)

            model.linkGoogleForReauth("token")
            advanceUntilIdle()

            assertEquals(LinkUiState.Error(afterSignIn = false), model.uiState.value)
            assertEquals(listOf("reauth_failed"), analytics.events)
            assertEquals(false, signaled)
            collector.cancel()
        }

    @Test
    fun `reauth merge failure after sign-in is a post-sign-in error and does not signal reset`() =
        runTest(dispatcher) {
            val resetBus = AccountResetBus()
            var signaled = false
            val collector = launch { resetBus.events.collect { signaled = true } }
            val model = vm(FakeLinker(LinkOutcome.FailedAfterSignIn), resetBus = resetBus)

            model.linkGoogleForReauth("token")
            advanceUntilIdle()

            assertEquals(LinkUiState.Error(afterSignIn = true), model.uiState.value)
            assertEquals(false, signaled)
            collector.cancel()
        }

    @Test
    fun `retryMergeForReauth success sets Success and signals account reset`() =
        runTest(dispatcher) {
            val resetBus = AccountResetBus()
            var signaled = false
            val collector = launch { resetBus.events.collect { signaled = true } }
            val model = vm(FakeLinker(retry = LinkOutcome.Merged), resetBus = resetBus)

            model.retryMergeForReauth()
            advanceUntilIdle()

            assertEquals(LinkUiState.Success, model.uiState.value)
            assertTrue(signaled)
            collector.cancel()
        }

    @Test
    fun `retryMergeForReauth still-failing stays post-sign-in error without signaling`() =
        runTest(dispatcher) {
            val resetBus = AccountResetBus()
            var signaled = false
            val collector = launch { resetBus.events.collect { signaled = true } }
            val model = vm(FakeLinker(retry = LinkOutcome.FailedAfterSignIn), resetBus = resetBus)

            model.retryMergeForReauth()
            advanceUntilIdle()

            assertEquals(LinkUiState.Error(afterSignIn = true), model.uiState.value)
            assertEquals(false, signaled)
            collector.cancel()
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

    /** Minimal [AuthRepository] fake — only [currentUid] matters to the stitching seam under test. */
    private class FakeLinkAuthRepository(
        private val uid: String? = "guest-uid",
    ) : AuthRepository {
        override val currentUid: String? get() = uid

        override val isAnonymous: Boolean = false

        override suspend fun ensureSignedIn(): String = uid ?: error("no uid")
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

        override fun reauthLinkSucceeded() {
            events += "reauth_succeeded"
        }

        override fun reauthLinkConflictMerged() {
            events += "reauth_merged"
        }

        override fun reauthLinkFailed() {
            events += "reauth_failed"
        }
    }
}

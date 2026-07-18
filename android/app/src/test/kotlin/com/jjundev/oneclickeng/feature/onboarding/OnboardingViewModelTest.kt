package com.jjundev.oneclickeng.feature.onboarding

import android.app.Application
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.core.auth.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [OnboardingViewModel] 부수효과 검증(M3-02, 결정 4·9·15). 수용기준 "profile.level 저장(폐기 안 함)"의 seam
 * 호출을 fake 로 반증가능하게 고정한다: 레벨 탭 → `saveLevel(uid, level)` 호출 + `level_selected` 분석.
 * Firebase 없이 fake 리포지토리/분석으로 검증한다(레포 관례 = mockk 미사용). VM 이 실패 경로에서 `android.util.Log`
 * 를 쓰므로 Robolectric 로 돌린다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class OnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `level tap saves the picked level to profile and logs analytics`() =
        runTest(dispatcher) {
            val profile = FakeProfileRepository()
            val analytics = RecordingOnboardingAnalytics()
            val vm = OnboardingViewModel(FakeAuthRepository(uid = "uid-1"), profile, analytics)

            vm.onLevelSelected("hard")
            advanceUntilIdle()

            assertEquals("uid-1" to "hard", profile.savedLevel)
            assertEquals(listOf("hard"), analytics.levels)
        }

    @Test
    fun `level tap ensures sign-in when no current uid yet`() =
        runTest(dispatcher) {
            val profile = FakeProfileRepository()
            // currentUid null → ensureSignedIn() 로 폴백해도 저장은 반드시 일어난다(폐기 안 함).
            val vm =
                OnboardingViewModel(
                    FakeAuthRepository(uid = null, ensuredUid = "uid-ensured"),
                    profile,
                    RecordingOnboardingAnalytics(),
                )

            vm.onLevelSelected("normal")
            advanceUntilIdle()

            assertEquals("uid-ensured" to "normal", profile.savedLevel)
        }

    @Test
    fun `topic tap logs beginner-friendly true`() {
        val analytics = RecordingOnboardingAnalytics()
        val vm = OnboardingViewModel(FakeAuthRepository(uid = "uid-1"), FakeProfileRepository(), analytics)

        vm.onTopicSelected("cafe-order")

        assertEquals(listOf("cafe-order" to true), analytics.topics)
    }

    @Test
    fun `save failure does not crash the tap`() =
        runTest(dispatcher) {
            val profile = FakeProfileRepository(failSave = true)
            val vm = OnboardingViewModel(FakeAuthRepository(uid = "uid-1"), profile, RecordingOnboardingAnalytics())

            vm.onLevelSelected("easy")
            advanceUntilIdle()

            // saveLevel threw; VM swallows it (Firestore offline queue owns durability). Nothing persisted here.
            assertNull(profile.savedLevel)
        }

    // --- fakes -------------------------------------------------------------

    private class FakeAuthRepository(
        private val uid: String?,
        private val ensuredUid: String = "ensured",
    ) : AuthRepository {
        override val currentUid: String? = uid

        override suspend fun ensureSignedIn(): String = ensuredUid
    }

    private class FakeProfileRepository(
        private val failSave: Boolean = false,
    ) : ProfileRepository {
        var savedLevel: Pair<String, String>? = null

        override suspend fun ensureProfile(uid: String) = Unit

        override suspend fun saveLevel(
            uid: String,
            level: String,
        ) {
            if (failSave) error("simulated Firestore failure")
            savedLevel = uid to level
        }

        override suspend fun readLevel(uid: String): String? = null

        override suspend fun saveNickname(
            uid: String,
            nickname: String,
        ) = Unit

        override suspend fun readNickname(uid: String): String? = null
    }

    private class RecordingOnboardingAnalytics : OnboardingAnalytics {
        val levels = mutableListOf<String>()
        val topics = mutableListOf<Pair<String, Boolean>>()

        override fun onboardingStarted(isReturning: Boolean) = Unit

        override fun levelSelected(level: String) {
            levels += level
        }

        override fun topicSelected(
            topicId: String,
            beginnerFriendly: Boolean,
        ) {
            topics += topicId to beginnerFriendly
        }

        override fun googleSavePromptShown(sessionId: String) = Unit

        override fun googleLinkSkipped(sessionId: String) = Unit

        override fun googleLinkSucceeded(sessionId: String) = Unit

        override fun googleLinkConflictMerged(sessionId: String) = Unit

        override fun googleLinkFailed(sessionId: String) = Unit

        override fun reauthLinkSucceeded() = Unit

        override fun reauthLinkConflictMerged() = Unit

        override fun reauthLinkFailed() = Unit
    }
}

package com.jjundev.oneclickeng.feature.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.core.auth.ProfileRepository
import com.jjundev.oneclickeng.feature.onboarding.level.LevelQuestionScreen
import com.jjundev.oneclickeng.feature.onboarding.topic.OnboardingTopic
import com.jjundev.oneclickeng.feature.onboarding.topic.TopicQuestionScreen
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 온보딩 2문항 화면 계측(M3-02). 레벨 3카드·상황 6카드가 렌더되고, 탭이 선택 값을 상위 내비 콜백으로
 * 넘기는지 확인한다. Hilt 없이 fake seam 을 주입해 화면만 격리 검증한다.
 */
class OnboardingScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun fakeViewModel() =
        OnboardingViewModel(
            authRepository =
                object : AuthRepository {
                    override val currentUid: String = "uid-1"

                    override suspend fun ensureSignedIn(): String = "uid-1"
                },
            profileRepository =
                object : ProfileRepository {
                    override suspend fun ensureProfile(uid: String) = Unit

                    override suspend fun saveLevel(
                        uid: String,
                        level: String,
                    ) = Unit

                    override suspend fun readLevel(uid: String): String? = null

                    override suspend fun saveNickname(
                        uid: String,
                        nickname: String,
                    ) = Unit

                    override suspend fun readNickname(uid: String): String? = null
                },
            analytics = NoOpOnboardingAnalytics(),
        )

    @Test
    fun levelQuestionRendersThreeCardsAndReportsPick() {
        var picked: String? = null
        composeRule.setContent {
            OceTheme {
                LevelQuestionScreen(onLevelSelected = { picked = it }, viewModel = fakeViewModel())
            }
        }

        composeRule.onNodeWithText("쉬움").assertIsDisplayed()
        composeRule.onNodeWithText("보통").assertIsDisplayed()
        composeRule.onNodeWithText("어려움").assertIsDisplayed()

        composeRule.onNodeWithText("어려움").performClick()
        assertEquals("hard", picked)
    }

    @Test
    fun topicQuestionShowsCafeFirstAndReportsPick() {
        var picked: OnboardingTopic? = null
        composeRule.setContent {
            OceTheme {
                TopicQuestionScreen(onTopicSelected = { picked = it }, onBack = {}, viewModel = fakeViewModel())
            }
        }

        // 첫 카드 = 카페에서 주문하기(비강조지만 노출).
        composeRule.onNodeWithText("카페에서 주문하기").assertIsDisplayed()

        composeRule.onNodeWithText("호텔 체크인").performClick()
        assertEquals("hotel-checkin", picked?.id)
    }
}

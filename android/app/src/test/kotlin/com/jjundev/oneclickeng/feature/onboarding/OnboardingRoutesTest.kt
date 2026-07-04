package com.jjundev.oneclickeng.feature.onboarding

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 온보딩 생성 파라미터·실경로 빌더의 검증(M3-02, 결정 5·18). 대부분 값→값이지만 실경로 빌더가
 * `android.net.Uri.encode` 를 쓰므로 Robolectric 로 돌린다. 수용기준 "2문항 → 첫 세션(쉬움·5턴) 강제"의
 * 핵심 로직을 반증가능하게 고정한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class OnboardingRoutesTest {
    @Test
    fun `first session forces easy and 5 turns regardless of picked level`() {
        // 사용자가 hard 를 골라도 첫 세션은 easy·5턴.
        val params = onboardingGenParams(firstSession = true, userLevel = "hard")
        assertEquals("easy", params.level)
        assertEquals(5, params.length)
    }

    @Test
    fun `first session ignores the saved level for every choice`() {
        listOf("easy", "normal", "hard").forEach { picked ->
            val params = onboardingGenParams(firstSession = true, userLevel = picked)
            assertEquals("easy", params.level)
            assertEquals(5, params.length)
        }
    }

    @Test
    fun `repeat session uses the saved level and 10 turns`() {
        val params = onboardingGenParams(firstSession = false, userLevel = "hard")
        assertEquals("hard", params.level)
        assertEquals(10, params.length)
    }

    @Test
    fun `generating route url-encodes the topic prompt seed`() {
        val route =
            onboardingGeneratingRoute(
                topic = "ordering a drink at a café counter",
                level = "easy",
                first = true,
            )
        // 공백/특수문자가 인코딩돼 nav 파싱을 깨지 않는다(하니스 선례).
        assertTrue(route.contains("topic=ordering%20a%20drink"))
        assertTrue(route.contains("level=easy"))
        assertTrue(route.contains("first=true"))
    }

    @Test
    fun `one-more loop carries the saved level and marks the repeat session`() {
        // "한 번 더" → 상황 문항 재진입: 저장 레벨 유지, first=false(2차 세션).
        val route = onboardingTopicRoute(level = "normal", first = false)
        assertTrue(route.contains("level=normal"))
        assertTrue(route.contains("first=false"))
    }

    @Test
    fun `summary route carries session id level and first flag`() {
        val route = onboardingSummaryRoute(sessionId = "sess-1", level = "hard", first = true)
        assertTrue(route.contains("sessionId=sess-1"))
        assertTrue(route.contains("level=hard"))
        assertTrue(route.contains("first=true"))
    }
}

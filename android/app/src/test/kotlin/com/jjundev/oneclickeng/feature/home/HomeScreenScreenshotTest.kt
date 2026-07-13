package com.jjundev.oneclickeng.feature.home

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.ui.foundation.OceBottomNav
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 홈 화면 스크린샷 캡처(파일럿) — 프로토타입(`prototype/Prototype Flow`) 홈 상태와 시각 대조하기 위한
 * Compose 렌더 비트맵을 JVM(Robolectric 네이티브 그래픽)에서 뽑는다. Roborazzi 는 신뢰 가능한 비트맵을
 * 산출할 뿐이고, 프로토타입과의 diff 는 사람이 시각 비교한다(크로스 엔진 픽셀 일치는 목표가 아님).
 *
 * 실행: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*HomeScreenScreenshotTest*' -Proborazzi.record`
 * 산출: `android/app/build/outputs/roborazzi/home_light_*.png`
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class HomeScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun home_light_default() {
        capture(
            HomeUiState(
                studyTimeLabel = "오늘 5분",
                streak = 3,
                isOnline = true,
                level = "easy",
                selectedSituation = sampleSelected,
            ),
            "home_light_default",
        )
    }

    @Test
    fun home_light_resume() {
        capture(
            HomeUiState(
                studyTimeLabel = "오늘 0분",
                streak = 7,
                isOnline = true,
                hasResume = true,
                resumeTopic = "카페에서 주문하기",
                resumeTurn = 2,
                resumeTotalTurns = 5,
                situations = sampleSituations,
            ),
            "home_light_resume",
        )
    }

    @Test
    fun home_light_offline() {
        capture(
            HomeUiState(
                studyTimeLabel = "오늘 5분",
                streak = 3,
                isOnline = false,
            ),
            "home_light_offline",
        )
    }

    /** 이어하기 없음(신규 세션 준비) — 프로토타입 홈(hero "바로 대화 시작하기" + 설정 변경 인라인) 대조. */
    @Test
    fun home_light_newsession() {
        capture(
            HomeUiState(
                studyTimeLabel = "오늘 8분",
                streak = 7,
                isOnline = true,
                hasResume = false,
                level = "easy",
                length = 5,
                selectedSituation = sampleSelected,
                situations = sampleSituations,
            ),
            "home_light_newsession",
        )
    }

    /** 그리드 레이아웃 — 추천 4개가 2×2 컴팩트 셀(chevron 없음)로 배치되는지 프로토와 대조. */
    @Test
    fun home_light_grid() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        HomeContent(
                            state =
                                HomeUiState(
                                    studyTimeLabel = "오늘 8분",
                                    streak = 7,
                                    isOnline = true,
                                    hasResume = false,
                                    level = "easy",
                                    length = 5,
                                    selectedSituation = sampleSelected,
                                    situations = sampleSituations,
                                ),
                            onStartLearning = {},
                            onResumeContinue = {},
                            onResumeStartNew = {},
                            onViewRecords = {},
                            onOfflineBlocked = {},
                            gridMode = true,
                            reduceMotion = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                        OceBottomNav(
                            navController = rememberNavController(),
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/home_light_grid.png")
    }

    /** 하단 네비(OceBottomNav) 포함 — 앱 셸(Scaffold+NavHost)을 재현해 프로토타입 전체 화면과 대조. */
    @Test
    fun home_light_resume_nav() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                val nav = rememberNavController()
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    NavHost(
                        navController = nav,
                        startDestination = "home",
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        composable("home") {
                            HomeContent(
                                state =
                                    HomeUiState(
                                        studyTimeLabel = "오늘 0분",
                                        streak = 7,
                                        isOnline = true,
                                        hasResume = true,
                                        resumeTopic = "카페에서 주문하기",
                                        resumeTurn = 2,
                                        resumeTotalTurns = 5,
                                        situations = sampleSituations,
                                    ),
                                onStartLearning = {},
                                onResumeContinue = {},
                                onResumeStartNew = {},
                                onViewRecords = {},
                                onOfflineBlocked = {},
                                reduceMotion = true,
                            )
                        }
                    }
                    OceBottomNav(
                        navController = nav,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/home_light_resume_nav.png")
    }

    private val sampleSelected =
        SelectedSituation("weather", "날씨로 가볍게 대화하기", "making light small talk about the weather")

    private val sampleSituations =
        listOf(
            HomeSituation("cafe", "카페에서 주문하기", OceIcon.LocalCafe),
            HomeSituation("intro", "처음 만나 자기소개하기", OceIcon.WavingHand),
            HomeSituation("appointment", "친구와 약속 잡기", OceIcon.Event),
            HomeSituation("hotel", "호텔 체크인하기", OceIcon.Hotel),
        )

    /**
     * 홈 캡처 — 프로덕션 셸(3탭 [OceBottomNav])을 포함해 프로토 전체 화면과 대조한다(하단 네비 바가 캡처에
     * 나오도록, 사용자 요청). [HomeContent] 는 전체 뷰포트를 채우고 네비 바가 하단에 오버레이된다.
     */
    private fun capture(
        state: HomeUiState,
        name: String,
    ) {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val nav = rememberNavController()
                    Box(modifier = Modifier.fillMaxSize()) {
                        HomeContent(
                            state = state,
                            onStartLearning = {},
                            onResumeContinue = {},
                            onResumeStartNew = {},
                            onViewRecords = {},
                            onOfflineBlocked = {},
                            reduceMotion = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                        OceBottomNav(
                            navController = nav,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }
}

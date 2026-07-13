package com.jjundev.oneclickeng.feature.reminder.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.feature.home.HomeContent
import com.jjundev.oneclickeng.feature.home.HomeSituation
import com.jjundev.oneclickeng.feature.home.HomeUiState
import com.jjundev.oneclickeng.feature.home.SelectedSituation
import com.jjundev.oneclickeng.ui.component.OneClickPermissionPrimingSheetContent
import com.jjundev.oneclickeng.ui.component.OneClickReminderOptInSheetContent
import com.jjundev.oneclickeng.ui.component.primitive.OceSheetDefaults
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 리마인더 플로우 스크린샷 캡처 — 프로토타입 스트릭 넛지 시트 · 알림 권한 priming 시트 · 홈 상단 켜짐 확인
 * 배너와 시각 대조. 모달 시트는 [TopicSelectScreenshotTest] 선례대로 딤 스크림 + 하단 콘텐츠로 재현한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class ReminderScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reminder_optin_light() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        HomeContent(
                            state = sampleHomeState.copy(),
                            onStartLearning = {},
                            onResumeContinue = {},
                            onResumeStartNew = {},
                            onViewRecords = {},
                            onOfflineBlocked = {},
                        )
                    }
                    Box(modifier = Modifier.fillMaxSize().background(Color(SCRIM)))
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column {
                            // 프로토 정합 핸들(36×4, 위12/아래16)
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(36.dp)
                                            .height(4.dp)
                                            .clip(OceTheme.shapes.pill)
                                            .background(OceTheme.colors.borderStrong),
                                )
                            }
                            Box(
                                modifier =
                                    Modifier.padding(
                                        PaddingValues(start = 24.dp, end = 24.dp, top = 0.dp, bottom = 26.dp),
                                    ),
                            ) {
                                OneClickReminderOptInSheetContent(onOptIn = {}, onLater = {})
                            }
                        }
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/reminder_optin_light.png")
    }

    @Test
    fun reminder_priming_light() {
        captureSheet("reminder_priming_light") {
            OneClickPermissionPrimingSheetContent(
                icon = OceIcon.Notifications,
                rationale =
                    "다음 화면에서 허용을 눌러주세요.\n" +
                        "매일 정한 시각에 학습 리마인더만 보내드려요.\n" +
                        "광고나 다른 알림은 없어요.",
                emphasis = "허용",
                onRequest = {},
                onLater = {},
                title = "알림을 보내도 될까요?",
                requestLabel = "계속",
                laterLabel = "다음에",
                assurance = "거부해도 학습에는 아무 영향이 없어요.",
            )
        }
    }

    /** 홈 상단 리마인더 켜짐 배너(프로토 reminderBanner) — 홈 in-flow 배치까지 함께 대조. */
    @Test
    fun home_light_reminder_banner() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    HomeContent(
                        state = sampleHomeState.copy(),
                        onStartLearning = {},
                        onResumeContinue = {},
                        onResumeStartNew = {},
                        onViewRecords = {},
                        onOfflineBlocked = {},
                        showReminderBanner = true,
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/home_light_reminder_banner.png")
    }

    /**
     * 실제 앱처럼 **홈 화면 위에 딤 스크림 + 하단 시트**(상단 radius24 + 드래그 핸들 + 흰 서피스)를 얹어
     * [OneClickBottomSheet](ModalBottomSheet, 별도 윈도) 프레젠테이션을 근사 재현한다 — 시트 뒤로 딤된 홈이
     * 비친다(TopicSelectScreenshotTest 관용구).
     */
    private fun captureSheet(
        name: String,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 배경: 실제 홈(시트가 이 위에 뜬다).
                    Surface(color = MaterialTheme.colorScheme.background) {
                        HomeContent(
                            state = sampleHomeState.copy(),
                            onStartLearning = {},
                            onResumeContinue = {},
                            onResumeStartNew = {},
                            onViewRecords = {},
                            onOfflineBlocked = {},
                        )
                    }
                    // 딤 스크림(ModalBottomSheet 기본 스크림 근사).
                    Box(modifier = Modifier.fillMaxSize().background(Color(SCRIM)))
                    // 하단 시트(상단 라운드 + 핸들 + 흰 서피스).
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(32.dp)
                                            .height(4.dp)
                                            .clip(OceTheme.shapes.pill)
                                            .background(MaterialTheme.colorScheme.outlineVariant),
                                )
                            }
                            Box(modifier = Modifier.padding(OceSheetDefaults.contentPadding)) {
                                content()
                            }
                        }
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private val sampleHomeState =
        HomeUiState(
            studyMinutes = 8,
            streak = 7,
            isOnline = true,
            level = "easy",
            length = 5,
            selectedSituation = SelectedSituation("cafe-order", "카페에서 주문하기", "seed"),
            situations =
                listOf(
                    HomeSituation("weather", "날씨로 가볍게 대화하기", OceIcon.PartlyCloudyDay),
                    HomeSituation("intro", "처음 만나 자기소개하기", OceIcon.WavingHand),
                    HomeSituation("appointment", "친구와 약속 잡기", OceIcon.Event),
                    HomeSituation("hotel", "호텔 체크인하기", OceIcon.Hotel),
                ),
        )

    private companion object {
        const val SCRIM = 0x66000000
    }
}

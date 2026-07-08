package com.jjundev.oneclickeng.feature.home.topic

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.feature.home.HomeContent
import com.jjundev.oneclickeng.feature.home.HomeSituation
import com.jjundev.oneclickeng.feature.home.HomeUiState
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 상황 고르기 시트 스크린샷 캡처(프로토타입 `topic_select` 시트 대조). ModalBottomSheet 는 Robolectric 에서
 * 별도 윈도로 떠 onRoot 캡처가 어려우므로, 프로덕션 프레젠테이션을 근사 재현한다: 뒤에 실제 [HomeContent]
 * (고정 상태) + 딤 스크림 + 화면 하단 ~70% 시트 콘텐츠([TopicSelectSheetContent]). 프로토처럼 시트 뒤로 딤된
 * 홈이 비친다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class TopicSelectScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val homeState =
        HomeUiState(
            studyTimeLabel = "오늘 0분",
            streak = 7,
            isOnline = true,
            hasResume = true,
            resumeTopic = "카페에서 주문하기",
            resumeTurn = 2,
            resumeTotalTurns = 5,
            situations =
                listOf(
                    HomeSituation("weather", "날씨로 가볍게 대화하기", OceIcon.PartlyCloudyDay),
                    HomeSituation("intro", "처음 만나 자기소개하기", OceIcon.WavingHand),
                    HomeSituation("appointment", "친구와 약속 잡기", OceIcon.Event),
                    HomeSituation("hotel", "호텔 체크인하기", OceIcon.Hotel),
                ),
        )

    @Test
    fun topic_select_light() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 배경: 실제 홈 허브(시트가 이 위에 뜬다)
                    Surface(color = MaterialTheme.colorScheme.background) {
                        HomeContent(
                            state = homeState,
                            onStartLearning = {},
                            onResumeContinue = {},
                            onResumeStartNew = {},
                            onViewRecords = {},
                            onOfflineBlocked = {},
                        )
                    }
                    // 딤 스크림(ModalBottomSheet 기본 스크림 근사)
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA)),
                    )
                    // 하단 ~70% 시트
                    Surface(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .fillMaxHeight(0.7f),
                        color = MaterialTheme.colorScheme.surface,
                        shape = OceTheme.shapes.radius24,
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(top = OceTheme.spacing.md),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(DpSize(32.dp, 4.dp))
                                            .clip(OceTheme.shapes.pill)
                                            .background(MaterialTheme.colorScheme.outlineVariant),
                                )
                            }
                            TopicSelectSheetContent(
                                onTopicChosen = { _, _ -> },
                                onDismiss = {},
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            )
                        }
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/topic_select_light.png")
    }
}

private const val SCRIM_ALPHA = 0.32f

package com.jjundev.oneclickeng

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.jjundev.oneclickeng.feature.session.tts.TtsPlaybackCoordinator
import com.jjundev.oneclickeng.ui.root.AppRoot
import com.jjundev.oneclickeng.ui.theme.OceTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 단일 Activity 진입점. Compose 만으로 UI 를 구성한다.
 *
 * 알림 탭 라우팅(notification-reminder.md §5, 결정 #10): 리마인더 알림의 PendingIntent 는
 * `nav=home` extra 를 실어 이 Activity 로 온다. `launchMode=singleTop`(매니페스트)이라 앱이 이미 떠
 * 있으면 새 인스턴스 대신 [onNewIntent] 로 재전달되며, extra 를 [pendingNav] 상태로 올려 [AppRoot] 가
 * NavController 를 홈으로 이동시킨다(소비 후 클리어).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var tts: TtsPlaybackCoordinator

    private val pendingNav = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingNav.value = intent?.getStringExtra(EXTRA_NAV)
        setContent {
            OceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val nav by pendingNav
                    AppRoot(
                        pendingNav = nav,
                        onNavConsumed = { pendingNav.value = null },
                    )
                }
            }
        }
    }

    /**
     * 앱이 전면에 올 때마다 TTS 모델을 예열한다. 대본 생성(`gemini-3.1-flash-lite`)과 음성 합성
     * (`gemini-2.5-flash-preview-tts`)은 **서로 다른 모델**이라 생성이 아무리 돌아도 음성 모델은 차갑다.
     * 콜드 상태의 첫 합성은 7초를 넘겨 서버가 자체 타임아웃으로 포기 → 첫 대사가 단말 음성으로 폴백된다.
     * 세션 시작 전에 미리 데워두면 첫 합성이 웜(~5-6초)이라 그대로 성공한다. SERVER·비음소거 게이트와
     * 중복 방지는 코디네이터가 처리하므로 여기선 무조건 호출한다(fire-and-forget).
     */
    override fun onStart() {
        super.onStart()
        tts.warmUpModel()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNav.value = intent.getStringExtra(EXTRA_NAV)
    }

    companion object {
        const val EXTRA_NAV = "nav"
        const val NAV_HOME = "home"
    }
}

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
import com.jjundev.oneclickeng.ui.root.AppRoot
import com.jjundev.oneclickeng.ui.theme.OceTheme
import dagger.hilt.android.AndroidEntryPoint

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

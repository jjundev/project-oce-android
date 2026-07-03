package com.jjundev.oneclickeng

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jjundev.oneclickeng.ui.root.AppRoot
import com.jjundev.oneclickeng.ui.theme.OceTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 단일 Activity 진입점. Compose 만으로 UI 를 구성한다.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OceTheme {
                AppRoot()
            }
        }
    }
}

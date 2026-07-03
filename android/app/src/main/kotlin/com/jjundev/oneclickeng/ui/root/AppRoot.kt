package com.jjundev.oneclickeng.ui.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 앱 루트 컴포저블. M0-01 스캐폴드 단계에서는 빈 화면 플레이스홀더만 제공한다.
 * 3탭 내비게이션·전역 Scaffold 규약과 실제 화면은 M0-09 에서 이 자리를 대체한다.
 */
@Composable
fun AppRoot() {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        )
    }
}

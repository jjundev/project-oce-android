package com.jjundev.oneclickeng.ui.foundation

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * reduce-motion(시스템 "애니메이션 제거") 감지. `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`.
 * 정본: 06-accessibility-impl.md A7:105 — 슬롯→스냅·시머→정적·전환→크로스페이드의 공용 게이트.
 *
 * 원래 [com.jjundev.oneclickeng.ui.audio.WaveformCanvas] 안의 private 함수였던 것을 여기로 승격했다.
 * 소비처: WaveformCanvas(I2)·OneClickSkeleton(C6)·OneClickOfflineBanner(C4) 등.
 *
 * v1은 진입 시 1회 계산([remember])이며 런타임 토글 변경을 관찰하지 않는다(ContentObserver 비목표).
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

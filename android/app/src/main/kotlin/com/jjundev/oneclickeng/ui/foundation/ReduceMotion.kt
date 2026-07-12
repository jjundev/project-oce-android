package com.jjundev.oneclickeng.ui.foundation

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 시스템 reduce-motion(애니메이션 끄기) 감지 seam. `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` 이면 true.
 * 정본: 06-accessibility-impl.md A7:105 — 슬롯→스냅·시머→정적·전환→크로스페이드의 공용 게이트.
 *
 * 원래 [com.jjundev.oneclickeng.ui.audio.WaveformCanvas] 안의 private 함수였던 것을 여기로 승격했다.
 * 소비처: WaveformCanvas(I2)·OneClickSkeleton(C6)·OneClickOfflineBanner(C4) 등.
 *
 * **스냅샷 시맨틱(의도적):** 값은 최초 컴포지션 1회에 `remember(context)` 로 읽고 이후 재구독하지
 * 않는다. 정상 사용 경로에서 reduce-motion 은 앱 실행 전 시스템 설정에서 켜지므로 1회 읽기로 충분하고,
 * 이는 파형(I2)이 이미 채택한 프로덕션 패턴과 동일하다. 앱 포그라운드 중 토글 반영은 비대상(ContentObserver 비목표).
 *
 * **테스트/주입:** 소비처는 이 값을 파라미터 seam 으로 받도록 설계한다(예: `OneClickSkeleton(reduceMotion = …)`).
 * 정적 대체 동작 검증은 시스템 설정을 토글(WRITE_SECURE_SETTINGS 필요)하는 대신 `reduceMotion = true` 를
 * 직접 주입해 반증가능하게 한다.
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

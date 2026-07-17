package com.jjundev.oneclickeng.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [TtsCalibrationProbe] 의 adb 트리거 — **디버그 변이 전용**(`src/debug/AndroidManifest.xml`).
 *
 * 실행:
 * ```
 * adb shell am broadcast -a com.jjundev.oneclickeng.RUN_TTS_CALIBRATION \
 *   -n com.jjundev.oneclickeng/com.jjundev.oneclickeng.debug.TtsCalibrationReceiver
 * ```
 *
 * **앱이 실행 중이고 로그인된 상태여야 한다** — 서버 합성이 살아 있는 Hilt 그래프의 인증 토큰을 쓴다.
 * 결과는 `adb logcat -s TtsCalib`, WAV 는 `adb pull` (경로는 로그 첫 줄에 찍힌다).
 *
 * 설정 화면에 디버그 행을 넣지 않은 이유: `SettingsContent` 는 Roborazzi 스크린샷 seam 이라
 * 행이 하나만 늘어도 프로토타입 정합 테스트가 깨진다.
 */
@AndroidEntryPoint
class TtsCalibrationReceiver : BroadcastReceiver() {
    @Inject
    lateinit var probe: TtsCalibrationProbe

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION) return
        // goAsync: 계측은 네트워크 6회 + 온디바이스 합성 3회라 onReceive 의 10초 예산을 넘는다.
        val pending = goAsync()
        Log.i(CALIB_TAG, "계측 요청 수신 — 수십 초 걸립니다")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                probe.run()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val ACTION = "com.jjundev.oneclickeng.RUN_TTS_CALIBRATION"
    }
}

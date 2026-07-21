package com.jjundev.oneclickeng.ui.foundation.refresh

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

/**
 * 정규화된 물결 bob 곡선. 프로토타입 waveBob 키프레임(0 → +1@32% → -0.27@66% → 0)을 구간 선형 보간.
 * [t] 는 아이템별 로컬 진행도[0,1], 반환값에 진폭(px)을 곱해 translationY 로 쓴다.
 */
fun waveBob(t: Float): Float {
    if (t <= 0f || t >= 1f) return 0f
    val keys = floatArrayOf(0f, 0.32f, 0.66f, 1f)
    val vals = floatArrayOf(0f, 1f, -0.27f, 0f)
    var result = 0f
    for (i in 0 until keys.size - 1) {
        if (t <= keys[i + 1]) {
            val span = keys[i + 1] - keys[i]
            val f = if (span == 0f) 0f else (t - keys[i]) / span
            result = vals[i] + (vals[i + 1] - vals[i]) * f
            break
        }
    }
    return result
}

/**
 * 물결 애니메이션 시계. [clockMs] 를 0 부터 (WAVE_DURATION_MS + maxIndex*WAVE_STAGGER_MS) 까지 진행시키면
 * 각 아이템이 인덱스만큼 지연돼 파도처럼 전파된다. -1 = 유휴(정지).
 */
class RefreshWaveState {
    private val _clockMs = mutableFloatStateOf(-1f)
    var clockMs: Float
        get() = _clockMs.floatValue
        set(value) {
            _clockMs.floatValue = value
        }

    fun translationYPx(
        index: Int,
        amplitudePx: Float,
    ): Float {
        val c = clockMs
        if (c < 0f) return 0f
        val local =
            ((c - index * OverscrollDefaults.WAVE_STAGGER_MS) / OverscrollDefaults.WAVE_DURATION_MS)
                .coerceIn(0f, 1f)
        return waveBob(local) * amplitudePx
    }
}

/** 물결 시계 제공자. 리프레시 박스가 실제 인스턴스를 제공하고, 그 밖에서는 유휴 인스턴스라 no-op. */
val LocalRefreshWave = staticCompositionLocalOf { RefreshWaveState() }

/**
 * 리스트/헤더 요소가 물결에 참여하도록 하는 opt-in 모디파이어.
 * [index] 는 물결 전파 순서(위→아래), [soft]=true 는 헤더용 작은 진폭.
 * graphicsLayer 람다에서 clock 을 읽어 draw 단계에서만 갱신(리컴포지션 없음).
 */
fun Modifier.refreshWave(
    index: Int,
    soft: Boolean = false,
): Modifier =
    composed {
        val wave = LocalRefreshWave.current
        val amplitudePx =
            with(LocalDensity.current) {
                (if (soft) OverscrollDefaults.WaveHeaderPeak else OverscrollDefaults.WaveCardPeak).toPx()
            }
        graphicsLayer { translationY = wave.translationYPx(index, amplitudePx) }
    }

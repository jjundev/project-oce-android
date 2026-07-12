package com.jjundev.oneclickeng.ui.foundation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlinx.coroutines.delay

// oc-rise 정본(prototype .oc-home-stagger): translateY 14px→0 + opacity 0→1, 0.38s easeOut,
// 지연 20 + 60*index (index 0..11 → 20..680ms).
const val STAGGER_RISE_DP = 14
const val STAGGER_DURATION_MS = 380
const val STAGGER_BASE_DELAY_MS = 20
const val STAGGER_STEP_MS = 60
const val STAGGER_MAX_INDEX = 11

/** 스태거 지연(ms): 20 + 60*clamp(index,0,11). 순수 함수(테스트 대상). */
fun staggerDelayMs(index: Int): Int =
    STAGGER_BASE_DELAY_MS + STAGGER_STEP_MS * index.coerceIn(0, STAGGER_MAX_INDEX)

/**
 * 화면 진입 스태거 게이트. [active] 가 true 인 동안(진입 창) 첫 컴포즈된 섹션만 애니메이션한다.
 * 창은 [rememberScreenEntrance] 의 windowMs 후 닫혀, 이후 스크롤로 들어온 섹션은 즉시 표시(재발동 없음).
 */
class ScreenEntranceState internal constructor(active: Boolean) {
    var active: Boolean by mutableStateOf(active)
        internal set
}

/**
 * 진입 스태거 상태 생성. [reduceMotion] 이면 상시 비활성(즉시 최종 상태). 아니면 [windowMs] 동안 창을 열어
 * 그 사이 첫 컴포즈된 섹션이 스태거로 등장하고, 이후 창을 닫는다.
 */
@Composable
fun rememberScreenEntrance(
    reduceMotion: Boolean,
    windowMs: Int = 300,
): ScreenEntranceState {
    val state = remember { ScreenEntranceState(active = !reduceMotion) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            state.active = false
            return@LaunchedEffect
        }
        delay(windowMs.toLong())
        state.active = false
    }
    return state
}

/**
 * 프로토 oc-rise 스태거를 적용한다. 첫 컴포지션에 [entrance].active 를 스냅샷해, 진입 창 안이면 [index] 지연 후
 * 상승([STAGGER_RISE_DP]dp)+페이드([STAGGER_DURATION_MS]ms, easeOut), 아니면 즉시 최종 상태(no-op).
 * graphicsLayer 합성만 사용 → relayout 없음(부드러움). reduce-motion 은 [entrance] 가 상시 비활성이라 자동 no-op.
 */
fun Modifier.staggerReveal(
    index: Int,
    entrance: ScreenEntranceState,
): Modifier =
    composed {
        val animateOnEnter = remember { entrance.active }
        if (!animateOnEnter) return@composed this
        val progress = remember { Animatable(0f) }
        val risePx = with(LocalDensity.current) { STAGGER_RISE_DP.dp.toPx() }
        val easing = OceTheme.motion.easingOut
        LaunchedEffect(Unit) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec =
                    tween(
                        durationMillis = STAGGER_DURATION_MS,
                        delayMillis = staggerDelayMs(index),
                        easing = easing,
                    ),
            )
        }
        graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * risePx
        }
    }

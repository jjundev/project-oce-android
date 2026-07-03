package com.jjundev.oneclickeng.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 모션 토큰. 값 정본: buildspec A.2. §6 시그니처 인터랙션의 모션 정본은 design-tokens.md §6 (여기서 재정의 금지).
 * duration 은 ms(Int), snap 은 scaleY 범위. reduce-motion 시 정적 대체는 소비처(F4)가 담당.
 */
@Immutable
data class OceMotion(
    val durationFastMs: Int = 100,
    val durationBaseMs: Int = 200,
    val easingStandard: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f),
    val easingOut: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f),
    val springDamping: Float = 0.55f,
    val springStiffness: Float = Spring.StiffnessMedium,
    val slotMachineFastPhaseMs: Int = 800,
    val slotMachineTotalMs: Int = 1260,
    val slotMachineSnapFrom: Float = 0.92f,
    val slotMachineSnapTo: Float = 1.0f,
    val rippleLoopMs: Int = 600,
    val rippleCount: Int = 3,
    val shimmerLoopMs: Int = 1200,
)

internal val OceMotionTokens = OceMotion()

val LocalOceMotion = staticCompositionLocalOf { OceMotionTokens }

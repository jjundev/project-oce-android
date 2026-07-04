package com.jjundev.oneclickeng.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlin.math.roundToInt

/**
 * C16 슬롯머신 카운트업 = scratch 독립 primitive. 정본: 02-shared-components.md:120 ·
 * 03-signature-interactions.md I3(:45-46). RewardStrip 이 이를 **합성**하지 흡수하지 않는다.
 *
 * 두 애니메이터로 구성한다(정본 인용, 재결정 금지 — 수치·구조는 SoT, Compose 이징명은 그 매핑):
 *  1. **값 롤업** [Animatable] [from]→[target] 을 `keyframes`(기본 [from]=0; 학습시간 오늘 누계 before→after
 *     롤업(§4.4)은 [from]=세션 전 값. 총 [OceMotion.slotMachineTotalMs]=1260ms,
 *     [OceMotion.slotMachineFastPhaseMs]=800ms 지점까지 0.98·target 급가속 후 감속) 로 굴린다.
 *  2. **scaleY 스프링 바운스** [Animatable] [OceMotion.slotMachineSnapFrom](0.92)→
 *     [OceMotion.slotMachineSnapTo](1.0) 을 `spring` 으로 튕긴다(롤업과 독립 축).
 *
 * [static] (same-day 2번째 세션 streak 롤업 안 함) 또는 [reduceMotion](A7) 이면 **둘 다 즉시 스냅**
 * (값=target, scaleY=1) — 정보 유지·움직임 제거. a11y: 최종값 한 번만 polite announce(A6, 프레임별
 * 스팸 방지 위해 시맨틱을 최종 라벨로 치환).
 */
@Composable
fun OneClickCountUp(
    target: Int,
    modifier: Modifier = Modifier,
    from: Int = 0,
    unit: String = "",
    static: Boolean = false,
    reduceMotion: Boolean = rememberReduceMotion(),
    style: TextStyle = OceTheme.typography.turnScore,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val snap = static || reduceMotion
    val motion = OceTheme.motion
    val value = remember { Animatable(if (snap) target.toFloat() else from.toFloat()) }
    val scale = remember { Animatable(if (snap) motion.slotMachineSnapTo else motion.slotMachineSnapFrom) }
    val finalLabel = "$target$unit"

    LaunchedEffect(target, from, snap) {
        if (snap) {
            value.snapTo(target.toFloat())
            scale.snapTo(motion.slotMachineSnapTo)
        } else {
            value.snapTo(from.toFloat())
            scale.snapTo(motion.slotMachineSnapFrom)
            // fast-phase 지점은 [from]→[target] 구간의 slotMachineSnapFrom(92%) 지점(0-베이스일 때 기존과 동일).
            val fastPhaseValue = from + (target - from) * motion.slotMachineSnapFrom
            value.animateTo(
                targetValue = target.toFloat(),
                animationSpec =
                    keyframes {
                        durationMillis = motion.slotMachineTotalMs
                        from.toFloat() at 0 using FastOutLinearInEasing
                        fastPhaseValue at motion.slotMachineFastPhaseMs using LinearOutSlowInEasing
                        target.toFloat() at motion.slotMachineTotalMs
                    },
            )
            scale.animateTo(
                targetValue = motion.slotMachineSnapTo,
                animationSpec =
                    spring(
                        dampingRatio = motion.springDamping,
                        stiffness = motion.springStiffness,
                    ),
            )
        }
    }

    Text(
        text = "${value.value.roundToInt()}$unit",
        style = style,
        color = color,
        modifier =
            modifier
                .graphicsLayer { scaleY = scale.value }
                // 시각적 롤업은 유지하되, 스크린리더에는 프레임별 숫자 대신 최종값만 한 번 알린다(A6 polite).
                .clearAndSetSemantics {
                    contentDescription = finalLabel
                    liveRegion = LiveRegionMode.Polite
                },
    )
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 200)
@Composable
private fun OneClickCountUpPreview() {
    OceTheme {
        OneClickCountUp(target = 120, unit = " XP", reduceMotion = true)
    }
}

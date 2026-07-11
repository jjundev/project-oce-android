package com.jjundev.oneclickeng.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import com.jjundev.oneclickeng.ui.theme.OceMotion

/**
 * 전역 화면/탭 전환 정본(F4 확정). 프로토타입 `oc-fade-up`(opacity 0→1 + translateY 8px→0) 이식:
 * 진입 = fade + 8dp 상승([motion].durationBaseMs / easingOut), 퇴장 = 하드 컷(구 화면 즉시 제거 — 프로토
 * 정합, [oceScreenExit]). [reduceMotion] 시 진입도 정적([EnterTransition.None], A7 "전환→즉시").
 *
 * 내부 3탭([OceNavHost])·바깥 그래프([com.jjundev.oneclickeng.ui.root.AppRoot] NavHost)가 이 동일 스펙을
 * 공유해 전 전환을 균일화한다. 탭 전환도 가로 슬라이드가 아니라 동일 세로 fade-up(프로토 parity 우선).
 *
 * @param offsetY8Px 8dp 를 px 로 환산한 상승 시작 오프셋(호출부에서 `LocalDensity` 로 계산해 주입 — 팩토리는
 *   밀도 비의존). 프로토 `translateY(8px)` 대응.
 */
fun oceScreenEnter(
    motion: OceMotion,
    offsetY8Px: Int,
    reduceMotion: Boolean,
): EnterTransition =
    if (reduceMotion) {
        EnterTransition.None
    } else {
        fadeIn(tween(motion.durationBaseMs, easing = motion.easingOut)) +
            slideInVertically(tween(motion.durationBaseMs, easing = motion.easingOut)) { offsetY8Px }
    }

/**
 * 퇴장 전환 = 하드 컷([ExitTransition.None]). 프로토는 명시 exit 없이 구 화면을 즉시 제거하고 새 콘텐츠만
 * fade-up 시킨다 — 이를 정합(reduce-motion 무관 동일). exit 에 fadeOut 추가 금지(Global Constraints).
 */
val oceScreenExit: ExitTransition = ExitTransition.None

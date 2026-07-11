package com.jjundev.oneclickeng.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import com.jjundev.oneclickeng.ui.theme.OceMotion

/**
 * 전역 화면/탭 전환 정본(F4 확정). 컨테이너 = 빠른 fade(slide 없음 — jank 원인 제거),
 * 화면 등장감 = 섹션별 stagger(oc-rise, Task 3~5).
 * 퇴장 = 하드 컷(구 화면 즉시 제거 — 프로토 정합, [oceScreenExit]).
 * [reduceMotion] 시 진입도 정적([EnterTransition.None], A7 "전환→즉시").
 *
 * 내부 3탭([OceNavHost])·바깥 그래프([com.jjundev.oneclickeng.ui.root.AppRoot] NavHost)가 이 동일 스펙을
 * 공유해 전 전환을 균일화한다.
 */
fun oceScreenEnter(
    motion: OceMotion,
    reduceMotion: Boolean,
): EnterTransition =
    if (reduceMotion) {
        EnterTransition.None
    } else {
        // 컨테이너는 빠른 페이드만 — slide 제거(렉 원인). 화면 등장감은 섹션 스태거(oc-rise)가 담당.
        fadeIn(tween(motion.durationFastMs, easing = motion.easingStandard))
    }

/**
 * 퇴장 전환 = 하드 컷([ExitTransition.None]). 프로토는 명시 exit 없이 구 화면을 즉시 제거하고 새 콘텐츠만
 * fade-up 시킨다 — 이를 정합(reduce-motion 무관 동일). exit 에 fadeOut 추가 금지(Global Constraints).
 */
val oceScreenExit: ExitTransition = ExitTransition.None

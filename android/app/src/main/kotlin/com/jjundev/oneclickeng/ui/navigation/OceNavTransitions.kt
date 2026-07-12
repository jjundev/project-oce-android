package com.jjundev.oneclickeng.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

/**
 * 전역 화면/탭 전환 정본(F4 확정). 컨테이너는 **즉시 교체(무전환)** — 이전 fade 는 구 화면 위에 겹쳐
 * 페이드인돼 잔상을 남겨 제거했다. 화면 등장감은 섹션별 stagger(oc-rise)가 전담한다(프로토처럼 컨테이너는
 * 전환 없음). 내부 3탭([OceNavHost])·바깥 그래프([com.jjundev.oneclickeng.ui.root.AppRoot] NavHost)가 공유한다.
 */
val oceScreenEnter: EnterTransition = EnterTransition.None

/**
 * 퇴장 전환 = 하드 컷([ExitTransition.None]). 프로토는 명시 exit 없이 구 화면을 즉시 제거하고 새 콘텐츠만
 * fade-up 시킨다 — 이를 정합(reduce-motion 무관 동일). exit 에 fadeOut 추가 금지(Global Constraints).
 */
val oceScreenExit: ExitTransition = ExitTransition.None

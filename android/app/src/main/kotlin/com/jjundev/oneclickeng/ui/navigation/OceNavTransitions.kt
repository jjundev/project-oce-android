package com.jjundev.oneclickeng.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.jjundev.oneclickeng.ui.theme.OceMotionTokens

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

/** 세션 요약 핸드오프 슬라이드 길이(ms). 전체 화면 전환이라 느긋한 박자로 밀어 넣는다(요구: 속도 추가 완화). */
private const val SUMMARY_HANDOFF_SLIDE_MS = 700

/**
 * 대화학습 종료 → 세션 요약 진입 전환: 요약이 **오른쪽에서** 밀려 들어온다(`+fullWidth → 0`). reduce-motion 이면
 * 무전환([EnterTransition.None]) — 컨테이너 기본([oceScreenEnter])과 동일. 컨테이너 기본은 건드리지 않고 이 목적지
 * 엣지에만 얹는 예외다(Global Constraints).
 */
fun summaryHandoffEnter(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) {
        EnterTransition.None
    } else {
        slideInHorizontally(
            animationSpec = tween(SUMMARY_HANDOFF_SLIDE_MS, easing = OceMotionTokens.easingOut),
        ) { fullWidth -> fullWidth }
    }

/**
 * 대화학습 화면의 요약 핸드오프 퇴장: **왼쪽으로** 밀려 나간다(`0 → -fullWidth`, 요약이 오른쪽에서 들어오는 것과 짝).
 * reduce-motion 이면 하드 컷([ExitTransition.None]).
 */
fun sessionHandoffExit(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) {
        ExitTransition.None
    } else {
        slideOutHorizontally(
            animationSpec = tween(SUMMARY_HANDOFF_SLIDE_MS, easing = OceMotionTokens.easingOut),
        ) { fullWidth -> -fullWidth }
    }

/**
 * 요약 목적지의 진입 전환을 **session→summary 엣지로 한정**한다. 진입 소스([sourceRoute])가 [sessionRoute]일 때만
 * 슬라이드([summaryHandoffEnter]), 그 외/`null` 진입은 무전환. (현재 요약 진입 엣지는 하나뿐이나, 미래에 다른 진입
 * 경로가 생겨도 슬라이드가 새지 않도록 명시 게이트.)
 */
fun summaryEnterFor(
    sourceRoute: String?,
    sessionRoute: String,
    reduceMotion: Boolean,
): EnterTransition = if (sourceRoute == sessionRoute) summaryHandoffEnter(reduceMotion) else EnterTransition.None

/**
 * 대화 목적지의 퇴장 전환을 **session→summary 엣지로 한정**한다. 퇴장 타깃([targetRoute])이 [summaryRoute]일 때만
 * 슬라이드([sessionHandoffExit]), 그 외(예: 온보딩의 홈-이동 `navigate(MAIN_TABS)`)는 무전환. 온보딩 session 은
 * 요약 외에도 전진 내비게이션이 있어 이 게이트가 필수다.
 */
fun sessionExitFor(
    targetRoute: String?,
    summaryRoute: String,
    reduceMotion: Boolean,
): ExitTransition = if (targetRoute == summaryRoute) sessionHandoffExit(reduceMotion) else ExitTransition.None

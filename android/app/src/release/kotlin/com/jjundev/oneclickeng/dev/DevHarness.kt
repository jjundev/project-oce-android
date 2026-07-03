package com.jjundev.oneclickeng.dev

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

/**
 * 내부 개발 하니스(M1-09)의 **release 변이 no-op**. 하니스 런처·세션 라우트·시작목적지 override 는
 * `src/debug` 에만 존재하므로, 릴리즈 APK 에는 하니스 표면이 물리적으로 부재한다(수용기준 #1 · 검증:
 * 릴리즈 빌드에서 진입점 부재). AppRoot 의 seam 두 곳(harnessStartRoute · harnessGraph)이 릴리즈 변이
 * 컴파일 시 이 no-op 에 링크된다 — 시그니처는 debug 대응 파일과 동일해야 한다.
 */
fun harnessStartRoute(): String? = null

@Suppress("UnusedParameter")
fun NavGraphBuilder.harnessGraph(navController: NavHostController) = Unit

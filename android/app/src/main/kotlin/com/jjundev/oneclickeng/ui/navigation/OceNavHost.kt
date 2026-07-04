package com.jjundev.oneclickeng.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jjundev.oneclickeng.feature.home.HomeScreen
import com.jjundev.oneclickeng.feature.records.RecordsScreen
import com.jjundev.oneclickeng.feature.session.summary.AccrualStrip
import com.jjundev.oneclickeng.feature.session.summary.SummaryRoute
import com.jjundev.oneclickeng.feature.settings.SettingsScreen
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 3탭 내비게이션 그래프. 각 탭은 빈 화면 플레이스홀더로 전환된다(M0-09).
 *
 * **전환 훅(F4 미결 · 잠정 기본값):** F4(전역 Nav 전환 패턴)는 SoT에서 여전히 미결(🟠)이고 F8 스코프
 * 밖이다. 이슈는 "F4 훅"만 요구하므로, 여기서는 **교체 가능한 전환 seam** 에 사용자 승인 잠정 기본값
 * (크로스페이드 `durationBaseMs` 200ms · `easingStandard`)을 주입한다. F4 확정 시 이 enter/exit 만
 * 교체한다(SoT 재결정 아님 — 잠정 훅).
 *
 * **reduce-motion:** [reduceMotion] seam(기본값 [rememberReduceMotion])이 true 면 전환을 정적 스냅
 * ([EnterTransition.None]/[ExitTransition.None])으로 대체한다(수용기준 #4). 테스트는 이 인자를 직접
 * 주입해 정적 대체를 반증가능하게 검증한다.
 */
@Composable
fun OceNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotion(),
) {
    val durationMs = OceTheme.motion.durationBaseMs
    val easing = OceTheme.motion.easingStandard
    NavHost(
        navController = navController,
        startDestination = OceTab.Start.route,
        modifier = modifier,
        enterTransition = {
            if (reduceMotion) EnterTransition.None else fadeIn(tween(durationMs, easing = easing))
        },
        exitTransition = {
            if (reduceMotion) ExitTransition.None else fadeOut(tween(durationMs, easing = easing))
        },
    ) {
        composable(OceTab.Home.route) { HomeScreen() }
        composable(OceTab.Records.route) { RecordsScreen() }
        composable(OceTab.Settings.route) { SettingsScreen() }

        // 세션 요약(M2-02). 신규 세션 목적지 — 상위 대화 그래프는 아직 미배선이라 이 라우트 자체가
        // 세션 목적지의 시작점이다(#22). `difficulty`/`modeId`/accrual 값의 실제 전달은 M1 nav 통합 의존:
        // 대화 세션이 이 값을 route arg 로 실어 보내게 되면 아래 placeholder 를 교체한다. accrual 정적 값의
        // 실제 소스는 M3-05, 카운트업은 M3-06.
        composable(
            route = SUMMARY_ROUTE,
            arguments =
                listOf(
                    navArgument(ARG_SESSION_ID) { type = NavType.StringType },
                    navArgument(ARG_DIFFICULTY) { type = NavType.StringType; defaultValue = "normal" },
                    navArgument(ARG_MODE_ID) { type = NavType.StringType; defaultValue = "default" },
                ),
        ) { entry ->
            val args = entry.arguments
            SummaryRoute(
                sessionId = args?.getString(ARG_SESSION_ID).orEmpty(),
                difficulty = args?.getString(ARG_DIFFICULTY) ?: "normal",
                modeId = args?.getString(ARG_MODE_ID) ?: "default",
                // M3-05 배선 전 정적 placeholder(스트립은 0 값으로 렌더). 실제 값 주입 시 교체.
                accrual = AccrualStrip(streakDays = 0, studyTimeLabel = "", xp = 0),
            )
        }
    }
}

/** 요약 목적지 route 템플릿 + arg 키(대화 그래프 배선 시 [buildSummaryRoute] 로 목적지를 만든다, #22). */
const val ARG_SESSION_ID = "sessionId"
const val ARG_DIFFICULTY = "difficulty"
const val ARG_MODE_ID = "modeId"
const val SUMMARY_ROUTE =
    "session/summary/{$ARG_SESSION_ID}?$ARG_DIFFICULTY={$ARG_DIFFICULTY}&$ARG_MODE_ID={$ARG_MODE_ID}"

/**
 * 요약 목적지 실경로 빌더. 대화 완료(DialogueCompletion.onViewSummary)에서 세션 값으로 호출한다 —
 * `navController.navigate(buildSummaryRoute(sessionId, difficulty, modeId))`. 상위 배선은 M1 nav 통합(#22).
 */
fun buildSummaryRoute(
    sessionId: String,
    difficulty: String = "normal",
    modeId: String = "default",
): String = "session/summary/$sessionId?$ARG_DIFFICULTY=$difficulty&$ARG_MODE_ID=$modeId"

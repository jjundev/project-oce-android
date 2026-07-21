@file:Suppress("TooManyFunctions") // 그래프 = 5개 목적지 빌더 + route 실경로 헬퍼(OnboardingGraph/Routes 선례).

package com.jjundev.oneclickeng.feature.home

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGeneratingRoute
import com.jjundev.oneclickeng.feature.session.summary.AccrualStrip
import com.jjundev.oneclickeng.feature.session.summary.SummaryRoute
import com.jjundev.oneclickeng.feature.session.turn.GeneratedDialogueSessionRoute
import com.jjundev.oneclickeng.ui.navigation.sessionExitFor
import com.jjundev.oneclickeng.ui.navigation.summaryEnterFor
import com.jjundev.oneclickeng.ui.root.MAIN_TABS_ROUTE
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 홈 주도 세션 루프 그래프(M3-08 → 프로토 완전 정합). outer NavHost 에 [MAIN_TABS_ROUTE] 형제로 등록돼
 * 3탭 밖 풀스크린으로 뜬다. 흐름: 생성 → 대화세션 → 요약(→ 홈). 세션 설정 화면은 폐기 — 상황·레벨·길이는
 * 홈(인라인 패널·상황 시트)이 확정해 [homeSessionStartRoute] 로 싣는다(프로토 heroPrimary=바로 생성).
 * 온보딩 그래프와 동일한 세션 루프 컴포저블([DialogueGeneratingRoute]/[GeneratedDialogueSessionRoute]/
 * [SummaryRoute])을 재사용한다.
 *
 * **종료 = MAIN_TABS 보존(결정 #1):** 온보딩은 시작점이라 `MAIN_TABS` 를 새로 생성하지만, 홈은 이미 스택에
 * 있으므로 이 그래프만 pop 해([exitToTabs]) 기존 `MainTabsScaffold`(inner 탭 백스택)를 살린다.
 *
 * **이어하기 재진입(#12):** [homeSessionResumeRoute] 로 [HOME_SESSION_ROUTE] 에 직접 진입한다(생성 라우트
 * 우회 — 생성 라우트는 `start()` 로 turns 를 wipe). 세션 VM 은 durable 스냅샷/라이브 코디네이터에서 복원한다.
 */
fun NavGraphBuilder.homeSessionGraph(navController: NavHostController, reduceMotion: Boolean) {
    navigation(startDestination = HOME_GENERATING_ROUTE, route = HOME_SESSION_GRAPH_ROUTE) {
        generatingDestination(navController)
        sessionDestination(navController, reduceMotion)
        summaryDestination(navController, reduceMotion)
    }
}

/**
 * 홈 히어로/추천 행 진입: 홈이 확정한 상황·레벨·길이를 실어 바로 생성으로(프로토 startGeneration).
 * [topicLabel]/[topicEmoji] 는 세션 헤더 정체성(주제 제목·아바타)용으로 생성→세션까지 함께 흐른다.
 */
fun homeSessionStartRoute(
    level: String,
    topic: String,
    length: Int,
    topicLabel: String,
    topicEmoji: String,
): String =
    homeGeneratingRoute(
        level = level,
        topic = topic,
        length = length,
        topicLabel = topicLabel,
        topicEmoji = topicEmoji,
    )

/** 이어하기 진입: 세션 목적지로 직접(레벨은 스냅샷 복원값 — nav-arg 는 비움). */
fun homeSessionResumeRoute(): String = "home/session?$H_ARG_LEVEL="

/** 그래프 전체를 pop 해 기존 3탭 셸로 복귀(MAIN_TABS 재생성하지 않음). */
private fun NavHostController.exitToTabs() {
    popBackStack(HOME_SESSION_GRAPH_ROUTE, inclusive = true)
}

private fun NavGraphBuilder.generatingDestination(navController: NavHostController) {
    composable(
        route = HOME_GENERATING_ROUTE,
        arguments =
            listOf(
                navArgument(H_ARG_LEVEL) { type = NavType.StringType; defaultValue = DEFAULT_LEVEL },
                navArgument(H_ARG_TOPIC) { type = NavType.StringType; defaultValue = "" },
                navArgument(H_ARG_LENGTH) { type = NavType.IntType; defaultValue = DEFAULT_LENGTH },
                navArgument(H_ARG_TOPIC_LABEL) { type = NavType.StringType; defaultValue = "" },
                navArgument(H_ARG_TOPIC_EMOJI) { type = NavType.StringType; defaultValue = "" },
            ),
    ) { entry ->
        val args = entry.arguments
        val level = args?.getString(H_ARG_LEVEL)?.ifBlank { DEFAULT_LEVEL } ?: DEFAULT_LEVEL
        val topic = Uri.decode(args?.getString(H_ARG_TOPIC).orEmpty())
        val length = args?.getInt(H_ARG_LENGTH) ?: DEFAULT_LENGTH
        // 세션 헤더 정체성(주제 제목·이모지)은 생성 화면이 소비하지 않고 세션으로 그대로 전달만 한다.
        val topicLabel = Uri.decode(args?.getString(H_ARG_TOPIC_LABEL).orEmpty())
        val topicEmoji = Uri.decode(args?.getString(H_ARG_TOPIC_EMOJI).orEmpty())
        DialogueGeneratingRoute(
            level = level,
            topic = topic,
            length = length,
            firstSession = false,
            isOnboarding = false,
            onStartConversation = {
                // 생성 화면을 백스택에서 제거(<1s 준비 자동전이 시 대화턴 뒤로가기 데드엔드 방지, 하니스·온보딩 선례).
                navController.navigate(
                    homeSessionRoute(level, length, topicLabel, topicEmoji),
                ) {
                    popUpTo(HOME_GENERATING_ROUTE) { inclusive = true }
                }
            },
            onViewRecords = { navController.exitToTabs() },
        )
    }
}

private fun NavGraphBuilder.sessionDestination(navController: NavHostController, reduceMotion: Boolean) {
    composable(
        route = HOME_SESSION_ROUTE,
        arguments =
            listOf(
                navArgument(H_ARG_LEVEL) { type = NavType.StringType; defaultValue = "" },
                navArgument(H_ARG_LENGTH) { type = NavType.IntType; defaultValue = DEFAULT_LENGTH },
                navArgument(H_ARG_TOPIC_LABEL) { type = NavType.StringType; defaultValue = "" },
                navArgument(H_ARG_TOPIC_EMOJI) { type = NavType.StringType; defaultValue = "" },
            ),
        // 대화 → 요약 핸드오프에서만 왼쪽으로 슬라이드 퇴장(요약 나가기 pop 은 popExitTransition=기본 유지).
        exitTransition = { sessionExitFor(targetState.destination.route, HOME_SUMMARY_ROUTE, reduceMotion) },
    ) { entry ->
        val args = entry.arguments
        // 정상 흐름은 level 을 실어오고, 이어하기 재진입은 비운다(요약 difficulty 는 표시용 → 비면 normal).
        val level = args?.getString(H_ARG_LEVEL)?.ifBlank { null } ?: DISPLAY_DIFFICULTY_DEFAULT
        // 세션 헤더 재료: 시작 플로우만 실어오고, 이어하기 재진입은 비어(빈 제목) 헤더 미표시로 폴백한다.
        val length = args?.getInt(H_ARG_LENGTH) ?: DEFAULT_LENGTH
        val topicLabel = Uri.decode(args?.getString(H_ARG_TOPIC_LABEL).orEmpty())
        val topicEmoji = Uri.decode(args?.getString(H_ARG_TOPIC_EMOJI).orEmpty())
        GeneratedDialogueSessionRoute(
            topicEmoji = topicEmoji,
            topicTitle = topicLabel,
            level = level,
            totalTurns = length,
            onViewSummary = { sessionId ->
                navController.navigate(homeSummaryRoute(sessionId = sessionId, level = level)) {
                    popUpTo(HOME_SESSION_ROUTE) { inclusive = true }
                }
            },
            // 대화 나가기(뒤로가기·헤더 back·시트 dismiss) → 기존 3탭 셸로 복귀(결정 #1, MAIN_TABS 보존).
            onExit = { navController.exitToTabs() },
        )
    }
}

private fun NavGraphBuilder.summaryDestination(navController: NavHostController, reduceMotion: Boolean) {
    composable(
        route = HOME_SUMMARY_ROUTE,
        arguments =
            listOf(
                navArgument(H_ARG_SESSION_ID) { type = NavType.StringType; defaultValue = "" },
                navArgument(H_ARG_LEVEL) { type = NavType.StringType; defaultValue = DISPLAY_DIFFICULTY_DEFAULT },
            ),
        // 대화(HOME_SESSION_ROUTE)에서 진입할 때만 오른쪽에서 슬라이드 진입.
        enterTransition = { summaryEnterFor(initialState.destination.route, HOME_SESSION_ROUTE, reduceMotion) },
    ) { entry ->
        val args = entry.arguments
        val sessionId = args?.getString(H_ARG_SESSION_ID).orEmpty()
        val difficulty =
            args?.getString(H_ARG_LEVEL)?.ifBlank { DISPLAY_DIFFICULTY_DEFAULT } ?: DISPLAY_DIFFICULTY_DEFAULT
        // 진입 슬라이드가 완전히 끝난 뒤에만 폭죽을 발사한다(요구): 전환이 목표 상태(Visible)에 정착하면 true.
        // 전환이 없으면(reduce-motion·비-세션 진입) 즉시 정착 → 사실상 바로 발사.
        val slideSettled = transition.currentState == transition.targetState
        SummaryRoute(
            sessionId = sessionId,
            difficulty = difficulty,
            modeId = "default",
            // 적립 값 실소스는 M3-05(요약 코디네이터가 기록 시 산출) — 여기선 정적 placeholder(animate=false).
            accrual = AccrualStrip(streakDays = 0, xp = 0),
            // 완료 버튼은 요약 화면이 고정 풋터로 소유(항상 노출, 프로토 정합).
            onDone = { navController.exitToTabs() },
            startConfetti = slideSettled,
        )
    }
}

private fun homeGeneratingRoute(
    level: String,
    topic: String,
    length: Int,
    topicLabel: String,
    topicEmoji: String,
): String =
    "home/generating?$H_ARG_LEVEL=$level&$H_ARG_TOPIC=${Uri.encode(topic)}&$H_ARG_LENGTH=$length" +
        "&$H_ARG_TOPIC_LABEL=${Uri.encode(topicLabel)}&$H_ARG_TOPIC_EMOJI=${Uri.encode(topicEmoji)}"

private fun homeSessionRoute(
    level: String,
    length: Int,
    topicLabel: String,
    topicEmoji: String,
): String =
    "home/session?$H_ARG_LEVEL=$level&$H_ARG_LENGTH=$length" +
        "&$H_ARG_TOPIC_LABEL=${Uri.encode(topicLabel)}&$H_ARG_TOPIC_EMOJI=${Uri.encode(topicEmoji)}"

private fun homeSummaryRoute(
    sessionId: String,
    level: String,
): String = "home/summary?$H_ARG_SESSION_ID=$sessionId&$H_ARG_LEVEL=$level"

/** outer NavHost 에 등록되는 그래프 route. */
const val HOME_SESSION_GRAPH_ROUTE = "home_session"

private const val HOME_SESSION_ROUTE =
    "home/session?level={level}&length={length}&topicLabel={topicLabel}&topicEmoji={topicEmoji}"
private const val HOME_GENERATING_ROUTE =
    "home/generating?level={level}&topic={topic}&length={length}&topicLabel={topicLabel}&topicEmoji={topicEmoji}"
private const val HOME_SUMMARY_ROUTE = "home/summary?sessionId={sessionId}&level={level}"

private const val H_ARG_LEVEL = "level"
private const val H_ARG_TOPIC = "topic"
private const val H_ARG_LENGTH = "length"
private const val H_ARG_TOPIC_LABEL = "topicLabel"
private const val H_ARG_TOPIC_EMOJI = "topicEmoji"
private const val H_ARG_SESSION_ID = "sessionId"

private const val DEFAULT_LEVEL = "easy"
private const val DEFAULT_LENGTH = 10
private const val DISPLAY_DIFFICULTY_DEFAULT = "normal"
